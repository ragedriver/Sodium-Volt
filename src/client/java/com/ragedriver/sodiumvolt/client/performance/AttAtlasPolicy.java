package com.ragedriver.sodiumvolt.client.performance;

import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

/** Atlas definition IDs and texture IDs are distinct in Minecraft 26.3. */
public final class AttAtlasPolicy {
	private static final Identifier BLOCKS_TEXTURE = textureLocation(AtlasIds.BLOCKS);
	private static final Identifier PARTICLES_TEXTURE = textureLocation(AtlasIds.PARTICLES);

	private AttAtlasPolicy() {
	}

	public static boolean isWorldAtlasTexture(Identifier textureLocation) {
		return BLOCKS_TEXTURE.equals(textureLocation) || PARTICLES_TEXTURE.equals(textureLocation);
	}

	private static Identifier textureLocation(Identifier atlasId) {
		return atlasId.withPath(path -> "textures/atlas/" + path + ".png");
	}
}
