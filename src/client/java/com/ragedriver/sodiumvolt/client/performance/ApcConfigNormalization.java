package com.ragedriver.sodiumvolt.client.performance;

import com.ragedriver.sodiumvolt.client.config.VoltPerformanceConfig;

public final class ApcConfigNormalization {
	private ApcConfigNormalization() {
	}

	public static DistanceBounds normalizeDistanceBounds(int minimum, int maximum) {
		int normalizedMinimum = clamp(
				minimum,
				VoltPerformanceConfig.MIN_RENDER_DISTANCE_MIN,
				VoltPerformanceConfig.MIN_RENDER_DISTANCE_MAX
		);
		int normalizedMaximum = clamp(
				maximum,
				VoltPerformanceConfig.MAX_RENDER_DISTANCE_MIN,
				VoltPerformanceConfig.MAX_RENDER_DISTANCE_MAX
		);
		if (normalizedMinimum > normalizedMaximum) {
			normalizedMinimum = normalizedMaximum;
		}
		return new DistanceBounds(normalizedMinimum, normalizedMaximum);
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	public record DistanceBounds(int minimum, int maximum) {
	}
}
