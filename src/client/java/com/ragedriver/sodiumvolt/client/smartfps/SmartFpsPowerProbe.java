package com.ragedriver.sodiumvolt.client.smartfps;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.PowerSource;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class SmartFpsPowerProbe {
	private static final RetryableLazy<OshiState> OSHI_STATE = new RetryableLazy<>(
			SmartFpsPowerProbe::createOshiState
	);

	private SmartFpsPowerProbe() {
	}

	public static SmartFpsPowerSnapshot query() {
		List<PowerSource> sources = OSHI_STATE.get().hardware().getPowerSources();
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

	private static OshiState createOshiState() {
		SystemInfo systemInfo = new SystemInfo();
		return new OshiState(systemInfo, systemInfo.getHardware());
	}

	@SuppressWarnings("removal")
	static boolean mustRethrow(Error error) {
		return error instanceof VirtualMachineError || error instanceof ThreadDeath;
	}

	private record OshiState(
			SystemInfo systemInfo,
			HardwareAbstractionLayer hardware
	) {
		private OshiState {
			Objects.requireNonNull(systemInfo, "systemInfo");
			Objects.requireNonNull(hardware, "hardware");
		}
	}

	static final class RetryableLazy<T> {
		private final Supplier<? extends T> factory;
		private volatile T value;

		RetryableLazy(Supplier<? extends T> factory) {
			this.factory = Objects.requireNonNull(factory, "factory");
		}

		T get() {
			T current = value;
			if (current != null) {
				return current;
			}
			synchronized (this) {
				current = value;
				if (current == null) {
					current = Objects.requireNonNull(factory.get(), "factory result");
					value = current;
				}
			}
			return current;
		}
	}
}
