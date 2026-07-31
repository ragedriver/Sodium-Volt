package com.ragedriver.sodiumvolt.client.recovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RecoveryLogicTest {
	private static final long SECOND = 1_000_000_000L;

	private RecoveryLogicTest() {
	}

	public static void main(String[] arguments) throws Exception {
		testFirstCleanSession();
		testUncleanThreshold();
		testForceOneShotStaging();
		testExternalWatchdogRequestStaging();
		testMaximumAttemptGuardAndSaturation();
		testFailedMarkerWritePreventsMutation();
		testBackupReuseAndOwnershipAwareRestore();
		testStableTimerAndRollback();
		testStableRuntimeOffAndCleanTransitions();
		testRetainedProfileApcSuspension();
		testBelowThresholdStableMonitoringBreaksTheStreak();
		testTransitionFailureRetryGate();
		testNormalization();
		testStateRoundTripAndMalformedInputs();
		testSanitizedReport();
		testFpsCompositionAndApcSuspension();
		System.out.println("Volt Recovery logic tests passed");
	}

	private static void testFirstCleanSession() {
		RecoverySessionLogic.StartupPlan plan = RecoverySessionLogic.planStartup(
				RecoveryPersistentState.EMPTY,
				true,
				true,
				false,
				2,
				3
		);
		check(plan.stagedState().sessionActive(), "first enabled launch must stage a marker");
		check(plan.stagedState().crashStreak() == 0, "clean first launch must not add a streak");
		check(!plan.activateRecovery(), "clean first launch must only monitor");
		check(plan.stagedState().clean().equals(RecoveryPersistentState.EMPTY),
				"clean stop must clear marker, counters, and backup");
	}

	private static void testUncleanThreshold() {
		RecoveryPersistentState firstUnclean = new RecoveryPersistentState(
				true, 0, 0, false, false, false, false, null, null
		);
		RecoverySessionLogic.StartupPlan first = RecoverySessionLogic.planStartup(
				firstUnclean, true, true, false, 2, 3
		);
		check(first.uncleanPreviousSession(), "active marker must be detected as unclean");
		check(first.stagedState().crashStreak() == 1 && !first.activateRecovery(),
				"first unclean session must remain below threshold two");
		RecoverySessionLogic.StartupPlan second = RecoverySessionLogic.planStartup(
				first.stagedState(), true, true, false, 2, 3
		);
		check(second.stagedState().crashStreak() == 2 && second.activateRecovery(),
				"second consecutive unclean session must activate at threshold");
		check(second.stagedState().recoveryAttempts() == 1,
				"activation must persist the next attempt");
	}

	private static void testForceOneShotStaging() {
		RecoverySessionLogic.StartupPlan forced = RecoverySessionLogic.planStartup(
				RecoveryPersistentState.EMPTY, false, false, true, 5, 3
		);
		check(forced.activateRecovery() && forced.stagedState().forceRequestStaged(),
				"manual force must work with automatic detection off");
		check(!forced.consumeForceRequest(false),
				"force request must remain when recovery staging fails");
		check(forced.consumeForceRequest(true),
				"force request must be consumed only after staging succeeds");
	}

	private static void testExternalWatchdogRequestStaging() {
		RecoverySessionLogic.ExternalRequestPlan first =
				RecoverySessionLogic.planExternalRequest(true, false);
		check(first.requestRecovery(),
				"a new watchdog request must request one Recovery activation");
		check(!first.shouldAcknowledge(false, true),
				"failed Recovery state write must retain the watchdog request");
		check(!first.shouldAcknowledge(true, false),
				"attempt-guarded request must remain pending like manual force");
		check(first.shouldAcknowledge(true, true),
				"request is acknowledged only after activation was durably staged");

		RecoveryPersistentState exhausted = new RecoveryPersistentState(
				false, 0, 3, false, false, false, false, null, null
		);
		RecoverySessionLogic.StartupPlan guarded = RecoverySessionLogic.planStartup(
				exhausted, false, false, first.requestRecovery(), 2, 3
		);
		check(guarded.loopGuardActive() && !guarded.activateRecovery()
						&& !first.shouldAcknowledge(true, guarded.activateRecovery()),
				"a new request blocked by maximum attempts must remain pending");

		RecoveryPersistentState crashWindow = new RecoveryPersistentState(
				true, 0, 1, true, true, false, false, null, null
		);
		RecoverySessionLogic.ExternalRequestPlan retained =
				RecoverySessionLogic.planExternalRequest(
						true,
						crashWindow.forceRequestStaged()
				);
		check(!retained.requestRecovery(),
				"a retained request after the crash window must not increment attempts again");
		RecoverySessionLogic.StartupPlan next = RecoverySessionLogic.planStartup(
				crashWindow, false, false, retained.requestRecovery(), 2, 3
		);
		check(next.stagedState().recoveryAttempts() == 1
						&& !next.activateRecovery(),
				"previously staged force marker prevents a second activation");
		check(!retained.shouldAcknowledge(false, false)
						&& retained.shouldAcknowledge(true, false),
				"retained request is acknowledged only after the next successful state write");
		check(next.stagedState().withForceRequestStaged(
						retained.acknowledgePreviouslyStagedRequest()
				).forceRequestStaged(),
				"write-before-delete window must retain the durable no-retry latch");
	}

	private static void testMaximumAttemptGuardAndSaturation() {
		RecoveryPersistentState exhausted = new RecoveryPersistentState(
				true, 4, 3, true, false, false, false, null, null
		);
		RecoverySessionLogic.StartupPlan guarded = RecoverySessionLogic.planStartup(
				exhausted, true, true, false, 2, 3
		);
		check(guarded.loopGuardActive() && !guarded.activateRecovery(),
				"attempt exhaustion must stop profile reapplication");
		check(guarded.stagedState().recoveryAttempts() == 3,
				"attempt guard must not increment attempts");
		check(
				RecoveryPersistentState.saturatingIncrement(
						RecoveryPersistentState.MAXIMUM_COUNTER
				) == RecoveryPersistentState.MAXIMUM_COUNTER,
				"state counters must saturate"
		);
	}

	private static void testFailedMarkerWritePreventsMutation() {
		RecoverySessionLogic.StartupPlan plan = RecoverySessionLogic.planStartup(
				RecoveryPersistentState.EMPTY, false, false, true, 2, 3
		);
		check(!plan.mayMutateOptions(false),
				"a failed marker/attempt write must forbid graphics mutation");
		check(plan.mayMutateOptions(true),
				"a successful state transaction may proceed to its staged mutation");
	}

	private static void testBackupReuseAndOwnershipAwareRestore() {
		RecoveryOptionSnapshot original = snapshot(
				16, 100, RecoveryOptionSnapshot.PARTICLES_ALL,
				RecoveryOptionSnapshot.CLOUDS_FANCY, true, true, 5,
				RecoveryOptionSnapshot.GRAPHICS_FANCY
		);
		RecoveryOptionSnapshot lastApplied = snapshot(
				8, 50, RecoveryOptionSnapshot.PARTICLES_MINIMAL,
				RecoveryOptionSnapshot.CLOUDS_OFF, false, false, 0,
				RecoveryOptionSnapshot.GRAPHICS_CUSTOM
		);
		RecoveryOptionSnapshot actualAfterCrash = snapshot(
				6, 50, RecoveryOptionSnapshot.PARTICLES_MINIMAL,
				RecoveryOptionSnapshot.CLOUDS_OFF, false, false, 0,
				RecoveryOptionSnapshot.GRAPHICS_CUSTOM
		);
		RecoveryOptionSnapshot rebased = original.rebase(actualAfterCrash, lastApplied);
		check(rebased.renderDistance() == 6,
				"a lower VRAM/APC/external render value must rebase into the original");
		check(rebased.entityDistancePercent() == 100,
				"unchanged Recovery-owned values must retain the true original");
		check(rebased.graphicsPreset() == RecoveryOptionSnapshot.GRAPHICS_CUSTOM,
				"crash rebase must preserve current preset metadata with an external render value");
		RecoveryOptionSnapshot desired = actualAfterCrash.safeProfile(8, 50, true);
		check(desired.renderDistance() == 6,
				"backup reuse must never raise the lower current render distance");
		RecoveryOptionSnapshot.RestoreResult crashRoundTrip =
				rebased.restoreOwned(desired, desired);
		check(crashRoundTrip.snapshot().equals(rebased),
				"crash-rebased ownership must round-trip its values and preset metadata");

		RecoveryOptionSnapshot.RestoreResult restored =
				original.restoreOwned(actualAfterCrash, lastApplied);
		check(restored.snapshot().renderDistance() == 6,
				"restore must preserve an externally lowered render distance");
		check(restored.snapshot().entityDistancePercent() == 100,
				"restore must recover a field still equal to Recovery's last value");
		check(restored.snapshot().particleMode() == RecoveryOptionSnapshot.PARTICLES_ALL,
				"owned particle mode must restore");
		check(restored.snapshot().graphicsPreset() == RecoveryOptionSnapshot.GRAPHICS_CUSTOM,
				"preserving an external render value must conservatively preserve current metadata");

		RecoveryOptionSnapshot mixedExternal = snapshot(
				6, 50, RecoveryOptionSnapshot.PARTICLES_DECREASED,
				RecoveryOptionSnapshot.CLOUDS_FAST, false, false, 0,
				RecoveryOptionSnapshot.GRAPHICS_CUSTOM
		);
		RecoveryOptionSnapshot mixedRebased = original.rebase(mixedExternal, lastApplied);
		check(mixedRebased.renderDistance() == 6
						&& mixedRebased.particleMode() == RecoveryOptionSnapshot.PARTICLES_DECREASED
						&& mixedRebased.cloudMode() == RecoveryOptionSnapshot.CLOUDS_FAST
						&& mixedRebased.graphicsPreset() == RecoveryOptionSnapshot.GRAPHICS_CUSTOM,
				"mixed render, particle, and cloud changes must rebase with current metadata");
		RecoveryOptionSnapshot.RestoreResult mixedRestored =
				original.restoreOwned(mixedExternal, lastApplied);
		check(mixedRestored.snapshot().renderDistance() == 6
						&& mixedRestored.snapshot().particleMode()
								== RecoveryOptionSnapshot.PARTICLES_DECREASED
						&& mixedRestored.snapshot().cloudMode()
								== RecoveryOptionSnapshot.CLOUDS_FAST
						&& mixedRestored.snapshot().graphicsPreset()
								== RecoveryOptionSnapshot.GRAPHICS_CUSTOM,
				"mixed external fields must be preserved without inconsistent preset metadata");

		RecoveryOptionSnapshot.RestoreResult fullyOwned =
				original.restoreOwned(lastApplied, lastApplied);
		check(fullyOwned.snapshot().equals(original),
				"a wholly Recovery-owned safe snapshot must still restore its original preset");
	}

	private static void testStableTimerAndRollback() {
		RecoveryStableTimer timer = new RecoveryStableTimer();
		check(!timer.update(false, 10L * SECOND, 30L * SECOND),
				"invalid frame must not start the stable timer");
		check(!timer.update(true, 100L * SECOND, 30L * SECOND),
				"first valid frame must establish a baseline");
		check(!timer.update(true, 120L * SECOND, 30L * SECOND),
				"partial stable interval must wait");
		check(!timer.update(true, 50L * SECOND, 30L * SECOND),
				"clock rollback must restart rather than complete the timer");
		check(timer.update(true, 80L * SECOND, 30L * SECOND),
				"restarted stable interval must complete at its new boundary");
		timer.reset();
		check(timer.remainingSeconds(0L, 120L * SECOND) == 120L,
				"reset countdown must expose the configured duration");
	}

	private static void testStableRuntimeOffAndCleanTransitions() {
		RecoveryOptionSnapshot original = snapshot(
				16, 100, 0, 2, true, true, 5, 1
		);
		RecoveryOptionSnapshot safe = original.safeProfile(8, 50, true);
		RecoveryPersistentState active = new RecoveryPersistentState(
				true, 2, 1, true, false, true, true, original, safe
		);
		RecoveryPersistentState stableKeepingProfile = active.stable(true);
		check(stableKeepingProfile.sessionActive()
						&& stableKeepingProfile.hasBackup()
						&& !stableKeepingProfile.recoveryActive()
						&& stableKeepingProfile.crashStreak() == 0
						&& stableKeepingProfile.recoveryAttempts() == 0,
				"stable session without restoration must keep only ownership until clean stop");
		check(active.stable(false).hasBackup() == false,
				"stable restoration must clear ownership after success");
		check(active.clean().equals(RecoveryPersistentState.EMPTY),
				"runtime off and clean stop share the fully cleared committed state");
	}

	private static void testRetainedProfileApcSuspension() {
		RecoveryOptionSnapshot original = snapshot(
				16, 100, 0, 2, true, true, 5, 1
		);
		RecoveryOptionSnapshot safe = original.safeProfile(8, 50, true);
		RecoveryPersistentState active = new RecoveryPersistentState(
				true, 2, 1, true, false, true, true, original, safe
		);
		RecoveryPersistentState retained = active.stable(true);
		check(RecoverySessionLogic.suspendApc(
						retained.recoveryActive(),
						retained.hasBackup() && retained.profileApplied(),
						true
				),
				"a retained applied safe profile must keep APC suspended after stability");
		check(RecoverySessionLogic.composeFpsLimit(144, retained.recoveryActive(), true, 60) == 144,
				"a retained stable profile must not retain the transient Recovery FPS cap");

		RecoveryPersistentState restored = active.stable(false);
		check(!RecoverySessionLogic.suspendApc(
						restored.recoveryActive(),
						restored.hasBackup() && restored.profileApplied(),
						true
				),
				"stable restoration must release APC suspension");
		check(!RecoverySessionLogic.suspendApc(
						active.clean().recoveryActive(),
						active.clean().hasBackup() && active.clean().profileApplied(),
						true
				),
				"runtime off and clean stop must release APC suspension");

		RecoveryPersistentState unappliedBackup = new RecoveryPersistentState(
				true, 2, 1, false, false, true, false, original, safe
		);
		check(!RecoverySessionLogic.suspendApc(
						unappliedBackup.recoveryActive(),
						unappliedBackup.hasBackup() && unappliedBackup.profileApplied(),
						true
				),
				"a backup that was never applied must not suspend APC");
		check(!RecoverySessionLogic.suspendApc(false, true, false),
				"explicitly disabling Suspend APC must allow the intentional conflict");
	}

	private static void testBelowThresholdStableMonitoringBreaksTheStreak() {
		RecoveryPersistentState priorUnclean = new RecoveryPersistentState(
				true, 0, 0, false, false, false, false, null, null
		);
		RecoverySessionLogic.StartupPlan belowThreshold = RecoverySessionLogic.planStartup(
				priorUnclean, true, true, false, 2, 3
		);
		check(belowThreshold.stagedState().crashStreak() == 1
						&& !belowThreshold.activateRecovery(),
				"first unclean launch must monitor below threshold");
		RecoveryPersistentState stableMonitoring =
				belowThreshold.stagedState().stable(false);
		check(stableMonitoring.sessionActive() && stableMonitoring.crashStreak() == 0,
				"stable monitoring must keep this session marked while clearing old counters");
		RecoverySessionLogic.StartupPlan laterUnclean = RecoverySessionLogic.planStartup(
				stableMonitoring, true, true, false, 2, 3
		);
		check(laterUnclean.stagedState().crashStreak() == 1
						&& !laterUnclean.activateRecovery(),
				"a later unclean stop after a stable session must restart at one, not reach two");
	}

	private static void testTransitionFailureRetryGate() {
		RecoveryTransitionGate gate = new RecoveryTransitionGate();
		gate.observeMasterState(true);
		int startupAttempts = 0;
		if (gate.mayAttemptTransition()) {
			startupAttempts++;
			gate.transitionFailed();
		}
		for (int frame = 0; frame < 10_000; frame++) {
			gate.observeMasterState(true);
			if (gate.mayAttemptTransition()) {
				startupAttempts++;
			}
		}
		check(startupAttempts == 1,
				"persistent startup failure must permit one attempt, not one per frame");

		gate.observeMasterState(false);
		check(gate.mayAttemptTransition(),
				"turning the master off must permit one bounded cleanup transition");
		gate.transitionFailed();
		for (int frame = 0; frame < 10_000; frame++) {
			gate.observeMasterState(false);
			check(!gate.mayAttemptTransition(),
					"failed runtime-off cleanup must not retry every frame");
		}

		gate.observeMasterState(true);
		check(gate.mayAttemptTransition(),
				"OFF to ON must permit exactly one new startup staging attempt");
		gate.transitionSucceeded();
		check(gate.mayAttemptTransition(),
				"successful startup staging must leave later transitions available");

		gate.transitionFailed();
		for (int frame = 0; frame < 10_000; frame++) {
			gate.observeMasterState(true);
			check(!gate.mayAttemptTransition(),
					"failed stable completion must not retry restore or save every frame");
		}

		RecoverySessionLogic.StartupPlan plan = RecoverySessionLogic.planStartup(
				RecoveryPersistentState.EMPTY, false, false, true, 2, 3
		);
		check(!plan.mayMutateOptions(false),
				"a blocked staging transaction must never authorize graphics mutation");

		RecoveryTransitionGate stoppingGate = new RecoveryTransitionGate();
		stoppingGate.observeMasterState(true);
		stoppingGate.transitionSucceeded();
		stoppingGate.beginClientStopping();
		for (int frame = 0; frame < 10_000; frame++) {
			stoppingGate.observeMasterState(frame % 2 == 0);
			check(stoppingGate.isClientStopping()
							&& !stoppingGate.mayAttemptTransition(),
					"late render frames must never rearm monitoring after CLIENT_STOPPING");
		}
	}

	private static void testNormalization() {
		check(RecoveryConfigNormalization.clamp(-1, 4, 16) == 4,
				"render-distance normalization lower bound");
		check(RecoveryConfigNormalization.clamp(99, 4, 16) == 16,
				"render-distance normalization upper bound");
		check(RecoveryConfigNormalization.clampStep(53, 25, 100, 5) == 55,
				"entity-distance normalization step");
		check(RecoveryConfigNormalization.clampStep(127, 30, 120, 5) == 120,
				"FPS normalization upper bound");
		check(RecoveryConfigNormalization.clampStep(179, 30, 300, 30) == 180,
				"stable-duration normalization step");
	}

	private static void testStateRoundTripAndMalformedInputs() throws IOException {
		Path directory = Files.createTempDirectory("sodium-volt-recovery-test-");
		try {
			Path statePath = directory.resolve("state.json");
			RecoveryOptionSnapshot original = snapshot(
					16, 100, 0, 2, true, true, 5, 1
			);
			RecoveryOptionSnapshot safe = original.safeProfile(8, 50, true);
			RecoveryPersistentState state = new RecoveryPersistentState(
					true, 2, 1, true, true, true, true, original, safe
			);
			check(RecoveryStateStore.save(statePath, state), "state round-trip write");
			check(RecoveryStateStore.load(statePath).equals(state), "state round-trip read");

			String validState = Files.readString(statePath);
			Files.writeString(
					statePath,
					validState.replaceFirst(
							"\"session_active\"",
							"\"unknown_root\": false,\\n  \"session_active\""
					)
			);
			check(RecoveryStateStore.load(statePath).equals(RecoveryPersistentState.EMPTY),
					"unknown root state field must fail open");

			Files.writeString(
					statePath,
					validState.replaceFirst(
							"\"render_distance\"",
							"\"unknown_snapshot\": 0,\\n    \"render_distance\""
					)
			);
			check(RecoveryStateStore.load(statePath).equals(RecoveryPersistentState.EMPTY),
					"unknown nested snapshot field must fail open");

			Files.writeString(
					statePath,
					validState.replaceFirst(
							"\"version\": 1",
							"\"version\": 1,\\n  \"version\": 1"
					)
			);
			check(RecoveryStateStore.load(statePath).equals(RecoveryPersistentState.EMPTY),
					"duplicate state field must fail open instead of silently overwriting");

			Files.writeString(statePath, "{\"version\":1,\"session_active\":\"yes\"}");
			check(RecoveryStateStore.load(statePath).equals(RecoveryPersistentState.EMPTY),
					"wrongly typed state must fail open");

			byte[] oversized = new byte[RecoveryStateStore.MAXIMUM_SIZE_BYTES + 1];
			Files.write(statePath, oversized);
			check(RecoveryStateStore.load(statePath).equals(RecoveryPersistentState.EMPTY),
					"oversized state must fail open");

			StringBuilder deep = new StringBuilder(40_000);
			for (int index = 0; index < 10_000; index++) {
				deep.append('{').append("\"x\":");
			}
			deep.append('0');
			for (int index = 0; index < 10_000; index++) {
				deep.append('}');
			}
			Files.writeString(statePath, deep, StandardCharsets.UTF_8);
			check(RecoveryStateStore.load(statePath).equals(RecoveryPersistentState.EMPTY),
					"deep malformed state must fail open without escaping stack overflow");
		} finally {
			Files.deleteIfExists(directory.resolve("state.json"));
			Files.deleteIfExists(directory);
		}
	}

	private static void testSanitizedReport() {
		RecoveryReport report = new RecoveryReport(
				RecoveryReport.Reason.POSSIBLE_RENDERER_FAILURE,
				2,
				1,
				true,
				RecoveryReport.Restoration.PENDING
		);
		String json = report.toJson().toString();
		check(report.toJson().size() == 6, "report must retain a fixed six-field schema");
		check(!json.contains("path") && !json.contains("stack")
						&& !json.contains("server") && !json.contains("world")
						&& !json.contains("account") && !json.contains("device"),
				"report must not contain private or raw diagnostic fields");
		check(json.length() < 1024, "fixed recovery report must remain tightly bounded");
	}

	private static void testFpsCompositionAndApcSuspension() {
		check(RecoverySessionLogic.composeFpsLimit(144, true, true, 60) == 60,
				"recovery FPS cap must lower a larger vanilla/Smart result");
		check(RecoverySessionLogic.composeFpsLimit(30, true, true, 60) == 30,
				"recovery FPS cap must never raise a stricter result");
		check(RecoverySessionLogic.composeFpsLimit(144, false, true, 60) == 144,
				"inactive recovery must pass through exactly");
		check(RecoverySessionLogic.composeFpsLimit(144, true, false, 60) == 144,
				"disabled recovery limiter must pass through exactly");
		check(RecoverySessionLogic.suspendApc(true, false, true),
				"active configured recovery must suspend APC");
		check(!RecoverySessionLogic.suspendApc(false, false, true)
						&& !RecoverySessionLogic.suspendApc(true, false, false),
				"APC suspension must require both state and setting");
	}

	private static RecoveryOptionSnapshot snapshot(
			int renderDistance,
			int entityDistancePercent,
			int particles,
			int clouds,
			boolean ambientOcclusion,
			boolean entityShadows,
			int biomeBlendRadius,
			int graphicsPreset
	) {
		return new RecoveryOptionSnapshot(
				renderDistance,
				entityDistancePercent,
				particles,
				clouds,
				ambientOcclusion,
				entityShadows,
				biomeBlendRadius,
				graphicsPreset
		);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
