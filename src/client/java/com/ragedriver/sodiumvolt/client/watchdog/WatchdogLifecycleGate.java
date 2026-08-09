package com.ragedriver.sodiumvolt.client.watchdog;

final class WatchdogLifecycleGate {
	private volatile boolean enabled;
	private boolean threadStarted;
	private volatile boolean clientStopping;
	private long transitionGeneration;

	synchronized boolean setEnabled(boolean value) {
		if (this.clientStopping) {
			return false;
		}
		if (this.enabled != value) {
			this.enabled = value;
			this.transitionGeneration = nextGeneration(this.transitionGeneration);
			notifyAll();
		}
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
		this.transitionGeneration = nextGeneration(this.transitionGeneration);
		notifyAll();
	}

	synchronized long transitionGeneration() {
		return this.transitionGeneration;
	}

	synchronized long awaitTransition(long observedGeneration, long timeoutMillis) {
		if (this.transitionGeneration != observedGeneration || this.clientStopping) {
			return this.transitionGeneration;
		}
		if (timeoutMillis <= 0L) {
			while (this.transitionGeneration == observedGeneration
					&& !this.clientStopping) {
				try {
					wait();
				} catch (InterruptedException exception) {
					if (this.clientStopping) {
						break;
					}
				}
			}
			return this.transitionGeneration;
		}
		try {
			wait(Math.max(1L, timeoutMillis));
		} catch (InterruptedException exception) {
			// Stop interrupts are paired with beginStopping(); unrelated interrupts
			// simply resample the lifecycle state without leaving a sticky spin flag.
		}
		return this.transitionGeneration;
	}

	private static long nextGeneration(long current) {
		return current == Long.MAX_VALUE ? 1L : current + 1L;
	}

	/*
	 * Reads stay monitor-free on render/reload hooks. Only the daemon waits on this
	 * condition, and every off/on/stop transition advances the generation.
	 */
	boolean isEnabled() {
		return this.enabled && !this.clientStopping;
	}

	boolean isClientStopping() {
		return this.clientStopping;
	}
}
