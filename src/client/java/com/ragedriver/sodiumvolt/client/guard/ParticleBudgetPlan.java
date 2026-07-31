package com.ragedriver.sodiumvolt.client.guard;

record ParticleBudgetPlan(int totalBudget, int specialReserveCapacity) {
	private static final int SPECIAL_RESERVE_DIVISOR = 8;

	static ParticleBudgetPlan create(int totalBudget, int specialParticleCount, boolean preserveCritical) {
		int safeBudget = Math.max(0, totalBudget);
		if (!preserveCritical || safeBudget == 0 || specialParticleCount <= 0) {
			return new ParticleBudgetPlan(safeBudget, 0);
		}

		int reserveLimit = Math.max(1, safeBudget / SPECIAL_RESERVE_DIVISOR);
		return new ParticleBudgetPlan(safeBudget, Math.min(specialParticleCount, reserveLimit));
	}

	int remainingCapacityAfter(int selectedSpecialCount) {
		return Math.max(0, this.totalBudget - Math.clamp(
				selectedSpecialCount,
				0,
				this.specialReserveCapacity
		));
	}
}
