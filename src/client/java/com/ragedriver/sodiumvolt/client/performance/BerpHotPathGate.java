package com.ragedriver.sodiumvolt.client.performance;

/** One volatile read for each redirected block-entity extraction. */
public final class BerpHotPathGate {
	private static volatile boolean extractionActive;

	private BerpHotPathGate() {
	}

	public static boolean extractionActive() {
		return extractionActive;
	}

	static void publish(boolean active) {
		extractionActive = active;
	}
}
