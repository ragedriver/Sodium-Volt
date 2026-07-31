package com.ragedriver.sodiumvolt.client.mixin;

import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.ragedriver.sodiumvolt.client.performance.VramTrackedResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlBuffer.Direct.class)
public abstract class GlDirectBufferVramMixin {
	@Inject(
			method = "<init>(Lcom/mojang/blaze3d/opengl/DirectStateAccess;IJIZ)V",
			at = @At("RETURN"),
			require = 1
	)
	private void sodiumVolt$trackSuccessfulBufferAllocation(
			DirectStateAccess directStateAccess,
			int usage,
			long size,
			int handle,
			boolean canPersistentMap,
			CallbackInfo callbackInfo
	) {
		((VramTrackedResource) this).sodiumVolt$registerVramEstimate(size, false, false);
	}

	@Inject(method = "close()V", at = @At("RETURN"), require = 1)
	private void sodiumVolt$releaseBufferEstimate(CallbackInfo callbackInfo) {
		((VramTrackedResource) this).sodiumVolt$releaseVramEstimate();
	}
}
