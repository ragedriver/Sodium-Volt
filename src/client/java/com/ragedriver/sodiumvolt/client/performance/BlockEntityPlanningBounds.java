package com.ragedriver.sodiumvolt.client.performance;

/**
 * Pure bounds used by the priority planner. One classification pass sees a
 * fixed raw-list prefix; reusable priority storage then orders that classified
 * work while entries beyond the prefix remain unclassified and fail-open.
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

	public static long maximumRawPlanningVisits(int listSize, int maximumStates) {
		return rawPrefixSize(listSize, maximumStates);
	}

	public static boolean budgetsCannotBind(
			int listSize,
			int globalBudget,
			boolean perTypeLimits,
			int perTypeLimit
	) {
		int upperBound = Math.max(0, listSize);
		return upperBound <= Math.max(1, globalBudget)
				&& (!perTypeLimits || upperBound <= Math.max(1, perTypeLimit));
	}

	public static boolean remainsFailOpen(int rawIndex, int listSize, int maximumStates) {
		return rawIndex >= rawPrefixSize(listSize, maximumStates)
				&& rawIndex >= 0
				&& rawIndex < Math.max(0, listSize);
	}
}
