package com.ragedriver.sodiumvolt.client.mixin;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import com.ragedriver.sodiumvolt.client.performance.VramByteMath;
import com.ragedriver.sodiumvolt.client.performance.VramTrackedResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VulkanGpuTexture.class)
public abstract class VulkanTextureVramMixin {
	@Inject(
			method = "<init>(Lcom/mojang/blaze3d/vulkan/VulkanDevice;ILjava/lang/String;Lcom/mojang/blaze3d/GpuFormat;IIII)V",
			at = @At("RETURN"),
			require = 1
	)
	private void sodiumVolt$trackSuccessfulTextureAllocation(
			VulkanDevice device,
			int usage,
			String label,
			GpuFormat format,
			int width,
			int height,
			int depthOrLayers,
			int mipLevels,
			CallbackInfo callbackInfo
	) {
		long bytes = VramByteMath.textureBytes(
				width, height, depthOrLayers, mipLevels, format.blockSize()
		);
		((VramTrackedResource) this).sodiumVolt$registerVramEstimate(
				bytes,
				true,
				(usage & GpuTexture.USAGE_RENDER_ATTACHMENT) != 0
		);
	}

	@Inject(method = "destroy()V", at = @At("RETURN"), require = 1)
	private void sodiumVolt$releaseTextureEstimate(CallbackInfo callbackInfo) {
		((VramTrackedResource) this).sodiumVolt$releaseVramEstimate();
	}
}
