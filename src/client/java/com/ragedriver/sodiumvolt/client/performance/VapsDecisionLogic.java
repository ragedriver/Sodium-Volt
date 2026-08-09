package com.ragedriver.sodiumvolt.client.performance;

public final class VapsDecisionLogic {
	private static final long EXPIRES_FLAG = 1L << Integer.SIZE;
	private static final long AGE_MASK = 0xFFFF_FFFFL;

	private VapsDecisionLogic() {
	}

	public static boolean shouldRunFullTick(
			boolean enabled,
			boolean critical,
			double distanceSquared,
			double fullRateDistanceSquared,
			int farTickInterval,
			int age
	) {
		if (!enabled || critical || !Double.isFinite(distanceSquared)
				|| distanceSquared <= fullRateDistanceSquared) {
			return true;
		}
		int interval = Math.max(2, farTickInterval);
		return Math.floorMod(age, interval) == 0;
	}

	public static long ageOnlyStep(int age, int lifetime) {
		boolean expires = age >= lifetime;
		int nextAge = age == Integer.MAX_VALUE ? Integer.MAX_VALUE : age + 1;
		return Integer.toUnsignedLong(nextAge) | (expires ? EXPIRES_FLAG : 0L);
	}

	public static int ageOnlyStepAge(long step) {
		return (int) (step & AGE_MASK);
	}

	public static boolean ageOnlyStepExpires(long step) {
		return (step & EXPIRES_FLAG) != 0L;
	}

	public static long saturatingAdd(long value, long increment) {
		if (increment <= 0L) {
			return value;
		}
		return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
	}

}
