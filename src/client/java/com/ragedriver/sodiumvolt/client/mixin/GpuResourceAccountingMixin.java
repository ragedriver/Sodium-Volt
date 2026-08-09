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
	private static final int SODIUM_VOLT_VRAM_UNTRACKED = 0;
	@Unique
	private static final int SODIUM_VOLT_VRAM_REGISTERING = 1;
	@Unique
	private static final int SODIUM_VOLT_VRAM_REGISTERED = 2;
	@Unique
	private static final int SODIUM_VOLT_VRAM_RELEASED = 3;

	@Unique
	private long sodiumVolt$vramBytes;
	@Unique
	private boolean sodiumVolt$vramTexture;
	@Unique
	private boolean sodiumVolt$vramRenderAttachment;
	@Unique
	private volatile int sodiumVolt$vramState = SODIUM_VOLT_VRAM_UNTRACKED;

	@Override
	public void sodiumVolt$registerVramEstimate(
			long bytes,
			boolean texture,
			boolean renderAttachment
	) {
		if (this.sodiumVolt$vramState != SODIUM_VOLT_VRAM_UNTRACKED || bytes < 0L) {
			return;
		}
		synchronized (this) {
			if (this.sodiumVolt$vramState != SODIUM_VOLT_VRAM_UNTRACKED) {
				return;
			}
			this.sodiumVolt$vramState = SODIUM_VOLT_VRAM_REGISTERING;
			this.sodiumVolt$vramBytes = bytes;
			this.sodiumVolt$vramTexture = texture;
			this.sodiumVolt$vramRenderAttachment = texture && renderAttachment;
			VramAllocationTracker.allocated(
					bytes,
					this.sodiumVolt$vramTexture,
					this.sodiumVolt$vramRenderAttachment
			);
			// Publish last so a concurrent release cannot run before accounting.
			this.sodiumVolt$vramState = SODIUM_VOLT_VRAM_REGISTERED;
		}
	}

	@Override
	public void sodiumVolt$releaseVramEstimate() {
		int observedState = this.sodiumVolt$vramState;
		if (observedState == SODIUM_VOLT_VRAM_RELEASED
				|| observedState == SODIUM_VOLT_VRAM_UNTRACKED
						&& !VramAllocationTracker.shouldTrackAllocations()) {
			return;
		}
		synchronized (this) {
			if (this.sodiumVolt$vramState == SODIUM_VOLT_VRAM_RELEASED) {
				return;
			}
			if (this.sodiumVolt$vramState == SODIUM_VOLT_VRAM_UNTRACKED) {
				// Destruction won the race with constructor-tail registration.
				this.sodiumVolt$vramState = SODIUM_VOLT_VRAM_RELEASED;
				return;
			}
			if (this.sodiumVolt$vramState != SODIUM_VOLT_VRAM_REGISTERED) {
				return;
			}
			this.sodiumVolt$vramState = SODIUM_VOLT_VRAM_RELEASED;
			VramAllocationTracker.released(
					this.sodiumVolt$vramBytes,
					this.sodiumVolt$vramTexture,
					this.sodiumVolt$vramRenderAttachment
			);
		}
	}
}
