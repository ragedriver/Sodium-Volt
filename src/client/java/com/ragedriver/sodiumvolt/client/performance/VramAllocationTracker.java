package com.ragedriver.sodiumvolt.client.performance;

public final class VramAllocationTracker {
	private static final VramAccountingLedger LEDGER = new VramAccountingLedger();

	private VramAllocationTracker() {
	}

	public static void allocated(long bytes, boolean texture, boolean renderAttachment) {
		try {
			LEDGER.add(bytes, texture, renderAttachment);
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
