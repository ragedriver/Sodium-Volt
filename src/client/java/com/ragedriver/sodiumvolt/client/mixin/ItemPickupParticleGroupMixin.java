package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.guard.VoltGuardEngine;
import com.ragedriver.sodiumvolt.client.performance.VisibilityAwareParticleScheduler;
import net.minecraft.client.particle.ItemPickupParticle;
import net.minecraft.client.particle.ItemPickupParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Queue;
import java.util.stream.Stream;

@Mixin(ItemPickupParticleGroup.class)
public abstract class ItemPickupParticleGroupMixin {
	@Redirect(
			method = "extractRenderState",
			at = @At(value = "INVOKE", target = "Ljava/util/Queue;stream()Ljava/util/stream/Stream;")
	)
	private Stream<ItemPickupParticle> sodiumVolt$limitExtractedParticles(Queue<ItemPickupParticle> particles) {
		Stream<ItemPickupParticle> stream = particles.stream();
		return VoltGuardEngine.isEnabled() || VisibilityAwareParticleScheduler.isEnabled()
				? stream.filter(particle -> VisibilityAwareParticleScheduler.shouldRenderParticle(particle)
						&& VoltGuardEngine.shouldExtractParticle(particle))
				: stream;
	}
}
