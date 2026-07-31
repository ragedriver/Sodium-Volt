package com.ragedriver.sodiumvolt.client.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class PrivacyScreenshotConfigTestSupport {
	private PrivacyScreenshotConfigTestSupport() {
	}

	public static void run() throws Exception {
		Path directory = Files.createTempDirectory("sodium-volt-privacy-config-test-");
		Path path = directory.resolve("privacy.json");
		Path link = directory.resolve("privacy-link.json");
		try {
			PrivacyScreenshotConfig defaults = PrivacyScreenshotConfig.load(path);
			check(!defaults.isEnabled(), "master defaults off");
			check(defaults.isHideChat() && defaults.isHideDebugOverlay()
						&& defaults.isBlockOpenScreens() && defaults.isFailClosed(),
					"protective controls default on");
			defaults.setEnabled(true);
			defaults.setHideHeldItem(true);
			check(defaults.save(path), "strict config save");
			PrivacyScreenshotConfig loaded = PrivacyScreenshotConfig.load(path);
			check(loaded.isEnabled() && loaded.isHideHeldItem(), "strict round trip");
			PrivacyScreenshotConfig.RuntimeSnapshot snapshot = loaded.runtimeSnapshot();
			check(snapshot.enabled() && snapshot.policy().hideHeldItem()
						&& snapshot.policy().randomizeFilename(),
					"coherent immutable runtime snapshot");

			String valid = Files.readString(path);
			assertInvalid(path, valid.replaceFirst(
					"\"version\": 1", "\"version\": 1, \"version\": 1"
			), "duplicate key");
			assertInvalid(path, valid.replaceFirst(
					"\"hide_chat\": true", "\"hide_chat\": \"true\""
			), "wrong value type");
			assertInvalid(path, valid.replaceFirst(
					"\"show_notifications\": true",
					"\"unexpected\": true, \"show_notifications\": true"
			), "unexpected key");
			Files.write(path, new byte[PrivacyScreenshotConfig.MAXIMUM_CONFIG_BYTES + 1]);
			check(!PrivacyScreenshotConfig.load(path).isEnabled(), "oversized document");

			Files.delete(path);
			if (createDanglingSymlink(link)) {
				check(!PrivacyScreenshotConfig.load(link).isEnabled(),
						"symlink load rejected");
				check(!defaults.save(link) && Files.isSymbolicLink(link),
						"symlink save rejected without replacement");
			}
		} finally {
			Files.deleteIfExists(link);
			Files.deleteIfExists(path);
			Files.deleteIfExists(directory);
		}
	}

	private static void assertInvalid(Path path, String document, String label)
			throws Exception {
		Files.writeString(path, document, StandardCharsets.UTF_8);
		check(!PrivacyScreenshotConfig.load(path).isEnabled(), label);
	}

	private static boolean createDanglingSymlink(Path link) throws Exception {
		try {
			Files.createSymbolicLink(link, Path.of("missing-privacy-config.json"));
			return true;
		} catch (UnsupportedOperationException exception) {
			return false;
		} catch (FileSystemException exception) {
			String reason = String.valueOf(exception.getReason()).toLowerCase(Locale.ROOT);
			if (reason.contains("not supported") || reason.contains("privilege")) {
				return false;
			}
			throw exception;
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("Privacy config: " + message);
		}
	}
}
