package com.ragedriver.sodiumvolt.client.performance;

import java.util.Locale;

public final class VramAutoBudgetHeuristic {
	public static final int MINIMUM_BUDGET_MIB = 512;
	public static final int MAXIMUM_BUDGET_MIB = 24_576;
	public static final int FALLBACK_BUDGET_MIB = 4_096;

	private VramAutoBudgetHeuristic() {
	}

	public static int estimateMib(
			long physicalMemoryMib,
			String vendor,
			String backend,
			boolean integrated,
			boolean discrete
	) {
		if (physicalMemoryMib <= 0L) {
			return FALLBACK_BUDGET_MIB;
		}
		String safeVendor = vendor == null ? "" : vendor.toLowerCase(Locale.ROOT);
		String safeBackend = backend == null ? "" : backend.toLowerCase(Locale.ROOT);
		boolean unified = integrated
				|| safeVendor.contains("apple")
				|| safeVendor.contains("intel")
				|| safeBackend.contains("metal");
		long estimate;
		if (unified) {
			estimate = physicalMemoryMib / 4L;
			estimate = Math.min(estimate, 8_192L);
		} else {
			/*
			 * A discrete device type or vendor name does not reveal its capacity. A
			 * conservative RAM fraction avoids treating a common 4 GiB card as if it
			 * had 12+ GiB merely because the host has abundant system memory.
			 */
			estimate = physicalMemoryMib / 8L;
			estimate = Math.min(estimate, 6_144L);
		}
		return (int) Math.max(
				MINIMUM_BUDGET_MIB,
				Math.min(MAXIMUM_BUDGET_MIB, estimate)
		);
	}
}
