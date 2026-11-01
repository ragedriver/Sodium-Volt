package com.ragedriver.sodiumvolt.client.mixin;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuBuffer;
import com.ragedriver.sodiumvolt.client.performance.VramAllocationTracker;
import com.ragedriver.sodiumvolt.client.performance.VramTrackedResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(VulkanGpuBuffer.Direct.class)
public abstract class VulkanDirectBufferVramMixin {
	@Inject(
			method = "<init>(Lcom/mojang/renderpearl/backend/vulkan/VulkanDevice;Ljava/util/function/Supplier;IJZ)V",
			at = @At("RETURN"),
			require = 1
	)
	private void sodiumVolt$trackSuccessfulBufferAllocation(
			VulkanDevice device,
			Supplier<String> label,
			int usage,
			long size,
			boolean mappable,
			CallbackInfo callbackInfo
	) {
		if (!VramAllocationTracker.shouldTrackAllocations()) {
			return;
		}
		((VramTrackedResource) this).sodiumVolt$registerVramEstimate(size, false, false);
	}

	@Inject(method = "destroy()V", at = @At("RETURN"), require = 1)
	private void sodiumVolt$releaseBufferEstimate(CallbackInfo callbackInfo) {
		((VramTrackedResource) this).sodiumVolt$releaseVramEstimate();
	}
}
