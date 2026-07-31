package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.performance.VisibilityAwareParticleScheduler;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ParticleGroup.class)
public abstract class ParticleGroupTickMixin {
	@Redirect(
			method = "tickParticle",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;tick()V")
	)
	private void sodiumVolt$scheduleParticleTick(Particle particle) {
		VisibilityAwareParticleScheduler.tickParticle(particle);
	}
}
