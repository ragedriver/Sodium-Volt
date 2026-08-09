package com.ragedriver.sodiumvolt.client.performance;

public final class VramAllocationTracker {
	private static final VramAccountingGate GATE = new VramAccountingGate();
	private static final VramAccountingLedger LEDGER = new VramAccountingLedger();

	private VramAllocationTracker() {
	}

	public static void initialize(boolean enabledAtStartup) {
		GATE.initialize(enabledAtStartup);
	}

	public static boolean shouldTrackAllocations() {
		return GATE.isTracking();
	}

	public static boolean requiresTrackingTransition(boolean enabled) {
		return GATE.requiresTransition(enabled);
	}

	public static void publishEnabled(boolean enabled) {
		boolean transitioned = GATE.publishEnabled(enabled);
		if (!enabled && transitioned) {
			LEDGER.clearSpikeSignal();
		}
	}

	public static boolean hasCompleteBaseline() {
		return GATE.hasCompleteBaseline();
	}

	public static void allocated(long bytes, boolean texture, boolean renderAttachment) {
		try {
			LEDGER.add(bytes, texture, renderAttachment);
			if (!GATE.isTracking()) {
				// Disable may race a constructor tail after its one admission read.
				// Keep the release bookkeeping, but never publish a dormant spike.
				LEDGER.clearSpikeSignal();
			}
		} catch (RuntimeException | LinkageError ignored) {
			// GPU resource creation must never fail because estimate accounting failed.
		}
	}

	public static void released(long bytes, boolean texture, boolean renderAttachment) {
		try {
			LEDGER.release(bytes, texture, renderAttachment);
		} catch (RuntimeException | LinkageError ignored) {
			// GPU destruction must never fail because estimate accounting failed.
		}
	}

	public static void setSpikeThresholdBytes(long bytes) {
		LEDGER.setSpikeThresholdBytes(bytes);
	}

	public static boolean consumeSpikeSignal() {
		return LEDGER.consumeSpikeSignal();
	}

	public static boolean hasSpikeSignal() {
		return LEDGER.hasSpikeSignal();
	}

	public static VramAccountingLedger.Snapshot snapshot() {
		return LEDGER.snapshot();
	}
}
