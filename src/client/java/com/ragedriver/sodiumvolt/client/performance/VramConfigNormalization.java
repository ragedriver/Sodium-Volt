package com.ragedriver.sodiumvolt.client.performance;

public final class VramConfigNormalization {
	private VramConfigNormalization() {
	}

	public static Thresholds normalizeThresholds(int protection, int critical) {
		int safeProtection = clamp(protection, 60, 90);
		int minimumCritical = Math.min(98, safeProtection + 5);
		int safeCritical = Math.max(minimumCritical, clamp(critical, 75, 98));
		return new Thresholds(safeProtection, safeCritical);
	}

	public static int clampStep(int value, int minimum, int maximum, int step) {
		int clamped = clamp(value, minimum, maximum);
		int steps = (clamped - minimum + step / 2) / step;
		return Math.min(maximum, minimum + steps * step);
	}

	public static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	public record Thresholds(int protection, int critical) {
	}
}
