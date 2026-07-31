package com.ragedriver.sodiumvolt.client.watchdog;

import com.google.gson.JsonObject;

import java.util.Set;

record GpuWatchdogIncidentReport(
		int observedMillis,
		int warningThresholdMillis,
		int criticalThresholdMillis,
		int confirmationCount,
		int incidentCount,
		boolean recoveryRequestStaged
) {
	static final int MAXIMUM_SIZE_BYTES = 16 * 1024;
	static final int VERSION = 1;
	static final String CLASSIFICATION = "possible_gpu_render_stall";
	static final Set<String> KEYS = Set.of(
			"version",
			"classification",
			"observed_millis",
			"warning_threshold_millis",
			"critical_threshold_millis",
			"confirmation_count",
			"incident_count",
			"recovery_request_staged"
	);

	GpuWatchdogIncidentReport {
		observedMillis = clamp(observedMillis, 0, GpuWatchdogPolicy.MAXIMUM_REPORTED_MILLIS);
		warningThresholdMillis = clamp(warningThresholdMillis, 1_000, 15_000);
		criticalThresholdMillis = Math.max(
				warningThresholdMillis + 1_000,
				clamp(criticalThresholdMillis, 2_000, 30_000)
		);
		confirmationCount = clamp(confirmationCount, 1, 5);
		incidentCount = clamp(incidentCount, 1, 10);
	}

	JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		root.addProperty("classification", CLASSIFICATION);
		root.addProperty("observed_millis", this.observedMillis);
		root.addProperty("warning_threshold_millis", this.warningThresholdMillis);
		root.addProperty("critical_threshold_millis", this.criticalThresholdMillis);
		root.addProperty("confirmation_count", this.confirmationCount);
		root.addProperty("incident_count", this.incidentCount);
		root.addProperty("recovery_request_staged", this.recoveryRequestStaged);
		return root;
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
