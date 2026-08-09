package com.ragedriver.sodiumvolt.client.guard;

/** Pure normal-load upper-bound checks shared by Volt Guard's hot paths. */
final class GuardWorkloadBounds {
	private GuardWorkloadBounds() {
	}

	static boolean particleQueuesFitBudget(
			int quadCount,
			int itemPickupCount,
			int elderGuardianCount,
			int budget
	) {
		return relevantParticleUpperBound(quadCount, itemPickupCount, elderGuardianCount)
				<= Math.max(0, budget);
	}

	static int relevantParticleUpperBound(
			int quadCount,
			int itemPickupCount,
			int elderGuardianCount
	) {
		return saturatingAdd(saturatingAdd(quadCount, itemPickupCount), elderGuardianCount);
	}

	static boolean eligibleParticlesFitBudget(int eligibleCount, int budget) {
		return Math.max(0, eligibleCount) <= Math.max(0, budget);
	}

	static boolean statesFitBudget(int sourceSize, int budget) {
		return Math.max(0, sourceSize) <= Math.max(0, budget);
	}

	private static int saturatingAdd(int first, int second) {
		int safeFirst = Math.max(0, first);
		int safeSecond = Math.max(0, second);
		return safeFirst >= Integer.MAX_VALUE - safeSecond
				? Integer.MAX_VALUE
				: safeFirst + safeSecond;
	}
}
