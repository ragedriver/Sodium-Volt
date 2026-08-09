package com.ragedriver.sodiumvolt.client.performance;

/** Tracks the one cleanup boundary between enabled and stable-disabled ATT ticks. */
final class AttRuntimeLifecycle {
	private boolean observedEnabled;
	private boolean activation;

	boolean observeAndShouldCleanup(boolean configuredEnabled) {
		if (configuredEnabled) {
			if (!this.observedEnabled) {
				this.observedEnabled = true;
				this.activation = true;
			}
			return false;
		}
		if (!this.observedEnabled) {
			return false;
		}
		this.observedEnabled = false;
		return true;
	}

	boolean consumeActivation() {
		if (!this.activation) {
			return false;
		}
		this.activation = false;
		return true;
	}
}
