package com.ragedriver.sodiumvolt.client.profile;

import com.ragedriver.sodiumvolt.client.config.ProfilesConfigTestSupport;
import com.ragedriver.sodiumvolt.client.config.VoltConfigFactoryResetTestSupport;

import java.util.concurrent.atomic.AtomicInteger;

public final class ProfilesLogicTest {
	private ProfilesLogicTest() {
	}

	public static void main(String[] arguments) throws Exception {
		testSettingsBoundsAndOwnership();
		testPrivateDeterministicIdentity();
		testBoundedDeterministicEviction();
		testContextTogglePolicy();
		testFactoryResetCancellation();
		ProfilesConfigTestSupport.run();
		VoltConfigFactoryResetTestSupport.run();
		System.out.println("Sodium Volt Profiles logic tests passed");
	}

	private static void testSettingsBoundsAndOwnership() {
		ProfileSettings dirty = new ProfileSettings(
				-100, 100, 61, 257, ProfileParticleMode.MINIMAL
		);
		ProfileSettings bounded = dirty.sanitized();
		check(bounded.renderDistance() == ProfileSettings.RENDER_DISTANCE_MIN,
				"render distance is bounded");
		check(bounded.simulationDistance() == ProfileSettings.SIMULATION_DISTANCE_MAX,
				"simulation distance is bounded");
		check(bounded.entityDistancePercent() == 50
					&& bounded.framerateLimit() == 255,
				"stepped settings are normalized");

		ProfileSettings global = new ProfileSettings(
				16, 12, 100, 120, ProfileParticleMode.ALL
		);
		ProfileSettings applied = new ProfileSettings(
				8, 8, 75, 60, ProfileParticleMode.MINIMAL
		);
		ProfileSettings actual = new ProfileSettings(
				6, 8, 125, 60, ProfileParticleMode.MINIMAL
		);
		ProfileSettings rebased = global.rebase(actual, applied);
		ProfileSettings restored = rebased.restoreOwned(actual, applied).settings();
		check(restored.renderDistance() == 6
					&& restored.entityDistancePercent() == 125,
				"external and controller-owned changes survive disconnect");
		check(restored.simulationDistance() == 12
					&& restored.framerateLimit() == 120
					&& restored.particleMode() == ProfileParticleMode.ALL,
				"only unchanged profile-owned fields restore globally");
	}

	private static void testPrivateDeterministicIdentity() {
		byte[] salt = new byte[ProfileIdentity.SALT_BYTES];
		byte[] otherSalt = new byte[ProfileIdentity.SALT_BYTES];
		otherSalt[0] = 1;
		String first = ProfileIdentity.serverKey(" Example.COM. ", salt).orElseThrow();
		String equivalent = ProfileIdentity.serverKey("example.com", salt).orElseThrow();
		String privateVariant = ProfileIdentity.serverKey("example.com", otherSalt)
				.orElseThrow();
		check(first.equals(equivalent), "server identity normalization is deterministic");
		check(!first.equals(privateVariant), "per-install salt changes stored identity");
		check(ProfileIdentity.isValidStoredKey(first)
					&& !first.contains("example"), "stored key is a fixed one-way token");
		check(!ProfileIdentity.singlePlayerKey("A\u0000B", salt).isPresent(),
				"control characters are rejected");
		check(!ProfileIdentity.serverKey(
				"x".repeat(ProfileIdentity.MAXIMUM_IDENTITY_CHARACTERS + 1), salt
		).isPresent(), "identity work has a hard input bound");
		String upperPath = ProfileIdentity.singlePlayerKey("/Saves/World", salt).orElseThrow();
		String lowerPath = ProfileIdentity.singlePlayerKey("/saves/world", salt).orElseThrow();
		check(!upperPath.equals(lowerPath), "distinct case-sensitive save paths do not collide");
	}

	private static void testBoundedDeterministicEviction() {
		BoundedProfileStore store = new BoundedProfileStore(2);
		String first = "00".repeat(32);
		String second = "11".repeat(32);
		String third = "22".repeat(32);
		store.put(first, ProfileSettings.globalDefaults());
		store.put(second, ProfileSettings.singlePlayerDefaults());
		check(store.get(first) != null, "lookup refreshes deterministic recency");
		store.put(third, ProfileSettings.serverDefaults());
		check(store.size() == 2 && store.get(second) == null,
				"bounded store evicts the least recently used record");
		check(store.get(first) != null && store.get(third) != null,
				"newest and refreshed records remain");
	}

	private static void testContextTogglePolicy() {
		check(ProfileContextPolicy.isEnabled(
				ProfileContextPolicy.Kind.SINGLE_PLAYER, true, false
		), "single-player context follows its toggle");
		check(!ProfileContextPolicy.isEnabled(
				ProfileContextPolicy.Kind.SINGLE_PLAYER, false, true
		), "turning off single-player profiles releases an active overlay");
		check(!ProfileContextPolicy.isEnabled(
				ProfileContextPolicy.Kind.SERVER, true, false
		), "turning off server profiles releases an active overlay");
		check(!ProfileContextPolicy.isEnabled(
				ProfileContextPolicy.Kind.MENU, true, true
		), "menu never selects a world overlay");
	}

	private static void testFactoryResetCancellation() {
		AtomicInteger reset = new AtomicInteger();
		AtomicInteger close = new AtomicInteger();
		AtomicInteger cancel = new AtomicInteger();
		FactoryResetDecision.handle(
				false, reset::incrementAndGet, close::incrementAndGet, cancel::incrementAndGet
		);
		check(reset.get() == 0 && close.get() == 0 && cancel.get() == 1,
				"No performs no reset and leaves the parent available");
		FactoryResetDecision.handle(
				true, reset::incrementAndGet, close::incrementAndGet, cancel::incrementAndGet
		);
		check(reset.get() == 1 && close.get() == 1 && cancel.get() == 1,
				"Yes resets once and closes the stale settings parent");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("Profiles: " + message);
		}
	}
}
