package com.ragedriver.sodiumvolt.client.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.textures.GpuTexture;
import com.ragedriver.sodiumvolt.client.performance.VramAllocationTracker;
import com.ragedriver.sodiumvolt.client.performance.VramTrackedResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({GpuTexture.class, GpuBuffer.class})
public abstract class GpuResourceAccountingMixin implements VramTrackedResource {
	@Unique
	private long sodiumVolt$vramBytes;
	@Unique
	private boolean sodiumVolt$vramTexture;
	@Unique
	private boolean sodiumVolt$vramRenderAttachment;
	@Unique
	private boolean sodiumVolt$vramRegistered;
	@Unique
	private boolean sodiumVolt$vramReleased;

	@Override
	public synchronized void sodiumVolt$registerVramEstimate(
			long bytes,
			boolean texture,
			boolean renderAttachment
	) {
		if (this.sodiumVolt$vramRegistered || bytes < 0L) {
			return;
		}
		this.sodiumVolt$vramRegistered = true;
		this.sodiumVolt$vramBytes = bytes;
		this.sodiumVolt$vramTexture = texture;
		this.sodiumVolt$vramRenderAttachment = texture && renderAttachment;
		VramAllocationTracker.allocated(
				bytes,
				this.sodiumVolt$vramTexture,
				this.sodiumVolt$vramRenderAttachment
		);
	}

	@Override
	public synchronized void sodiumVolt$releaseVramEstimate() {
		if (!this.sodiumVolt$vramRegistered || this.sodiumVolt$vramReleased) {
			return;
		}
		this.sodiumVolt$vramReleased = true;
		VramAllocationTracker.released(
				this.sodiumVolt$vramBytes,
				this.sodiumVolt$vramTexture,
				this.sodiumVolt$vramRenderAttachment
		);
	}
}
