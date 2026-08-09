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
	private static final AttDiagnosticCounters DIAGNOSTICS =
			new AttDiagnosticCounters();
	private static final AttRuntimeLifecycle RUNTIME_LIFECYCLE = new AttRuntimeLifecycle();
	private static final AttExemptionReloadGate EXEMPTION_RELOAD_GATE =
			new AttExemptionReloadGate();
	private static final Identifier[] CRITICAL_TEXTURES = makeCriticalTextures();

	private static volatile AttIdentifierLookup exemptionLookup = new AttIdentifierLookup();
	private static volatile ResourceManager latestResourceManager;
	private static long clientTick;
	private static int visibilityGeneration = 1;
	private static int previousVisibilityGeneration;
	private static int completedVisibilityGeneration;
	private static int visibleSections;
	private static int visibleSprites;
	private static boolean visibilityScanActive;
	private static boolean visibilityScanTruncated;
	private static boolean visibleSectionPrepared;
	private static float visibleSectionDistance;
	private static boolean failureLogged;
	private static boolean mappingFailureLogged;

	private AnimatedTextureThrottleEngine() {
	}

	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			boolean enabled = CONFIG.isAnimatedTextureThrottlingEnabled();
			if (!enabled) {
				if (!RUNTIME_LIFECYCLE.observeAndShouldCleanup(false)) {
					return;
				}
				FAILURE.observeDisabled();
				clearRuntimeCounters();
				return;
			}
			RUNTIME_LIFECYCLE.observeAndShouldCleanup(true);
			if (RUNTIME_LIFECYCLE.consumeActivation()) {
				requestEnabledExemptionReload(client.getResourceManager());
			}
			clientTick = AttPolicy.saturatingAdd(clientTick, 1L);
		});
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
				SodiumVolt.id("animated_texture_exemptions"),
				(ResourceManagerReloadListener) AnimatedTextureThrottleEngine::requestResourceReload
		);
		rebuildExemptionLookup(new String[0]);
	}

	public static boolean isConfiguredEnabled() {
		boolean enabled = CONFIG.isAnimatedTextureThrottlingEnabled();
		if (enabled) {
			RUNTIME_LIFECYCLE.observeAndShouldCleanup(true);
		}
		return enabled;
	}

	public static boolean canSchedule() {
		return isConfiguredEnabled()
				&& EXEMPTION_RELOAD_GATE.ready()
				&& FAILURE.canRun()
				&& !exemptionLookup.isSaturated();
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

	public static boolean beginAtlasCycle(Identifier atlasLocation, boolean warmup) {
		try {
			double fullSpeedDistance = CONFIG.getAttFullSpeedDistance();
			Minecraft minecraft = Minecraft.getInstance();
			BUDGET.beginClientTick(clientTick, CONFIG.getAttPerAtlasAnimationBudget());
			return AttAnimationCycleContext.pushCaptured(
				atlasLocation,
				warmup,
				CONFIG.isAttShowInspectorStatistics(),
				CONFIG.isAttKeepInterfaceAtlasesFullSpeed(),
				minecraft.gui.screen() != null || minecraft.gui.overlay() != null,
				CONFIG.isAttPauseInvisibleAnimations(),
				CONFIG.getAttUnseenKeepaliveTicks(),
				CONFIG.isAttDistanceAwareCadence(),
				fullSpeedDistance * fullSpeedDistance,
				CONFIG.getAttDistantUpdateInterval(),
				CONFIG.isAttImmediateSmoothResume(),
				AdaptivePerformanceController.shouldThrottleAtlasAnimations(),
				CONFIG.isAttExemptCriticalVanillaTextures(),
				CONFIG.isAttHonorExemptionLists(),
					exemptionLookup
			);
		} catch (RuntimeException | LinkageError exception) {
			fail(exception);
			return false;
		}
	}

	public static TickGate gateStateTick(TextureAtlasSprite sprite) {
		AttRuntimeSettings.AtlasCycle cycle = AttAnimationCycleContext.current();
		if (cycle == null || !FAILURE.canRun()
				|| cycle.exemptionLookup().isSaturated() || sprite == null) {
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
			boolean interfaceProtected = cycle.keepInterfaceAtlasesFullSpeed()
					&& !TextureAtlas.LOCATION_BLOCKS.equals(cycle.atlasLocation())
					&& !TextureAtlas.LOCATION_PARTICLES.equals(cycle.atlasLocation());
			boolean screenProtected = cycle.keepInterfaceAtlasesFullSpeed()
					&& active
					&& cycle.screenOpen();
			boolean unknownActive = completedVisibilityGeneration == 0 || active && !visible;
			Identifier spriteId = sprite.contents().name();
			boolean exempt = isExempt(spriteId, cycle);
			boolean exemptionGranted = exempt
					&& !cycle.warmup()
					&& !interfaceProtected
					&& !screenProtected
					&& !unknownActive
					&& BUDGET.claimExemption();
			double distance = visibility.sodiumVolt$minimumDistanceSquared();
			boolean normalAvailable = BUDGET.canClaimNormal();
			decision = AttPolicy.decide(
					true,
					cycle.warmup(),
					interfaceProtected,
					screenProtected,
					unknownActive,
					exempt,
					exemptionGranted,
					visible,
					cycle.pauseInvisibleAnimations(),
					cycle.unseenKeepaliveTicks(),
					clientTick,
					visibility.sodiumVolt$lastVisibleTick(),
					cycle.distanceAwareCadence(),
					distance,
					cycle.fullSpeedDistanceSquared(),
					cycle.distantUpdateInterval(),
					cycle.immediateSmoothResume(),
					visibility.sodiumVolt$resumePending(),
					cycle.apcThrottleAnimations(),
					normalAvailable
			);
		} catch (RuntimeException | LinkageError exception) {
			fail(exception);
			return TickGate.ALLOW_FAIL_OPEN;
		}

		switch (decision) {
			case TICK_FAIL_OPEN, TICK_PROTECTED -> {
				DIAGNOSTICS.recordProtected(cycle.collectStatistics());
				return TickGate.ALLOW_TRACKED;
			}
			case TICK_EXEMPT -> {
				DIAGNOSTICS.recordExempt(cycle.collectStatistics());
				return TickGate.ALLOW_TRACKED;
			}
			case TICK_NORMAL -> {
				if (BUDGET.claimNormal()) {
					DIAGNOSTICS.recordTicked(cycle.collectStatistics());
					return TickGate.ALLOW_TRACKED;
				} else {
					sodium.sodium$setActive(false);
					DIAGNOSTICS.recordSkippedBudget(cycle.collectStatistics());
					return TickGate.SKIP;
				}
			}
			case SKIP_INVISIBLE -> {
				sodium.sodium$setActive(false);
				DIAGNOSTICS.recordSkippedInvisible(cycle.collectStatistics());
				return TickGate.SKIP;
			}
			case SKIP_CADENCE -> {
				sodium.sodium$setActive(false);
				DIAGNOSTICS.recordSkippedCadence(cycle.collectStatistics());
				return TickGate.SKIP;
			}
			case SKIP_BUDGET -> {
				sodium.sodium$setActive(false);
				DIAGNOSTICS.recordSkippedBudget(cycle.collectStatistics());
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
		clearVisibleSectionContext();
		AttHotPathGate.publishVisibilityScan(false);
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
		AttHotPathGate.publishVisibilityScan(true);
	}

	public static void prepareVisibleSection(
			RenderRegion region,
			int sectionIndex,
			TextureAtlasSprite[] sprites,
			Vector3dc camera
	) {
		clearVisibleSectionContext();
		if (!visibilityScanActive || sprites == null || camera == null) {
			return;
		}
		if (++visibleSections > MAXIMUM_VISIBLE_SECTIONS) {
			visibilityScanTruncated = true;
			return;
		}
		if (visibilityScanTruncated) {
			return;
		}
		try {
			RenderSection[] sections = ((RenderRegionAccessor) region).sodiumVolt$getSections();
			if (sectionIndex < 0 || sectionIndex >= sections.length || sections[sectionIndex] == null) {
				visibilityScanTruncated = true;
				return;
			}
			visibleSectionDistance = sections[sectionIndex].getSquaredDistance(
					(float) camera.x(),
					(float) camera.y(),
					(float) camera.z()
			);
			visibleSectionPrepared = true;
		} catch (RuntimeException | LinkageError exception) {
			fail(exception);
		}
	}

	public static void recordVisibleSprite(TextureAtlasSprite sprite) {
		if (!visibilityScanActive || !visibleSectionPrepared) {
			return;
		}
		if (++visibleSprites > MAXIMUM_VISIBLE_SPRITES) {
			visibilityScanTruncated = true;
			clearVisibleSectionContext();
			return;
		}
		if (sprite == null) {
			return;
		}
		try {
			((AttSpriteExtension) sprite).sodiumVolt$recordVisibility(
					visibilityGeneration,
					previousVisibilityGeneration,
					visibleSectionDistance,
					clientTick
			);
		} catch (RuntimeException | LinkageError exception) {
			fail(exception);
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
		AttHotPathGate.publishVisibilityScan(false);
		clearVisibleSectionContext();
	}

	public static void fail(Throwable exception) {
		FAILURE.fail();
		visibilityScanActive = false;
		AttHotPathGate.publishVisibilityScan(false);
		clearVisibleSectionContext();
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
		DIAGNOSTICS.recordMappingFallback(CONFIG.isAttShowInspectorStatistics());
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
				DIAGNOSTICS.ticked(),
				DIAGNOSTICS.skippedInvisible(),
				DIAGNOSTICS.skippedCadence(),
				DIAGNOSTICS.skippedBudget(),
				DIAGNOSTICS.protectedTicks(),
				DIAGNOSTICS.exemptTicks(),
				DIAGNOSTICS.mappingFallbacks(),
				visibleSections,
				visibleSprites,
				visibilityScanTruncated
		);
	}

	private static void requestResourceReload(ResourceManager manager) {
		latestResourceManager = manager;
		long generation = EXEMPTION_RELOAD_GATE.onResourceReload(
				CONFIG.isAnimatedTextureThrottlingEnabled()
		);
		runExemptionLoads(generation);
	}

	private static void requestEnabledExemptionReload(ResourceManager manager) {
		latestResourceManager = manager;
		runExemptionLoads(EXEMPTION_RELOAD_GATE.onEnabledTick());
	}

	private static void runExemptionLoads(long generation) {
		long current = generation;
		while (current != 0L) {
			try {
				ResourceManager manager = latestResourceManager;
				if (manager == null) {
					EXEMPTION_RELOAD_GATE.fail(current);
					return;
				}
				AttResourceExemptionLoader.LoadResult result = AttResourceExemptionLoader.load(manager);
				AttIdentifierLookup replacement = buildExemptionLookup(result.identifiers());
				AttExemptionReloadGate.Completion completion =
						EXEMPTION_RELOAD_GATE.complete(current, () -> exemptionLookup = replacement);
				if (completion.publish()) {
					FAILURE.resetForReload();
					failureLogged = false;
				}
				current = completion.followUpGeneration();
			} catch (RuntimeException | LinkageError exception) {
				EXEMPTION_RELOAD_GATE.fail(current);
				fail(exception);
				return;
			}
		}
	}

	private static void rebuildExemptionLookup(String[] resources) {
		exemptionLookup = buildExemptionLookup(resources);
	}

	private static AttIdentifierLookup buildExemptionLookup(String[] resources) {
		AttIdentifierLookup replacement = new AttIdentifierLookup();
		for (String user : CONFIG.getAttUserExemptTextures()) {
			replacement.add(user);
		}
		for (String resource : resources) {
			replacement.add(resource);
		}
		return replacement;
	}

	private static boolean isExempt(
			Identifier identifier,
			AttRuntimeSettings.AtlasCycle cycle
	) {
		if (cycle.exemptCriticalTextures()) {
			for (Identifier critical : CRITICAL_TEXTURES) {
				if (critical.equals(identifier)) {
					return true;
				}
			}
		}
		return cycle.honorExemptionLists()
				&& cycle.exemptionLookup().contains(identifier);
	}

	private static Identifier[] makeCriticalTextures() {
		Identifier[] identifiers = new Identifier[AttCriticalTextures.EXACT_IDS.length];
		for (int index = 0; index < identifiers.length; index++) {
			identifiers[index] = Identifier.parse(AttCriticalTextures.EXACT_IDS[index]);
		}
		return identifiers;
	}

	private static void clearRuntimeCounters() {
		DIAGNOSTICS.clear();
		visibleSections = 0;
		visibleSprites = 0;
		visibilityScanTruncated = false;
		visibilityScanActive = false;
		AttHotPathGate.publishVisibilityScan(false);
		clearVisibleSectionContext();
		completedVisibilityGeneration = 0;
	}

	private static void clearVisibleSectionContext() {
		visibleSectionPrepared = false;
		visibleSectionDistance = 0.0F;
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
