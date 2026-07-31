package com.ragedriver.sodiumvolt.client.resourcepack;

import com.google.gson.JsonObject;
import com.ragedriver.sodiumvolt.SodiumVolt;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ResourcePackShieldReportStore {
	private static final AtomicBoolean READ_FAILURE_LOGGED = new AtomicBoolean();
	private static final AtomicBoolean WRITE_FAILURE_LOGGED = new AtomicBoolean();

	private ResourcePackShieldReportStore() {
	}

	public static boolean write(ResourcePackShieldReport report) {
		return write(reportPath(), report);
	}

	public static boolean write(Path path, ResourcePackShieldReport report) {
		try {
			ShieldJsonFile.writeObject(
					path,
					report.toJson(),
					ResourcePackShieldReport.MAXIMUM_SIZE_BYTES,
					"sodium-volt-resource-pack-shield-report-"
			);
			return true;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			if (WRITE_FAILURE_LOGGED.compareAndSet(false, true)) {
				SodiumVolt.LOGGER.warn("Could not write sanitized Resource-Pack Shield report");
			}
			return false;
		}
	}

	public static ResourcePackShieldReport read(Path path) {
		try {
			JsonObject root = ShieldJsonFile.readObject(
					path, ResourcePackShieldReport.MAXIMUM_SIZE_BYTES
			);
			if (root == null) {
				return null;
			}
			ShieldJsonFile.requireExactKeys(root, ResourcePackShieldReport.KEYS);
			if (ShieldJsonFile.requiredInteger(root, "version")
							!= ResourcePackShieldReport.VERSION
					|| !ResourcePackShieldReport.CLASSIFICATION.equals(
							ShieldJsonFile.requiredString(root, "classification", 48)
					)) {
				throw new IllegalArgumentException("Invalid Resource-Pack Shield report");
			}
			ShieldReason reason = parseReason(
					ShieldJsonFile.requiredString(root, "reason", 48)
			);
			ShieldSourceKind source = parseSource(
					ShieldJsonFile.requiredString(root, "source_kind", 16)
			);
			String action = ShieldJsonFile.requiredString(root, "action", 16);
			boolean rejected;
			if ("rejected".equals(action)) {
				rejected = true;
			} else if ("monitored".equals(action)) {
				rejected = false;
			} else {
				throw new IllegalArgumentException("Invalid report action");
			}
			int violations = range(
					ShieldJsonFile.requiredInteger(root, "violations"), 0, 1_000_000_000
			);
			int packs = range(
					ShieldJsonFile.requiredInteger(root, "packs_scanned"), 0, 1_000_000_000
			);
			int resources = range(
					ShieldJsonFile.requiredInteger(root, "resources_seen"), 0, 1_000_000_000
			);
			long declared = range(
					ShieldJsonFile.requiredLong(root, "declared_bytes"),
					0L,
					8L * 1024L * 1024L * 1024L
			);
			long live = range(
					ShieldJsonFile.requiredLong(root, "live_read_bytes"),
					0L,
					8L * 1024L * 1024L * 1024L
			);
			int failures = range(
					ShieldJsonFile.requiredInteger(root, "monitor_failures"),
					0,
					1_000_000_000
			);
			return new ResourcePackShieldReport(
					reason,
					source,
					rejected,
					violations,
					packs,
					resources,
					declared,
					live,
					failures
			);
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			if (READ_FAILURE_LOGGED.compareAndSet(false, true)) {
				SodiumVolt.LOGGER.warn("Ignoring invalid Resource-Pack Shield report");
			}
			return null;
		}
	}

	private static ShieldReason parseReason(String value) {
		try {
			ShieldReason reason = ShieldReason.valueOf(value.toUpperCase(Locale.ROOT));
			if (reason == ShieldReason.NONE) {
				throw new IllegalArgumentException("Invalid report reason");
			}
			return reason;
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid report reason", exception);
		}
	}

	private static ShieldSourceKind parseSource(String value) {
		try {
			ShieldSourceKind source =
					ShieldSourceKind.valueOf(value.toUpperCase(Locale.ROOT));
			if (source == ShieldSourceKind.IGNORED) {
				throw new IllegalArgumentException("Invalid report source");
			}
			return source;
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid report source", exception);
		}
	}

	private static int range(int value, int minimum, int maximum) {
		if (value < minimum || value > maximum) {
			throw new IllegalArgumentException("Out-of-range report field");
		}
		return value;
	}

	private static long range(long value, long minimum, long maximum) {
		if (value < minimum || value > maximum) {
			throw new IllegalArgumentException("Out-of-range report field");
		}
		return value;
	}

	private static Path reportPath() {
		return FabricLoader.getInstance().getConfigDir()
				.resolve("sodium-volt-resource-pack-shield-report.json");
	}
}
