package com.ragedriver.sodiumvolt.client.mixin;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.ragedriver.sodiumvolt.client.inspector.VulkanChunkUploadProfiler;
import net.caffeinemc.mods.sodium.client.gpu.arena.staging.MappedStagingBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(value = MappedStagingBuffer.class, remap = false)
public abstract class MappedStagingBufferProfilingMixin {
	@Inject(method = "flush()V", at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;copyToBuffer(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;)V"
	))
	private void sodiumVolt$countConsolidatedCopy(CallbackInfo callbackInfo) {
		VulkanChunkUploadProfiler.recordMappedCopy();
	}

	@Inject(method = "enqueueCopy(Ljava/nio/ByteBuffer;Lcom/mojang/renderpearl/api/buffers/GpuBuffer;J)V", at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;writeToBuffer(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Ljava/nio/ByteBuffer;)V"
	))
	private void sodiumVolt$countOverflowCopy(ByteBuffer data, GpuBuffer buffer, long offset, CallbackInfo callbackInfo) {
		VulkanChunkUploadProfiler.recordOverflowCopy(data.remaining());
	}
}
