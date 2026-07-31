package com.ragedriver.sodiumvolt.client.recovery;

final class RecoveryTransitionGate {
	private boolean masterStateObserved;
	private boolean masterEnabled;
	private boolean transitionBlocked;
	private boolean clientStopping;

	void observeMasterState(boolean enabled) {
		if (this.clientStopping) {
			return;
		}
		if (!masterStateObserved || masterEnabled != enabled) {
			masterStateObserved = true;
			masterEnabled = enabled;
			transitionBlocked = false;
		}
	}

	boolean mayAttemptTransition() {
		return !this.clientStopping && !this.transitionBlocked;
	}

	void transitionFailed() {
		transitionBlocked = true;
	}

	void transitionSucceeded() {
		transitionBlocked = false;
	}

	void beginClientStopping() {
		this.clientStopping = true;
		this.transitionBlocked = true;
	}

	boolean isClientStopping() {
		return this.clientStopping;
	}
}
