package com.ragedriver.sodiumvolt.client.performance;

/**
 * Owns exemption reload generations. At most one scan is in flight; a request
 * arriving during it is coalesced into one latest-generation follow-up.
 */
final class AttExemptionReloadGate {
	private long requestedGeneration = 1L;
	private long loadedGeneration;
	private long inFlightGeneration;
	private boolean honorExemptionLists = true;

	synchronized long onResourceReload(boolean enabled, boolean honorExemptionLists) {
		this.honorExemptionLists = honorExemptionLists;
		this.requestedGeneration = nextGeneration(this.requestedGeneration);
		return enabled ? claimLatest() : 0L;
	}

	synchronized long onEnabledTick(boolean honorExemptionLists) {
		if (this.honorExemptionLists != honorExemptionLists) {
			this.honorExemptionLists = honorExemptionLists;
			this.requestedGeneration = nextGeneration(this.requestedGeneration);
		}
		return claimLatest();
	}

	synchronized Completion complete(long generation) {
		return complete(generation, () -> { });
	}

	synchronized Completion complete(long generation, Runnable publishLatest) {
		if (generation == 0L || generation != this.inFlightGeneration) {
			return Completion.REJECTED;
		}
		this.inFlightGeneration = 0L;
		boolean publish = generation == this.requestedGeneration;
		if (publish) {
			publishLatest.run();
			this.loadedGeneration = generation;
		}
		return new Completion(publish, claimLatest());
	}

	synchronized void fail(long generation) {
		if (generation == this.inFlightGeneration) {
			this.inFlightGeneration = 0L;
		}
	}

	synchronized boolean ready() {
		return this.inFlightGeneration == 0L
				&& this.loadedGeneration == this.requestedGeneration;
	}

	private long claimLatest() {
		if (this.inFlightGeneration != 0L
				|| this.loadedGeneration == this.requestedGeneration) {
			return 0L;
		}
		this.inFlightGeneration = this.requestedGeneration;
		return this.inFlightGeneration;
	}

	private static long nextGeneration(long current) {
		return current == Long.MAX_VALUE ? 1L : current + 1L;
	}

	record Completion(boolean publish, long followUpGeneration) {
		private static final Completion REJECTED = new Completion(false, 0L);
	}
}
