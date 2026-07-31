package com.ragedriver.sodiumvolt.client.performance;

public final class BlockEntityCadenceLogic {
	private BlockEntityCadenceLogic() {
	}

	public static boolean shouldUseCache(boolean cacheEnabled, boolean distanceAware) {
		return cacheEnabled && distanceAware;
	}

	public static boolean cadenceEligible(
			boolean validCacheIdentity,
			boolean vanillaType,
			boolean includeModded
	) {
		return validCacheIdentity && (vanillaType || includeModded);
	}

	public static boolean shouldCullBeyondFar(
			boolean enabled,
			boolean vanillaType,
			boolean protectedState,
			double distanceSquared,
			double farDistanceSquared
	) {
		return enabled
				&& vanillaType
				&& !protectedState
				&& Double.isFinite(distanceSquared)
				&& distanceSquared > farDistanceSquared;
	}

	public static boolean shouldExtractFresh(
			boolean enabled,
			boolean protectedState,
			boolean distanceAware,
			boolean cacheEnabled,
			boolean cacheAvailable,
			double distanceSquared,
			double nearDistanceSquared,
			double mediumDistanceSquared,
			long gameTick,
			long lastFreshTick,
			int mediumInterval,
			int farInterval
	) {
		if (!enabled || protectedState || !distanceAware || !cacheEnabled || !cacheAvailable
				|| !Double.isFinite(distanceSquared) || distanceSquared <= nearDistanceSquared) {
			return true;
		}
		int interval = distanceSquared <= mediumDistanceSquared
				? Math.max(2, mediumInterval)
				: Math.max(4, farInterval);
		return elapsed(gameTick, lastFreshTick) >= interval;
	}

	public static long elapsed(long current, long previous) {
		return current < previous ? Long.MAX_VALUE : current - previous;
	}

	public static long saturatingAdd(long value, long increment) {
		return increment <= 0L
				? value
				: value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
	}
}
