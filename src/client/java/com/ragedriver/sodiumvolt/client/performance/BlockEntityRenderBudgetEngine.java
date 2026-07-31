package com.ragedriver.sodiumvolt.client.performance;

import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.config.VoltPerformanceConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client/render-thread-only block-entity extraction cadence and submission
 * budgeting. It never changes block-entity ticking, packets, NBT, or world state.
 */
public final class BlockEntityRenderBudgetEngine {
	private static final int CACHE_TABLE_MAXIMUM = VoltPerformanceConfig.BERP_CACHE_CAPACITY_MAX;
	private static final int CACHE_SWEEP_PER_FRAME = 64;
	private static final int MINIMUM_CACHE_TTL_TICKS = 40;
	private static final int MAXIMUM_STATES_PLANNED = 8_192;
	private static final int DECISION_TABLE_SIZE = 16_384;
	private static final int MAXIMUM_STAT_TYPES = 64;
	private static final int MAXIMUM_INSPECTOR_TYPES = 3;

	private static final VoltPerformanceConfig CONFIG = VoltPerformanceConfig.getInstance();
	private static final VapsIdentityDecisionTable<BlockEntityRenderState> FRAME_DECISIONS =
			new VapsIdentityDecisionTable<>(DECISION_TABLE_SIZE);
	private static final BlockEntityBudgetQuotas FRAME_QUOTAS = new BlockEntityBudgetQuotas();
	private static final Statistics STATS = new Statistics();
	private static final BlockEntityFailOpenLatch FAIL_OPEN_LATCH = new BlockEntityFailOpenLatch();

	private static BoundedBlockEntityStateCache<
			BlockEntity,
			BlockEntityRenderState,
			BlockEntityType<?>,
			BlockState
	> cache;
	private static ClientLevel currentLevel;
	private static Vec3 cameraPosition = Vec3.ZERO;
	private static boolean active;
	private static boolean frameReady;
	private static boolean hasTarget;
	private static long targetPosition;
	private static boolean hasRecentInteraction;
	private static long recentInteractionPosition;
	private static long recentInteractionUntilTick;
	private static boolean runtimeFailureLogged;

	private BlockEntityRenderBudgetEngine() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level.isClientSide()) {
				Minecraft minecraft = Minecraft.getInstance();
				if (player == minecraft.player) {
					recordInteraction(level, hitResult.getBlockPos());
				}
			}
			return InteractionResult.PASS;
		});
		LevelExtractionEvents.END_EXTRACTION.register(BlockEntityRenderBudgetEngine::finishExtraction);
	}

	public static boolean isEnabled() {
		return CONFIG.isBlockEntityRenderBudgetingEnabled();
	}

	public static void beginFrame(ClientLevel level, Camera camera) {
		if (!isEnabled()) {
			disableIfNeeded();
			return;
		}
		if (!FAIL_OPEN_LATCH.canRun()) {
			active = false;
			frameReady = false;
			return;
		}
		try {
			if (currentLevel != level) {
				clearForLevel(level);
			}
			active = true;
			frameReady = true;
			cameraPosition = camera.position();
			long gameTick = level == null ? 0L : level.getGameTime();
			HitResult hitResult = Minecraft.getInstance().hitResult;
			if (hitResult instanceof BlockHitResult blockHitResult) {
				hasTarget = true;
				targetPosition = blockHitResult.getBlockPos().asLong();
			} else {
				hasTarget = false;
			}
			hasRecentInteraction = CONFIG.isBerpRecentInteractionGrace()
					&& level != null
					&& gameTick <= recentInteractionUntilTick;
			if (!cacheFeaturesEnabled()) {
				clearCache();
			} else if (cache != null) {
				cache.shrinkTo(CONFIG.getBerpCacheCapacity());
				int expired = cache.expire(gameTick, cacheTtlTicks(), CACHE_SWEEP_PER_FRAME);
				STATS.addCacheExpirations(expired);
			}
		} catch (RuntimeException | LinkageError exception) {
			failOpen(exception);
		}
	}

	public static BlockEntityRenderState extract(
			BlockEntityRenderDispatcher dispatcher,
			BlockEntity blockEntity,
			float partialTick,
			ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
			boolean offscreen
	) {
		if (!isEnabled() || !active || !frameReady) {
			return dispatcher.tryExtractRenderState(blockEntity, partialTick, crumblingOverlay, offscreen);
		}
		long position;
		long gameTick;
		boolean allowCaching;
		boolean cadenceEligible;
		boolean vanillaType;
		try {
			vanillaType = isVanilla(blockEntity.getType());
			cadenceEligible = BlockEntityCadenceLogic.cadenceEligible(
					cacheEligible(blockEntity),
					vanillaType,
					CONFIG.isBerpIncludeModdedBlockEntities()
			);
		} catch (RuntimeException | LinkageError exception) {
			failOpen(exception);
			return dispatcher.tryExtractRenderState(blockEntity, partialTick, crumblingOverlay, offscreen);
		}
		if (!cadenceEligible) {
			return dispatcher.tryExtractRenderState(blockEntity, partialTick, crumblingOverlay, offscreen);
		}
		try {
			position = blockEntity.getBlockPos().asLong();
			boolean protectedState = crumblingOverlay != null || isTargetOrRecent(position);
			double distanceSquared = distanceSquared(blockEntity.getBlockPos(), cameraPosition);
			double farDistance = CONFIG.getBerpFarRenderDistance();
			if (BlockEntityCadenceLogic.shouldCullBeyondFar(
					CONFIG.isBerpCullBeyondFarDistance(),
					vanillaType,
					protectedState,
					distanceSquared,
					farDistance * farDistance
			)) {
				STATS.addFarCull(blockEntity.getType());
				return null;
			}

			allowCaching = cacheFeaturesEnabled();
			BlockEntityRenderState cached = null;
			long lastFreshTick = Long.MIN_VALUE;
			gameTick = currentLevel == null ? 0L : currentLevel.getGameTime();
			if (allowCaching && cache != null) {
				cached = cache.lookup(
						position,
						blockEntity,
						blockEntity.getType(),
						blockEntity.getBlockState(),
						gameTick,
						cacheTtlTicks()
				);
				lastFreshTick = cache.lastFreshTickForLookup();
			}
			int near = CONFIG.getBerpNearDistance();
			int medium = CONFIG.getBerpMediumDistance();
			boolean fresh = BlockEntityCadenceLogic.shouldExtractFresh(
					true,
					protectedState,
					CONFIG.isBerpDistanceAwareStateUpdates(),
					allowCaching,
					cached != null,
					distanceSquared,
					(double) near * near,
					(double) medium * medium,
					gameTick,
					lastFreshTick,
					CONFIG.getBerpMediumUpdateInterval(),
					CONFIG.getBerpFarUpdateInterval()
			);
			if (!fresh && cached != null) {
				STATS.addCacheHit(blockEntity.getType());
				return cached;
			}
		} catch (RuntimeException | LinkageError exception) {
			failOpen(exception);
			return dispatcher.tryExtractRenderState(blockEntity, partialTick, crumblingOverlay, offscreen);
		}

		// Renderer code runs outside the scheduler catch. If a renderer fails,
		// preserve Minecraft's normal crash/reporting behavior and never invoke it twice.
		BlockEntityRenderState extracted =
				dispatcher.tryExtractRenderState(blockEntity, partialTick, crumblingOverlay, offscreen);
		try {
			STATS.addFreshExtraction();
			if (allowCaching && crumblingOverlay == null && validStateFor(blockEntity, extracted)) {
				if (cache == null) {
					cache = new BoundedBlockEntityStateCache<>();
				}
				BoundedBlockEntityStateCache.PutResult result = cache.put(
						position,
						blockEntity,
						extracted,
						blockEntity.getType(),
						blockEntity.getBlockState(),
						gameTick,
						Math.min(CACHE_TABLE_MAXIMUM, CONFIG.getBerpCacheCapacity())
				);
				if (result == BoundedBlockEntityStateCache.PutResult.EVICTED) {
					STATS.addCacheEviction();
				} else if (result == BoundedBlockEntityStateCache.PutResult.SATURATED) {
					STATS.markSaturated();
				}
			}
		} catch (RuntimeException | LinkageError exception) {
			failOpen(exception);
		}
		return extracted;
	}

	public static void onLevelChanged(ClientLevel level) {
		clearForLevel(level);
	}

	public static void onResourceReload() {
		clearCache();
		FAIL_OPEN_LATCH.resetForLifecycle();
	}

	public static StatisticsSnapshot snapshotStatistics() {
		if (!isEnabled() || !CONFIG.isBerpShowInspectorStatistics()) {
			return StatisticsSnapshot.EMPTY;
		}
		return STATS.snapshot(MAXIMUM_INSPECTOR_TYPES, cache == null ? 0 : cache.size());
	}

	private static void finishExtraction(LevelExtractionContext context) {
		if (!isEnabled() || !active) {
			FRAME_DECISIONS.releaseFrame();
			return;
		}
		List<BlockEntityRenderState> states = context.levelState().blockEntityRenderStates;
		if (states.isEmpty()) {
			FRAME_DECISIONS.releaseFrame();
			return;
		}
		try {
			FRAME_DECISIONS.nextFrame();
			FRAME_QUOTAS.reset();
			int passes = CONFIG.isBerpPrioritizeNearby() ? 5 : 2;
			int rawPrefixSize = BlockEntityPlanningBounds.rawPrefixSize(
					states.size(),
					MAXIMUM_STATES_PLANNED
			);
			boolean truncated = BlockEntityPlanningBounds.hasFailOpenOverflow(
					states.size(),
					MAXIMUM_STATES_PLANNED
			);
			planning:
			for (int pass = 0; pass < passes; pass++) {
				// Each priority sees the same bounded raw prefix. Entries after
				// it are never recorded and therefore remain rendered fail-open.
				Iterator<BlockEntityRenderState> planningIterator = states.iterator();
				for (int rawIndex = 0;
						rawIndex < rawPrefixSize && planningIterator.hasNext();
						rawIndex++) {
					BlockEntityRenderState state = planningIterator.next();
					if (!validForBudget(state)) {
						continue;
					}
					BlockEntityBudgetQuotas.Priority priority = priorityOf(state);
					boolean matches = pass == 0
							? priority == BlockEntityBudgetQuotas.Priority.TARGET_OR_RECENT
							: CONFIG.isBerpPrioritizeNearby()
									? priority.ordinal() == pass
									: priority != BlockEntityBudgetQuotas.Priority.TARGET_OR_RECENT;
					if (!matches || FRAME_DECISIONS.isScanned(state)) {
						continue;
					}
					if (!FRAME_DECISIONS.addScanned(state)) {
						truncated = true;
						break planning;
					}
					BlockEntityBudgetQuotas.Decision decision = FRAME_QUOTAS.trySelect(
							state.blockEntityType,
							CONFIG.getBerpGlobalRenderBudget(),
							CONFIG.isBerpPerTypeRenderLimits(),
							CONFIG.getBerpPerTypeRenderLimit(),
							priority
					);
					if (decision == BlockEntityBudgetQuotas.Decision.SELECTED
							|| decision == BlockEntityBudgetQuotas.Decision.SELECTED_ABSOLUTE) {
						FRAME_DECISIONS.select(state);
						if (decision == BlockEntityBudgetQuotas.Decision.SELECTED_ABSOLUTE) {
							STATS.addProtected();
						}
					} else if (decision == BlockEntityBudgetQuotas.Decision.GLOBAL_LIMIT) {
						STATS.addGlobalLimit(state.blockEntityType);
					} else {
						STATS.addPerTypeLimit(state.blockEntityType);
					}
				}
			}
			if (truncated || FRAME_QUOTAS.isSaturated()) {
				STATS.markSaturated();
				if (truncated) {
					STATS.addTruncatedFrame();
				}
			}
			Iterator<BlockEntityRenderState> iterator = states.iterator();
			while (iterator.hasNext()) {
				BlockEntityRenderState state = iterator.next();
				if (FRAME_DECISIONS.isScanned(state) && !FRAME_DECISIONS.isSelected(state)) {
					iterator.remove();
				}
			}
		} catch (RuntimeException | LinkageError exception) {
			failOpen(exception);
		} finally {
			FRAME_DECISIONS.releaseFrame();
		}
	}

	private static BlockEntityBudgetQuotas.Priority priorityOf(BlockEntityRenderState state) {
		long position = state.blockPos.asLong();
		if (isTargetOrRecent(position)) {
			return BlockEntityBudgetQuotas.Priority.TARGET_OR_RECENT;
		}
		if (state.breakProgress != null) {
			return BlockEntityBudgetQuotas.Priority.BREAKING;
		}
		double distanceSquared = distanceSquared(state.blockPos, cameraPosition);
		double near = CONFIG.getBerpNearDistance();
		if (!Double.isFinite(distanceSquared) || distanceSquared <= near * near) {
			return BlockEntityBudgetQuotas.Priority.NEAR;
		}
		double medium = CONFIG.getBerpMediumDistance();
		return distanceSquared <= medium * medium
				? BlockEntityBudgetQuotas.Priority.MEDIUM
				: BlockEntityBudgetQuotas.Priority.FAR;
	}

	private static boolean cacheEligible(BlockEntity blockEntity) {
		return blockEntity != null
				&& !blockEntity.isRemoved()
				&& blockEntity.getLevel() == currentLevel
				&& blockEntity.getBlockPos() != null
				&& blockEntity.getType() != null;
	}

	private static boolean validStateFor(BlockEntity blockEntity, BlockEntityRenderState state) {
		return state != null
				&& state.breakProgress == null
				&& state.blockPos != null
				&& state.blockPos.equals(blockEntity.getBlockPos())
				&& state.blockEntityType == blockEntity.getType();
	}

	private static boolean validForBudget(BlockEntityRenderState state) {
		return state != null && state.blockPos != null && state.blockEntityType != null;
	}

	private static boolean isVanilla(BlockEntityType<?> type) {
		Identifier identifier = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
		return identifier != null && "minecraft".equals(identifier.getNamespace());
	}

	private static boolean isTargetOrRecent(long position) {
		return hasTarget && targetPosition == position
				|| hasRecentInteraction && recentInteractionPosition == position;
	}

	private static double distanceSquared(BlockPos position, Vec3 camera) {
		double x = position.getX() + 0.5D - camera.x;
		double y = position.getY() + 0.5D - camera.y;
		double z = position.getZ() + 0.5D - camera.z;
		return x * x + y * y + z * z;
	}

	private static void recordInteraction(Level level, BlockPos position) {
		if (!isEnabled() || level != currentLevel || position == null) {
			return;
		}
		long gameTick = level.getGameTime();
		long graceTicks = CONFIG.getBerpInteractionGraceSeconds() * 20L;
		recentInteractionPosition = position.asLong();
		recentInteractionUntilTick = gameTick > Long.MAX_VALUE - graceTicks
				? Long.MAX_VALUE
				: gameTick + graceTicks;
		hasRecentInteraction = true;
	}

	private static int cacheTtlTicks() {
		return Math.max(MINIMUM_CACHE_TTL_TICKS, CONFIG.getBerpFarUpdateInterval() * 3);
	}

	private static boolean cacheFeaturesEnabled() {
		return BlockEntityCadenceLogic.shouldUseCache(
				CONFIG.isBerpCacheFarRenderStates(),
				CONFIG.isBerpDistanceAwareStateUpdates()
		);
	}

	private static void disableIfNeeded() {
		// Observing the feature disabled is the explicit retry boundary after a
		// fail-open. Reset this even when there is no active/cache state left.
		FAIL_OPEN_LATCH.observeDisabled();
		hasTarget = false;
		hasRecentInteraction = false;
		if (!active && cache == null) {
			return;
		}
		active = false;
		frameReady = false;
		FRAME_DECISIONS.releaseFrame();
		clearCache();
	}

	private static void clearForLevel(ClientLevel level) {
		currentLevel = level;
		active = false;
		frameReady = false;
		hasTarget = false;
		hasRecentInteraction = false;
		recentInteractionUntilTick = Long.MIN_VALUE;
		FAIL_OPEN_LATCH.resetForLifecycle();
		clearCache();
		STATS.reset();
	}

	private static void clearCache() {
		if (cache != null) {
			cache.clear();
			cache = null;
		}
	}

	private static void failOpen(Throwable exception) {
		FRAME_DECISIONS.releaseFrame();
		clearCache();
		active = false;
		frameReady = false;
		FAIL_OPEN_LATCH.fail();
		if (!runtimeFailureLogged) {
			runtimeFailureLogged = true;
			SodiumVolt.LOGGER.warn(
					"Block Entity Render-budgeting failed open; vanilla extraction will continue",
					exception
			);
		}
	}

	private static final class Statistics {
		private final BlockEntityType<?>[] types = new BlockEntityType<?>[MAXIMUM_STAT_TYPES];
		private final long[] limitedByType = new long[MAXIMUM_STAT_TYPES];
		private final long[] cacheHitsByType = new long[MAXIMUM_STAT_TYPES];
		private long cacheHits;
		private long freshExtractions;
		private long farCulled;
		private long globalLimited;
		private long perTypeLimited;
		private long protectedStates;
		private long cacheEvictions;
		private long cacheExpirations;
		private long truncatedFrames;
		private boolean saturated;

		private void addCacheHit(BlockEntityType<?> type) {
			this.cacheHits = saturatedAdd(this.cacheHits, 1L);
			int index = typeIndex(type);
			if (index >= 0) {
				this.cacheHitsByType[index] = saturatedAdd(this.cacheHitsByType[index], 1L);
			}
		}

		private void addFreshExtraction() {
			this.freshExtractions = saturatedAdd(this.freshExtractions, 1L);
		}

		private void addFarCull(BlockEntityType<?> type) {
			this.farCulled = saturatedAdd(this.farCulled, 1L);
			addLimitedType(type);
		}

		private void addGlobalLimit(BlockEntityType<?> type) {
			this.globalLimited = saturatedAdd(this.globalLimited, 1L);
			addLimitedType(type);
		}

		private void addPerTypeLimit(BlockEntityType<?> type) {
			this.perTypeLimited = saturatedAdd(this.perTypeLimited, 1L);
			addLimitedType(type);
		}

		private void addProtected() {
			this.protectedStates = saturatedAdd(this.protectedStates, 1L);
		}

		private void addCacheEviction() {
			this.cacheEvictions = saturatedAdd(this.cacheEvictions, 1L);
		}

		private void addCacheExpirations(int count) {
			this.cacheExpirations = saturatedAdd(this.cacheExpirations, Math.max(0, count));
		}

		private void addTruncatedFrame() {
			this.truncatedFrames = saturatedAdd(this.truncatedFrames, 1L);
		}

		private void markSaturated() {
			this.saturated = true;
		}

		private void addLimitedType(BlockEntityType<?> type) {
			int index = typeIndex(type);
			if (index >= 0) {
				this.limitedByType[index] = saturatedAdd(this.limitedByType[index], 1L);
			}
		}

		private int typeIndex(BlockEntityType<?> type) {
			int empty = -1;
			for (int index = 0; index < this.types.length; index++) {
				if (this.types[index] == type) {
					return index;
				}
				if (empty < 0 && this.types[index] == null) {
					empty = index;
				}
			}
			if (empty >= 0) {
				this.types[empty] = type;
				return empty;
			}
			this.saturated = true;
			return -1;
		}

		private StatisticsSnapshot snapshot(int maximumTypes, int cacheSize) {
			ArrayList<TypeStatistic> top = new ArrayList<>(maximumTypes);
			boolean[] selected = new boolean[this.types.length];
			for (int output = 0; output < maximumTypes; output++) {
				int best = -1;
				long bestTotal = 0L;
				for (int index = 0; index < this.types.length; index++) {
					long total = saturatedAdd(this.limitedByType[index], this.cacheHitsByType[index]);
					if (!selected[index] && this.types[index] != null && total > bestTotal) {
						best = index;
						bestTotal = total;
					}
				}
				if (best < 0) {
					break;
				}
				selected[best] = true;
				top.add(new TypeStatistic(
						sanitizedTypeLabel(this.types[best]),
						this.limitedByType[best],
						this.cacheHitsByType[best]
				));
			}
			return new StatisticsSnapshot(
					this.cacheHits,
					this.freshExtractions,
					this.farCulled,
					this.globalLimited,
					this.perTypeLimited,
					this.protectedStates,
					this.cacheEvictions,
					this.cacheExpirations,
					this.truncatedFrames,
					cacheSize,
					this.saturated,
					List.copyOf(top)
			);
		}

		private void reset() {
			for (int index = 0; index < this.types.length; index++) {
				this.types[index] = null;
				this.limitedByType[index] = 0L;
				this.cacheHitsByType[index] = 0L;
			}
			this.cacheHits = 0L;
			this.freshExtractions = 0L;
			this.farCulled = 0L;
			this.globalLimited = 0L;
			this.perTypeLimited = 0L;
			this.protectedStates = 0L;
			this.cacheEvictions = 0L;
			this.cacheExpirations = 0L;
			this.truncatedFrames = 0L;
			this.saturated = false;
		}
	}

	private static String sanitizedTypeLabel(BlockEntityType<?> type) {
		Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
		String value = id == null ? "unknown" : id.toString();
		StringBuilder result = new StringBuilder(Math.min(48, value.length()));
		for (int index = 0; index < value.length() && result.length() < 48; index++) {
			char character = value.charAt(index);
			if (!Character.isISOControl(character)) {
				result.append(character);
			}
		}
		return result.isEmpty() ? "unknown" : result.toString();
	}

	private static long saturatedAdd(long value, long increment) {
		return BlockEntityCadenceLogic.saturatingAdd(value, increment);
	}

	public record StatisticsSnapshot(
			long cacheHits,
			long freshExtractions,
			long farCulled,
			long globalLimited,
			long perTypeLimited,
			long protectedStates,
			long cacheEvictions,
			long cacheExpirations,
			long truncatedFrames,
			int cacheSize,
			boolean saturated,
			List<TypeStatistic> topTypes
	) {
		public static final StatisticsSnapshot EMPTY =
				new StatisticsSnapshot(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0, false, List.of());
	}

	public record TypeStatistic(String label, long limited, long cacheHits) {
	}
}
