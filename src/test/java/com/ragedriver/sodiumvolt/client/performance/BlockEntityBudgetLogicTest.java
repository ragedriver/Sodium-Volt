package com.ragedriver.sodiumvolt.client.performance;

public final class BlockEntityBudgetLogicTest {
	private BlockEntityBudgetLogicTest() {
	}

	public static void main(String[] arguments) {
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
		testAdversarialRawPlanningBounds();
		testSaturatingCountersAndTickRollback();
		System.out.println("Block Entity Render-budgeting logic tests passed");
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
			check(decisions.addScanned(identity), "long-run frame decision insertion must remain valid");
			decisions.select(identity);
			check(decisions.isSelected(identity), "the current frame selection must remain visible");
		}
		decisions.releaseFrame();
		check(decisions.occupiedCountForTesting() == 0
						&& !decisions.retainsReferenceForTesting(identity),
				"long-run frame generation must release every retained identity");
	}

	private static void testAdversarialRawPlanningBounds() {
		int adversarialSize = 1_000_000_000;
		int maximum = 8_192;
		check(BlockEntityPlanningBounds.rawPrefixSize(adversarialSize, maximum) == maximum,
				"adversarial lists must be reduced to a fixed raw prefix");
		check(BlockEntityPlanningBounds.maximumRawVisits(adversarialSize, 5, maximum) == 40_960L,
				"five priority passes must have a fixed raw-visit upper bound independent of N");
		check(BlockEntityPlanningBounds.maximumRawVisits(adversarialSize, 2, maximum) == 16_384L,
				"extraction-order planning must remain bounded to two fixed-prefix passes");
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

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
