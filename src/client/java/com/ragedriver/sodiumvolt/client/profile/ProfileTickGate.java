package com.ragedriver.sodiumvolt.client.profile;

final class ProfileTickGate {
	private ProfileTickGate() {
	}

	static boolean requiresUpdate(
			boolean configuredEnabled,
			boolean observedEnabled,
			boolean recoveryOwnsGraphics
	) {
		// Keep the previous context pending; evaluate the current world after Recovery releases ownership.
		return !recoveryOwnsGraphics && (configuredEnabled || observedEnabled);
	}
}
