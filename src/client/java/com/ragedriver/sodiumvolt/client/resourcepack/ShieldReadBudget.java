package com.ragedriver.sodiumvolt.client.resourcepack;

import java.util.concurrent.atomic.AtomicLong;

public final class ShieldReadBudget {
	private final long maximumBytes;
	private final AtomicLong consumedBytes = new AtomicLong();

	public ShieldReadBudget(long maximumBytes) {
		this.maximumBytes = Math.max(1L, maximumBytes);
	}

	public boolean consume(long bytes) {
		if (bytes <= 0L) {
			return true;
		}
		while (true) {
			long current = this.consumedBytes.get();
			if (current > this.maximumBytes - bytes) {
				this.consumedBytes.compareAndSet(current, this.maximumBytes);
				return false;
			}
			if (this.consumedBytes.compareAndSet(current, current + bytes)) {
				return true;
			}
		}
	}

	public long consumedBytes() {
		return this.consumedBytes.get();
	}

	public long remainingBytes() {
		return Math.max(0L, this.maximumBytes - this.consumedBytes.get());
	}
}
