package com.ragedriver.sodiumvolt.client.smartfps;

/** Transition gate that keeps the statistics-off limiter path allocation-free. */
final class SmartFpsStatisticsGate {
	enum Action {
		NONE,
		PUBLISH,
		CLEAR
	}

	private boolean active;
	private volatile long publishedRevision = Long.MIN_VALUE;

	Action refresh(boolean requested) {
		if (requested) {
			this.active = true;
			return Action.PUBLISH;
		}
		if (!this.active) {
			return Action.NONE;
		}
		this.active = false;
		this.publishedRevision = Long.MIN_VALUE;
		return Action.CLEAR;
	}

	void markPublished(long revision) {
		this.publishedRevision = revision;
	}

	boolean canExpose(boolean requested, long revision) {
		return requested && this.publishedRevision == revision;
	}

	void reset() {
		this.active = false;
		this.publishedRevision = Long.MIN_VALUE;
	}
}
