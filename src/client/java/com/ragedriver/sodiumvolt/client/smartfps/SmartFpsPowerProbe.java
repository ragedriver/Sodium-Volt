package com.ragedriver.sodiumvolt.client.smartfps;

import oshi.SystemInfo;
import oshi.hardware.PowerSource;

import java.util.List;

public final class SmartFpsPowerProbe {
	private SmartFpsPowerProbe() {
	}

	public static SmartFpsPowerSnapshot query() {
		List<PowerSource> sources = new SystemInfo().getHardware().getPowerSources();
		if (sources == null || sources.isEmpty()) {
			return SmartFpsPowerSnapshot.UNKNOWN;
		}
		SmartFpsPowerAggregation aggregation = new SmartFpsPowerAggregation();
		for (PowerSource source : sources) {
			if (source == null) {
				continue;
			}
			aggregation.accept(
					source.getRemainingCapacityPercent(),
					source.isCharging(),
					source.isPowerOnLine(),
					source.isDischarging()
			);
		}
		return aggregation.snapshot();
	}

	@SuppressWarnings("removal")
	static boolean mustRethrow(Error error) {
		return error instanceof VirtualMachineError || error instanceof ThreadDeath;
	}
}
