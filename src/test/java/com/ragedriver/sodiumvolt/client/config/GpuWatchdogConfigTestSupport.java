package com.ragedriver.sodiumvolt.client.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class GpuWatchdogConfigTestSupport {
	private GpuWatchdogConfigTestSupport() {
	}

	public static void run() throws Exception {
		Path directory = Files.createTempDirectory("sodium-volt-watchdog-config-test-");
		Path path = directory.resolve("watchdog.json");
		Path dangling = directory.resolve("dangling-watchdog.json");
		try {
			GpuWatchdogConfig defaults = GpuWatchdogConfig.load(path);
			check(!defaults.isGpuTimeoutWatchdogEnabled(), "watchdog default must remain off");
			check(!Files.exists(path), "reading defaults must not create a config file");

			defaults.setGpuTimeoutWatchdogEnabled(true);
			Files.writeString(path, defaults.toJson().toString(), StandardCharsets.UTF_8);
			GpuWatchdogConfig loaded = GpuWatchdogConfig.load(path);
			check(loaded.isGpuTimeoutWatchdogEnabled()
							&& loaded.getCriticalTimeoutThresholdSeconds()
									> loaded.getWarningStallThresholdSeconds(),
					"strict config round-trip");

			String valid = defaults.toJson().toString();
			Files.writeString(
					path,
					valid.replaceFirst(
							"\"version\":1",
							"\"version\":1,\"unknown\":false"
					),
					StandardCharsets.UTF_8
			);
			check(!GpuWatchdogConfig.load(path).isGpuTimeoutWatchdogEnabled(),
					"unknown config keys must fail open");

			Files.writeString(
					path,
					valid.replaceFirst(
							"\"version\":1",
							"\"version\":1,\"version\":1"
					),
					StandardCharsets.UTF_8
			);
			check(!GpuWatchdogConfig.load(path).isGpuTimeoutWatchdogEnabled(),
					"duplicate config keys must fail open");

			Files.writeString(
					path,
					valid.replace(
							"\"warning_stall_threshold_seconds\":3",
							"\"warning_stall_threshold_seconds\":999"
					),
					StandardCharsets.UTF_8
			);
			check(!GpuWatchdogConfig.load(path).isGpuTimeoutWatchdogEnabled(),
					"out-of-range config values must be rejected instead of clamped");

			Files.writeString(path, "{\"version\":\"one\"}", StandardCharsets.UTF_8);
			check(!GpuWatchdogConfig.load(path).isGpuTimeoutWatchdogEnabled(),
					"wrongly typed config must fail open");

			Files.write(path, new byte[GpuWatchdogConfig.MAXIMUM_CONFIG_BYTES + 1]);
			check(!GpuWatchdogConfig.load(path).isGpuTimeoutWatchdogEnabled(),
					"oversized config must fail open");

			if (createDanglingSymlink(dangling)) {
				check(Files.isSymbolicLink(dangling) && !Files.exists(dangling),
						"test config link must be dangling");
				check(!GpuWatchdogConfig.load(dangling).isGpuTimeoutWatchdogEnabled()
								&& Files.isSymbolicLink(dangling),
						"dangling config symlink must fail open and remain untouched");
			}
		} finally {
			Files.deleteIfExists(dangling);
			Files.deleteIfExists(path);
			Files.deleteIfExists(directory);
		}
	}

	private static boolean createDanglingSymlink(Path link) throws Exception {
		try {
			Files.createSymbolicLink(link, Path.of("missing-watchdog-config.json"));
			return true;
		} catch (UnsupportedOperationException exception) {
			System.out.println("Skipping dangling config-symlink assertion: unsupported");
			return false;
		} catch (FileSystemException exception) {
			String reason = String.valueOf(exception.getReason()).toLowerCase(Locale.ROOT);
			if (reason.contains("not supported")
					|| reason.contains("privilege")
					|| reason.contains("symbolic links are not supported")) {
				System.out.println(
						"Skipping dangling config-symlink assertion: platform cannot create symlinks"
				);
				return false;
			}
			throw exception;
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
