package com.ragedriver.sodiumvolt.client.config;

import com.ragedriver.sodiumvolt.client.profile.ProfileIdentity;
import com.ragedriver.sodiumvolt.client.profile.ProfileParticleMode;
import com.ragedriver.sodiumvolt.client.profile.ProfileSettings;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ProfilesConfigTestSupport {
	private ProfilesConfigTestSupport() {
	}

	public static void run() throws Exception {
		Path directory = Files.createTempDirectory("sodium-volt-profiles-config-test-");
		Path path = directory.resolve("profiles.json");
		Path link = directory.resolve("profiles-link.json");
		try {
			ProfilesConfig defaults = ProfilesConfig.load(path);
			check(!defaults.isProfilesEnabled() && !defaults.isGlobalDefaultsInitialized(),
					"master and captured baseline default off");
			check(defaults.isSinglePlayerProfilesEnabled()
						&& defaults.isSpecificServerProfilesEnabled(),
					"context recognition defaults ready behind the master");

			String rawAddress = "Private.Example:25565";
			String serverKey = ProfileIdentity.serverKey(
					rawAddress, defaults.identitySalt()
			).orElseThrow();
			String worldKey = ProfileIdentity.singlePlayerKey(
					"/private/saves/My World", defaults.identitySalt()
			).orElseThrow();
			defaults.setProfilesEnabled(true);
			defaults.initializeGlobalDefaults(new ProfileSettings(
					20, 10, 150, 90, ProfileParticleMode.DECREASED
			));
			defaults.storeServerProfile(serverKey, ProfileSettings.serverDefaults());
			defaults.storeSinglePlayerProfile(worldKey, ProfileSettings.singlePlayerDefaults());
			check(defaults.save(path), "strict config save");
			String valid = Files.readString(path);
			check(!valid.contains(rawAddress) && !valid.contains("My World"),
					"raw context identities never enter persisted JSON");

			ProfilesConfig loaded = ProfilesConfig.load(path);
			check(loaded.isProfilesEnabled() && loaded.isGlobalDefaultsInitialized(),
					"strict round trip preserves behavior");
			check(loaded.serverProfileCount() == 1
						&& loaded.singlePlayerProfileCount() == 1,
					"strict round trip preserves hashed records");

			assertInvalid(path, valid.replaceFirst(
					"\"version\": 1", "\"version\": 1, \"version\": 1"
			), "duplicate key");
			assertInvalid(path, valid.replaceFirst(
					"\"particle_mode\": \"decreased\"",
					"\"particle_mode\": \"unknown\""
			), "unknown enum");
			assertInvalid(path, valid.replaceFirst(
					"\"profiles_enabled\": true",
					"\"unexpected\": true, \"profiles_enabled\": true"
			), "unexpected root key");
			Files.write(path, new byte[ProfilesConfig.MAXIMUM_CONFIG_BYTES + 1]);
			check(!ProfilesConfig.load(path).isProfilesEnabled(), "oversized document");

			loaded.resetToFactoryDefaults();
			check(!loaded.isProfilesEnabled()
						&& !loaded.isGlobalDefaultsInitialized()
						&& loaded.serverProfileCount() == 0
						&& loaded.singlePlayerProfileCount() == 0,
					"factory reset clears profile records and captured state");
			check(loaded.getGlobalDefaults().equals(ProfileSettings.globalDefaults())
						&& loaded.getServerTemplate().equals(ProfileSettings.serverDefaults()),
					"factory reset copies subordinate profile defaults");

			Files.deleteIfExists(path);
			if (createDanglingSymlink(link)) {
				check(!ProfilesConfig.load(link).isProfilesEnabled(), "symlink load rejected");
				check(!loaded.save(link) && Files.isSymbolicLink(link),
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
		check(!ProfilesConfig.load(path).isProfilesEnabled(), label);
	}

	private static boolean createDanglingSymlink(Path link) throws Exception {
		try {
			Files.createSymbolicLink(link, Path.of("missing-profiles-config.json"));
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
			throw new AssertionError("Profiles config: " + message);
		}
	}
}
