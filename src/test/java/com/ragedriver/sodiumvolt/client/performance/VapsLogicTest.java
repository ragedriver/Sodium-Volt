package com.ragedriver.sodiumvolt.client.performance;

public final class VapsLogicTest {
	private VapsLogicTest() {
	}

	public static void main(String[] arguments) {
		testDisabledAndNearCadence();
		testFarCadence();
		testCriticalAlwaysFullRate();
		testAgeAndLifetimeBoundary();
		testCriticalReserveIsBounded();
		testPerTypeQuota();
		testAmbientCellQuota();
		testRenderPlanningFastPathBoundaries();
		testDirectNormalLoadVisibilityFiltering();
		testFrameStorageLifecycle();
		testIdentityDecisionFrames();
		testBoundedIdentityStatisticsIndex();
		testGuardEligibilityHandoffEliminatesRawRescans();
		testGuardEligibilityHandoffFailureLifecycle();
		testCycleSnapshotFastGates();
		testStatisticsCollectionGateTransitions();
		testSaturatingStatistics();
		System.out.println("VAPS logic tests passed");
	}

	private static void testDisabledAndNearCadence() {
		check(VapsDecisionLogic.shouldRunFullTick(false, false, 10_000.0D, 576.0D, 4, 1),
				"disabled scheduling must use the exact vanilla tick");
		check(VapsDecisionLogic.shouldRunFullTick(true, false, 576.0D, 576.0D, 4, 1),
				"particles on the full-rate boundary must tick");
	}

	private static void testFarCadence() {
		for (int age = 0; age < 12; age++) {
			boolean expected = age % 4 == 0;
			check(VapsDecisionLogic.shouldRunFullTick(true, false, 577.0D, 576.0D, 4, age) == expected,
					"far cadence must be deterministic from particle age");
		}
	}

	private static void testCriticalAlwaysFullRate() {
		for (int age = 0; age < 16; age++) {
			check(VapsDecisionLogic.shouldRunFullTick(true, true, 100_000.0D, 64.0D, 8, age),
					"critical particles must remain full-rate");
		}
	}

	private static void testAgeAndLifetimeBoundary() {
		long live = VapsDecisionLogic.ageOnlyStep(4, 5);
		check(VapsDecisionLogic.ageOnlyStepAge(live) == 5
					&& !VapsDecisionLogic.ageOnlyStepExpires(live),
				"age-only cadence must match vanilla post-increment lifetime semantics");
		long expired = VapsDecisionLogic.ageOnlyStep(5, 5);
		check(VapsDecisionLogic.ageOnlyStepAge(expired) == 6
					&& VapsDecisionLogic.ageOnlyStepExpires(expired),
				"particle must expire on the same tick as vanilla");
		long saturated =
				VapsDecisionLogic.ageOnlyStep(Integer.MAX_VALUE, Integer.MAX_VALUE);
		check(VapsDecisionLogic.ageOnlyStepAge(saturated) == Integer.MAX_VALUE
					&& VapsDecisionLogic.ageOnlyStepExpires(saturated),
				"the production packed age step must saturate without allocating a result object");
	}

	private static void testCriticalReserveIsBounded() {
		VapsFrameLimiter limiter = new VapsFrameLimiter();
		limiter.reset();
		check(limiter.tryCritical(2), "first critical slot should be accepted");
		check(limiter.tryCritical(2), "second critical slot should be accepted");
		check(!limiter.tryCritical(2), "critical reserve must have a hard cap");
	}

	private static void testPerTypeQuota() {
		VapsFrameLimiter limiter = new VapsFrameLimiter();
		limiter.reset();
		check(limiter.tryType(String.class, 2), "first type slot should be accepted");
		check(limiter.tryType(String.class, 2), "second type slot should be accepted");
		check(!limiter.tryType(String.class, 2), "per-type limit must reject excess entries");
		check(limiter.tryType(Integer.class, 2), "a distinct type must receive its own quota");
		limiter.reset();
		check(limiter.tryType(String.class, 2), "frame reset must restore type quota");
	}

	private static void testAmbientCellQuota() {
		VapsFrameLimiter limiter = new VapsFrameLimiter();
		limiter.reset();
		check(limiter.tryAmbientCell(String.class, 1, 2, 3, 1),
				"first ambient particle in a cell should render");
		check(!limiter.tryAmbientCell(String.class, 1, 2, 3, 1),
				"equivalent ambient particle in a full cell should coalesce");
		check(limiter.tryAmbientCell(String.class, 2, 2, 3, 1),
				"a neighboring cell must have independent capacity");
		check(limiter.tryAmbientCell(Integer.class, 1, 2, 3, 1),
				"a distinct implementation must have independent cell capacity");
	}

	private static void testRenderPlanningFastPathBoundaries() {
		check(VapsRenderFastPath.canSkipPlanning(
				false, false, 1, false, 1, Integer.MAX_VALUE),
				"no suppressing render filter must skip decision-frame work at any load");
		check(VapsRenderFastPath.canSkipPlanning(
				true, false, 1, false, 1,
				VapsRenderFastPath.NORMAL_LOAD_VISIBILITY_THRESHOLD),
				"normal-load visibility filtering must avoid a duplicate full traversal");
		check(!VapsRenderFastPath.canSkipPlanning(
				true, false, 1, false, 1,
				VapsRenderFastPath.NORMAL_LOAD_VISIBILITY_THRESHOLD + 1),
				"visibility filtering must activate immediately above its adaptive threshold");
		check(VapsRenderFastPath.canSkipPlanning(
				false, true, 64, false, 1, 64),
				"the per-type upper-bound boundary cannot reject a particle");
		check(!VapsRenderFastPath.canSkipPlanning(
				false, true, 64, false, 1, 65),
				"one particle beyond the per-type proof boundary must retain planning");
		check(VapsRenderFastPath.canSkipPlanning(
				false, false, 1, true, 8, 8),
				"the ambient-cell upper-bound boundary cannot coalesce a particle");
		check(!VapsRenderFastPath.canSkipPlanning(
				false, false, 1, true, 8, 9),
				"one particle beyond the ambient proof boundary must retain planning");
		check(VapsRenderFastPath.saturatingAdd(Integer.MAX_VALUE - 2, 10) == Integer.MAX_VALUE,
				"adversarial group sizes must saturate instead of wrapping into a fast path");
	}

	private static void testFrameStorageLifecycle() {
		VapsLazyState<Object> state = new VapsLazyState<>(Object::new);
		Object first = state.acquire();
		check(first != null && state.peek() == first,
				"planning storage must be created lazily on first demand");
		check(state.release() == first && state.peek() == null,
				"durable disable/level transitions must release large planning arrays");
		check(state.acquire() != first,
				"re-enable must recreate planning storage lazily");
		state.release();
	}

	private static void testDirectNormalLoadVisibilityFiltering() {
		VapsDirectVisibilityState direct = new VapsDirectVisibilityState();
		direct.begin(true, 1, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, 1.0F);
		Object behind = new Object();
		Object front = new Object();
		Object critical = new Object();
		check(!direct.shouldRender(behind, false, 0.0D, 0.0D, -1.0D),
				"one behind-camera normal-load particle must be rejected in Sodium's traversal");
		check(direct.shouldRender(front, false, 0.0D, 0.0D, 1.0D),
				"a front-facing particle must remain visible");
		check(direct.shouldRender(critical, true, 0.0D, 0.0D, -2.0D)
					&& direct.shouldRender(critical, true, 0.0D, 0.0D, -2.0D),
				"a critical particle inside the reserve must remain forced visible");
		check(!direct.shouldRender(new Object(), true, 0.0D, 0.0D, -3.0D),
				"critical forcing must retain the configured reserve boundary");
		direct.disable();
		check(direct.shouldRender(behind, false, 0.0D, 0.0D, -1.0D),
				"ending extraction must restore the fail-open path");
	}

	private static void testIdentityDecisionFrames() {
		boolean rejectedAliasingCapacity = false;
		try {
			new VapsIdentityDecisionTable<>(1 << 30);
		} catch (IllegalArgumentException expected) {
			rejectedAliasingCapacity = true;
		}
		check(rejectedAliasingCapacity,
				"decision-table capacity must never overlap the inserted-token flag");
		VapsIdentityDecisionTable<Object> decisions = new VapsIdentityDecisionTable<>(16);
		Object selected = new Object();
		Object suppressed = new Object();
		decisions.nextFrame();
		int selectedToken = decisions.record(selected);
		int suppressedToken = decisions.record(suppressed);
		check(VapsIdentityDecisionTable.wasInserted(selectedToken)
					&& VapsIdentityDecisionTable.wasInserted(suppressedToken),
				"bounded identity decisions must accept normal frame entries");
		check(!VapsIdentityDecisionTable.wasInserted(decisions.record(suppressed)),
				"duplicate identities must be returned from the insertion probe itself");
		decisions.selectRecorded(selectedToken);
		check(decisions.decision(selected) == VapsIdentityDecisionTable.SELECTED,
				"a recorded slot token must select without probing again");
		check(decisions.decision(suppressed) == VapsIdentityDecisionTable.SCANNED,
				"one decision probe must distinguish scanned-only suppression");
		check(decisions.shouldRender(new Object()),
				"unknown identities must remain fail-open after one decision probe");
		check(decisions.occupiedCountForTesting() == 2
						&& decisions.retainsReferenceForTesting(selected)
						&& decisions.retainsReferenceForTesting(suppressed),
				"current-frame identities must remain retained until extraction release");
		decisions.releaseFrame();
		check(decisions.decision(selected) == VapsIdentityDecisionTable.UNKNOWN
					&& decisions.decision(suppressed) == VapsIdentityDecisionTable.UNKNOWN,
				"released identities must no longer have decisions");
		check(decisions.occupiedCountForTesting() == 0
						&& !decisions.retainsReferenceForTesting(selected)
						&& !decisions.retainsReferenceForTesting(suppressed),
				"frame release must clear occupied references and its ledger without GC");

		check(decisions.record(selected) != VapsIdentityDecisionTable.SATURATED_TOKEN,
				"table must remain reusable after explicit frame release");
		decisions.nextFrame();
		check(decisions.decision(selected) == VapsIdentityDecisionTable.UNKNOWN,
				"next-frame recovery must release prior-frame decisions");
		check(decisions.occupiedCountForTesting() == 0
						&& !decisions.retainsReferenceForTesting(selected),
				"next-frame recovery must release references when a prior end was missed");

		VapsIdentityDecisionTable<Object> saturated = new VapsIdentityDecisionTable<>(2);
		Object first = new Object();
		Object second = new Object();
		Object failOpen = new Object();
		saturated.nextFrame();
		check(saturated.record(first) != VapsIdentityDecisionTable.SATURATED_TOKEN
					&& saturated.record(second) != VapsIdentityDecisionTable.SATURATED_TOKEN,
				"small decision table must accept entries up to capacity");
		check(saturated.record(failOpen) == VapsIdentityDecisionTable.SATURATED_TOKEN
					&& saturated.decision(failOpen) == VapsIdentityDecisionTable.UNKNOWN,
				"decision-table saturation must leave excess identities fail-open");
		saturated.releaseFrame();
		check(saturated.occupiedCountForTesting() == 0
						&& !saturated.retainsReferenceForTesting(first)
						&& !saturated.retainsReferenceForTesting(second),
				"saturated tables must release every recorded reference");

		decisions.clear();
		check(decisions.decision(suppressed) == VapsIdentityDecisionTable.UNKNOWN,
				"explicit clear must release old decisions");
	}

	private static void testSaturatingStatistics() {
		check(VapsDecisionLogic.saturatingAdd(Long.MAX_VALUE - 1L, 10L) == Long.MAX_VALUE,
				"statistics must saturate rather than overflow");
		check(VapsDecisionLogic.saturatingAdd(7L, -4L) == 7L,
				"invalid negative increments must not reduce counters");
	}

	private static void testBoundedIdentityStatisticsIndex() {
		BoundedIdentityIndex<Object> index = new BoundedIdentityIndex<>(2);
		Object first = new Object();
		Object second = new Object();
		Object overflow = new Object();
		check(index.index(first) == 0 && index.index(first) == 0,
				"repeated identity statistics updates must resolve to one bounded index");
		check(index.index(second) == 1 && index.keyAt(0) == first && index.keyAt(1) == second,
				"identity statistics indices must retain dense insertion order");
		check(index.index(overflow) == -1 && !index.retainsReferenceForTesting(overflow),
				"statistics cardinality overflow must remain bounded and allocation-free");
		index.clear();
		check(index.size() == 0
					&& !index.retainsReferenceForTesting(first)
					&& !index.retainsReferenceForTesting(second),
				"statistics lifecycle clear must release every retained identity");
	}

	private static void testStatisticsCollectionGateTransitions() {
		StatisticsCollectionGate gate = new StatisticsCollectionGate();
		InspectorStatisticsDemand.publish(false);
		check(InspectorStatisticsDemand.publish(true)
					&& !InspectorStatisticsDemand.publish(true),
				"Inspector demand publication must write only on visibility transitions");
		InspectorStatisticsDemand.publish(false);
		gate.refresh(true, true && InspectorStatisticsDemand.isRequested());
		check(!gate.shouldCollect(),
				"a hidden Inspector must keep detailed particle accounting dormant");
		InspectorStatisticsDemand.publish(true);
		gate.refresh(true, true && InspectorStatisticsDemand.isRequested());
		check(gate.shouldCollect(),
				"a visible Inspector with feature statistics enabled must collect");

		// A changed option is intentionally observed only at the next tick/frame boundary.
		boolean statisticsRequested = false;
		check(gate.shouldCollect() && !statisticsRequested,
				"hot paths must use the cached boundary decision without rereading configuration");
		gate.refresh(true, statisticsRequested);
		check(!gate.shouldCollect(),
				"the next boundary must stop all optional accounting");

		long accumulated = 4L;
		if (gate.shouldCollect()) {
			accumulated++;
		}
		gate.refresh(true, true);
		if (gate.shouldCollect()) {
			accumulated++;
		}
		check(accumulated == 5L,
				"disabling collection must preserve prior totals and re-enable future accounting");
		gate.disable();
		check(!gate.shouldCollect(), "lifecycle cleanup must clear cached collection state");
		InspectorStatisticsDemand.publish(false);
	}

	private static void testCycleSnapshotFastGates() {
		VapsHotPathGate.clear();
		for (int particle = 0; particle < 10_000; particle++) {
			check(!VapsHotPathGate.simulationActive()
						&& !VapsHotPathGate.renderActive(),
					"disabled particle hooks use only a dormant primitive gate");
		}
		VapsCycleSettings.Simulation captured = new VapsCycleSettings.Simulation(
				true, true, false, true,
				1.0D, 2.0D, 3.0D, 576.0D, 4, 64
		);
		VapsCycleSettings.Simulation later = new VapsCycleSettings.Simulation(
				true, true, true, false,
				9.0D, 9.0D, 9.0D, 64.0D, 8, 8
		);
		VapsHotPathGate.publishSimulation(true);
		check(VapsHotPathGate.simulationActive()
					&& captured.playerX() == 1.0D
					&& captured.farTickInterval() == 4
					&& !captured.collectStatistics()
					&& later.farTickInterval() == 8,
				"simulation settings remain immutable until the next tick boundary");
		VapsHotPathGate.publishRender(true);
		check(VapsHotPathGate.renderActive(),
				"render planning publishes once for the extraction cycle");
		VapsHotPathGate.clear();

		VapsCycleSettings.Simulation reusable = new VapsCycleSettings.Simulation();
		VapsCycleSettings.Simulation firstCycle = reusable.capture(
				true, true, false, true,
				1.0D, 2.0D, 3.0D, 576.0D, 4, 64
		);
		VapsCycleSettings.Simulation secondCycle = reusable.capture(
				true, true, true, false,
				9.0D, 8.0D, 7.0D, 64.0D, 8, 8
		);
		check(firstCycle == secondCycle && secondCycle.playerX() == 9.0D
					&& secondCycle.collectStatistics() && secondCycle.farTickInterval() == 8,
				"later simulation boundaries must reuse storage and publish one complete new snapshot");
		reusable.disable();
		check(!reusable.active(), "simulation lifecycle cleanup must disable reusable settings");
	}

	private static void testGuardEligibilityHandoffEliminatesRawRescans() {
		ParticleEligibilityHandoff<Object> handoff = new ParticleEligibilityHandoff<>(8);
		Object quad = new Object();
		Object itemPickup = new Object();
		Object elderGuardian = new Object();
		handoff.begin();
		check(handoff.add(quad, false, 1), "visible VAPS-approved quads must enter the shared plan");
		check(handoff.add(itemPickup, true, 3), "item-pickup candidates must retain special priority");
		check(handoff.add(elderGuardian, true, 5), "guardian candidates must retain special priority");
		handoff.complete(6);

		int legacyOverloadedRawVisits = 6 + 6 + 4 + 6;
		check(handoff.rawSourceVisitsForTesting() == 6 && legacyOverloadedRawVisits == 22,
				"combined scheduling must use one six-particle raw scan instead of 22 VAPS/Guard raw visits");
		check(handoff.candidateCount() == 3 && handoff.specialCount() == 2,
				"the shared plan must contain only VAPS-approved, Guard-relevant candidates");
		check(handoff.candidateAt(0) == quad && handoff.originalIndexAt(0) == 1
					&& !handoff.isSpecial(0),
				"quad identity and vanilla source order must remain exact");
		check(handoff.candidateAt(1) == itemPickup && handoff.originalIndexAt(1) == 3
					&& handoff.isSpecial(1)
					&& handoff.candidateAt(2) == elderGuardian
					&& handoff.originalIndexAt(2) == 5,
				"special groups must preserve item-before-guardian source indices");

		handoff.abort();
		check(!handoff.isComplete()
					&& !handoff.retainsReferenceForTesting(quad)
					&& !handoff.retainsReferenceForTesting(itemPickup)
					&& !handoff.retainsReferenceForTesting(elderGuardian),
				"extraction end must explicitly release every handed-off game object");
	}

	private static void testGuardEligibilityHandoffFailureLifecycle() {
		ParticleEligibilityHandoff<Object> handoff = new ParticleEligibilityHandoff<>(2);
		Object first = new Object();
		Object second = new Object();
		Object overflow = new Object();
		check(!handoff.isComplete(), "a disabled or unstarted handoff must force Guard's standalone path");

		handoff.begin();
		check(handoff.add(first, false, 0) && handoff.add(second, true, 1),
				"a bounded plan must accept candidates up to capacity");
		check(!handoff.add(overflow, false, 2),
				"capacity saturation must invalidate the handoff instead of returning partial decisions");
		check(!handoff.isComplete()
					&& handoff.candidateCount() == 0
					&& !handoff.retainsReferenceForTesting(first)
					&& !handoff.retainsReferenceForTesting(second),
				"saturation must clear references and make Volt Guard fall back to its raw scan");

		handoff.begin();
		check(handoff.add(overflow, false, 0), "the fixed buffer must be reusable after an aborted frame");
		handoff.complete(1);
		check(handoff.isComplete() && handoff.candidateAt(0) == overflow,
				"a later healthy frame must publish a complete handoff");
		handoff.abort();
		check(!handoff.retainsReferenceForTesting(overflow),
				"failure and lifecycle aborts must release later-frame identities too");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
