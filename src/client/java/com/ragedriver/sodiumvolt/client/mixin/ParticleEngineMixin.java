package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.guard.VoltGuardEngine;
import com.ragedriver.sodiumvolt.client.performance.VisibilityAwareParticleScheduler;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Shadow
	@Final
	private Map<ParticleRenderType, ParticleGroup<?>> particles;

	@Inject(method = "tick", at = @At("HEAD"))
	private void sodiumVolt$beginParticleSimulationTick(CallbackInfo callbackInfo) {
		VisibilityAwareParticleScheduler.beginSimulationTick();
	}

	@Inject(method = "extract", at = @At("HEAD"))
	private void sodiumVolt$beginParticleExtraction(
			ParticlesRenderState particlesRenderState,
			Frustum frustum,
			Camera camera,
			float partialTickTime,
			CallbackInfo callbackInfo
	) {
		VisibilityAwareParticleScheduler.beginParticleExtraction(
				this.particles,
				frustum,
				camera,
				VoltGuardEngine.isEnabled()
		);
		VoltGuardEngine.beginParticleExtraction(this.particles, frustum, camera);
	}

	@Inject(method = "extract", at = @At("RETURN"))
	private void sodiumVolt$endParticleExtraction(
			ParticlesRenderState particlesRenderState,
			Frustum frustum,
			Camera camera,
			float partialTickTime,
			CallbackInfo callbackInfo
	) {
		VoltGuardEngine.endParticleExtraction();
		VisibilityAwareParticleScheduler.endParticleExtraction();
	}

	@Inject(method = "setLevel", at = @At("HEAD"))
	private void sodiumVolt$resetParticleScheduler(ClientLevel level, CallbackInfo callbackInfo) {
		VisibilityAwareParticleScheduler.onLevelChanged();
	}
}
