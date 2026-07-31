package com.ragedriver.sodiumvolt.client.smartfps;

public final class SmartFpsConfigNormalization {
	private SmartFpsConfigNormalization() {
	}

	public static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	public static int clampStep(int value, int minimum, int maximum, int step) {
		if (step <= 0 || maximum < minimum) {
			throw new IllegalArgumentException("Invalid stepped range");
		}
		int clamped = clamp(value, minimum, maximum);
		int steps = (clamped - minimum + step / 2) / step;
		return Math.min(maximum, minimum + steps * step);
	}
}
