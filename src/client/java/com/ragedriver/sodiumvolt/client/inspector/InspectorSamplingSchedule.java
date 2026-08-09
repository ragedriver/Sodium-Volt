package com.ragedriver.sodiumvolt.client.inspector;

/**
 * Render-thread sampling cadence and timing baseline for the Inspector HUD.
 * Hidden time is never folded into a later frame sample.
 */
final class InspectorSamplingSchedule {
	private boolean visible;
	private boolean wasPaused;
	private boolean baselineReady;
	private boolean refreshScheduled;
	private long previousFrameNanos;
	private long nextRefreshNanos;

	static boolean shouldSample(
			boolean inspectorEnabled,
			boolean overlayEnabled,
			boolean privacyCaptureActive,
			boolean levelPresent
	) {
		return inspectorEnabled && overlayEnabled && !privacyCaptureActive && levelPresent;
	}

	boolean show() {
		if (this.visible) {
			return false;
		}
		this.visible = true;
		resetTiming();
		return true;
	}

	boolean hide() {
		if (!this.visible) {
			return false;
		}
		this.visible = false;
		resetTiming();
		return true;
	}

	void resetForLevel() {
		resetTiming();
	}

	long recordFrame(long nowNanos, boolean paused) {
		if (!this.visible) {
			return 0L;
		}
		if (paused) {
			this.baselineReady = false;
			this.wasPaused = true;
			return 0L;
		}
		if (this.wasPaused) {
			this.previousFrameNanos = nowNanos;
			this.baselineReady = true;
			this.wasPaused = false;
			return 0L;
		}
		if (!this.baselineReady) {
			this.previousFrameNanos = nowNanos;
			this.baselineReady = true;
			return 0L;
		}
		long previous = this.previousFrameNanos;
		this.previousFrameNanos = nowNanos;
		return nowNanos - previous;
	}

	boolean claimRefresh(long nowNanos, long intervalNanos) {
		if (!this.visible) {
			return false;
		}
		if (this.refreshScheduled && nowNanos < this.nextRefreshNanos) {
			return false;
		}
		this.nextRefreshNanos = saturatingDeadline(nowNanos, intervalNanos);
		this.refreshScheduled = true;
		return true;
	}

	static long saturatingDeadline(long nowNanos, long intervalNanos) {
		long delay = Math.max(0L, intervalNanos);
		return nowNanos > Long.MAX_VALUE - delay ? Long.MAX_VALUE : nowNanos + delay;
	}

	long nextRefreshNanosForTesting() {
		return this.nextRefreshNanos;
	}

	private void resetTiming() {
		this.wasPaused = false;
		this.baselineReady = false;
		this.refreshScheduled = false;
		this.previousFrameNanos = 0L;
		this.nextRefreshNanos = 0L;
	}
}
