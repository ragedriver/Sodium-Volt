package com.ragedriver.sodiumvolt.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ragedriver.sodiumvolt.client.performance.AdaptivePerformanceController;
import com.ragedriver.sodiumvolt.client.performance.AnimatedTextureThrottleEngine;
import com.ragedriver.sodiumvolt.client.performance.AttAnimationCycleContext;
import com.ragedriver.sodiumvolt.client.performance.AttAnimationStateExtension;
import com.ragedriver.sodiumvolt.client.performance.AttMappingFailOpenLatch;
import com.ragedriver.sodiumvolt.client.performance.AttStateSpriteMapping;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasAnimationMixin {
	@Shadow
	private List<TextureAtlasSprite> sprites;
	@Shadow
	private List<SpriteContents.AnimationState> animatedTexturesStates;

	@Unique
	private int sodiumVolt$animationThrottlePhase;
	@Unique
	private AttStateSpriteMapping<SpriteContents.AnimationState, TextureAtlasSprite>
			sodiumVolt$attMapping;
	@Unique
	private int sodiumVolt$attWarmupCycles;
	@Unique
	private final AttMappingFailOpenLatch sodiumVolt$attMappingFailure =
			new AttMappingFailOpenLatch();

	@WrapMethod(method = "cycleAnimationFrames()V")
	private void sodiumVolt$cycleAnimationFrames(Operation<Void> original) {
		if (!AnimatedTextureThrottleEngine.isConfiguredEnabled()) {
			this.sodiumVolt$attMappingFailure.observeMasterDisabled();
			this.sodiumVolt$releaseAttMapping();
			if (!this.sodiumVolt$shouldSkipLegacyApcCycle()) {
				original.call();
			}
			return;
		}
		this.sodiumVolt$animationThrottlePhase = 0;
		if (!AnimatedTextureThrottleEngine.canSchedule()) {
			original.call();
			return;
		}
		if (this.sodiumVolt$attMapping == null && this.sodiumVolt$attMappingFailure.canBuild()) {
			this.sodiumVolt$buildAttMapping();
		}
		if (this.sodiumVolt$attMapping == null || !this.sodiumVolt$attMapping.isValid()) {
			original.call();
			return;
		}

		AnimatedTextureThrottleEngine.beginAtlasCycle();
		TextureAtlas atlas = (TextureAtlas) (Object) this;
		boolean installed = AttAnimationCycleContext.push(
				atlas.location(),
				this.sodiumVolt$attWarmupCycles > 0
		);
		try {
			original.call();
		} finally {
			AttAnimationCycleContext.pop(installed);
			if (this.sodiumVolt$attWarmupCycles > 0) {
				this.sodiumVolt$attWarmupCycles--;
			}
		}
	}

	@Inject(method = "upload(Lnet/minecraft/client/renderer/texture/SpriteLoader$Preparations;)V",
			at = @At("RETURN"))
	private void sodiumVolt$rebuildAttMappingAfterUpload(CallbackInfo callbackInfo) {
		if (AnimatedTextureThrottleEngine.isConfiguredEnabled()) {
			this.sodiumVolt$attMappingFailure.resetForUpload();
			this.sodiumVolt$buildAttMapping();
		}
	}

	@Inject(method = "clearTextureData()V", at = @At("HEAD"))
	private void sodiumVolt$releaseAttMappingBeforeClear(CallbackInfo callbackInfo) {
		this.sodiumVolt$releaseAttMapping();
		this.sodiumVolt$attMappingFailure.blockUntilUpload();
	}

	@Unique
	private void sodiumVolt$buildAttMapping() {
		this.sodiumVolt$releaseAttMapping();
		try {
			this.sodiumVolt$attMapping = AnimatedTextureThrottleEngine.buildMapping(
					this.sprites,
					this.animatedTexturesStates
			);
			if (this.sodiumVolt$attMapping.isValid()) {
				for (int index = 0; index < this.sodiumVolt$attMapping.size(); index++) {
					((AttAnimationStateExtension) this.sodiumVolt$attMapping.stateAt(index))
							.sodiumVolt$setAnimatedSprite(this.sodiumVolt$attMapping.spriteAt(index));
				}
				this.sodiumVolt$attWarmupCycles = AnimatedTextureThrottleEngine.WARMUP_CYCLES;
			} else {
				this.sodiumVolt$latchMappingFailure();
				this.sodiumVolt$releaseAttMapping();
			}
		} catch (RuntimeException | LinkageError exception) {
			AnimatedTextureThrottleEngine.fail(exception);
			this.sodiumVolt$releaseAttMapping();
		}
	}

	@Unique
	private void sodiumVolt$latchMappingFailure() {
		if (this.sodiumVolt$attMappingFailure.failOpen()) {
			AnimatedTextureThrottleEngine.recordMappingFallback();
		}
	}

	@Unique
	private void sodiumVolt$releaseAttMapping() {
		if (this.sodiumVolt$attMapping != null) {
			try {
				for (int index = 0; index < this.sodiumVolt$attMapping.size(); index++) {
					AttAnimationStateExtension state =
							(AttAnimationStateExtension) this.sodiumVolt$attMapping.stateAt(index);
					if (state.sodiumVolt$getAnimatedSprite()
							== this.sodiumVolt$attMapping.spriteAt(index)) {
						state.sodiumVolt$setAnimatedSprite(null);
					}
				}
			} catch (RuntimeException | LinkageError exception) {
				AnimatedTextureThrottleEngine.fail(exception);
			} finally {
				this.sodiumVolt$attMapping.release();
				this.sodiumVolt$attMapping = null;
			}
		}
		this.sodiumVolt$attWarmupCycles = 0;
	}

	@Unique
	private boolean sodiumVolt$shouldSkipLegacyApcCycle() {
		TextureAtlas atlas = (TextureAtlas) (Object) this;
		Identifier location = atlas.location();
		boolean eligibleAtlas = TextureAtlas.LOCATION_BLOCKS.equals(location)
				|| TextureAtlas.LOCATION_PARTICLES.equals(location);
		if (!eligibleAtlas || !AdaptivePerformanceController.shouldThrottleAtlasAnimations()) {
			this.sodiumVolt$animationThrottlePhase = 0;
			return false;
		}
		this.sodiumVolt$animationThrottlePhase ^= 1;
		return this.sodiumVolt$animationThrottlePhase != 0;
	}
}
