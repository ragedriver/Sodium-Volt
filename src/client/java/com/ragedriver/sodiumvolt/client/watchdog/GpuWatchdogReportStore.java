package com.ragedriver.sodiumvolt.client.watchdog;

import com.google.gson.JsonObject;
import com.ragedriver.sodiumvolt.SodiumVolt;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

final class GpuWatchdogReportStore {
	private static final AtomicBoolean READ_FAILURE_LOGGED = new AtomicBoolean();
	private static final AtomicBoolean WRITE_FAILURE_LOGGED = new AtomicBoolean();

	private GpuWatchdogReportStore() {
	}

	static boolean write(GpuWatchdogIncidentReport report) {
		return write(reportPath(), report);
	}

	static boolean write(Path path, GpuWatchdogIncidentReport report) {
		try {
			WatchdogJson.writeObject(
					path,
					report.toJson(),
					GpuWatchdogIncidentReport.MAXIMUM_SIZE_BYTES,
					"sodium-volt-watchdog-report-"
			);
			return true;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			if (WRITE_FAILURE_LOGGED.compareAndSet(false, true)) {
				SodiumVolt.LOGGER.warn("Could not write sanitized GPU Watchdog incident report");
			}
			return false;
		}
	}

	static GpuWatchdogIncidentReport read(Path path) {
		try {
			JsonObject root = WatchdogJson.readObject(
					path,
					GpuWatchdogIncidentReport.MAXIMUM_SIZE_BYTES
			);
			if (root == null) {
				return null;
			}
			WatchdogJson.requireExactKeys(root, GpuWatchdogIncidentReport.KEYS);
			if (WatchdogJson.requiredInteger(root, "version")
							!= GpuWatchdogIncidentReport.VERSION
					|| !GpuWatchdogIncidentReport.CLASSIFICATION.equals(
							WatchdogJson.requiredString(root, "classification", 48)
					)) {
				throw new IllegalArgumentException("Invalid watchdog report");
			}
			int observed = WatchdogJson.requiredInteger(root, "observed_millis");
			int warning = WatchdogJson.requiredInteger(root, "warning_threshold_millis");
			int critical = WatchdogJson.requiredInteger(root, "critical_threshold_millis");
			int confirmations = WatchdogJson.requiredInteger(root, "confirmation_count");
			int incidents = WatchdogJson.requiredInteger(root, "incident_count");
			boolean staged = WatchdogJson.requiredBoolean(root, "recovery_request_staged");
			if (observed < 0 || observed > GpuWatchdogPolicy.MAXIMUM_REPORTED_MILLIS
					|| warning < 1_000 || warning > 15_000
					|| critical < warning + 1_000 || critical > 30_000
					|| confirmations < 1 || confirmations > 5
					|| incidents < 1 || incidents > 10) {
				throw new IllegalArgumentException("Out-of-range watchdog report");
			}
			return new GpuWatchdogIncidentReport(
					observed, warning, critical, confirmations, incidents, staged
			);
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			if (READ_FAILURE_LOGGED.compareAndSet(false, true)) {
				SodiumVolt.LOGGER.warn("Ignoring invalid GPU Watchdog incident report");
			}
			return null;
		}
	}

	private static Path reportPath() {
		return FabricLoader.getInstance().getConfigDir()
				.resolve("sodium-volt-gpu-watchdog-report.json");
	}
}
