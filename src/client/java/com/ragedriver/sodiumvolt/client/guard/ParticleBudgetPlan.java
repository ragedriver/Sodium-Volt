package com.ragedriver.sodiumvolt.client.guard;

record ParticleBudgetPlan(int totalBudget, int specialReserveCapacity) {
	private static final int SPECIAL_RESERVE_DIVISOR = 8;

	static ParticleBudgetPlan create(int totalBudget, int specialParticleCount, boolean preserveCritical) {
		int safeBudget = Math.max(0, totalBudget);
		int reserveLimit = maximumSpecialReserveCapacity(safeBudget, preserveCritical);
		if (reserveLimit == 0 || specialParticleCount <= 0) {
			return new ParticleBudgetPlan(safeBudget, 0);
		}
		return new ParticleBudgetPlan(safeBudget, Math.min(specialParticleCount, reserveLimit));
	}

	static int maximumSpecialReserveCapacity(int totalBudget, boolean preserveCritical) {
		int safeBudget = Math.max(0, totalBudget);
		return !preserveCritical || safeBudget == 0
				? 0
				: Math.max(1, safeBudget / SPECIAL_RESERVE_DIVISOR);
	}

	int remainingCapacityAfter(int selectedSpecialCount) {
		return Math.max(0, this.totalBudget - Math.clamp(
				selectedSpecialCount,
				0,
				this.specialReserveCapacity
		));
	}
}
