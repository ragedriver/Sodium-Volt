package com.ragedriver.sodiumvolt.client.profile;

import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.config.ProfilesConfig;
import com.ragedriver.sodiumvolt.client.performance.AdaptivePerformanceController;
import com.ragedriver.sodiumvolt.client.recovery.VoltRecoveryEngine;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public final class PerformanceProfileEngine {
	private static final ProfilesConfig CONFIG = ProfilesConfig.getInstance();
	private static final Context MENU_CONTEXT = new Context(ContextKind.MENU, "");
	private static final ProfileContextIdentityCache<Context> IDENTITY_CACHE =
			new ProfileContextIdentityCache<>();

	private static Context activeContext = MENU_CONTEXT;
	private static ProfileSettings lastApplied;
	private static volatile boolean observedEnabled;
	private static volatile boolean runtimeFailed;

	private PerformanceProfileEngine() {
	}

	/**
	 * Register this after other option-owning Volt engines. Their stopping hooks then
	 * release temporary ownership before Profiles performs its final owned restore.
	 */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(PerformanceProfileEngine::onClientTick);
		ClientPlayConnectionEvents.INIT.register(
				PerformanceProfileEngine::onConnectionTransition
		);
		ClientPlayConnectionEvents.DISCONNECT.register(
				PerformanceProfileEngine::onConnectionTransition
		);
		ClientLifecycleEvents.CLIENT_STOPPING.register(
				PerformanceProfileEngine::onClientStopping
		);
	}

	public static void onClientTick(Minecraft minecraft) {
		if (runtimeFailed || minecraft == null || minecraft.options == null
				|| !minecraft.isSameThread()) {
			return;
		}
		ProfilesConfig.RuntimeControl control = CONFIG.runtimeControl();
		boolean configuredEnabled = control.enabled();
		if (!ProfileTickGate.requiresUpdate(
				configuredEnabled, observedEnabled, VoltRecoveryEngine.ownsGraphicsOptions())) {
			return;
		}
		try {
			update(minecraft, control);
		} catch (RuntimeException | LinkageError exception) {
			IDENTITY_CACHE.clear();
			runtimeFailed = true;
			SodiumVolt.LOGGER.warn(
					"Sodium Volt Profiles failed safely and stopped for this session"
			);
		}
	}

	public static synchronized boolean captureCurrentSinglePlayerProfile(
			Minecraft minecraft
	) {
		if (VoltRecoveryEngine.ownsGraphicsOptions()) {
			return false;
		}
		Context context = currentWorldContext(minecraft);
		if (context == null || context.kind != ContextKind.SINGLE_PLAYER) {
			return false;
		}
		ProfileSettings captured = capture(minecraft.options);
		CONFIG.storeSinglePlayerProfile(context.key, captured);
		if (context.equals(activeContext)) {
			lastApplied = captured;
		}
		return CONFIG.saveChecked();
	}

	public static synchronized boolean captureCurrentServerProfile(Minecraft minecraft) {
		if (VoltRecoveryEngine.ownsGraphicsOptions()) {
			return false;
		}
		Context context = currentWorldContext(minecraft);
		if (context == null || context.kind != ContextKind.SERVER) {
			return false;
		}
		ProfileSettings captured = capture(minecraft.options);
		CONFIG.storeServerProfile(context.key, captured);
		if (context.equals(activeContext)) {
			lastApplied = captured;
		}
		return CONFIG.saveChecked();
	}

	public static synchronized boolean forgetCurrentSinglePlayerProfile(
			Minecraft minecraft
	) {
		Context context = currentWorldContext(minecraft);
		if (context == null || context.kind != ContextKind.SINGLE_PLAYER
				|| !CONFIG.forgetSinglePlayerProfile(context.key)) {
			return false;
		}
		return CONFIG.saveChecked();
	}

	public static synchronized boolean forgetCurrentServerProfile(Minecraft minecraft) {
		Context context = currentWorldContext(minecraft);
		if (context == null || context.kind != ContextKind.SERVER
				|| !CONFIG.forgetServerProfile(context.key)) {
			return false;
		}
		return CONFIG.saveChecked();
	}

	public static synchronized ProfileSettings prepareFactoryReset(Minecraft minecraft) {
		if (minecraft == null || minecraft.options == null) {
			return ProfileSettings.globalDefaults();
		}
		ProfileSettings actual = capture(minecraft.options);
		if (!CONFIG.isGlobalDefaultsInitialized() || lastApplied == null) {
			// Factory reset owns only Volt state; never replace unrelated menu edits.
			return actual;
		}
		ProfileSettings rebased = CONFIG.getGlobalDefaults().rebase(actual, lastApplied);
		return rebased.restoreOwned(actual, lastApplied).settings();
	}

	public static synchronized void finishFactoryReset(
			Minecraft minecraft,
			ProfileSettings baseline
	) {
		activeContext = MENU_CONTEXT;
		IDENTITY_CACHE.clear();
		lastApplied = null;
		observedEnabled = false;
		runtimeFailed = false;
		if (minecraft == null || minecraft.options == null || baseline == null) {
			return;
		}
		apply(minecraft.options, baseline.sanitized());
		minecraft.options.save();
	}

	static ProfileSettings capture(Options options) {
		return new ProfileSettings(
				options.renderDistance().get(),
				options.simulationDistance().get(),
				(int) Math.round(options.entityDistanceScaling().get() * 100.0D),
				options.framerateLimit().get(),
				fromMinecraftParticles(options.particles().get())
		).sanitized();
	}

	static boolean apply(Options options, ProfileSettings requested) {
		ProfileSettings settings = requested.sanitized();
		AdaptivePerformanceController.prepareForProfileOwnedOptions(options);
		boolean changed = set(options.renderDistance(), settings.renderDistance());
		changed |= set(options.simulationDistance(), settings.simulationDistance());
		changed |= set(
				options.entityDistanceScaling(),
				settings.entityDistancePercent() / 100.0D
		);
		changed |= set(options.framerateLimit(), settings.framerateLimit());
		changed |= set(options.particles(), toMinecraftParticles(settings.particleMode()));
		AdaptivePerformanceController.acceptProfileOwnedOptions(options);
		return changed;
	}

	private static void update(Minecraft minecraft, ProfilesConfig.RuntimeControl control) {
		boolean enabled = control.enabled();
		if (!enabled) {
			IDENTITY_CACHE.clear();
			if (observedEnabled && lastApplied != null) {
				restoreGlobalDefaults(minecraft, true);
			}
			observedEnabled = false;
			activeContext = MENU_CONTEXT;
			lastApplied = null;
			return;
		}
		if (!observedEnabled) {
			observedEnabled = true;
			if (CONFIG.initializeGlobalDefaults(capture(minecraft.options))) {
				CONFIG.save();
			}
		}

		Context detected = currentWorldContext(minecraft, control.identityContextRevision());
		Context desired = detected == null ? MENU_CONTEXT : detected;
		if (activeContext.kind != ContextKind.MENU
				&& !ProfileContextPolicy.isEnabled(
						activeContext.kind.policyKind,
						control.singlePlayerProfilesEnabled(),
						control.specificServerProfilesEnabled()
				)) {
			restoreGlobalDefaults(minecraft, true);
			activeContext = MENU_CONTEXT;
			return;
		}
		if (desired.equals(activeContext)) {
			return;
		}
		if (lastApplied != null) {
			restoreGlobalDefaults(minecraft, true);
		}
		activeContext = desired;
		if (desired.kind == ContextKind.MENU) {
			lastApplied = null;
			return;
		}
		ProfileSettings profile = resolveProfile(desired);
		if (profile == null) {
			activeContext = MENU_CONTEXT;
			lastApplied = null;
			return;
		}
		apply(minecraft.options, profile);
		lastApplied = capture(minecraft.options);
	}

	private static ProfileSettings resolveProfile(Context context) {
		long before = CONFIG.revision();
		ProfileSettings settings;
		if (context.kind == ContextKind.SINGLE_PLAYER) {
			if (!CONFIG.isSinglePlayerProfilesEnabled()) {
				return null;
			}
			settings = CONFIG.resolveSinglePlayerProfile(context.key);
		} else if (context.kind == ContextKind.SERVER) {
			if (!CONFIG.isSpecificServerProfilesEnabled()) {
				return null;
			}
			settings = CONFIG.resolveServerProfile(context.key);
		} else {
			return null;
		}
		if (CONFIG.revision() != before) {
			CONFIG.save();
		}
		return settings;
	}

	private static void restoreGlobalDefaults(Minecraft minecraft, boolean saveOptions) {
		if (lastApplied == null || !CONFIG.isGlobalDefaultsInitialized()) {
			lastApplied = null;
			return;
		}
		ProfileSettings actual = capture(minecraft.options);
		ProfileSettings original = CONFIG.getGlobalDefaults();
		ProfileSettings rebased = original.rebase(actual, lastApplied);
		if (!rebased.equals(original)) {
			CONFIG.rebaseGlobalDefaults(rebased);
			CONFIG.save();
		}
		if (CONFIG.isRestoreGlobalDefaultsOnMenu()) {
			ProfileSettings restored = rebased.restoreOwned(actual, lastApplied).settings();
			if (apply(minecraft.options, restored) && saveOptions) {
				minecraft.options.save();
			}
		}
		lastApplied = null;
	}

	private static Context currentWorldContext(Minecraft minecraft) {
		return currentWorldContext(minecraft, CONFIG.runtimeControl().identityContextRevision());
	}

	private static Context currentWorldContext(
			Minecraft minecraft,
			long contextRevision
	) {
		if (minecraft == null || minecraft.options == null || minecraft.level == null) {
			IDENTITY_CACHE.clear();
			return null;
		}
		Object level = minecraft.level;
		ClientPacketListener connection = minecraft.getConnection();
		IntegratedServer integratedServer = minecraft.getSingleplayerServer();
		if (integratedServer != null) {
			// MinecraftServer owns a final storage source. A different storage root therefore
			// arrives with a different IntegratedServer token; startup exceptions are retried.
			if (IDENTITY_CACHE.matches(
					ProfileContextIdentityCache.SourceKind.SINGLE_PLAYER,
					level, connection, integratedServer, null, contextRevision
			)) {
				return IDENTITY_CACHE.value();
			}
			ContextDerivation derivation = deriveSinglePlayerContext(integratedServer);
			if (derivation.cacheable()) {
				IDENTITY_CACHE.store(
						ProfileContextIdentityCache.SourceKind.SINGLE_PLAYER,
						level, connection, integratedServer, null, contextRevision,
						derivation.context()
				);
			}
			return derivation.context();
		}
		ServerData server = connection == null ? null : connection.getServerData();
		if (server == null) {
			IDENTITY_CACHE.clear();
			return null;
		}
		String address = server.ip;
		if (IDENTITY_CACHE.matches(
				ProfileContextIdentityCache.SourceKind.SERVER,
				level, connection, server, address, contextRevision
		)) {
			return IDENTITY_CACHE.value();
		}
		Context derived = deriveServerContext(address);
		// Invalid immutable address strings are cached as unavailable until a token changes.
		IDENTITY_CACHE.store(
				ProfileContextIdentityCache.SourceKind.SERVER,
				level, connection, server, address, contextRevision, derived
		);
		return derived;
	}

	private static ContextDerivation deriveSinglePlayerContext(
			IntegratedServer integratedServer
	) {
		Path worldRoot;
		try {
			worldRoot = integratedServer.getWorldPath(LevelResource.ROOT);
		} catch (RuntimeException exception) {
			// Startup can expose the client level before integrated storage is ready.
			return ContextDerivation.RETRY;
		}
		String worldStorageIdentity;
		try {
			worldStorageIdentity = worldRoot.toAbsolutePath().normalize().toString();
		} catch (RuntimeException exception) {
			return ContextDerivation.INVALID;
		}
		return new ContextDerivation(
				deriveContext(ContextKind.SINGLE_PLAYER, worldStorageIdentity), true
		);
	}

	private static Context deriveServerContext(String address) {
		return deriveContext(ContextKind.SERVER, address);
	}

	private static Context deriveContext(ContextKind kind, String rawIdentity) {
		byte[] salt = CONFIG.identitySalt();
		try {
			String key = (kind == ContextKind.SINGLE_PLAYER
					? ProfileIdentity.singlePlayerKey(rawIdentity, salt)
					: ProfileIdentity.serverKey(rawIdentity, salt)).orElse(null);
			return key == null ? null : new Context(kind, key);
		} finally {
			Arrays.fill(salt, (byte) 0);
		}
	}

	private static synchronized void onConnectionTransition(
			ClientPacketListener listener,
			Minecraft minecraft
	) {
		Objects.requireNonNull(listener, "listener");
		Objects.requireNonNull(minecraft, "minecraft");
		IDENTITY_CACHE.clear();
	}

	private static void onClientStopping(Minecraft minecraft) {
		synchronized (PerformanceProfileEngine.class) {
			IDENTITY_CACHE.clear();
			if (lastApplied != null && !VoltRecoveryEngine.ownsGraphicsOptions()) {
				try {
					restoreGlobalDefaults(minecraft, true);
				} catch (RuntimeException | LinkageError exception) {
					SodiumVolt.LOGGER.warn(
							"Sodium Volt Profiles could not restore owned settings while stopping"
					);
				}
			}
			activeContext = MENU_CONTEXT;
			lastApplied = null;
			observedEnabled = false;
		}
	}

	private static ProfileParticleMode fromMinecraftParticles(ParticleStatus status) {
		return switch (status) {
			case ALL -> ProfileParticleMode.ALL;
			case DECREASED -> ProfileParticleMode.DECREASED;
			case MINIMAL -> ProfileParticleMode.MINIMAL;
		};
	}

	private static ParticleStatus toMinecraftParticles(ProfileParticleMode mode) {
		return switch (mode) {
			case ALL -> ParticleStatus.ALL;
			case DECREASED -> ParticleStatus.DECREASED;
			case MINIMAL -> ParticleStatus.MINIMAL;
		};
	}

	private static <T> boolean set(net.minecraft.client.OptionInstance<T> option, T value) {
		if (Objects.equals(option.get(), value)) {
			return false;
		}
		option.set(value);
		return true;
	}

	private enum ContextKind {
		MENU(ProfileContextPolicy.Kind.MENU),
		SINGLE_PLAYER(ProfileContextPolicy.Kind.SINGLE_PLAYER),
		SERVER(ProfileContextPolicy.Kind.SERVER);

		private final ProfileContextPolicy.Kind policyKind;

		ContextKind(ProfileContextPolicy.Kind policyKind) {
			this.policyKind = policyKind;
		}
	}

	private record Context(ContextKind kind, String key) {
		private Context {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(key, "key");
		}
	}

	private record ContextDerivation(Context context, boolean cacheable) {
		private static final ContextDerivation RETRY =
				new ContextDerivation(null, false);
		private static final ContextDerivation INVALID =
				new ContextDerivation(null, true);
	}
}
