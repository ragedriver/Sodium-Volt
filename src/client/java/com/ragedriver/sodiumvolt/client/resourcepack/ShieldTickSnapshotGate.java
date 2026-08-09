package com.ragedriver.sodiumvolt.client.resourcepack;

final class ShieldTickSnapshotGate {
	private ShieldTickSnapshotGate() {
	}

	static boolean shouldCapture(
			long configuredRevision,
			boolean configuredEnabled,
			long observedRevision,
			boolean observedEnabled
	) {
		return configuredRevision != observedRevision
				|| configuredEnabled != observedEnabled;
	}
}
