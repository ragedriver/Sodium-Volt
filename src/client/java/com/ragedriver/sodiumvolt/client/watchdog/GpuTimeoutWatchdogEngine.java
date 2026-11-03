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
	private static final WatchdogFrameGate FRAME_GATE = new WatchdogFrameGate();
	private static final AtomicBoolean IN_FRAME = new AtomicBoolean();
	private static final AtomicBoolean MONITORING_ALLOWED = new AtomicBoolean();
	private static final AtomicLong FRAME_START_NANOS = new AtomicLong();
	private static final AtomicLong FRAME_SEQUENCE = new AtomicLong();
	private static final AtomicLong CONTROL_GENERATION = new AtomicLong(1L);
	private static final WatchdogReloadAccounting RELOAD_ACCOUNTING =
			new WatchdogReloadAccounting(MAXIMUM_ACTIVE_RELOADS);
	private static final AtomicReference<Long> RELOAD_GRACE_UNTIL_NANOS = new AtomicReference<>();
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
	private static boolean notificationShown;
	private static int previousLevelIdentity;

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
		if (!FRAME_GATE.runtimeEnabled()) {
			enableRuntime(nowNanos);
		}
		if (!LIFECYCLE.isEnabled()) {
			return;
		}

		MONITORING_ALLOWED.set(updateMonitoringContext(minecraft, nowNanos));
		publishHeartbeat(nowNanos);
	}

	public static boolean requiresRenderContextRefresh() {
		return FRAME_GATE.runtimeEnabled();
	}

	public static void refreshRenderContext(Minecraft minecraft, long nowNanos) {
		if (!IN_FRAME.get() || Thread.currentThread() != minecraft.getRunningThread()) {
			return;
		}
		boolean allowed = updateMonitoringContext(minecraft, nowNanos);
		if (allowed && !MONITORING_ALLOWED.get()) {
			// The tick may have left a loading or paused state before rendering.
			// Start measuring from that transition instead of counting the suppressed time.
			IN_FRAME.set(false);
			MONITORING_ALLOWED.set(true);
			publishHeartbeat(nowNanos);
		} else {
			MONITORING_ALLOWED.set(allowed);
		}
	}

	private static boolean updateMonitoringContext(Minecraft minecraft, long nowNanos) {
		int levelIdentity = minecraft.level == null
				? 0
				: System.identityHashCode(minecraft.level);
		if (levelIdentity != previousLevelIdentity) {
			previousLevelIdentity = levelIdentity;
			startupWorldGraceUntilNanos = nowNanos + settings.startupWorldGraceNanos();
		}
		Long reloadGraceUntil = RELOAD_GRACE_UNTIL_NANOS.get();
		boolean grace = nowNanos - startupWorldGraceUntilNanos < 0L
				|| RELOAD_ACCOUNTING.active() > 0
				|| reloadGraceUntil != null && nowNanos - reloadGraceUntil < 0L;
		boolean suppressed = isSuppressed(minecraft);
		status = grace
				? WatchdogStatus.GRACE
				: suppressed ? WatchdogStatus.SUPPRESSED : WatchdogStatus.MONITORING;
		return !grace && !suppressed;
	}

	private static void publishHeartbeat(long nowNanos) {
		FRAME_SEQUENCE.updateAndGet(current -> current == Long.MAX_VALUE ? 1L : current + 1L);
		FRAME_START_NANOS.set(nowNanos);
		// This publication is last so the daemon cannot observe new heartbeat data too early.
		IN_FRAME.set(true);
		FRAME_GATE.publishHeartbeat();
	}

	public static void onRenderFrameEnd(Minecraft minecraft, long nowNanos) {
		// Clear first: anything below may call UI or persistence code.
		if (FRAME_GATE.finishHeartbeat()) {
			IN_FRAME.set(false);
		}
		if (LIFECYCLE.isClientStopping()
				|| Thread.currentThread() != minecraft.getRunningThread()) {
			return;
		}
		if (!CONFIG.isGpuTimeoutWatchdogEnabled()) {
			disableRuntime();
			return;
		}
		Signal signal = PENDING_SIGNAL.getAndSet(null);
		if (signal != null) {
			latestDurationMillis = signal.durationMillis();
			incidentCount = signal.incidentCount();
			incidentCapReached = signal.capReached();
			if (signal.recoveryRequestStaged()) {
				recoveryRequestStaged = true;
			}
			status = signal.critical()
					? WatchdogStatus.CRITICAL
					: WatchdogStatus.WARNING;
			showTransitionNotification(minecraft, nowNanos, signal.critical());
		}
	}

	public static boolean requiresRenderFrameBeginTimestamp() {
		return !LIFECYCLE.isClientStopping()
				&& FRAME_GATE.requiresBegin(CONFIG.isGpuTimeoutWatchdogEnabled());
	}

	public static boolean requiresRenderFrameEndTimestamp() {
		return !LIFECYCLE.isClientStopping() && FRAME_GATE.requiresEnd();
	}

	public static ReloadToken beginResourceReload() {
		if (!CONFIG.isGpuTimeoutWatchdogEnabled()
				|| !FRAME_GATE.runtimeEnabled() || !LIFECYCLE.isEnabled()) {
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
		if (token == null || !token.counted()) {
			return;
		}
		if (future == null) {
			completeReload(token);
			return;
		}
		future.whenComplete((ignored, throwable) -> completeReload(token));
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
		FRAME_GATE.enableRuntime();
		CONTROL_GENERATION.updateAndGet(
				value -> value == Long.MAX_VALUE ? 1L : value + 1L
		);
		RELOAD_ACCOUNTING.reset();
		RELOAD_GRACE_UNTIL_NANOS.set(null);
		startupWorldGraceUntilNanos = nowNanos + settings.startupWorldGraceNanos();
		previousLevelIdentity = 0;
		// Publish enabled last so a parked daemon cannot observe stale generation data.
		LIFECYCLE.setEnabled(true);
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
		if (!FRAME_GATE.runtimeEnabled()) {
			return;
		}
		FRAME_GATE.disableRuntime();
		LIFECYCLE.setEnabled(false);
		CONTROL_GENERATION.updateAndGet(
				value -> value == Long.MAX_VALUE ? 1L : value + 1L
		);
		IN_FRAME.set(false);
		MONITORING_ALLOWED.set(false);
		RELOAD_ACCOUNTING.reset();
		RELOAD_GRACE_UNTIL_NANOS.set(null);
		PENDING_SIGNAL.set(null);
		status = WatchdogStatus.OFF;
	}

	private static void onClientStopping() {
		LIFECYCLE.beginStopping();
		FRAME_GATE.disableRuntime();
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
		long lifecycleGeneration = LIFECYCLE.transitionGeneration();
		while (!LIFECYCLE.isClientStopping()) {
			long currentGeneration = LIFECYCLE.transitionGeneration();
			if (currentGeneration != lifecycleGeneration) {
				policy.resetMonitoring();
				lifecycleGeneration = currentGeneration;
			}
			EngineSettings current = settings;
			if (!LIFECYCLE.isEnabled()) {
				policy.resetMonitoring();
				lifecycleGeneration = LIFECYCLE.awaitTransition(
						lifecycleGeneration, 0L
				);
				continue;
			}
			long sequence = FRAME_SEQUENCE.get();
			boolean activeAtStart = IN_FRAME.get();
			long startNanos = FRAME_START_NANOS.get();
			long nowNanos = System.nanoTime();
			boolean allowed = MONITORING_ALLOWED.get();
			boolean active = activeAtStart && IN_FRAME.get() && sequence == FRAME_SEQUENCE.get();
			int event = policy.evaluate(nowNanos, active, allowed, startNanos, sequence, current.policy());
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
				if (requestStaged) {
					recoveryRequestStaged = true;
				}
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
			lifecycleGeneration = LIFECYCLE.awaitTransition(
					lifecycleGeneration,
					current.policy().sampleIntervalMillis()
			);
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
			return window.isIconified() || !window.isFocused();
		}
		return false;
	}

	private static void completeReload(ReloadToken token) {
		if (token == null || !token.counted()
				|| !RELOAD_ACCOUNTING.release(
						token.controlGeneration(),
						CONTROL_GENERATION.get(),
						token.completed()
				)) {
			return;
		}
		long nowNanos = System.nanoTime();
		long graceUntil = nowNanos + settings.resourceReloadGraceNanos();
		RELOAD_GRACE_UNTIL_NANOS.accumulateAndGet(graceUntil,
				(previous, candidate) -> previous == null || candidate - previous > 0L ? candidate : previous
		);
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
		long sinceNotification = nowNanos - lastNotificationNanos;
		if (notificationShown && sinceNotification >= 0L
				&& sinceNotification < NOTIFICATION_INTERVAL_NANOS) {
			return;
		}
		minecraft.gui.hud.setOverlayMessage(Component.translatable(
				critical
						? "sodium-volt.notification.watchdog.critical"
						: "sodium-volt.notification.watchdog.warning"
		), false);
		lastNotificationNanos = nowNanos;
		notificationShown = true;
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
