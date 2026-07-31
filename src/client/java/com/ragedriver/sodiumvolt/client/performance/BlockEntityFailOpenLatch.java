package com.ragedriver.sodiumvolt.client.performance;

/**
 * Small state machine that keeps a failed scheduler disabled until either a
 * disabled frame or a lifecycle reset creates an explicit retry boundary.
 */
final class BlockEntityFailOpenLatch {
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

	void resetForLifecycle() {
		this.failed = false;
	}
}
