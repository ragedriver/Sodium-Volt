package com.ragedriver.sodiumvolt.client.watchdog;

import com.google.gson.JsonObject;
import com.ragedriver.sodiumvolt.SodiumVolt;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WatchdogRecoveryRequestStore {
	static final int MAXIMUM_SIZE_BYTES = 16 * 1024;
	private static final int VERSION = 1;
	private static final String CLASSIFICATION = "possible_gpu_render_stall";
	private static final Set<String> KEYS = Set.of(
			"version",
			"pending",
			"classification",
			"longest_stall_millis",
			"incident_count"
	);
	private static final AtomicBoolean READ_FAILURE_LOGGED = new AtomicBoolean();
	private static final AtomicBoolean WRITE_FAILURE_LOGGED = new AtomicBoolean();
	private static final AtomicBoolean DELETE_FAILURE_LOGGED = new AtomicBoolean();

	private WatchdogRecoveryRequestStore() {
	}

	public static Request load() {
		return load(requestPath());
	}

	static Request load(Path path) {
		try {
			JsonObject root = WatchdogJson.readObject(path, MAXIMUM_SIZE_BYTES);
			if (root == null) {
				return Request.EMPTY;
			}
			WatchdogJson.requireExactKeys(root, KEYS);
			if (WatchdogJson.requiredInteger(root, "version") != VERSION
					|| !WatchdogJson.requiredBoolean(root, "pending")
					|| !CLASSIFICATION.equals(
							WatchdogJson.requiredString(root, "classification", 48)
					)) {
				throw new IllegalArgumentException("Invalid watchdog recovery request");
			}
			int duration = WatchdogJson.requiredInteger(root, "longest_stall_millis");
			int incidents = WatchdogJson.requiredInteger(root, "incident_count");
			if (duration < 0 || duration > GpuWatchdogPolicy.MAXIMUM_REPORTED_MILLIS
					|| incidents < 1 || incidents > 10) {
				throw new IllegalArgumentException("Out-of-range watchdog recovery request");
			}
			return new Request(true, duration, incidents);
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			if (READ_FAILURE_LOGGED.compareAndSet(false, true)) {
				SodiumVolt.LOGGER.warn("Ignoring an invalid GPU Watchdog recovery request");
			}
			return Request.EMPTY;
		}
	}

	public static boolean stage(int longestStallMillis, int incidentCount) {
		return stage(requestPath(), longestStallMillis, incidentCount);
	}

	static boolean stage(Path path, int longestStallMillis, int incidentCount) {
		Request existing = load(path);
		int duration = Math.max(
				existing.longestStallMillis(),
				Math.max(0, Math.min(
						GpuWatchdogPolicy.MAXIMUM_REPORTED_MILLIS,
						longestStallMillis
				))
		);
		int incidents = Math.max(existing.incidentCount(), Math.max(1, Math.min(10, incidentCount)));
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		root.addProperty("pending", true);
		root.addProperty("classification", CLASSIFICATION);
		root.addProperty("longest_stall_millis", duration);
		root.addProperty("incident_count", incidents);
		try {
			WatchdogJson.writeObject(
					path,
					root,
					MAXIMUM_SIZE_BYTES,
					"sodium-volt-watchdog-request-"
			);
			return true;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			if (WRITE_FAILURE_LOGGED.compareAndSet(false, true)) {
				SodiumVolt.LOGGER.warn("Could not stage GPU Watchdog recovery request");
			}
			return false;
		}
	}

	public static boolean acknowledge() {
		return acknowledge(requestPath());
	}

	static boolean acknowledge(Path path) {
		try {
			return WatchdogJson.deleteRegularFile(path);
		} catch (IOException | RuntimeException exception) {
			if (DELETE_FAILURE_LOGGED.compareAndSet(false, true)) {
				SodiumVolt.LOGGER.warn("Could not acknowledge GPU Watchdog recovery request");
			}
			return false;
		}
	}

	private static Path requestPath() {
		return FabricLoader.getInstance().getConfigDir()
				.resolve("sodium-volt-gpu-watchdog-request.json");
	}

	public record Request(boolean pending, int longestStallMillis, int incidentCount) {
		public static final Request EMPTY = new Request(false, 0, 0);
	}
}
