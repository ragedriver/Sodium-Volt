package com.ragedriver.sodiumvolt.client.config;

import com.google.gson.JsonObject;
import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.watchdog.GpuWatchdogPolicy;
import com.ragedriver.sodiumvolt.client.watchdog.WatchdogJson;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GpuWatchdogConfig {
	public static final int WARNING_THRESHOLD_MIN = 1;
	public static final int WARNING_THRESHOLD_MAX = 15;
	public static final int WARNING_THRESHOLD_DEFAULT = 3;
	public static final int CRITICAL_THRESHOLD_MIN = 2;
	public static final int CRITICAL_THRESHOLD_MAX = 30;
	public static final int CRITICAL_THRESHOLD_DEFAULT = 8;
	public static final int CONFIRMATION_COUNT_MIN = 1;
	public static final int CONFIRMATION_COUNT_MAX = 5;
	public static final int CONFIRMATION_COUNT_DEFAULT = 2;
	public static final int STARTUP_GRACE_MIN = 5;
	public static final int STARTUP_GRACE_MAX = 60;
	public static final int STARTUP_GRACE_STEP = 5;
	public static final int STARTUP_GRACE_DEFAULT = 20;
	public static final int RELOAD_GRACE_MIN = 5;
	public static final int RELOAD_GRACE_MAX = 120;
	public static final int RELOAD_GRACE_STEP = 5;
	public static final int RELOAD_GRACE_DEFAULT = 30;
	public static final int SAMPLE_INTERVAL_MIN = 100;
	public static final int SAMPLE_INTERVAL_MAX = 1_000;
	public static final int SAMPLE_INTERVAL_STEP = 50;
	public static final int SAMPLE_INTERVAL_DEFAULT = 250;
	public static final int COOLDOWN_MIN = 15;
	public static final int COOLDOWN_MAX = 300;
	public static final int COOLDOWN_STEP = 15;
	public static final int COOLDOWN_DEFAULT = 60;
	public static final int MAXIMUM_INCIDENTS_MIN = 1;
	public static final int MAXIMUM_INCIDENTS_MAX = 10;
	public static final int MAXIMUM_INCIDENTS_DEFAULT = 3;

	static final int CONFIG_VERSION = 1;
	static final int MAXIMUM_CONFIG_BYTES = 32 * 1024;
	private static final Set<String> CONFIG_KEYS = Set.of(
			"version",
			"gpu_timeout_watchdog_enabled",
			"warning_stall_threshold_seconds",
			"critical_timeout_threshold_seconds",
			"critical_confirmation_count",
			"startup_world_grace_seconds",
			"resource_reload_grace_seconds",
			"ignore_paused_loading",
			"ignore_unfocused_minimized",
			"sample_interval_millis",
			"incident_cooldown_seconds",
			"maximum_incidents_per_session",
			"arm_recovery_next_launch",
			"show_transition_notifications",
			"write_sanitized_incident_report",
			"show_inspector_statistics"
	);
	private static final AtomicBoolean LOAD_FAILURE_LOGGED = new AtomicBoolean();
	private static final AtomicBoolean SAVE_FAILURE_LOGGED = new AtomicBoolean();

	private boolean gpuTimeoutWatchdogEnabled;
	private int warningStallThresholdSeconds = WARNING_THRESHOLD_DEFAULT;
	private int criticalTimeoutThresholdSeconds = CRITICAL_THRESHOLD_DEFAULT;
	private int criticalConfirmationCount = CONFIRMATION_COUNT_DEFAULT;
	private int startupWorldGraceSeconds = STARTUP_GRACE_DEFAULT;
	private int resourceReloadGraceSeconds = RELOAD_GRACE_DEFAULT;
	private boolean ignorePausedLoading = true;
	private boolean ignoreUnfocusedMinimized = true;
	private int sampleIntervalMillis = SAMPLE_INTERVAL_DEFAULT;
	private int incidentCooldownSeconds = COOLDOWN_DEFAULT;
	private int maximumIncidentsPerSession = MAXIMUM_INCIDENTS_DEFAULT;
	private boolean armRecoveryNextLaunch = true;
	private boolean showTransitionNotifications = true;
	private boolean writeSanitizedIncidentReport = true;
	private boolean showInspectorStatistics = true;
	private transient boolean wasEverEnabled;
	private transient long revision;

	private GpuWatchdogConfig() {
	}

	public static GpuWatchdogConfig getInstance() {
		return Holder.INSTANCE;
	}

	static GpuWatchdogConfig createForTest() {
		return new GpuWatchdogConfig();
	}

	public boolean isGpuTimeoutWatchdogEnabled() {
		return this.gpuTimeoutWatchdogEnabled;
	}

	public void setGpuTimeoutWatchdogEnabled(boolean value) {
		this.gpuTimeoutWatchdogEnabled = value;
		this.wasEverEnabled |= value;
		this.revision++;
	}

	public int getWarningStallThresholdSeconds() {
		return this.warningStallThresholdSeconds;
	}

	public void setWarningStallThresholdSeconds(int value) {
		this.warningStallThresholdSeconds = clamp(
				value, WARNING_THRESHOLD_MIN, WARNING_THRESHOLD_MAX
		);
		this.criticalTimeoutThresholdSeconds = Math.max(
				this.criticalTimeoutThresholdSeconds,
				this.warningStallThresholdSeconds + 1
		);
		this.revision++;
	}

	public int getCriticalTimeoutThresholdSeconds() {
		return this.criticalTimeoutThresholdSeconds;
	}

	public void setCriticalTimeoutThresholdSeconds(int value) {
		this.criticalTimeoutThresholdSeconds = Math.max(
				clamp(value, CRITICAL_THRESHOLD_MIN, CRITICAL_THRESHOLD_MAX),
				this.warningStallThresholdSeconds + 1
		);
		this.revision++;
	}

	public int getCriticalConfirmationCount() {
		return this.criticalConfirmationCount;
	}

	public void setCriticalConfirmationCount(int value) {
		this.criticalConfirmationCount = clamp(
				value, CONFIRMATION_COUNT_MIN, CONFIRMATION_COUNT_MAX
		);
		this.revision++;
	}

	public int getStartupWorldGraceSeconds() {
		return this.startupWorldGraceSeconds;
	}

	public void setStartupWorldGraceSeconds(int value) {
		this.startupWorldGraceSeconds = clampStep(
				value, STARTUP_GRACE_MIN, STARTUP_GRACE_MAX, STARTUP_GRACE_STEP
		);
		this.revision++;
	}

	public int getResourceReloadGraceSeconds() {
		return this.resourceReloadGraceSeconds;
	}

	public void setResourceReloadGraceSeconds(int value) {
		this.resourceReloadGraceSeconds = clampStep(
				value, RELOAD_GRACE_MIN, RELOAD_GRACE_MAX, RELOAD_GRACE_STEP
		);
		this.revision++;
	}

	public boolean isIgnorePausedLoading() {
		return this.ignorePausedLoading;
	}

	public void setIgnorePausedLoading(boolean value) {
		this.ignorePausedLoading = value;
		this.revision++;
	}

	public boolean isIgnoreUnfocusedMinimized() {
		return this.ignoreUnfocusedMinimized;
	}

	public void setIgnoreUnfocusedMinimized(boolean value) {
		this.ignoreUnfocusedMinimized = value;
		this.revision++;
	}

	public int getSampleIntervalMillis() {
		return this.sampleIntervalMillis;
	}

	public void setSampleIntervalMillis(int value) {
		this.sampleIntervalMillis = clampStep(
				value, SAMPLE_INTERVAL_MIN, SAMPLE_INTERVAL_MAX, SAMPLE_INTERVAL_STEP
		);
		this.revision++;
	}

	public int getIncidentCooldownSeconds() {
		return this.incidentCooldownSeconds;
	}

	public void setIncidentCooldownSeconds(int value) {
		this.incidentCooldownSeconds = clampStep(
				value, COOLDOWN_MIN, COOLDOWN_MAX, COOLDOWN_STEP
		);
		this.revision++;
	}

	public int getMaximumIncidentsPerSession() {
		return this.maximumIncidentsPerSession;
	}

	public void setMaximumIncidentsPerSession(int value) {
		this.maximumIncidentsPerSession = clamp(
				value, MAXIMUM_INCIDENTS_MIN, MAXIMUM_INCIDENTS_MAX
		);
		this.revision++;
	}

	public boolean isArmRecoveryNextLaunch() {
		return this.armRecoveryNextLaunch;
	}

	public void setArmRecoveryNextLaunch(boolean value) {
		this.armRecoveryNextLaunch = value;
		this.revision++;
	}

	public boolean isShowTransitionNotifications() {
		return this.showTransitionNotifications;
	}

	public void setShowTransitionNotifications(boolean value) {
		this.showTransitionNotifications = value;
		this.revision++;
	}

	public boolean isWriteSanitizedIncidentReport() {
		return this.writeSanitizedIncidentReport;
	}

	public void setWriteSanitizedIncidentReport(boolean value) {
		this.writeSanitizedIncidentReport = value;
		this.revision++;
	}

	public boolean isShowInspectorStatistics() {
		return this.showInspectorStatistics;
	}

	public void setShowInspectorStatistics(boolean value) {
		this.showInspectorStatistics = value;
		this.revision++;
	}

	public long revision() {
		return this.revision;
	}

	public GpuWatchdogPolicy.Settings policySettings() {
		return new GpuWatchdogPolicy.Settings(
				this.warningStallThresholdSeconds * 1_000_000_000L,
				this.criticalTimeoutThresholdSeconds * 1_000_000_000L,
				this.criticalConfirmationCount,
				this.incidentCooldownSeconds * 1_000_000_000L,
				this.maximumIncidentsPerSession,
				this.sampleIntervalMillis,
				this.armRecoveryNextLaunch,
				this.writeSanitizedIncidentReport
		);
	}

	public synchronized void resetToFactoryDefaults() {
		long nextRevision = ConfigFactoryDefaults.nextRevision(this.revision);
		ConfigFactoryDefaults.copyMutableFields(this, new GpuWatchdogConfig());
		this.revision = nextRevision;
	}

	public synchronized void save() {
		saveChecked();
	}

	public synchronized boolean saveChecked() {
		normalize();
		Path path = configPath();
		if (!this.wasEverEnabled && !Files.exists(path)) {
			return true;
		}
		try {
			WatchdogJson.writeObject(
					path,
					toJson(),
					MAXIMUM_CONFIG_BYTES,
					"sodium-volt-gpu-watchdog-"
			);
			return true;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			if (SAVE_FAILURE_LOGGED.compareAndSet(false, true)) {
				SodiumVolt.LOGGER.warn("Could not save GPU Timeout Watchdog configuration");
			}
			return false;
		}
	}

	static GpuWatchdogConfig load(Path path) {
		GpuWatchdogConfig config = new GpuWatchdogConfig();
		try {
			JsonObject root = WatchdogJson.readObject(path, MAXIMUM_CONFIG_BYTES);
			if (root == null) {
				return config;
			}
			WatchdogJson.requireExactKeys(root, CONFIG_KEYS);
			if (WatchdogJson.requiredInteger(root, "version") != CONFIG_VERSION) {
				throw new IllegalArgumentException("Unsupported watchdog config version");
			}
			config.gpuTimeoutWatchdogEnabled =
					WatchdogJson.requiredBoolean(root, "gpu_timeout_watchdog_enabled");
			config.warningStallThresholdSeconds =
					WatchdogJson.requiredInteger(root, "warning_stall_threshold_seconds");
			config.criticalTimeoutThresholdSeconds =
					WatchdogJson.requiredInteger(root, "critical_timeout_threshold_seconds");
			config.criticalConfirmationCount =
					WatchdogJson.requiredInteger(root, "critical_confirmation_count");
			config.startupWorldGraceSeconds =
					WatchdogJson.requiredInteger(root, "startup_world_grace_seconds");
			config.resourceReloadGraceSeconds =
					WatchdogJson.requiredInteger(root, "resource_reload_grace_seconds");
			config.ignorePausedLoading =
					WatchdogJson.requiredBoolean(root, "ignore_paused_loading");
			config.ignoreUnfocusedMinimized =
					WatchdogJson.requiredBoolean(root, "ignore_unfocused_minimized");
			config.sampleIntervalMillis =
					WatchdogJson.requiredInteger(root, "sample_interval_millis");
			config.incidentCooldownSeconds =
					WatchdogJson.requiredInteger(root, "incident_cooldown_seconds");
			config.maximumIncidentsPerSession =
					WatchdogJson.requiredInteger(root, "maximum_incidents_per_session");
			config.armRecoveryNextLaunch =
					WatchdogJson.requiredBoolean(root, "arm_recovery_next_launch");
			config.showTransitionNotifications =
					WatchdogJson.requiredBoolean(root, "show_transition_notifications");
			config.writeSanitizedIncidentReport =
					WatchdogJson.requiredBoolean(root, "write_sanitized_incident_report");
			config.showInspectorStatistics =
					WatchdogJson.requiredBoolean(root, "show_inspector_statistics");
			config.validateStoredValues();
			config.normalize();
			config.wasEverEnabled = config.gpuTimeoutWatchdogEnabled;
			config.revision = 0L;
			return config;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			if (LOAD_FAILURE_LOGGED.compareAndSet(false, true)) {
				SodiumVolt.LOGGER.warn("Ignoring invalid GPU Timeout Watchdog configuration");
			}
			return new GpuWatchdogConfig();
		}
	}

	JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("version", CONFIG_VERSION);
		root.addProperty("gpu_timeout_watchdog_enabled", this.gpuTimeoutWatchdogEnabled);
		root.addProperty("warning_stall_threshold_seconds", this.warningStallThresholdSeconds);
		root.addProperty("critical_timeout_threshold_seconds", this.criticalTimeoutThresholdSeconds);
		root.addProperty("critical_confirmation_count", this.criticalConfirmationCount);
		root.addProperty("startup_world_grace_seconds", this.startupWorldGraceSeconds);
		root.addProperty("resource_reload_grace_seconds", this.resourceReloadGraceSeconds);
		root.addProperty("ignore_paused_loading", this.ignorePausedLoading);
		root.addProperty("ignore_unfocused_minimized", this.ignoreUnfocusedMinimized);
		root.addProperty("sample_interval_millis", this.sampleIntervalMillis);
		root.addProperty("incident_cooldown_seconds", this.incidentCooldownSeconds);
		root.addProperty("maximum_incidents_per_session", this.maximumIncidentsPerSession);
		root.addProperty("arm_recovery_next_launch", this.armRecoveryNextLaunch);
		root.addProperty("show_transition_notifications", this.showTransitionNotifications);
		root.addProperty("write_sanitized_incident_report", this.writeSanitizedIncidentReport);
		root.addProperty("show_inspector_statistics", this.showInspectorStatistics);
		return root;
	}

	private void normalize() {
		this.warningStallThresholdSeconds = clamp(
				this.warningStallThresholdSeconds,
				WARNING_THRESHOLD_MIN,
				WARNING_THRESHOLD_MAX
		);
		this.criticalTimeoutThresholdSeconds = Math.max(
				clamp(
						this.criticalTimeoutThresholdSeconds,
						CRITICAL_THRESHOLD_MIN,
						CRITICAL_THRESHOLD_MAX
				),
				this.warningStallThresholdSeconds + 1
		);
		this.criticalConfirmationCount = clamp(
				this.criticalConfirmationCount,
				CONFIRMATION_COUNT_MIN,
				CONFIRMATION_COUNT_MAX
		);
		this.startupWorldGraceSeconds = clampStep(
				this.startupWorldGraceSeconds,
				STARTUP_GRACE_MIN,
				STARTUP_GRACE_MAX,
				STARTUP_GRACE_STEP
		);
		this.resourceReloadGraceSeconds = clampStep(
				this.resourceReloadGraceSeconds,
				RELOAD_GRACE_MIN,
				RELOAD_GRACE_MAX,
				RELOAD_GRACE_STEP
		);
		this.sampleIntervalMillis = clampStep(
				this.sampleIntervalMillis,
				SAMPLE_INTERVAL_MIN,
				SAMPLE_INTERVAL_MAX,
				SAMPLE_INTERVAL_STEP
		);
		this.incidentCooldownSeconds = clampStep(
				this.incidentCooldownSeconds,
				COOLDOWN_MIN,
				COOLDOWN_MAX,
				COOLDOWN_STEP
		);
		this.maximumIncidentsPerSession = clamp(
				this.maximumIncidentsPerSession,
				MAXIMUM_INCIDENTS_MIN,
				MAXIMUM_INCIDENTS_MAX
		);
	}

	private void validateStoredValues() {
		requireRange(
				this.warningStallThresholdSeconds,
				WARNING_THRESHOLD_MIN,
				WARNING_THRESHOLD_MAX
		);
		requireRange(
				this.criticalTimeoutThresholdSeconds,
				CRITICAL_THRESHOLD_MIN,
				CRITICAL_THRESHOLD_MAX
		);
		if (this.criticalTimeoutThresholdSeconds
				< this.warningStallThresholdSeconds + 1) {
			throw new IllegalArgumentException("Critical threshold must exceed warning threshold");
		}
		requireRange(
				this.criticalConfirmationCount,
				CONFIRMATION_COUNT_MIN,
				CONFIRMATION_COUNT_MAX
		);
		requireStep(
				this.startupWorldGraceSeconds,
				STARTUP_GRACE_MIN,
				STARTUP_GRACE_MAX,
				STARTUP_GRACE_STEP
		);
		requireStep(
				this.resourceReloadGraceSeconds,
				RELOAD_GRACE_MIN,
				RELOAD_GRACE_MAX,
				RELOAD_GRACE_STEP
		);
		requireStep(
				this.sampleIntervalMillis,
				SAMPLE_INTERVAL_MIN,
				SAMPLE_INTERVAL_MAX,
				SAMPLE_INTERVAL_STEP
		);
		requireStep(
				this.incidentCooldownSeconds,
				COOLDOWN_MIN,
				COOLDOWN_MAX,
				COOLDOWN_STEP
		);
		requireRange(
				this.maximumIncidentsPerSession,
				MAXIMUM_INCIDENTS_MIN,
				MAXIMUM_INCIDENTS_MAX
		);
	}

	private static void requireRange(int value, int minimum, int maximum) {
		if (value < minimum || value > maximum) {
			throw new IllegalArgumentException("Out-of-range watchdog configuration");
		}
	}

	private static void requireStep(int value, int minimum, int maximum, int step) {
		requireRange(value, minimum, maximum);
		if ((value - minimum) % step != 0) {
			throw new IllegalArgumentException("Off-step watchdog configuration");
		}
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static int clampStep(int value, int minimum, int maximum, int step) {
		int clamped = clamp(value, minimum, maximum);
		int offset = clamped - minimum;
		int rounded = minimum + Math.round((float) offset / step) * step;
		return clamp(rounded, minimum, maximum);
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir()
				.resolve("sodium-volt-gpu-watchdog.json");
	}

	private static final class Holder {
		private static final GpuWatchdogConfig INSTANCE = load(configPath());
	}
}
