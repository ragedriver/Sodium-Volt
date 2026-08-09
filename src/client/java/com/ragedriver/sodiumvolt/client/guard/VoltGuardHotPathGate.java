package com.ragedriver.sodiumvolt.client.guard;

/** Prevents dormant Guard hooks from initializing their render-thread state. */
public final class VoltGuardHotPathGate {
	private static final int RUNTIME = 1;
	private static final int PARTICLE_FILTER = 2;

	private static volatile int state;

	private VoltGuardHotPathGate() {
	}

	public static boolean runtimeActive() {
		return (state & RUNTIME) != 0;
	}

	public static boolean particleFilterActive() {
		return (state & PARTICLE_FILTER) != 0;
	}

	static void publishRuntime(boolean active) {
		int current = state;
		state = active ? current | RUNTIME : current & ~RUNTIME;
	}

	static void publishParticleFilter(boolean active) {
		int current = state;
		state = active ? current | PARTICLE_FILTER : current & ~PARTICLE_FILTER;
	}

	static void clear() {
		state = 0;
	}
}
