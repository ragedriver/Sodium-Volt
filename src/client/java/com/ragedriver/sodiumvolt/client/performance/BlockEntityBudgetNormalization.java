package com.ragedriver.sodiumvolt.client.performance;

import com.ragedriver.sodiumvolt.client.config.VoltPerformanceConfig;

public final class BlockEntityBudgetNormalization {
	private BlockEntityBudgetNormalization() {
	}

	public static Distances normalizeDistances(int near, int medium, int far) {
		int normalizedNear = clampToStep(
				near,
				VoltPerformanceConfig.BERP_NEAR_DISTANCE_MIN,
				VoltPerformanceConfig.BERP_NEAR_DISTANCE_MAX,
				VoltPerformanceConfig.BERP_NEAR_DISTANCE_STEP
		);
		int normalizedMedium = Math.max(
				normalizedNear,
				clampToStep(
						medium,
						VoltPerformanceConfig.BERP_MEDIUM_DISTANCE_MIN,
						VoltPerformanceConfig.BERP_MEDIUM_DISTANCE_MAX,
						VoltPerformanceConfig.BERP_MEDIUM_DISTANCE_STEP
				)
		);
		int normalizedFar = Math.max(
				normalizedMedium,
				clampToStep(
						far,
						VoltPerformanceConfig.BERP_FAR_DISTANCE_MIN,
						VoltPerformanceConfig.BERP_FAR_DISTANCE_MAX,
						VoltPerformanceConfig.BERP_FAR_DISTANCE_STEP
				)
		);
		return new Distances(normalizedNear, normalizedMedium, normalizedFar);
	}

	private static int clampToStep(int value, int minimum, int maximum, int step) {
		int clamped = Math.max(minimum, Math.min(maximum, value));
		int steps = (clamped - minimum + step / 2) / step;
		return Math.min(maximum, minimum + steps * step);
	}

	public record Distances(int near, int medium, int far) {
	}
}
