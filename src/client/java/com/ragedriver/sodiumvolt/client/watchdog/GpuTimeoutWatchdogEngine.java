package com.ragedriver.sodiumvolt.client.watchdog;

import com.ragedriver.sodiumvolt.client.config.GpuWatchdogConfig;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class GpuTimeoutWatchdogEngine {
	private static final long NOTIFICATION_INTERVAL_NANOS = 10_000_000_000L;
	private static final int MAXIMUM_ACTIVE_RELOADS = 32;
	private static final GpuWatchdogConfig CONFIG = GpuWatchdogConfig.getInstance();
	private static final WatchdogLifecycleGate LIFECYCLE = new WatchdogLifecycleGate();
	private static final AtomicBoolean IN_FRAME = new AtomicBoolean();
	private static final AtomicBoolean MONITORING_ALLOWED = new AtomicBoolean();
	private static final AtomicLong FRAME_START_NANOS = new AtomicLong();
	private static final AtomicLong FRAME_SEQUENCE = new AtomicLong();
	private static final AtomicLong CONTROL_GENERATION = new AtomicLong(1L);
	private static final WatchdogReloadAccounting RELOAD_ACCOUNTING =
			new WatchdogReloadAccounting(MAXIMUM_ACTIVE_RELOADS);
	private static final AtomicLong RELOAD_GRACE_UNTIL_NANOS = new AtomicLong();
	private static final AtomicReference<Signal> PENDING_SIGNAL = new AtomicReference<>();

	private static volatile EngineSettings settings = EngineSettings.defaults();
	private static volatile Thread daemonThread;
	private static volatile int latestDurationMillis;
	private static volatile int incidentCount;
	private static volatile boolean incidentCapReached;
	private static volatile WatchdogStatus status = WatchdogStatus.OFF;
	private static volatile boolean recoveryRequestStaged;
	private static long observedConfigRevision = Long.MIN_VALUE;
	private static long startupWorldGraceUntilNanos;
	private static long lastNotificationNanos;
	private static int previousLevelIdentity;
	private static boolean runtimeEnabled;

	private GpuTimeoutWatchdogEngine() {
	}

	public static void register() {
		ClientLifecycleEvents.CLIENT_STOPPING.register(
				minecraft -> onClientStopping()
		);
	}

	public static void onRenderFrameBegin(Minecraft minecraft, long nowNanos) {
		if (LIFECYCLE.isClientStopping()
				|| Thread.currentThread() != minecraft.getRunningThread()) {
			return;
		}
		refreshSettings();
		if (!CONFIG.isGpuTimeoutWatchdogEnabled()) {
			disableRuntime();
			return;
		}
		if (!runtimeEnabled) {
			enableRuntime(nowNanos);
		}
		if (!LIFECYCLE.isEnabled()) {
			return;
		}

		int levelIdentity = minecraft.level == null
				? 0
				: System.identityHashCode(minecraft.level);
		if (levelIdentity != previousLevelIdentity) {
			previousLevelIdentity = levelIdentity;
			startupWorldGraceUntilNanos = saturatedAdd(
					nowNanos,
					settings.startupWorldGraceNanos()
			);
		}
		boolean grace = nowNanos < startupWorldGraceUntilNanos
				|| RELOAD_ACCOUNTING.active() > 0
				|| nowNanos < RELOAD_GRACE_UNTIL_NANOS.get();
		boolean suppressed = isSuppressed(minecraft);
		boolean allowed = !grace && !suppressed;
		MONITORING_ALLOWED.set(allowed);
		status = grace
				? WatchdogStatus.GRACE
				: suppressed ? WatchdogStatus.SUPPRESSED : WatchdogStatus.MONITORING;
		long sequence = FRAME_SEQUENCE.updateAndGet(
				current -> current == Long.MAX_VALUE ? 1L : current + 1L
		);
		FRAME_START_NANOS.set(nowNanos);
		// This publication is last so the daemon cannot observe a new frame with stale metadata.
		if (sequence > 0L) {
			IN_FRAME.set(true);
		}
	}

	public static void onRenderFrameEnd(Minecraft minecraft, long nowNanos) {
		// Clear first: anything below may call UI or persistence code.
		IN_FRAME.set(false);
		if (LIFECYCLE.isClientStopping()
				|| Thread.currentThread() != minecraft.getRunningThread()) {
			return;
		}
		Signal signal = PENDING_SIGNAL.getAndSet(null);
		if (signal != null) {
			latestDurationMillis = signal.durationMillis();
			incidentCount = signal.incidentCount();
			incidentCapReached = signal.capReached();
			recoveryRequestStaged |= signal.recoveryRequestStaged();
			status = signal.critical()
					? WatchdogStatus.CRITICAL
					: WatchdogStatus.WARNING;
			showTransitionNotification(minecraft, nowNanos, signal.critical());
		}
	}

	public static ReloadToken beginResourceReload() {
		if (!runtimeEnabled || !LIFECYCLE.isEnabled()) {
			return ReloadToken.DISABLED;
		}
		if (!RELOAD_ACCOUNTING.tryClaim()) {
			return ReloadToken.DISABLED;
		}
		MONITORING_ALLOWED.set(false);
		return new ReloadToken(CONTROL_GENERATION.get(), true, new AtomicBoolean());
	}

	public static void watchResourceReload(
			CompletableFuture<Void> future,
			ReloadToken token
	) {
		if (token == null || !token.counted() || future == null) {
			completeReload(token, System.nanoTime());
			return;
		}
		future.whenComplete((ignored, throwable) -> completeReload(token, System.nanoTime()));
	}

	public static StatisticsSnapshot snapshotStatistics() {
		if (!CONFIG.isGpuTimeoutWatchdogEnabled()
				|| !CONFIG.isShowInspectorStatistics()) {
			return StatisticsSnapshot.EMPTY;
		}
		return new StatisticsSnapshot(
				status,
				latestDurationMillis,
				incidentCount,
				settings.policy().maximumIncidents(),
				incidentCapReached,
				recoveryRequestStaged
		);
	}

	private static void refreshSettings() {
		long revision = CONFIG.revision();
		if (revision == observedConfigRevision) {
			return;
		}
		settings = new EngineSettings(
				CONFIG.policySettings(),
				CONFIG.getStartupWorldGraceSeconds() * 1_000_000_000L,
				CONFIG.getResourceReloadGraceSeconds() * 1_000_000_000L,
				CONFIG.isIgnorePausedLoading(),
				CONFIG.isIgnoreUnfocusedMinimized(),
				CONFIG.isShowTransitionNotifications()
		);
		observedConfigRevision = revision;
	}

	private static void enableRuntime(long nowNanos) {
		runtimeEnabled = true;
		LIFECYCLE.setEnabled(true);
		CONTROL_GENERATION.updateAndGet(
				value -> value == Long.MAX_VALUE ? 1L : value + 1L
		);
		RELOAD_ACCOUNTING.reset();
		RELOAD_GRACE_UNTIL_NANOS.set(0L);
		startupWorldGraceUntilNanos = saturatedAdd(
				nowNanos,
				settings.startupWorldGraceNanos()
		);
		previousLevelIdentity = 0;
		if (LIFECYCLE.claimThreadStart()) {
			Thread thread = new Thread(
					GpuTimeoutWatchdogEngine::monitorLoop,
					"Sodium Volt GPU Timeout Watchdog"
			);
			thread.setDaemon(true);
			thread.setPriority(Thread.MIN_PRIORITY);
			daemonThread = thread;
			thread.start();
		}
	}

	private static void disableRuntime() {
		if (!runtimeEnabled) {
			return;
		}
		runtimeEnabled = false;
		LIFECYCLE.setEnabled(false);
		CONTROL_GENERATION.updateAndGet(
				value -> value == Long.MAX_VALUE ? 1L : value + 1L
		);
		IN_FRAME.set(false);
		MONITORING_ALLOWED.set(false);
		RELOAD_ACCOUNTING.reset();
		RELOAD_GRACE_UNTIL_NANOS.set(0L);
		PENDING_SIGNAL.set(null);
		status = WatchdogStatus.OFF;
	}

	private static void onClientStopping() {
		LIFECYCLE.beginStopping();
		runtimeEnabled = false;
		CONTROL_GENERATION.updateAndGet(
				value -> value == Long.MAX_VALUE ? 1L : value + 1L
		);
		IN_FRAME.set(false);
		MONITORING_ALLOWED.set(false);
		RELOAD_ACCOUNTING.reset();
		PENDING_SIGNAL.set(null);
		status = WatchdogStatus.OFF;
		Thread thread = daemonThread;
		if (thread != null) {
			thread.interrupt();
			try {
				thread.join(500L);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private static void monitorLoop() {
		GpuWatchdogPolicy policy = new GpuWatchdogPolicy();
		while (!LIFECYCLE.isClientStopping()) {
			EngineSettings current = settings;
			if (!LIFECYCLE.isEnabled()) {
				policy.resetMonitoring();
				park(250);
				continue;
			}
			int event = policy.evaluate(
					System.nanoTime(),
					IN_FRAME.get(),
					MONITORING_ALLOWED.get(),
					FRAME_START_NANOS.get(),
					FRAME_SEQUENCE.get(),
					current.policy()
			);
			latestDurationMillis = policy.latestDurationMillis();
			incidentCount = policy.incidents();
			incidentCapReached = policy.capReached(current.policy());
			if (event == GpuWatchdogPolicy.EVENT_WARNING) {
				PENDING_SIGNAL.compareAndSet(
						null,
						new Signal(
								false,
								policy.latestDurationMillis(),
								policy.incidents(),
								policy.capReached(current.policy()),
								false
						)
				);
			} else if (event == GpuWatchdogPolicy.EVENT_CRITICAL) {
				boolean requestStaged = current.policy().armRecoveryNextLaunch()
						&& WatchdogRecoveryRequestStore.stage(
								policy.latestDurationMillis(),
								policy.incidents()
						);
				recoveryRequestStaged |= requestStaged;
				if (current.policy().writeReport()) {
					GpuWatchdogReportStore.write(new GpuWatchdogIncidentReport(
							policy.latestDurationMillis(),
							(int) (current.policy().warningThresholdNanos() / 1_000_000L),
							(int) (current.policy().criticalThresholdNanos() / 1_000_000L),
							current.policy().criticalConfirmationCount(),
							policy.incidents(),
							requestStaged
					));
				}
				PENDING_SIGNAL.set(new Signal(
						true,
						policy.latestDurationMillis(),
						policy.incidents(),
						policy.capReached(current.policy()),
						requestStaged
				));
			}
			park(current.policy().sampleIntervalMillis());
		}
	}

	private static void park(int milliseconds) {
		try {
			Thread.sleep(Math.max(100, milliseconds));
		} catch (InterruptedException exception) {
			if (!LIFECYCLE.isClientStopping()) {
				Thread.interrupted();
			}
		}
	}

	private static boolean isSuppressed(Minecraft minecraft) {
		if (settings.ignorePausedLoading()
				&& (minecraft.level == null
						|| minecraft.player == null
						|| !minecraft.isGameLoadFinished()
						|| minecraft.isPaused()
						|| minecraft.gui.screen() != null
						|| minecraft.gui.overlay() != null)) {
			return true;
		}
		if (settings.ignoreUnfocusedMinimized()) {
			Window window = minecraft.getWindow();
			return window.isMinimized() || window.isIconified() || !window.isFocused();
		}
		return false;
	}

	private static void completeReload(ReloadToken token, long nowNanos) {
		if (token == null || !token.counted()
				|| !RELOAD_ACCOUNTING.release(
						token.controlGeneration(),
						CONTROL_GENERATION.get(),
						token.completed()
				)) {
			return;
		}
		long graceUntil = saturatedAdd(nowNanos, settings.resourceReloadGraceNanos());
		RELOAD_GRACE_UNTIL_NANOS.accumulateAndGet(graceUntil, Math::max);
	}

	private static void showTransitionNotification(
			Minecraft minecraft,
			long nowNanos,
			boolean critical
	) {
		if (!settings.showNotifications()
				|| minecraft.level == null || minecraft.player == null
				|| minecraft.gui.screen() != null || minecraft.gui.overlay() != null) {
			return;
		}
		if (lastNotificationNanos != 0L && nowNanos < lastNotificationNanos) {
			lastNotificationNanos = 0L;
		}
		if (lastNotificationNanos != 0L
				&& nowNanos - lastNotificationNanos < NOTIFICATION_INTERVAL_NANOS) {
			return;
		}
		minecraft.gui.hud.setOverlayMessage(Component.translatable(
				critical
						? "sodium-volt.notification.watchdog.critical"
						: "sodium-volt.notification.watchdog.warning"
		), false);
		lastNotificationNanos = nowNanos;
	}

	private static long saturatedAdd(long value, long increment) {
		if (increment <= 0L) {
			return value;
		}
		return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
	}

	private record EngineSettings(
			GpuWatchdogPolicy.Settings policy,
			long startupWorldGraceNanos,
			long resourceReloadGraceNanos,
			boolean ignorePausedLoading,
			boolean ignoreUnfocusedMinimized,
			boolean showNotifications
	) {
		private static EngineSettings defaults() {
			return new EngineSettings(
					new GpuWatchdogPolicy.Settings(
							3_000_000_000L,
							8_000_000_000L,
							2,
							60_000_000_000L,
							3,
							250,
							true,
							true
					),
					20_000_000_000L,
					30_000_000_000L,
					true,
					true,
					true
			);
		}
	}

	private record Signal(
			boolean critical,
			int durationMillis,
			int incidentCount,
			boolean capReached,
			boolean recoveryRequestStaged
	) {
	}

	public record ReloadToken(
			long controlGeneration,
			boolean counted,
			AtomicBoolean completed
	) {
		public static final ReloadToken DISABLED =
				new ReloadToken(0L, false, new AtomicBoolean(true));
	}

	public enum WatchdogStatus {
		OFF,
		GRACE,
		SUPPRESSED,
		MONITORING,
		WARNING,
		CRITICAL
	}

	public record StatisticsSnapshot(
			WatchdogStatus status,
			int latestDurationMillis,
			int incidentCount,
			int maximumIncidents,
			boolean capReached,
			boolean recoveryRequestStaged
	) {
		public static final StatisticsSnapshot EMPTY = new StatisticsSnapshot(
				WatchdogStatus.OFF, 0, 0, 0, false, false
		);
	}
}
