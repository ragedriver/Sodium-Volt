package com.ragedriver.sodiumvolt.client.privacy;

import com.ragedriver.sodiumvolt.client.config.PrivacyScreenshotConfigTestSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class PrivacyScreenshotLogicTest {
	private PrivacyScreenshotLogicTest() {
	}

	public static void main(String[] arguments) throws Exception {
		testCaptureStateMachine();
		testFinallyCleanupAndResetGeneration();
		testFilenameSafetyAndBounds();
		PrivacyScreenshotConfigTestSupport.run();
		System.out.println("Privacy Screenshot Mode logic tests passed");
	}

	private static void testCaptureStateMachine() {
		PrivacyCaptureStateMachine state = new PrivacyCaptureStateMachine();
		check(state.state() == PrivacyCaptureStateMachine.State.IDLE, "initial idle");
		check(state.request() == PrivacyCaptureStateMachine.RequestResult.ACCEPTED,
				"first request accepted");
		check(state.request() == PrivacyCaptureStateMachine.RequestResult.COALESCED,
				"second request coalesced");
		PrivacyCaptureStateMachine.CaptureScope scope = state.beginFrame();
		check(scope.active() && state.isActive(), "pending request enters one frame");
		check(state.request() == PrivacyCaptureStateMachine.RequestResult.COALESCED,
				"active request coalesced");
		scope.close();
		scope.close();
		check(state.state() == PrivacyCaptureStateMachine.State.IDLE,
				"scope close is idempotent and returns idle");
	}

	private static void testFinallyCleanupAndResetGeneration() {
		PrivacyCaptureStateMachine state = new PrivacyCaptureStateMachine();
		state.request();
		try (PrivacyCaptureStateMachine.CaptureScope ignored = state.beginFrame()) {
			throw new ExpectedFailure();
		} catch (ExpectedFailure ignored) {
			// Expected: AutoCloseable is the same finally boundary used by renderFrame.
		}
		check(!state.isActive(), "exception leaves capture scope");

		state.request();
		PrivacyCaptureStateMachine.CaptureScope stale = state.beginFrame();
		state.reset();
		state.request();
		stale.close();
		check(state.state() == PrivacyCaptureStateMachine.State.PENDING,
				"stale scope cannot clear a newer generation");
	}

	private static void testFilenameSafetyAndBounds() throws Exception {
		Path directory = Files.createTempDirectory("sodium-volt-privacy-name-test-");
		try {
			String zeros = "privacy-" + "00".repeat(16) + ".png";
			Files.createFile(directory.resolve(zeros));
			AtomicInteger calls = new AtomicInteger();
			Optional<String> selected = PrivacyFilenameGenerator.choose(
					directory,
					destination -> Arrays.fill(
							destination,
							(byte) (calls.getAndIncrement() == 0 ? 0 : 1)
					),
					2
			);
			check(selected.isPresent() && PrivacyFilenameGenerator.isSafeFilename(
					selected.get()
			), "collision retries with a confined safe filename");
			check(selected.get().equals("privacy-" + "01".repeat(16) + ".png"),
					"filename contains only neutral random token");

			AtomicInteger boundedCalls = new AtomicInteger();
			Optional<String> exhausted = PrivacyFilenameGenerator.choose(
					directory,
					destination -> {
						boundedCalls.incrementAndGet();
						Arrays.fill(destination, (byte) 0);
					},
					PrivacyFilenameGenerator.MAXIMUM_ATTEMPTS
			);
			check(exhausted.isEmpty()
						&& boundedCalls.get() == PrivacyFilenameGenerator.MAXIMUM_ATTEMPTS,
					"collisions have a hard attempt bound");
			check(!PrivacyFilenameGenerator.isSafeFilename("../private.png")
						&& !PrivacyFilenameGenerator.isSafeFilename(
								"privacy-" + "gg".repeat(16) + ".png"
						), "unsafe names rejected");
		} finally {
			Files.deleteIfExists(directory.resolve("privacy-" + "00".repeat(16) + ".png"));
			Files.deleteIfExists(directory);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("Privacy Screenshot Mode: " + message);
		}
	}

	private static final class ExpectedFailure extends RuntimeException {
	}
}
