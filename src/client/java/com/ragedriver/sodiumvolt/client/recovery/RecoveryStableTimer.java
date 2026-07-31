package com.ragedriver.sodiumvolt.client.recovery;

public final class RecoveryStableTimer {
	private long validSinceNanos = Long.MIN_VALUE;

	public boolean update(boolean validFrame, long nowNanos, long durationNanos) {
		if (!validFrame) {
			reset();
			return false;
		}
		if (this.validSinceNanos == Long.MIN_VALUE || nowNanos < this.validSinceNanos) {
			this.validSinceNanos = nowNanos;
		}
		return elapsed(nowNanos) >= Math.max(0L, durationNanos);
	}

	public long remainingSeconds(long nowNanos, long durationNanos) {
		if (this.validSinceNanos == Long.MIN_VALUE) {
			return Math.max(0L, durationNanos) / 1_000_000_000L;
		}
		long remaining = Math.max(0L, durationNanos - elapsed(nowNanos));
		return (remaining + 999_999_999L) / 1_000_000_000L;
	}

	public void reset() {
		this.validSinceNanos = Long.MIN_VALUE;
	}

	private long elapsed(long nowNanos) {
		if (this.validSinceNanos == Long.MIN_VALUE || nowNanos < this.validSinceNanos) {
			return 0L;
		}
		long elapsed = nowNanos - this.validSinceNanos;
		return elapsed < 0L ? Long.MAX_VALUE : elapsed;
	}
}
