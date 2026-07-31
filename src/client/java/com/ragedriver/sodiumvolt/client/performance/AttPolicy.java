package com.ragedriver.sodiumvolt.client.performance;

public final class AttPolicy {
	private AttPolicy() {
	}

	public static Decision decide(
			boolean enabled,
			boolean warmup,
			boolean interfaceProtected,
			boolean screenProtected,
			boolean unknownActive,
			boolean exemptionCandidate,
			boolean exemptionReserveGranted,
			boolean visible,
			boolean pauseInvisible,
			int keepaliveTicks,
			long clientTick,
			long lastVisibleTick,
			boolean distanceAware,
			double distanceSquared,
			double fullSpeedDistanceSquared,
			int distantInterval,
			boolean immediateResume,
			boolean resumePending,
			boolean apcPressure,
			boolean normalBudgetAvailable
	) {
		if (!enabled) {
			return Decision.TICK_FAIL_OPEN;
		}
		if (warmup || interfaceProtected || screenProtected || unknownActive) {
			return Decision.TICK_PROTECTED;
		}
		if (exemptionCandidate && exemptionReserveGranted) {
			return Decision.TICK_EXEMPT;
		}

		boolean recentlyVisible = keepaliveTicks > 0
				&& elapsed(clientTick, lastVisibleTick) <= keepaliveTicks;
		if (!exemptionCandidate && !visible && !recentlyVisible && pauseInvisible) {
			return Decision.SKIP_INVISIBLE;
		}

		boolean due = exemptionCandidate || immediateResume && resumePending;
		if (!due) {
			int interval = 1;
			if (visible && distanceAware
					&& Double.isFinite(distanceSquared)
					&& distanceSquared > fullSpeedDistanceSquared) {
				interval = Math.max(interval, Math.max(2, distantInterval));
			}
			if (apcPressure) {
				interval = Math.max(interval, 2);
			}
			due = interval <= 1 || Math.floorMod(clientTick, interval) == 0;
		}
		if (!due) {
			return Decision.SKIP_CADENCE;
		}
		return normalBudgetAvailable ? Decision.TICK_NORMAL : Decision.SKIP_BUDGET;
	}

	public static long elapsed(long current, long previous) {
		if (previous == Long.MIN_VALUE || current < previous) {
			return Long.MAX_VALUE;
		}
		long difference = current - previous;
		return difference < 0L ? Long.MAX_VALUE : difference;
	}

	public static long saturatingAdd(long value, long increment) {
		return increment <= 0L
				? value
				: value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
	}

	public enum Decision {
		TICK_FAIL_OPEN,
		TICK_PROTECTED,
		TICK_EXEMPT,
		TICK_NORMAL,
		SKIP_INVISIBLE,
		SKIP_CADENCE,
		SKIP_BUDGET
	}
}
