package com.ragedriver.sodiumvolt.client.profile;

import com.ragedriver.sodiumvolt.client.config.ProfilesConfigTestSupport;
import com.ragedriver.sodiumvolt.client.config.VoltConfigFactoryResetTestSupport;
import com.ragedriver.sodiumvolt.client.config.VoltTunerProfileTestSupport;

import java.util.concurrent.atomic.AtomicInteger;

public final class ProfilesLogicTest {
	private ProfilesLogicTest() {
	}

	public static void main(String[] arguments) throws Exception {
		testDisabledTickMonitorGate();
		testSettingsBoundsAndOwnership();
		testPrivateDeterministicIdentity();
		testContextIdentityCache();
		testBoundedDeterministicEviction();
		testContextTogglePolicy();
		testFactoryResetCancellation();
		ProfilesConfigTestSupport.run();
		VoltConfigFactoryResetTestSupport.run();
		VoltTunerProfileTestSupport.run();
		System.out.println("Sodium Volt Profiles logic tests passed");
	}

	private static void testDisabledTickMonitorGate() {
		check(!ProfileTickGate.requiresUpdate(false, false),
				"stable disabled profile ticks must stop after one volatile snapshot check");
		check(ProfileTickGate.requiresUpdate(false, true),
				"the enabled-to-disabled profile transition must run once to restore owned state");
		check(ProfileTickGate.requiresUpdate(true, false),
				"enabling profiles must initialize and apply the active context");
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

	private static void testContextIdentityCache() {
		ProfileContextIdentityCache<String> cache = new ProfileContextIdentityCache<>();
		AtomicInteger derivations = new AtomicInteger();
		Object levelA = new Object();
		Object connectionA = new Object();
		Object serverA = new Object();
		String privateAddressA = "private-a.example:25565";
		String keyA = "0a".repeat(32);

		check(resolveCached(
				cache, ProfileContextIdentityCache.SourceKind.SERVER,
				levelA, connectionA, serverA, privateAddressA, 1L, keyA, derivations
		).equals(keyA), "initial server context derives a private key");
		for (int tick = 0; tick < 1_000; tick++) {
			check(resolveCached(
					cache, ProfileContextIdentityCache.SourceKind.SERVER,
					levelA, connectionA, serverA, privateAddressA, 1L,
					"unexpected", derivations
			).equals(keyA), "stable context uses the constant-time cache path");
		}
		check(derivations.get() == 1, "stable ticks do not repeat identity work");
		check(!cache.value().contains("private-a"),
				"cached value contains no plaintext address");

		Object levelB = new Object();
		String keyLevelB = "1b".repeat(32);
		check(resolveCached(
				cache, ProfileContextIdentityCache.SourceKind.SERVER,
				levelB, connectionA, serverA, privateAddressA, 1L,
				keyLevelB, derivations
		).equals(keyLevelB), "level instance change derives before capture or forget");

		String privateAddressB = "private-b.example:25565";
		String keyAddressB = "2c".repeat(32);
		check(resolveCached(
				cache, ProfileContextIdentityCache.SourceKind.SERVER,
				levelB, connectionA, serverA, privateAddressB, 1L,
				keyAddressB, derivations
		).equals(keyAddressB), "address replacement invalidates the server context");

		Object connectionB = new Object();
		Object serverB = new Object();
		String reconnectKey = "3d".repeat(32);
		check(resolveCached(
				cache, ProfileContextIdentityCache.SourceKind.SERVER,
				new Object(), connectionB, serverB, privateAddressB, 1L,
				reconnectKey, derivations
		).equals(reconnectKey), "same-address reconnect derives for the new connection");

		Object integratedLevel = new Object();
		Object integratedConnection = new Object();
		Object integratedServer = new Object();
		check(!cache.matches(
				ProfileContextIdentityCache.SourceKind.SINGLE_PLAYER,
				integratedLevel, integratedConnection, integratedServer, null, 1L
		), "integrated-server transition clears the remote key");
		derivations.incrementAndGet();
		check(!cache.isCached(),
				"temporarily unavailable integrated path is not cached");
		String worldKey = "4e".repeat(32);
		check(resolveCached(
				cache, ProfileContextIdentityCache.SourceKind.SINGLE_PLAYER,
				integratedLevel, integratedConnection, integratedServer, null, 1L,
				worldKey, derivations
		).equals(worldKey), "integrated path readiness derives on a later call");

		String changedSaltKey = "5f".repeat(32);
		check(resolveCached(
				cache, ProfileContextIdentityCache.SourceKind.SINGLE_PLAYER,
				integratedLevel, integratedConnection, integratedServer, null, 2L,
				changedSaltKey, derivations
		).equals(changedSaltKey), "salt or scope generation invalidates the key");

		cache.clear();
		check(!cache.isCached(), "menu, factory reset, and client stop clear the cache");
		cache.store(
				ProfileContextIdentityCache.SourceKind.SERVER,
				levelA, connectionA, serverA, privateAddressA, 2L, keyA
		);
		check(!cache.matches(
				ProfileContextIdentityCache.SourceKind.SERVER,
				levelB, connectionB, serverB, privateAddressB, 2L
		) && !cache.isCached(),
				"failed new-context derivation cannot expose the previous key");
		cache.store(
				ProfileContextIdentityCache.SourceKind.SERVER,
				levelB, connectionB, serverB, privateAddressB, 2L, null
		);
		check(cache.isCached() && cache.matches(
				ProfileContextIdentityCache.SourceKind.SERVER,
				levelB, connectionB, serverB, privateAddressB, 2L
		) && cache.value() == null,
				"bounded invalid identities are cached as unavailable without a key leak");
	}

	private static String resolveCached(
			ProfileContextIdentityCache<String> cache,
			ProfileContextIdentityCache.SourceKind sourceKind,
			Object level,
			Object connection,
			Object source,
			Object detail,
			long contextRevision,
			String derived,
			AtomicInteger derivations
	) {
		if (cache.matches(
				sourceKind, level, connection, source, detail, contextRevision
		)) {
			return cache.value();
		}
		derivations.incrementAndGet();
		cache.store(
				sourceKind, level, connection, source, detail, contextRevision, derived
		);
		return derived;
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
