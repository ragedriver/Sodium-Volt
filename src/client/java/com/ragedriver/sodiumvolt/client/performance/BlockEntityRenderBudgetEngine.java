package com.ragedriver.sodiumvolt.client.performance;

import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.config.VoltPerformanceConfig;
import com.ragedriver.sodiumvolt.client.guard.VoltGuardEngine;
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
    private static final int MINIMUM_TYPE_CLASSIFICATION_CAPACITY = 64;

    private static final VoltPerformanceConfig CONFIG = VoltPerformanceConfig.getInstance();
    private static VapsIdentityDecisionTable<BlockEntityRenderState> frameDecisions;
    private static final BlockEntityBudgetQuotas FRAME_QUOTAS = new BlockEntityBudgetQuotas();
    private static final BlockEntityBudgetQuotas.Priority[] PLANNING_PRIORITIES =
            BlockEntityBudgetQuotas.Priority.values();
    private static BlockEntityPlanningWorkspace<BlockEntityRenderState> framePlanning;
    private static final Statistics STATS = new Statistics();
    private static final StatisticsCollectionGate STATISTICS_GATE = new StatisticsCollectionGate();
    private static final BlockEntityFailOpenLatch FAIL_OPEN_LATCH = new BlockEntityFailOpenLatch();
    private static final BerpFrameRuntimeState FRAME_RUNTIME = new BerpFrameRuntimeState();
    private static final BerpRuntimeLifecycle RUNTIME_LIFECYCLE = new BerpRuntimeLifecycle();
    private static final BlockEntityTypeClassificationCache.Classifier<BlockEntityType<?>>
            VANILLA_TYPE_CLASSIFIER = BlockEntityRenderBudgetEngine::classifyVanillaType;

    private static BoundedBlockEntityStateCache<
            BlockEntity,
            BlockEntityRenderState,
            BlockEntityType<?>,
            BlockState
            > cache;
    private static BlockEntityTypeClassificationCache<BlockEntityType<?>> typeClassificationCache;
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
            if (!RUNTIME_LIFECYCLE.observeAndShouldCleanup(false)) {
                return;
            }
            closeExtractionFrame(true);
            disableAfterFrameClosed();
            return;
        }
        RUNTIME_LIFECYCLE.observeAndShouldCleanup(true);
        closeExtractionFrame(true);
        if (!FAIL_OPEN_LATCH.canRun()) {
            STATISTICS_GATE.disable();
            active = false;
            frameReady = false;
            return;
        }
        try {
            ensureFrameStorage();
            if (currentLevel != level) {
                clearForLevelAfterFrameClosed(level);
            }
            STATISTICS_GATE.refresh(
                    true,
                    CONFIG.isBerpShowInspectorStatistics()
                            && InspectorStatisticsDemand.isRequested()
            );
            BerpRuntimeSettings settings = FRAME_RUNTIME.capture(
                    CONFIG,
                    STATISTICS_GATE.shouldCollect()
            );
            cameraPosition = camera.position();
            long gameTick = level == null ? 0L : level.getGameTime();
            HitResult hitResult = Minecraft.getInstance().hitResult;
            if (hitResult instanceof BlockHitResult blockHitResult) {
                hasTarget = true;
                targetPosition = blockHitResult.getBlockPos().asLong();
            } else {
                hasTarget = false;
            }
            hasRecentInteraction = settings.recentInteractionGrace()
                    && level != null
                    && gameTick <= recentInteractionUntilTick;
            if (!settings.cacheFeaturesEnabled()) {
                clearCache();
            } else if (cache != null) {
                cache.shrinkTo(settings.cacheCapacity());
                int expired = cache.expire(
                        gameTick,
                        settings.cacheTtlTicks(MINIMUM_CACHE_TTL_TICKS),
                        CACHE_SWEEP_PER_FRAME
                );
                if (settings.collectStatistics()) {
                    STATS.addCacheExpirations(expired);
                }
            }
            active = true;
            frameReady = true;
            FRAME_RUNTIME.activate(settings);
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
        BerpRuntimeSettings settings = FRAME_RUNTIME.settings();
        if (!settings.active() || !active || !frameReady) {
            return dispatcher.tryExtractRenderState(blockEntity, partialTick, crumblingOverlay, offscreen);
        }
        boolean collectStatistics = settings.collectStatistics();
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
                    settings.includeModdedBlockEntities()
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
            double farDistance = settings.farRenderDistance();
            if (BlockEntityCadenceLogic.shouldCullBeyondFar(
                    settings.cullBeyondFarDistance(),
                    vanillaType,
                    protectedState,
                    distanceSquared,
                    farDistance * farDistance
            )) {
                if (collectStatistics) {
                    STATS.addFarCull(blockEntity.getType());
                }
                return null;
            }

            allowCaching = settings.cacheFeaturesEnabled();
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
                        settings.cacheTtlTicks(MINIMUM_CACHE_TTL_TICKS)
                );
                lastFreshTick = cache.lastFreshTickForLookup();
            }
            int near = settings.nearDistance();
            int medium = settings.mediumDistance();
            boolean fresh = BlockEntityCadenceLogic.shouldExtractFresh(
                    true,
                    protectedState,
                    settings.distanceAwareStateUpdates(),
                    allowCaching,
                    cached != null,
                    distanceSquared,
                    (double) near * near,
                    (double) medium * medium,
                    gameTick,
                    lastFreshTick,
                    settings.mediumUpdateInterval(),
                    settings.farUpdateInterval()
            );
            if (!fresh && cached != null) {
                if (collectStatistics) {
                    STATS.addCacheHit(blockEntity.getType());
                }
                return cached;
            }
        } catch (RuntimeException | LinkageError exception) {
            failOpen(exception);
            return dispatcher.tryExtractRenderState(blockEntity, partialTick, crumblingOverlay, offscreen);
        }

        // Renderer code runs outside the scheduler catch. If a renderer fails,
        // preserve Minecraft's normal crash/reporting behavior and never invoke it twice.
        BlockEntityRenderState extracted;
        try {
            extracted = dispatcher.tryExtractRenderState(
                    blockEntity,
                    partialTick,
                    crumblingOverlay,
                    offscreen
            );
        } catch (RuntimeException | LinkageError rendererFailure) {
            closeExtractionFrame(true);
            throw rendererFailure;
        }
        try {
            if (collectStatistics) {
                STATS.addFreshExtraction();
            }
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
                        Math.min(CACHE_TABLE_MAXIMUM, settings.cacheCapacity())
                );
                if (collectStatistics && result == BoundedBlockEntityStateCache.PutResult.EVICTED) {
                    STATS.addCacheEviction();
                } else if (collectStatistics
                        && result == BoundedBlockEntityStateCache.PutResult.SATURATED) {
                    STATS.markSaturated();
                }
            }
        } catch (RuntimeException | LinkageError exception) {
            failOpen(exception);
        }
        return extracted;
    }

    public static void onLevelChanged(ClientLevel level) {
        if (!isEnabled() && !RUNTIME_LIFECYCLE.observedEnabled()) {
            return;
        }
        clearForLevel(level);
    }

    public static void onResourceReload() {
        closeExtractionFrame(true);
        STATISTICS_GATE.disable();
        clearCache();
        active = false;
        frameReady = false;
        FAIL_OPEN_LATCH.resetForLifecycle();
        releaseFrameStorage();
        RUNTIME_LIFECYCLE.reset();
    }

    public static StatisticsSnapshot snapshotStatistics() {
        if (!isEnabled() || !CONFIG.isBerpShowInspectorStatistics()
                || !InspectorStatisticsDemand.isRequested()) {
            return StatisticsSnapshot.EMPTY;
        }
        return STATS.snapshot(MAXIMUM_INSPECTOR_TYPES, cache == null ? 0 : cache.size());
    }

    private static void finishExtraction(LevelExtractionContext context) {
        if (!BerpHotPathGate.extractionActive()) {
            return;
        }
        BerpRuntimeSettings settings = FRAME_RUNTIME.settings();
        if (!settings.active() || !active || !frameReady) {
            closeExtractionFrame(true);
            return;
        }
        List<BlockEntityRenderState> states = context.levelState().blockEntityRenderStates;
        if (states.isEmpty()) {
            closeExtractionFrame(true);
            return;
        }
        try {
            boolean collectStatistics = settings.collectStatistics();
            boolean prioritizeNearby = settings.prioritizeNearby();
            int globalBudget = settings.globalRenderBudget();
            boolean perTypeLimits = settings.perTypeRenderLimits();
            int perTypeLimit = settings.perTypeRenderLimit();
            boolean budgetsCannotBind = BlockEntityPlanningBounds.budgetsCannotBind(
                    states.size(),
                    globalBudget,
                    perTypeLimits,
                    perTypeLimit
            );
            if (budgetsCannotBind) {
                // Protected statistics count absolute quota admissions. With no
                // possible quota pressure, no protection slot is consumed.
                frameDecisions.releaseFrame();
                completeGuardHandoffIfNeeded(states, context, false);
            } else {
                frameDecisions.nextFrame();
                FRAME_QUOTAS.reset();
                framePlanning.begin();
                int rawPrefixSize = BlockEntityPlanningBounds.rawPrefixSize(
                        states.size(),
                        MAXIMUM_STATES_PLANNED
                );
                boolean truncated = BlockEntityPlanningBounds.hasFailOpenOverflow(
                        states.size(),
                        MAXIMUM_STATES_PLANNED
                );
                Iterator<BlockEntityRenderState> planningIterator = states.iterator();
                for (int rawIndex = 0;
                     rawIndex < rawPrefixSize && planningIterator.hasNext();
                     rawIndex++) {
                    BlockEntityRenderState state = planningIterator.next();
                    if (validForBudget(state)) {
                        BlockEntityBudgetQuotas.Priority priority = priorityOf(state, settings);
                    if (!framePlanning.add(state, priority.ordinal())) {
                            truncated = true;
                            break;
                        }
                    }
                }

                boolean rejectedAny = false;
                boolean decisionSaturated = false;
                if (prioritizeNearby) {
                    planning:
                    for (BlockEntityBudgetQuotas.Priority priority : PLANNING_PRIORITIES) {
                        for (int index = framePlanning.firstInPriority(priority.ordinal());
                             index >= 0;
                             index = framePlanning.nextInPriority(index)) {
                            int result = planState(
                                    framePlanning.valueAt(index),
                                    priority,
                                    globalBudget,
                                    perTypeLimits,
                                    perTypeLimit,
                                    collectStatistics
                            );
                            rejectedAny |= (result & PLAN_REJECTED) != 0;
                            if ((result & PLAN_SATURATED) != 0) {
                                decisionSaturated = true;
                                break planning;
                            }
                        }
                    }
                } else {
                    BlockEntityBudgetQuotas.Priority target =
                            BlockEntityBudgetQuotas.Priority.TARGET_OR_RECENT;
                    for (int index = framePlanning.firstInPriority(target.ordinal());
                         index >= 0;
                         index = framePlanning.nextInPriority(index)) {
                        int result = planState(
                                framePlanning.valueAt(index),
                                target,
                                globalBudget,
                                perTypeLimits,
                                perTypeLimit,
                                collectStatistics
                        );
                        rejectedAny |= (result & PLAN_REJECTED) != 0;
                        if ((result & PLAN_SATURATED) != 0) {
                            decisionSaturated = true;
                            break;
                        }
                    }
                    if (!decisionSaturated) {
                        for (int index = 0; index < framePlanning.size(); index++) {
                            BlockEntityBudgetQuotas.Priority priority =
                                    PLANNING_PRIORITIES[framePlanning.priorityAt(index)];
                            if (priority == target) {
                                continue;
                            }
                            int result = planState(
                                    framePlanning.valueAt(index),
                                    priority,
                                    globalBudget,
                                    perTypeLimits,
                                    perTypeLimit,
                                    collectStatistics
                            );
                            rejectedAny |= (result & PLAN_REJECTED) != 0;
                            if ((result & PLAN_SATURATED) != 0) {
                                decisionSaturated = true;
                                break;
                            }
                        }
                    }
                }
                truncated |= decisionSaturated;
                if (collectStatistics && (truncated || FRAME_QUOTAS.isSaturated())) {
                    STATS.markSaturated();
                    if (truncated) {
                        STATS.addTruncatedFrame();
                    }
                }
                completeGuardHandoffIfNeeded(states, context, rejectedAny);
            }
        } catch (RuntimeException | LinkageError exception) {
            VoltGuardEngine.abortBlockEntityHandoff();
            failOpenAfterFrameClosed(exception);
        } finally {
            closeExtractionFrame(false);
        }
    }

    private static final int PLAN_REJECTED = 1;
    private static final int PLAN_SATURATED = 1 << 1;

    private static int planState(
            BlockEntityRenderState state,
            BlockEntityBudgetQuotas.Priority priority,
            int globalBudget,
            boolean perTypeLimits,
            int perTypeLimit,
            boolean collectStatistics
    ) {
        int decisionToken = frameDecisions.record(state);
        if (decisionToken == VapsIdentityDecisionTable.SATURATED_TOKEN) {
            return PLAN_SATURATED;
        }
        if (!VapsIdentityDecisionTable.wasInserted(decisionToken)) {
            return 0;
        }
        BlockEntityBudgetQuotas.Decision decision = FRAME_QUOTAS.trySelect(
                state.blockEntityType,
                globalBudget,
                perTypeLimits,
                perTypeLimit,
                priority
        );
        if (decision == BlockEntityBudgetQuotas.Decision.SELECTED
                || decision == BlockEntityBudgetQuotas.Decision.SELECTED_ABSOLUTE) {
            frameDecisions.selectRecorded(decisionToken);
            if (collectStatistics
                    && decision == BlockEntityBudgetQuotas.Decision.SELECTED_ABSOLUTE) {
                STATS.addProtected();
            }
            return 0;
        }
        if (collectStatistics) {
            if (decision == BlockEntityBudgetQuotas.Decision.GLOBAL_LIMIT) {
                STATS.addGlobalLimit(state.blockEntityType);
            } else {
                STATS.addPerTypeLimit(state.blockEntityType);
            }
        }
        return PLAN_REJECTED;
    }

    private static void completeGuardHandoffIfNeeded(
            List<BlockEntityRenderState> states,
            LevelExtractionContext context,
            boolean removeRejected
    ) {
        boolean guardHandoffStarted = VoltGuardEngine.beginBlockEntityHandoff(
                states,
                context.camera().position(),
                Minecraft.getInstance().hitResult
        );
        if (!removeRejected && !guardHandoffStarted) {
            return;
        }
        int survivorIndex = 0;
        Iterator<BlockEntityRenderState> iterator = states.iterator();
        while (iterator.hasNext()) {
            BlockEntityRenderState state = iterator.next();
            int decision = removeRejected
                    ? frameDecisions.decision(state)
                    : VapsIdentityDecisionTable.UNKNOWN;
            if (decision == VapsIdentityDecisionTable.SCANNED) {
                iterator.remove();
            } else {
                if (guardHandoffStarted) {
                    VoltGuardEngine.offerBlockEntitySurvivor(state, survivorIndex);
                }
                survivorIndex++;
            }
        }
        if (guardHandoffStarted) {
            VoltGuardEngine.completeBlockEntityHandoff(states);
        }
    }

    private static BlockEntityBudgetQuotas.Priority priorityOf(
            BlockEntityRenderState state,
            BerpRuntimeSettings settings
    ) {
        long position = state.blockPos.asLong();
        if (isTargetOrRecent(position)) {
            return BlockEntityBudgetQuotas.Priority.TARGET_OR_RECENT;
        }
        if (state.breakProgress != null) {
            return BlockEntityBudgetQuotas.Priority.BREAKING;
        }
        double distanceSquared = distanceSquared(state.blockPos, cameraPosition);
        double near = settings.nearDistance();
        if (!Double.isFinite(distanceSquared) || distanceSquared <= near * near) {
            return BlockEntityBudgetQuotas.Priority.NEAR;
        }
        double medium = settings.mediumDistance();
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
        if (type == null) {
            return false;
        }
        BlockEntityTypeClassificationCache<BlockEntityType<?>> classifications =
                typeClassificationCache;
        if (classifications == null) {
            int capacity = Math.max(
                    MINIMUM_TYPE_CLASSIFICATION_CAPACITY,
                    BuiltInRegistries.BLOCK_ENTITY_TYPE.size()
            );
            classifications = new BlockEntityTypeClassificationCache<>(capacity);
            typeClassificationCache = classifications;
        }
        return classifications.isVanilla(type, VANILLA_TYPE_CLASSIFIER);
    }

    private static boolean classifyVanillaType(BlockEntityType<?> type) {
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

    private static void disableAfterFrameClosed() {
        STATISTICS_GATE.disable();
        // Observing the feature disabled is the explicit retry boundary after a
        // fail-open. Reset this even when there is no active/cache state left.
        FAIL_OPEN_LATCH.observeDisabled();
        hasTarget = false;
        hasRecentInteraction = false;
        active = false;
        frameReady = false;
        clearCache();
        releaseFrameStorage();
    }

    private static void clearForLevel(ClientLevel level) {
        closeExtractionFrame(true);
        clearForLevelAfterFrameClosed(level);
    }

    private static void clearForLevelAfterFrameClosed(ClientLevel level) {
        STATISTICS_GATE.disable();
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

    private static void closeExtractionFrame(boolean abortGuardHandoff) {
        frameReady = false;
        FRAME_RUNTIME.close();
        if (frameDecisions != null) {
            frameDecisions.releaseFrame();
        }
        if (framePlanning != null) {
            framePlanning.clear();
        }
        if (abortGuardHandoff) {
            VoltGuardEngine.abortBlockEntityHandoff();
        }
    }

    private static void ensureFrameStorage() {
        if (frameDecisions == null) {
            frameDecisions = new VapsIdentityDecisionTable<>(DECISION_TABLE_SIZE);
        }
        if (framePlanning == null) {
            framePlanning = new BlockEntityPlanningWorkspace<>(
                    MAXIMUM_STATES_PLANNED,
                    PLANNING_PRIORITIES.length
            );
        }
    }

    private static void releaseFrameStorage() {
        frameDecisions = null;
        framePlanning = null;
    }

    private static void clearCache() {
        if (cache != null) {
            cache.clear();
            cache = null;
        }
    }

    private static void failOpen(Throwable exception) {
        closeExtractionFrame(true);
        failOpenAfterFrameClosed(exception);
    }

    private static void failOpenAfterFrameClosed(Throwable exception) {
        STATISTICS_GATE.disable();
        clearCache();
        releaseFrameStorage();
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
        private final BoundedIdentityIndex<BlockEntityType<?>> types =
                new BoundedIdentityIndex<>(MAXIMUM_STAT_TYPES);
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
            int index = this.types.index(type);
            if (index < 0) {
                this.saturated = true;
            }
            return index;
        }

        private StatisticsSnapshot snapshot(int maximumTypes, int cacheSize) {
            ArrayList<TypeStatistic> top = new ArrayList<>(maximumTypes);
            boolean[] selected = new boolean[MAXIMUM_STAT_TYPES];
            int typeCount = this.types.size();
            for (int output = 0; output < maximumTypes; output++) {
                int best = -1;
                long bestTotal = 0L;
                for (int index = 0; index < typeCount; index++) {
                    long total = saturatedAdd(this.limitedByType[index], this.cacheHitsByType[index]);
                    if (!selected[index] && total > bestTotal) {
                        best = index;
                        bestTotal = total;
                    }
                }
                if (best < 0) {
                    break;
                }
                selected[best] = true;
                top.add(new TypeStatistic(
                        sanitizedTypeLabel(this.types.keyAt(best)),
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
            for (int index = 0; index < MAXIMUM_STAT_TYPES; index++) {
                this.limitedByType[index] = 0L;
                this.cacheHitsByType[index] = 0L;
            }
            this.types.clear();
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
