package com.ragedriver.sodiumvolt.client.performance;

public final class VramPressureStateMachine {
	private Level level = Level.NORMAL;
	private int protectionSamples;
	private int criticalSamples;
	private int deescalationSamples;
	private long recoveryStartNanos = Long.MIN_VALUE;

	public Action sample(
			long estimatedBytes,
			long budgetBytes,
			int protectionPercent,
			int criticalPercent,
			int sustainedSamples,
			long recoveryDelayNanos,
			long nowNanos
	) {
		if (estimatedBytes < 0L || budgetBytes <= 0L || nowNanos < 0L
				|| sustainedSamples <= 0 || criticalPercent <= protectionPercent) {
			return Action.UNKNOWN;
		}
		long protectionBytes = thresholdBytes(budgetBytes, protectionPercent);
		long criticalBytes = thresholdBytes(budgetBytes, criticalPercent);
		long recoveryBytes = thresholdBytes(budgetBytes, Math.max(0, protectionPercent - 10));
		long criticalRecoveryBytes =
				thresholdBytes(budgetBytes, Math.max(protectionPercent, criticalPercent - 5));
		boolean critical = estimatedBytes >= criticalBytes;
		boolean protection = estimatedBytes >= protectionBytes;

		if (this.level == Level.NORMAL) {
			this.criticalSamples = critical ? boundedIncrement(this.criticalSamples) : 0;
			this.protectionSamples = protection ? boundedIncrement(this.protectionSamples) : 0;
			if (this.criticalSamples >= sustainedSamples) {
				this.level = Level.CRITICAL;
				resetTransient();
				return Action.ENTER_CRITICAL;
			}
			if (this.protectionSamples >= sustainedSamples) {
				this.level = Level.PROTECTION;
				resetTransient();
				return Action.ENTER_PROTECTION;
			}
			return Action.HOLD;
		}

		if (this.level == Level.PROTECTION) {
			this.criticalSamples = critical ? boundedIncrement(this.criticalSamples) : 0;
			if (this.criticalSamples >= sustainedSamples) {
				this.level = Level.CRITICAL;
				resetTransient();
				return Action.ENTER_CRITICAL;
			}
			return recoverIfStable(estimatedBytes, recoveryBytes, recoveryDelayNanos, nowNanos);
		}

		if (estimatedBytes < criticalRecoveryBytes && estimatedBytes >= recoveryBytes) {
			this.deescalationSamples = boundedIncrement(this.deescalationSamples);
			if (this.deescalationSamples >= sustainedSamples) {
				this.level = Level.PROTECTION;
				resetTransient();
				return Action.DEESCALATE;
			}
		} else {
			this.deescalationSamples = 0;
		}
		return recoverIfStable(estimatedBytes, recoveryBytes, recoveryDelayNanos, nowNanos);
	}

	public Level level() {
		return this.level;
	}

	public void reset() {
		this.level = Level.NORMAL;
		resetTransient();
	}

	private Action recoverIfStable(
			long estimatedBytes,
			long recoveryBytes,
			long recoveryDelayNanos,
			long nowNanos
	) {
		if (estimatedBytes > recoveryBytes) {
			this.recoveryStartNanos = Long.MIN_VALUE;
			return Action.HOLD;
		}
		if (this.recoveryStartNanos == Long.MIN_VALUE) {
			this.recoveryStartNanos = nowNanos;
			return Action.HOLD;
		}
		long elapsed = safeElapsed(nowNanos, this.recoveryStartNanos);
		if (elapsed >= Math.max(0L, recoveryDelayNanos)) {
			this.level = Level.NORMAL;
			resetTransient();
			return Action.RECOVER_NORMAL;
		}
		return Action.HOLD;
	}

	private void resetTransient() {
		this.protectionSamples = 0;
		this.criticalSamples = 0;
		this.deescalationSamples = 0;
		this.recoveryStartNanos = Long.MIN_VALUE;
	}

	static long thresholdBytes(long budgetBytes, int percent) {
		if (budgetBytes <= 0L || percent <= 0) {
			return 0L;
		}
		long quotient = budgetBytes / 100L;
		long remainder = budgetBytes % 100L;
		return VramByteMath.saturatingAdd(
				VramByteMath.saturatingMultiply(quotient, percent),
				VramByteMath.saturatingMultiply(remainder, percent) / 100L
		);
	}

	static long safeElapsed(long current, long previous) {
		if (previous == Long.MIN_VALUE || current < previous) {
			return Long.MAX_VALUE;
		}
		long elapsed = current - previous;
		return elapsed < 0L ? Long.MAX_VALUE : elapsed;
	}

	private static int boundedIncrement(int value) {
		return value == Integer.MAX_VALUE ? value : value + 1;
	}

	public enum Level {
		NORMAL,
		PROTECTION,
		CRITICAL,
		UNKNOWN
	}

	public enum Action {
		HOLD,
		ENTER_PROTECTION,
		ENTER_CRITICAL,
		DEESCALATE,
		RECOVER_NORMAL,
		UNKNOWN
	}
}
