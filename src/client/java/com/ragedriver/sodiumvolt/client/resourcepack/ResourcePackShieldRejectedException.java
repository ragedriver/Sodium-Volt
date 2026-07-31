package com.ragedriver.sodiumvolt.client.resourcepack;

public final class ResourcePackShieldRejectedException extends RuntimeException {
	public ResourcePackShieldRejectedException() {
		super("Resource pack rejected by Resource-Pack Shield policy");
	}
}
