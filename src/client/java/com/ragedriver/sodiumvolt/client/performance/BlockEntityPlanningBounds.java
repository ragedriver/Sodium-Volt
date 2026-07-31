package com.ragedriver.sodiumvolt.client.performance;

/**
 * Pure bounds used by the priority planner. Every pass sees the same fixed raw
 * list prefix so an early priority class cannot consume later classes' scan
 * opportunity, while entries beyond the prefix remain unclassified/fail-open.
 */
public final class BlockEntityPlanningBounds {
	private BlockEntityPlanningBounds() {
	}

	public static int rawPrefixSize(int listSize, int maximumStates) {
		return Math.min(Math.max(0, listSize), Math.max(0, maximumStates));
	}

	public static boolean hasFailOpenOverflow(int listSize, int maximumStates) {
		return Math.max(0, listSize) > Math.max(0, maximumStates);
	}

	public static long maximumRawVisits(int listSize, int passes, int maximumStates) {
		return (long) rawPrefixSize(listSize, maximumStates) * Math.max(0, passes);
	}

	public static boolean remainsFailOpen(int rawIndex, int listSize, int maximumStates) {
		return rawIndex >= rawPrefixSize(listSize, maximumStates)
				&& rawIndex >= 0
				&& rawIndex < Math.max(0, listSize);
	}
}
