package com.ragedriver.sodiumvolt.client.inspector;

import java.util.Arrays;

/** Deterministic refresh phases that keep expensive Inspector samples staggered. */
final class InspectorRefreshCadence {
	static final int FRAME_AND_CHUNKS = 0;
	static final int SCENE = 1;
	static final int PARTICLES = 2;
	static final int TEXTURES = 3;
	static final int SYSTEM = 4;
	static final int PHASE_COUNT = 5;
	static final int MAXIMUM_COMPLETED_REFRESH_AGE = PHASE_COUNT - 1;

	private final long[] sampledGeneration = new long[PHASE_COUNT];
	private long generation;
	private int nextPhase;

	int beginRefresh() {
		if (this.generation == Long.MAX_VALUE) {
			Arrays.fill(this.sampledGeneration, 0L);
			this.generation = 0L;
		}
		this.generation++;
		int phase = this.nextPhase;
		this.nextPhase = (this.nextPhase + 1) % PHASE_COUNT;
		return phase;
	}

	void markSampled(int phase) {
		if (phase >= 0 && phase < PHASE_COUNT) {
			this.sampledGeneration[phase] = this.generation;
		}
	}

	int age(int phase) {
		if (phase < 0 || phase >= PHASE_COUNT || this.sampledGeneration[phase] == 0L) {
			return -1;
		}
		return (int) Math.min(Integer.MAX_VALUE, this.generation - this.sampledGeneration[phase]);
	}

	void reset() {
		Arrays.fill(this.sampledGeneration, 0L);
		this.generation = 0L;
		this.nextPhase = 0;
	}
}
