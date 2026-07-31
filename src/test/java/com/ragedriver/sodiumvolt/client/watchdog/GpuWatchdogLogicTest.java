package com.ragedriver.sodiumvolt.client.watchdog;

import com.ragedriver.sodiumvolt.client.config.GpuWatchdogConfigTestSupport;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GpuWatchdogLogicTest {
	private static final long SECOND = 1_000_000_000L;

	private GpuWatchdogLogicTest() {
	}

	public static void main(String[] arguments) throws Exception {
		testThresholdsAndConfirmations();
		testSuppressionAndClockSafety();
		testCooldownCapAndSaturation();
		testLifecycleLateLatch();
		testReloadBoundsAndGenerations();
		testRequestStoreStrictness();
		testReportBoundsAndStrictness();
		GpuWatchdogConfigTestSupport.run();
		System.out.println("GPU Timeout Watchdog logic tests passed");
	}

	private static void testThresholdsAndConfirmations() {
		GpuWatchdogPolicy policy = new GpuWatchdogPolicy();
		GpuWatchdogPolicy.Settings settings = settings(2, 2);
		check(policy.evaluate(2 * SECOND, true, true, 1L, 1L, settings)
						== GpuWatchdogPolicy.EVENT_NONE,
				"warning must wait for the configured duration");
		check(policy.evaluate(4 * SECOND, true, true, 1L, 1L, settings)
						== GpuWatchdogPolicy.EVENT_WARNING,
				"warning must transition once");
		check(policy.evaluate(9 * SECOND, true, true, 1L, 1L, settings)
						== GpuWatchdogPolicy.EVENT_NONE,
				"first critical sample must only confirm");
		check(policy.evaluate(9 * SECOND + 250_000_000L, true, true, 1L, 1L, settings)
						== GpuWatchdogPolicy.EVENT_CRITICAL,
				"second critical sample must confirm one incident");
		check(policy.evaluate(20 * SECOND, true, true, 1L, 1L, settings)
						== GpuWatchdogPolicy.EVENT_NONE,
				"one stuck frame must be handled only once");
	}

	private static void testSuppressionAndClockSafety() {
		GpuWatchdogPolicy policy = new GpuWatchdogPolicy();
		GpuWatchdogPolicy.Settings settings = settings(3, 3);
		policy.evaluate(9 * SECOND, true, true, 1L, 1L, settings);
		check(policy.evaluate(10 * SECOND, true, false, 1L, 1L, settings)
						== GpuWatchdogPolicy.EVENT_NONE,
				"suppression must produce no event");
		check(policy.evaluate(11 * SECOND, true, true, 1L, 1L, settings)
						== GpuWatchdogPolicy.EVENT_WARNING,
				"resuming must begin a new warning observation");
		check(policy.evaluate(11 * SECOND + 250_000_000L, true, true, 1L, 1L, settings)
						== GpuWatchdogPolicy.EVENT_NONE,
				"confirmation count must restart after suppression");

		check(policy.evaluate(5L, true, true, 10L, 2L, settings)
						== GpuWatchdogPolicy.EVENT_NONE
						&& policy.latestDurationMillis() == 0,
				"clock rollback must reset rather than classify");
		check(policy.evaluate(Long.MAX_VALUE, true, true, 1L, 3L, settings)
						== GpuWatchdogPolicy.EVENT_WARNING
						&& policy.latestDurationMillis()
								== GpuWatchdogPolicy.MAXIMUM_REPORTED_MILLIS,
				"reported duration must saturate at its fixed bound");
	}

	private static void testCooldownCapAndSaturation() {
		GpuWatchdogPolicy policy = new GpuWatchdogPolicy();
		GpuWatchdogPolicy.Settings settings = settings(1, 2);
		check(critical(policy, settings, 20 * SECOND, 1L)
						== GpuWatchdogPolicy.EVENT_CRITICAL,
				"first incident");
		check(critical(policy, settings, 30 * SECOND, 2L)
						== GpuWatchdogPolicy.EVENT_NONE,
				"cooldown must suppress a new frame incident");
		check(critical(policy, settings, 90 * SECOND, 3L)
						== GpuWatchdogPolicy.EVENT_CRITICAL,
				"incident after cooldown");
		check(critical(policy, settings, 160 * SECOND, 4L)
						== GpuWatchdogPolicy.EVENT_NONE
						&& policy.incidents() == 2
						&& policy.capReached(settings),
				"session cap must be strict");
		GpuWatchdogPolicy.Settings normalized = new GpuWatchdogPolicy.Settings(
				5 * SECOND, 4 * SECOND, -5, -1L, 99, 1, true, true
		);
		check(normalized.criticalThresholdNanos() > normalized.warningThresholdNanos()
						&& normalized.criticalConfirmationCount() == 1
						&& normalized.maximumIncidents() == 10
						&& normalized.sampleIntervalMillis() == 100,
				"runtime settings must normalize to safe bounds");
	}

	private static int critical(
			GpuWatchdogPolicy policy,
			GpuWatchdogPolicy.Settings settings,
			long now,
			long sequence
	) {
		return policy.evaluate(now, true, true, 1L, sequence, settings);
	}

	private static void testLifecycleLateLatch() {
		WatchdogLifecycleGate gate = new WatchdogLifecycleGate();
		check(gate.setEnabled(true) && gate.claimThreadStart(),
				"first enable may start exactly one daemon");
		check(!gate.claimThreadStart(), "duplicate start must be rejected");
		gate.setEnabled(false);
		check(!gate.isEnabled(), "runtime off");
		check(gate.setEnabled(true) && !gate.claimThreadStart(),
				"runtime on must reuse the original daemon");
		gate.beginStopping();
		for (int frame = 0; frame < 10_000; frame++) {
			check(!gate.setEnabled(true)
							&& !gate.claimThreadStart()
							&& gate.isClientStopping(),
					"late frames must never rearm after the permanent stop latch");
		}
	}

	private static void testReloadBoundsAndGenerations() {
		WatchdogReloadAccounting accounting = new WatchdogReloadAccounting(32);
		AtomicBoolean[] completions = new AtomicBoolean[32];
		for (int index = 0; index < completions.length; index++) {
			completions[index] = new AtomicBoolean();
			check(accounting.tryClaim(), "first 32 reloads must be counted");
		}
		for (int index = 0; index < 1_000; index++) {
			check(!accounting.tryClaim(), "overflow reloads must remain uncounted");
		}
		check(accounting.active() == 32, "reload count must saturate exactly");
		check(accounting.release(7L, 7L, completions[31])
						&& accounting.release(7L, 7L, completions[0]),
				"out-of-order valid completions must release their own slots");
		check(!accounting.release(7L, 7L, completions[31]),
				"double completion must be ignored");
		check(!accounting.release(6L, 7L, completions[1]),
				"stale generation must not release current accounting");
		check(accounting.active() == 30, "only two valid unique completions release");
		accounting.reset();
		check(accounting.active() == 0, "runtime generation reset clears old accounting");
	}

	private static void testRequestStoreStrictness() throws Exception {
		Path directory = Files.createTempDirectory("sodium-volt-watchdog-request-test-");
		Path path = directory.resolve("request.json");
		Path dangling = directory.resolve("dangling-request.json");
		try {
			check(WatchdogRecoveryRequestStore.stage(path, 42_000, 2),
					"request stage must write atomically");
			WatchdogRecoveryRequestStore.Request request =
					WatchdogRecoveryRequestStore.load(path);
			check(request.pending() && request.longestStallMillis() == 42_000
							&& request.incidentCount() == 2,
					"request round-trip");
			String valid = Files.readString(path);
			assertInvalidRequest(path, valid.replaceFirst(
					"\"version\": 1",
					"\"version\": 1, \"version\": 1"
			), "duplicate request field");
			assertInvalidRequest(path, valid.replaceFirst(
					"\"pending\": true",
					"\"pending\": \"yes\""
			), "wrong request type");
			assertInvalidRequest(path, valid.replaceFirst(
					"\"pending\": true",
					"\"unknown\": true, \"pending\": true"
			), "unknown request field");
			Files.write(path, new byte[WatchdogRecoveryRequestStore.MAXIMUM_SIZE_BYTES + 1]);
			check(!WatchdogRecoveryRequestStore.load(path).pending(),
					"oversized request must fail open");
			StringBuilder deep = new StringBuilder(8_000);
			for (int index = 0; index < 1_000; index++) {
				deep.append("{\"x\":");
			}
			deep.append('0');
			for (int index = 0; index < 1_000; index++) {
				deep.append('}');
			}
			Files.writeString(path, deep, StandardCharsets.UTF_8);
			check(!WatchdogRecoveryRequestStore.load(path).pending(),
					"deep malformed request must not escape stack safety");
			check(WatchdogRecoveryRequestStore.acknowledge(path)
							&& !Files.exists(path),
					"acknowledgement must delete only the exact regular request file");
			Files.createDirectory(path);
			check(!WatchdogRecoveryRequestStore.load(path).pending()
							&& !WatchdogRecoveryRequestStore.acknowledge(path)
							&& Files.isDirectory(path),
					"unsafe non-regular targets must be rejected and preserved");
			if (createDanglingSymlink(dangling)) {
				check(Files.isSymbolicLink(dangling) && !Files.exists(dangling),
						"test request link must be dangling");
				check(!WatchdogRecoveryRequestStore.load(dangling).pending(),
						"dangling request symlink must fail open on read");
				check(!WatchdogRecoveryRequestStore.stage(dangling, 9_000, 1)
								&& Files.isSymbolicLink(dangling)
								&& !Files.exists(dangling),
						"request stage must refuse and preserve a dangling symlink");
				check(!WatchdogRecoveryRequestStore.acknowledge(dangling)
								&& Files.isSymbolicLink(dangling),
						"request acknowledgement must refuse and preserve a dangling symlink");
			}
		} finally {
			Files.deleteIfExists(dangling);
			Files.deleteIfExists(path);
			Files.deleteIfExists(directory);
		}
	}

	private static void assertInvalidRequest(Path path, String document, String message)
			throws Exception {
		Files.writeString(path, document, StandardCharsets.UTF_8);
		check(!WatchdogRecoveryRequestStore.load(path).pending(), message);
	}

	private static void testReportBoundsAndStrictness() throws Exception {
		Path directory = Files.createTempDirectory("sodium-volt-watchdog-report-test-");
		Path path = directory.resolve("report.json");
		Path dangling = directory.resolve("dangling-report.json");
		try {
			GpuWatchdogIncidentReport report = new GpuWatchdogIncidentReport(
					Integer.MAX_VALUE, 3_000, 8_000, 2, 3, true
			);
			check(report.toJson().size() == 8
							&& report.toJson().toString().length() < 1_024,
					"sanitized report must have a small fixed schema");
			String json = report.toJson().toString();
			check(!json.contains("path") && !json.contains("stack")
							&& !json.contains("driver") && !json.contains("device")
							&& !json.contains("server") && !json.contains("world")
							&& !json.contains("account"),
					"report must contain no raw private diagnostic fields");
			check(GpuWatchdogReportStore.write(path, report)
							&& GpuWatchdogReportStore.read(path) != null,
					"report strict round-trip");
			String valid = Files.readString(path);
			Files.writeString(path, valid.replaceFirst(
					"\"version\": 1",
					"\"version\": 1, \"unknown\": 1"
			), StandardCharsets.UTF_8);
			check(GpuWatchdogReportStore.read(path) == null,
					"unknown report field must fail open");
			Files.writeString(path, valid.replaceFirst(
					"\"incident_count\": 3",
					"\"incident_count\": 99"
			), StandardCharsets.UTF_8);
			check(GpuWatchdogReportStore.read(path) == null,
					"out-of-range report field must fail open");
			if (createDanglingSymlink(dangling)) {
				check(Files.isSymbolicLink(dangling) && !Files.exists(dangling),
						"test report link must be dangling");
				check(GpuWatchdogReportStore.read(dangling) == null,
						"dangling report symlink must fail open on read");
				check(!GpuWatchdogReportStore.write(dangling, report)
								&& Files.isSymbolicLink(dangling)
								&& !Files.exists(dangling),
						"report write must refuse and preserve a dangling symlink");
			}
		} finally {
			Files.deleteIfExists(dangling);
			Files.deleteIfExists(path);
			Files.deleteIfExists(directory);
		}
	}

	private static boolean createDanglingSymlink(Path link) throws Exception {
		try {
			Files.createSymbolicLink(link, Path.of("missing-watchdog-target.json"));
			return true;
		} catch (UnsupportedOperationException exception) {
			System.out.println("Skipping dangling-symlink assertions: symlinks unsupported");
			return false;
		} catch (FileSystemException exception) {
			String reason = String.valueOf(exception.getReason()).toLowerCase(Locale.ROOT);
			if (reason.contains("not supported")
					|| reason.contains("privilege")
					|| reason.contains("symbolic links are not supported")) {
				System.out.println(
						"Skipping dangling-symlink assertions: platform cannot create symlinks"
				);
				return false;
			}
			throw exception;
		}
	}

	private static GpuWatchdogPolicy.Settings settings(
			int confirmations,
			int maximumIncidents
	) {
		return new GpuWatchdogPolicy.Settings(
				3 * SECOND,
				8 * SECOND,
				confirmations,
				60 * SECOND,
				maximumIncidents,
				250,
				true,
				true
		);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
