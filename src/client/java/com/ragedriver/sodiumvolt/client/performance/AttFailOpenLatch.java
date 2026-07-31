package com.ragedriver.sodiumvolt.client.performance;

final class AttFailOpenLatch {
	private boolean failed;

	boolean canRun() {
		return !this.failed;
	}

	void fail() {
		this.failed = true;
	}

	void observeDisabled() {
		this.failed = false;
	}

	void resetForReload() {
		this.failed = false;
	}
}
