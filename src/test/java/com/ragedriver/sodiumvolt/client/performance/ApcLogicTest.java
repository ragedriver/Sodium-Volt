package com.ragedriver.sodiumvolt.client.performance;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.server.level.ParticleStatus;

public final class ApcLogicTest {
	private ApcLogicTest() {
	}

	public static void main(String[] args) {
		testFrameWindowPercentileAndBounds();
		testEffectiveTargetCeilings();
		testHysteresisAndBoundaryCounters();
		testQualityOrderingAndProfileBounds();
		testAnimationThrottleSeverityThreshold();
		testConfigDistanceNormalization();
		testImmutableSnapshotRebase();
		testRecoveryOwnedOptionHandoff();
		System.out.println("Volt APC logic tests passed");
	}

	private static void testFrameWindowPercentileAndBounds() {
		ApcFrameWindow window = new ApcFrameWindow(100);
		window.addNanos(50_000L);
		window.addNanos(1_100_000_000L);
		for (int milliseconds = 1; milliseconds <= 100; milliseconds++) {
			window.addNanos(milliseconds * 1_000_000L);
		}
		long[] sortingBuffer = new long[100];
		assertNear(95.0D, window.p95Milliseconds(100, sortingBuffer), 0.0001D, "p95");
		assertNear(97.0D, window.p95Milliseconds(60, sortingBuffer), 0.0001D, "recent p95");
		assertEquals(60, window.size(60), "requested frame window bound");
		window.clear();
		assertEquals(0, window.size(100), "clear");
	}

	private static void testEffectiveTargetCeilings() {
		assertEquals(60, ApcControllerLogic.effectiveTargetFps(60, 260, false, 144), "slider target");
		assertEquals(90, ApcControllerLogic.effectiveTargetFps(120, 90, false, 144), "cap ceiling");
		assertEquals(60, ApcControllerLogic.effectiveTargetFps(120, 260, true, 60), "VSync ceiling");
		assertEquals(75, ApcControllerLogic.effectiveTargetFps(260, 120, true, 75), "Max with two ceilings");
		assertEquals(
				ApcControllerLogic.UNLIMITED_TARGET,
				ApcControllerLogic.effectiveTargetFps(260, 260, false, 0),
				"fully unlimited"
		);
	}

	private static void testHysteresisAndBoundaryCounters() {
		ApcControllerLogic controller = new ApcControllerLogic();
		controller.reset(0, 0L);
		ApcControllerLogic.Decision first = controller.evaluate(
				20.0D, 60, 5, 0, 3, 10_000_000_000L, 1_000_000_000L
		);
		assertEquals(ApcControllerLogic.Action.HOLD, first.action(), "first pressure confirmation");
		ApcControllerLogic.Decision second = controller.evaluate(
				20.0D, 60, 5, 0, 3, 10_000_000_000L, 2_000_000_000L
		);
		assertEquals(ApcControllerLogic.Action.DOWNSHIFT, second.action(), "confirmed downshift");
		assertEquals(1, second.level(), "downshift level");
		assertEquals(
				ApcControllerLogic.Action.HOLD,
				controller.evaluate(16.7D, 60, 5, 0, 3, 10_000_000_000L, 3_000_000_000L).action(),
				"hysteresis band"
		);
		assertEquals(
				ApcControllerLogic.Action.HOLD,
				controller.evaluate(10.0D, 60, 5, 0, 3, 10_000_000_000L, 5_000_000_000L).action(),
				"recovery delay"
		);
		controller.evaluate(10.0D, 60, 5, 0, 3, 10_000_000_000L, 12_000_000_000L);
		ApcControllerLogic.Decision recovery = controller.evaluate(
				10.0D, 60, 5, 0, 3, 10_000_000_000L, 13_000_000_000L
		);
		assertEquals(ApcControllerLogic.Action.RECOVER, recovery.action(), "confirmed recovery");
		assertEquals(0, recovery.level(), "recovery floor");

		controller.reset(3, 0L);
		for (int index = 0; index < 100_000; index++) {
			assertEquals(
					ApcControllerLogic.Action.HOLD,
					controller.evaluate(40.0D, 60, 5, 0, 3, 0L, index + 1L).action(),
					"maximum boundary"
			);
		}
		controller.reset(0, 0L);
		for (int index = 0; index < 100_000; index++) {
			assertEquals(
					ApcControllerLogic.Action.HOLD,
					controller.evaluate(5.0D, 60, 5, 0, 3, 0L, index + 1L).action(),
					"minimum boundary"
			);
		}
	}

	private static void testQualityOrderingAndProfileBounds() {
		int maximum = ApcQualityPlan.maximumLevel(12, 4, true, true, true, true);
		assertEquals(11, maximum, "maximum quality level");
		assertStages(ApcQualityPlan.stages(1, 12, 4, true, true, true, true), 1, 0, 0, 12, 0);
		assertStages(ApcQualityPlan.stages(2, 12, 4, true, true, true, true), 2, 0, 0, 12, 0);
		assertStages(ApcQualityPlan.stages(3, 12, 4, true, true, true, true), 2, 1, 0, 12, 0);
		assertStages(ApcQualityPlan.stages(5, 12, 4, true, true, true, true), 2, 2, 1, 10, 0);
		assertStages(ApcQualityPlan.stages(11, 12, 4, true, true, true, true), 2, 2, 4, 4, 3);
		assertEquals(
				11,
				ApcQualityPlan.initialLevel(ApcQualityPlan.ProfilePolicy.MAX_PERFORMANCE, maximum),
				"Max Performance initial floor"
		);
		assertEquals(
				8,
				ApcQualityPlan.recoveryFloor(ApcQualityPlan.ProfilePolicy.MAX_PERFORMANCE, maximum),
				"Max Performance recovery ceiling"
		);
		assertEquals(
				0,
				ApcQualityPlan.recoveryFloor(ApcQualityPlan.ProfilePolicy.MAX_QUALITY, maximum),
				"Max Quality recovery ceiling"
		);
	}

	private static void testConfigDistanceNormalization() {
		assertEquals(
				new ApcConfigNormalization.DistanceBounds(8, 8),
				ApcConfigNormalization.normalizeDistanceBounds(16, 8),
				"cross validation"
		);
		assertEquals(
				new ApcConfigNormalization.DistanceBounds(4, 32),
				ApcConfigNormalization.normalizeDistanceBounds(-50, 200),
				"range clamping"
		);
	}

	private static void testAnimationThrottleSeverityThreshold() {
		assertEquals(false, ApcQualityPlan.shouldThrottleAnimations(0, 0), "empty plan baseline");
		assertEquals(false, ApcQualityPlan.shouldThrottleAnimations(1, 0), "one-level plan baseline");
		assertEquals(false, ApcQualityPlan.shouldThrottleAnimations(2, 0), "two-level plan baseline");
		assertEquals(true, ApcQualityPlan.shouldThrottleAnimations(1, 1), "one-level severe pressure");
		assertEquals(true, ApcQualityPlan.shouldThrottleAnimations(2, 1), "two-level severe pressure");
		assertEquals(false, ApcQualityPlan.shouldThrottleAnimations(8, 5), "large plan moderate pressure");
		assertEquals(true, ApcQualityPlan.shouldThrottleAnimations(8, 6), "large plan severe pressure");
	}

	private static void testImmutableSnapshotRebase() {
		AdaptivePerformanceController.OptionSnapshot original = snapshot(16, ParticleStatus.ALL, 128);
		AdaptivePerformanceController.OptionSnapshot expected = snapshot(8, ParticleStatus.MINIMAL, 32);
		AdaptivePerformanceController.OptionSnapshot external = snapshot(8, ParticleStatus.DECREASED, 48);
		AdaptivePerformanceController.OptionSnapshot rebased = original.rebase(external, expected);
		assertEquals(16, rebased.renderDistance(), "APC-owned render value keeps original");
		assertEquals(ParticleStatus.DECREASED, rebased.particles(), "external particle edit rebased");
		assertEquals(48, rebased.cloudRange(), "external cloud-range edit rebased");
		assertEquals(original.weatherRadius(), rebased.weatherRadius(), "unchanged field keeps original");
		assertEquals(16, original.renderDistance(), "record remains immutable");
	}

	private static void testRecoveryOwnedOptionHandoff() {
		AdaptivePerformanceController.OptionSnapshot original =
				snapshot(16, ParticleStatus.ALL, 128);
		AdaptivePerformanceController.OptionSnapshot apcApplied =
				snapshot(12, ParticleStatus.MINIMAL, 128);
		ApcOptionOwnership ownership = new ApcOptionOwnership(
				original,
				apcApplied,
				GraphicsPreset.FANCY,
				GraphicsPreset.FANCY
		);

		ownership = ownership.prepareForOwnedMutation(
				apcApplied,
				GraphicsPreset.FANCY
		);
		ownership = ownership.alignAfterOwnedMutation(
				snapshot(8, ParticleStatus.MINIMAL, 128),
				GraphicsPreset.CUSTOM
		);
		ownership = ownership.prepareForOwnedMutation(
				snapshot(8, ParticleStatus.MINIMAL, 128),
				GraphicsPreset.CUSTOM
		);
		ownership = ownership.alignAfterOwnedMutation(
				apcApplied,
				GraphicsPreset.FANCY
		);
		assertEquals(16, ownership.original().renderDistance(),
				"Recovery 8 to restored APC 12 must preserve APC original 16");
		assertEquals(12, ownership.lastApplied().renderDistance(),
				"Recovery restoration must realign APC expected render distance to 12");
		assertEquals(GraphicsPreset.FANCY, ownership.originalGraphicsPreset(),
				"Recovery preset round trip must preserve APC's original preset");

		ApcOptionOwnership mixed = new ApcOptionOwnership(
				original,
				apcApplied,
				GraphicsPreset.FANCY,
				GraphicsPreset.FANCY
		);
		mixed = mixed.prepareForOwnedMutation(
				snapshot(12, ParticleStatus.MINIMAL, 48),
				GraphicsPreset.FABULOUS
		);
		mixed = mixed.alignAfterOwnedMutation(
				snapshot(8, ParticleStatus.MINIMAL, 48),
				GraphicsPreset.CUSTOM
		);
		mixed = mixed.prepareForOwnedMutation(
				snapshot(6, ParticleStatus.MINIMAL, 48),
				GraphicsPreset.CUSTOM
		);
		mixed = mixed.alignAfterOwnedMutation(
				snapshot(6, ParticleStatus.MINIMAL, 48),
				GraphicsPreset.CUSTOM
		);
		assertEquals(6, mixed.original().renderDistance(),
				"user change from Recovery 8 to 6 must become APC's restorable original");
		assertEquals(ParticleStatus.ALL, mixed.original().particles(),
				"APC original particle quality must survive Recovery ownership");
		assertEquals(48, mixed.original().cloudRange(),
				"external non-Recovery field must remain rebased in APC original");
		assertEquals(6, mixed.lastApplied().renderDistance(),
				"Recovery must preserve and align the user-owned render value");
		assertEquals(ParticleStatus.MINIMAL, mixed.lastApplied().particles(),
				"Recovery-restored APC value must become APC's expected particle value");
		assertEquals(GraphicsPreset.FABULOUS, mixed.originalGraphicsPreset(),
				"pre-Recovery external preset must remain APC's restorable original");
		assertEquals(GraphicsPreset.CUSTOM, mixed.lastAppliedGraphicsPreset(),
				"conservatively preserved Recovery metadata must become APC's expected preset");

		ApcOptionOwnership explicitPreset = ownership.alignAfterOwnedMutation(
				snapshot(8, ParticleStatus.MINIMAL, 128),
				GraphicsPreset.CUSTOM
		);
		explicitPreset = explicitPreset.prepareForOwnedMutation(
				snapshot(8, ParticleStatus.MINIMAL, 128),
				GraphicsPreset.FAST
		);
		explicitPreset = explicitPreset.alignAfterOwnedMutation(
				snapshot(8, ParticleStatus.MINIMAL, 128),
				GraphicsPreset.FAST
		);
		assertEquals(GraphicsPreset.FAST, explicitPreset.originalGraphicsPreset(),
				"an explicit preset edit during Recovery must become APC's original preset");
		assertEquals(GraphicsPreset.FAST, explicitPreset.lastAppliedGraphicsPreset(),
				"an explicitly preserved preset must become APC's expected preset");
	}

	private static AdaptivePerformanceController.OptionSnapshot snapshot(
			int renderDistance,
			ParticleStatus particles,
			int cloudRange
	) {
		return new AdaptivePerformanceController.OptionSnapshot(
				renderDistance,
				1.0D,
				particles,
				CloudStatus.FANCY,
				cloudRange,
				10,
				true,
				true,
				true,
				true,
				1.0D,
				PrioritizeChunkUpdates.PLAYER_AFFECTED,
				true,
				2
		);
	}

	private static void assertStages(
			ApcQualityPlan.Stages actual,
			int particles,
			int entities,
			int renderStage,
			int renderDistance,
			int visuals
	) {
		assertEquals(particles, actual.particleStage(), "particle stage");
		assertEquals(entities, actual.entityStage(), "entity stage");
		assertEquals(renderStage, actual.renderStage(), "render stage");
		assertEquals(renderDistance, actual.renderDistance(), "render distance");
		assertEquals(visuals, actual.visualStage(), "visual stage");
	}

	private static void assertNear(double expected, double actual, double tolerance, String label) {
		if (Math.abs(expected - actual) > tolerance) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}

	private static void assertEquals(Object expected, Object actual, String label) {
		if (!expected.equals(actual)) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}
}
