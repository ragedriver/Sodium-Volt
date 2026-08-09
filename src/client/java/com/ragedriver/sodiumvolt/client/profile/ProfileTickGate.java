package com.ragedriver.sodiumvolt.client.profile;

final class ProfileTickGate {
	private ProfileTickGate() {
	}

	static boolean requiresUpdate(
			boolean configuredEnabled,
			boolean observedEnabled
	) {
		return configuredEnabled || observedEnabled;
	}
}
