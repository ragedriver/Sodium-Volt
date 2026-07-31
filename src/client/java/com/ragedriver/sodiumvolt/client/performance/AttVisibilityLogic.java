package com.ragedriver.sodiumvolt.client.performance;

public final class AttVisibilityLogic {
	private AttVisibilityLogic() {
	}

	public static int nextGeneration(int generation) {
		return generation == Integer.MAX_VALUE ? 1 : generation + 1;
	}

	public static float minimumDistance(float current, float candidate) {
		float safeCandidate = Float.isFinite(candidate) ? Math.max(0.0F, candidate) : 0.0F;
		return Math.min(current, safeCandidate);
	}

	public static boolean isNewlyVisible(int spriteGeneration, int previousGeneration) {
		return spriteGeneration != previousGeneration;
	}

	public static boolean canPublishGeneration(
			boolean scanActive,
			boolean failed,
			boolean truncated
	) {
		return scanActive && !failed && !truncated;
	}
}
