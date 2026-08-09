package com.ragedriver.sodiumvolt.client.config;

import com.ragedriver.sodiumvolt.client.profile.ProfileIdentity;
import com.ragedriver.sodiumvolt.client.profile.ProfileParticleMode;
import com.ragedriver.sodiumvolt.client.profile.ProfileSettings;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
			ProfilesConfig.RuntimeControl defaultControl = defaults.runtimeControl();
			check(!defaultControl.enabled()
						&& defaultControl == defaults.runtimeControl(),
					"disabled ticks read one stable lock-free runtime snapshot");
			check(!defaults.isProfilesEnabled() && !defaults.isGlobalDefaultsInitialized(),
					"master and captured baseline default off");
			check(defaults.isSinglePlayerProfilesEnabled()
						&& defaults.isSpecificServerProfilesEnabled(),
					"context recognition defaults ready behind the master");
			long initialIdentityRevision = defaults.identityContextRevision();

			String rawAddress = "Private.Example:25565";
			String serverKey = ProfileIdentity.serverKey(
					rawAddress, defaults.identitySalt()
			).orElseThrow();
			String worldKey = ProfileIdentity.singlePlayerKey(
					"/private/saves/My World", defaults.identitySalt()
			).orElseThrow();
			defaults.setProfilesEnabled(true);
			ProfilesConfig.RuntimeControl enabledControl = defaults.runtimeControl();
			check(enabledControl.enabled() && enabledControl != defaultControl,
					"a config mutation atomically publishes a new runtime snapshot");
			check(defaults.identityContextRevision() != initialIdentityRevision,
					"enabling profiles advances the identity-context generation");
			long enabledIdentityRevision = defaults.identityContextRevision();
			defaults.setProfilesEnabled(true);
			check(defaults.identityContextRevision() == enabledIdentityRevision,
					"idempotent scope writes do not invalidate a stable identity");
			long beforeScopeRevision = defaults.identityContextRevision();
			defaults.setSinglePlayerProfilesEnabled(false);
			check(!defaults.runtimeControl().singlePlayerProfilesEnabled(),
					"scope mutations publish before the next profile tick");
			check(defaults.identityContextRevision() != beforeScopeRevision,
					"single-player scope changes invalidate identity context");
			defaults.setSinglePlayerProfilesEnabled(true);
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
			check(loaded.runtimeControl().enabled()
						&& loaded.runtimeControl().identityContextRevision()
						== loaded.identityContextRevision(),
					"loaded runtime state is published as one coherent snapshot");
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

			long beforeFactoryIdentityRevision = loaded.identityContextRevision();
			byte[] beforeFactorySalt = loaded.identitySalt();
			loaded.resetToFactoryDefaults();
			check(!loaded.runtimeControl().enabled(),
					"factory reset publishes the disabled fast path immediately");
			check(!loaded.isProfilesEnabled()
						&& !loaded.isGlobalDefaultsInitialized()
						&& loaded.serverProfileCount() == 0
						&& loaded.singlePlayerProfileCount() == 0,
					"factory reset clears profile records and captured state");
			check(loaded.getGlobalDefaults().equals(ProfileSettings.globalDefaults())
						&& loaded.getServerTemplate().equals(ProfileSettings.serverDefaults()),
					"factory reset copies subordinate profile defaults");
			check(loaded.identityContextRevision() != beforeFactoryIdentityRevision,
					"factory reset invalidates every cached profile identity");
			check(!Arrays.equals(beforeFactorySalt, loaded.identitySalt()),
					"factory reset replaces the profile identity salt");

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
