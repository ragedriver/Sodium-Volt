package com.ragedriver.sodiumvolt.client.config;

import com.google.gson.JsonObject;
import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.resourcepack.ResourcePackShieldPolicy;
import com.ragedriver.sodiumvolt.client.resourcepack.ShieldJsonFile;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ResourcePackShieldConfig {
	public static final int ENTRY_LIMIT_MIN = 1_024;
	public static final int ENTRY_LIMIT_MAX = 65_536;
	public static final int ENTRY_LIMIT_STEP = 1_024;
	public static final int ENTRY_LIMIT_DEFAULT = 16_384;
	public static final int ARCHIVE_MIB_MIN = 16;
	public static final int ARCHIVE_MIB_MAX = 1_024;
	public static final int ARCHIVE_MIB_STEP = 16;
	public static final int ARCHIVE_MIB_DEFAULT = 256;
	public static final int SINGLE_MIB_MIN = 1;
	public static final int SINGLE_MIB_MAX = 256;
	public static final int SINGLE_MIB_DEFAULT = 64;
	public static final int TOTAL_MIB_MIN = 64;
	public static final int TOTAL_MIB_MAX = 2_048;
	public static final int TOTAL_MIB_STEP = 64;
	public static final int TOTAL_MIB_DEFAULT = 512;
	public static final int RATIO_MIN = 10;
	public static final int RATIO_MAX = 1_000;
	public static final int RATIO_STEP = 10;
	public static final int RATIO_DEFAULT = 200;
	public static final int PNG_DIMENSION_MIN = 2_048;
	public static final int PNG_DIMENSION_MAX = 32_768;
	public static final int PNG_DIMENSION_STEP = 1_024;
	public static final int PNG_DIMENSION_DEFAULT = 16_384;
	public static final int PNG_MEGAPIXELS_MIN = 16;
	public static final int PNG_MEGAPIXELS_MAX = 512;
	public static final int PNG_MEGAPIXELS_STEP = 16;
	public static final int PNG_MEGAPIXELS_DEFAULT = 128;
	public static final int JSON_DEPTH_MIN = 16;
	public static final int JSON_DEPTH_MAX = 512;
	public static final int JSON_DEPTH_STEP = 16;
	public static final int JSON_DEPTH_DEFAULT = 128;
	public static final int PATH_LENGTH_MIN = 128;
	public static final int PATH_LENGTH_MAX = 2_048;
	public static final int PATH_LENGTH_STEP = 64;
	public static final int PATH_LENGTH_DEFAULT = 512;
	public static final int PATH_DEPTH_MIN = 8;
	public static final int PATH_DEPTH_MAX = 128;
	public static final int PATH_DEPTH_STEP = 4;
	public static final int PATH_DEPTH_DEFAULT = 32;
	public static final int SCAN_MILLIS_MIN = 250;
	public static final int SCAN_MILLIS_MAX = 5_000;
	public static final int SCAN_MILLIS_STEP = 250;
	public static final int SCAN_MILLIS_DEFAULT = 2_000;

	static final int CONFIG_VERSION = 1;
	static final int MAXIMUM_CONFIG_BYTES = 32 * 1024;
	static final Set<String> CONFIG_KEYS = Set.of(
			"version",
			"resource_pack_shield_enabled",
			"monitor_local_packs",
			"monitor_server_packs",
			"detect_unsafe_paths_and_symlinks",
			"block_core_shader_overrides",
			"reject_violations",
			"maximum_entries",
			"maximum_archive_mib",
			"maximum_single_resource_mib",
			"maximum_total_read_mib",
			"maximum_compression_ratio",
			"maximum_png_dimension",
			"maximum_png_megapixels",
			"maximum_json_depth",
			"maximum_path_length",
			"maximum_path_depth",
			"maximum_scan_millis",
			"show_transition_notifications",
			"write_sanitized_local_report",
			"show_inspector_statistics"
	);
	private static final AtomicBoolean LOAD_FAILURE_LOGGED = new AtomicBoolean();
	private static final AtomicBoolean SAVE_FAILURE_LOGGED = new AtomicBoolean();

	private volatile boolean resourcePackShieldEnabled;
	private volatile boolean monitorLocalPacks = true;
	private volatile boolean monitorServerPacks = true;
	private volatile boolean detectUnsafePathsAndSymlinks = true;
	private volatile boolean blockCoreShaderOverrides = true;
	private volatile boolean rejectViolations = true;
	private volatile int maximumEntries = ENTRY_LIMIT_DEFAULT;
	private volatile int maximumArchiveMiB = ARCHIVE_MIB_DEFAULT;
	private volatile int maximumSingleResourceMiB = SINGLE_MIB_DEFAULT;
	private volatile int maximumTotalReadMiB = TOTAL_MIB_DEFAULT;
	private volatile int maximumCompressionRatio = RATIO_DEFAULT;
	private volatile int maximumPngDimension = PNG_DIMENSION_DEFAULT;
	private volatile int maximumPngMegapixels = PNG_MEGAPIXELS_DEFAULT;
	private volatile int maximumJsonDepth = JSON_DEPTH_DEFAULT;
	private volatile int maximumPathLength = PATH_LENGTH_DEFAULT;
	private volatile int maximumPathDepth = PATH_DEPTH_DEFAULT;
	private volatile int maximumScanMillis = SCAN_MILLIS_DEFAULT;
	private volatile boolean showTransitionNotifications = true;
	private volatile boolean writeSanitizedLocalReport = true;
	private volatile boolean showInspectorStatistics = true;
	private volatile boolean wasEverEnabled;
	private volatile long revision;

	private ResourcePackShieldConfig() {
	}

	public static ResourcePackShieldConfig getInstance() {
		return Holder.INSTANCE;
	}

	static ResourcePackShieldConfig createForTest() {
		return new ResourcePackShieldConfig();
	}

	public boolean isResourcePackShieldEnabled() {
		return this.resourcePackShieldEnabled;
	}

	public synchronized void setResourcePackShieldEnabled(boolean value) {
		this.resourcePackShieldEnabled = value;
		this.wasEverEnabled |= value;
		this.revision++;
	}

	public boolean isMonitorLocalPacks() {
		return this.monitorLocalPacks;
	}

	public synchronized void setMonitorLocalPacks(boolean value) {
		this.monitorLocalPacks = value;
		this.revision++;
	}

	public boolean isMonitorServerPacks() {
		return this.monitorServerPacks;
	}

	public synchronized void setMonitorServerPacks(boolean value) {
		this.monitorServerPacks = value;
		this.revision++;
	}

	public boolean isDetectUnsafePathsAndSymlinks() {
		return this.detectUnsafePathsAndSymlinks;
	}

	public synchronized void setDetectUnsafePathsAndSymlinks(boolean value) {
		this.detectUnsafePathsAndSymlinks = value;
		this.revision++;
	}

	public boolean isBlockCoreShaderOverrides() {
		return this.blockCoreShaderOverrides;
	}

	public synchronized void setBlockCoreShaderOverrides(boolean value) {
		this.blockCoreShaderOverrides = value;
		this.revision++;
	}

	public boolean isRejectViolations() {
		return this.rejectViolations;
	}

	public synchronized void setRejectViolations(boolean value) {
		this.rejectViolations = value;
		this.revision++;
	}

	public int getMaximumEntries() {
		return this.maximumEntries;
	}

	public synchronized void setMaximumEntries(int value) {
		this.maximumEntries = clampStep(
				value, ENTRY_LIMIT_MIN, ENTRY_LIMIT_MAX, ENTRY_LIMIT_STEP
		);
		this.revision++;
	}

	public int getMaximumArchiveMiB() {
		return this.maximumArchiveMiB;
	}

	public synchronized void setMaximumArchiveMiB(int value) {
		this.maximumArchiveMiB = clampStep(
				value, ARCHIVE_MIB_MIN, ARCHIVE_MIB_MAX, ARCHIVE_MIB_STEP
		);
		this.revision++;
	}

	public int getMaximumSingleResourceMiB() {
		return this.maximumSingleResourceMiB;
	}

	public synchronized void setMaximumSingleResourceMiB(int value) {
		this.maximumSingleResourceMiB = clampStep(
				value, SINGLE_MIB_MIN, SINGLE_MIB_MAX, 1
		);
		this.maximumTotalReadMiB = validTotalAtLeast(
				this.maximumTotalReadMiB, this.maximumSingleResourceMiB
		);
		this.revision++;
	}

	public int getMaximumTotalReadMiB() {
		return this.maximumTotalReadMiB;
	}

	public synchronized void setMaximumTotalReadMiB(int value) {
		this.maximumTotalReadMiB = validTotalAtLeast(
				value, this.maximumSingleResourceMiB
		);
		this.revision++;
	}

	public int getMaximumCompressionRatio() {
		return this.maximumCompressionRatio;
	}

	public synchronized void setMaximumCompressionRatio(int value) {
		this.maximumCompressionRatio = clampStep(value, RATIO_MIN, RATIO_MAX, RATIO_STEP);
		this.revision++;
	}

	public int getMaximumPngDimension() {
		return this.maximumPngDimension;
	}

	public synchronized void setMaximumPngDimension(int value) {
		this.maximumPngDimension = clampStep(
				value, PNG_DIMENSION_MIN, PNG_DIMENSION_MAX, PNG_DIMENSION_STEP
		);
		this.revision++;
	}

	public int getMaximumPngMegapixels() {
		return this.maximumPngMegapixels;
	}

	public synchronized void setMaximumPngMegapixels(int value) {
		this.maximumPngMegapixels = clampStep(
				value, PNG_MEGAPIXELS_MIN, PNG_MEGAPIXELS_MAX, PNG_MEGAPIXELS_STEP
		);
		this.revision++;
	}

	public int getMaximumJsonDepth() {
		return this.maximumJsonDepth;
	}

	public synchronized void setMaximumJsonDepth(int value) {
		this.maximumJsonDepth = clampStep(
				value, JSON_DEPTH_MIN, JSON_DEPTH_MAX, JSON_DEPTH_STEP
		);
		this.revision++;
	}

	public int getMaximumPathLength() {
		return this.maximumPathLength;
	}

	public synchronized void setMaximumPathLength(int value) {
		this.maximumPathLength = clampStep(
				value, PATH_LENGTH_MIN, PATH_LENGTH_MAX, PATH_LENGTH_STEP
		);
		this.revision++;
	}

	public int getMaximumPathDepth() {
		return this.maximumPathDepth;
	}

	public synchronized void setMaximumPathDepth(int value) {
		this.maximumPathDepth = clampStep(
				value, PATH_DEPTH_MIN, PATH_DEPTH_MAX, PATH_DEPTH_STEP
		);
		this.revision++;
	}

	public int getMaximumScanMillis() {
		return this.maximumScanMillis;
	}

	public synchronized void setMaximumScanMillis(int value) {
		this.maximumScanMillis = clampStep(
				value, SCAN_MILLIS_MIN, SCAN_MILLIS_MAX, SCAN_MILLIS_STEP
		);
		this.revision++;
	}

	public boolean isShowTransitionNotifications() {
		return this.showTransitionNotifications;
	}

	public synchronized void setShowTransitionNotifications(boolean value) {
		this.showTransitionNotifications = value;
		this.revision++;
	}

	public boolean isWriteSanitizedLocalReport() {
		return this.writeSanitizedLocalReport;
	}

	public synchronized void setWriteSanitizedLocalReport(boolean value) {
		this.writeSanitizedLocalReport = value;
		this.revision++;
	}

	public boolean isShowInspectorStatistics() {
		return this.showInspectorStatistics;
	}

	public synchronized void setShowInspectorStatistics(boolean value) {
		this.showInspectorStatistics = value;
		this.revision++;
	}

	public long revision() {
		return this.revision;
	}

	public synchronized ResourcePackShieldPolicy policySnapshot() {
		return policySnapshotUnlocked();
	}

	public synchronized RuntimeSnapshot runtimeSnapshot() {
		return new RuntimeSnapshot(
				this.revision,
				this.resourcePackShieldEnabled,
				this.monitorLocalPacks,
				this.monitorServerPacks,
				this.showTransitionNotifications,
				this.writeSanitizedLocalReport,
				this.showInspectorStatistics,
				policySnapshotUnlocked()
		);
	}

	private ResourcePackShieldPolicy policySnapshotUnlocked() {
		return new ResourcePackShieldPolicy(
				this.detectUnsafePathsAndSymlinks,
				this.blockCoreShaderOverrides,
				this.rejectViolations,
				this.maximumEntries,
				mib(this.maximumArchiveMiB),
				mib(this.maximumSingleResourceMiB),
				mib(this.maximumTotalReadMiB),
				this.maximumCompressionRatio,
				this.maximumPngDimension,
				(long) this.maximumPngMegapixels * 1_000_000L,
				this.maximumJsonDepth,
				this.maximumPathLength,
				this.maximumPathDepth,
				(long) this.maximumScanMillis * 1_000_000L
		);
	}

	public record RuntimeSnapshot(
			long revision,
			boolean enabled,
			boolean monitorLocalPacks,
			boolean monitorServerPacks,
			boolean showTransitionNotifications,
			boolean writeSanitizedLocalReport,
			boolean showInspectorStatistics,
			ResourcePackShieldPolicy policy
	) {
	}

	public synchronized void resetToFactoryDefaults() {
		long nextRevision = ConfigFactoryDefaults.nextRevision(this.revision);
		ConfigFactoryDefaults.copyMutableFields(this, new ResourcePackShieldConfig());
		this.revision = nextRevision;
	}

	public synchronized void save() {
		saveChecked();
	}

	public synchronized boolean saveChecked() {
		return save(configPath(), true);
	}

	synchronized boolean save(Path path, boolean honorNeverEnabled) {
		normalize();
		if (honorNeverEnabled && !this.wasEverEnabled
				&& !Files.exists(path, LinkOption.NOFOLLOW_LINKS)
				&& !Files.isSymbolicLink(path)) {
			return true;
		}
		try {
			ShieldJsonFile.writeObject(
					path, toJson(), MAXIMUM_CONFIG_BYTES, "sodium-volt-resource-pack-shield-"
			);
			return true;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			if (SAVE_FAILURE_LOGGED.compareAndSet(false, true)) {
				SodiumVolt.LOGGER.warn("Could not save Resource-Pack Shield configuration");
			}
			return false;
		}
	}

	static ResourcePackShieldConfig load(Path path) {
		ResourcePackShieldConfig config = new ResourcePackShieldConfig();
		try {
			JsonObject root = ShieldJsonFile.readObject(path, MAXIMUM_CONFIG_BYTES);
			if (root == null) {
				return config;
			}
			ShieldJsonFile.requireExactKeys(root, CONFIG_KEYS);
			if (ShieldJsonFile.requiredInteger(root, "version") != CONFIG_VERSION) {
				throw new IllegalArgumentException("Unsupported Resource-Pack Shield version");
			}
			config.resourcePackShieldEnabled = ShieldJsonFile.requiredBoolean(
					root, "resource_pack_shield_enabled"
			);
			config.monitorLocalPacks = ShieldJsonFile.requiredBoolean(root, "monitor_local_packs");
			config.monitorServerPacks = ShieldJsonFile.requiredBoolean(root, "monitor_server_packs");
			config.detectUnsafePathsAndSymlinks = ShieldJsonFile.requiredBoolean(
					root, "detect_unsafe_paths_and_symlinks"
			);
			config.blockCoreShaderOverrides = ShieldJsonFile.requiredBoolean(
					root, "block_core_shader_overrides"
			);
			config.rejectViolations = ShieldJsonFile.requiredBoolean(root, "reject_violations");
			config.maximumEntries = ShieldJsonFile.requiredInteger(root, "maximum_entries");
			config.maximumArchiveMiB = ShieldJsonFile.requiredInteger(
					root, "maximum_archive_mib"
			);
			config.maximumSingleResourceMiB = ShieldJsonFile.requiredInteger(
					root, "maximum_single_resource_mib"
			);
			config.maximumTotalReadMiB = ShieldJsonFile.requiredInteger(
					root, "maximum_total_read_mib"
			);
			config.maximumCompressionRatio = ShieldJsonFile.requiredInteger(
					root, "maximum_compression_ratio"
			);
			config.maximumPngDimension = ShieldJsonFile.requiredInteger(
					root, "maximum_png_dimension"
			);
			config.maximumPngMegapixels = ShieldJsonFile.requiredInteger(
					root, "maximum_png_megapixels"
			);
			config.maximumJsonDepth = ShieldJsonFile.requiredInteger(
					root, "maximum_json_depth"
			);
			config.maximumPathLength = ShieldJsonFile.requiredInteger(
					root, "maximum_path_length"
			);
			config.maximumPathDepth = ShieldJsonFile.requiredInteger(
					root, "maximum_path_depth"
			);
			config.maximumScanMillis = ShieldJsonFile.requiredInteger(
					root, "maximum_scan_millis"
			);
			config.showTransitionNotifications = ShieldJsonFile.requiredBoolean(
					root, "show_transition_notifications"
			);
			config.writeSanitizedLocalReport = ShieldJsonFile.requiredBoolean(
					root, "write_sanitized_local_report"
			);
			config.showInspectorStatistics = ShieldJsonFile.requiredBoolean(
					root, "show_inspector_statistics"
			);
			config.validateStoredValues();
			config.wasEverEnabled = config.resourcePackShieldEnabled;
			return config;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			if (LOAD_FAILURE_LOGGED.compareAndSet(false, true)) {
				SodiumVolt.LOGGER.warn("Ignoring invalid Resource-Pack Shield configuration");
			}
			return new ResourcePackShieldConfig();
		}
	}

	JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("version", CONFIG_VERSION);
		root.addProperty("resource_pack_shield_enabled", this.resourcePackShieldEnabled);
		root.addProperty("monitor_local_packs", this.monitorLocalPacks);
		root.addProperty("monitor_server_packs", this.monitorServerPacks);
		root.addProperty(
				"detect_unsafe_paths_and_symlinks", this.detectUnsafePathsAndSymlinks
		);
		root.addProperty("block_core_shader_overrides", this.blockCoreShaderOverrides);
		root.addProperty("reject_violations", this.rejectViolations);
		root.addProperty("maximum_entries", this.maximumEntries);
		root.addProperty("maximum_archive_mib", this.maximumArchiveMiB);
		root.addProperty("maximum_single_resource_mib", this.maximumSingleResourceMiB);
		root.addProperty("maximum_total_read_mib", this.maximumTotalReadMiB);
		root.addProperty("maximum_compression_ratio", this.maximumCompressionRatio);
		root.addProperty("maximum_png_dimension", this.maximumPngDimension);
		root.addProperty("maximum_png_megapixels", this.maximumPngMegapixels);
		root.addProperty("maximum_json_depth", this.maximumJsonDepth);
		root.addProperty("maximum_path_length", this.maximumPathLength);
		root.addProperty("maximum_path_depth", this.maximumPathDepth);
		root.addProperty("maximum_scan_millis", this.maximumScanMillis);
		root.addProperty("show_transition_notifications", this.showTransitionNotifications);
		root.addProperty("write_sanitized_local_report", this.writeSanitizedLocalReport);
		root.addProperty("show_inspector_statistics", this.showInspectorStatistics);
		return root;
	}

	private void validateStoredValues() {
		requireStep(this.maximumEntries, ENTRY_LIMIT_MIN, ENTRY_LIMIT_MAX, ENTRY_LIMIT_STEP);
		requireStep(
				this.maximumArchiveMiB,
				ARCHIVE_MIB_MIN,
				ARCHIVE_MIB_MAX,
				ARCHIVE_MIB_STEP
		);
		requireStep(this.maximumSingleResourceMiB, SINGLE_MIB_MIN, SINGLE_MIB_MAX, 1);
		requireStep(
				this.maximumTotalReadMiB, TOTAL_MIB_MIN, TOTAL_MIB_MAX, TOTAL_MIB_STEP
		);
		requireStep(
				this.maximumCompressionRatio, RATIO_MIN, RATIO_MAX, RATIO_STEP
		);
		requireStep(
				this.maximumPngDimension,
				PNG_DIMENSION_MIN,
				PNG_DIMENSION_MAX,
				PNG_DIMENSION_STEP
		);
		requireStep(
				this.maximumPngMegapixels,
				PNG_MEGAPIXELS_MIN,
				PNG_MEGAPIXELS_MAX,
				PNG_MEGAPIXELS_STEP
		);
		requireStep(
				this.maximumJsonDepth, JSON_DEPTH_MIN, JSON_DEPTH_MAX, JSON_DEPTH_STEP
		);
		requireStep(
				this.maximumPathLength, PATH_LENGTH_MIN, PATH_LENGTH_MAX, PATH_LENGTH_STEP
		);
		requireStep(
				this.maximumPathDepth, PATH_DEPTH_MIN, PATH_DEPTH_MAX, PATH_DEPTH_STEP
		);
		requireStep(
				this.maximumScanMillis, SCAN_MILLIS_MIN, SCAN_MILLIS_MAX, SCAN_MILLIS_STEP
		);
		if (this.maximumSingleResourceMiB > this.maximumTotalReadMiB) {
			throw new IllegalArgumentException("Single-resource limit exceeds total limit");
		}
	}

	private void normalize() {
		setWithoutRevision();
	}

	private void setWithoutRevision() {
		this.maximumEntries = clampStep(
				this.maximumEntries, ENTRY_LIMIT_MIN, ENTRY_LIMIT_MAX, ENTRY_LIMIT_STEP
		);
		this.maximumArchiveMiB = clampStep(
				this.maximumArchiveMiB, ARCHIVE_MIB_MIN, ARCHIVE_MIB_MAX, ARCHIVE_MIB_STEP
		);
		this.maximumSingleResourceMiB = clampStep(
				this.maximumSingleResourceMiB, SINGLE_MIB_MIN, SINGLE_MIB_MAX, 1
		);
		this.maximumTotalReadMiB = validTotalAtLeast(
				this.maximumTotalReadMiB, this.maximumSingleResourceMiB
		);
		this.maximumCompressionRatio = clampStep(
				this.maximumCompressionRatio, RATIO_MIN, RATIO_MAX, RATIO_STEP
		);
		this.maximumPngDimension = clampStep(
				this.maximumPngDimension,
				PNG_DIMENSION_MIN,
				PNG_DIMENSION_MAX,
				PNG_DIMENSION_STEP
		);
		this.maximumPngMegapixels = clampStep(
				this.maximumPngMegapixels,
				PNG_MEGAPIXELS_MIN,
				PNG_MEGAPIXELS_MAX,
				PNG_MEGAPIXELS_STEP
		);
		this.maximumJsonDepth = clampStep(
				this.maximumJsonDepth, JSON_DEPTH_MIN, JSON_DEPTH_MAX, JSON_DEPTH_STEP
		);
		this.maximumPathLength = clampStep(
				this.maximumPathLength, PATH_LENGTH_MIN, PATH_LENGTH_MAX, PATH_LENGTH_STEP
		);
		this.maximumPathDepth = clampStep(
				this.maximumPathDepth, PATH_DEPTH_MIN, PATH_DEPTH_MAX, PATH_DEPTH_STEP
		);
		this.maximumScanMillis = clampStep(
				this.maximumScanMillis, SCAN_MILLIS_MIN, SCAN_MILLIS_MAX, SCAN_MILLIS_STEP
		);
	}

	private static void requireStep(int value, int minimum, int maximum, int step) {
		if (value < minimum || value > maximum || (value - minimum) % step != 0) {
			throw new IllegalArgumentException("Out-of-range Resource-Pack Shield value");
		}
	}

	private static int clampStep(int value, int minimum, int maximum, int step) {
		int clamped = Math.max(minimum, Math.min(maximum, value));
		return minimum + Math.round((clamped - minimum) / (float) step) * step;
	}

	private static int validTotalAtLeast(int requestedTotal, int singleResource) {
		int total = clampStep(
				requestedTotal, TOTAL_MIB_MIN, TOTAL_MIB_MAX, TOTAL_MIB_STEP
		);
		if (total >= singleResource) {
			return total;
		}
		int steps = (singleResource - TOTAL_MIB_MIN + TOTAL_MIB_STEP - 1)
				/ TOTAL_MIB_STEP;
		return Math.min(TOTAL_MIB_MAX, TOTAL_MIB_MIN + steps * TOTAL_MIB_STEP);
	}

	private static long mib(int value) {
		return (long) value * 1024L * 1024L;
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir()
				.resolve("sodium-volt-resource-pack-shield.json");
	}

	private static final class Holder {
		private static final ResourcePackShieldConfig INSTANCE =
				ResourcePackShieldConfig.load(configPath());
	}
}
