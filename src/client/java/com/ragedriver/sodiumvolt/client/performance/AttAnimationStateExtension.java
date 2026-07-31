package com.ragedriver.sodiumvolt.client.performance;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Binds an atlas animation state to the sprite it advances. The binding is
 * owned by the atlas and cleared whenever that atlas releases its texture
 * data, so it cannot retain an obsolete resource-pack sprite graph.
 */
public interface AttAnimationStateExtension {
	TextureAtlasSprite sodiumVolt$getAnimatedSprite();

	void sodiumVolt$setAnimatedSprite(TextureAtlasSprite sprite);
}
