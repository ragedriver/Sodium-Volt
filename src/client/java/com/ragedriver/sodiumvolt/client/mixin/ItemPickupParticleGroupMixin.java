package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.guard.VoltGuardEngine;
import com.ragedriver.sodiumvolt.client.guard.VoltGuardHotPathGate;
import com.ragedriver.sodiumvolt.client.performance.VisibilityAwareParticleScheduler;
import com.ragedriver.sodiumvolt.client.performance.VapsHotPathGate;
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
		boolean vaps = VapsHotPathGate.renderActive();
		boolean guard = VoltGuardHotPathGate.particleFilterActive();
		return vaps || guard
				? stream.filter(particle -> (!vaps
						|| VisibilityAwareParticleScheduler.shouldRenderParticle(particle))
						&& (!guard || VoltGuardEngine.shouldExtractParticle(particle)))
				: stream;
	}
}
