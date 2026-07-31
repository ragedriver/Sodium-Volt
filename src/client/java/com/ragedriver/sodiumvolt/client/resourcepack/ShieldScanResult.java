package com.ragedriver.sodiumvolt.client.resourcepack;

public record ShieldScanResult(
		ShieldReason reason,
		int entries,
		long declaredBytes,
		long inspectedBytes
) {
	public static final ShieldScanResult EMPTY =
			new ShieldScanResult(ShieldReason.NONE, 0, 0L, 0L);

	public ShieldScanResult {
		reason = reason == null ? ShieldReason.MONITOR_FAILURE : reason;
		entries = Math.max(0, entries);
		declaredBytes = Math.max(0L, declaredBytes);
		inspectedBytes = Math.max(0L, inspectedBytes);
	}

	public boolean accepted() {
		return this.reason == ShieldReason.NONE;
	}
}
