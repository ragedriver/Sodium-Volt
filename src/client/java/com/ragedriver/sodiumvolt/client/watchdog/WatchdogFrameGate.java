package com.ragedriver.sodiumvolt.client.watchdog;

final class WatchdogFrameGate {
	private static final int RUNTIME_ENABLED = 1;
	private static final int HEARTBEAT_PUBLISHED = 2;

	private volatile int state;

	boolean requiresBegin(boolean masterEnabled) {
		return masterEnabled || (this.state & RUNTIME_ENABLED) != 0;
	}

	boolean requiresEnd() {
		return this.state != 0;
	}

	boolean runtimeEnabled() {
		return (this.state & RUNTIME_ENABLED) != 0;
	}

	void enableRuntime() {
		this.state = RUNTIME_ENABLED;
	}

	void publishHeartbeat() {
		this.state = RUNTIME_ENABLED | HEARTBEAT_PUBLISHED;
	}

	boolean finishHeartbeat() {
		int current = this.state;
		if ((current & HEARTBEAT_PUBLISHED) == 0) {
			return false;
		}
		this.state = current & ~HEARTBEAT_PUBLISHED;
		return true;
	}

	void disableRuntime() {
		this.state = 0;
	}
}
