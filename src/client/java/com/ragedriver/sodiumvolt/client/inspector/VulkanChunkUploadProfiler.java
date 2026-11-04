package com.ragedriver.sodiumvolt.client.inspector;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.ragedriver.sodiumvolt.client.config.VoltInspectorConfig;
import com.ragedriver.sodiumvolt.client.performance.InspectorStatisticsDemand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** Render-thread counters for Sodium terrain uploads while Volt Inspector is sampling. */
public final class VulkanChunkUploadProfiler {
	private static ClientLevel level;
	private static long frameStart;
	private static boolean inUpload;
	private static long uploadNanos;
	private static long copies;
	private static long overflowCopies;
	private static long overflowBytes;
	private static long totalCopies;
	private static long totalOverflowCopies;
	private static long totalOverflowBytes;
	private static Sample slowestRender;
	private static Sample slowestUpload;

	private VulkanChunkUploadProfiler() {
	}

	public static void beginFrame(Minecraft minecraft, boolean renderLevel) {
		frameStart = 0L;
		if (!InspectorStatisticsDemand.isRequested()
				|| !VoltInspectorConfig.getInstance().isChunkActivity()
				|| minecraft.level == null) {
			reset(null);
			return;
		}
		if (!renderLevel || minecraft.isPaused()) {
			return;
		}
		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null || !"Vulkan".equals(device.getDeviceInfo().backendName())) {
			reset(null);
			return;
		}
		if (level != minecraft.level) {
			reset(minecraft.level);
		}
		uploadNanos = 0L;
		copies = 0L;
		overflowCopies = 0L;
		overflowBytes = 0L;
		frameStart = System.nanoTime();
	}

	public static void endFrame() {
		if (frameStart == 0L) {
			return;
		}
		long renderNanos = System.nanoTime() - frameStart;
		frameStart = 0L;
		totalCopies += copies;
		totalOverflowCopies += overflowCopies;
		totalOverflowBytes += overflowBytes;
		boolean renderPeak = slowestRender == null || renderNanos > slowestRender.renderNanos();
		boolean uploadPeak = uploadNanos > 0L
				&& (slowestUpload == null || uploadNanos > slowestUpload.uploadNanos());
		if (renderPeak || uploadPeak) {
			Sample sample = new Sample(renderNanos, uploadNanos, copies, overflowCopies, overflowBytes);
			if (renderPeak) {
				slowestRender = sample;
			}
			if (uploadPeak) {
				slowestUpload = sample;
			}
		}
	}

	public static long beginUpload() {
		if (frameStart == 0L || inUpload) {
			return 0L;
		}
		inUpload = true;
		return System.nanoTime();
	}

	public static void endUpload(long start) {
		if (start != 0L) {
			uploadNanos += System.nanoTime() - start;
			inUpload = false;
		}
	}

	public static void recordMappedCopy() {
		if (inUpload) {
			copies++;
		}
	}

	public static void recordOverflowCopy(int bytes) {
		if (inUpload) {
			copies++;
			overflowCopies++;
			overflowBytes += bytes;
		}
	}

	static Snapshot snapshot() {
		if (slowestRender == null) {
			return null;
		}
		return new Snapshot(slowestRender, slowestUpload, totalCopies, totalOverflowCopies, totalOverflowBytes);
	}

	private static void reset(ClientLevel newLevel) {
		level = newLevel;
		totalCopies = 0L;
		totalOverflowCopies = 0L;
		totalOverflowBytes = 0L;
		slowestRender = null;
		slowestUpload = null;
	}

	record Sample(long renderNanos, long uploadNanos, long copies, long overflowCopies, long overflowBytes) {
	}

	record Snapshot(Sample slowestRender, Sample slowestUpload, long copies, long overflowCopies, long overflowBytes) {
	}
}
