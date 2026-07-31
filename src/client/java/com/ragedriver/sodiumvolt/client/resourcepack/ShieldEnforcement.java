package com.ragedriver.sodiumvolt.client.resourcepack;

public final class ShieldEnforcement {
	private ShieldEnforcement() {
	}

	public static boolean shouldReject(
			ResourcePackShieldPolicy policy,
			ShieldReason reason
	) {
		return policy != null
				&& policy.rejectViolations()
				&& reason != null
				&& reason != ShieldReason.NONE
				&& reason != ShieldReason.MONITOR_FAILURE;
	}
}
