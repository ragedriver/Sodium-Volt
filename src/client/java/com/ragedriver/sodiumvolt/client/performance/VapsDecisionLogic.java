package com.ragedriver.sodiumvolt.client.performance;

public final class VapsDecisionLogic {
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

	public static AgeStep ageOnlyStep(int age, int lifetime) {
		boolean expires = age >= lifetime;
		int nextAge = age == Integer.MAX_VALUE ? Integer.MAX_VALUE : age + 1;
		return new AgeStep(nextAge, expires);
	}

	public static long saturatingAdd(long value, long increment) {
		if (increment <= 0L) {
			return value;
		}
		return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
	}

	public record AgeStep(int age, boolean expires) {
	}
}
