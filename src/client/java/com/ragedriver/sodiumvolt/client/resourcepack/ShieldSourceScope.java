package com.ragedriver.sodiumvolt.client.resourcepack;

import net.minecraft.server.packs.repository.PackSource;

public final class ShieldSourceScope {
	private ShieldSourceScope() {
	}

	public static ShieldSourceKind classify(PackSource source) {
		if (source == PackSource.SERVER) {
			return ShieldSourceKind.SERVER;
		}
		if (source == PackSource.DEFAULT || source == PackSource.WORLD) {
			return ShieldSourceKind.LOCAL;
		}
		return ShieldSourceKind.IGNORED;
	}
}
