package com.ragedriver.sodiumvolt.client.performance;

import com.ragedriver.sodiumvolt.client.privacy.PrivacyCaptureFastGate;

/** Cached Inspector visibility demand, published once by the HUD lifecycle. */
public final class InspectorStatisticsDemand {
	private static volatile boolean requested;

	private InspectorStatisticsDemand() {
	}

	public static boolean isRequested() {
		return requested && !PrivacyCaptureFastGate.isCaptureActive();
	}

	public static boolean publish(boolean visible) {
		if (requested == visible) {
			return false;
		}
		requested = visible;
		return true;
	}
}
