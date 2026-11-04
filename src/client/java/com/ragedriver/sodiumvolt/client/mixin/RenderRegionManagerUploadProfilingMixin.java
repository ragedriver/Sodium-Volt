package com.ragedriver.sodiumvolt.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ragedriver.sodiumvolt.client.inspector.VulkanChunkUploadProfiler;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Collection;

@Mixin(value = RenderRegionManager.class, remap = false)
public abstract class RenderRegionManagerUploadProfilingMixin {
	@WrapMethod(method = "uploadResults(Ljava/util/Collection;Lnet/caffeinemc/mods/sodium/client/render/chunk/UniformBufferManager;)V")
	private void sodiumVolt$profileUploads(
			Collection<BuilderTaskOutput> results,
			UniformBufferManager uniforms,
			Operation<Void> original
	) {
		long start = results.isEmpty() ? 0L : VulkanChunkUploadProfiler.beginUpload();
		try {
			original.call(results, uniforms);
		} finally {
			VulkanChunkUploadProfiler.endUpload(start);
		}
	}
}
