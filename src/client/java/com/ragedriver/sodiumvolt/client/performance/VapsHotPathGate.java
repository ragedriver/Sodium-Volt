package com.ragedriver.sodiumvolt.client.performance;

/** One volatile bit-mask read for the always-injected particle hooks. */
public final class VapsHotPathGate {
	private static final int SIMULATION = 1;
	private static final int RENDER = 2;

	private static volatile int state;

	private VapsHotPathGate() {
	}

	public static boolean simulationActive() {
		return (state & SIMULATION) != 0;
	}

	public static boolean renderActive() {
		return (state & RENDER) != 0;
	}

	static void publishSimulation(boolean active) {
		int current = state;
		state = active ? current | SIMULATION : current & ~SIMULATION;
	}

	static void publishRender(boolean active) {
		int current = state;
		state = active ? current | RENDER : current & ~RENDER;
	}

	static void clear() {
		state = 0;
	}
}
