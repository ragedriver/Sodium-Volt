package com.ragedriver.sodiumvolt.client.performance;

/**
 * The user-facing, one-shot preset for Sodium Volt's performance features.
 *
 * <p>This is intentionally separate from the Adaptive Performance Controller's
 * {@code Profile}. The latter controls APC's runtime quality strategy, while a
 * tuner profile configures the feature set as a whole when the user selects it.</p>
 */
public enum VoltTunerProfile {
	VOLT_DEFAULT,
	COMPETITIVE,
	AGGRESSIVE
}
