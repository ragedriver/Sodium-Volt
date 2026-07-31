package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.performance.AnimatedTextureThrottleEngine;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class RenderSectionManagerAttMixin {
	@Shadow
	private Vector3dc cameraPosition;

	@Inject(method = "tickVisibleRenders()V", at = @At("HEAD"))
	private void sodiumVolt$beginAttVisibilityScan(CallbackInfo callbackInfo) {
		AnimatedTextureThrottleEngine.beginVisibilityScan(this.cameraPosition);
	}

	@Redirect(
			method = "tickVisibleRenders()V",
			require = 1,
			at = @At(
					value = "INVOKE",
					target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;getAnimatedSprites(I)[Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
			)
	)
	private TextureAtlasSprite[] sodiumVolt$captureAnimatedSpriteDistance(
			RenderRegion region,
			int sectionIndex
	) {
		TextureAtlasSprite[] sprites = region.getAnimatedSprites(sectionIndex);
		AnimatedTextureThrottleEngine.recordVisibleSection(
				region,
				sectionIndex,
				sprites,
				this.cameraPosition
		);
		return sprites;
	}

	@Inject(method = "tickVisibleRenders()V", at = @At("RETURN"))
	private void sodiumVolt$endAttVisibilityScan(CallbackInfo callbackInfo) {
		AnimatedTextureThrottleEngine.endVisibilityScan();
	}
}
