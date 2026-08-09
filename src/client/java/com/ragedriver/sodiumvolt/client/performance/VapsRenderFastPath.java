package com.ragedriver.sodiumvolt.client.performance;

/**
 * Pure upper-bound checks for deciding when VAPS render planning cannot reject
 * a particle. Distance-aware simulation is intentionally outside this gate.
 */
final class VapsRenderFastPath {
	/** Below this total, a second visibility traversal costs more than it saves. */
	static final int NORMAL_LOAD_VISIBILITY_THRESHOLD = 512;
	private VapsRenderFastPath() {
	}

	static boolean canSkipPlanning(
			boolean skipBehindCamera,
			boolean perTypeLimits,
			int perTypeLimit,
			boolean ambientCoalescing,
			int ambientPerCell,
			int particleCountUpperBound
	) {
		int upperBound = Math.max(0, particleCountUpperBound);
		return (!skipBehindCamera || upperBound <= NORMAL_LOAD_VISIBILITY_THRESHOLD)
				&& (!perTypeLimits || upperBound <= Math.max(1, perTypeLimit))
				&& (!ambientCoalescing || upperBound <= Math.max(1, ambientPerCell));
	}

	static int saturatingAdd(int current, int increment) {
		int safeCurrent = Math.max(0, current);
		int safeIncrement = Math.max(0, increment);
		return safeCurrent >= Integer.MAX_VALUE - safeIncrement
				? Integer.MAX_VALUE
				: safeCurrent + safeIncrement;
	}
}
