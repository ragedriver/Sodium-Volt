package com.ragedriver.sodiumvolt.client.performance;

final class ApcControllerLogic {
	static final int UNLIMITED_TARGET = 0;
	private static final int PRESSURE_CONFIRMATIONS = 2;
	private static final int HEADROOM_CONFIRMATIONS = 2;

	private int currentLevel;
	private int pressureConfirmations;
	private int headroomConfirmations;
	private long lastPressureNanos;

	void reset(int initialLevel, long nowNanos) {
		this.currentLevel = Math.max(0, initialLevel);
		this.pressureConfirmations = 0;
		this.headroomConfirmations = 0;
		this.lastPressureNanos = nowNanos;
	}

	Decision evaluate(
			double p95Milliseconds,
			int effectiveTargetFps,
			int toleranceFps,
			int minimumLevel,
			int maximumLevel,
			long recoveryDelayNanos,
			long nowNanos
	) {
		int normalizedMaximum = Math.max(0, maximumLevel);
		int normalizedMinimum = Math.max(0, Math.min(normalizedMaximum, minimumLevel));
		this.currentLevel = Math.max(normalizedMinimum, Math.min(normalizedMaximum, this.currentLevel));
		if (effectiveTargetFps == UNLIMITED_TARGET
				|| !Double.isFinite(p95Milliseconds)
				|| p95Milliseconds <= 0.0D) {
			clearConfirmations();
			return new Decision(Action.HOLD, this.currentLevel);
		}

		double p95Fps = 1_000.0D / p95Milliseconds;
		double lowerBound = Math.max(1.0D, effectiveTargetFps - toleranceFps);
		double upperBound = effectiveTargetFps + toleranceFps;
		if (p95Fps < lowerBound) {
			this.headroomConfirmations = 0;
			this.lastPressureNanos = nowNanos;
			if (this.currentLevel >= normalizedMaximum) {
				this.pressureConfirmations = 0;
				return new Decision(Action.HOLD, this.currentLevel);
			}
			this.pressureConfirmations = Math.min(
					PRESSURE_CONFIRMATIONS,
					this.pressureConfirmations + 1
			);
			if (this.pressureConfirmations >= PRESSURE_CONFIRMATIONS) {
				this.pressureConfirmations = 0;
				this.currentLevel++;
				return new Decision(Action.DOWNSHIFT, this.currentLevel);
			}
			return new Decision(Action.HOLD, this.currentLevel);
		}

		if (p95Fps > upperBound) {
			this.pressureConfirmations = 0;
			if (this.currentLevel <= normalizedMinimum) {
				this.headroomConfirmations = 0;
				return new Decision(Action.HOLD, this.currentLevel);
			}
			boolean delayElapsed = nowNanos - this.lastPressureNanos >= Math.max(0L, recoveryDelayNanos);
			if (!delayElapsed) {
				this.headroomConfirmations = 0;
				return new Decision(Action.HOLD, this.currentLevel);
			}
			this.headroomConfirmations = Math.min(
					HEADROOM_CONFIRMATIONS,
					this.headroomConfirmations + 1
			);
			if (this.headroomConfirmations >= HEADROOM_CONFIRMATIONS) {
				this.headroomConfirmations = 0;
				this.currentLevel--;
				return new Decision(Action.RECOVER, this.currentLevel);
			}
			return new Decision(Action.HOLD, this.currentLevel);
		}

		clearConfirmations();
		return new Decision(Action.HOLD, this.currentLevel);
	}

	int currentLevel() {
		return this.currentLevel;
	}

	private void clearConfirmations() {
		this.pressureConfirmations = 0;
		this.headroomConfirmations = 0;
	}

	static int effectiveTargetFps(
			int configuredTargetFps,
			int framerateCap,
			boolean vsyncEnabled,
			int refreshRate
	) {
		int ceiling = Integer.MAX_VALUE;
		if (configuredTargetFps > 0 && configuredTargetFps < 260) {
			ceiling = Math.min(ceiling, configuredTargetFps);
		}
		if (framerateCap > 0 && framerateCap < 260) {
			ceiling = Math.min(ceiling, framerateCap);
		}
		if (vsyncEnabled && refreshRate > 0) {
			ceiling = Math.min(ceiling, refreshRate);
		}
		return ceiling == Integer.MAX_VALUE ? UNLIMITED_TARGET : ceiling;
	}

	enum Action {
		HOLD,
		DOWNSHIFT,
		RECOVER
	}

	record Decision(Action action, int level) {
	}
}
