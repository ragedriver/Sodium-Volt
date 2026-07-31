package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.performance.AnimatedTextureThrottleEngine;
import com.ragedriver.sodiumvolt.client.performance.AttAnimationCycleContext;
import com.ragedriver.sodiumvolt.client.performance.AttAnimationStateExtension;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteContents.AnimationState.class)
public abstract class SpriteContentsAnimationStateAttMixin
		implements AttAnimationStateExtension {
	@Unique
	private TextureAtlasSprite sodiumVolt$animatedSprite;
	@Unique
	private TextureAtlasSprite sodiumVolt$pendingCompletedTick;

	@Override
	public TextureAtlasSprite sodiumVolt$getAnimatedSprite() {
		return this.sodiumVolt$animatedSprite;
	}

	@Override
	public void sodiumVolt$setAnimatedSprite(TextureAtlasSprite sprite) {
		this.sodiumVolt$animatedSprite = sprite;
		if (sprite == null) {
			this.sodiumVolt$pendingCompletedTick = null;
		}
	}

	@Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
	private void sodiumVolt$gateAnimatedTextureTick(CallbackInfo callbackInfo) {
		this.sodiumVolt$pendingCompletedTick = null;
		Identifier atlasLocation = AttAnimationCycleContext.atlasLocation();
		if (atlasLocation == null || this.sodiumVolt$animatedSprite == null) {
			return;
		}
		AnimatedTextureThrottleEngine.TickGate gate =
				AnimatedTextureThrottleEngine.gateStateTick(
						atlasLocation,
						this.sodiumVolt$animatedSprite,
						AttAnimationCycleContext.isWarmup()
				);
		if (gate == AnimatedTextureThrottleEngine.TickGate.SKIP) {
			callbackInfo.cancel();
		} else if (gate == AnimatedTextureThrottleEngine.TickGate.ALLOW_TRACKED) {
			this.sodiumVolt$pendingCompletedTick = this.sodiumVolt$animatedSprite;
		}
	}

	@Inject(method = "tick()V", at = @At("RETURN"))
	private void sodiumVolt$completeAnimatedTextureTick(CallbackInfo callbackInfo) {
		TextureAtlasSprite completed = this.sodiumVolt$pendingCompletedTick;
		this.sodiumVolt$pendingCompletedTick = null;
		if (completed != null) {
			AnimatedTextureThrottleEngine.completeStateTick(completed);
		}
	}
}
