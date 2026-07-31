package com.ragedriver.sodiumvolt.client.smartfps;

public final class SmartFpsPolicy {
	public static final int NO_CAP = Integer.MAX_VALUE;
	public static final int REASON_MINIMIZED = 1;
	public static final int REASON_UNFOCUSED = 1 << 1;
	public static final int REASON_BATTERY = 1 << 2;
	public static final int REASON_LOW_BATTERY = 1 << 3;

	private long unfocusedSinceNanos = Long.MIN_VALUE;
	private int smartCap = NO_CAP;
	private int effectiveLimit = NO_CAP;
	private int reasons;
	private boolean suspendApcSampling;

	public int evaluate(
			int vanillaLimit,
			long nowNanos,
			boolean masterEnabled,
			boolean minimized,
			boolean focused,
			boolean throttleMinimized,
			int minimizedTarget,
			boolean throttleUnfocused,
			int unfocusedTarget,
			long unfocusedDelayNanos,
			boolean batteryMode,
			int batteryTarget,
			boolean bypassWhileCharging,
			boolean lowBatteryProtection,
			int lowBatteryThreshold,
			int lowBatteryTarget,
			SmartFpsPowerSnapshot power
	) {
		this.smartCap = NO_CAP;
		this.reasons = 0;
		this.suspendApcSampling = false;
		if (!masterEnabled) {
			resetBackgroundDelay();
			this.effectiveLimit = vanillaLimit;
			return vanillaLimit;
		}

		if (minimized) {
			resetBackgroundDelay();
			if (throttleMinimized) {
				addCap(Math.max(1, minimizedTarget), REASON_MINIMIZED);
			}
		} else if (focused || !throttleUnfocused) {
			resetBackgroundDelay();
		} else {
			if (this.unfocusedSinceNanos == Long.MIN_VALUE || nowNanos < this.unfocusedSinceNanos) {
				this.unfocusedSinceNanos = nowNanos;
			}
			long elapsed = Math.max(0L, nowNanos - this.unfocusedSinceNanos);
			if (elapsed >= Math.max(0L, unfocusedDelayNanos)) {
				addCap(Math.max(1, unfocusedTarget), REASON_UNFOCUSED);
			}
		}

		SmartFpsPowerSnapshot safePower =
				power == null ? SmartFpsPowerSnapshot.UNKNOWN : power;
		if (batteryMode && safePower.isKnown()) {
			boolean bypass = safePower.state() == SmartFpsPowerSnapshot.PowerState.CHARGING
					&& bypassWhileCharging;
			if (!bypass) {
				addCap(Math.max(1, batteryTarget), REASON_BATTERY);
				if (lowBatteryProtection
						&& safePower.percentage() <= lowBatteryThreshold) {
					addCap(Math.max(1, lowBatteryTarget), REASON_LOW_BATTERY);
				}
			}
		}

		this.effectiveLimit = Math.min(vanillaLimit, this.smartCap);
		this.suspendApcSampling = this.reasons != 0;
		return this.effectiveLimit;
	}

	public int smartCap() {
		return this.smartCap;
	}

	public int effectiveLimit() {
		return this.effectiveLimit;
	}

	public int reasons() {
		return this.reasons;
	}

	public boolean shouldSuspendApcSampling() {
		return this.suspendApcSampling;
	}

	public void reset() {
		resetBackgroundDelay();
		this.smartCap = NO_CAP;
		this.effectiveLimit = NO_CAP;
		this.reasons = 0;
		this.suspendApcSampling = false;
	}

	private void addCap(int candidate, int reason) {
		this.smartCap = Math.min(this.smartCap, candidate);
		this.reasons |= reason;
	}

	private void resetBackgroundDelay() {
		this.unfocusedSinceNanos = Long.MIN_VALUE;
	}
}
