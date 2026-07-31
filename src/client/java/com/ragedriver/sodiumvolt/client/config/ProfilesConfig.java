package com.ragedriver.sodiumvolt.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.profile.BoundedProfileStore;
import com.ragedriver.sodiumvolt.client.profile.ProfileIdentity;
import com.ragedriver.sodiumvolt.client.profile.ProfileParticleMode;
import com.ragedriver.sodiumvolt.client.profile.ProfileSettings;
import com.ragedriver.sodiumvolt.client.resourcepack.ShieldJsonFile;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProfilesConfig {
	static final int CONFIG_VERSION = 1;
	static final int MAXIMUM_CONFIG_BYTES = 96 * 1024;
	public static final int MAXIMUM_SINGLE_PLAYER_PROFILES = 32;
	public static final int MAXIMUM_SERVER_PROFILES = 64;
	private static final Set<String> ROOT_KEYS = Set.of(
			"version",
			"profiles_enabled",
			"restore_global_defaults_on_menu",
			"single_player_profiles_enabled",
			"specific_server_profiles_enabled",
			"global_defaults_initialized",
			"identity_salt",
			"global_defaults",
			"single_player_template",
			"server_template",
			"single_player_profiles",
			"server_profiles"
	);
	private static final Set<String> SETTINGS_KEYS = Set.of(
			"render_distance",
			"simulation_distance",
			"entity_distance_percent",
			"framerate_limit",
			"particle_mode"
	);
	private static final AtomicBoolean LOAD_FAILURE_LOGGED = new AtomicBoolean();
	private static final AtomicBoolean SAVE_FAILURE_LOGGED = new AtomicBoolean();

	private boolean profilesEnabled;
	private boolean restoreGlobalDefaultsOnMenu = true;
	private boolean singlePlayerProfilesEnabled = true;
	private boolean specificServerProfilesEnabled = true;
	private boolean globalDefaultsInitialized;
	private byte[] identitySalt = newSalt();
	private ProfileSettings globalDefaults = ProfileSettings.globalDefaults();
	private ProfileSettings singlePlayerTemplate = ProfileSettings.singlePlayerDefaults();
	private ProfileSettings serverTemplate = ProfileSettings.serverDefaults();
	private final BoundedProfileStore singlePlayerProfiles =
			new BoundedProfileStore(MAXIMUM_SINGLE_PLAYER_PROFILES);
	private final BoundedProfileStore serverProfiles =
			new BoundedProfileStore(MAXIMUM_SERVER_PROFILES);
	private long revision;

	private ProfilesConfig() {
	}

	public static ProfilesConfig getInstance() {
		return Holder.INSTANCE;
	}

	static ProfilesConfig createForTest() {
		return new ProfilesConfig();
	}

	public synchronized boolean isProfilesEnabled() {
		return this.profilesEnabled;
	}

	public synchronized void setProfilesEnabled(boolean value) {
		this.profilesEnabled = value;
		this.revision++;
	}

	public synchronized boolean isRestoreGlobalDefaultsOnMenu() {
		return this.restoreGlobalDefaultsOnMenu;
	}

	public synchronized void setRestoreGlobalDefaultsOnMenu(boolean value) {
		this.restoreGlobalDefaultsOnMenu = value;
		this.revision++;
	}

	public synchronized boolean isSinglePlayerProfilesEnabled() {
		return this.singlePlayerProfilesEnabled;
	}

	public synchronized void setSinglePlayerProfilesEnabled(boolean value) {
		this.singlePlayerProfilesEnabled = value;
		this.revision++;
	}

	public synchronized boolean isSpecificServerProfilesEnabled() {
		return this.specificServerProfilesEnabled;
	}

	public synchronized void setSpecificServerProfilesEnabled(boolean value) {
		this.specificServerProfilesEnabled = value;
		this.revision++;
	}

	public synchronized boolean isGlobalDefaultsInitialized() {
		return this.globalDefaultsInitialized;
	}

	public synchronized long revision() {
		return this.revision;
	}

	public synchronized ProfileSettings getGlobalDefaults() {
		return this.globalDefaults;
	}

	public synchronized ProfileSettings getSinglePlayerTemplate() {
		return this.singlePlayerTemplate;
	}

	public synchronized ProfileSettings getServerTemplate() {
		return this.serverTemplate;
	}

	public synchronized void setGlobalRenderDistance(int value) {
		setGlobalDefaults(new ProfileSettings(
				value,
				this.globalDefaults.simulationDistance(),
				this.globalDefaults.entityDistancePercent(),
				this.globalDefaults.framerateLimit(),
				this.globalDefaults.particleMode()
		));
	}

	public synchronized void setGlobalSimulationDistance(int value) {
		setGlobalDefaults(new ProfileSettings(
				this.globalDefaults.renderDistance(),
				value,
				this.globalDefaults.entityDistancePercent(),
				this.globalDefaults.framerateLimit(),
				this.globalDefaults.particleMode()
		));
	}

	public synchronized void setGlobalEntityDistancePercent(int value) {
		setGlobalDefaults(new ProfileSettings(
				this.globalDefaults.renderDistance(),
				this.globalDefaults.simulationDistance(),
				value,
				this.globalDefaults.framerateLimit(),
				this.globalDefaults.particleMode()
		));
	}

	public synchronized void setGlobalFramerateLimit(int value) {
		setGlobalDefaults(new ProfileSettings(
				this.globalDefaults.renderDistance(),
				this.globalDefaults.simulationDistance(),
				this.globalDefaults.entityDistancePercent(),
				value,
				this.globalDefaults.particleMode()
		));
	}

	public synchronized void setGlobalParticleMode(ProfileParticleMode value) {
		setGlobalDefaults(new ProfileSettings(
				this.globalDefaults.renderDistance(),
				this.globalDefaults.simulationDistance(),
				this.globalDefaults.entityDistancePercent(),
				this.globalDefaults.framerateLimit(),
				value
		));
	}

	public synchronized void setSinglePlayerRenderDistance(int value) {
		setSinglePlayerTemplate(new ProfileSettings(
				value,
				this.singlePlayerTemplate.simulationDistance(),
				this.singlePlayerTemplate.entityDistancePercent(),
				this.singlePlayerTemplate.framerateLimit(),
				this.singlePlayerTemplate.particleMode()
		));
	}

	public synchronized void setSinglePlayerSimulationDistance(int value) {
		setSinglePlayerTemplate(new ProfileSettings(
				this.singlePlayerTemplate.renderDistance(),
				value,
				this.singlePlayerTemplate.entityDistancePercent(),
				this.singlePlayerTemplate.framerateLimit(),
				this.singlePlayerTemplate.particleMode()
		));
	}

	public synchronized void setSinglePlayerEntityDistancePercent(int value) {
		setSinglePlayerTemplate(new ProfileSettings(
				this.singlePlayerTemplate.renderDistance(),
				this.singlePlayerTemplate.simulationDistance(),
				value,
				this.singlePlayerTemplate.framerateLimit(),
				this.singlePlayerTemplate.particleMode()
		));
	}

	public synchronized void setSinglePlayerFramerateLimit(int value) {
		setSinglePlayerTemplate(new ProfileSettings(
				this.singlePlayerTemplate.renderDistance(),
				this.singlePlayerTemplate.simulationDistance(),
				this.singlePlayerTemplate.entityDistancePercent(),
				value,
				this.singlePlayerTemplate.particleMode()
		));
	}

	public synchronized void setSinglePlayerParticleMode(ProfileParticleMode value) {
		setSinglePlayerTemplate(new ProfileSettings(
				this.singlePlayerTemplate.renderDistance(),
				this.singlePlayerTemplate.simulationDistance(),
				this.singlePlayerTemplate.entityDistancePercent(),
				this.singlePlayerTemplate.framerateLimit(),
				value
		));
	}

	public synchronized void setServerRenderDistance(int value) {
		setServerTemplate(new ProfileSettings(
				value,
				this.serverTemplate.simulationDistance(),
				this.serverTemplate.entityDistancePercent(),
				this.serverTemplate.framerateLimit(),
				this.serverTemplate.particleMode()
		));
	}

	public synchronized void setServerSimulationDistance(int value) {
		setServerTemplate(new ProfileSettings(
				this.serverTemplate.renderDistance(),
				value,
				this.serverTemplate.entityDistancePercent(),
				this.serverTemplate.framerateLimit(),
				this.serverTemplate.particleMode()
		));
	}

	public synchronized void setServerEntityDistancePercent(int value) {
		setServerTemplate(new ProfileSettings(
				this.serverTemplate.renderDistance(),
				this.serverTemplate.simulationDistance(),
				value,
				this.serverTemplate.framerateLimit(),
				this.serverTemplate.particleMode()
		));
	}

	public synchronized void setServerFramerateLimit(int value) {
		setServerTemplate(new ProfileSettings(
				this.serverTemplate.renderDistance(),
				this.serverTemplate.simulationDistance(),
				this.serverTemplate.entityDistancePercent(),
				value,
				this.serverTemplate.particleMode()
		));
	}

	public synchronized void setServerParticleMode(ProfileParticleMode value) {
		setServerTemplate(new ProfileSettings(
				this.serverTemplate.renderDistance(),
				this.serverTemplate.simulationDistance(),
				this.serverTemplate.entityDistancePercent(),
				this.serverTemplate.framerateLimit(),
				value
		));
	}

	public synchronized boolean initializeGlobalDefaults(ProfileSettings captured) {
		if (this.globalDefaultsInitialized) {
			return false;
		}
		this.globalDefaults = captured.sanitized();
		this.globalDefaultsInitialized = true;
		this.revision++;
		return true;
	}

	public synchronized void rebaseGlobalDefaults(ProfileSettings settings) {
		this.globalDefaults = settings.sanitized();
		this.globalDefaultsInitialized = true;
		this.revision++;
	}

	public synchronized byte[] identitySalt() {
		return this.identitySalt.clone();
	}

	public synchronized ProfileSettings resolveSinglePlayerProfile(String key) {
		ProfileSettings stored = this.singlePlayerProfiles.get(key);
		if (stored != null) {
			return stored;
		}
		this.singlePlayerProfiles.put(key, this.singlePlayerTemplate);
		this.revision++;
		return this.singlePlayerTemplate;
	}

	public synchronized ProfileSettings resolveServerProfile(String key) {
		ProfileSettings stored = this.serverProfiles.get(key);
		if (stored != null) {
			return stored;
		}
		this.serverProfiles.put(key, this.serverTemplate);
		this.revision++;
		return this.serverTemplate;
	}

	public synchronized void storeSinglePlayerProfile(String key, ProfileSettings settings) {
		this.singlePlayerProfiles.put(key, settings);
		this.revision++;
	}

	public synchronized void storeServerProfile(String key, ProfileSettings settings) {
		this.serverProfiles.put(key, settings);
		this.revision++;
	}

	public synchronized boolean forgetSinglePlayerProfile(String key) {
		boolean removed = this.singlePlayerProfiles.remove(key);
		if (removed) {
			this.revision++;
		}
		return removed;
	}

	public synchronized boolean forgetServerProfile(String key) {
		boolean removed = this.serverProfiles.remove(key);
		if (removed) {
			this.revision++;
		}
		return removed;
	}

	public synchronized int singlePlayerProfileCount() {
		return this.singlePlayerProfiles.size();
	}

	public synchronized int serverProfileCount() {
		return this.serverProfiles.size();
	}

	public synchronized void resetToFactoryDefaults() {
		long nextRevision = this.revision == Long.MAX_VALUE
				? Long.MAX_VALUE : this.revision + 1L;
		this.profilesEnabled = false;
		this.restoreGlobalDefaultsOnMenu = true;
		this.singlePlayerProfilesEnabled = true;
		this.specificServerProfilesEnabled = true;
		this.globalDefaultsInitialized = false;
		this.identitySalt = newSalt();
		this.globalDefaults = ProfileSettings.globalDefaults();
		this.singlePlayerTemplate = ProfileSettings.singlePlayerDefaults();
		this.serverTemplate = ProfileSettings.serverDefaults();
		this.singlePlayerProfiles.clear();
		this.serverProfiles.clear();
		this.revision = nextRevision;
	}

	public synchronized void save() {
		saveChecked();
	}

	public synchronized boolean saveChecked() {
		return save(configPath());
	}

	synchronized boolean save(Path path) {
		try {
			ShieldJsonFile.writeObject(
					path,
					toJson(),
					MAXIMUM_CONFIG_BYTES,
					"sodium-volt-profiles-"
			);
			return true;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			if (SAVE_FAILURE_LOGGED.compareAndSet(false, true)) {
				SodiumVolt.LOGGER.warn("Could not save Sodium Volt Profiles configuration");
			}
			return false;
		}
	}

	static ProfilesConfig load(Path path) {
		ProfilesConfig config = new ProfilesConfig();
		try {
			JsonObject root = ShieldJsonFile.readObject(path, MAXIMUM_CONFIG_BYTES);
			if (root == null) {
				return config;
			}
			ShieldJsonFile.requireExactKeys(root, ROOT_KEYS);
			if (ShieldJsonFile.requiredInteger(root, "version") != CONFIG_VERSION) {
				throw new IllegalArgumentException("Unsupported profiles config version");
			}
			config.profilesEnabled = ShieldJsonFile.requiredBoolean(
					root, "profiles_enabled"
			);
			config.restoreGlobalDefaultsOnMenu = ShieldJsonFile.requiredBoolean(
					root, "restore_global_defaults_on_menu"
			);
			config.singlePlayerProfilesEnabled = ShieldJsonFile.requiredBoolean(
					root, "single_player_profiles_enabled"
			);
			config.specificServerProfilesEnabled = ShieldJsonFile.requiredBoolean(
					root, "specific_server_profiles_enabled"
			);
			config.globalDefaultsInitialized = ShieldJsonFile.requiredBoolean(
					root, "global_defaults_initialized"
			);
			config.identitySalt = ProfileIdentity.parseSalt(
					ShieldJsonFile.requiredString(root, "identity_salt", 32)
			);
			config.globalDefaults = readSettings(root, "global_defaults");
			config.singlePlayerTemplate = readSettings(root, "single_player_template");
			config.serverTemplate = readSettings(root, "server_template");
			readRecords(root, "single_player_profiles", config.singlePlayerProfiles,
					MAXIMUM_SINGLE_PLAYER_PROFILES);
			readRecords(root, "server_profiles", config.serverProfiles,
					MAXIMUM_SERVER_PROFILES);
			return config;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			if (LOAD_FAILURE_LOGGED.compareAndSet(false, true)) {
				SodiumVolt.LOGGER.warn("Ignoring invalid Sodium Volt Profiles configuration");
			}
			return new ProfilesConfig();
		}
	}

	synchronized JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("version", CONFIG_VERSION);
		root.addProperty("profiles_enabled", this.profilesEnabled);
		root.addProperty(
				"restore_global_defaults_on_menu", this.restoreGlobalDefaultsOnMenu
		);
		root.addProperty(
				"single_player_profiles_enabled", this.singlePlayerProfilesEnabled
		);
		root.addProperty(
				"specific_server_profiles_enabled", this.specificServerProfilesEnabled
		);
		root.addProperty("global_defaults_initialized", this.globalDefaultsInitialized);
		root.addProperty("identity_salt", ProfileIdentity.formatSalt(this.identitySalt));
		root.add("global_defaults", settingsJson(this.globalDefaults));
		root.add("single_player_template", settingsJson(this.singlePlayerTemplate));
		root.add("server_template", settingsJson(this.serverTemplate));
		root.add("single_player_profiles", recordsJson(this.singlePlayerProfiles.snapshot()));
		root.add("server_profiles", recordsJson(this.serverProfiles.snapshot()));
		return root;
	}

	private synchronized void setGlobalDefaults(ProfileSettings settings) {
		this.globalDefaults = settings.sanitized();
		this.globalDefaultsInitialized = true;
		this.revision++;
	}

	private synchronized void setSinglePlayerTemplate(ProfileSettings settings) {
		this.singlePlayerTemplate = settings.sanitized();
		this.revision++;
	}

	private synchronized void setServerTemplate(ProfileSettings settings) {
		this.serverTemplate = settings.sanitized();
		this.revision++;
	}

	private static ProfileSettings readSettings(JsonObject root, String key) {
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonObject()) {
			throw new IllegalArgumentException("Missing profile settings");
		}
		JsonObject object = element.getAsJsonObject();
		ShieldJsonFile.requireExactKeys(object, SETTINGS_KEYS);
		ProfileSettings settings = new ProfileSettings(
				ShieldJsonFile.requiredInteger(object, "render_distance"),
				ShieldJsonFile.requiredInteger(object, "simulation_distance"),
				ShieldJsonFile.requiredInteger(object, "entity_distance_percent"),
				ShieldJsonFile.requiredInteger(object, "framerate_limit"),
				ProfileParticleMode.parse(ShieldJsonFile.requiredString(
						object, "particle_mode", 16
				))
		);
		if (!settings.isSanitized()) {
			throw new IllegalArgumentException("Out-of-range profile settings");
		}
		return settings;
	}

	private static void readRecords(
			JsonObject root,
			String key,
			BoundedProfileStore destination,
			int maximumRecords
	) {
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonObject()) {
			throw new IllegalArgumentException("Missing profile record collection");
		}
		JsonObject records = element.getAsJsonObject();
		if (records.size() > maximumRecords) {
			throw new IllegalArgumentException("Too many profile records");
		}
		for (Map.Entry<String, JsonElement> entry : records.entrySet()) {
			if (!ProfileIdentity.isValidStoredKey(entry.getKey())
					|| !entry.getValue().isJsonObject()) {
				throw new IllegalArgumentException("Invalid profile record");
			}
			JsonObject wrapper = new JsonObject();
			wrapper.add("settings", entry.getValue());
			destination.put(entry.getKey(), readSettings(wrapper, "settings"));
		}
	}

	private static JsonObject recordsJson(Map<String, ProfileSettings> records) {
		JsonObject object = new JsonObject();
		for (Map.Entry<String, ProfileSettings> entry : records.entrySet()) {
			object.add(entry.getKey(), settingsJson(entry.getValue()));
		}
		return object;
	}

	private static JsonObject settingsJson(ProfileSettings settings) {
		JsonObject object = new JsonObject();
		object.addProperty("render_distance", settings.renderDistance());
		object.addProperty("simulation_distance", settings.simulationDistance());
		object.addProperty("entity_distance_percent", settings.entityDistancePercent());
		object.addProperty("framerate_limit", settings.framerateLimit());
		object.addProperty("particle_mode", settings.particleMode().serializedName());
		return object;
	}

	private static byte[] newSalt() {
		byte[] salt = new byte[ProfileIdentity.SALT_BYTES];
		new SecureRandom().nextBytes(salt);
		return salt;
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir()
				.resolve("sodium-volt-profiles.json");
	}

	private static final class Holder {
		private static final ProfilesConfig INSTANCE = ProfilesConfig.load(configPath());
	}
}
