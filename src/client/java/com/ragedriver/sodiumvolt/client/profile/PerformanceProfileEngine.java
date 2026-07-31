package com.ragedriver.sodiumvolt.client.profile;

import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.config.ProfilesConfig;
import com.ragedriver.sodiumvolt.client.performance.AdaptivePerformanceController;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.level.storage.LevelResource;

import java.util.Objects;
import java.util.Optional;

public final class PerformanceProfileEngine {
	private static final ProfilesConfig CONFIG = ProfilesConfig.getInstance();
	private static final Context MENU_CONTEXT = new Context(ContextKind.MENU, "");

	private static Context activeContext = MENU_CONTEXT;
	private static ProfileSettings lastApplied;
	private static boolean observedEnabled;
	private static boolean runtimeFailed;

	private PerformanceProfileEngine() {
	}

	/**
	 * Register this after other option-owning Volt engines. Their stopping hooks then
	 * release temporary ownership before Profiles performs its final owned restore.
	 */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(PerformanceProfileEngine::onClientTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(
				PerformanceProfileEngine::onClientStopping
		);
	}

	public static synchronized void onClientTick(Minecraft minecraft) {
		if (runtimeFailed || minecraft == null || minecraft.options == null
				|| !minecraft.isSameThread()) {
			return;
		}
		try {
			update(minecraft);
		} catch (RuntimeException | LinkageError exception) {
			runtimeFailed = true;
			SodiumVolt.LOGGER.warn(
					"Sodium Volt Profiles failed safely and stopped for this session"
			);
		}
	}

	public static synchronized boolean captureCurrentSinglePlayerProfile(
			Minecraft minecraft
	) {
		Optional<Context> context = currentWorldContext(minecraft);
		if (context.isEmpty() || context.get().kind != ContextKind.SINGLE_PLAYER) {
			return false;
		}
		ProfileSettings captured = capture(minecraft.options);
		CONFIG.storeSinglePlayerProfile(context.get().key, captured);
		if (context.get().equals(activeContext)) {
			lastApplied = captured;
		}
		return CONFIG.saveChecked();
	}

	public static synchronized boolean captureCurrentServerProfile(Minecraft minecraft) {
		Optional<Context> context = currentWorldContext(minecraft);
		if (context.isEmpty() || context.get().kind != ContextKind.SERVER) {
			return false;
		}
		ProfileSettings captured = capture(minecraft.options);
		CONFIG.storeServerProfile(context.get().key, captured);
		if (context.get().equals(activeContext)) {
			lastApplied = captured;
		}
		return CONFIG.saveChecked();
	}

	public static synchronized boolean forgetCurrentSinglePlayerProfile(
			Minecraft minecraft
	) {
		Optional<Context> context = currentWorldContext(minecraft);
		if (context.isEmpty() || context.get().kind != ContextKind.SINGLE_PLAYER
				|| !CONFIG.forgetSinglePlayerProfile(context.get().key)) {
			return false;
		}
		return CONFIG.saveChecked();
	}

	public static synchronized boolean forgetCurrentServerProfile(Minecraft minecraft) {
		Optional<Context> context = currentWorldContext(minecraft);
		if (context.isEmpty() || context.get().kind != ContextKind.SERVER
				|| !CONFIG.forgetServerProfile(context.get().key)) {
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

	private static void update(Minecraft minecraft) {
		boolean enabled = CONFIG.isProfilesEnabled();
		if (!enabled) {
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

		Optional<Context> detected = currentWorldContext(minecraft);
		Context desired = detected.orElse(MENU_CONTEXT);
		if (activeContext.kind != ContextKind.MENU
				&& !ProfileContextPolicy.isEnabled(
						activeContext.kind.policyKind,
						CONFIG.isSinglePlayerProfilesEnabled(),
						CONFIG.isSpecificServerProfilesEnabled()
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

	private static Optional<Context> currentWorldContext(Minecraft minecraft) {
		if (minecraft == null || minecraft.options == null || minecraft.level == null) {
			return Optional.empty();
		}
		byte[] salt = CONFIG.identitySalt();
		IntegratedServer integratedServer = minecraft.getSingleplayerServer();
		if (integratedServer != null) {
			String worldStorageIdentity = integratedServer.getWorldPath(LevelResource.ROOT)
					.toAbsolutePath()
					.normalize()
					.toString();
			return ProfileIdentity.singlePlayerKey(worldStorageIdentity, salt)
					.map(key -> new Context(ContextKind.SINGLE_PLAYER, key));
		}
		ServerData server = minecraft.getCurrentServer();
		if (server == null) {
			return Optional.empty();
		}
		return ProfileIdentity.serverKey(server.ip, salt)
				.map(key -> new Context(ContextKind.SERVER, key));
	}

	private static void onClientStopping(Minecraft minecraft) {
		synchronized (PerformanceProfileEngine.class) {
			if (lastApplied != null) {
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
}
