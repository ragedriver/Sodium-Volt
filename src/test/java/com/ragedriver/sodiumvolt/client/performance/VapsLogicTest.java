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
		testIdentityDecisionFrames();
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
		VapsDecisionLogic.AgeStep live = VapsDecisionLogic.ageOnlyStep(4, 5);
		check(live.age() == 5 && !live.expires(),
				"age-only cadence must match vanilla post-increment lifetime semantics");
		VapsDecisionLogic.AgeStep expired = VapsDecisionLogic.ageOnlyStep(5, 5);
		check(expired.age() == 6 && expired.expires(),
				"particle must expire on the same tick as vanilla");
		VapsDecisionLogic.AgeStep saturated =
				VapsDecisionLogic.ageOnlyStep(Integer.MAX_VALUE, Integer.MAX_VALUE);
		check(saturated.age() == Integer.MAX_VALUE && saturated.expires(),
				"age arithmetic must saturate safely");
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

	private static void testIdentityDecisionFrames() {
		VapsIdentityDecisionTable<Object> decisions = new VapsIdentityDecisionTable<>(16);
		Object selected = new Object();
		Object suppressed = new Object();
		decisions.nextFrame();
		check(decisions.addScanned(selected) && decisions.addScanned(suppressed),
				"bounded identity decisions must accept normal frame entries");
		decisions.select(selected);
		check(decisions.isSelected(selected), "selected identity must be retained");
		check(decisions.isScanned(suppressed) && !decisions.isSelected(suppressed),
				"scanned-only identity must remain suppressed");
		check(!decisions.isScanned(new Object()),
				"unknown identities must remain fail-open to the scheduler caller");
		check(decisions.occupiedCountForTesting() == 2
						&& decisions.retainsReferenceForTesting(selected)
						&& decisions.retainsReferenceForTesting(suppressed),
				"current-frame identities must remain retained until extraction release");
		decisions.releaseFrame();
		check(!decisions.isScanned(selected) && !decisions.isScanned(suppressed),
				"released identities must no longer have decisions");
		check(decisions.occupiedCountForTesting() == 0
						&& !decisions.retainsReferenceForTesting(selected)
						&& !decisions.retainsReferenceForTesting(suppressed),
				"frame release must clear occupied references and its ledger without GC");

		check(decisions.addScanned(selected),
				"table must remain reusable after explicit frame release");
		decisions.nextFrame();
		check(!decisions.isScanned(selected),
				"next-frame recovery must release prior-frame decisions");
		check(decisions.occupiedCountForTesting() == 0
						&& !decisions.retainsReferenceForTesting(selected),
				"next-frame recovery must release references when a prior end was missed");

		VapsIdentityDecisionTable<Object> saturated = new VapsIdentityDecisionTable<>(2);
		Object first = new Object();
		Object second = new Object();
		Object failOpen = new Object();
		saturated.nextFrame();
		check(saturated.addScanned(first) && saturated.addScanned(second),
				"small decision table must accept entries up to capacity");
		check(!saturated.addScanned(failOpen) && !saturated.isScanned(failOpen),
				"decision-table saturation must leave excess identities fail-open");
		saturated.releaseFrame();
		check(saturated.occupiedCountForTesting() == 0
						&& !saturated.retainsReferenceForTesting(first)
						&& !saturated.retainsReferenceForTesting(second),
				"saturated tables must release every recorded reference");

		decisions.clear();
		check(!decisions.isScanned(suppressed), "explicit clear must release old decisions");
	}

	private static void testSaturatingStatistics() {
		check(VapsDecisionLogic.saturatingAdd(Long.MAX_VALUE - 1L, 10L) == Long.MAX_VALUE,
				"statistics must saturate rather than overflow");
		check(VapsDecisionLogic.saturatingAdd(7L, -4L) == 7L,
				"invalid negative increments must not reduce counters");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
