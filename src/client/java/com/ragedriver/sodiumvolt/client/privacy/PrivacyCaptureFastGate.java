package com.ragedriver.sodiumvolt.client.privacy;

/**
 * One volatile read for the always-injected entity and HUD hooks. Policy and
 * state-machine work is reached only during the single privacy capture frame.
 */
public final class PrivacyCaptureFastGate {
	private static final int PENDING = 1;
	private static final int ACTIVE = 2;

	private static volatile int state;

	private PrivacyCaptureFastGate() {
	}

	public static boolean hasPendingCapture() {
		return (state & PENDING) != 0;
	}

	public static boolean isCaptureActive() {
		return (state & ACTIVE) != 0;
	}

	static void markPending() {
		state = PENDING;
	}

	static void beginCapture() {
		state = ACTIVE;
	}

	static void clear() {
		state = 0;
	}
}
