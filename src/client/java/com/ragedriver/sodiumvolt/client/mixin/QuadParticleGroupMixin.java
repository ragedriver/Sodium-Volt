package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.guard.VoltGuardEngine;
import com.ragedriver.sodiumvolt.client.guard.VoltGuardHotPathGate;
import com.ragedriver.sodiumvolt.client.performance.VisibilityAwareParticleScheduler;
import com.ragedriver.sodiumvolt.client.performance.VapsHotPathGate;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(QuadParticleGroup.class)
public abstract class QuadParticleGroupMixin {
	@Redirect(
			method = "extractRenderState",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/particle/SingleQuadParticle;extract("
							+ "Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;"
							+ "Lnet/minecraft/client/Camera;F)V"
			)
	)
	private void sodiumVolt$limitExtractedParticles(
			SingleQuadParticle particle,
			QuadParticleRenderState renderState,
			Camera camera,
			float partialTickTime
	) {
		boolean vaps = VapsHotPathGate.renderActive();
		boolean guard = VoltGuardHotPathGate.particleFilterActive();
		if ((!vaps || VisibilityAwareParticleScheduler.shouldRenderParticle(particle))
				&& (!guard || VoltGuardEngine.shouldExtractParticle(particle))) {
			particle.extract(renderState, camera, partialTickTime);
		}
	}
}
