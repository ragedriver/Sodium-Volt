package com.ragedriver.sodiumvolt.client.performance;

public final class VramPressureLogicTest {
	private VramPressureLogicTest() {
	}

	public static void main(String[] arguments) {
		testTextureAndHeadroomByteMath();
		testLedgerReleaseAndSaturation();
		testAutomaticBudgetHeuristic();
		testSustainedPressureHysteresisAndRecovery();
		testRenderDistanceOwnership();
		testConfigurationNormalization();
		System.out.println("VRAM Pressure Protection logic tests passed");
	}

	private static void testTextureAndHeadroomByteMath() {
		assertEquals(84L, VramByteMath.textureBytes(4, 4, 1, 3, 4),
				"mip chain byte estimate");
		assertEquals(504L, VramByteMath.textureBytes(4, 4, 6, 3, 4),
				"layered mip chain byte estimate");
		assertEquals(-1L, VramByteMath.textureBytes(4, 4, 1, 33, 4),
				"mip iteration hard bound");
		assertEquals(
				Long.MAX_VALUE,
				VramByteMath.textureBytes(
						Integer.MAX_VALUE,
						Integer.MAX_VALUE,
						Integer.MAX_VALUE,
						32,
						Integer.MAX_VALUE
				),
				"texture size overflow saturation"
		);
		assertEquals(4_294_967_296L, VramByteMath.mibToBytes(4096),
				"MiB conversion");
		assertEquals(
				1_330L,
				VramByteMath.addHeadroom(1_000L, 20, 130L),
				"percentage and fixed headroom"
		);
		assertEquals(Long.MAX_VALUE, VramByteMath.saturatingAdd(Long.MAX_VALUE, 1L),
				"saturating addition");
	}

	private static void testLedgerReleaseAndSaturation() {
		VramAccountingLedger ledger = new VramAccountingLedger();
		ledger.setSpikeThresholdBytes(100L);
		VramAccountingLedger.Allocation texture = ledger.allocate(100L, true, true);
		VramAccountingLedger.Allocation buffer = ledger.allocate(50L, false, false);
		VramAccountingLedger.Snapshot allocated = ledger.snapshot();
		assertEquals(150L, allocated.totalBytes(), "combined allocation estimate");
		assertEquals(100L, allocated.renderAttachmentBytes(), "render-target subset");
		assertEquals(1L, allocated.textureCount(), "texture count");
		assertEquals(1L, allocated.bufferCount(), "buffer count");
		assertEquals(150L, allocated.peakBytes(), "peak estimate");
		assertEquals(1L, allocated.spikeCount(), "large-allocation spike count");
		check(ledger.hasSpikeSignal() && ledger.consumeSpikeSignal()
						&& !ledger.hasSpikeSignal(),
				"spike signal must be observable and consumable");

		texture.release();
		texture.release();
		assertEquals(50L, ledger.snapshot().totalBytes(),
				"allocation token release must be idempotent");
		buffer.release();
		buffer.release();
		assertEquals(0L, ledger.snapshot().totalBytes(),
				"double release must never make totals negative");
		ledger.release(500L, false, false);
		assertEquals(0L, ledger.snapshot().bufferCount(),
				"unmatched release count must stay nonnegative");

		VramAccountingLedger saturated = new VramAccountingLedger();
		saturated.add(Long.MAX_VALUE, true, false);
		saturated.add(10L, false, false);
		assertEquals(Long.MAX_VALUE, saturated.snapshot().totalBytes(),
				"ledger totals must saturate");
		assertEquals(Long.MAX_VALUE, saturated.snapshot().peakBytes(),
				"ledger peak must saturate");
	}

	private static void testAutomaticBudgetHeuristic() {
		assertEquals(4096, VramAutoBudgetHeuristic.estimateMib(
				-1L, "unknown", "unknown", false, false
		), "unknown-memory fallback");
		assertEquals(8192, VramAutoBudgetHeuristic.estimateMib(
				65_536L, "Apple", "Metal", true, false
		), "unified-memory fraction and cap");
		assertEquals(6144, VramAutoBudgetHeuristic.estimateMib(
				65_536L, "NVIDIA", "OpenGL", false, true
		), "unknown discrete capacity conservative cap");
		assertEquals(2048, VramAutoBudgetHeuristic.estimateMib(
				16_384L, "AMD", "Vulkan", false, true
		), "unknown discrete capacity conservative fraction");
		assertEquals(512, VramAutoBudgetHeuristic.estimateMib(
				2_048L, "unknown", "unknown", false, false
		), "automatic budget hard minimum");
	}

	private static void testSustainedPressureHysteresisAndRecovery() {
		VramPressureStateMachine state = new VramPressureStateMachine();
		assertAction(VramPressureStateMachine.Action.HOLD,
				sample(state, 850L, 1L), "first protection confirmation");
		assertAction(VramPressureStateMachine.Action.HOLD,
				sample(state, 850L, 2L), "second protection confirmation");
		assertAction(VramPressureStateMachine.Action.ENTER_PROTECTION,
				sample(state, 850L, 3L), "sustained protection entry");
		assertAction(VramPressureStateMachine.Action.HOLD,
				sample(state, 950L, 4L), "first critical confirmation");
		assertAction(VramPressureStateMachine.Action.HOLD,
				sample(state, 950L, 5L), "second critical confirmation");
		assertAction(VramPressureStateMachine.Action.ENTER_CRITICAL,
				sample(state, 950L, 6L), "sustained critical entry");
		assertAction(VramPressureStateMachine.Action.HOLD,
				sample(state, 850L, 7L), "first critical de-escalation confirmation");
		assertAction(VramPressureStateMachine.Action.HOLD,
				sample(state, 850L, 8L), "second critical de-escalation confirmation");
		assertAction(VramPressureStateMachine.Action.DEESCALATE,
				sample(state, 850L, 9L), "critical hysteresis de-escalation");
		assertAction(VramPressureStateMachine.Action.HOLD,
				sample(state, 650L, 10L), "recovery timer start");
		assertAction(VramPressureStateMachine.Action.HOLD,
				sample(state, 650L, 29_000_000_010L), "recovery boundary before delay");
		assertAction(VramPressureStateMachine.Action.RECOVER_NORMAL,
				sample(state, 650L, 30_000_000_010L), "recovery after full delay");
		assertEquals(VramPressureStateMachine.Level.NORMAL, state.level(),
				"normal state after recovery");
		assertEquals(920L, VramPressureStateMachine.thresholdBytes(1_000L, 92),
				"overflow-safe threshold math");
		assertAction(VramPressureStateMachine.Action.UNKNOWN,
				state.sample(-1L, 1_000L, 80, 92, 3, 30L, 1L),
				"invalid metric fail-open decision");
	}

	private static VramPressureStateMachine.Action sample(
			VramPressureStateMachine state,
			long bytes,
			long nowNanos
	) {
		return state.sample(bytes, 1_000L, 80, 92, 3, 30_000_000_000L, nowNanos);
	}

	private static void testRenderDistanceOwnership() {
		VramRenderDistanceCap cap = new VramRenderDistanceCap();
		assertEquals(11, cap.lower(12, 6, 1), "gradual cap reduction");
		check(cap.isActive() && cap.ownsCurrent(11), "controller must own its applied value");
		assertEquals(10, cap.lower(11, 6, 1), "second gradual cap reduction");
		assertEquals(11, cap.recover(10), "owned value may recover one step");
		assertEquals(8, cap.recover(8), "external lower value must never be raised");

		VramRenderDistanceCap restore = new VramRenderDistanceCap();
		assertEquals(10, restore.lower(12, 6, 2), "critical two-chunk reduction");
		assertEquals(12, restore.disable(10, true, true),
				"owned value may be restored on disable");
		VramRenderDistanceCap apc = new VramRenderDistanceCap();
		assertEquals(10, apc.lower(12, 6, 2), "APC composition setup");
		assertEquals(10, apc.disable(10, true, false),
				"active APC must prevent a raise");
		check(!apc.isActive() && apc.currentCap() == Integer.MAX_VALUE,
				"disable must remove the hidden cap");
	}

	private static void testConfigurationNormalization() {
		assertEquals(
				new VramConfigNormalization.Thresholds(90, 95),
				VramConfigNormalization.normalizeThresholds(99, 75),
				"critical threshold must stay five points above protection"
		);
		assertEquals(
				new VramConfigNormalization.Thresholds(60, 98),
				VramConfigNormalization.normalizeThresholds(1, 999),
				"threshold range clamp"
		);
		assertEquals(768, VramConfigNormalization.clampStep(700, 512, 24_576, 256),
				"stepped slider normalization");
	}

	private static void assertAction(
			VramPressureStateMachine.Action expected,
			VramPressureStateMachine.Action actual,
			String label
	) {
		assertEquals(expected, actual, label);
	}

	private static void assertEquals(Object expected, Object actual, String label) {
		if (!expected.equals(actual)) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
