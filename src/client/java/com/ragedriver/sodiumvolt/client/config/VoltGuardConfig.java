package com.ragedriver.sodiumvolt.client.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.ragedriver.sodiumvolt.SodiumVolt;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class VoltGuardConfig {
	public static final int TARGET_FPS_MIN = 30;
	public static final int TARGET_FPS_MAX = 240;
	public static final int TARGET_FPS_STEP = 5;
	public static final int TARGET_FPS_DEFAULT = 60;

	public static final int PARTICLE_BUDGET_MIN = 256;
	public static final int PARTICLE_BUDGET_MAX = 16_384;
	public static final int PARTICLE_BUDGET_STEP = 256;
	public static final int PARTICLE_BUDGET_DEFAULT = 4_096;

	public static final int BLOCK_ENTITY_BUDGET_MIN = 32;
	public static final int BLOCK_ENTITY_BUDGET_MAX = 2_048;
	public static final int BLOCK_ENTITY_BUDGET_STEP = 32;
	public static final int BLOCK_ENTITY_BUDGET_DEFAULT = 256;

	public static final int DISPLAY_ENTITY_BUDGET_MIN = 16;
	public static final int DISPLAY_ENTITY_BUDGET_MAX = 1_024;
	public static final int DISPLAY_ENTITY_BUDGET_STEP = 16;
	public static final int DISPLAY_ENTITY_BUDGET_DEFAULT = 128;

	private static final int CONFIG_VERSION = 1;
	private static final long MAX_CONFIG_SIZE_BYTES = 1024L * 1024L;
	private static final Gson GSON = new GsonBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.setPrettyPrinting()
			.create();

	private final int version = CONFIG_VERSION;
	private volatile boolean voltGuardEnabled;
	private volatile boolean adaptiveWorkloadControl = true;
	private volatile boolean prioritizeVisibleEffects = true;
	private volatile boolean preserveGameplayCriticalEffects = true;
	private volatile boolean showProtectionNotifications = true;
	private volatile int targetFps = TARGET_FPS_DEFAULT;
	private volatile int particleRenderBudget = PARTICLE_BUDGET_DEFAULT;
	private volatile int blockEntityRenderBudget = BLOCK_ENTITY_BUDGET_DEFAULT;
	private volatile int displayEntityRenderBudget = DISPLAY_ENTITY_BUDGET_DEFAULT;

	private VoltGuardConfig() {
	}

	public static VoltGuardConfig getInstance() {
		return Holder.INSTANCE;
	}

	static VoltGuardConfig createForTest() {
		return new VoltGuardConfig();
	}

	public boolean isVoltGuardEnabled() {
		return this.voltGuardEnabled;
	}

	public void setVoltGuardEnabled(boolean enabled) {
		this.voltGuardEnabled = enabled;
	}

	public boolean isAdaptiveWorkloadControl() {
		return this.adaptiveWorkloadControl;
	}

	public void setAdaptiveWorkloadControl(boolean enabled) {
		this.adaptiveWorkloadControl = enabled;
	}

	public boolean isPrioritizeVisibleEffects() {
		return this.prioritizeVisibleEffects;
	}

	public void setPrioritizeVisibleEffects(boolean enabled) {
		this.prioritizeVisibleEffects = enabled;
	}

	public boolean isPreserveGameplayCriticalEffects() {
		return this.preserveGameplayCriticalEffects;
	}

	public void setPreserveGameplayCriticalEffects(boolean enabled) {
		this.preserveGameplayCriticalEffects = enabled;
	}

	public boolean isShowProtectionNotifications() {
		return this.showProtectionNotifications;
	}

	public void setShowProtectionNotifications(boolean enabled) {
		this.showProtectionNotifications = enabled;
	}

	public int getTargetFps() {
		return this.targetFps;
	}

	public void setTargetFps(int targetFps) {
		this.targetFps = clampToStep(targetFps, TARGET_FPS_MIN, TARGET_FPS_MAX, TARGET_FPS_STEP);
	}

	public int getParticleRenderBudget() {
		return this.particleRenderBudget;
	}

	public void setParticleRenderBudget(int particleRenderBudget) {
		this.particleRenderBudget = clampToStep(
				particleRenderBudget,
				PARTICLE_BUDGET_MIN,
				PARTICLE_BUDGET_MAX,
				PARTICLE_BUDGET_STEP
		);
	}

	public int getBlockEntityRenderBudget() {
		return this.blockEntityRenderBudget;
	}

	public void setBlockEntityRenderBudget(int blockEntityRenderBudget) {
		this.blockEntityRenderBudget = clampToStep(
				blockEntityRenderBudget,
				BLOCK_ENTITY_BUDGET_MIN,
				BLOCK_ENTITY_BUDGET_MAX,
				BLOCK_ENTITY_BUDGET_STEP
		);
	}

	public int getDisplayEntityRenderBudget() {
		return this.displayEntityRenderBudget;
	}

	public void setDisplayEntityRenderBudget(int displayEntityRenderBudget) {
		this.displayEntityRenderBudget = clampToStep(
				displayEntityRenderBudget,
				DISPLAY_ENTITY_BUDGET_MIN,
				DISPLAY_ENTITY_BUDGET_MAX,
				DISPLAY_ENTITY_BUDGET_STEP
		);
	}

	public synchronized void resetToFactoryDefaults() {
		ConfigFactoryDefaults.copyMutableFields(this, new VoltGuardConfig());
	}

	public synchronized void save() {
		saveChecked();
	}

	public synchronized boolean saveChecked() {
		this.validate();

		Path path = configPath();
		Path temporaryPath = null;
		boolean saved = false;
		try {
			Path configDirectory = path.getParent();
			Files.createDirectories(configDirectory);
			temporaryPath = Files.createTempFile(configDirectory, "sodium-volt-", ".tmp");
			Files.writeString(
					temporaryPath,
					GSON.toJson(this),
					StandardCharsets.UTF_8,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE
			);
			moveIntoPlace(temporaryPath, path);
			temporaryPath = null;
			saved = true;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			SodiumVolt.LOGGER.error("Could not save Sodium Volt configuration to {}", path, exception);
		} finally {
			if (temporaryPath != null) {
				try {
					Files.deleteIfExists(temporaryPath);
				} catch (IOException | SecurityException cleanupException) {
					SodiumVolt.LOGGER.warn(
							"Could not clean up temporary Sodium Volt configuration file {}",
							temporaryPath,
							cleanupException
					);
				}
			}
		}
		return saved;
	}

	private static VoltGuardConfig load() {
		VoltGuardConfig config = new VoltGuardConfig();
		Path path = configPath();

		try {
			if (!Files.exists(path)) {
				return config;
			}

			if (!Files.isRegularFile(path)) {
				SodiumVolt.LOGGER.warn(
						"Ignoring Sodium Volt configuration because it is not a regular file: {}",
						path
				);
				return config;
			}

			if (Files.size(path) > MAX_CONFIG_SIZE_BYTES) {
				SodiumVolt.LOGGER.warn(
						"Ignoring Sodium Volt configuration larger than {} bytes: {}",
						MAX_CONFIG_SIZE_BYTES,
						path
				);
				return config;
			}

			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonElement rootElement = JsonParser.parseReader(reader);
				if (!rootElement.isJsonObject()) {
					throw new IllegalArgumentException("Configuration root must be a JSON object");
				}

				JsonObject root = rootElement.getAsJsonObject();
				config.voltGuardEnabled = readBoolean(root, "volt_guard_enabled", config.voltGuardEnabled);
				config.adaptiveWorkloadControl = readBoolean(
						root,
						"adaptive_workload_control",
						config.adaptiveWorkloadControl
				);
				config.prioritizeVisibleEffects = readBoolean(
						root,
						"prioritize_visible_effects",
						config.prioritizeVisibleEffects
				);
				config.preserveGameplayCriticalEffects = readBoolean(
						root,
						"preserve_gameplay_critical_effects",
						config.preserveGameplayCriticalEffects
				);
				config.showProtectionNotifications = readBoolean(
						root,
						"show_protection_notifications",
						config.showProtectionNotifications
				);
				config.targetFps = readInteger(root, "target_fps", config.targetFps);
				config.particleRenderBudget = readInteger(
						root,
						"particle_render_budget",
						config.particleRenderBudget
				);
				config.blockEntityRenderBudget = readInteger(
						root,
						"block_entity_render_budget",
						config.blockEntityRenderBudget
				);
				config.displayEntityRenderBudget = readInteger(
						root,
						"display_entity_render_budget",
						config.displayEntityRenderBudget
				);
			}

			config.validate();
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			SodiumVolt.LOGGER.error(
					"Could not load Sodium Volt configuration from {}; using safe defaults",
					path,
					exception
			);
			return new VoltGuardConfig();
		}

		return config;
	}

	private static boolean readBoolean(JsonObject root, String key, boolean defaultValue) {
		JsonPrimitive primitive = getPrimitive(root, key);
		return primitive != null && primitive.isBoolean() ? primitive.getAsBoolean() : defaultValue;
	}

	private static int readInteger(JsonObject root, String key, int defaultValue) {
		JsonPrimitive primitive = getPrimitive(root, key);
		if (primitive == null || !primitive.isNumber()) {
			return defaultValue;
		}

		try {
			BigDecimal value = primitive.getAsBigDecimal();
			return value.intValueExact();
		} catch (ArithmeticException | NumberFormatException exception) {
			return defaultValue;
		}
	}

	private static JsonPrimitive getPrimitive(JsonObject root, String key) {
		JsonElement element = root.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsJsonPrimitive() : null;
	}

	private void validate() {
		this.targetFps = clampToStep(
				this.targetFps,
				TARGET_FPS_MIN,
				TARGET_FPS_MAX,
				TARGET_FPS_STEP
		);
		this.particleRenderBudget = clampToStep(
				this.particleRenderBudget,
				PARTICLE_BUDGET_MIN,
				PARTICLE_BUDGET_MAX,
				PARTICLE_BUDGET_STEP
		);
		this.blockEntityRenderBudget = clampToStep(
				this.blockEntityRenderBudget,
				BLOCK_ENTITY_BUDGET_MIN,
				BLOCK_ENTITY_BUDGET_MAX,
				BLOCK_ENTITY_BUDGET_STEP
		);
		this.displayEntityRenderBudget = clampToStep(
				this.displayEntityRenderBudget,
				DISPLAY_ENTITY_BUDGET_MIN,
				DISPLAY_ENTITY_BUDGET_MAX,
				DISPLAY_ENTITY_BUDGET_STEP
		);
	}

	private static int clampToStep(int value, int minimum, int maximum, int step) {
		int clampedValue = Math.max(minimum, Math.min(maximum, value));
		int stepsFromMinimum = (clampedValue - minimum + (step / 2)) / step;
		return Math.min(maximum, minimum + (stepsFromMinimum * step));
	}

	private static void moveIntoPlace(Path source, Path destination) throws IOException {
		try {
			Files.move(
					source,
					destination,
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING
			);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("sodium-volt.json");
	}

	private static final class Holder {
		private static final VoltGuardConfig INSTANCE = load();
	}
}
