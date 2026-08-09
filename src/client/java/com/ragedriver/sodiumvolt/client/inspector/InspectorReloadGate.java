package com.ragedriver.sodiumvolt.client.inspector;

/**
 * Thread-safe lifecycle gate for asynchronous resource-reload observations.
 * Fast rejection is a volatile read; synchronization is used only for the
 * rare begin, hide, completion, and layout-invalidation transitions.
 */
final class InspectorReloadGate {
	private long sequence;
	private long latestAcceptedGeneration;
	private volatile long minimumValidGeneration;
	private volatile boolean accepting;
	private volatile boolean layoutInvalidationRequested;

	synchronized void resume() {
		this.accepting = true;
	}

	synchronized long begin() {
		if (!this.accepting) {
			return 0L;
		}
		return nextGeneration();
	}

	synchronized long suspend() {
		this.accepting = false;
		long barrier = nextGeneration();
		this.minimumValidGeneration = barrier;
		this.layoutInvalidationRequested = false;
		return barrier;
	}

	boolean isAccepting() {
		return this.accepting;
	}

	long suspendIfAccepting() {
		if (!this.accepting) {
			return 0L;
		}
		return suspendIfAcceptingSlow();
	}

	boolean mayComplete(long generation) {
		return this.accepting
				&& generation != 0L
				&& generation >= this.minimumValidGeneration;
	}

	synchronized boolean acceptMeasuredCompletion(long generation) {
		if (!mayComplete(generation) || generation < this.latestAcceptedGeneration) {
			return false;
		}
		this.latestAcceptedGeneration = generation;
		this.layoutInvalidationRequested = true;
		return true;
	}

	synchronized boolean acceptLayoutCompletion(long generation) {
		if (!mayComplete(generation)) {
			return false;
		}
		this.layoutInvalidationRequested = true;
		return true;
	}

	boolean consumeLayoutInvalidation() {
		if (!this.layoutInvalidationRequested) {
			return false;
		}
		synchronized (this) {
			if (!this.layoutInvalidationRequested) {
				return false;
			}
			this.layoutInvalidationRequested = false;
			return true;
		}
	}

	private long nextGeneration() {
		this.sequence = this.sequence == Long.MAX_VALUE ? 1L : this.sequence + 1L;
		return this.sequence;
	}

	private synchronized long suspendIfAcceptingSlow() {
		return this.accepting ? suspend() : 0L;
	}
}
