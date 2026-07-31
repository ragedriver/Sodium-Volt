package com.ragedriver.sodiumvolt.client.profile;

public final class ProfileContextPolicy {
	private ProfileContextPolicy() {
	}

	public static boolean isEnabled(
			Kind kind,
			boolean singlePlayerProfilesEnabled,
			boolean serverProfilesEnabled
	) {
		return switch (kind) {
			case MENU -> false;
			case SINGLE_PLAYER -> singlePlayerProfilesEnabled;
			case SERVER -> serverProfilesEnabled;
		};
	}

	public enum Kind {
		MENU,
		SINGLE_PLAYER,
		SERVER
	}
}
