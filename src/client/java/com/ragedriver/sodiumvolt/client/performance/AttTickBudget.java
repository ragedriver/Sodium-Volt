package com.ragedriver.sodiumvolt.client.performance;

final class AttTickBudget {
	static final int EXEMPTION_RESERVE = 64;

	private int perAtlasRemaining;
	private int globalRemaining;
	private int exemptionRemaining;
	private long epoch = Long.MIN_VALUE;

	void beginClientTick(long clientTick, int configuredPerAtlasBudget) {
		if (this.epoch != clientTick) {
			this.epoch = clientTick;
			this.globalRemaining = saturatingDouble(Math.max(1, configuredPerAtlasBudget));
		}
		this.perAtlasRemaining = Math.max(1, configuredPerAtlasBudget);
		this.exemptionRemaining = EXEMPTION_RESERVE;
	}

	boolean canClaimNormal() {
		return this.perAtlasRemaining > 0 && this.globalRemaining > 0;
	}

	boolean claimNormal() {
		if (!canClaimNormal()) {
			return false;
		}
		this.perAtlasRemaining--;
		this.globalRemaining--;
		return true;
	}

	boolean claimExemption() {
		if (this.exemptionRemaining <= 0) {
			return false;
		}
		this.exemptionRemaining--;
		return true;
	}

	int exemptionRemainingForTesting() {
		return this.exemptionRemaining;
	}

	private static int saturatingDouble(int value) {
		return value > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : value * 2;
	}
}
