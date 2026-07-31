package com.ragedriver.sodiumvolt.client.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.performance.ApcConfigNormalization;
import com.ragedriver.sodiumvolt.client.performance.AttExemptionParsing;
import com.ragedriver.sodiumvolt.client.performance.BlockEntityBudgetNormalization;
import com.ragedriver.sodiumvolt.client.performance.VramConfigNormalization;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public final class VoltPerformanceConfig {
	public static final int TARGET_FPS_MIN = 30;
	public static final int TARGET_FPS_MAX = 260;
	public static final int TARGET_FPS_STEP = 10;
	public static final int TARGET_FPS_DEFAULT = 60;
	public static final int MIN_RENDER_DISTANCE_MIN = 4;
	public static final int MIN_RENDER_DISTANCE_MAX = 16;
	public static final int MIN_RENDER_DISTANCE_DEFAULT = 6;
	public static final int MAX_RENDER_DISTANCE_MIN = 8;
	public static final int MAX_RENDER_DISTANCE_MAX = 32;
	public static final int MAX_RENDER_DISTANCE_DEFAULT = 24;
	public static final int ADJUSTMENT_INTERVAL_MIN = 1;
	public static final int ADJUSTMENT_INTERVAL_MAX = 10;
	public static final int ADJUSTMENT_INTERVAL_DEFAULT = 2;
	public static final int RECOVERY_DELAY_MIN = 5;
	public static final int RECOVERY_DELAY_MAX = 60;
	public static final int RECOVERY_DELAY_DEFAULT = 15;
	public static final int FPS_TOLERANCE_MIN = 2;
	public static final int FPS_TOLERANCE_MAX = 20;
	public static final int FPS_TOLERANCE_DEFAULT = 5;
	public static final int SAMPLE_WINDOW_MIN = 120;
	public static final int SAMPLE_WINDOW_MAX = 600;
	public static final int SAMPLE_WINDOW_STEP = 60;
	public static final int SAMPLE_WINDOW_DEFAULT = 240;
	public static final int VAPS_FULL_RATE_DISTANCE_MIN = 8;
	public static final int VAPS_FULL_RATE_DISTANCE_MAX = 64;
	public static final int VAPS_FULL_RATE_DISTANCE_STEP = 4;
	public static final int VAPS_FULL_RATE_DISTANCE_DEFAULT = 24;
	public static final int VAPS_FAR_TICK_INTERVAL_MIN = 2;
	public static final int VAPS_FAR_TICK_INTERVAL_MAX = 8;
	public static final int VAPS_FAR_TICK_INTERVAL_DEFAULT = 4;
	public static final int VAPS_PER_TYPE_RENDER_LIMIT_MIN = 32;
	public static final int VAPS_PER_TYPE_RENDER_LIMIT_MAX = 1024;
	public static final int VAPS_PER_TYPE_RENDER_LIMIT_STEP = 32;
	public static final int VAPS_PER_TYPE_RENDER_LIMIT_DEFAULT = 256;
	public static final int VAPS_AMBIENT_PER_CELL_MIN = 1;
	public static final int VAPS_AMBIENT_PER_CELL_MAX = 16;
	public static final int VAPS_AMBIENT_PER_CELL_DEFAULT = 4;
	public static final int VAPS_CRITICAL_RESERVE_MIN = 16;
	public static final int VAPS_CRITICAL_RESERVE_MAX = 256;
	public static final int VAPS_CRITICAL_RESERVE_STEP = 16;
	public static final int VAPS_CRITICAL_RESERVE_DEFAULT = 64;
	public static final int BERP_NEAR_DISTANCE_MIN = 8;
	public static final int BERP_NEAR_DISTANCE_MAX = 64;
	public static final int BERP_NEAR_DISTANCE_STEP = 4;
	public static final int BERP_NEAR_DISTANCE_DEFAULT = 24;
	public static final int BERP_MEDIUM_DISTANCE_MIN = 24;
	public static final int BERP_MEDIUM_DISTANCE_MAX = 128;
	public static final int BERP_MEDIUM_DISTANCE_STEP = 4;
	public static final int BERP_MEDIUM_DISTANCE_DEFAULT = 48;
	public static final int BERP_MEDIUM_INTERVAL_MIN = 2;
	public static final int BERP_MEDIUM_INTERVAL_MAX = 10;
	public static final int BERP_MEDIUM_INTERVAL_DEFAULT = 3;
	public static final int BERP_FAR_INTERVAL_MIN = 4;
	public static final int BERP_FAR_INTERVAL_MAX = 20;
	public static final int BERP_FAR_INTERVAL_DEFAULT = 8;
	public static final int BERP_FAR_DISTANCE_MIN = 48;
	public static final int BERP_FAR_DISTANCE_MAX = 256;
	public static final int BERP_FAR_DISTANCE_STEP = 8;
	public static final int BERP_FAR_DISTANCE_DEFAULT = 96;
	public static final int BERP_GLOBAL_BUDGET_MIN = 64;
	public static final int BERP_GLOBAL_BUDGET_MAX = 2048;
	public static final int BERP_GLOBAL_BUDGET_STEP = 64;
	public static final int BERP_GLOBAL_BUDGET_DEFAULT = 512;
	public static final int BERP_PER_TYPE_LIMIT_MIN = 16;
	public static final int BERP_PER_TYPE_LIMIT_MAX = 512;
	public static final int BERP_PER_TYPE_LIMIT_STEP = 16;
	public static final int BERP_PER_TYPE_LIMIT_DEFAULT = 128;
	public static final int BERP_GRACE_SECONDS_MIN = 1;
	public static final int BERP_GRACE_SECONDS_MAX = 10;
	public static final int BERP_GRACE_SECONDS_DEFAULT = 3;
	public static final int BERP_CACHE_CAPACITY_MIN = 256;
	public static final int BERP_CACHE_CAPACITY_MAX = 4096;
	public static final int BERP_CACHE_CAPACITY_STEP = 256;
	public static final int BERP_CACHE_CAPACITY_DEFAULT = 1024;
	public static final int ATT_FULL_SPEED_DISTANCE_MIN = 8;
	public static final int ATT_FULL_SPEED_DISTANCE_MAX = 128;
	public static final int ATT_FULL_SPEED_DISTANCE_STEP = 8;
	public static final int ATT_FULL_SPEED_DISTANCE_DEFAULT = 32;
	public static final int ATT_DISTANT_INTERVAL_MIN = 2;
	public static final int ATT_DISTANT_INTERVAL_MAX = 12;
	public static final int ATT_DISTANT_INTERVAL_DEFAULT = 4;
	public static final int ATT_UNSEEN_KEEPALIVE_MIN = 0;
	public static final int ATT_UNSEEN_KEEPALIVE_MAX = 100;
	public static final int ATT_UNSEEN_KEEPALIVE_STEP = 5;
	public static final int ATT_UNSEEN_KEEPALIVE_DEFAULT = 0;
	public static final int ATT_PER_ATLAS_BUDGET_MIN = 32;
	public static final int ATT_PER_ATLAS_BUDGET_MAX = 2048;
	public static final int ATT_PER_ATLAS_BUDGET_STEP = 32;
	public static final int ATT_PER_ATLAS_BUDGET_DEFAULT = 256;
	public static final int VRAM_MANUAL_BUDGET_MIN = 512;
	public static final int VRAM_MANUAL_BUDGET_MAX = 24_576;
	public static final int VRAM_MANUAL_BUDGET_STEP = 256;
	public static final int VRAM_MANUAL_BUDGET_DEFAULT = 4_096;
	public static final int VRAM_PROTECTION_THRESHOLD_MIN = 60;
	public static final int VRAM_PROTECTION_THRESHOLD_MAX = 90;
	public static final int VRAM_PROTECTION_THRESHOLD_STEP = 5;
	public static final int VRAM_PROTECTION_THRESHOLD_DEFAULT = 80;
	public static final int VRAM_CRITICAL_THRESHOLD_MIN = 75;
	public static final int VRAM_CRITICAL_THRESHOLD_MAX = 98;
	public static final int VRAM_CRITICAL_THRESHOLD_DEFAULT = 92;
	public static final int VRAM_SAFETY_HEADROOM_MIN = 5;
	public static final int VRAM_SAFETY_HEADROOM_MAX = 40;
	public static final int VRAM_SAFETY_HEADROOM_STEP = 5;
	public static final int VRAM_SAFETY_HEADROOM_DEFAULT = 15;
	public static final int VRAM_FIXED_RESERVE_MIN = 128;
	public static final int VRAM_FIXED_RESERVE_MAX = 2_048;
	public static final int VRAM_FIXED_RESERVE_STEP = 128;
	public static final int VRAM_FIXED_RESERVE_DEFAULT = 512;
	public static final int VRAM_MIN_SAFE_RENDER_DISTANCE_MIN = 4;
	public static final int VRAM_MIN_SAFE_RENDER_DISTANCE_MAX = 16;
	public static final int VRAM_MIN_SAFE_RENDER_DISTANCE_DEFAULT = 6;
	public static final int VRAM_SAMPLE_INTERVAL_MIN = 1;
	public static final int VRAM_SAMPLE_INTERVAL_MAX = 10;
	public static final int VRAM_SAMPLE_INTERVAL_DEFAULT = 2;
	public static final int VRAM_SUSTAINED_SAMPLES_MIN = 2;
	public static final int VRAM_SUSTAINED_SAMPLES_MAX = 10;
	public static final int VRAM_SUSTAINED_SAMPLES_DEFAULT = 3;
	public static final int VRAM_RENDER_STEP_INTERVAL_MIN = 2;
	public static final int VRAM_RENDER_STEP_INTERVAL_MAX = 30;
	public static final int VRAM_RENDER_STEP_INTERVAL_DEFAULT = 5;
	public static final int VRAM_RECOVERY_DELAY_MIN = 10;
	public static final int VRAM_RECOVERY_DELAY_MAX = 120;
	public static final int VRAM_RECOVERY_DELAY_STEP = 5;
	public static final int VRAM_RECOVERY_DELAY_DEFAULT = 30;
	public static final int VRAM_ALLOCATION_SPIKE_MIN = 32;
	public static final int VRAM_ALLOCATION_SPIKE_MAX = 1_024;
	public static final int VRAM_ALLOCATION_SPIKE_STEP = 32;
	public static final int VRAM_ALLOCATION_SPIKE_DEFAULT = 256;

	private static final int CONFIG_VERSION = 5;
	private static final long MAX_CONFIG_SIZE_BYTES = 1024L * 1024L;
	private static final Gson GSON = new GsonBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.setPrettyPrinting()
			.create();

	private final int version = CONFIG_VERSION;
	private volatile boolean adaptivePerformanceControllerEnabled;
	private volatile Profile profile = Profile.BALANCED;
	private volatile int targetFps = TARGET_FPS_DEFAULT;
	private volatile boolean adaptiveRenderDistance = true;
	private volatile boolean adaptiveEntityDistance = true;
	private volatile boolean adaptiveParticleQuality = true;
	private volatile boolean adaptiveVisualEffects = true;
	private volatile boolean adaptiveAnimationThrottling;
	private volatile boolean restoreOriginalSettings = true;
	private volatile boolean showControllerNotifications = true;
	private volatile int minimumRenderDistance = MIN_RENDER_DISTANCE_DEFAULT;
	private volatile int maximumRenderDistance = MAX_RENDER_DISTANCE_DEFAULT;
	private volatile int adjustmentIntervalSeconds = ADJUSTMENT_INTERVAL_DEFAULT;
	private volatile int qualityRecoveryDelaySeconds = RECOVERY_DELAY_DEFAULT;
	private volatile int fpsTolerance = FPS_TOLERANCE_DEFAULT;
	private volatile int sampleWindow = SAMPLE_WINDOW_DEFAULT;
	private volatile boolean visibilityAwareParticleSchedulerEnabled;
	private volatile boolean vapsPrioritizeInFrustum = true;
	private volatile boolean vapsSkipBehindCamera = true;
	private volatile boolean vapsDistanceAwareSimulation = true;
	private volatile boolean vapsPreserveCriticalParticles = true;
	private volatile boolean vapsCoalesceAmbientParticles = true;
	private volatile boolean vapsPerTypeRenderLimits = true;
	private volatile boolean vapsShowInspectorStatistics = true;
	private volatile int vapsFullRateDistance = VAPS_FULL_RATE_DISTANCE_DEFAULT;
	private volatile int vapsFarTickInterval = VAPS_FAR_TICK_INTERVAL_DEFAULT;
	private volatile int vapsPerTypeRenderLimit = VAPS_PER_TYPE_RENDER_LIMIT_DEFAULT;
	private volatile int vapsAmbientPerCell = VAPS_AMBIENT_PER_CELL_DEFAULT;
	private volatile int vapsCriticalReserve = VAPS_CRITICAL_RESERVE_DEFAULT;
	private volatile boolean blockEntityRenderBudgetingEnabled;
	private volatile boolean berpPrioritizeNearby = true;
	private volatile boolean berpRecentInteractionGrace = true;
	private volatile boolean berpDistanceAwareStateUpdates = true;
	private volatile boolean berpCacheFarRenderStates = true;
	private volatile boolean berpPerTypeRenderLimits = true;
	private volatile boolean berpCullBeyondFarDistance;
	private volatile boolean berpIncludeModdedBlockEntities;
	private volatile boolean berpShowInspectorStatistics = true;
	private volatile int berpNearDistance = BERP_NEAR_DISTANCE_DEFAULT;
	private volatile int berpMediumDistance = BERP_MEDIUM_DISTANCE_DEFAULT;
	private volatile int berpMediumUpdateInterval = BERP_MEDIUM_INTERVAL_DEFAULT;
	private volatile int berpFarUpdateInterval = BERP_FAR_INTERVAL_DEFAULT;
	private volatile int berpFarRenderDistance = BERP_FAR_DISTANCE_DEFAULT;
	private volatile int berpGlobalRenderBudget = BERP_GLOBAL_BUDGET_DEFAULT;
	private volatile int berpPerTypeRenderLimit = BERP_PER_TYPE_LIMIT_DEFAULT;
	private volatile int berpInteractionGraceSeconds = BERP_GRACE_SECONDS_DEFAULT;
	private volatile int berpCacheCapacity = BERP_CACHE_CAPACITY_DEFAULT;
	private volatile boolean animatedTextureThrottlingEnabled;
	private volatile boolean attPauseInvisibleAnimations = true;
	private volatile boolean attDistanceAwareCadence = true;
	private volatile boolean attKeepInterfaceAtlasesFullSpeed = true;
	private volatile boolean attExemptCriticalVanillaTextures = true;
	private volatile boolean attHonorExemptionLists = true;
	private volatile boolean attImmediateSmoothResume = true;
	private volatile boolean attShowInspectorStatistics = true;
	private volatile int attFullSpeedDistance = ATT_FULL_SPEED_DISTANCE_DEFAULT;
	private volatile int attDistantUpdateInterval = ATT_DISTANT_INTERVAL_DEFAULT;
	private volatile int attUnseenKeepaliveTicks = ATT_UNSEEN_KEEPALIVE_DEFAULT;
	private volatile int attPerAtlasAnimationBudget = ATT_PER_ATLAS_BUDGET_DEFAULT;
	private volatile String[] attUserExemptTextures = new String[0];
	private volatile boolean vramPressureProtectionEnabled;
	private volatile boolean vramAutoDetectBudget = true;
	private volatile boolean vramApplySafeRenderDistanceProfile = true;
	private volatile boolean vramRespondToAllocationSpikes = true;
	private volatile boolean vramRestoreQualityAfterRecovery = true;
	private volatile boolean vramShowPressureWarnings = true;
	private volatile boolean vramAccountForHeadroom = true;
	private volatile boolean vramShowInspectorStatistics = true;
	private volatile int vramManualBudgetMib = VRAM_MANUAL_BUDGET_DEFAULT;
	private volatile int vramProtectionThresholdPercent = VRAM_PROTECTION_THRESHOLD_DEFAULT;
	private volatile int vramCriticalThresholdPercent = VRAM_CRITICAL_THRESHOLD_DEFAULT;
	private volatile int vramSafetyHeadroomPercent = VRAM_SAFETY_HEADROOM_DEFAULT;
	private volatile int vramFixedReserveMib = VRAM_FIXED_RESERVE_DEFAULT;
	private volatile int vramMinimumSafeRenderDistance = VRAM_MIN_SAFE_RENDER_DISTANCE_DEFAULT;
	private volatile int vramSampleIntervalSeconds = VRAM_SAMPLE_INTERVAL_DEFAULT;
	private volatile int vramSustainedSamples = VRAM_SUSTAINED_SAMPLES_DEFAULT;
	private volatile int vramRenderDistanceStepIntervalSeconds = VRAM_RENDER_STEP_INTERVAL_DEFAULT;
	private volatile int vramRecoveryDelaySeconds = VRAM_RECOVERY_DELAY_DEFAULT;
	private volatile int vramLargeAllocationSpikeMib = VRAM_ALLOCATION_SPIKE_DEFAULT;

	private VoltPerformanceConfig() {
	}

	public static VoltPerformanceConfig getInstance() {
		return Holder.INSTANCE;
	}

	static VoltPerformanceConfig createForTest() {
		return new VoltPerformanceConfig();
	}

	public boolean isAdaptivePerformanceControllerEnabled() {
		return this.adaptivePerformanceControllerEnabled;
	}

	public void setAdaptivePerformanceControllerEnabled(boolean value) {
		this.adaptivePerformanceControllerEnabled = value;
	}

	public Profile getProfile() {
		return this.profile;
	}

	public void setProfile(Profile value) {
		this.profile = value == null ? Profile.BALANCED : value;
	}

	public int getTargetFps() {
		return this.targetFps;
	}

	public void setTargetFps(int value) {
		this.targetFps = clampToStep(value, TARGET_FPS_MIN, TARGET_FPS_MAX, TARGET_FPS_STEP);
	}

	public boolean isAdaptiveRenderDistance() {
		return this.adaptiveRenderDistance;
	}

	public void setAdaptiveRenderDistance(boolean value) {
		this.adaptiveRenderDistance = value;
	}

	public boolean isAdaptiveEntityDistance() {
		return this.adaptiveEntityDistance;
	}

	public void setAdaptiveEntityDistance(boolean value) {
		this.adaptiveEntityDistance = value;
	}

	public boolean isAdaptiveParticleQuality() {
		return this.adaptiveParticleQuality;
	}

	public void setAdaptiveParticleQuality(boolean value) {
		this.adaptiveParticleQuality = value;
	}

	public boolean isAdaptiveVisualEffects() {
		return this.adaptiveVisualEffects;
	}

	public void setAdaptiveVisualEffects(boolean value) {
		this.adaptiveVisualEffects = value;
	}

	public boolean isAdaptiveAnimationThrottling() {
		return this.adaptiveAnimationThrottling;
	}

	public void setAdaptiveAnimationThrottling(boolean value) {
		this.adaptiveAnimationThrottling = value;
	}

	public boolean isRestoreOriginalSettings() {
		return this.restoreOriginalSettings;
	}

	public void setRestoreOriginalSettings(boolean value) {
		this.restoreOriginalSettings = value;
	}

	public boolean isShowControllerNotifications() {
		return this.showControllerNotifications;
	}

	public void setShowControllerNotifications(boolean value) {
		this.showControllerNotifications = value;
	}

	public int getMinimumRenderDistance() {
		return this.minimumRenderDistance;
	}

	public void setMinimumRenderDistance(int value) {
		this.minimumRenderDistance = clamp(value, MIN_RENDER_DISTANCE_MIN, MIN_RENDER_DISTANCE_MAX);
		if (this.minimumRenderDistance > this.maximumRenderDistance) {
			this.minimumRenderDistance = this.maximumRenderDistance;
		}
	}

	public int getMaximumRenderDistance() {
		return this.maximumRenderDistance;
	}

	public void setMaximumRenderDistance(int value) {
		this.maximumRenderDistance = clamp(value, MAX_RENDER_DISTANCE_MIN, MAX_RENDER_DISTANCE_MAX);
		if (this.maximumRenderDistance < this.minimumRenderDistance) {
			this.maximumRenderDistance = this.minimumRenderDistance;
		}
	}

	public int getAdjustmentIntervalSeconds() {
		return this.adjustmentIntervalSeconds;
	}

	public void setAdjustmentIntervalSeconds(int value) {
		this.adjustmentIntervalSeconds = clamp(value, ADJUSTMENT_INTERVAL_MIN, ADJUSTMENT_INTERVAL_MAX);
	}

	public int getQualityRecoveryDelaySeconds() {
		return this.qualityRecoveryDelaySeconds;
	}

	public void setQualityRecoveryDelaySeconds(int value) {
		this.qualityRecoveryDelaySeconds = clamp(value, RECOVERY_DELAY_MIN, RECOVERY_DELAY_MAX);
	}

	public int getFpsTolerance() {
		return this.fpsTolerance;
	}

	public void setFpsTolerance(int value) {
		this.fpsTolerance = clamp(value, FPS_TOLERANCE_MIN, FPS_TOLERANCE_MAX);
	}

	public int getSampleWindow() {
		return this.sampleWindow;
	}

	public void setSampleWindow(int value) {
		this.sampleWindow = clampToStep(value, SAMPLE_WINDOW_MIN, SAMPLE_WINDOW_MAX, SAMPLE_WINDOW_STEP);
	}

	public boolean isVisibilityAwareParticleSchedulerEnabled() {
		return this.visibilityAwareParticleSchedulerEnabled;
	}

	public void setVisibilityAwareParticleSchedulerEnabled(boolean value) {
		this.visibilityAwareParticleSchedulerEnabled = value;
	}

	public boolean isVapsPrioritizeInFrustum() {
		return this.vapsPrioritizeInFrustum;
	}

	public void setVapsPrioritizeInFrustum(boolean value) {
		this.vapsPrioritizeInFrustum = value;
	}

	public boolean isVapsSkipBehindCamera() {
		return this.vapsSkipBehindCamera;
	}

	public void setVapsSkipBehindCamera(boolean value) {
		this.vapsSkipBehindCamera = value;
	}

	public boolean isVapsDistanceAwareSimulation() {
		return this.vapsDistanceAwareSimulation;
	}

	public void setVapsDistanceAwareSimulation(boolean value) {
		this.vapsDistanceAwareSimulation = value;
	}

	public boolean isVapsPreserveCriticalParticles() {
		return this.vapsPreserveCriticalParticles;
	}

	public void setVapsPreserveCriticalParticles(boolean value) {
		this.vapsPreserveCriticalParticles = value;
	}

	public boolean isVapsCoalesceAmbientParticles() {
		return this.vapsCoalesceAmbientParticles;
	}

	public void setVapsCoalesceAmbientParticles(boolean value) {
		this.vapsCoalesceAmbientParticles = value;
	}

	public boolean isVapsPerTypeRenderLimits() {
		return this.vapsPerTypeRenderLimits;
	}

	public void setVapsPerTypeRenderLimits(boolean value) {
		this.vapsPerTypeRenderLimits = value;
	}

	public boolean isVapsShowInspectorStatistics() {
		return this.vapsShowInspectorStatistics;
	}

	public void setVapsShowInspectorStatistics(boolean value) {
		this.vapsShowInspectorStatistics = value;
	}

	public int getVapsFullRateDistance() {
		return this.vapsFullRateDistance;
	}

	public void setVapsFullRateDistance(int value) {
		this.vapsFullRateDistance = clampToStep(
				value,
				VAPS_FULL_RATE_DISTANCE_MIN,
				VAPS_FULL_RATE_DISTANCE_MAX,
				VAPS_FULL_RATE_DISTANCE_STEP
		);
	}

	public int getVapsFarTickInterval() {
		return this.vapsFarTickInterval;
	}

	public void setVapsFarTickInterval(int value) {
		this.vapsFarTickInterval = clamp(
				value,
				VAPS_FAR_TICK_INTERVAL_MIN,
				VAPS_FAR_TICK_INTERVAL_MAX
		);
	}

	public int getVapsPerTypeRenderLimit() {
		return this.vapsPerTypeRenderLimit;
	}

	public void setVapsPerTypeRenderLimit(int value) {
		this.vapsPerTypeRenderLimit = clampToStep(
				value,
				VAPS_PER_TYPE_RENDER_LIMIT_MIN,
				VAPS_PER_TYPE_RENDER_LIMIT_MAX,
				VAPS_PER_TYPE_RENDER_LIMIT_STEP
		);
	}

	public int getVapsAmbientPerCell() {
		return this.vapsAmbientPerCell;
	}

	public void setVapsAmbientPerCell(int value) {
		this.vapsAmbientPerCell = clamp(
				value,
				VAPS_AMBIENT_PER_CELL_MIN,
				VAPS_AMBIENT_PER_CELL_MAX
		);
	}

	public int getVapsCriticalReserve() {
		return this.vapsCriticalReserve;
	}

	public void setVapsCriticalReserve(int value) {
		this.vapsCriticalReserve = clampToStep(
				value,
				VAPS_CRITICAL_RESERVE_MIN,
				VAPS_CRITICAL_RESERVE_MAX,
				VAPS_CRITICAL_RESERVE_STEP
		);
	}

	public boolean isBlockEntityRenderBudgetingEnabled() {
		return this.blockEntityRenderBudgetingEnabled;
	}

	public void setBlockEntityRenderBudgetingEnabled(boolean value) {
		this.blockEntityRenderBudgetingEnabled = value;
	}

	public boolean isBerpPrioritizeNearby() {
		return this.berpPrioritizeNearby;
	}

	public void setBerpPrioritizeNearby(boolean value) {
		this.berpPrioritizeNearby = value;
	}

	public boolean isBerpRecentInteractionGrace() {
		return this.berpRecentInteractionGrace;
	}

	public void setBerpRecentInteractionGrace(boolean value) {
		this.berpRecentInteractionGrace = value;
	}

	public boolean isBerpDistanceAwareStateUpdates() {
		return this.berpDistanceAwareStateUpdates;
	}

	public void setBerpDistanceAwareStateUpdates(boolean value) {
		this.berpDistanceAwareStateUpdates = value;
	}

	public boolean isBerpCacheFarRenderStates() {
		return this.berpCacheFarRenderStates;
	}

	public void setBerpCacheFarRenderStates(boolean value) {
		this.berpCacheFarRenderStates = value;
	}

	public boolean isBerpPerTypeRenderLimits() {
		return this.berpPerTypeRenderLimits;
	}

	public void setBerpPerTypeRenderLimits(boolean value) {
		this.berpPerTypeRenderLimits = value;
	}

	public boolean isBerpCullBeyondFarDistance() {
		return this.berpCullBeyondFarDistance;
	}

	public void setBerpCullBeyondFarDistance(boolean value) {
		this.berpCullBeyondFarDistance = value;
	}

	public boolean isBerpIncludeModdedBlockEntities() {
		return this.berpIncludeModdedBlockEntities;
	}

	public void setBerpIncludeModdedBlockEntities(boolean value) {
		this.berpIncludeModdedBlockEntities = value;
	}

	public boolean isBerpShowInspectorStatistics() {
		return this.berpShowInspectorStatistics;
	}

	public void setBerpShowInspectorStatistics(boolean value) {
		this.berpShowInspectorStatistics = value;
	}

	public int getBerpNearDistance() {
		return this.berpNearDistance;
	}

	public void setBerpNearDistance(int value) {
		this.berpNearDistance = clampToStep(
				value, BERP_NEAR_DISTANCE_MIN, BERP_NEAR_DISTANCE_MAX, BERP_NEAR_DISTANCE_STEP
		);
		if (this.berpMediumDistance < this.berpNearDistance) {
			this.berpMediumDistance = this.berpNearDistance;
		}
		if (this.berpFarRenderDistance < this.berpMediumDistance) {
			this.berpFarRenderDistance = this.berpMediumDistance;
		}
	}

	public int getBerpMediumDistance() {
		return this.berpMediumDistance;
	}

	public void setBerpMediumDistance(int value) {
		this.berpMediumDistance = Math.max(
				this.berpNearDistance,
				clampToStep(
						value,
						BERP_MEDIUM_DISTANCE_MIN,
						BERP_MEDIUM_DISTANCE_MAX,
						BERP_MEDIUM_DISTANCE_STEP
				)
		);
		if (this.berpFarRenderDistance < this.berpMediumDistance) {
			this.berpFarRenderDistance = this.berpMediumDistance;
		}
	}

	public int getBerpMediumUpdateInterval() {
		return this.berpMediumUpdateInterval;
	}

	public void setBerpMediumUpdateInterval(int value) {
		this.berpMediumUpdateInterval = clamp(value, BERP_MEDIUM_INTERVAL_MIN, BERP_MEDIUM_INTERVAL_MAX);
	}

	public int getBerpFarUpdateInterval() {
		return this.berpFarUpdateInterval;
	}

	public void setBerpFarUpdateInterval(int value) {
		this.berpFarUpdateInterval = clamp(value, BERP_FAR_INTERVAL_MIN, BERP_FAR_INTERVAL_MAX);
	}

	public int getBerpFarRenderDistance() {
		return this.berpFarRenderDistance;
	}

	public void setBerpFarRenderDistance(int value) {
		this.berpFarRenderDistance = Math.max(
				this.berpMediumDistance,
				clampToStep(
						value,
						BERP_FAR_DISTANCE_MIN,
						BERP_FAR_DISTANCE_MAX,
						BERP_FAR_DISTANCE_STEP
				)
		);
	}

	public int getBerpGlobalRenderBudget() {
		return this.berpGlobalRenderBudget;
	}

	public void setBerpGlobalRenderBudget(int value) {
		this.berpGlobalRenderBudget = clampToStep(
				value, BERP_GLOBAL_BUDGET_MIN, BERP_GLOBAL_BUDGET_MAX, BERP_GLOBAL_BUDGET_STEP
		);
	}

	public int getBerpPerTypeRenderLimit() {
		return this.berpPerTypeRenderLimit;
	}

	public void setBerpPerTypeRenderLimit(int value) {
		this.berpPerTypeRenderLimit = clampToStep(
				value, BERP_PER_TYPE_LIMIT_MIN, BERP_PER_TYPE_LIMIT_MAX, BERP_PER_TYPE_LIMIT_STEP
		);
	}

	public int getBerpInteractionGraceSeconds() {
		return this.berpInteractionGraceSeconds;
	}

	public void setBerpInteractionGraceSeconds(int value) {
		this.berpInteractionGraceSeconds = clamp(value, BERP_GRACE_SECONDS_MIN, BERP_GRACE_SECONDS_MAX);
	}

	public int getBerpCacheCapacity() {
		return this.berpCacheCapacity;
	}

	public void setBerpCacheCapacity(int value) {
		this.berpCacheCapacity = clampToStep(
				value, BERP_CACHE_CAPACITY_MIN, BERP_CACHE_CAPACITY_MAX, BERP_CACHE_CAPACITY_STEP
		);
	}

	public boolean isAnimatedTextureThrottlingEnabled() {
		return this.animatedTextureThrottlingEnabled;
	}

	public void setAnimatedTextureThrottlingEnabled(boolean value) {
		this.animatedTextureThrottlingEnabled = value;
	}

	public boolean isAttPauseInvisibleAnimations() {
		return this.attPauseInvisibleAnimations;
	}

	public void setAttPauseInvisibleAnimations(boolean value) {
		this.attPauseInvisibleAnimations = value;
	}

	public boolean isAttDistanceAwareCadence() {
		return this.attDistanceAwareCadence;
	}

	public void setAttDistanceAwareCadence(boolean value) {
		this.attDistanceAwareCadence = value;
	}

	public boolean isAttKeepInterfaceAtlasesFullSpeed() {
		return this.attKeepInterfaceAtlasesFullSpeed;
	}

	public void setAttKeepInterfaceAtlasesFullSpeed(boolean value) {
		this.attKeepInterfaceAtlasesFullSpeed = value;
	}

	public boolean isAttExemptCriticalVanillaTextures() {
		return this.attExemptCriticalVanillaTextures;
	}

	public void setAttExemptCriticalVanillaTextures(boolean value) {
		this.attExemptCriticalVanillaTextures = value;
	}

	public boolean isAttHonorExemptionLists() {
		return this.attHonorExemptionLists;
	}

	public void setAttHonorExemptionLists(boolean value) {
		this.attHonorExemptionLists = value;
	}

	public boolean isAttImmediateSmoothResume() {
		return this.attImmediateSmoothResume;
	}

	public void setAttImmediateSmoothResume(boolean value) {
		this.attImmediateSmoothResume = value;
	}

	public boolean isAttShowInspectorStatistics() {
		return this.attShowInspectorStatistics;
	}

	public void setAttShowInspectorStatistics(boolean value) {
		this.attShowInspectorStatistics = value;
	}

	public int getAttFullSpeedDistance() {
		return this.attFullSpeedDistance;
	}

	public void setAttFullSpeedDistance(int value) {
		this.attFullSpeedDistance = clampToStep(
				value,
				ATT_FULL_SPEED_DISTANCE_MIN,
				ATT_FULL_SPEED_DISTANCE_MAX,
				ATT_FULL_SPEED_DISTANCE_STEP
		);
	}

	public int getAttDistantUpdateInterval() {
		return this.attDistantUpdateInterval;
	}

	public void setAttDistantUpdateInterval(int value) {
		this.attDistantUpdateInterval = clamp(
				value,
				ATT_DISTANT_INTERVAL_MIN,
				ATT_DISTANT_INTERVAL_MAX
		);
	}

	public int getAttUnseenKeepaliveTicks() {
		return this.attUnseenKeepaliveTicks;
	}

	public void setAttUnseenKeepaliveTicks(int value) {
		this.attUnseenKeepaliveTicks = clampToStep(
				value,
				ATT_UNSEEN_KEEPALIVE_MIN,
				ATT_UNSEEN_KEEPALIVE_MAX,
				ATT_UNSEEN_KEEPALIVE_STEP
		);
	}

	public int getAttPerAtlasAnimationBudget() {
		return this.attPerAtlasAnimationBudget;
	}

	public void setAttPerAtlasAnimationBudget(int value) {
		this.attPerAtlasAnimationBudget = clampToStep(
				value,
				ATT_PER_ATLAS_BUDGET_MIN,
				ATT_PER_ATLAS_BUDGET_MAX,
				ATT_PER_ATLAS_BUDGET_STEP
		);
	}

	public String[] getAttUserExemptTextures() {
		return this.attUserExemptTextures.clone();
	}

	public void setAttUserExemptTextures(String[] values) {
		this.attUserExemptTextures = AttExemptionParsing.normalizeUserEntries(values);
	}

	public boolean isVramPressureProtectionEnabled() {
		return this.vramPressureProtectionEnabled;
	}

	public void setVramPressureProtectionEnabled(boolean value) {
		this.vramPressureProtectionEnabled = value;
	}

	public boolean isVramAutoDetectBudget() {
		return this.vramAutoDetectBudget;
	}

	public void setVramAutoDetectBudget(boolean value) {
		this.vramAutoDetectBudget = value;
	}

	public boolean isVramApplySafeRenderDistanceProfile() {
		return this.vramApplySafeRenderDistanceProfile;
	}

	public void setVramApplySafeRenderDistanceProfile(boolean value) {
		this.vramApplySafeRenderDistanceProfile = value;
	}

	public boolean isVramRespondToAllocationSpikes() {
		return this.vramRespondToAllocationSpikes;
	}

	public void setVramRespondToAllocationSpikes(boolean value) {
		this.vramRespondToAllocationSpikes = value;
	}

	public boolean isVramRestoreQualityAfterRecovery() {
		return this.vramRestoreQualityAfterRecovery;
	}

	public void setVramRestoreQualityAfterRecovery(boolean value) {
		this.vramRestoreQualityAfterRecovery = value;
	}

	public boolean isVramShowPressureWarnings() {
		return this.vramShowPressureWarnings;
	}

	public void setVramShowPressureWarnings(boolean value) {
		this.vramShowPressureWarnings = value;
	}

	public boolean isVramAccountForHeadroom() {
		return this.vramAccountForHeadroom;
	}

	public void setVramAccountForHeadroom(boolean value) {
		this.vramAccountForHeadroom = value;
	}

	public boolean isVramShowInspectorStatistics() {
		return this.vramShowInspectorStatistics;
	}

	public void setVramShowInspectorStatistics(boolean value) {
		this.vramShowInspectorStatistics = value;
	}

	public int getVramManualBudgetMib() {
		return this.vramManualBudgetMib;
	}

	public void setVramManualBudgetMib(int value) {
		this.vramManualBudgetMib = VramConfigNormalization.clampStep(
				value, VRAM_MANUAL_BUDGET_MIN, VRAM_MANUAL_BUDGET_MAX, VRAM_MANUAL_BUDGET_STEP
		);
	}

	public int getVramProtectionThresholdPercent() {
		return this.vramProtectionThresholdPercent;
	}

	public void setVramProtectionThresholdPercent(int value) {
		VramConfigNormalization.Thresholds thresholds =
				VramConfigNormalization.normalizeThresholds(value, this.vramCriticalThresholdPercent);
		this.vramProtectionThresholdPercent = thresholds.protection();
		this.vramCriticalThresholdPercent = thresholds.critical();
	}

	public int getVramCriticalThresholdPercent() {
		return this.vramCriticalThresholdPercent;
	}

	public void setVramCriticalThresholdPercent(int value) {
		VramConfigNormalization.Thresholds thresholds =
				VramConfigNormalization.normalizeThresholds(this.vramProtectionThresholdPercent, value);
		this.vramProtectionThresholdPercent = thresholds.protection();
		this.vramCriticalThresholdPercent = thresholds.critical();
	}

	public int getVramSafetyHeadroomPercent() {
		return this.vramSafetyHeadroomPercent;
	}

	public void setVramSafetyHeadroomPercent(int value) {
		this.vramSafetyHeadroomPercent = VramConfigNormalization.clampStep(
				value, VRAM_SAFETY_HEADROOM_MIN, VRAM_SAFETY_HEADROOM_MAX, VRAM_SAFETY_HEADROOM_STEP
		);
	}

	public int getVramFixedReserveMib() {
		return this.vramFixedReserveMib;
	}

	public void setVramFixedReserveMib(int value) {
		this.vramFixedReserveMib = VramConfigNormalization.clampStep(
				value, VRAM_FIXED_RESERVE_MIN, VRAM_FIXED_RESERVE_MAX, VRAM_FIXED_RESERVE_STEP
		);
	}

	public int getVramMinimumSafeRenderDistance() {
		return this.vramMinimumSafeRenderDistance;
	}

	public void setVramMinimumSafeRenderDistance(int value) {
		this.vramMinimumSafeRenderDistance = VramConfigNormalization.clamp(
				value, VRAM_MIN_SAFE_RENDER_DISTANCE_MIN, VRAM_MIN_SAFE_RENDER_DISTANCE_MAX
		);
	}

	public int getVramSampleIntervalSeconds() {
		return this.vramSampleIntervalSeconds;
	}

	public void setVramSampleIntervalSeconds(int value) {
		this.vramSampleIntervalSeconds = VramConfigNormalization.clamp(
				value, VRAM_SAMPLE_INTERVAL_MIN, VRAM_SAMPLE_INTERVAL_MAX
		);
	}

	public int getVramSustainedSamples() {
		return this.vramSustainedSamples;
	}

	public void setVramSustainedSamples(int value) {
		this.vramSustainedSamples = VramConfigNormalization.clamp(
				value, VRAM_SUSTAINED_SAMPLES_MIN, VRAM_SUSTAINED_SAMPLES_MAX
		);
	}

	public int getVramRenderDistanceStepIntervalSeconds() {
		return this.vramRenderDistanceStepIntervalSeconds;
	}

	public void setVramRenderDistanceStepIntervalSeconds(int value) {
		this.vramRenderDistanceStepIntervalSeconds = VramConfigNormalization.clamp(
				value, VRAM_RENDER_STEP_INTERVAL_MIN, VRAM_RENDER_STEP_INTERVAL_MAX
		);
	}

	public int getVramRecoveryDelaySeconds() {
		return this.vramRecoveryDelaySeconds;
	}

	public void setVramRecoveryDelaySeconds(int value) {
		this.vramRecoveryDelaySeconds = VramConfigNormalization.clampStep(
				value, VRAM_RECOVERY_DELAY_MIN, VRAM_RECOVERY_DELAY_MAX, VRAM_RECOVERY_DELAY_STEP
		);
	}

	public int getVramLargeAllocationSpikeMib() {
		return this.vramLargeAllocationSpikeMib;
	}

	public void setVramLargeAllocationSpikeMib(int value) {
		this.vramLargeAllocationSpikeMib = VramConfigNormalization.clampStep(
				value, VRAM_ALLOCATION_SPIKE_MIN, VRAM_ALLOCATION_SPIKE_MAX,
				VRAM_ALLOCATION_SPIKE_STEP
		);
	}

	public synchronized void resetToFactoryDefaults() {
		ConfigFactoryDefaults.copyMutableFields(this, new VoltPerformanceConfig());
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
			temporaryPath = Files.createTempFile(directory, "sodium-volt-performance-", ".tmp");
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
			SodiumVolt.LOGGER.error("Could not save Sodium Volt performance configuration to {}", path, exception);
		} finally {
			if (temporaryPath != null) {
				try {
					Files.deleteIfExists(temporaryPath);
				} catch (IOException | SecurityException exception) {
					SodiumVolt.LOGGER.warn(
							"Could not remove temporary Sodium Volt performance configuration {}",
							temporaryPath,
							exception
					);
				}
			}
		}
		return saved;
	}

	private static VoltPerformanceConfig load() {
		VoltPerformanceConfig config = new VoltPerformanceConfig();
		Path path = configPath();
		try {
			if (!Files.exists(path)) {
				return config;
			}
			if (!Files.isRegularFile(path) || Files.size(path) > MAX_CONFIG_SIZE_BYTES) {
				SodiumVolt.LOGGER.warn("Ignoring invalid Sodium Volt performance configuration {}", path);
				return config;
			}
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonElement element = JsonParser.parseReader(reader);
				if (!element.isJsonObject()) {
					throw new IllegalArgumentException("Configuration root must be an object");
				}
				JsonObject root = element.getAsJsonObject();
				config.adaptivePerformanceControllerEnabled = readBoolean(
						root,
						"adaptive_performance_controller_enabled",
						config.adaptivePerformanceControllerEnabled
				);
				config.profile = readProfile(root, "profile", config.profile);
				config.targetFps = readInteger(root, "target_fps", config.targetFps);
				config.adaptiveRenderDistance = readBoolean(
						root, "adaptive_render_distance", config.adaptiveRenderDistance
				);
				config.adaptiveEntityDistance = readBoolean(
						root, "adaptive_entity_distance", config.adaptiveEntityDistance
				);
				config.adaptiveParticleQuality = readBoolean(
						root, "adaptive_particle_quality", config.adaptiveParticleQuality
				);
				config.adaptiveVisualEffects = readBoolean(
						root, "adaptive_visual_effects", config.adaptiveVisualEffects
				);
				config.adaptiveAnimationThrottling = readBoolean(
						root, "adaptive_animation_throttling", config.adaptiveAnimationThrottling
				);
				config.restoreOriginalSettings = readBoolean(
						root, "restore_original_settings", config.restoreOriginalSettings
				);
				config.showControllerNotifications = readBoolean(
						root, "show_controller_notifications", config.showControllerNotifications
				);
				config.minimumRenderDistance = readInteger(
						root, "minimum_render_distance", config.minimumRenderDistance
				);
				config.maximumRenderDistance = readInteger(
						root, "maximum_render_distance", config.maximumRenderDistance
				);
				config.adjustmentIntervalSeconds = readInteger(
						root, "adjustment_interval_seconds", config.adjustmentIntervalSeconds
				);
				config.qualityRecoveryDelaySeconds = readInteger(
						root, "quality_recovery_delay_seconds", config.qualityRecoveryDelaySeconds
				);
				config.fpsTolerance = readInteger(root, "fps_tolerance", config.fpsTolerance);
				config.sampleWindow = readInteger(root, "sample_window", config.sampleWindow);
				config.visibilityAwareParticleSchedulerEnabled = readBoolean(
						root,
						"visibility_aware_particle_scheduler_enabled",
						config.visibilityAwareParticleSchedulerEnabled
				);
				config.vapsPrioritizeInFrustum = readBoolean(
						root, "vaps_prioritize_in_frustum", config.vapsPrioritizeInFrustum
				);
				config.vapsSkipBehindCamera = readBoolean(
						root, "vaps_skip_behind_camera", config.vapsSkipBehindCamera
				);
				config.vapsDistanceAwareSimulation = readBoolean(
						root, "vaps_distance_aware_simulation", config.vapsDistanceAwareSimulation
				);
				config.vapsPreserveCriticalParticles = readBoolean(
						root, "vaps_preserve_critical_particles", config.vapsPreserveCriticalParticles
				);
				config.vapsCoalesceAmbientParticles = readBoolean(
						root, "vaps_coalesce_ambient_particles", config.vapsCoalesceAmbientParticles
				);
				config.vapsPerTypeRenderLimits = readBoolean(
						root, "vaps_per_type_render_limits", config.vapsPerTypeRenderLimits
				);
				config.vapsShowInspectorStatistics = readBoolean(
						root, "vaps_show_inspector_statistics", config.vapsShowInspectorStatistics
				);
				config.vapsFullRateDistance = readInteger(
						root, "vaps_full_rate_distance", config.vapsFullRateDistance
				);
				config.vapsFarTickInterval = readInteger(
						root, "vaps_far_tick_interval", config.vapsFarTickInterval
				);
				config.vapsPerTypeRenderLimit = readInteger(
						root, "vaps_per_type_render_limit", config.vapsPerTypeRenderLimit
				);
				config.vapsAmbientPerCell = readInteger(
						root, "vaps_ambient_per_cell", config.vapsAmbientPerCell
				);
				config.vapsCriticalReserve = readInteger(
						root, "vaps_critical_reserve", config.vapsCriticalReserve
				);
				config.blockEntityRenderBudgetingEnabled = readBoolean(
						root, "block_entity_render_budgeting_enabled", config.blockEntityRenderBudgetingEnabled
				);
				config.berpPrioritizeNearby = readBoolean(
						root, "berp_prioritize_nearby", config.berpPrioritizeNearby
				);
				config.berpRecentInteractionGrace = readBoolean(
						root, "berp_recent_interaction_grace", config.berpRecentInteractionGrace
				);
				config.berpDistanceAwareStateUpdates = readBoolean(
						root, "berp_distance_aware_state_updates", config.berpDistanceAwareStateUpdates
				);
				config.berpCacheFarRenderStates = readBoolean(
						root, "berp_cache_far_render_states", config.berpCacheFarRenderStates
				);
				config.berpPerTypeRenderLimits = readBoolean(
						root, "berp_per_type_render_limits", config.berpPerTypeRenderLimits
				);
				config.berpCullBeyondFarDistance = readBoolean(
						root, "berp_cull_beyond_far_distance", config.berpCullBeyondFarDistance
				);
				config.berpIncludeModdedBlockEntities = readBoolean(
						root, "berp_include_modded_block_entities", config.berpIncludeModdedBlockEntities
				);
				config.berpShowInspectorStatistics = readBoolean(
						root, "berp_show_inspector_statistics", config.berpShowInspectorStatistics
				);
				config.berpNearDistance = readInteger(root, "berp_near_distance", config.berpNearDistance);
				config.berpMediumDistance = readInteger(root, "berp_medium_distance", config.berpMediumDistance);
				config.berpMediumUpdateInterval = readInteger(
						root, "berp_medium_update_interval", config.berpMediumUpdateInterval
				);
				config.berpFarUpdateInterval = readInteger(
						root, "berp_far_update_interval", config.berpFarUpdateInterval
				);
				config.berpFarRenderDistance = readInteger(
						root, "berp_far_render_distance", config.berpFarRenderDistance
				);
				config.berpGlobalRenderBudget = readInteger(
						root, "berp_global_render_budget", config.berpGlobalRenderBudget
				);
				config.berpPerTypeRenderLimit = readInteger(
						root, "berp_per_type_render_limit", config.berpPerTypeRenderLimit
				);
				config.berpInteractionGraceSeconds = readInteger(
						root, "berp_interaction_grace_seconds", config.berpInteractionGraceSeconds
				);
				config.berpCacheCapacity = readInteger(
						root, "berp_cache_capacity", config.berpCacheCapacity
				);
				config.animatedTextureThrottlingEnabled = readBoolean(
						root,
						"animated_texture_throttling_enabled",
						config.animatedTextureThrottlingEnabled
				);
				config.attPauseInvisibleAnimations = readBoolean(
						root, "att_pause_invisible_animations", config.attPauseInvisibleAnimations
				);
				config.attDistanceAwareCadence = readBoolean(
						root, "att_distance_aware_cadence", config.attDistanceAwareCadence
				);
				config.attKeepInterfaceAtlasesFullSpeed = readBoolean(
						root,
						"att_keep_interface_atlases_full_speed",
						config.attKeepInterfaceAtlasesFullSpeed
				);
				config.attExemptCriticalVanillaTextures = readBoolean(
						root,
						"att_exempt_critical_vanilla_textures",
						config.attExemptCriticalVanillaTextures
				);
				config.attHonorExemptionLists = readBoolean(
						root, "att_honor_exemption_lists", config.attHonorExemptionLists
				);
				config.attImmediateSmoothResume = readBoolean(
						root, "att_immediate_smooth_resume", config.attImmediateSmoothResume
				);
				config.attShowInspectorStatistics = readBoolean(
						root, "att_show_inspector_statistics", config.attShowInspectorStatistics
				);
				config.attFullSpeedDistance = readInteger(
						root, "att_full_speed_distance", config.attFullSpeedDistance
				);
				config.attDistantUpdateInterval = readInteger(
						root, "att_distant_update_interval", config.attDistantUpdateInterval
				);
				config.attUnseenKeepaliveTicks = readInteger(
						root, "att_unseen_keepalive_ticks", config.attUnseenKeepaliveTicks
				);
				config.attPerAtlasAnimationBudget = readInteger(
						root, "att_per_atlas_animation_budget", config.attPerAtlasAnimationBudget
				);
				config.attUserExemptTextures = readStringArray(
						root, "att_user_exempt_textures", config.attUserExemptTextures
				);
				config.vramPressureProtectionEnabled = readBoolean(
						root, "vram_pressure_protection_enabled", config.vramPressureProtectionEnabled
				);
				config.vramAutoDetectBudget = readBoolean(
						root, "vram_auto_detect_budget", config.vramAutoDetectBudget
				);
				config.vramApplySafeRenderDistanceProfile = readBoolean(
						root,
						"vram_apply_safe_render_distance_profile",
						config.vramApplySafeRenderDistanceProfile
				);
				config.vramRespondToAllocationSpikes = readBoolean(
						root, "vram_respond_to_allocation_spikes", config.vramRespondToAllocationSpikes
				);
				config.vramRestoreQualityAfterRecovery = readBoolean(
						root,
						"vram_restore_quality_after_recovery",
						config.vramRestoreQualityAfterRecovery
				);
				config.vramShowPressureWarnings = readBoolean(
						root, "vram_show_pressure_warnings", config.vramShowPressureWarnings
				);
				config.vramAccountForHeadroom = readBoolean(
						root, "vram_account_for_headroom", config.vramAccountForHeadroom
				);
				config.vramShowInspectorStatistics = readBoolean(
						root, "vram_show_inspector_statistics", config.vramShowInspectorStatistics
				);
				config.vramManualBudgetMib = readInteger(
						root, "vram_manual_budget_mib", config.vramManualBudgetMib
				);
				config.vramProtectionThresholdPercent = readInteger(
						root,
						"vram_protection_threshold_percent",
						config.vramProtectionThresholdPercent
				);
				config.vramCriticalThresholdPercent = readInteger(
						root, "vram_critical_threshold_percent", config.vramCriticalThresholdPercent
				);
				config.vramSafetyHeadroomPercent = readInteger(
						root, "vram_safety_headroom_percent", config.vramSafetyHeadroomPercent
				);
				config.vramFixedReserveMib = readInteger(
						root, "vram_fixed_reserve_mib", config.vramFixedReserveMib
				);
				config.vramMinimumSafeRenderDistance = readInteger(
						root,
						"vram_minimum_safe_render_distance",
						config.vramMinimumSafeRenderDistance
				);
				config.vramSampleIntervalSeconds = readInteger(
						root, "vram_sample_interval_seconds", config.vramSampleIntervalSeconds
				);
				config.vramSustainedSamples = readInteger(
						root, "vram_sustained_samples", config.vramSustainedSamples
				);
				config.vramRenderDistanceStepIntervalSeconds = readInteger(
						root,
						"vram_render_distance_step_interval_seconds",
						config.vramRenderDistanceStepIntervalSeconds
				);
				config.vramRecoveryDelaySeconds = readInteger(
						root, "vram_recovery_delay_seconds", config.vramRecoveryDelaySeconds
				);
				config.vramLargeAllocationSpikeMib = readInteger(
						root, "vram_large_allocation_spike_mib", config.vramLargeAllocationSpikeMib
				);
			}
			config.validate();
			return config;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			SodiumVolt.LOGGER.error(
					"Could not load Sodium Volt performance configuration from {}; using safe defaults",
					path,
					exception
			);
			return new VoltPerformanceConfig();
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

	private static String[] readStringArray(JsonObject root, String key, String[] defaultValue) {
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonArray()) {
			return defaultValue;
		}
		String[] values = new String[Math.min(
				element.getAsJsonArray().size(),
				AttExemptionParsing.MAX_USER_ENTRIES
		)];
		int count = 0;
		for (JsonElement entry : element.getAsJsonArray()) {
			if (count >= values.length) {
				break;
			}
			if (entry != null && entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
				values[count++] = entry.getAsString();
			}
		}
		if (count == values.length) {
			return values;
		}
		String[] compact = new String[count];
		System.arraycopy(values, 0, compact, 0, count);
		return compact;
	}

	private static Profile readProfile(JsonObject root, String key, Profile defaultValue) {
		JsonPrimitive primitive = primitive(root, key);
		if (primitive == null || !primitive.isString()) {
			return defaultValue;
		}
		try {
			return Profile.valueOf(primitive.getAsString().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return defaultValue;
		}
	}

	private static JsonPrimitive primitive(JsonObject root, String key) {
		JsonElement element = root.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsJsonPrimitive() : null;
	}

	private void validate() {
		this.setProfile(this.profile);
		this.setTargetFps(this.targetFps);
		ApcConfigNormalization.DistanceBounds normalizedDistances =
				ApcConfigNormalization.normalizeDistanceBounds(
				this.minimumRenderDistance,
				this.maximumRenderDistance
		);
		this.minimumRenderDistance = normalizedDistances.minimum();
		this.maximumRenderDistance = normalizedDistances.maximum();
		this.setAdjustmentIntervalSeconds(this.adjustmentIntervalSeconds);
		this.setQualityRecoveryDelaySeconds(this.qualityRecoveryDelaySeconds);
		this.setFpsTolerance(this.fpsTolerance);
		this.setSampleWindow(this.sampleWindow);
		this.setVapsFullRateDistance(this.vapsFullRateDistance);
		this.setVapsFarTickInterval(this.vapsFarTickInterval);
		this.setVapsPerTypeRenderLimit(this.vapsPerTypeRenderLimit);
		this.setVapsAmbientPerCell(this.vapsAmbientPerCell);
		this.setVapsCriticalReserve(this.vapsCriticalReserve);
		BlockEntityBudgetNormalization.Distances berpDistances =
				BlockEntityBudgetNormalization.normalizeDistances(
						this.berpNearDistance,
						this.berpMediumDistance,
						this.berpFarRenderDistance
				);
		this.berpNearDistance = berpDistances.near();
		this.berpMediumDistance = berpDistances.medium();
		this.berpFarRenderDistance = berpDistances.far();
		this.setBerpMediumUpdateInterval(this.berpMediumUpdateInterval);
		this.setBerpFarUpdateInterval(this.berpFarUpdateInterval);
		this.setBerpGlobalRenderBudget(this.berpGlobalRenderBudget);
		this.setBerpPerTypeRenderLimit(this.berpPerTypeRenderLimit);
		this.setBerpInteractionGraceSeconds(this.berpInteractionGraceSeconds);
		this.setBerpCacheCapacity(this.berpCacheCapacity);
		this.setAttFullSpeedDistance(this.attFullSpeedDistance);
		this.setAttDistantUpdateInterval(this.attDistantUpdateInterval);
		this.setAttUnseenKeepaliveTicks(this.attUnseenKeepaliveTicks);
		this.setAttPerAtlasAnimationBudget(this.attPerAtlasAnimationBudget);
		this.setAttUserExemptTextures(this.attUserExemptTextures);
		this.setVramManualBudgetMib(this.vramManualBudgetMib);
		VramConfigNormalization.Thresholds vramThresholds =
				VramConfigNormalization.normalizeThresholds(
						this.vramProtectionThresholdPercent,
						this.vramCriticalThresholdPercent
				);
		this.vramProtectionThresholdPercent = vramThresholds.protection();
		this.vramCriticalThresholdPercent = vramThresholds.critical();
		this.setVramSafetyHeadroomPercent(this.vramSafetyHeadroomPercent);
		this.setVramFixedReserveMib(this.vramFixedReserveMib);
		this.setVramMinimumSafeRenderDistance(this.vramMinimumSafeRenderDistance);
		this.setVramSampleIntervalSeconds(this.vramSampleIntervalSeconds);
		this.setVramSustainedSamples(this.vramSustainedSamples);
		this.setVramRenderDistanceStepIntervalSeconds(this.vramRenderDistanceStepIntervalSeconds);
		this.setVramRecoveryDelaySeconds(this.vramRecoveryDelaySeconds);
		this.setVramLargeAllocationSpikeMib(this.vramLargeAllocationSpikeMib);
	}

	private static int clampToStep(int value, int minimum, int maximum, int step) {
		int clamped = clamp(value, minimum, maximum);
		int steps = (clamped - minimum + step / 2) / step;
		return Math.min(maximum, minimum + steps * step);
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
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
				.resolve("sodium-volt-performance.json");
	}

	private static final class Holder {
		private static final VoltPerformanceConfig INSTANCE = load();
	}

	public enum Profile {
		BALANCED,
		MAX_QUALITY,
		MAX_PERFORMANCE
	}
}
