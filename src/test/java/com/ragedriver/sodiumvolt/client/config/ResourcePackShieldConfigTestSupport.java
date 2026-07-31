package com.ragedriver.sodiumvolt.client.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class ResourcePackShieldConfigTestSupport {
	private ResourcePackShieldConfigTestSupport() {
	}

	public static void run() throws Exception {
		Path directory = Files.createTempDirectory("sodium-volt-shield-config-test-");
		Path path = directory.resolve("shield.json");
		Path dangling = directory.resolve("dangling.json");
		try {
			ResourcePackShieldConfig fresh = ResourcePackShieldConfig.load(path);
			check(fresh.save(path, true) && !Files.exists(path),
					"disabled never-enabled config must not be created");
			fresh.setMaximumSingleResourceMiB(65);
			check(fresh.getMaximumTotalReadMiB() == 512,
					"larger existing valid total remains unchanged");
			fresh.setMaximumTotalReadMiB(64);
			check(fresh.getMaximumTotalReadMiB() == 128,
					"total ceiling rounds upward to its valid 64 MiB step");
			fresh.setResourcePackShieldEnabled(true);
			check(fresh.save(path, true), "enabled config must save atomically");
			ResourcePackShieldConfig roundTrip = ResourcePackShieldConfig.load(path);
			check(roundTrip.isResourcePackShieldEnabled()
							&& roundTrip.getMaximumSingleResourceMiB() == 65
							&& roundTrip.getMaximumTotalReadMiB() == 128,
					"strict config round-trip");
			testCoherentRuntimeSnapshots(roundTrip);
			String valid = Files.readString(path);
			assertInvalid(path, valid.replaceFirst(
					"\"version\": 1", "\"version\": 1, \"version\": 1"
			), "duplicate field");
			assertInvalid(path, valid.replaceFirst(
					"\"show_inspector_statistics\": true",
					"\"extra\": true, \"show_inspector_statistics\": true"
			), "extra field");
			assertInvalid(path, valid.replaceFirst(
					"\"monitor_local_packs\": true",
					"\"monitor_local_packs\": \"true\""
			), "wrong type");
			assertInvalid(path, valid.replaceFirst(
					"\"maximum_entries\": 16384",
					"\"maximum_entries\": 999"
			), "out-of-range value");
			Files.write(path, new byte[ResourcePackShieldConfig.MAXIMUM_CONFIG_BYTES + 1]);
			check(!ResourcePackShieldConfig.load(path).isResourcePackShieldEnabled(),
					"oversized config");
			StringBuilder deep = new StringBuilder(4_096);
			deep.append("{\"version\":1,\"x\":");
			for (int index = 0; index < 100; index++) {
				deep.append('[');
			}
			deep.append('0');
			for (int index = 0; index < 100; index++) {
				deep.append(']');
			}
			deep.append('}');
			Files.writeString(path, deep, StandardCharsets.UTF_8);
			check(!ResourcePackShieldConfig.load(path).isResourcePackShieldEnabled(),
					"deep config");
			Files.delete(path);
			if (createDanglingSymlink(dangling)) {
				check(!ResourcePackShieldConfig.load(dangling)
								.isResourcePackShieldEnabled(),
						"dangling config link must be rejected");
				check(!fresh.save(dangling, false)
								&& Files.isSymbolicLink(dangling)
								&& !Files.exists(dangling),
						"dangling config link must be preserved on failed save");
			}
		} finally {
			Files.deleteIfExists(dangling);
			Files.deleteIfExists(path);
			Files.deleteIfExists(directory);
		}
	}

	private static void testCoherentRuntimeSnapshots(ResourcePackShieldConfig config)
			throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread writer = new Thread(() -> {
			try {
				start.await();
				for (int index = 0; index < 10_000; index++) {
					config.setMaximumSingleResourceMiB(index % 2 == 0 ? 64 : 256);
					config.setMaximumTotalReadMiB(index % 2 == 0 ? 64 : 512);
				}
			} catch (Throwable throwable) {
				failure.compareAndSet(null, throwable);
			}
		});
		Thread reader = new Thread(() -> {
			try {
				start.await();
				long previousRevision = Long.MIN_VALUE;
				for (int index = 0; index < 10_000; index++) {
					ResourcePackShieldConfig.RuntimeSnapshot snapshot =
							config.runtimeSnapshot();
					if (snapshot.revision() < previousRevision
							|| snapshot.policy().maximumTotalResourceBytes()
									< snapshot.policy().maximumSingleResourceBytes()) {
						throw new AssertionError("torn runtime snapshot");
					}
					previousRevision = snapshot.revision();
				}
			} catch (Throwable throwable) {
				failure.compareAndSet(null, throwable);
			}
		});
		writer.start();
		reader.start();
		start.countDown();
		writer.join();
		reader.join();
		check(failure.get() == null,
				"concurrent setters and runtime snapshots remain coherent");
	}

	private static void assertInvalid(Path path, String document, String message)
			throws Exception {
		Files.writeString(path, document, StandardCharsets.UTF_8);
		check(!ResourcePackShieldConfig.load(path).isResourcePackShieldEnabled(), message);
	}

	private static boolean createDanglingSymlink(Path link) throws Exception {
		try {
			Files.createSymbolicLink(link, Path.of("missing-shield-config.json"));
			return true;
		} catch (UnsupportedOperationException exception) {
			System.out.println("Skipping config symlink assertions: unsupported");
			return false;
		} catch (FileSystemException exception) {
			String reason = String.valueOf(exception.getReason()).toLowerCase(Locale.ROOT);
			if (reason.contains("not supported") || reason.contains("privilege")) {
				System.out.println("Skipping config symlink assertions: unavailable");
				return false;
			}
			throw exception;
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("Resource-Pack Shield config: " + message);
		}
	}
}
