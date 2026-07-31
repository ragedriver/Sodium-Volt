package com.ragedriver.sodiumvolt.client.resourcepack;

import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Set;

public record ResourcePackShieldReport(
		ShieldReason reason,
		ShieldSourceKind source,
		boolean rejected,
		int violations,
		int packsScanned,
		int resourcesSeen,
		long declaredBytes,
		long liveReadBytes,
		int monitorFailures
) {
	public static final int VERSION = 1;
	public static final int MAXIMUM_SIZE_BYTES = 8 * 1024;
	public static final String CLASSIFICATION = "resource_pack_policy_event";
	public static final Set<String> KEYS = Set.of(
			"version",
			"classification",
			"reason",
			"source_kind",
			"action",
			"violations",
			"packs_scanned",
			"resources_seen",
			"declared_bytes",
			"live_read_bytes",
			"monitor_failures"
	);
	private static final int MAXIMUM_COUNTER = 1_000_000_000;
	private static final long MAXIMUM_BYTES = 8L * 1024L * 1024L * 1024L;

	public ResourcePackShieldReport {
		reason = reason == null || reason == ShieldReason.NONE
				? ShieldReason.MONITOR_FAILURE
				: reason;
		source = source == null || source == ShieldSourceKind.IGNORED
				? ShieldSourceKind.LOCAL
				: source;
		violations = clamp(violations);
		packsScanned = clamp(packsScanned);
		resourcesSeen = clamp(resourcesSeen);
		declaredBytes = Math.max(0L, Math.min(MAXIMUM_BYTES, declaredBytes));
		liveReadBytes = Math.max(0L, Math.min(MAXIMUM_BYTES, liveReadBytes));
		monitorFailures = clamp(monitorFailures);
	}

	public JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		root.addProperty("classification", CLASSIFICATION);
		root.addProperty("reason", fixedName(this.reason));
		root.addProperty("source_kind", fixedName(this.source));
		root.addProperty("action", this.rejected ? "rejected" : "monitored");
		root.addProperty("violations", this.violations);
		root.addProperty("packs_scanned", this.packsScanned);
		root.addProperty("resources_seen", this.resourcesSeen);
		root.addProperty("declared_bytes", this.declaredBytes);
		root.addProperty("live_read_bytes", this.liveReadBytes);
		root.addProperty("monitor_failures", this.monitorFailures);
		return root;
	}

	private static String fixedName(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(MAXIMUM_COUNTER, value));
	}
}
