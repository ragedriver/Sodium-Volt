package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.guard.VoltGuardEngine;
import com.ragedriver.sodiumvolt.client.guard.VoltGuardHotPathGate;
import com.ragedriver.sodiumvolt.client.performance.VisibilityAwareParticleScheduler;
import com.ragedriver.sodiumvolt.client.performance.VapsHotPathGate;
import net.minecraft.client.particle.ElderGuardianParticle;
import net.minecraft.client.particle.ElderGuardianParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Queue;
import java.util.stream.Stream;

@Mixin(ElderGuardianParticleGroup.class)
public abstract class ElderGuardianParticleGroupMixin {
	@Redirect(
			method = "extractRenderState",
			at = @At(value = "INVOKE", target = "Ljava/util/Queue;stream()Ljava/util/stream/Stream;")
	)
	private Stream<ElderGuardianParticle> sodiumVolt$limitExtractedParticles(
			Queue<ElderGuardianParticle> particles
	) {
		Stream<ElderGuardianParticle> stream = particles.stream();
		boolean vaps = VapsHotPathGate.renderActive();
		boolean guard = VoltGuardHotPathGate.particleFilterActive();
		return vaps || guard
				? stream.filter(particle -> (!vaps
						|| VisibilityAwareParticleScheduler.shouldRenderParticle(particle))
						&& (!guard || VoltGuardEngine.shouldExtractParticle(particle)))
				: stream;
	}
}
