package com.ragedriver.sodiumvolt.client.smartfps;

final class SmartFpsPowerAggregation {
	private int minimumPercentage = 100;
	private boolean hasValidSource;
	private boolean charging;
	private boolean discharging;

	void accept(
			double remainingCapacity,
			boolean sourceCharging,
			boolean sourceOnline,
			boolean sourceDischarging
	) {
		if (!Double.isFinite(remainingCapacity)
				|| remainingCapacity < 0.0D
				|| remainingCapacity > 1.0D) {
			// Ignore flags without a valid percentage so an unrelated UPS or stale
			// virtual source cannot poison a valid battery's classification.
			return;
		}
		int percentage = (int) Math.round(remainingCapacity * 100.0D);
		this.minimumPercentage = Math.min(
				this.minimumPercentage,
				Math.clamp(percentage, 0, 100)
		);
		this.hasValidSource = true;
		this.charging |= sourceCharging || sourceOnline;
		this.discharging |= sourceDischarging;
	}

	SmartFpsPowerSnapshot snapshot() {
		if (!this.hasValidSource) {
			return SmartFpsPowerSnapshot.UNKNOWN;
		}
		if (this.discharging) {
			return SmartFpsPowerSnapshot.discharging(this.minimumPercentage);
		}
		if (this.charging) {
			return SmartFpsPowerSnapshot.charging(this.minimumPercentage);
		}
		return SmartFpsPowerSnapshot.UNKNOWN;
	}
}
