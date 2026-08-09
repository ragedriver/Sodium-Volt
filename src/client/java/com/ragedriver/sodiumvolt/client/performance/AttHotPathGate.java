package com.ragedriver.sodiumvolt.client.performance;

/** One volatile read for Sodium's per-section and per-sprite visibility hooks. */
public final class AttHotPathGate {
	private static volatile boolean visibilityScanActive;

	private AttHotPathGate() {
	}

	public static boolean visibilityScanActive() {
		return visibilityScanActive;
	}

	static void publishVisibilityScan(boolean active) {
		visibilityScanActive = active;
	}
}
