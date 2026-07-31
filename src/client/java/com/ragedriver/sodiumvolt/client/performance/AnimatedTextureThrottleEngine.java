package com.ragedriver.sodiumvolt.client.performance;

import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.config.VoltPerformanceConfig;
import com.ragedriver.sodiumvolt.client.mixin.RenderRegionAccessor;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteContentsExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.joml.Vector3dc;

import java.util.List;

public final class AnimatedTextureThrottleEngine {
	public static final int MAXIMUM_RAW_ATLAS_SPRITES = 8_192;
	public static final int MAXIMUM_ANIMATION_STATES = 4_096;
	public static final int WARMUP_CYCLES = 40;
	private static final int MAXIMUM_VISIBLE_SECTIONS = 8_192;
	private static final int MAXIMUM_VISIBLE_SPRITES = 65_536;
	private static final VoltPerformanceConfig CONFIG = VoltPerformanceConfig.getInstance();
	private static final AttFailOpenLatch FAILURE = new AttFailOpenLatch();
	private static final AttTickBudget BUDGET = new AttTickBudget();
	private static final Identifier[] CRITICAL_TEXTURES = makeCriticalTextures();

	private static volatile AttIdentifierLookup exemptionLookup = new AttIdentifierLookup();
	private static long clientTick;
	private static int visibilityGeneration = 1;
	private static int previousVisibilityGeneration;
	private static int completedVisibilityGeneration;
	private static int visibleSections;
	private static int visibleSprites;
	private static boolean visibilityScanActive;
	private static boolean visibilityScanTruncated;
	private static boolean failureLogged;
	private static boolean mappingFailureLogged;
	private static long ticked;
	private static long skippedInvisible;
	private static long skippedCadence;
	private static long skippedBudget;
	private static long protectedTicks;
	private static long exemptTicks;
	private static long mappingFallbacks;

	private AnimatedTextureThrottleEngine() {
	}

	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			clientTick = AttPolicy.saturatingAdd(clientTick, 1L);
			if (!CONFIG.isAnimatedTextureThrottlingEnabled()) {
				FAILURE.observeDisabled();
				clearRuntimeCounters();
			}
		});
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
				SodiumVolt.id("animated_texture_exemptions"),
				(ResourceManagerReloadListener) AnimatedTextureThrottleEngine::reloadExemptions
		);
		rebuildExemptionLookup(new String[0]);
	}

	public static boolean isConfiguredEnabled() {
		boolean enabled = CONFIG.isAnimatedTextureThrottlingEnabled();
		if (!enabled) {
			FAILURE.observeDisabled();
		}
		return enabled;
	}

	public static boolean canSchedule() {
		return isConfiguredEnabled() && FAILURE.canRun() && !exemptionLookup.isSaturated();
	}

	public static AttStateSpriteMapping<SpriteContents.AnimationState, TextureAtlasSprite> buildMapping(
			List<TextureAtlasSprite> sprites,
			List<SpriteContents.AnimationState> states
	) {
		return AttStateSpriteMapping.build(
				sprites,
				states,
				sprite -> sprite != null && sprite.contents().isAnimated(),
				MAXIMUM_RAW_ATLAS_SPRITES,
				MAXIMUM_ANIMATION_STATES
		);
	}

	public static void beginAtlasCycle() {
		BUDGET.beginClientTick(clientTick, CONFIG.getAttPerAtlasAnimationBudget());
	}

	public static TickGate gateStateTick(
			Identifier atlasLocation,
			TextureAtlasSprite sprite,
			boolean warmup
	) {
		if (!canSchedule() || sprite == null) {
			return TickGate.ALLOW_FAIL_OPEN;
		}
		AttPolicy.Decision decision;
		AttSpriteExtension visibility;
		SpriteContentsExtension sodium;
		try {
			visibility = (AttSpriteExtension) sprite;
			sodium = (SpriteContentsExtension) sprite.contents();
			boolean active = sodium.sodium$isActive();
			boolean visible = visibility.sodiumVolt$visibilityGeneration()
					== completedVisibilityGeneration && completedVisibilityGeneration != 0;
			boolean interfaceProtected = CONFIG.isAttKeepInterfaceAtlasesFullSpeed()
					&& !TextureAtlas.LOCATION_BLOCKS.equals(atlasLocation)
					&& !TextureAtlas.LOCATION_PARTICLES.equals(atlasLocation);
			Minecraft minecraft = Minecraft.getInstance();
			boolean screenProtected = CONFIG.isAttKeepInterfaceAtlasesFullSpeed()
					&& active
					&& (minecraft.gui.screen() != null || minecraft.gui.overlay() != null);
			boolean unknownActive = completedVisibilityGeneration == 0 || active && !visible;
			Identifier spriteId = sprite.contents().name();
			boolean exempt = isExempt(spriteId);
			boolean exemptionGranted = exempt
					&& !warmup
					&& !interfaceProtected
					&& !screenProtected
					&& !unknownActive
					&& BUDGET.claimExemption();
			double distance = visibility.sodiumVolt$minimumDistanceSquared();
			double fullSpeed = CONFIG.getAttFullSpeedDistance();
			fullSpeed *= fullSpeed;
			boolean normalAvailable = BUDGET.canClaimNormal();
			decision = AttPolicy.decide(
					true,
					warmup,
					interfaceProtected,
					screenProtected,
					unknownActive,
					exempt,
					exemptionGranted,
					visible,
					CONFIG.isAttPauseInvisibleAnimations(),
					CONFIG.getAttUnseenKeepaliveTicks(),
					clientTick,
					visibility.sodiumVolt$lastVisibleTick(),
					CONFIG.isAttDistanceAwareCadence(),
					distance,
					fullSpeed,
					CONFIG.getAttDistantUpdateInterval(),
					CONFIG.isAttImmediateSmoothResume(),
					visibility.sodiumVolt$resumePending(),
					AdaptivePerformanceController.shouldThrottleAtlasAnimations(),
					normalAvailable
			);
		} catch (RuntimeException | LinkageError exception) {
			fail(exception);
			return TickGate.ALLOW_FAIL_OPEN;
		}

		switch (decision) {
			case TICK_FAIL_OPEN, TICK_PROTECTED -> {
				protectedTicks = AttPolicy.saturatingAdd(protectedTicks, 1L);
				return TickGate.ALLOW_TRACKED;
			}
			case TICK_EXEMPT -> {
				exemptTicks = AttPolicy.saturatingAdd(exemptTicks, 1L);
				return TickGate.ALLOW_TRACKED;
			}
			case TICK_NORMAL -> {
				if (BUDGET.claimNormal()) {
					ticked = AttPolicy.saturatingAdd(ticked, 1L);
					return TickGate.ALLOW_TRACKED;
				} else {
					sodium.sodium$setActive(false);
					skippedBudget = AttPolicy.saturatingAdd(skippedBudget, 1L);
					return TickGate.SKIP;
				}
			}
			case SKIP_INVISIBLE -> {
				sodium.sodium$setActive(false);
				skippedInvisible = AttPolicy.saturatingAdd(skippedInvisible, 1L);
				return TickGate.SKIP;
			}
			case SKIP_CADENCE -> {
				sodium.sodium$setActive(false);
				skippedCadence = AttPolicy.saturatingAdd(skippedCadence, 1L);
				return TickGate.SKIP;
			}
			case SKIP_BUDGET -> {
				sodium.sodium$setActive(false);
				skippedBudget = AttPolicy.saturatingAdd(skippedBudget, 1L);
				return TickGate.SKIP;
			}
			default -> {
				return TickGate.ALLOW_FAIL_OPEN;
			}
		}
	}

	public static void completeStateTick(TextureAtlasSprite sprite) {
		try {
			AttSpriteExtension visibility = (AttSpriteExtension) sprite;
			if (visibility.sodiumVolt$resumePending()) {
				visibility.sodiumVolt$consumeResume();
			}
		} catch (RuntimeException | LinkageError exception) {
			fail(exception);
		}
	}

	public static void beginVisibilityScan(Vector3dc camera) {
		if (!canSchedule() || camera == null) {
			visibilityScanActive = false;
			return;
		}
		previousVisibilityGeneration = completedVisibilityGeneration;
		visibilityGeneration = AttVisibilityLogic.nextGeneration(visibilityGeneration);
		visibleSections = 0;
		visibleSprites = 0;
		visibilityScanTruncated = false;
		visibilityScanActive = true;
	}

	public static void recordVisibleSection(
			RenderRegion region,
			int sectionIndex,
			TextureAtlasSprite[] sprites,
			Vector3dc camera
	) {
		if (!visibilityScanActive || sprites == null || camera == null) {
			return;
		}
		if (++visibleSections > MAXIMUM_VISIBLE_SECTIONS) {
			visibilityScanTruncated = true;
			return;
		}
		try {
			RenderSection[] sections = ((RenderRegionAccessor) region).sodiumVolt$getSections();
			if (sectionIndex < 0 || sectionIndex >= sections.length || sections[sectionIndex] == null) {
				visibilityScanTruncated = true;
				return;
			}
			float distance = sections[sectionIndex].getSquaredDistance(
					(float) camera.x(),
					(float) camera.y(),
					(float) camera.z()
			);
			for (TextureAtlasSprite sprite : sprites) {
				if (++visibleSprites > MAXIMUM_VISIBLE_SPRITES) {
					visibilityScanTruncated = true;
					return;
				}
				if (sprite != null) {
					((AttSpriteExtension) sprite).sodiumVolt$recordVisibility(
							visibilityGeneration,
							previousVisibilityGeneration,
							distance,
							clientTick
					);
				}
			}
		} catch (RuntimeException | LinkageError exception) {
			fail(exception);
			visibilityScanActive = false;
		}
	}

	public static void endVisibilityScan() {
		if (AttVisibilityLogic.canPublishGeneration(
				visibilityScanActive,
				!FAILURE.canRun(),
				visibilityScanTruncated
		)) {
			completedVisibilityGeneration = visibilityGeneration;
		} else if (visibilityScanTruncated) {
			completedVisibilityGeneration = 0;
		}
		visibilityScanActive = false;
	}

	public static void fail(Throwable exception) {
		FAILURE.fail();
		visibilityScanActive = false;
		completedVisibilityGeneration = 0;
		if (!failureLogged) {
			failureLogged = true;
			SodiumVolt.LOGGER.error(
					"Animated Texture Throttling failed open; disable and re-enable it or reload resources to retry",
					exception
			);
		}
	}

	public static void recordMappingFallback() {
		mappingFallbacks = AttPolicy.saturatingAdd(mappingFallbacks, 1L);
		if (!mappingFailureLogged) {
			mappingFailureLogged = true;
			SodiumVolt.LOGGER.warn(
					"An animated texture atlas mapping was inconsistent; that atlas will tick vanilla until a meaningful retry"
			);
		}
	}

	public static StatisticsSnapshot snapshotStatistics() {
		if (!canSchedule() || !CONFIG.isAttShowInspectorStatistics()) {
			return StatisticsSnapshot.EMPTY;
		}
		return new StatisticsSnapshot(
				ticked,
				skippedInvisible,
				skippedCadence,
				skippedBudget,
				protectedTicks,
				exemptTicks,
				mappingFallbacks,
				visibleSections,
				visibleSprites,
				visibilityScanTruncated
		);
	}

	private static void reloadExemptions(ResourceManager manager) {
		try {
			AttResourceExemptionLoader.LoadResult result = AttResourceExemptionLoader.load(manager);
			rebuildExemptionLookup(result.identifiers());
			FAILURE.resetForReload();
			failureLogged = false;
		} catch (RuntimeException | LinkageError exception) {
			fail(exception);
		}
	}

	private static void rebuildExemptionLookup(String[] resources) {
		AttIdentifierLookup replacement = new AttIdentifierLookup();
		for (String user : CONFIG.getAttUserExemptTextures()) {
			replacement.add(user);
		}
		for (String resource : resources) {
			replacement.add(resource);
		}
		exemptionLookup = replacement;
	}

	private static boolean isExempt(Identifier identifier) {
		if (CONFIG.isAttExemptCriticalVanillaTextures()) {
			for (Identifier critical : CRITICAL_TEXTURES) {
				if (critical.equals(identifier)) {
					return true;
				}
			}
		}
		return CONFIG.isAttHonorExemptionLists() && exemptionLookup.contains(identifier);
	}

	private static Identifier[] makeCriticalTextures() {
		Identifier[] identifiers = new Identifier[AttCriticalTextures.EXACT_IDS.length];
		for (int index = 0; index < identifiers.length; index++) {
			identifiers[index] = Identifier.parse(AttCriticalTextures.EXACT_IDS[index]);
		}
		return identifiers;
	}

	private static void clearRuntimeCounters() {
		ticked = 0L;
		skippedInvisible = 0L;
		skippedCadence = 0L;
		skippedBudget = 0L;
		protectedTicks = 0L;
		exemptTicks = 0L;
		mappingFallbacks = 0L;
		visibleSections = 0;
		visibleSprites = 0;
		visibilityScanTruncated = false;
		completedVisibilityGeneration = 0;
	}

	public record StatisticsSnapshot(
			long ticked,
			long skippedInvisible,
			long skippedCadence,
			long skippedBudget,
			long protectedTicks,
			long exemptTicks,
			long mappingFallbacks,
			int visibleSections,
			int visibleSprites,
			boolean scanTruncated
	) {
		public static final StatisticsSnapshot EMPTY =
				new StatisticsSnapshot(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0, false);
	}

	public enum TickGate {
		ALLOW_FAIL_OPEN,
		ALLOW_TRACKED,
		SKIP
	}
}
