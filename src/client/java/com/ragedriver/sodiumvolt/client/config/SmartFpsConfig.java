package com.ragedriver.sodiumvolt.client.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.smartfps.SmartFpsConfigNormalization;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class SmartFpsConfig {
	public static final int MINIMIZED_TARGET_MIN = 5;
	public static final int MINIMIZED_TARGET_MAX = 60;
	public static final int MINIMIZED_TARGET_DEFAULT = 15;
	public static final int UNFOCUSED_TARGET_MIN = 10;
	public static final int UNFOCUSED_TARGET_MAX = 120;
	public static final int UNFOCUSED_TARGET_STEP = 5;
	public static final int UNFOCUSED_TARGET_DEFAULT = 30;
	public static final int BACKGROUND_DELAY_MIN = 0;
	public static final int BACKGROUND_DELAY_MAX = 10;
	public static final int BACKGROUND_DELAY_DEFAULT = 2;
	public static final int BATTERY_TARGET_MIN = 15;
	public static final int BATTERY_TARGET_MAX = 120;
	public static final int BATTERY_TARGET_STEP = 5;
	public static final int BATTERY_TARGET_DEFAULT = 45;
	public static final int LOW_BATTERY_THRESHOLD_MIN = 10;
	public static final int LOW_BATTERY_THRESHOLD_MAX = 50;
	public static final int LOW_BATTERY_THRESHOLD_STEP = 5;
	public static final int LOW_BATTERY_THRESHOLD_DEFAULT = 25;
	public static final int LOW_BATTERY_TARGET_MIN = 5;
	public static final int LOW_BATTERY_TARGET_MAX = 60;
	public static final int LOW_BATTERY_TARGET_STEP = 5;
	public static final int LOW_BATTERY_TARGET_DEFAULT = 20;
	public static final int POWER_POLL_INTERVAL_MIN = 5;
	public static final int POWER_POLL_INTERVAL_MAX = 60;
	public static final int POWER_POLL_INTERVAL_STEP = 5;
	public static final int POWER_POLL_INTERVAL_DEFAULT = 15;

	private static final int CONFIG_VERSION = 1;
	private static final long MAX_CONFIG_SIZE_BYTES = 1024L * 1024L;
	private static final Gson GSON = new GsonBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.setPrettyPrinting()
			.create();

	private final int version = CONFIG_VERSION;
	private volatile boolean smartFpsEnabled;
	private volatile int minimizedTargetFps = MINIMIZED_TARGET_DEFAULT;
	private volatile boolean throttleWhenMinimized = true;
	private volatile boolean throttleWhenUnfocused = true;
	private volatile int unfocusedTargetFps = UNFOCUSED_TARGET_DEFAULT;
	private volatile int backgroundActivationDelaySeconds = BACKGROUND_DELAY_DEFAULT;
	private volatile boolean batteryMode = true;
	private volatile int batteryTargetFps = BATTERY_TARGET_DEFAULT;
	private volatile boolean bypassBatteryLimitWhileCharging = true;
	private volatile boolean lowBatteryProtection = true;
	private volatile int lowBatteryThresholdPercent = LOW_BATTERY_THRESHOLD_DEFAULT;
	private volatile int lowBatteryTargetFps = LOW_BATTERY_TARGET_DEFAULT;
	private volatile int powerPollIntervalSeconds = POWER_POLL_INTERVAL_DEFAULT;
	private volatile boolean showStatusNotifications = true;
	private volatile boolean showInspectorStatistics = true;

	private SmartFpsConfig() {
	}

	public static SmartFpsConfig getInstance() {
		return Holder.INSTANCE;
	}

	static SmartFpsConfig createForTest() {
		return new SmartFpsConfig();
	}

	public boolean isSmartFpsEnabled() {
		return this.smartFpsEnabled;
	}

	public void setSmartFpsEnabled(boolean value) {
		this.smartFpsEnabled = value;
	}

	public int getMinimizedTargetFps() {
		return this.minimizedTargetFps;
	}

	public void setMinimizedTargetFps(int value) {
		this.minimizedTargetFps = SmartFpsConfigNormalization.clamp(
				value, MINIMIZED_TARGET_MIN, MINIMIZED_TARGET_MAX
		);
	}

	public boolean isThrottleWhenMinimized() {
		return this.throttleWhenMinimized;
	}

	public void setThrottleWhenMinimized(boolean value) {
		this.throttleWhenMinimized = value;
	}

	public boolean isThrottleWhenUnfocused() {
		return this.throttleWhenUnfocused;
	}

	public void setThrottleWhenUnfocused(boolean value) {
		this.throttleWhenUnfocused = value;
	}

	public int getUnfocusedTargetFps() {
		return this.unfocusedTargetFps;
	}

	public void setUnfocusedTargetFps(int value) {
		this.unfocusedTargetFps = SmartFpsConfigNormalization.clampStep(
				value, UNFOCUSED_TARGET_MIN, UNFOCUSED_TARGET_MAX, UNFOCUSED_TARGET_STEP
		);
	}

	public int getBackgroundActivationDelaySeconds() {
		return this.backgroundActivationDelaySeconds;
	}

	public void setBackgroundActivationDelaySeconds(int value) {
		this.backgroundActivationDelaySeconds = SmartFpsConfigNormalization.clamp(
				value, BACKGROUND_DELAY_MIN, BACKGROUND_DELAY_MAX
		);
	}

	public boolean isBatteryMode() {
		return this.batteryMode;
	}

	public void setBatteryMode(boolean value) {
		this.batteryMode = value;
	}

	public int getBatteryTargetFps() {
		return this.batteryTargetFps;
	}

	public void setBatteryTargetFps(int value) {
		this.batteryTargetFps = SmartFpsConfigNormalization.clampStep(
				value, BATTERY_TARGET_MIN, BATTERY_TARGET_MAX, BATTERY_TARGET_STEP
		);
	}

	public boolean isBypassBatteryLimitWhileCharging() {
		return this.bypassBatteryLimitWhileCharging;
	}

	public void setBypassBatteryLimitWhileCharging(boolean value) {
		this.bypassBatteryLimitWhileCharging = value;
	}

	public boolean isLowBatteryProtection() {
		return this.lowBatteryProtection;
	}

	public void setLowBatteryProtection(boolean value) {
		this.lowBatteryProtection = value;
	}

	public int getLowBatteryThresholdPercent() {
		return this.lowBatteryThresholdPercent;
	}

	public void setLowBatteryThresholdPercent(int value) {
		this.lowBatteryThresholdPercent = SmartFpsConfigNormalization.clampStep(
				value,
				LOW_BATTERY_THRESHOLD_MIN,
				LOW_BATTERY_THRESHOLD_MAX,
				LOW_BATTERY_THRESHOLD_STEP
		);
	}

	public int getLowBatteryTargetFps() {
		return this.lowBatteryTargetFps;
	}

	public void setLowBatteryTargetFps(int value) {
		this.lowBatteryTargetFps = SmartFpsConfigNormalization.clampStep(
				value,
				LOW_BATTERY_TARGET_MIN,
				LOW_BATTERY_TARGET_MAX,
				LOW_BATTERY_TARGET_STEP
		);
	}

	public int getPowerPollIntervalSeconds() {
		return this.powerPollIntervalSeconds;
	}

	public void setPowerPollIntervalSeconds(int value) {
		this.powerPollIntervalSeconds = SmartFpsConfigNormalization.clampStep(
				value,
				POWER_POLL_INTERVAL_MIN,
				POWER_POLL_INTERVAL_MAX,
				POWER_POLL_INTERVAL_STEP
		);
	}

	public boolean isShowStatusNotifications() {
		return this.showStatusNotifications;
	}

	public void setShowStatusNotifications(boolean value) {
		this.showStatusNotifications = value;
	}

	public boolean isShowInspectorStatistics() {
		return this.showInspectorStatistics;
	}

	public void setShowInspectorStatistics(boolean value) {
		this.showInspectorStatistics = value;
	}

	public synchronized void resetToFactoryDefaults() {
		ConfigFactoryDefaults.copyMutableFields(this, new SmartFpsConfig());
	}

	public synchronized void save() {
		saveChecked();
	}

	public synchronized boolean saveChecked() {
		validate();
		Path path = configPath();
		Path temporaryPath = null;
		boolean saved = false;
		try {
			Path directory = path.getParent();
			Files.createDirectories(directory);
			temporaryPath = Files.createTempFile(directory, "sodium-volt-smart-fps-", ".tmp");
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
			SodiumVolt.LOGGER.error("Could not save Smart FPS configuration to {}", path, exception);
		} finally {
			if (temporaryPath != null) {
				try {
					Files.deleteIfExists(temporaryPath);
				} catch (IOException | SecurityException exception) {
					SodiumVolt.LOGGER.warn(
							"Could not remove temporary Smart FPS configuration {}",
							temporaryPath
					);
				}
			}
		}
		return saved;
	}

	private static SmartFpsConfig load() {
		SmartFpsConfig config = new SmartFpsConfig();
		Path path = configPath();
		try {
			if (!Files.exists(path)) {
				return config;
			}
			if (!Files.isRegularFile(path) || Files.size(path) > MAX_CONFIG_SIZE_BYTES) {
				SodiumVolt.LOGGER.warn("Ignoring invalid Smart FPS configuration {}", path);
				return config;
			}
			byte[] document;
			try (InputStream input = Files.newInputStream(path)) {
				document = input.readNBytes((int) MAX_CONFIG_SIZE_BYTES + 1);
			}
			if (document.length > MAX_CONFIG_SIZE_BYTES) {
				SodiumVolt.LOGGER.warn("Ignoring oversized Smart FPS configuration {}", path);
				return config;
			}
			try (Reader reader = new InputStreamReader(
					new ByteArrayInputStream(document),
					StandardCharsets.UTF_8
			)) {
				JsonElement element = JsonParser.parseReader(reader);
				if (!element.isJsonObject()) {
					throw new IllegalArgumentException("Configuration root must be an object");
				}
				JsonObject root = element.getAsJsonObject();
				int schemaVersion = readInteger(root, "version", CONFIG_VERSION);
				if (schemaVersion != CONFIG_VERSION) {
					throw new IllegalArgumentException(
							"Unsupported Smart FPS configuration version " + schemaVersion
					);
				}
				config.smartFpsEnabled = readBoolean(root, "smart_fps_enabled", config.smartFpsEnabled);
				config.minimizedTargetFps = readInteger(
						root, "minimized_target_fps", config.minimizedTargetFps
				);
				config.throttleWhenMinimized = readBoolean(
						root, "throttle_when_minimized", config.throttleWhenMinimized
				);
				config.throttleWhenUnfocused = readBoolean(
						root, "throttle_when_unfocused", config.throttleWhenUnfocused
				);
				config.unfocusedTargetFps = readInteger(
						root, "unfocused_target_fps", config.unfocusedTargetFps
				);
				config.backgroundActivationDelaySeconds = readInteger(
						root,
						"background_activation_delay_seconds",
						config.backgroundActivationDelaySeconds
				);
				config.batteryMode = readBoolean(root, "battery_mode", config.batteryMode);
				config.batteryTargetFps = readInteger(
						root, "battery_target_fps", config.batteryTargetFps
				);
				config.bypassBatteryLimitWhileCharging = readBoolean(
						root,
						"bypass_battery_limit_while_charging",
						config.bypassBatteryLimitWhileCharging
				);
				config.lowBatteryProtection = readBoolean(
						root, "low_battery_protection", config.lowBatteryProtection
				);
				config.lowBatteryThresholdPercent = readInteger(
						root, "low_battery_threshold_percent", config.lowBatteryThresholdPercent
				);
				config.lowBatteryTargetFps = readInteger(
						root, "low_battery_target_fps", config.lowBatteryTargetFps
				);
				config.powerPollIntervalSeconds = readInteger(
						root, "power_poll_interval_seconds", config.powerPollIntervalSeconds
				);
				config.showStatusNotifications = readBoolean(
						root, "show_status_notifications", config.showStatusNotifications
				);
				config.showInspectorStatistics = readBoolean(
						root, "show_inspector_statistics", config.showInspectorStatistics
				);
			}
			config.validate();
			return config;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			SodiumVolt.LOGGER.error(
					"Could not load Smart FPS configuration from {}; using safe defaults",
					path,
					exception
			);
			return new SmartFpsConfig();
		}
	}

	private static boolean readBoolean(JsonObject root, String key, boolean defaultValue) {
		JsonPrimitive primitive = primitive(root, key);
		return primitive != null && primitive.isBoolean() ? primitive.getAsBoolean() : defaultValue;
	}

	private static int readInteger(JsonObject root, String key, int defaultValue) {
		JsonPrimitive primitive = primitive(root, key);
		if (primitive == null || !primitive.isNumber()) {
			return defaultValue;
		}
		try {
			return primitive.getAsBigDecimal().intValueExact();
		} catch (ArithmeticException | NumberFormatException exception) {
			return defaultValue;
		}
	}

	private static JsonPrimitive primitive(JsonObject root, String key) {
		JsonElement element = root.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsJsonPrimitive() : null;
	}

	private void validate() {
		setMinimizedTargetFps(this.minimizedTargetFps);
		setUnfocusedTargetFps(this.unfocusedTargetFps);
		setBackgroundActivationDelaySeconds(this.backgroundActivationDelaySeconds);
		setBatteryTargetFps(this.batteryTargetFps);
		setLowBatteryThresholdPercent(this.lowBatteryThresholdPercent);
		setLowBatteryTargetFps(this.lowBatteryTargetFps);
		setPowerPollIntervalSeconds(this.powerPollIntervalSeconds);
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
		return FabricLoader.getInstance().getConfigDir()
				.resolve("sodium-volt-smart-fps.json");
	}

	private static final class Holder {
		private static final SmartFpsConfig INSTANCE = load();
	}
}
