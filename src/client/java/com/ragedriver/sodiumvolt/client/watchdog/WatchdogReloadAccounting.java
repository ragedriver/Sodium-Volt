package com.ragedriver.sodiumvolt.client.watchdog;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class WatchdogReloadAccounting {
	private final int maximumActive;
	private final AtomicInteger active = new AtomicInteger();

	WatchdogReloadAccounting(int maximumActive) {
		this.maximumActive = Math.max(1, maximumActive);
	}

	boolean tryClaim() {
		while (true) {
			int current = this.active.get();
			if (current >= this.maximumActive) {
				return false;
			}
			if (this.active.compareAndSet(current, current + 1)) {
				return true;
			}
		}
	}

	boolean release(
			long tokenGeneration,
			long currentGeneration,
			AtomicBoolean completed
	) {
		if (completed == null
				|| tokenGeneration != currentGeneration
				|| !completed.compareAndSet(false, true)) {
			return false;
		}
		this.active.updateAndGet(value -> Math.max(0, value - 1));
		return true;
	}

	int active() {
		return this.active.get();
	}

	void reset() {
		this.active.set(0);
	}
}
