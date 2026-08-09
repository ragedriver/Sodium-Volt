package com.ragedriver.sodiumvolt.client.smartfps;

import java.util.concurrent.atomic.AtomicLong;

/** Generation-tagged ownership for cancel-safe asynchronous power probes. */
final class SmartFpsPowerQueryGate {
	private final AtomicLong sequence = new AtomicLong();
	private final AtomicLong inFlightToken = new AtomicLong();

	long tryBegin() {
		long token = nextToken();
		return this.inFlightToken.compareAndSet(0L, token) ? token : 0L;
	}

	void finish(long token) {
		if (token != 0L) {
			this.inFlightToken.compareAndSet(token, 0L);
		}
	}

	void cancel() {
		this.inFlightToken.set(0L);
	}

	boolean isInFlight() {
		return this.inFlightToken.get() != 0L;
	}

	private long nextToken() {
		while (true) {
			long current = this.sequence.get();
			long next = current == Long.MAX_VALUE ? 1L : current + 1L;
			if (this.sequence.compareAndSet(current, next)) {
				return next;
			}
		}
	}
}
