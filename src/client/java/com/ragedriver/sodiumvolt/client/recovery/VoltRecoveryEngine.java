package com.ragedriver.sodiumvolt.client.recovery;

import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.config.VoltRecoveryConfig;
import com.ragedriver.sodiumvolt.client.mixin.OptionInstanceAccessor;
import com.ragedriver.sodiumvolt.client.performance.AdaptivePerformanceController;
import com.ragedriver.sodiumvolt.client.watchdog.WatchdogRecoveryRequestStore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;

import java.util.Objects;

public final class VoltRecoveryEngine {
	private static final long NOTIFICATION_INTERVAL_NANOS = 10_000_000_000L;
	private static final VoltRecoveryConfig CONFIG = VoltRecoveryConfig.getInstance();
	private static final RecoveryStableTimer STABLE_TIMER = new RecoveryStableTimer();
	private static final RecoveryTransitionGate TRANSITION_GATE = new RecoveryTransitionGate();

	private static volatile boolean recoveryActive;
	private static volatile StatisticsSnapshot statistics = StatisticsSnapshot.EMPTY;
	private static RecoveryPersistentState state = RecoveryPersistentState.EMPTY;
	private static boolean monitoring;
	private static boolean forceEligibleThisLaunch;
	private static boolean loopGuardActive;
	private static boolean manualActivation;
	private static boolean watchdogActivation;
	private static boolean stableSessionReached;
	private static boolean persistenceFailureLogged;
	private static long lastNotificationNanos;
	private static Notification pendingNotification = Notification.NONE;

	private VoltRecoveryEngine() {
	}

	public static void register() {
		boolean enabled = CONFIG.isVoltRecoveryEnabled();
		forceEligibleThisLaunch = enabled;
		TRANSITION_GATE.observeMasterState(enabled);
		ClientLifecycleEvents.CLIENT_STARTED.register(minecraft -> {
			boolean currentlyEnabled = CONFIG.isVoltRecoveryEnabled();
			TRANSITION_GATE.observeMasterState(currentlyEnabled);
			if (currentlyEnabled && !monitoring
					&& TRANSITION_GATE.mayAttemptTransition()) {
				startMonitoring(minecraft, forceEligibleThisLaunch);
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(VoltRecoveryEngine::onClientStopping);
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null && minecraft.options != null && enabled
				&& TRANSITION_GATE.mayAttemptTransition()) {
			startMonitoring(minecraft, forceEligibleThisLaunch);
		}
	}

	public static void onRenderFrame(Minecraft minecraft, long nowNanos) {
		if (Thread.currentThread() != minecraft.getRunningThread()) {
			return;
		}
		if (TRANSITION_GATE.isClientStopping()) {
			clearTransientRecoveryState();
			publishStatistics(nowNanos, RecoveryStatus.OFF);
			return;
		}
		boolean enabled = CONFIG.isVoltRecoveryEnabled();
		TRANSITION_GATE.observeMasterState(enabled);
		if (enabled) {
			if (!monitoring && TRANSITION_GATE.mayAttemptTransition()) {
				startMonitoring(minecraft, forceEligibleThisLaunch);
			}
		} else if (monitoring || state.sessionActive() || state.hasBackup()) {
			if (TRANSITION_GATE.mayAttemptTransition()) {
				disableAtRuntime(minecraft);
			}
		}
		if (!monitoring || !enabled) {
			publishStatistics(nowNanos, RecoveryStatus.OFF);
			return;
		}
		boolean stabilityTrackingRequired = recoveryActive
				|| state.crashStreak() > 0
				|| state.recoveryAttempts() > 0
				|| loopGuardActive;
		if (stabilityTrackingRequired) {
			boolean validFrame = minecraft.level != null
					&& minecraft.player != null
					&& minecraft.isGameLoadFinished()
					&& !minecraft.isPaused()
					&& minecraft.gui.screen() == null
					&& minecraft.gui.overlay() == null;
			long stableDuration = CONFIG.getStableSessionDurationSeconds() * 1_000_000_000L;
			if (STABLE_TIMER.update(validFrame, nowNanos, stableDuration)) {
				if (TRANSITION_GATE.mayAttemptTransition()) {
					completeStableSession(minecraft);
				}
			}
		} else {
			STABLE_TIMER.reset();
		}
		showPendingNotification(minecraft, nowNanos);
		publishStatistics(nowNanos, currentStatus());
	}

	public static int applyFramerateLimit(int currentLimit) {
		return RecoverySessionLogic.composeFpsLimit(
				currentLimit,
				!TRANSITION_GATE.isClientStopping()
						&& CONFIG.isVoltRecoveryEnabled() && recoveryActive,
				CONFIG.isLimitFpsDuringRecovery(),
				CONFIG.getRecoveryFpsCap()
		);
	}

	public static boolean shouldSuspendApcSampling() {
		return !TRANSITION_GATE.isClientStopping()
				&& CONFIG.isVoltRecoveryEnabled()
				&& RecoverySessionLogic.suspendApc(
						recoveryActive,
						state.hasBackup() && state.profileApplied(),
						CONFIG.isSuspendAdaptiveController()
				);
	}

	public static StatisticsSnapshot snapshotStatistics() {
		if (!CONFIG.isVoltRecoveryEnabled() || !CONFIG.isShowRecoveryStatsInInspector()) {
			return StatisticsSnapshot.EMPTY;
		}
		return statistics;
	}

	private static void startMonitoring(Minecraft minecraft, boolean allowForceForThisLaunch) {
		RecoveryPersistentState loaded = RecoveryStateStore.load();
		boolean stagedForceFromPriorLaunch = loaded.forceRequestStaged();
		WatchdogRecoveryRequestStore.Request watchdogRequest =
				WatchdogRecoveryRequestStore.load();
		RecoverySessionLogic.ExternalRequestPlan externalRequest =
				RecoverySessionLogic.planExternalRequest(
						watchdogRequest.pending(),
						stagedForceFromPriorLaunch
				);
		boolean manualForceRequest = allowForceForThisLaunch
				&& CONFIG.isForceSafeModeNextLaunch()
				&& !stagedForceFromPriorLaunch;
		boolean forceRequest = manualForceRequest || externalRequest.requestRecovery();
		RecoverySessionLogic.StartupPlan plan = RecoverySessionLogic.planStartup(
				loaded,
				CONFIG.isDetectUncleanSessions(),
				CONFIG.isAutomaticSafeMode(),
				forceRequest,
				CONFIG.getCrashStreakThreshold(),
				CONFIG.getMaximumRecoveryAttempts()
		);
		RecoveryPersistentState staged = plan.stagedState();
		if (externalRequest.acknowledgePreviouslyStagedRequest()) {
			// Preserve the durable no-retry latch across the write-before-delete window.
			staged = staged.withForceRequestStaged(true);
		}
		RecoveryOptionSnapshot actualBeforeMutation = null;
		if (plan.activateRecovery() && CONFIG.isApplySafeGraphicsProfile()) {
			actualBeforeMutation = capture(minecraft.options);
			RecoveryOptionSnapshot original = loaded.hasBackup()
					? loaded.original().rebase(actualBeforeMutation, loaded.lastApplied())
					: actualBeforeMutation;
			RecoveryOptionSnapshot desired = actualBeforeMutation.safeProfile(
					CONFIG.getSafeRenderDistance(),
					CONFIG.getSafeEntityDistancePercent(),
					CONFIG.isReduceExpensiveGraphics()
			);
			staged = staged.withBackup(original, desired);
		}
		if (!RecoveryStateStore.save(staged)) {
			TRANSITION_GATE.transitionFailed();
			failPersistenceOnce();
			clearTransientRecoveryState();
			state = loaded;
			return;
		}
		if (externalRequest.shouldAcknowledge(true, plan.activateRecovery())) {
			WatchdogRecoveryRequestStore.acknowledge();
		}

		TRANSITION_GATE.transitionSucceeded();
		state = staged;
		monitoring = true;
		forceEligibleThisLaunch = false;
		loopGuardActive = plan.loopGuardActive();
		watchdogActivation = externalRequest.requestRecovery() && plan.activateRecovery();
		manualActivation = manualForceRequest
				&& !watchdogActivation
				&& plan.activateRecovery();
		stableSessionReached = false;
		recoveryActive = plan.activateRecovery();
		STABLE_TIMER.reset();

		boolean profileApplied = false;
		if (plan.mayMutateOptions(true) && CONFIG.isApplySafeGraphicsProfile()
				&& actualBeforeMutation != null && state.hasBackup()) {
			profileApplied = applyStagedProfile(minecraft, actualBeforeMutation);
		}
		if (manualForceRequest && plan.consumeForceRequest(true)) {
			consumeForceRequest();
		} else if (stagedForceFromPriorLaunch && CONFIG.isForceSafeModeNextLaunch()) {
			consumeForceRequest();
		}

		if (recoveryActive) {
			pendingNotification = watchdogActivation
					? Notification.WATCHDOG
					: manualActivation ? Notification.MANUAL : Notification.SAFE;
			writeReport(new RecoveryReport(
					watchdogActivation
							? RecoveryReport.Reason.POSSIBLE_GPU_RENDER_STALL
							: manualActivation
									? RecoveryReport.Reason.MANUAL_REQUEST
									: RecoveryReport.Reason.POSSIBLE_RENDERER_FAILURE,
					state.crashStreak(),
					state.recoveryAttempts(),
					profileApplied,
					RecoveryReport.Restoration.NOT_REQUESTED
			));
		} else if (loopGuardActive) {
			pendingNotification = Notification.LOOP_GUARD;
			writeReport(new RecoveryReport(
					RecoveryReport.Reason.ATTEMPT_LIMIT_REACHED,
					state.crashStreak(),
					state.recoveryAttempts(),
					false,
					state.hasBackup()
							? RecoveryReport.Restoration.PENDING
							: RecoveryReport.Restoration.NOT_REQUESTED
			));
		}
		publishStatistics(System.nanoTime(), currentStatus());
	}

	private static boolean applyStagedProfile(
			Minecraft minecraft,
			RecoveryOptionSnapshot actualBeforeMutation
	) {
		try {
			AdaptivePerformanceController.prepareForRecoveryOwnedOptions(minecraft.options);
			boolean changed = apply(minecraft.options, state.lastApplied());
			AdaptivePerformanceController.acceptRecoveryOwnedOptions(minecraft.options);
			if (changed) {
				minecraft.options.save();
			}
			RecoveryOptionSnapshot applied = capture(minecraft.options);
			RecoveryPersistentState updated =
					state.withAppliedBackup(state.original(), applied);
			if (RecoveryStateStore.save(updated)) {
				state = updated;
			} else {
				TRANSITION_GATE.transitionFailed();
				failPersistenceOnce();
			}
			return true;
		} catch (RuntimeException | LinkageError exception) {
			try {
				boolean rolledBack = apply(minecraft.options, actualBeforeMutation);
				AdaptivePerformanceController.acceptRecoveryOwnedOptions(minecraft.options);
				if (rolledBack) {
					minecraft.options.save();
				}
			} catch (RuntimeException | LinkageError rollbackFailure) {
				SodiumVolt.LOGGER.warn(
						"Volt Recovery could not fully roll back its owned graphics profile"
				);
			}
			SodiumVolt.LOGGER.warn(
					"Volt Recovery could not apply its owned graphics profile; backup retained"
			);
			return false;
		}
	}

	private static void completeStableSession(Minecraft minecraft) {
		boolean reportTransition = recoveryActive || loopGuardActive || state.hasBackup();
		boolean profileWasApplied = state.profileApplied();
		boolean restore = CONFIG.isRestoreOwnedSettingsAfterStableSession();
		RecoveryReport.Restoration restoration = RecoveryReport.Restoration.NOT_REQUESTED;
		if (restore && state.hasBackup()) {
			RestoreOutcome outcome = restoreOwnedOptions(minecraft);
			if (!outcome.succeeded()) {
				TRANSITION_GATE.transitionFailed();
				return;
			}
			restoration = outcome.externalChangesPreserved()
					? RecoveryReport.Restoration.PRESERVED_EXTERNAL_CHANGES
					: RecoveryReport.Restoration.RESTORED;
		}
		RecoveryPersistentState stable = state.stable(!restore && state.hasBackup());
		if (!RecoveryStateStore.save(stable)) {
			TRANSITION_GATE.transitionFailed();
			failPersistenceOnce();
			return;
		}
		TRANSITION_GATE.transitionSucceeded();
		state = stable;
		recoveryActive = false;
		loopGuardActive = false;
		stableSessionReached = reportTransition;
		STABLE_TIMER.reset();
		if (reportTransition) {
			pendingNotification = Notification.STABLE;
			writeReport(new RecoveryReport(
					RecoveryReport.Reason.STABLE_SESSION,
					0,
					0,
					profileWasApplied,
					restoration
			));
		}
	}

	private static void disableAtRuntime(Minecraft minecraft) {
		boolean hadBackup = state.hasBackup();
		boolean reportTransition = state.crashStreak() > 0
				|| state.recoveryAttempts() > 0
				|| hadBackup
				|| loopGuardActive;
		clearTransientRecoveryState();
		RestoreOutcome outcome = state.hasBackup()
				? restoreOwnedOptions(minecraft)
				: RestoreOutcome.SUCCESS;
		if (!outcome.succeeded()) {
			TRANSITION_GATE.transitionFailed();
			publishStatistics(System.nanoTime(), RecoveryStatus.RESTORATION_PENDING);
			return;
		}
		if (!RecoveryStateStore.save(RecoveryPersistentState.EMPTY)) {
			TRANSITION_GATE.transitionFailed();
			failPersistenceOnce();
			publishStatistics(System.nanoTime(), RecoveryStatus.RESTORATION_PENDING);
			return;
		}
		TRANSITION_GATE.transitionSucceeded();
		if (reportTransition) {
			writeReport(new RecoveryReport(
					RecoveryReport.Reason.RUNTIME_DISABLED,
					state.crashStreak(),
					state.recoveryAttempts(),
					false,
					restorationResult(hadBackup, outcome)
			));
		}
		state = RecoveryPersistentState.EMPTY;
		statistics = StatisticsSnapshot.EMPTY;
	}

	private static void onClientStopping(Minecraft minecraft) {
		TRANSITION_GATE.beginClientStopping();
		recoveryActive = false;
		if (!monitoring && !state.sessionActive() && !state.hasBackup()) {
			return;
		}
		boolean hadBackup = state.hasBackup();
		boolean reportTransition = state.recoveryAttempts() > 0
				|| hadBackup
				|| loopGuardActive;
		RestoreOutcome outcome = hadBackup
				? restoreOwnedOptions(minecraft)
				: RestoreOutcome.SUCCESS;
		if (!outcome.succeeded() || !RecoveryStateStore.save(RecoveryPersistentState.EMPTY)) {
			TRANSITION_GATE.transitionFailed();
			failPersistenceOnce();
			return;
		}
		TRANSITION_GATE.transitionSucceeded();
		if (reportTransition) {
			writeReport(new RecoveryReport(
					RecoveryReport.Reason.CLEAN_STOP,
					state.crashStreak(),
					state.recoveryAttempts(),
					false,
					restorationResult(hadBackup, outcome)
			));
		}
		state = RecoveryPersistentState.EMPTY;
		monitoring = false;
		statistics = StatisticsSnapshot.EMPTY;
	}

	private static void clearTransientRecoveryState() {
		recoveryActive = false;
		monitoring = false;
		loopGuardActive = false;
		manualActivation = false;
		watchdogActivation = false;
		stableSessionReached = false;
		pendingNotification = Notification.NONE;
		STABLE_TIMER.reset();
	}

	private static RestoreOutcome restoreOwnedOptions(Minecraft minecraft) {
		if (!state.hasBackup()) {
			return RestoreOutcome.SUCCESS;
		}
		try {
			RecoveryOptionSnapshot actual = capture(minecraft.options);
			RecoveryOptionSnapshot.RestoreResult result =
					state.original().restoreOwned(actual, state.lastApplied());
			AdaptivePerformanceController.prepareForRecoveryOwnedOptions(minecraft.options);
			if (result.changed()) {
				try {
					apply(minecraft.options, result.snapshot());
					AdaptivePerformanceController.acceptRecoveryOwnedOptions(minecraft.options);
					minecraft.options.save();
				} catch (RuntimeException | LinkageError exception) {
					rollbackOwnedOptions(minecraft, actual);
					throw exception;
				}
			}
			boolean externalChanges = !result.snapshot().equals(state.original());
			return new RestoreOutcome(true, externalChanges);
		} catch (RuntimeException | LinkageError exception) {
			SodiumVolt.LOGGER.warn(
					"Volt Recovery could not restore all owned graphics settings; backup retained"
			);
			return RestoreOutcome.FAILURE;
		}
	}

	private static void rollbackOwnedOptions(
			Minecraft minecraft,
			RecoveryOptionSnapshot snapshot
	) {
		try {
			boolean changed = apply(minecraft.options, snapshot);
			AdaptivePerformanceController.acceptRecoveryOwnedOptions(minecraft.options);
			if (changed) {
				minecraft.options.save();
			}
		} catch (RuntimeException | LinkageError rollbackFailure) {
			SodiumVolt.LOGGER.warn(
					"Volt Recovery could not fully roll back its owned graphics restoration"
			);
		}
	}

	private static RecoveryOptionSnapshot capture(Options options) {
		return new RecoveryOptionSnapshot(
				options.renderDistance().get(),
				(int) Math.round(options.entityDistanceScaling().get() * 100.0D),
				particleMode(options.particles().get()),
				cloudMode(options.cloudStatus().get()),
				options.ambientOcclusion().get(),
				options.entityShadows().get(),
				options.biomeBlendRadius().get(),
				graphicsPreset(options.graphicsPreset().get())
		);
	}

	@SuppressWarnings("unchecked")
	private static boolean apply(Options options, RecoveryOptionSnapshot snapshot) {
		boolean changed = false;
		changed |= setIfDifferent(options.renderDistance(), snapshot.renderDistance());
		changed |= setIfDifferent(
				options.entityDistanceScaling(),
				snapshot.entityDistancePercent() / 100.0D
		);
		changed |= setIfDifferent(options.particles(), particleStatus(snapshot.particleMode()));
		changed |= setIfDifferent(options.cloudStatus(), cloudStatus(snapshot.cloudMode()));
		changed |= setIfDifferent(options.ambientOcclusion(), snapshot.ambientOcclusion());
		changed |= setIfDifferent(options.entityShadows(), snapshot.entityShadows());
		changed |= setIfDifferent(options.biomeBlendRadius(), snapshot.biomeBlendRadius());
		GraphicsPreset preset = graphicsPreset(snapshot.graphicsPreset());
		if (options.graphicsPreset().get() != preset) {
			((OptionInstanceAccessor<GraphicsPreset>) (Object) options.graphicsPreset())
					.sodiumVolt$setValueWithoutCallback(preset);
			changed = true;
		}
		return changed;
	}

	private static int particleMode(ParticleStatus value) {
		return switch (value) {
			case ALL -> RecoveryOptionSnapshot.PARTICLES_ALL;
			case DECREASED -> RecoveryOptionSnapshot.PARTICLES_DECREASED;
			case MINIMAL -> RecoveryOptionSnapshot.PARTICLES_MINIMAL;
		};
	}

	private static ParticleStatus particleStatus(int value) {
		return switch (value) {
			case RecoveryOptionSnapshot.PARTICLES_ALL -> ParticleStatus.ALL;
			case RecoveryOptionSnapshot.PARTICLES_DECREASED -> ParticleStatus.DECREASED;
			default -> ParticleStatus.MINIMAL;
		};
	}

	private static int cloudMode(CloudStatus value) {
		return switch (value) {
			case OFF -> RecoveryOptionSnapshot.CLOUDS_OFF;
			case FAST -> RecoveryOptionSnapshot.CLOUDS_FAST;
			case FANCY -> RecoveryOptionSnapshot.CLOUDS_FANCY;
		};
	}

	private static CloudStatus cloudStatus(int value) {
		return switch (value) {
			case RecoveryOptionSnapshot.CLOUDS_FAST -> CloudStatus.FAST;
			case RecoveryOptionSnapshot.CLOUDS_FANCY -> CloudStatus.FANCY;
			default -> CloudStatus.OFF;
		};
	}

	private static int graphicsPreset(GraphicsPreset value) {
		return switch (value) {
			case FAST -> RecoveryOptionSnapshot.GRAPHICS_FAST;
			case FANCY -> RecoveryOptionSnapshot.GRAPHICS_FANCY;
			case FABULOUS -> RecoveryOptionSnapshot.GRAPHICS_FABULOUS;
			case CUSTOM -> RecoveryOptionSnapshot.GRAPHICS_CUSTOM;
		};
	}

	private static GraphicsPreset graphicsPreset(int value) {
		return switch (value) {
			case RecoveryOptionSnapshot.GRAPHICS_FAST -> GraphicsPreset.FAST;
			case RecoveryOptionSnapshot.GRAPHICS_FANCY -> GraphicsPreset.FANCY;
			case RecoveryOptionSnapshot.GRAPHICS_FABULOUS -> GraphicsPreset.FABULOUS;
			default -> GraphicsPreset.CUSTOM;
		};
	}

	private static <T> boolean setIfDifferent(OptionInstance<T> option, T value) {
		if (Objects.equals(option.get(), value)) {
			return false;
		}
		option.set(value);
		return true;
	}

	private static void consumeForceRequest() {
		CONFIG.setForceSafeModeNextLaunch(false);
		if (!CONFIG.saveChecked()) {
			failPersistenceOnce();
		}
	}

	private static void writeReport(RecoveryReport report) {
		if (CONFIG.isWriteSanitizedLocalRecoveryReport()) {
			RecoveryReportWriter.write(report);
		}
	}

	private static void showPendingNotification(Minecraft minecraft, long nowNanos) {
		if (pendingNotification == Notification.NONE
				|| !CONFIG.isShowRecoveryNotifications()
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
		String key = switch (pendingNotification) {
			case SAFE -> "sodium-volt.notification.recovery.safe";
			case MANUAL -> "sodium-volt.notification.recovery.manual";
			case WATCHDOG -> "sodium-volt.notification.recovery.watchdog";
			case STABLE -> "sodium-volt.notification.recovery.stable";
			case LOOP_GUARD -> "sodium-volt.notification.recovery.loop_guard";
			case NONE -> throw new IllegalStateException("No notification");
		};
		minecraft.gui.hud.setOverlayMessage(Component.translatable(key), false);
		lastNotificationNanos = nowNanos;
		pendingNotification = Notification.NONE;
	}

	private static RecoveryStatus currentStatus() {
		if (loopGuardActive) {
			return RecoveryStatus.LOOP_GUARD;
		}
		if (recoveryActive) {
			return RecoveryStatus.SAFE_MODE;
		}
		if (stableSessionReached) {
			return RecoveryStatus.STABLE;
		}
		return monitoring ? RecoveryStatus.MONITORING : RecoveryStatus.OFF;
	}

	private static RecoveryReport.Restoration restorationResult(
			boolean hadBackup,
			RestoreOutcome outcome
	) {
		if (!hadBackup) {
			return RecoveryReport.Restoration.NOT_REQUESTED;
		}
		return outcome.externalChangesPreserved()
				? RecoveryReport.Restoration.PRESERVED_EXTERNAL_CHANGES
				: RecoveryReport.Restoration.RESTORED;
	}

	private static void publishStatistics(long nowNanos, RecoveryStatus status) {
		boolean trackingStability = recoveryActive
				|| state.crashStreak() > 0
				|| state.recoveryAttempts() > 0
				|| loopGuardActive;
		long secondsToStable = trackingStability
				? STABLE_TIMER.remainingSeconds(
						nowNanos,
						CONFIG.getStableSessionDurationSeconds() * 1_000_000_000L
				)
				: 0L;
		statistics = new StatisticsSnapshot(
				status,
				state.crashStreak(),
				state.recoveryAttempts(),
				CONFIG.getMaximumRecoveryAttempts(),
				state.profileApplied(),
				secondsToStable,
				RecoverySessionLogic.composeFpsLimit(
						Integer.MAX_VALUE,
						CONFIG.isVoltRecoveryEnabled() && recoveryActive,
						CONFIG.isLimitFpsDuringRecovery(),
						CONFIG.getRecoveryFpsCap()
				),
				shouldSuspendApcSampling()
		);
	}

	private static void failPersistenceOnce() {
		if (persistenceFailureLogged) {
			return;
		}
		persistenceFailureLogged = true;
		SodiumVolt.LOGGER.warn(
				"Volt Recovery could not safely persist owned state and will not mutate graphics"
		);
	}

	private enum Notification {
		NONE,
		SAFE,
		MANUAL,
		WATCHDOG,
		STABLE,
		LOOP_GUARD
	}

	public enum RecoveryStatus {
		OFF,
		MONITORING,
		SAFE_MODE,
		LOOP_GUARD,
		STABLE,
		RESTORATION_PENDING
	}

	public record StatisticsSnapshot(
			RecoveryStatus status,
			int crashStreak,
			int recoveryAttempts,
			int maximumAttempts,
			boolean ownedProfile,
			long secondsToStable,
			int recoveryFpsCap,
			boolean apcSuspended
	) {
		public static final StatisticsSnapshot EMPTY = new StatisticsSnapshot(
				RecoveryStatus.OFF,
				0,
				0,
				0,
				false,
				0L,
				Integer.MAX_VALUE,
				false
		);
	}

	private record RestoreOutcome(boolean succeeded, boolean externalChangesPreserved) {
		private static final RestoreOutcome SUCCESS = new RestoreOutcome(true, false);
		private static final RestoreOutcome FAILURE = new RestoreOutcome(false, false);
	}
}
