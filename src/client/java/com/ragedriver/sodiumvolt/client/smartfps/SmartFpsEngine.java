package com.ragedriver.sodiumvolt.client.smartfps;

import com.mojang.blaze3d.platform.Window;
import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.config.SmartFpsConfig;
import com.ragedriver.sodiumvolt.client.performance.InspectorStatisticsDemand;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public final class SmartFpsEngine {
	private static final long NOTIFICATION_INTERVAL_NANOS = 10_000_000_000L;
	private static final SmartFpsConfig CONFIG = SmartFpsConfig.getInstance();
	private static final SmartFpsPolicy POLICY = new SmartFpsPolicy();
	private static final SmartFpsStatisticsGate STATISTICS_GATE =
			new SmartFpsStatisticsGate();
	private static final SmartFpsPowerQueryGate POWER_QUERY_GATE =
			new SmartFpsPowerQueryGate();
	private static final java.util.concurrent.atomic.AtomicBoolean POWER_FAILURE_LOGGED =
			new java.util.concurrent.atomic.AtomicBoolean();
	private static final AtomicLong POWER_GENERATION = new AtomicLong();

	private static volatile SmartFpsConfig.RuntimeSnapshot runtimeSettings =
			CONFIG.runtimeSnapshot();
	private static volatile SmartFpsPowerSnapshot power = SmartFpsPowerSnapshot.UNKNOWN;
	private static volatile StatisticsSnapshot statistics = StatisticsSnapshot.EMPTY;
	private static volatile boolean suspendApcSampling;
	private static ExecutorService powerExecutor;
	private static volatile boolean masterActive;
	private static boolean batteryPollingActive;
	private static volatile boolean runtimeFailed;
	private static boolean wrongThreadLogged;
	private static long nextPowerPollNanos;
	private static long previousRenderNanos = Long.MIN_VALUE;
	private static long lastNotificationNanos;
	private static int previousReasons;
	private static int previousEffectiveLimit = SmartFpsPolicy.NO_CAP;

	private SmartFpsEngine() {
	}

	public static void register() {
		ClientLifecycleEvents.CLIENT_STOPPING.register(SmartFpsEngine::onClientStopping);
	}

	public static int applyFramerateLimit(Minecraft minecraft, int vanillaLimit, long nowNanos) {
		if (Thread.currentThread() != minecraft.getRunningThread()) {
			if (!wrongThreadLogged) {
				wrongThreadLogged = true;
				SodiumVolt.LOGGER.warn("Smart FPS ignored a limiter query from a non-client thread");
			}
			return vanillaLimit;
		}
		SmartFpsConfig.RuntimeSnapshot settings = refreshRuntimeSettings();
		if (runtimeFailed) {
			if (!settings.enabled()) {
				runtimeFailed = false;
			}
			return vanillaLimit;
		}
		try {
			return update(minecraft, vanillaLimit, nowNanos, settings);
		} catch (RuntimeException | LinkageError exception) {
			runtimeFailed = true;
			deactivate();
			SodiumVolt.LOGGER.warn("Smart FPS encountered an unexpected failure and will fail open");
			return vanillaLimit;
		}
	}

	public static boolean requiresFramerateLimitTimestamp() {
		return CONFIG.isSmartFpsEnabled() || masterActive || runtimeFailed;
	}

	public static boolean shouldSuspendApcSampling() {
		return suspendApcSampling;
	}

	public static StatisticsSnapshot snapshotStatistics() {
		SmartFpsConfig.RuntimeSnapshot settings = refreshRuntimeSettings();
		if (!settings.enabled()
				|| !STATISTICS_GATE.canExpose(
						settings.showInspectorStatistics()
								&& InspectorStatisticsDemand.isRequested(),
						settings.inspectorStatisticsRevision()
				)) {
			return StatisticsSnapshot.EMPTY;
		}
		return statistics;
	}

	private static int update(
			Minecraft minecraft,
			int vanillaLimit,
			long nowNanos,
			SmartFpsConfig.RuntimeSnapshot settings
	) {
		if (!settings.enabled()) {
			if (masterActive) {
				deactivate();
			}
			return vanillaLimit;
		}
		masterActive = true;
		updatePowerPolling(nowNanos, settings);

		Window window = minecraft.getWindow();
		boolean minimized = window.isIconified();
		boolean focused = window.isFocused();
		SmartFpsPowerSnapshot currentPower = power;
		int result = POLICY.evaluate(
				vanillaLimit,
				nowNanos,
				true,
				minimized,
				focused,
				settings.throttleWhenMinimized(),
				settings.minimizedTargetFps(),
				settings.throttleWhenUnfocused(),
				settings.unfocusedTargetFps(),
				settings.backgroundActivationDelaySeconds() * 1_000_000_000L,
				settings.batteryMode(),
				settings.batteryTargetFps(),
				settings.bypassBatteryLimitWhileCharging(),
				settings.lowBatteryProtection(),
				settings.lowBatteryThresholdPercent(),
				settings.lowBatteryTargetFps(),
				currentPower
		);
		suspendApcSampling = POLICY.shouldSuspendApcSampling();
		SmartFpsStatisticsGate.Action statisticsAction = STATISTICS_GATE.refresh(
				settings.showInspectorStatistics()
						&& InspectorStatisticsDemand.isRequested()
		);
		if (statisticsAction == SmartFpsStatisticsGate.Action.PUBLISH) {
			publishStatisticsIfChanged(
					minimized,
					focused,
					currentPower,
					POLICY.reasons(),
					POLICY.smartCap(),
					result,
					suspendApcSampling
			);
			STATISTICS_GATE.markPublished(settings.inspectorStatisticsRevision());
		} else if (statisticsAction == SmartFpsStatisticsGate.Action.CLEAR) {
			statistics = StatisticsSnapshot.EMPTY;
		}
		notifyTransition(
				minecraft,
				POLICY.reasons(),
				result,
				nowNanos,
				settings.showStatusNotifications()
		);
		return result;
	}

	private static void updatePowerPolling(
			long nowNanos,
			SmartFpsConfig.RuntimeSnapshot settings
	) {
		if (!settings.batteryMode()) {
			if (batteryPollingActive) {
				stopPowerPolling();
			}
			return;
		}
		batteryPollingActive = true;
		if (previousRenderNanos != Long.MIN_VALUE && nowNanos < previousRenderNanos) {
			nextPowerPollNanos = nowNanos;
		}
		previousRenderNanos = nowNanos;
		if (nextPowerPollNanos != 0L && nowNanos < nextPowerPollNanos) {
			return;
		}
		nextPowerPollNanos = saturatingAdd(
				nowNanos,
				settings.powerPollIntervalSeconds() * 1_000_000_000L
		);
		long queryToken = POWER_QUERY_GATE.tryBegin();
		if (queryToken == 0L) {
			return;
		}
		long generation = POWER_GENERATION.get();
		try {
			executor().execute(() -> {
				try {
					SmartFpsPowerSnapshot observed = SmartFpsPowerProbe.query();
					if (POWER_GENERATION.get() == generation) {
						power = observed;
					}
				} catch (RuntimeException | LinkageError exception) {
					failOpenPowerQuery(generation);
				} catch (Error error) {
					if (SmartFpsPowerProbe.mustRethrow(error)) {
						throw error;
					}
					failOpenPowerQuery(generation);
				} finally {
					POWER_QUERY_GATE.finish(queryToken);
				}
			});
		} catch (RejectedExecutionException exception) {
			POWER_QUERY_GATE.finish(queryToken);
			power = SmartFpsPowerSnapshot.UNKNOWN;
		}
	}

	private static void failOpenPowerQuery(long generation) {
		if (POWER_GENERATION.get() == generation) {
			power = SmartFpsPowerSnapshot.UNKNOWN;
		}
		if (POWER_FAILURE_LOGGED.compareAndSet(false, true)) {
			SodiumVolt.LOGGER.warn(
					"Smart FPS could not read a reliable local battery state; "
							+ "battery limits will fail open"
			);
		}
	}

	private static synchronized ExecutorService executor() {
		if (powerExecutor == null || powerExecutor.isShutdown()) {
			powerExecutor = Executors.newSingleThreadExecutor(task -> {
				Thread thread = new Thread(task, "Sodium Volt Power Probe");
				thread.setDaemon(true);
				return thread;
			});
		}
		return powerExecutor;
	}

	private static void notifyTransition(
			Minecraft minecraft,
			int reasons,
			int effectiveLimit,
			long nowNanos,
			boolean showStatusNotifications
	) {
		if (lastNotificationNanos != 0L && nowNanos < lastNotificationNanos) {
			lastNotificationNanos = 0L;
		}
		boolean changed = reasons != previousReasons
				|| reasons != 0 && effectiveLimit != previousEffectiveLimit;
		previousReasons = reasons;
		previousEffectiveLimit = effectiveLimit;
		if (!changed || !showStatusNotifications
				|| minecraft.level == null || minecraft.player == null
				|| minecraft.gui.screen() != null || minecraft.gui.overlay() != null
				|| lastNotificationNanos != 0L
				&& safeElapsed(nowNanos, lastNotificationNanos) < NOTIFICATION_INTERVAL_NANOS) {
			return;
		}
		Component message = reasons == 0
				? Component.translatable("sodium-volt.notification.smart_fps.restored")
				: Component.translatable(
						"sodium-volt.notification.smart_fps.active",
						effectiveLimit
				);
		lastNotificationNanos = nowNanos;
		minecraft.gui.hud.setOverlayMessage(message, false);
	}

	private static void publishStatisticsIfChanged(
			boolean minimized,
			boolean focused,
			SmartFpsPowerSnapshot currentPower,
			int reasons,
			int smartCap,
			int effectiveLimit,
			boolean apcSuspended
	) {
		StatisticsSnapshot previous = statistics;
		if (previous != StatisticsSnapshot.EMPTY
				&& previous.minimized == minimized
				&& previous.focused == focused
				&& previous.powerState == currentPower.state()
				&& previous.batteryPercentage == currentPower.percentage()
				&& previous.reasons == reasons
				&& previous.smartCap == smartCap
				&& previous.effectiveLimit == effectiveLimit
				&& previous.apcSamplingSuspended == apcSuspended) {
			return;
		}
		statistics = new StatisticsSnapshot(
				minimized,
				focused,
				currentPower.state(),
				currentPower.percentage(),
				reasons,
				smartCap,
				effectiveLimit,
				apcSuspended
		);
	}

	private static void deactivate() {
		masterActive = false;
		suspendApcSampling = false;
		POLICY.reset();
		stopPowerPolling();
		previousReasons = 0;
		previousEffectiveLimit = SmartFpsPolicy.NO_CAP;
		STATISTICS_GATE.reset();
		statistics = StatisticsSnapshot.EMPTY;
	}

	private static void invalidatePowerSnapshot() {
		POWER_GENERATION.incrementAndGet();
		POWER_QUERY_GATE.cancel();
		power = SmartFpsPowerSnapshot.UNKNOWN;
		batteryPollingActive = false;
		nextPowerPollNanos = 0L;
		previousRenderNanos = Long.MIN_VALUE;
	}

	private static void stopPowerPolling() {
		invalidatePowerSnapshot();
		shutdownPowerExecutor();
	}

	private static void shutdownPowerExecutor() {
		synchronized (SmartFpsEngine.class) {
			if (powerExecutor != null) {
				powerExecutor.shutdownNow();
				powerExecutor = null;
			}
		}
	}

	private static SmartFpsConfig.RuntimeSnapshot refreshRuntimeSettings() {
		SmartFpsConfig.RuntimeSnapshot current = runtimeSettings;
		long revision = CONFIG.runtimeRevision();
		if (current.revision() == revision) {
			return current;
		}
		SmartFpsConfig.RuntimeSnapshot refreshed = CONFIG.runtimeSnapshot();
		runtimeSettings = refreshed;
		if (!refreshed.enabled() || !refreshed.batteryMode()) {
			stopPowerPolling();
		} else if (!current.batteryMode()
				|| current.powerPollIntervalSeconds() != refreshed.powerPollIntervalSeconds()) {
			invalidatePowerSnapshot();
		}
		return refreshed;
	}

	private static void onClientStopping(Minecraft minecraft) {
		deactivate();
	}

	private static long safeElapsed(long current, long previous) {
		if (current < previous) {
			return 0L;
		}
		long elapsed = current - previous;
		return elapsed < 0L ? Long.MAX_VALUE : elapsed;
	}

	private static long saturatingAdd(long left, long right) {
		if (right > 0L && left > Long.MAX_VALUE - right) {
			return Long.MAX_VALUE;
		}
		return left + right;
	}

	public record StatisticsSnapshot(
			boolean minimized,
			boolean focused,
			SmartFpsPowerSnapshot.PowerState powerState,
			int batteryPercentage,
			int reasons,
			int smartCap,
			int effectiveLimit,
			boolean apcSamplingSuspended
	) {
		public static final StatisticsSnapshot EMPTY = new StatisticsSnapshot(
				false,
				true,
				SmartFpsPowerSnapshot.PowerState.UNKNOWN,
				-1,
				0,
				SmartFpsPolicy.NO_CAP,
				SmartFpsPolicy.NO_CAP,
				false
		);
	}
}
