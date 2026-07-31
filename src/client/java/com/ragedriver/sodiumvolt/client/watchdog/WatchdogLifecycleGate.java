package com.ragedriver.sodiumvolt.client.watchdog;

final class WatchdogLifecycleGate {
	private boolean enabled;
	private boolean threadStarted;
	private boolean clientStopping;

	synchronized boolean setEnabled(boolean value) {
		if (this.clientStopping) {
			return false;
		}
		this.enabled = value;
		return value;
	}

	synchronized boolean claimThreadStart() {
		if (!this.enabled || this.threadStarted || this.clientStopping) {
			return false;
		}
		this.threadStarted = true;
		return true;
	}

	synchronized void beginStopping() {
		this.clientStopping = true;
		this.enabled = false;
	}

	synchronized boolean isEnabled() {
		return this.enabled && !this.clientStopping;
	}

	synchronized boolean isClientStopping() {
		return this.clientStopping;
	}
}
