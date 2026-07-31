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

public final class VoltInspectorConfig {
	public static final int REFRESH_INTERVAL_MIN = 250;
	public static final int REFRESH_INTERVAL_MAX = 2_000;
	public static final int REFRESH_INTERVAL_STEP = 250;
	public static final int REFRESH_INTERVAL_DEFAULT = 500;
	public static final int SAMPLE_WINDOW_MIN = 240;
	public static final int SAMPLE_WINDOW_MAX = 1_200;
	public static final int SAMPLE_WINDOW_STEP = 120;
	public static final int SAMPLE_WINDOW_DEFAULT = 600;
	public static final int SPIKE_THRESHOLD_MIN = 20;
	public static final int SPIKE_THRESHOLD_MAX = 200;
	public static final int SPIKE_THRESHOLD_STEP = 5;
	public static final int SPIKE_THRESHOLD_DEFAULT = 50;

	private static final int CONFIG_VERSION = 1;
	private static final long MAX_CONFIG_SIZE_BYTES = 1024L * 1024L;
	private static final Gson GSON = new GsonBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.setPrettyPrinting()
			.create();

	private final int version = CONFIG_VERSION;
	private volatile boolean voltInspectorEnabled;
	private volatile boolean showInspectorOverlay = true;
	private volatile boolean frameTimeStatistics = true;
	private volatile boolean chunkActivity = true;
	private volatile boolean sceneComplexity = true;
	private volatile boolean particleBreakdown = true;
	private volatile boolean animatedTextureCount = true;
	private volatile boolean gcPauseMonitor = true;
	private volatile boolean bottleneckEstimate = true;
	private volatile boolean rendererGpuDetails = true;
	private volatile boolean resourceReloadTiming = true;
	private volatile boolean smartRecommendations = true;
	private volatile int refreshIntervalMs = REFRESH_INTERVAL_DEFAULT;
	private volatile int frameSampleWindow = SAMPLE_WINDOW_DEFAULT;
	private volatile int spikeThresholdMs = SPIKE_THRESHOLD_DEFAULT;

	private VoltInspectorConfig() {
	}

	public static VoltInspectorConfig getInstance() {
		return Holder.INSTANCE;
	}

	static VoltInspectorConfig createForTest() {
		return new VoltInspectorConfig();
	}

	public boolean isVoltInspectorEnabled() {
		return this.voltInspectorEnabled;
	}

	public void setVoltInspectorEnabled(boolean value) {
		this.voltInspectorEnabled = value;
	}

	public boolean isShowInspectorOverlay() {
		return this.showInspectorOverlay;
	}

	public void setShowInspectorOverlay(boolean value) {
		this.showInspectorOverlay = value;
	}

	public boolean isFrameTimeStatistics() {
		return this.frameTimeStatistics;
	}

	public void setFrameTimeStatistics(boolean value) {
		this.frameTimeStatistics = value;
	}

	public boolean isChunkActivity() {
		return this.chunkActivity;
	}

	public void setChunkActivity(boolean value) {
		this.chunkActivity = value;
	}

	public boolean isSceneComplexity() {
		return this.sceneComplexity;
	}

	public void setSceneComplexity(boolean value) {
		this.sceneComplexity = value;
	}

	public boolean isParticleBreakdown() {
		return this.particleBreakdown;
	}

	public void setParticleBreakdown(boolean value) {
		this.particleBreakdown = value;
	}

	public boolean isAnimatedTextureCount() {
		return this.animatedTextureCount;
	}

	public void setAnimatedTextureCount(boolean value) {
		this.animatedTextureCount = value;
	}

	public boolean isGcPauseMonitor() {
		return this.gcPauseMonitor;
	}

	public void setGcPauseMonitor(boolean value) {
		this.gcPauseMonitor = value;
	}

	public boolean isBottleneckEstimate() {
		return this.bottleneckEstimate;
	}

	public void setBottleneckEstimate(boolean value) {
		this.bottleneckEstimate = value;
	}

	public boolean isRendererGpuDetails() {
		return this.rendererGpuDetails;
	}

	public void setRendererGpuDetails(boolean value) {
		this.rendererGpuDetails = value;
	}

	public boolean isResourceReloadTiming() {
		return this.resourceReloadTiming;
	}

	public void setResourceReloadTiming(boolean value) {
		this.resourceReloadTiming = value;
	}

	public boolean isSmartRecommendations() {
		return this.smartRecommendations;
	}

	public void setSmartRecommendations(boolean value) {
		this.smartRecommendations = value;
	}

	public int getRefreshIntervalMs() {
		return this.refreshIntervalMs;
	}

	public void setRefreshIntervalMs(int value) {
		this.refreshIntervalMs = clampToStep(
				value,
				REFRESH_INTERVAL_MIN,
				REFRESH_INTERVAL_MAX,
				REFRESH_INTERVAL_STEP
		);
	}

	public int getFrameSampleWindow() {
		return this.frameSampleWindow;
	}

	public void setFrameSampleWindow(int value) {
		this.frameSampleWindow = clampToStep(
				value,
				SAMPLE_WINDOW_MIN,
				SAMPLE_WINDOW_MAX,
				SAMPLE_WINDOW_STEP
		);
	}

	public int getSpikeThresholdMs() {
		return this.spikeThresholdMs;
	}

	public void setSpikeThresholdMs(int value) {
		this.spikeThresholdMs = clampToStep(
				value,
				SPIKE_THRESHOLD_MIN,
				SPIKE_THRESHOLD_MAX,
				SPIKE_THRESHOLD_STEP
		);
	}

	public synchronized void resetToFactoryDefaults() {
		ConfigFactoryDefaults.copyMutableFields(this, new VoltInspectorConfig());
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
			Path directory = path.getParent();
			Files.createDirectories(directory);
			temporaryPath = Files.createTempFile(directory, "sodium-volt-inspector-", ".tmp");
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
			SodiumVolt.LOGGER.error("Could not save Volt Inspector configuration to {}", path, exception);
		} finally {
			if (temporaryPath != null) {
				try {
					Files.deleteIfExists(temporaryPath);
				} catch (IOException | SecurityException exception) {
					SodiumVolt.LOGGER.warn("Could not remove temporary Inspector configuration {}", temporaryPath);
				}
			}
		}
		return saved;
	}

	private static VoltInspectorConfig load() {
		VoltInspectorConfig config = new VoltInspectorConfig();
		Path path = configPath();
		try {
			if (!Files.exists(path)) {
				return config;
			}
			if (!Files.isRegularFile(path) || Files.size(path) > MAX_CONFIG_SIZE_BYTES) {
				SodiumVolt.LOGGER.warn("Ignoring invalid Volt Inspector configuration file {}", path);
				return config;
			}
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonElement element = JsonParser.parseReader(reader);
				if (!element.isJsonObject()) {
					throw new IllegalArgumentException("Configuration root must be an object");
				}
				JsonObject root = element.getAsJsonObject();
				config.voltInspectorEnabled = readBoolean(root, "volt_inspector_enabled", config.voltInspectorEnabled);
				config.showInspectorOverlay = readBoolean(root, "show_inspector_overlay", config.showInspectorOverlay);
				config.frameTimeStatistics = readBoolean(root, "frame_time_statistics", config.frameTimeStatistics);
				config.chunkActivity = readBoolean(root, "chunk_activity", config.chunkActivity);
				config.sceneComplexity = readBoolean(root, "scene_complexity", config.sceneComplexity);
				config.particleBreakdown = readBoolean(root, "particle_breakdown", config.particleBreakdown);
				config.animatedTextureCount = readBoolean(
						root, "animated_texture_count", config.animatedTextureCount
				);
				config.gcPauseMonitor = readBoolean(root, "gc_pause_monitor", config.gcPauseMonitor);
				config.bottleneckEstimate = readBoolean(root, "bottleneck_estimate", config.bottleneckEstimate);
				config.rendererGpuDetails = readBoolean(root, "renderer_gpu_details", config.rendererGpuDetails);
				config.resourceReloadTiming = readBoolean(
						root, "resource_reload_timing", config.resourceReloadTiming
				);
				config.smartRecommendations = readBoolean(
						root, "smart_recommendations", config.smartRecommendations
				);
				config.refreshIntervalMs = readInteger(root, "refresh_interval_ms", config.refreshIntervalMs);
				config.frameSampleWindow = readInteger(root, "frame_sample_window", config.frameSampleWindow);
				config.spikeThresholdMs = readInteger(root, "spike_threshold_ms", config.spikeThresholdMs);
			}
			config.validate();
			return config;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			SodiumVolt.LOGGER.error(
					"Could not load Volt Inspector configuration from {}; using safe defaults",
					path,
					exception
			);
			return new VoltInspectorConfig();
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
		this.setRefreshIntervalMs(this.refreshIntervalMs);
		this.setFrameSampleWindow(this.frameSampleWindow);
		this.setSpikeThresholdMs(this.spikeThresholdMs);
	}

	private static int clampToStep(int value, int minimum, int maximum, int step) {
		int clamped = Math.max(minimum, Math.min(maximum, value));
		int steps = (clamped - minimum + step / 2) / step;
		return Math.min(maximum, minimum + steps * step);
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
				.resolve("sodium-volt-inspector.json");
	}

	private static final class Holder {
		private static final VoltInspectorConfig INSTANCE = load();
	}
}
