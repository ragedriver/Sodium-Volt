package com.ragedriver.sodiumvolt.client.mixin;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.backend.opengl.FrameBufferCache;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.ragedriver.sodiumvolt.client.performance.VramByteMath;
import com.ragedriver.sodiumvolt.client.performance.VramAllocationTracker;
import com.ragedriver.sodiumvolt.client.performance.VramTrackedResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlTexture.class)
public abstract class GlTextureVramMixin {
	@Inject(
			method = "<init>(ILjava/lang/String;Lcom/mojang/renderpearl/api/GpuFormat;IIIIILcom/mojang/renderpearl/backend/opengl/FrameBufferCache;)V",
			at = @At("RETURN"),
			require = 1
	)
	private void sodiumVolt$trackSuccessfulTextureAllocation(
			int usage,
			String label,
			GpuFormat format,
			int width,
			int height,
			int depthOrLayers,
			int mipLevels,
			int id,
			FrameBufferCache frameBufferCache,
			CallbackInfo callbackInfo
	) {
		if (!VramAllocationTracker.shouldTrackAllocations()) {
			return;
		}
		long bytes = VramByteMath.textureBytes(
				width, height, depthOrLayers, mipLevels, format.blockSize()
		);
		((VramTrackedResource) this).sodiumVolt$registerVramEstimate(
				bytes,
				true,
				(usage & GpuTexture.USAGE_RENDER_ATTACHMENT) != 0
		);
	}

	@Inject(method = "destroyImmediately()V", at = @At("RETURN"), require = 1)
	private void sodiumVolt$releaseTextureEstimate(CallbackInfo callbackInfo) {
		((VramTrackedResource) this).sodiumVolt$releaseVramEstimate();
	}
}
