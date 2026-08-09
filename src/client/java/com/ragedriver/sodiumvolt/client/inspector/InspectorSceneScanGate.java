package com.ragedriver.sodiumvolt.client.inspector;

import java.util.concurrent.atomic.AtomicLong;

/**
 * One-shot, generation-tagged handoff from the HUD refresh cadence to level
 * extraction. Fabric invokes both callbacks on the client render path; atomics
 * retain defensive ordering without imposing an RMW on the normal no-request
 * extraction path. A hidden or restarted HUD invalidates every older request.
 */
final class InspectorSceneScanGate {
	private final AtomicLong generation = new AtomicLong();
	private final AtomicLong requestedGeneration = new AtomicLong();
	private volatile long activeGeneration;

	void resume() {
		restart();
	}

	void restart() {
		this.activeGeneration = 0L;
		this.requestedGeneration.set(0L);
		this.activeGeneration = advanceGeneration();
	}

	void suspend() {
		this.activeGeneration = 0L;
		this.requestedGeneration.set(0L);
		advanceGeneration();
	}

	boolean request() {
		long current = this.activeGeneration;
		if (current == 0L) {
			return false;
		}
		this.requestedGeneration.set(current);
		return true;
	}

	long consume() {
		long requested = this.requestedGeneration.get();
		if (requested == 0L) {
			return 0L;
		}
		return this.requestedGeneration.compareAndSet(requested, 0L)
				&& requested == this.activeGeneration
				? requested
				: 0L;
	}

	void cancel() {
		if (this.requestedGeneration.get() != 0L) {
			this.requestedGeneration.set(0L);
		}
	}

	boolean canPublish(long candidateGeneration, boolean stillEligible) {
		return stillEligible
				&& candidateGeneration != 0L
				&& candidateGeneration == this.activeGeneration;
	}

	private long advanceGeneration() {
		while (true) {
			long current = this.generation.get();
			long next = current == Long.MAX_VALUE ? 1L : current + 1L;
			if (this.generation.compareAndSet(current, next)) {
				return next;
			}
		}
	}
}
