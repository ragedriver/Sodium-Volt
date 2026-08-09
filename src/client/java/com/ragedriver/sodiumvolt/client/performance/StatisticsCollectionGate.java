package com.ragedriver.sodiumvolt.client.performance;

/**
 * Allocation-free cached gate for optional inspector accounting. Callers
 * refresh it only at their frame/tick boundary and read it from hot paths.
 * Disabling collection deliberately preserves already accumulated statistics.
 */
final class StatisticsCollectionGate {
	private boolean collecting;

	void refresh(boolean engineEnabled, boolean statisticsRequested) {
		this.collecting = engineEnabled && statisticsRequested;
	}

	void disable() {
		this.collecting = false;
	}

	boolean shouldCollect() {
		return this.collecting;
	}
}
