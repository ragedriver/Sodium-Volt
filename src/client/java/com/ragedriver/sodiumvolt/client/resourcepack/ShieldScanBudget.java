package com.ragedriver.sodiumvolt.client.resourcepack;

public final class ShieldScanBudget {
	private ShieldScanBudget() {
	}

	public static Allowance allowance(
			ResourcePackShieldPolicy policy,
			long aggregateDeadlineNanos,
			long nowNanos,
			boolean aggregateActive
	) {
		if (!aggregateActive || aggregateDeadlineNanos == 0L) {
			return new Allowance(
					policy,
					false,
					deadline(nowNanos, policy.maximumScanNanos())
			);
		}
		long remaining = aggregateDeadlineNanos - nowNanos;
		return remaining <= 0L
				? new Allowance(
						policy.withMaximumScanNanos(1L), true, aggregateDeadlineNanos
				)
				: new Allowance(
						policy.withMaximumScanNanos(remaining),
						false,
						aggregateDeadlineNanos
				);
	}

	private static long deadline(long nowNanos, long durationNanos) {
		return nowNanos >= Long.MAX_VALUE - durationNanos
				? Long.MAX_VALUE
				: nowNanos + durationNanos;
	}

	public record Allowance(
			ResourcePackShieldPolicy policy,
			boolean expired,
			long deadlineNanos
	) {
	}
}
