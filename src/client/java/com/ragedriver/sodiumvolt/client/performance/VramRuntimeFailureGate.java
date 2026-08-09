package com.ragedriver.sodiumvolt.client.performance;

final class VramRuntimeFailureGate {
	private volatile boolean failed;

	boolean requiresRenderFrame(boolean masterEnabled, boolean active) {
		return masterEnabled || active || this.failed;
	}

	boolean failed() {
		return this.failed;
	}

	void fail() {
		this.failed = true;
	}

	void clear() {
		this.failed = false;
	}
}
