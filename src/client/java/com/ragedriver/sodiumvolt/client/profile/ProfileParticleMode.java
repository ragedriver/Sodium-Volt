package com.ragedriver.sodiumvolt.client.profile;

public enum ProfileParticleMode {
	ALL("all"),
	DECREASED("decreased"),
	MINIMAL("minimal");

	private final String serializedName;

	ProfileParticleMode(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return this.serializedName;
	}

	public static ProfileParticleMode parse(String value) {
		for (ProfileParticleMode mode : values()) {
			if (mode.serializedName.equals(value)) {
				return mode;
			}
		}
		throw new IllegalArgumentException("Unknown profile particle mode");
	}
}
