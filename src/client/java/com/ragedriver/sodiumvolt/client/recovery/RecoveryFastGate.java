package com.ragedriver.sodiumvolt.client.recovery;

final class RecoveryFastGate {
	private static final int CLEANUP_PENDING = 1;
	private static final int PROFILE_APPLIED = 2;

	private volatile int state;

	boolean requiresRenderFrame(boolean masterEnabled, boolean clientStopping) {
		return !clientStopping && (masterEnabled || (this.state & CLEANUP_PENDING) != 0);
	}

	boolean maySuspendApc(boolean recoveryActive) {
		return recoveryActive || (this.state & PROFILE_APPLIED) != 0;
	}

	void publish(boolean cleanupPending, boolean profileApplied) {
		this.state = (cleanupPending ? CLEANUP_PENDING : 0)
				| (profileApplied ? PROFILE_APPLIED : 0);
	}

	void clear() {
		this.state = 0;
	}
}
