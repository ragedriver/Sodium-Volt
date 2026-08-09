package com.ragedriver.sodiumvolt.client.performance;

/** Emits exactly one cleanup boundary when BERP leaves an enabled lifecycle. */
final class BerpRuntimeLifecycle {
	private boolean observedEnabled;

	boolean observeAndShouldCleanup(boolean configuredEnabled) {
		if (configuredEnabled) {
			this.observedEnabled = true;
			return false;
		}
		if (!this.observedEnabled) {
			return false;
		}
		this.observedEnabled = false;
		return true;
	}

	boolean observedEnabled() {
		return this.observedEnabled;
	}

	void reset() {
		this.observedEnabled = false;
	}
}
