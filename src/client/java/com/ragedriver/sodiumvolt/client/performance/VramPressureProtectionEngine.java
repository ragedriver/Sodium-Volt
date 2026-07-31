package com.ragedriver.sodiumvolt.client.performance;

import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.DeviceType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.config.VoltPerformanceConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.NVXGPUMemoryInfo;

import java.lang.management.ManagementFactory;

public final class VramPressureProtectionEngine {
	private static final long WARNING_INTERVAL_NANOS = 15_000_000_000L;
	private static final long SPIKE_SAMPLE_FLOOR_NANOS = 250_000_000L;
	private static final long SPIKE_POLL_INTERVAL_NANOS = 100_000_000L;
	private static final VoltPerformanceConfig CONFIG = VoltPerformanceConfig.getInstance();
	private static final VramPressureStateMachine STATE = new VramPressureStateMachine();
	private static final VramRenderDistanceCap RENDER_DISTANCE_CAP = new VramRenderDistanceCap();

	private static boolean active;
	private static boolean runtimeFailed;
	private static boolean failureLogged;
	private static boolean budgetProbed;
	private static int automaticBudgetMib = VramAutoBudgetHeuristic.FALLBACK_BUDGET_MIB;
	private static BudgetSource automaticBudgetSource = BudgetSource.AUTO_HEURISTIC;
	private static long nextSampleNanos;
	private static long nextSpikePollNanos;
	private static long lastSampleNanos;
	private static long nextRenderStepNanos;
	private static long lastWarningNanos;
	private static long actionCount;
	private static int configuredSpikeMib = -1;
	private static volatile StatisticsSnapshot statistics = StatisticsSnapshot.EMPTY;

	private VramPressureProtectionEngine() {
	}

	public static void register() {
		updateSpikeThreshold();
		ClientLifecycleEvents.CLIENT_STOPPING.register(VramPressureProtectionEngine::onClientStopping);
	}

	public static void onRenderFrame(Minecraft minecraft, long nowNanos) {
		if (Thread.currentThread() != minecraft.getRunningThread()) {
			return;
		}
		if (runtimeFailed) {
			if (!CONFIG.isVramPressureProtectionEnabled()) {
				runtimeFailed = false;
			}
			return;
		}
		try {
			update(minecraft, nowNanos);
		} catch (RuntimeException | LinkageError exception) {
			runtimeFailed = true;
			failMetricOnce();
			deactivate(minecraft, true);
		}
	}

	public static int currentMaximumRenderDistanceCap() {
		return RENDER_DISTANCE_CAP.currentCap();
	}

	public static StatisticsSnapshot snapshotStatistics() {
		if (!CONFIG.isVramShowInspectorStatistics()) {
			return StatisticsSnapshot.EMPTY;
		}
		return statistics;
	}

	private static void update(Minecraft minecraft, long nowNanos) {
		if (!CONFIG.isVramPressureProtectionEnabled()) {
			if (active || RENDER_DISTANCE_CAP.isActive()) {
				deactivate(minecraft, true);
			}
			return;
		}
		active = true;
		if (!CONFIG.isVramApplySafeRenderDistanceProfile() && RENDER_DISTANCE_CAP.isActive()) {
			releaseSafeProfile(minecraft, CONFIG.isVramRestoreQualityAfterRecovery());
		}
		if (CONFIG.isVramRespondToAllocationSpikes() && nowNanos >= nextSpikePollNanos) {
			nextSpikePollNanos = saturatingAdd(nowNanos, SPIKE_POLL_INTERVAL_NANOS);
			if (VramAllocationTracker.hasSpikeSignal()
					&& VramPressureStateMachine.safeElapsed(nowNanos, lastSampleNanos)
							>= SPIKE_SAMPLE_FLOOR_NANOS
					&& VramAllocationTracker.consumeSpikeSignal()) {
				nextSampleNanos = nowNanos;
			}
		}
		if (nowNanos < nextSampleNanos) {
			return;
		}
		lastSampleNanos = nowNanos;
		nextSampleNanos = saturatingAdd(
				nowNanos,
				CONFIG.getVramSampleIntervalSeconds() * 1_000_000_000L
		);
		sample(minecraft, nowNanos);
	}

	private static void sample(Minecraft minecraft, long nowNanos) {
		updateSpikeThreshold();
		if (!CONFIG.isVramRespondToAllocationSpikes()) {
			VramAllocationTracker.consumeSpikeSignal();
		}
		VramAccountingLedger.Snapshot tracked = VramAllocationTracker.snapshot();
		long trackedBytes = tracked.totalBytes();
		if (trackedBytes == Long.MAX_VALUE) {
			failMetricOnce();
			failOpenMetric(minecraft, tracked);
			return;
		}

		Budget budget = selectBudget();
		long budgetBytes = VramByteMath.mibToBytes(budget.mib());
		long estimatedBytes = CONFIG.isVramAccountForHeadroom()
				? VramByteMath.addHeadroom(
						trackedBytes,
						CONFIG.getVramSafetyHeadroomPercent(),
						VramByteMath.mibToBytes(CONFIG.getVramFixedReserveMib())
				)
				: trackedBytes;
		if (estimatedBytes < 0L || estimatedBytes == Long.MAX_VALUE || budgetBytes <= 0L) {
			failMetricOnce();
			failOpenMetric(minecraft, tracked);
			return;
		}

		VramPressureStateMachine.Action transition = STATE.sample(
				estimatedBytes,
				budgetBytes,
				CONFIG.getVramProtectionThresholdPercent(),
				CONFIG.getVramCriticalThresholdPercent(),
				CONFIG.getVramSustainedSamples(),
				CONFIG.getVramRecoveryDelaySeconds() * 1_000_000_000L,
				nowNanos
		);
		boolean validWorld = minecraft.level != null
				&& minecraft.player != null
				&& minecraft.isGameLoadFinished();
		if (validWorld && CONFIG.isVramApplySafeRenderDistanceProfile()) {
			applySafeProfile(minecraft, nowNanos);
		}
		maybeWarn(minecraft, transition, nowNanos, validWorld);
		statistics = new StatisticsSnapshot(
				estimatedBytes,
				budgetBytes,
				pressurePercent(estimatedBytes, budgetBytes),
				tracked.textureBytes(),
				tracked.bufferBytes(),
				tracked.renderAttachmentBytes(),
				tracked.textureCount(),
				tracked.bufferCount(),
				tracked.renderAttachmentCount(),
				tracked.peakBytes(),
				tracked.spikeCount(),
				actionCount,
				RENDER_DISTANCE_CAP.isActive() ? RENDER_DISTANCE_CAP.currentCap() : -1,
				STATE.level(),
				budget.source(),
				false
		);
	}

	private static void applySafeProfile(Minecraft minecraft, long nowNanos) {
		int current = minecraft.options.renderDistance().get();
		if (STATE.level() == VramPressureStateMachine.Level.PROTECTION
				|| STATE.level() == VramPressureStateMachine.Level.CRITICAL) {
			if (nowNanos < nextRenderStepNanos) {
				return;
			}
			int step = STATE.level() == VramPressureStateMachine.Level.CRITICAL ? 2 : 1;
			int desired = RENDER_DISTANCE_CAP.lower(
					current,
					CONFIG.getVramMinimumSafeRenderDistance(),
					step
			);
			if (desired != current) {
				minecraft.options.renderDistance().set(desired);
				AdaptivePerformanceController.acceptVramOwnedRenderDistance(desired);
				actionCount = AttPolicy.saturatingAdd(actionCount, 1L);
			}
			nextRenderStepNanos = saturatingAdd(
					nowNanos,
					CONFIG.getVramRenderDistanceStepIntervalSeconds() * 1_000_000_000L
			);
		} else if (STATE.level() == VramPressureStateMachine.Level.NORMAL
				&& RENDER_DISTANCE_CAP.isActive()) {
			if (!CONFIG.isVramRestoreQualityAfterRecovery()) {
				releaseSafeProfile(minecraft, false);
			} else if (nowNanos >= nextRenderStepNanos) {
				int desired = RENDER_DISTANCE_CAP.recover(current);
				if (desired != current && !AdaptivePerformanceController.isActive()) {
					minecraft.options.renderDistance().set(desired);
					AdaptivePerformanceController.acceptVramOwnedRenderDistance(desired);
					actionCount = AttPolicy.saturatingAdd(actionCount, 1L);
				}
				nextRenderStepNanos = saturatingAdd(
						nowNanos,
						CONFIG.getVramRenderDistanceStepIntervalSeconds() * 1_000_000_000L
				);
			}
		}
	}

	private static void updateSpikeThreshold() {
		int configured = CONFIG.getVramLargeAllocationSpikeMib();
		if (configured != configuredSpikeMib) {
			VramAllocationTracker.setSpikeThresholdBytes(VramByteMath.mibToBytes(configured));
			configuredSpikeMib = configured;
		}
	}

	private static Budget selectBudget() {
		if (!CONFIG.isVramAutoDetectBudget()) {
			return new Budget(CONFIG.getVramManualBudgetMib(), BudgetSource.MANUAL);
		}
		if (!budgetProbed) {
			probeAutomaticBudget();
		}
		return new Budget(automaticBudgetMib, automaticBudgetSource);
	}

	private static void probeAutomaticBudget() {
		budgetProbed = true;
		try {
			GpuDevice device = RenderSystem.tryGetDevice();
			DeviceInfo info = device == null ? null : device.getDeviceInfo();
			if (info != null && info.backendName() != null
					&& info.backendName().toLowerCase(java.util.Locale.ROOT).contains("opengl")
					&& GL.getCapabilities().GL_NVX_gpu_memory_info) {
				int dedicatedKib = GL11C.glGetInteger(
						NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX
				);
				if (dedicatedKib > 0) {
					automaticBudgetMib = VramConfigNormalization.clamp(
							dedicatedKib / 1024,
							VramAutoBudgetHeuristic.MINIMUM_BUDGET_MIB,
							VramAutoBudgetHeuristic.MAXIMUM_BUDGET_MIB
					);
					automaticBudgetSource = BudgetSource.NATIVE;
					return;
				}
			}
			long physicalMib = physicalMemoryMib();
			DeviceType type = info == null ? DeviceType.OTHER : info.type();
			automaticBudgetMib = VramAutoBudgetHeuristic.estimateMib(
					physicalMib,
					info == null ? "" : info.vendorName(),
					info == null ? "" : info.backendName(),
					type == DeviceType.INTEGRATED,
					type == DeviceType.DISCRETE
			);
			automaticBudgetSource = BudgetSource.AUTO_HEURISTIC;
		} catch (RuntimeException | LinkageError exception) {
			automaticBudgetMib = VramAutoBudgetHeuristic.FALLBACK_BUDGET_MIB;
			automaticBudgetSource = BudgetSource.AUTO_HEURISTIC;
			failMetricOnce();
		}
	}

	private static long physicalMemoryMib() {
		java.lang.management.OperatingSystemMXBean bean =
				ManagementFactory.getOperatingSystemMXBean();
		if (bean instanceof com.sun.management.OperatingSystemMXBean extended) {
			long bytes = extended.getTotalMemorySize();
			return bytes <= 0L ? -1L : bytes / 1_048_576L;
		}
		return -1L;
	}

	private static void maybeWarn(
			Minecraft minecraft,
			VramPressureStateMachine.Action transition,
			long nowNanos,
			boolean validWorld
	) {
		if (!CONFIG.isVramShowPressureWarnings() || !validWorld
				|| minecraft.gui.screen() != null || minecraft.gui.overlay() != null
				|| transition == VramPressureStateMachine.Action.HOLD
				|| transition == VramPressureStateMachine.Action.UNKNOWN
				|| lastWarningNanos != 0L
				&& VramPressureStateMachine.safeElapsed(nowNanos, lastWarningNanos)
						< WARNING_INTERVAL_NANOS) {
			return;
		}
		Component message = switch (transition) {
			case ENTER_CRITICAL ->
					Component.translatable("sodium-volt.notification.vram.critical");
			case ENTER_PROTECTION ->
					Component.translatable("sodium-volt.notification.vram.protection");
			case RECOVER_NORMAL ->
					Component.translatable("sodium-volt.notification.vram.recovery");
			default -> null;
		};
		if (message != null) {
			lastWarningNanos = nowNanos;
			minecraft.gui.hud.setOverlayMessage(message, false);
		}
	}

	private static void deactivate(Minecraft minecraft, boolean restoreOwned) {
		releaseSafeProfile(
				minecraft,
				restoreOwned && CONFIG.isVramRestoreQualityAfterRecovery()
		);
		STATE.reset();
		active = false;
		nextSampleNanos = 0L;
		nextSpikePollNanos = 0L;
		nextRenderStepNanos = 0L;
		statistics = StatisticsSnapshot.EMPTY;
	}

	private static void releaseSafeProfile(Minecraft minecraft, boolean restoreOwned) {
		int current = minecraft.options.renderDistance().get();
		int restored = RENDER_DISTANCE_CAP.disable(
				current,
				restoreOwned,
				!AdaptivePerformanceController.isActive()
		);
		if (restored != current) {
			minecraft.options.renderDistance().set(restored);
			AdaptivePerformanceController.acceptVramOwnedRenderDistance(restored);
		}
		nextRenderStepNanos = 0L;
	}

	private static void onClientStopping(Minecraft minecraft) {
		try {
			boolean hadCap = RENDER_DISTANCE_CAP.isActive();
			deactivate(minecraft, true);
			if (hadCap) {
				minecraft.options.save();
			}
		} catch (RuntimeException exception) {
			failMetricOnce();
		}
	}

	private static void failOpenMetric(
			Minecraft minecraft,
			VramAccountingLedger.Snapshot tracked
	) {
		releaseSafeProfile(minecraft, CONFIG.isVramRestoreQualityAfterRecovery());
		STATE.reset();
		statistics = new StatisticsSnapshot(
				-1L,
				-1L,
				-1,
				tracked.textureBytes(),
				tracked.bufferBytes(),
				tracked.renderAttachmentBytes(),
				tracked.textureCount(),
				tracked.bufferCount(),
				tracked.renderAttachmentCount(),
				tracked.peakBytes(),
				tracked.spikeCount(),
				actionCount,
				RENDER_DISTANCE_CAP.isActive() ? RENDER_DISTANCE_CAP.currentCap() : -1,
				VramPressureStateMachine.Level.UNKNOWN,
				CONFIG.isVramAutoDetectBudget()
						? automaticBudgetSource
						: BudgetSource.MANUAL,
				true
		);
	}

	private static void failMetricOnce() {
		if (failureLogged) {
			return;
		}
		failureLogged = true;
		SodiumVolt.LOGGER.warn(
				"VRAM Pressure Protection could not obtain a reliable estimate and will fail open"
		);
	}

	private static int pressurePercent(long estimated, long budget) {
		if (estimated < 0L || budget <= 0L) {
			return -1;
		}
		double percentage = (double) estimated * 100.0D / (double) budget;
		return (int) Math.min(999.0D, Math.max(0.0D, percentage));
	}

	private static long saturatingAdd(long left, long right) {
		return VramByteMath.saturatingAdd(Math.max(0L, left), Math.max(0L, right));
	}

	private record Budget(int mib, BudgetSource source) {
	}

	public enum BudgetSource {
		NATIVE("native"),
		AUTO_HEURISTIC("auto heuristic"),
		MANUAL("manual");

		private final String label;

		BudgetSource(String label) {
			this.label = label;
		}

		public String label() {
			return this.label;
		}
	}

	public record StatisticsSnapshot(
			long estimatedBytes,
			long budgetBytes,
			int pressurePercent,
			long textureBytes,
			long bufferBytes,
			long renderAttachmentBytes,
			long textureCount,
			long bufferCount,
			long renderAttachmentCount,
			long peakTrackedBytes,
			long spikeCount,
			long actionCount,
			int safeRenderDistanceCap,
			VramPressureStateMachine.Level level,
			BudgetSource budgetSource,
			boolean metricFailed
	) {
		public static final StatisticsSnapshot EMPTY = new StatisticsSnapshot(
				0L, 0L, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, -1,
				VramPressureStateMachine.Level.NORMAL, BudgetSource.AUTO_HEURISTIC, false
		);
	}
}
