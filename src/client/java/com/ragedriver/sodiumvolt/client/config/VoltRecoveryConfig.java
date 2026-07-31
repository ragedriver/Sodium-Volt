package com.ragedriver.sodiumvolt.client.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.recovery.RecoveryConfigNormalization;
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

public final class VoltRecoveryConfig {
	public static final int CRASH_STREAK_MIN = 1;
	public static final int CRASH_STREAK_MAX = 5;
	public static final int CRASH_STREAK_DEFAULT = 2;
	public static final int MAXIMUM_ATTEMPTS_MIN = 1;
	public static final int MAXIMUM_ATTEMPTS_MAX = 5;
	public static final int MAXIMUM_ATTEMPTS_DEFAULT = 3;
	public static final int SAFE_RENDER_DISTANCE_MIN = 4;
	public static final int SAFE_RENDER_DISTANCE_MAX = 16;
	public static final int SAFE_RENDER_DISTANCE_DEFAULT = 8;
	public static final int SAFE_ENTITY_DISTANCE_MIN = 25;
	public static final int SAFE_ENTITY_DISTANCE_MAX = 100;
	public static final int SAFE_ENTITY_DISTANCE_STEP = 5;
	public static final int SAFE_ENTITY_DISTANCE_DEFAULT = 50;
	public static final int RECOVERY_FPS_MIN = 30;
	public static final int RECOVERY_FPS_MAX = 120;
	public static final int RECOVERY_FPS_STEP = 5;
	public static final int RECOVERY_FPS_DEFAULT = 60;
	public static final int STABLE_DURATION_MIN = 30;
	public static final int STABLE_DURATION_MAX = 300;
	public static final int STABLE_DURATION_STEP = 30;
	public static final int STABLE_DURATION_DEFAULT = 120;

	private static final int CONFIG_VERSION = 1;
	private static final int MAX_CONFIG_SIZE_BYTES = 1024 * 1024;
	private static final Gson GSON = new GsonBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.setPrettyPrinting()
			.create();

	private final int version = CONFIG_VERSION;
	private volatile boolean voltRecoveryEnabled;
	private volatile boolean detectUncleanSessions = true;
	private volatile boolean automaticSafeMode = true;
	private volatile boolean forceSafeModeNextLaunch;
	private volatile int crashStreakThreshold = CRASH_STREAK_DEFAULT;
	private volatile int maximumRecoveryAttempts = MAXIMUM_ATTEMPTS_DEFAULT;
	private volatile boolean applySafeGraphicsProfile = true;
	private volatile int safeRenderDistance = SAFE_RENDER_DISTANCE_DEFAULT;
	private volatile int safeEntityDistancePercent = SAFE_ENTITY_DISTANCE_DEFAULT;
	private volatile boolean reduceExpensiveGraphics = true;
	private volatile boolean limitFpsDuringRecovery = true;
	private volatile int recoveryFpsCap = RECOVERY_FPS_DEFAULT;
	private volatile boolean suspendAdaptiveController = true;
	private volatile boolean restoreOwnedSettingsAfterStableSession = true;
	private volatile int stableSessionDurationSeconds = STABLE_DURATION_DEFAULT;
	private volatile boolean showRecoveryNotifications = true;
	private volatile boolean writeSanitizedLocalRecoveryReport = true;
	private volatile boolean showRecoveryStatsInInspector = true;
	private transient volatile boolean wasEverEnabled;

	private VoltRecoveryConfig() {
	}

	public static VoltRecoveryConfig getInstance() {
		return Holder.INSTANCE;
	}

	static VoltRecoveryConfig createForTest() {
		return new VoltRecoveryConfig();
	}

	public boolean isVoltRecoveryEnabled() {
		return this.voltRecoveryEnabled;
	}

	public void setVoltRecoveryEnabled(boolean value) {
		this.voltRecoveryEnabled = value;
		this.wasEverEnabled |= value;
	}

	public boolean isDetectUncleanSessions() {
		return this.detectUncleanSessions;
	}

	public void setDetectUncleanSessions(boolean value) {
		this.detectUncleanSessions = value;
	}

	public boolean isAutomaticSafeMode() {
		return this.automaticSafeMode;
	}

	public void setAutomaticSafeMode(boolean value) {
		this.automaticSafeMode = value;
	}

	public boolean isForceSafeModeNextLaunch() {
		return this.forceSafeModeNextLaunch;
	}

	public void setForceSafeModeNextLaunch(boolean value) {
		this.forceSafeModeNextLaunch = value;
	}

	public int getCrashStreakThreshold() {
		return this.crashStreakThreshold;
	}

	public void setCrashStreakThreshold(int value) {
		this.crashStreakThreshold = RecoveryConfigNormalization.clamp(
				value, CRASH_STREAK_MIN, CRASH_STREAK_MAX
		);
	}

	public int getMaximumRecoveryAttempts() {
		return this.maximumRecoveryAttempts;
	}

	public void setMaximumRecoveryAttempts(int value) {
		this.maximumRecoveryAttempts = RecoveryConfigNormalization.clamp(
				value, MAXIMUM_ATTEMPTS_MIN, MAXIMUM_ATTEMPTS_MAX
		);
	}

	public boolean isApplySafeGraphicsProfile() {
		return this.applySafeGraphicsProfile;
	}

	public void setApplySafeGraphicsProfile(boolean value) {
		this.applySafeGraphicsProfile = value;
	}

	public int getSafeRenderDistance() {
		return this.safeRenderDistance;
	}

	public void setSafeRenderDistance(int value) {
		this.safeRenderDistance = RecoveryConfigNormalization.clamp(
				value, SAFE_RENDER_DISTANCE_MIN, SAFE_RENDER_DISTANCE_MAX
		);
	}

	public int getSafeEntityDistancePercent() {
		return this.safeEntityDistancePercent;
	}

	public void setSafeEntityDistancePercent(int value) {
		this.safeEntityDistancePercent = RecoveryConfigNormalization.clampStep(
				value,
				SAFE_ENTITY_DISTANCE_MIN,
				SAFE_ENTITY_DISTANCE_MAX,
				SAFE_ENTITY_DISTANCE_STEP
		);
	}

	public boolean isReduceExpensiveGraphics() {
		return this.reduceExpensiveGraphics;
	}

	public void setReduceExpensiveGraphics(boolean value) {
		this.reduceExpensiveGraphics = value;
	}

	public boolean isLimitFpsDuringRecovery() {
		return this.limitFpsDuringRecovery;
	}

	public void setLimitFpsDuringRecovery(boolean value) {
		this.limitFpsDuringRecovery = value;
	}

	public int getRecoveryFpsCap() {
		return this.recoveryFpsCap;
	}

	public void setRecoveryFpsCap(int value) {
		this.recoveryFpsCap = RecoveryConfigNormalization.clampStep(
				value, RECOVERY_FPS_MIN, RECOVERY_FPS_MAX, RECOVERY_FPS_STEP
		);
	}

	public boolean isSuspendAdaptiveController() {
		return this.suspendAdaptiveController;
	}

	public void setSuspendAdaptiveController(boolean value) {
		this.suspendAdaptiveController = value;
	}

	public boolean isRestoreOwnedSettingsAfterStableSession() {
		return this.restoreOwnedSettingsAfterStableSession;
	}

	public void setRestoreOwnedSettingsAfterStableSession(boolean value) {
		this.restoreOwnedSettingsAfterStableSession = value;
	}

	public int getStableSessionDurationSeconds() {
		return this.stableSessionDurationSeconds;
	}

	public void setStableSessionDurationSeconds(int value) {
		this.stableSessionDurationSeconds = RecoveryConfigNormalization.clampStep(
				value, STABLE_DURATION_MIN, STABLE_DURATION_MAX, STABLE_DURATION_STEP
		);
	}

	public boolean isShowRecoveryNotifications() {
		return this.showRecoveryNotifications;
	}

	public void setShowRecoveryNotifications(boolean value) {
		this.showRecoveryNotifications = value;
	}

	public boolean isWriteSanitizedLocalRecoveryReport() {
		return this.writeSanitizedLocalRecoveryReport;
	}

	public void setWriteSanitizedLocalRecoveryReport(boolean value) {
		this.writeSanitizedLocalRecoveryReport = value;
	}

	public boolean isShowRecoveryStatsInInspector() {
		return this.showRecoveryStatsInInspector;
	}

	public void setShowRecoveryStatsInInspector(boolean value) {
		this.showRecoveryStatsInInspector = value;
	}

	public synchronized void resetToFactoryDefaults() {
		ConfigFactoryDefaults.copyMutableFields(this, new VoltRecoveryConfig());
	}

	public synchronized void save() {
		saveChecked();
	}

	public synchronized boolean saveChecked() {
		validate();
		Path path = configPath();
		if (!this.wasEverEnabled && !Files.exists(path)) {
			return true;
		}
		Path temporaryPath = null;
		try {
			Path directory = path.getParent();
			Files.createDirectories(directory);
			if (Files.exists(path)
					&& (Files.isSymbolicLink(path) || !Files.isRegularFile(path))) {
				throw new IOException("Unsafe recovery configuration target");
			}
			temporaryPath = Files.createTempFile(directory, "sodium-volt-recovery-", ".tmp");
			Files.writeString(
					temporaryPath,
					GSON.toJson(this),
					StandardCharsets.UTF_8,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE
			);
			moveIntoPlace(temporaryPath, path);
			temporaryPath = null;
			return true;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			SodiumVolt.LOGGER.error("Could not save Volt Recovery configuration");
			return false;
		} finally {
			if (temporaryPath != null) {
				try {
					Files.deleteIfExists(temporaryPath);
				} catch (IOException | SecurityException exception) {
					SodiumVolt.LOGGER.warn("Could not remove a temporary Volt Recovery file");
				}
			}
		}
	}

	private static VoltRecoveryConfig load() {
		VoltRecoveryConfig config = new VoltRecoveryConfig();
		Path path = configPath();
		try {
			if (!Files.exists(path)) {
				return config;
			}
			if (Files.isSymbolicLink(path)
					|| !Files.isRegularFile(path)
					|| Files.size(path) > MAX_CONFIG_SIZE_BYTES) {
				SodiumVolt.LOGGER.warn("Ignoring an invalid Volt Recovery configuration");
				return config;
			}
			byte[] document;
			try (InputStream input = Files.newInputStream(path)) {
				document = input.readNBytes(MAX_CONFIG_SIZE_BYTES + 1);
			}
			if (document.length > MAX_CONFIG_SIZE_BYTES) {
				SodiumVolt.LOGGER.warn("Ignoring an oversized Volt Recovery configuration");
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
				if (readInteger(root, "version", CONFIG_VERSION) != CONFIG_VERSION) {
					throw new IllegalArgumentException("Unsupported recovery configuration version");
				}
				config.voltRecoveryEnabled = readBoolean(
						root, "volt_recovery_enabled", config.voltRecoveryEnabled
				);
				config.detectUncleanSessions = readBoolean(
						root, "detect_unclean_sessions", config.detectUncleanSessions
				);
				config.automaticSafeMode = readBoolean(
						root, "automatic_safe_mode", config.automaticSafeMode
				);
				config.forceSafeModeNextLaunch = readBoolean(
						root, "force_safe_mode_next_launch", config.forceSafeModeNextLaunch
				);
				config.crashStreakThreshold = readInteger(
						root, "crash_streak_threshold", config.crashStreakThreshold
				);
				config.maximumRecoveryAttempts = readInteger(
						root, "maximum_recovery_attempts", config.maximumRecoveryAttempts
				);
				config.applySafeGraphicsProfile = readBoolean(
						root, "apply_safe_graphics_profile", config.applySafeGraphicsProfile
				);
				config.safeRenderDistance = readInteger(
						root, "safe_render_distance", config.safeRenderDistance
				);
				config.safeEntityDistancePercent = readInteger(
						root, "safe_entity_distance_percent", config.safeEntityDistancePercent
				);
				config.reduceExpensiveGraphics = readBoolean(
						root, "reduce_expensive_graphics", config.reduceExpensiveGraphics
				);
				config.limitFpsDuringRecovery = readBoolean(
						root, "limit_fps_during_recovery", config.limitFpsDuringRecovery
				);
				config.recoveryFpsCap = readInteger(
						root, "recovery_fps_cap", config.recoveryFpsCap
				);
				config.suspendAdaptiveController = readBoolean(
						root, "suspend_adaptive_controller", config.suspendAdaptiveController
				);
				config.restoreOwnedSettingsAfterStableSession = readBoolean(
						root,
						"restore_owned_settings_after_stable_session",
						config.restoreOwnedSettingsAfterStableSession
				);
				config.stableSessionDurationSeconds = readInteger(
						root, "stable_session_duration_seconds", config.stableSessionDurationSeconds
				);
				config.showRecoveryNotifications = readBoolean(
						root, "show_recovery_notifications", config.showRecoveryNotifications
				);
				config.writeSanitizedLocalRecoveryReport = readBoolean(
						root,
						"write_sanitized_local_recovery_report",
						config.writeSanitizedLocalRecoveryReport
				);
				config.showRecoveryStatsInInspector = readBoolean(
						root,
						"show_recovery_stats_in_inspector",
						config.showRecoveryStatsInInspector
				);
			}
			config.wasEverEnabled = true;
			config.validate();
			return config;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			SodiumVolt.LOGGER.error("Could not load Volt Recovery configuration; using safe defaults");
			return new VoltRecoveryConfig();
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
		setCrashStreakThreshold(this.crashStreakThreshold);
		setMaximumRecoveryAttempts(this.maximumRecoveryAttempts);
		setSafeRenderDistance(this.safeRenderDistance);
		setSafeEntityDistancePercent(this.safeEntityDistancePercent);
		setRecoveryFpsCap(this.recoveryFpsCap);
		setStableSessionDurationSeconds(this.stableSessionDurationSeconds);
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
				.resolve("sodium-volt-recovery.json");
	}

	private static final class Holder {
		private static final VoltRecoveryConfig INSTANCE = load();
	}
}
