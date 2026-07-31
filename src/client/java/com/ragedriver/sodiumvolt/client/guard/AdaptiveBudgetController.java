package com.ragedriver.sodiumvolt.client.guard;

final class AdaptiveBudgetController {
	static final double MINIMUM_SCALE = 0.35D;

	private static final long MINIMUM_FRAME_INTERVAL_NANOS = 1_000_000L;
	private static final long MAXIMUM_FRAME_INTERVAL_NANOS = 1_000_000_000L;
	private static final double FRAME_TIME_SMOOTHING = 0.10D;
	private static final double SLOW_FRAME_THRESHOLD = 1.10D;
	private static final double RECOVERY_THRESHOLD = 0.92D;
	private static final int SLOW_FRAMES_BEFORE_ADJUSTMENT = 3;
	private static final int RECOVERY_FRAMES_BEFORE_ADJUSTMENT = 15;
	private static final double PRESSURE_ADJUSTMENT = 0.25D;
	private static final double RECOVERY_STEP = 0.025D;

	private long previousFrameNanos;
	private double smoothedFrameNanos;
	private double scale = 1.0D;
	private int slowFrameCount;
	private int recoveryFrameCount;

	synchronized double update(long nowNanos, int targetFps, boolean adaptive) {
		if (!adaptive) {
			this.reset(nowNanos);
			return 1.0D;
		}

		if (this.previousFrameNanos == 0L) {
			this.previousFrameNanos = nowNanos;
			return this.scale;
		}

		long frameInterval = nowNanos - this.previousFrameNanos;
		this.previousFrameNanos = nowNanos;
		if (frameInterval < MINIMUM_FRAME_INTERVAL_NANOS || frameInterval > MAXIMUM_FRAME_INTERVAL_NANOS) {
			this.smoothedFrameNanos = 0.0D;
			this.slowFrameCount = 0;
			this.recoveryFrameCount = 0;
			return this.scale;
		}

		if (this.smoothedFrameNanos == 0.0D) {
			this.smoothedFrameNanos = frameInterval;
		} else {
			this.smoothedFrameNanos += (frameInterval - this.smoothedFrameNanos) * FRAME_TIME_SMOOTHING;
		}

		double targetFrameNanos = 1_000_000_000.0D / Math.max(1, targetFps);
		double pressure = this.smoothedFrameNanos / targetFrameNanos;
		if (pressure > SLOW_FRAME_THRESHOLD) {
			this.recoveryFrameCount = 0;
			if (++this.slowFrameCount >= SLOW_FRAMES_BEFORE_ADJUSTMENT) {
				double desiredScale = clampScale(1.0D / pressure);
				if (desiredScale < this.scale) {
					this.scale = clampScale(
							this.scale + (desiredScale - this.scale) * PRESSURE_ADJUSTMENT
					);
				}
			}
		} else if (pressure < RECOVERY_THRESHOLD) {
			this.slowFrameCount = 0;
			if (++this.recoveryFrameCount >= RECOVERY_FRAMES_BEFORE_ADJUSTMENT) {
				this.scale = Math.min(1.0D, this.scale + RECOVERY_STEP);
				this.recoveryFrameCount = 0;
			}
		} else {
			this.slowFrameCount = 0;
			this.recoveryFrameCount = 0;
		}

		return this.scale;
	}

	synchronized void disable() {
		this.reset(0L);
	}

	private void reset(long nowNanos) {
		this.previousFrameNanos = nowNanos;
		this.smoothedFrameNanos = 0.0D;
		this.scale = 1.0D;
		this.slowFrameCount = 0;
		this.recoveryFrameCount = 0;
	}

	private static double clampScale(double value) {
		return Math.clamp(value, MINIMUM_SCALE, 1.0D);
	}
}
