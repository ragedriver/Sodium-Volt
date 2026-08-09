package com.ragedriver.sodiumvolt.client.performance;

public final class BlockEntityBudgetLogicTest {
    private BlockEntityBudgetLogicTest() {
    }

	public static void main(String[] arguments) {
		testDisabledRuntimeLifecycle();
        testDistanceNormalization();
        testModdedCompatibilityBoundary();
        testCacheFeatureGate();
        testFreshExtractionCadence();
        testCacheValidationAndExpiry();
        testCacheEvictionShrinkAndRelease();
        testGlobalAndPerTypeQuotas();
        testProtectedAndBreakingReserves();
        testDownstreamGuardComposition();
        testFailOpenRetryBoundary();
        testDecisionFramesAndLongRunRollover();
        testNormalLoadBudgetGate();
        testFrameRuntimeSnapshotLifecycle();
        testSinglePassPlanningWorkspace();
        testAdversarialRawPlanningBounds();
        testStatisticsCollectionGateTransitions();
        testTypeClassificationCache();
        testSaturatingCountersAndTickRollback();
        System.out.println("Block Entity Render-budgeting logic tests passed");
	}

	private static void testDisabledRuntimeLifecycle() {
		BerpRuntimeLifecycle lifecycle = new BerpRuntimeLifecycle();
		check(!lifecycle.observeAndShouldCleanup(false),
				"stable-disabled BERP extraction callbacks must remain dormant");
		check(!lifecycle.observeAndShouldCleanup(true) && lifecycle.observedEnabled(),
				"enabling BERP must enter one active lifecycle without cleanup");
		check(!lifecycle.observeAndShouldCleanup(true),
				"stable-enabled BERP frames must not emit cleanup transitions");
		check(lifecycle.observeAndShouldCleanup(false),
				"disabling BERP must emit exactly one storage cleanup boundary");
		check(!lifecycle.observeAndShouldCleanup(false),
				"later disabled extraction callbacks must remain allocation-free");
	}

    private static void testCacheFeatureGate() {
        check(BlockEntityCadenceLogic.shouldUseCache(true, true),
                "cache work may run only when both cache and distance-aware cadence are enabled");
        check(!BlockEntityCadenceLogic.shouldUseCache(true, false),
                "all-fresh mode must disable lookup, storage, sweeping, and retained cache references");
        check(!BlockEntityCadenceLogic.shouldUseCache(false, true),
                "the cache toggle must independently disable cache work");
    }

    private static void testModdedCompatibilityBoundary() {
        check(BlockEntityCadenceLogic.cadenceEligible(true, true, false),
                "vanilla block entities may use render-state cadence by default");
        check(!BlockEntityCadenceLogic.cadenceEligible(true, false, false),
                "modded block entities must extract fresh unless experimental cadence is opted in");
        check(BlockEntityCadenceLogic.cadenceEligible(true, false, true),
                "the experimental option must explicitly admit modded renderer cadence");
        check(!BlockEntityCadenceLogic.cadenceEligible(false, true, true),
                "removed, replaced, or wrong-world identities must never enter cadence/cache");
        check(!BlockEntityCadenceLogic.shouldCullBeyondFar(
                        true, false, false, 10_000.0D, 9_000.0D),
                "experimental modded cadence must not opt modded renderers into pre-extraction culling");
        check(!BlockEntityCadenceLogic.shouldCullBeyondFar(
                        true, true, true, 10_000.0D, 9_000.0D),
                "targeted, recent, and breaking vanilla block entities must bypass far culling");
        check(BlockEntityCadenceLogic.shouldCullBeyondFar(
                        true, true, false, 10_000.0D, 9_000.0D),
                "unprotected vanilla block entities beyond the boundary may be culled");
    }

    private static void testTypeClassificationCache() {
        BlockEntityTypeClassificationCache<Object> cache =
                new BlockEntityTypeClassificationCache<>(2);
        Object vanilla = new Object();
        Object modded = new Object();
        Object overflow = new Object();
        int[] resolutions = {0};
        BlockEntityTypeClassificationCache.Classifier<Object> classifier = type -> {
            resolutions[0]++;
            return type == vanilla;
        };

        check(cache.isVanilla(vanilla, classifier),
                "the type classifier must preserve vanilla classifications");
        check(cache.isVanilla(vanilla, classifier),
                "a cached vanilla identity must preserve its classification");
        check(!cache.isVanilla(modded, classifier),
                "the type classifier must preserve modded classifications");
        check(!cache.isVanilla(modded, classifier),
                "a cached modded identity must preserve its classification");
        check(resolutions[0] == 2 && cache.size() == 2,
                "each cached identity must invoke the registry classifier only once");

        check(!cache.isVanilla(overflow, classifier)
                        && !cache.isVanilla(overflow, classifier)
                        && resolutions[0] == 4,
                "a saturated cache must resolve uncached identities instead of guessing");

        BlockEntityTypeClassificationCache<Object> failureCache =
                new BlockEntityTypeClassificationCache<>(1);
        Object retry = new Object();
        int[] attempts = {0};
        boolean failed = false;
        try {
            failureCache.isVanilla(retry, type -> {
                attempts[0]++;
                throw new IllegalStateException("simulated registry failure");
            });
        } catch (IllegalStateException expected) {
            failed = true;
        }
        check(failed && failureCache.size() == 0,
                "a failed registry lookup must not poison the classification cache");
        check(failureCache.isVanilla(retry, type -> {
                    attempts[0]++;
                    return true;
                }) && attempts[0] == 2,
                "classification must retry successfully after a fail-open lookup failure");
    }

    private static void testDistanceNormalization() {
        BlockEntityBudgetNormalization.Distances defaults =
                BlockEntityBudgetNormalization.normalizeDistances(24, 48, 96);
        check(defaults.near() == 24 && defaults.medium() == 48 && defaults.far() == 96,
                "valid distance defaults must remain unchanged");

        BlockEntityBudgetNormalization.Distances crossed =
                BlockEntityBudgetNormalization.normalizeDistances(100, 10, 50);
        check(crossed.near() == 64 && crossed.medium() == 64 && crossed.far() == 64,
                "saved distances must clamp and normalize to near <= medium <= far");

        BlockEntityBudgetNormalization.Distances minimums =
                BlockEntityBudgetNormalization.normalizeDistances(-100, -100, -100);
        check(minimums.near() == 8 && minimums.medium() == 24 && minimums.far() == 48,
                "invalid low distances must use the configured lower bounds");
    }

    private static void testFreshExtractionCadence() {
        check(BlockEntityCadenceLogic.shouldExtractFresh(
                        false, false, true, true, true,
                        10_000.0D, 576.0D, 2_304.0D, 100L, 99L, 3, 8),
                "disabled scheduling must fail open to a fresh renderer extraction");
        check(BlockEntityCadenceLogic.shouldExtractFresh(
                        true, true, true, true, true,
                        10_000.0D, 576.0D, 2_304.0D, 100L, 99L, 3, 8),
                "targets, recent interactions, and breaking overlays must stay fresh");
        check(BlockEntityCadenceLogic.shouldExtractFresh(
                        true, false, true, true, true,
                        576.0D, 576.0D, 2_304.0D, 100L, 99L, 3, 8),
                "the near-distance boundary must stay fresh");
        check(BlockEntityCadenceLogic.shouldExtractFresh(
                        true, false, true, true, false,
                        1_000.0D, 576.0D, 2_304.0D, 100L, 99L, 3, 8),
                "a cache miss must always extract fresh");
        check(!BlockEntityCadenceLogic.shouldExtractFresh(
                        true, false, true, true, true,
                        1_000.0D, 576.0D, 2_304.0D, 101L, 100L, 3, 8),
                "a medium cached state must be reused before its interval");
        check(BlockEntityCadenceLogic.shouldExtractFresh(
                        true, false, true, true, true,
                        1_000.0D, 576.0D, 2_304.0D, 103L, 100L, 3, 8),
                "a medium cached state must refresh when its interval is due");
        check(!BlockEntityCadenceLogic.shouldExtractFresh(
                        true, false, true, true, true,
                        3_000.0D, 576.0D, 2_304.0D, 107L, 100L, 3, 8),
                "a far cached state must use the farther cadence");
        check(BlockEntityCadenceLogic.shouldExtractFresh(
                        true, false, true, true, true,
                        3_000.0D, 576.0D, 2_304.0D, 108L, 100L, 3, 8),
                "a far cached state must refresh at the selected interval");
    }

    private static void testCacheValidationAndExpiry() {
        BoundedBlockEntityStateCache<Object, Object, Object, Object> cache =
                new BoundedBlockEntityStateCache<>();
        Object entity = new Object();
        Object state = new Object();
        Object type = new Object();
        Object blockState = new Object();
        check(cache.put(1L, entity, state, type, blockState, 10L, 4)
                        == BoundedBlockEntityStateCache.PutResult.STORED,
                "a valid fresh state must enter the fixed cache");
        check(cache.lookup(1L, entity, type, blockState, 11L, 40) == state
                        && cache.lastFreshTickForLookup() == 10L,
                "matching identity, type, and block state must produce a cache hit");
        check(cache.lookup(1L, new Object(), type, blockState, 12L, 40) == null,
                "entity replacement at the same position must reject the stale state");
        check(cache.size() == 0 && !cache.retainsForTesting(entity, state),
                "identity mismatch must release stale entity and render-state references");

        Object secondEntity = new Object();
        Object secondState = new Object();
        cache.put(2L, secondEntity, secondState, type, blockState, 20L, 4);
        check(cache.lookup(2L, secondEntity, new Object(), blockState, 21L, 40) == null,
                "a changed block-entity type must invalidate the cache entry");

        Object thirdEntity = new Object();
        Object thirdState = new Object();
        cache.put(3L, thirdEntity, thirdState, type, blockState, 30L, 4);
        check(cache.lookup(3L, thirdEntity, type, new Object(), 31L, 40) == null,
                "a changed block state must invalidate the cache entry");

        Object expiringEntity = new Object();
        Object expiringState = new Object();
        cache.put(4L, expiringEntity, expiringState, type, blockState, 40L, 4);
        check(cache.lookup(4L, expiringEntity, type, blockState, 81L, 40) == null,
                "a cache entry older than its TTL must expire on lookup");
        check(!cache.retainsForTesting(expiringEntity, expiringState),
                "TTL expiry must release strong references immediately");
    }

    private static void testCacheEvictionShrinkAndRelease() {
        BoundedBlockEntityStateCache<Object, Object, Object, Object> cache =
                new BoundedBlockEntityStateCache<>();
        Object type = new Object();
        Object blockState = new Object();
        Object firstEntity = new Object();
        Object firstState = new Object();
        Object secondEntity = new Object();
        Object secondState = new Object();
        Object thirdEntity = new Object();
        Object thirdState = new Object();
        Object fourthEntity = new Object();
        Object fourthState = new Object();

        cache.put(1L, firstEntity, firstState, type, blockState, 0L, 2);
        cache.put(2L, secondEntity, secondState, type, blockState, 0L, 2);
        check(cache.put(3L, thirdEntity, thirdState, type, blockState, 0L, 2)
                        == BoundedBlockEntityStateCache.PutResult.EVICTED,
                "capacity pressure must use bounded eviction");
        check(!cache.retainsForTesting(firstEntity, firstState),
                "the first round-robin victim must be released");
        cache.put(4L, fourthEntity, fourthState, type, blockState, 0L, 2);
        check(!cache.retainsForTesting(secondEntity, secondState)
                        && cache.retainsForTesting(thirdEntity, thirdState)
                        && cache.retainsForTesting(fourthEntity, fourthState),
                "round-robin eviction must advance instead of repeatedly replacing one slot");

        Object replacementEntity = new Object();
        Object replacementState = new Object();
        cache.put(3L, replacementEntity, replacementState, type, blockState, 1L, 2);
        check(!cache.retainsForTesting(thirdEntity, thirdState)
                        && cache.retainsForTesting(replacementEntity, replacementState),
                "same-position replacement must overwrite and release prior references");

        cache.shrinkTo(1);
        check(cache.size() == 1, "lowering capacity must synchronously release excess entries");
        int expired = cache.expire(100L, 10, 16);
        check(expired == 1 && cache.size() == 0,
                "bounded sweeping must expire unused entries without waiting for GC");

        cache.put(8L, firstEntity, firstState, type, blockState, 100L, 4);
        cache.clear();
        check(cache.size() == 0 && !cache.retainsForTesting(firstEntity, firstState),
                "world, reload, disable, and failure clears must release all cache references");
        cache.put(9L, secondEntity, secondState, type, blockState, 101L, 4);
        check(cache.lookup(9L, secondEntity, type, blockState, 101L, 40) == secondState,
                "cache must remain reusable after clearing tombstones");
    }

    private static void testGlobalAndPerTypeQuotas() {
        BlockEntityBudgetQuotas quotas = new BlockEntityBudgetQuotas();
        Object type = new Object();
        quotas.reset();
        check(quotas.trySelect(type, 2, false, 1, BlockEntityBudgetQuotas.Priority.NEAR)
                        == BlockEntityBudgetQuotas.Decision.SELECTED,
                "the first normal state must receive global capacity");
        check(quotas.trySelect(type, 2, false, 1, BlockEntityBudgetQuotas.Priority.FAR)
                        == BlockEntityBudgetQuotas.Decision.SELECTED,
                "normal states may fill the global budget");
        check(quotas.trySelect(type, 2, false, 1, BlockEntityBudgetQuotas.Priority.FAR)
                        == BlockEntityBudgetQuotas.Decision.GLOBAL_LIMIT,
                "the global budget must reject normal overflow");

        quotas.reset();
        check(quotas.trySelect(type, 10, true, 1, BlockEntityBudgetQuotas.Priority.NEAR)
                        == BlockEntityBudgetQuotas.Decision.SELECTED,
                "the first state of a type must receive its quota");
        check(quotas.trySelect(type, 10, true, 1, BlockEntityBudgetQuotas.Priority.MEDIUM)
                        == BlockEntityBudgetQuotas.Decision.PER_TYPE_LIMIT,
                "per-type overflow must be limited");
        check(quotas.trySelect(new Object(), 10, true, 1, BlockEntityBudgetQuotas.Priority.FAR)
                        == BlockEntityBudgetQuotas.Decision.SELECTED,
                "a different type must receive independent capacity");
    }

    private static void testProtectedAndBreakingReserves() {
        BlockEntityBudgetQuotas quotas = new BlockEntityBudgetQuotas();
        Object type = new Object();
        quotas.reset();
        check(quotas.trySelect(type, 1, true, 1, BlockEntityBudgetQuotas.Priority.TARGET_OR_RECENT)
                        == BlockEntityBudgetQuotas.Decision.SELECTED_ABSOLUTE,
                "the current target must receive bounded absolute capacity");
        check(quotas.trySelect(type, 1, true, 1, BlockEntityBudgetQuotas.Priority.TARGET_OR_RECENT)
                        == BlockEntityBudgetQuotas.Decision.SELECTED_ABSOLUTE,
                "target and recent interaction may use at most two absolute slots");
        check(quotas.trySelect(type, 1, true, 1, BlockEntityBudgetQuotas.Priority.TARGET_OR_RECENT)
                        == BlockEntityBudgetQuotas.Decision.SELECTED,
                "excess protected labels must rejoin the normal bounded quota");
        check(quotas.trySelect(type, 1, true, 1, BlockEntityBudgetQuotas.Priority.TARGET_OR_RECENT)
                        == BlockEntityBudgetQuotas.Decision.GLOBAL_LIMIT,
                "protected-state spam must not bypass the global budget indefinitely");

        quotas.reset();
        for (int index = 0; index < 16; index++) {
            check(quotas.trySelect(type, 32, true, 1, BlockEntityBudgetQuotas.Priority.BREAKING)
                            == BlockEntityBudgetQuotas.Decision.SELECTED,
                    "the fixed breaking-overlay reserve must keep active overlays renderable");
        }
        check(quotas.trySelect(type, 32, true, 1, BlockEntityBudgetQuotas.Priority.BREAKING)
                        == BlockEntityBudgetQuotas.Decision.SELECTED,
                "breaking overflow must receive ordinary per-type capacity when available");
        check(quotas.trySelect(type, 32, true, 1, BlockEntityBudgetQuotas.Priority.BREAKING)
                        == BlockEntityBudgetQuotas.Decision.PER_TYPE_LIMIT,
                "breaking overlay spam beyond the reserve must obey normal quotas");
    }

    private static void testDownstreamGuardComposition() {
        BlockEntityBudgetQuotas quotas = new BlockEntityBudgetQuotas();
        quotas.reset();
        int survivors = 0;
        for (int index = 0; index < 8; index++) {
            BlockEntityBudgetQuotas.Decision decision = quotas.trySelect(
                    new Object(),
                    5,
                    false,
                    1,
                    BlockEntityBudgetQuotas.Priority.NEAR
            );
            if (decision == BlockEntityBudgetQuotas.Decision.SELECTED) {
                survivors++;
            }
        }
        int stricterGuardBudget = 3;
        check(survivors == 5 && Math.min(survivors, stricterGuardBudget) == 3,
                "pre-Guard budgeting must compose so Guard can further limit only the survivors");
    }

    private static void testFailOpenRetryBoundary() {
        BlockEntityFailOpenLatch latch = new BlockEntityFailOpenLatch();
        check(latch.canRun(), "a new scheduler latch must allow operation");
        latch.fail();
        check(!latch.canRun(), "a runtime scheduler failure must disable the whole engine");
        latch.observeDisabled();
        check(latch.canRun(),
                "fail -> disabled frame -> re-enable must create a safe retry boundary");
        latch.fail();
        latch.resetForLifecycle();
        check(latch.canRun(), "world or resource lifecycle reset must allow a new attempt");
    }

    private static void testDecisionFramesAndLongRunRollover() {
        VapsIdentityDecisionTable<Object> decisions = new VapsIdentityDecisionTable<>(16);
        Object identity = new Object();
        for (int frame = 0; frame < 100_000; frame++) {
            decisions.nextFrame();
            int token = decisions.record(identity);
            check(token != VapsIdentityDecisionTable.SATURATED_TOKEN,
                    "long-run frame decision insertion must remain valid");
            decisions.selectRecorded(token);
            check(decisions.decision(identity) == VapsIdentityDecisionTable.SELECTED,
                    "the current frame selection must remain visible");
        }
        decisions.releaseFrame();
        check(decisions.occupiedCountForTesting() == 0
                        && !decisions.retainsReferenceForTesting(identity),
                "long-run frame generation must release every retained identity");
    }

    private static void testNormalLoadBudgetGate() {
        check(BlockEntityPlanningBounds.budgetsCannotBind(128, 128, false, 1),
                "the global-budget boundary must skip classification and removal work");
        check(!BlockEntityPlanningBounds.budgetsCannotBind(129, 128, false, 1),
                "one state beyond the global budget must retain exact planning");
        check(BlockEntityPlanningBounds.budgetsCannotBind(32, 128, true, 32),
                "a total upper bound at the per-type limit proves every type is safe");
        check(!BlockEntityPlanningBounds.budgetsCannotBind(33, 128, true, 32),
                "a total upper bound beyond the per-type limit cannot prove normal load");
    }

    private static void testFrameRuntimeSnapshotLifecycle() {
        BerpFrameRuntimeState runtime = new BerpFrameRuntimeState();
        runtime.close();
        BerpRuntimeSettings captured = new BerpRuntimeSettings(
                true, true, true, true, true, true, true, true,
                false, 24, 48, 3, 8, 96, 256, 32, 2_048
        );
        BerpRuntimeSettings laterConfigValues = new BerpRuntimeSettings(
                true, false, false, false, false, false, false, false,
                true, 8, 24, 1, 2, 48, 64, 8, 512
        );
        int[] routeCalls = new int[2];
        BerpExtractionRouter.ExtractionPath<Object, Object, Object, String> backendPath =
                (dispatcher, blockEntity, partialTick, overlay, offscreen) -> {
                    routeCalls[0]++;
                    return "backend";
                };
        BerpExtractionRouter.ExtractionPath<Object, Object, Object, String> vanillaPath =
                (dispatcher, blockEntity, partialTick, overlay, offscreen) -> {
                    routeCalls[1]++;
                    return "vanilla";
                };

        String disabledResult = BerpExtractionRouter.routeWithGate(
                new Object(), new Object(), 0.25F, new Object(), false,
                backendPath, vanillaPath
        );
        check("vanilla".equals(disabledResult)
                        && routeCalls[0] == 0
                        && routeCalls[1] == 1,
                "a disabled redirect gate must invoke vanilla extraction exactly once");

        runtime.activate(captured);
        check(BerpHotPathGate.extractionActive(),
                "activating a prepared frame must publish its redirect gate");
        check(runtime.settings() == captured && runtime.settings() != laterConfigValues,
                "extraction and finish planning must retain one immutable frame snapshot");
        String activeResult = BerpExtractionRouter.routeWithGate(
                new Object(), new Object(), 0.75F, new Object(), true,
                backendPath, vanillaPath
        );
        check("backend".equals(activeResult)
                        && routeCalls[0] == 1
                        && routeCalls[1] == 1,
                "an active redirect gate must invoke the BERP backend exactly once");

        runtime.close();
        check(!BerpHotPathGate.extractionActive()
                        && runtime.settings() == BerpRuntimeSettings.DISABLED,
                "normal-load and lifecycle cleanup must close both gate and frame settings");
        runtime.close();
        check(!BerpHotPathGate.extractionActive()
                        && runtime.settings() == BerpRuntimeSettings.DISABLED,
                "frame cleanup must remain idempotent on stale and failure paths");

		BerpRuntimeSettings firstReusableFrame = runtime.capture(captured);
		runtime.activate(firstReusableFrame);
		boolean rejectedMidFrameMutation = false;
		try {
			runtime.capture(laterConfigValues);
		} catch (IllegalStateException expected) {
			rejectedMidFrameMutation = true;
		}
		check(rejectedMidFrameMutation && runtime.settings() == firstReusableFrame,
				"BERP must reject settings mutation while an extraction cycle is active");
		runtime.close();
		BerpRuntimeSettings secondReusableFrame = runtime.capture(laterConfigValues);
		check(firstReusableFrame == secondReusableFrame
					&& !secondReusableFrame.collectStatistics()
					&& secondReusableFrame.nearDistance() == 8
					&& !BerpHotPathGate.extractionActive(),
				"stable BERP frames must reuse storage and keep the gate closed until capture completes");
		runtime.activate(secondReusableFrame);
		check(BerpHotPathGate.extractionActive() && runtime.settings() == secondReusableFrame,
				"BERP must publish the fully transitioned settings before opening its extraction gate");
		runtime.close();

        boolean rejectedDisabled = false;
        try {
            runtime.activate(BerpRuntimeSettings.DISABLED);
        } catch (IllegalArgumentException expected) {
            rejectedDisabled = true;
        }
        check(rejectedDisabled && !BerpHotPathGate.extractionActive(),
                "a disconnected inactive snapshot must never open the extraction gate");
    }

    private static void testSinglePassPlanningWorkspace() {
        Object targetFirst = new Object();
        Object farFirst = new Object();
        Object near = new Object();
        Object targetSecond = new Object();
        Object farSecond = new Object();
        Object[] raw = {targetFirst, farFirst, near, targetSecond, farSecond};
        int[] priorities = {
                BlockEntityBudgetQuotas.Priority.TARGET_OR_RECENT.ordinal(),
                BlockEntityBudgetQuotas.Priority.FAR.ordinal(),
                BlockEntityBudgetQuotas.Priority.NEAR.ordinal(),
                BlockEntityBudgetQuotas.Priority.TARGET_OR_RECENT.ordinal(),
                BlockEntityBudgetQuotas.Priority.FAR.ordinal()
        };
        BlockEntityPlanningWorkspace<Object> workspace =
                new BlockEntityPlanningWorkspace<>(raw.length, 5);
        workspace.begin();
        int rawVisits = 0;
        for (int index = 0; index < raw.length; index++) {
            rawVisits++;
            check(workspace.add(raw[index], priorities[index]),
                    "bounded raw-prefix entries must fit reusable classification storage");
        }
        check(rawVisits == raw.length,
                "classification must visit each bounded raw entry exactly once, not two/five times");
        int targetIndex = workspace.firstInPriority(
                BlockEntityBudgetQuotas.Priority.TARGET_OR_RECENT.ordinal());
        check(workspace.valueAt(targetIndex) == targetFirst,
                "the first protected state must lead its priority bucket");
        targetIndex = workspace.nextInPriority(targetIndex);
        check(workspace.valueAt(targetIndex) == targetSecond
                        && workspace.nextInPriority(targetIndex) < 0,
                "priority buckets must retain extraction order without raw-list rescans");
        check(workspace.valueAt(1) == farFirst
                        && workspace.valueAt(2) == near
                        && workspace.valueAt(4) == farSecond,
                "the reusable array must retain original order for unprioritized processing");
        workspace.clear();
        for (Object value : raw) {
            check(!workspace.retainsReferenceForTesting(value),
                    "success, failure, and lifecycle clears must release planning references");
        }

        BlockEntityPlanningWorkspace<Object> bounded = new BlockEntityPlanningWorkspace<>(2, 5);
        Object first = new Object();
        Object second = new Object();
        Object failOpen = new Object();
        check(bounded.add(first, 0) && bounded.add(second, 1) && !bounded.add(failOpen, 2),
                "workspace saturation must reject excess classification without retaining it");
        check(!bounded.retainsReferenceForTesting(failOpen),
                "overflow entries must remain unclassified and fail-open");
        bounded.clear();
        check(!bounded.retainsReferenceForTesting(first)
                        && !bounded.retainsReferenceForTesting(second),
                "truncation cleanup must release the bounded prefix too");
    }

    private static void testAdversarialRawPlanningBounds() {
        int adversarialSize = 1_000_000_000;
        int maximum = 8_192;
        check(BlockEntityPlanningBounds.rawPrefixSize(adversarialSize, maximum) == maximum,
                "adversarial lists must be reduced to a fixed raw prefix");
        check(BlockEntityPlanningBounds.maximumRawPlanningVisits(adversarialSize, maximum) == 8_192L,
                "all priority modes must classify the bounded raw prefix exactly once");
        check(BlockEntityPlanningBounds.hasFailOpenOverflow(adversarialSize, maximum),
                "an oversized list must report fail-open overflow");
        check(!BlockEntityPlanningBounds.remainsFailOpen(maximum - 1, adversarialSize, maximum)
                        && BlockEntityPlanningBounds.remainsFailOpen(
                        maximum,
                        adversarialSize,
                        maximum
                )
                        && BlockEntityPlanningBounds.remainsFailOpen(
                        adversarialSize - 1,
                        adversarialSize,
                        maximum
                ),
                "only entries beyond the inspected prefix must remain unscanned/rendered fail-open");
    }

    private static void testSaturatingCountersAndTickRollback() {
        check(BlockEntityCadenceLogic.saturatingAdd(Long.MAX_VALUE - 2L, 10L) == Long.MAX_VALUE,
                "session counters must saturate instead of wrapping");
        check(BlockEntityCadenceLogic.saturatingAdd(7L, -1L) == 7L,
                "invalid negative counter increments must be ignored");
        check(BlockEntityCadenceLogic.elapsed(3L, Long.MAX_VALUE - 1L) == Long.MAX_VALUE,
                "world-time rollback and long rollover must force a safe fresh extraction");
        check(BlockEntityCadenceLogic.shouldExtractFresh(
                        true, false, true, true, true,
                        3_000.0D, 576.0D, 2_304.0D,
                        3L, Long.MAX_VALUE - 1L, 3, 8),
                "tick rollback must never reuse a potentially stale cached state");
    }

    private static void testStatisticsCollectionGateTransitions() {
        StatisticsCollectionGate gate = new StatisticsCollectionGate();
        InspectorStatisticsDemand.publish(false);
        gate.refresh(true, true && InspectorStatisticsDemand.isRequested());
        check(!gate.shouldCollect(),
                "hidden Inspector demand must keep detailed block-entity accounting dormant");
        InspectorStatisticsDemand.publish(true);
        gate.refresh(true, true && InspectorStatisticsDemand.isRequested());
        check(gate.shouldCollect(),
                "BERP must cache an enabled inspector-statistics decision at the frame boundary");

        long accumulated = 9L;
        gate.refresh(true, false);
        if (gate.shouldCollect()) {
            accumulated++;
        }
        check(accumulated == 9L,
                "a disabled statistics frame must not mutate retained counters");
        gate.refresh(true, true);
        if (gate.shouldCollect()) {
            accumulated++;
        }
        check(accumulated == 10L,
                "re-enabling statistics must resume from the preserved session totals");

        gate.refresh(false, true);
        check(!gate.shouldCollect(),
                "the inspector option must never collect while its engine is disabled");
        gate.disable();
        check(!gate.shouldCollect(), "failure and lifecycle cleanup must clear cached state");
        InspectorStatisticsDemand.publish(false);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
