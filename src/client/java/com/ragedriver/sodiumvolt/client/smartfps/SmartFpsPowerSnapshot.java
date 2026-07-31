package com.ragedriver.sodiumvolt.client.smartfps;

public record SmartFpsPowerSnapshot(PowerState state, int percentage) {
	public static final SmartFpsPowerSnapshot UNKNOWN =
			new SmartFpsPowerSnapshot(PowerState.UNKNOWN, -1);

	public SmartFpsPowerSnapshot {
		if (state == null || percentage < 0 || percentage > 100) {
			state = PowerState.UNKNOWN;
			percentage = -1;
		}
	}

	public static SmartFpsPowerSnapshot charging(int percentage) {
		return new SmartFpsPowerSnapshot(PowerState.CHARGING, percentage);
	}

	public static SmartFpsPowerSnapshot discharging(int percentage) {
		return new SmartFpsPowerSnapshot(PowerState.DISCHARGING, percentage);
	}

	public boolean isKnown() {
		return this.state != PowerState.UNKNOWN && this.percentage >= 0;
	}

	public enum PowerState {
		UNKNOWN,
		CHARGING,
		DISCHARGING
	}
}
