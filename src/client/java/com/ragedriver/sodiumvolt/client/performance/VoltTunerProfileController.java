package com.ragedriver.sodiumvolt.client.performance;

import com.ragedriver.sodiumvolt.client.config.SmartFpsConfig;
import com.ragedriver.sodiumvolt.client.config.VoltGuardConfig;
import com.ragedriver.sodiumvolt.client.config.VoltInspectorConfig;
import com.ragedriver.sodiumvolt.client.config.VoltPerformanceConfig;
import com.ragedriver.sodiumvolt.client.config.VoltRecoveryConfig;

/** Applies a Volt Tuner Profile exactly once for each user selection. */
public final class VoltTunerProfileController {
	private VoltTunerProfileController() {
	}

	/**
	 * Selects and applies a profile.
	 *
	 * @return {@code true} when the selected profile changed. A repeated selection
	 * does not touch any option, which keeps manual adjustments made after the
	 * preset was applied intact.
	 */
	public static synchronized boolean select(
			VoltTunerProfile requested,
			VoltPerformanceConfig performance,
			VoltRecoveryConfig recovery,
			VoltGuardConfig guard,
			SmartFpsConfig smartFps,
			VoltInspectorConfig inspector
	) {
		if (performance == null || recovery == null || guard == null || smartFps == null
				|| inspector == null) {
			throw new IllegalArgumentException("Volt Tuner requires all performance configs");
		}
		VoltTunerProfile profile = requested == null
				? VoltTunerProfile.VOLT_DEFAULT
				: requested;
		if (profile == performance.getTunerProfile()) {
			return false;
		}

		performance.setTunerProfile(profile);
		switch (profile) {
			case VOLT_DEFAULT -> applyVoltDefault(performance, recovery, guard, smartFps, inspector);
			case COMPETITIVE -> applyCompetitive(performance, recovery, guard, smartFps, inspector);
			case AGGRESSIVE -> applyAggressive(performance, recovery, guard, smartFps, inspector);
		}
		return true;
	}

	/**
	 * Rebaseline a previous preset without conflating this action with factory
	 * reset. Volt Guard remains available with its safe defaults; actual factory
	 * reset is still responsible for disabling every master across Sodium Volt.
	 */
	private static void applyVoltDefault(
			VoltPerformanceConfig performance,
			VoltRecoveryConfig recovery,
			VoltGuardConfig guard,
			SmartFpsConfig smartFps,
			VoltInspectorConfig inspector
	) {
		performance.resetToFactoryDefaults();
		recovery.resetToFactoryDefaults();
		guard.resetToFactoryDefaults();
		smartFps.resetToFactoryDefaults();
		inspector.resetToFactoryDefaults();

		performance.setTunerProfile(VoltTunerProfile.VOLT_DEFAULT);
		guard.setVoltGuardEnabled(true);

		// Keep every tuner feature except Volt Guard disabled. The explicit values
		// document the baseline and protect it from future default changes.
		performance.setAdaptivePerformanceControllerEnabled(false);
		performance.setVisibilityAwareParticleSchedulerEnabled(false);
		performance.setBlockEntityRenderBudgetingEnabled(false);
		performance.setAdaptiveAnimationThrottling(false);
		performance.setAnimatedTextureThrottlingEnabled(false);
		performance.setVramPressureProtectionEnabled(false);
		performance.setBerpIncludeModdedBlockEntities(false);
		performance.setBerpCullBeyondFarDistance(false);
		smartFps.setSmartFpsEnabled(false);
		recovery.setVoltRecoveryEnabled(false);
		inspector.setVoltInspectorEnabled(false);
	}

	private static void applyCompetitive(
			VoltPerformanceConfig config,
			VoltRecoveryConfig recovery,
			VoltGuardConfig guard,
			SmartFpsConfig smartFps,
			VoltInspectorConfig inspector
	) {
		// All feature masters use their conservative, gameplay-safe settings. The
		// APC only reduces graphics after sustained pressure, keeping normal play
		// visually close to the user's stock setup.
		config.setAdaptivePerformanceControllerEnabled(true);
		config.setProfile(VoltPerformanceConfig.Profile.BALANCED);
		config.setTargetFps(120);
		config.setAdaptiveRenderDistance(true);
		config.setAdaptiveEntityDistance(true);
		config.setAdaptiveParticleQuality(true);
		config.setAdaptiveVisualEffects(true);
		config.setAdaptiveAnimationThrottling(false);
		config.setRestoreOriginalSettings(true);
		config.setShowControllerNotifications(true);
		config.setMinimumRenderDistance(VoltPerformanceConfig.MIN_RENDER_DISTANCE_DEFAULT);
		config.setMaximumRenderDistance(VoltPerformanceConfig.MAX_RENDER_DISTANCE_DEFAULT);
		config.setAdjustmentIntervalSeconds(VoltPerformanceConfig.ADJUSTMENT_INTERVAL_DEFAULT);
		config.setQualityRecoveryDelaySeconds(VoltPerformanceConfig.RECOVERY_DELAY_DEFAULT);
		config.setFpsTolerance(VoltPerformanceConfig.FPS_TOLERANCE_DEFAULT);
		config.setSampleWindow(VoltPerformanceConfig.SAMPLE_WINDOW_DEFAULT);

		config.setVisibilityAwareParticleSchedulerEnabled(true);
		config.setVapsPrioritizeInFrustum(true);
		config.setVapsSkipBehindCamera(true);
		config.setVapsDistanceAwareSimulation(true);
		config.setVapsPreserveCriticalParticles(true);
		config.setVapsCoalesceAmbientParticles(true);
		config.setVapsPerTypeRenderLimits(true);
		config.setVapsShowInspectorStatistics(false);
		config.setVapsFullRateDistance(VoltPerformanceConfig.VAPS_FULL_RATE_DISTANCE_DEFAULT);
		config.setVapsFarTickInterval(VoltPerformanceConfig.VAPS_FAR_TICK_INTERVAL_DEFAULT);
		config.setVapsPerTypeRenderLimit(VoltPerformanceConfig.VAPS_PER_TYPE_RENDER_LIMIT_DEFAULT);
		config.setVapsAmbientPerCell(VoltPerformanceConfig.VAPS_AMBIENT_PER_CELL_DEFAULT);
		config.setVapsCriticalReserve(VoltPerformanceConfig.VAPS_CRITICAL_RESERVE_DEFAULT);

		config.setBlockEntityRenderBudgetingEnabled(true);
		config.setBerpPrioritizeNearby(true);
		config.setBerpRecentInteractionGrace(true);
		config.setBerpDistanceAwareStateUpdates(true);
		config.setBerpCacheFarRenderStates(true);
		config.setBerpPerTypeRenderLimits(true);
		config.setBerpCullBeyondFarDistance(false);
		config.setBerpIncludeModdedBlockEntities(false);
		config.setBerpShowInspectorStatistics(false);
		config.setBerpNearDistance(VoltPerformanceConfig.BERP_NEAR_DISTANCE_DEFAULT);
		config.setBerpMediumDistance(VoltPerformanceConfig.BERP_MEDIUM_DISTANCE_DEFAULT);
		config.setBerpMediumUpdateInterval(VoltPerformanceConfig.BERP_MEDIUM_INTERVAL_DEFAULT);
		config.setBerpFarUpdateInterval(VoltPerformanceConfig.BERP_FAR_INTERVAL_DEFAULT);
		config.setBerpFarRenderDistance(VoltPerformanceConfig.BERP_FAR_DISTANCE_DEFAULT);
		config.setBerpGlobalRenderBudget(VoltPerformanceConfig.BERP_GLOBAL_BUDGET_DEFAULT);
		config.setBerpPerTypeRenderLimit(VoltPerformanceConfig.BERP_PER_TYPE_LIMIT_DEFAULT);
		config.setBerpInteractionGraceSeconds(VoltPerformanceConfig.BERP_GRACE_SECONDS_DEFAULT);
		config.setBerpCacheCapacity(VoltPerformanceConfig.BERP_CACHE_CAPACITY_DEFAULT);

		// ATT and VRAM are explicitly labelled Experimental. Leave their masters
		// off and clear all of their subordinate values after an Aggressive preset.
		config.setAnimatedTextureThrottlingEnabled(false);
		config.setAttPauseInvisibleAnimations(true);
		config.setAttDistanceAwareCadence(true);
		config.setAttKeepInterfaceAtlasesFullSpeed(true);
		config.setAttExemptCriticalVanillaTextures(true);
		config.setAttHonorExemptionLists(true);
		config.setAttImmediateSmoothResume(true);
		config.setAttShowInspectorStatistics(true);
		config.setAttFullSpeedDistance(VoltPerformanceConfig.ATT_FULL_SPEED_DISTANCE_DEFAULT);
		config.setAttDistantUpdateInterval(VoltPerformanceConfig.ATT_DISTANT_INTERVAL_DEFAULT);
		config.setAttUnseenKeepaliveTicks(VoltPerformanceConfig.ATT_UNSEEN_KEEPALIVE_DEFAULT);
		config.setAttPerAtlasAnimationBudget(VoltPerformanceConfig.ATT_PER_ATLAS_BUDGET_DEFAULT);
		config.setAttUserExemptTextures(new String[0]);

		config.setVramPressureProtectionEnabled(false);
		config.setVramAutoDetectBudget(true);
		config.setVramApplySafeRenderDistanceProfile(true);
		config.setVramRespondToAllocationSpikes(true);
		config.setVramRestoreQualityAfterRecovery(true);
		config.setVramShowPressureWarnings(true);
		config.setVramAccountForHeadroom(true);
		config.setVramShowInspectorStatistics(true);
		config.setVramManualBudgetMib(VoltPerformanceConfig.VRAM_MANUAL_BUDGET_DEFAULT);
		config.setVramProtectionThresholdPercent(
				VoltPerformanceConfig.VRAM_PROTECTION_THRESHOLD_DEFAULT
		);
		config.setVramCriticalThresholdPercent(VoltPerformanceConfig.VRAM_CRITICAL_THRESHOLD_DEFAULT);
		config.setVramSafetyHeadroomPercent(VoltPerformanceConfig.VRAM_SAFETY_HEADROOM_DEFAULT);
		config.setVramFixedReserveMib(VoltPerformanceConfig.VRAM_FIXED_RESERVE_DEFAULT);
		config.setVramMinimumSafeRenderDistance(
				VoltPerformanceConfig.VRAM_MIN_SAFE_RENDER_DISTANCE_DEFAULT
		);
		config.setVramSampleIntervalSeconds(VoltPerformanceConfig.VRAM_SAMPLE_INTERVAL_DEFAULT);
		config.setVramSustainedSamples(VoltPerformanceConfig.VRAM_SUSTAINED_SAMPLES_DEFAULT);
		config.setVramRenderDistanceStepIntervalSeconds(
				VoltPerformanceConfig.VRAM_RENDER_STEP_INTERVAL_DEFAULT
		);
		config.setVramRecoveryDelaySeconds(VoltPerformanceConfig.VRAM_RECOVERY_DELAY_DEFAULT);
		config.setVramLargeAllocationSpikeMib(VoltPerformanceConfig.VRAM_ALLOCATION_SPIKE_DEFAULT);

		// Competitive must not retain Aggressive recovery behavior.
		recovery.resetToFactoryDefaults();
		applyCompetitiveGuard(guard);
		applyCompetitiveSmartFps(smartFps);
		inspector.resetToFactoryDefaults();
	}

	private static void applyAggressive(
			VoltPerformanceConfig config,
			VoltRecoveryConfig recovery,
			VoltGuardConfig guard,
			SmartFpsConfig smartFps,
			VoltInspectorConfig inspector
	) {
		config.setAdaptivePerformanceControllerEnabled(true);
		config.setProfile(VoltPerformanceConfig.Profile.MAX_PERFORMANCE);
		config.setTargetFps(VoltPerformanceConfig.TARGET_FPS_MAX);
		config.setAdaptiveRenderDistance(true);
		config.setAdaptiveEntityDistance(true);
		config.setAdaptiveParticleQuality(true);
		config.setAdaptiveVisualEffects(true);
		config.setAdaptiveAnimationThrottling(true);
		config.setRestoreOriginalSettings(false);
		config.setShowControllerNotifications(false);
		config.setMinimumRenderDistance(VoltPerformanceConfig.MIN_RENDER_DISTANCE_MIN);
		config.setMaximumRenderDistance(VoltPerformanceConfig.MAX_RENDER_DISTANCE_MIN);
		config.setAdjustmentIntervalSeconds(VoltPerformanceConfig.ADJUSTMENT_INTERVAL_MIN);
		config.setQualityRecoveryDelaySeconds(VoltPerformanceConfig.RECOVERY_DELAY_MAX);
		config.setFpsTolerance(VoltPerformanceConfig.FPS_TOLERANCE_MAX);
		config.setSampleWindow(VoltPerformanceConfig.SAMPLE_WINDOW_MIN);

		config.setVisibilityAwareParticleSchedulerEnabled(true);
		config.setVapsPrioritizeInFrustum(true);
		config.setVapsSkipBehindCamera(true);
		config.setVapsDistanceAwareSimulation(true);
		config.setVapsPreserveCriticalParticles(false);
		config.setVapsCoalesceAmbientParticles(true);
		config.setVapsPerTypeRenderLimits(true);
		config.setVapsShowInspectorStatistics(false);
		config.setVapsFullRateDistance(VoltPerformanceConfig.VAPS_FULL_RATE_DISTANCE_MIN);
		config.setVapsFarTickInterval(VoltPerformanceConfig.VAPS_FAR_TICK_INTERVAL_MAX);
		config.setVapsPerTypeRenderLimit(VoltPerformanceConfig.VAPS_PER_TYPE_RENDER_LIMIT_MIN);
		config.setVapsAmbientPerCell(VoltPerformanceConfig.VAPS_AMBIENT_PER_CELL_MIN);
		config.setVapsCriticalReserve(VoltPerformanceConfig.VAPS_CRITICAL_RESERVE_MIN);

		config.setBlockEntityRenderBudgetingEnabled(true);
		config.setBerpPrioritizeNearby(true);
		config.setBerpRecentInteractionGrace(false);
		config.setBerpDistanceAwareStateUpdates(true);
		config.setBerpCacheFarRenderStates(true);
		config.setBerpPerTypeRenderLimits(true);
		config.setBerpCullBeyondFarDistance(true);
		config.setBerpIncludeModdedBlockEntities(true);
		config.setBerpShowInspectorStatistics(false);
		config.setBerpNearDistance(VoltPerformanceConfig.BERP_NEAR_DISTANCE_MIN);
		config.setBerpMediumDistance(VoltPerformanceConfig.BERP_MEDIUM_DISTANCE_MIN);
		config.setBerpMediumUpdateInterval(VoltPerformanceConfig.BERP_MEDIUM_INTERVAL_MAX);
		config.setBerpFarUpdateInterval(VoltPerformanceConfig.BERP_FAR_INTERVAL_MAX);
		config.setBerpFarRenderDistance(VoltPerformanceConfig.BERP_FAR_DISTANCE_MIN);
		config.setBerpGlobalRenderBudget(VoltPerformanceConfig.BERP_GLOBAL_BUDGET_MIN);
		config.setBerpPerTypeRenderLimit(VoltPerformanceConfig.BERP_PER_TYPE_LIMIT_MIN);
		config.setBerpInteractionGraceSeconds(VoltPerformanceConfig.BERP_GRACE_SECONDS_MIN);
		config.setBerpCacheCapacity(VoltPerformanceConfig.BERP_CACHE_CAPACITY_MIN);

		config.setAnimatedTextureThrottlingEnabled(true);
		config.setAttPauseInvisibleAnimations(true);
		config.setAttDistanceAwareCadence(true);
		config.setAttKeepInterfaceAtlasesFullSpeed(false);
		config.setAttExemptCriticalVanillaTextures(false);
		config.setAttHonorExemptionLists(false);
		config.setAttImmediateSmoothResume(true);
		config.setAttShowInspectorStatistics(false);
		config.setAttFullSpeedDistance(VoltPerformanceConfig.ATT_FULL_SPEED_DISTANCE_MIN);
		config.setAttDistantUpdateInterval(VoltPerformanceConfig.ATT_DISTANT_INTERVAL_MAX);
		config.setAttUnseenKeepaliveTicks(VoltPerformanceConfig.ATT_UNSEEN_KEEPALIVE_MIN);
		config.setAttPerAtlasAnimationBudget(VoltPerformanceConfig.ATT_PER_ATLAS_BUDGET_MIN);
		config.setAttUserExemptTextures(new String[0]);

		config.setVramPressureProtectionEnabled(true);
		config.setVramAutoDetectBudget(false);
		config.setVramApplySafeRenderDistanceProfile(true);
		config.setVramRespondToAllocationSpikes(true);
		config.setVramRestoreQualityAfterRecovery(false);
		config.setVramShowPressureWarnings(false);
		config.setVramAccountForHeadroom(false);
		config.setVramShowInspectorStatistics(false);
		config.setVramManualBudgetMib(VoltPerformanceConfig.VRAM_MANUAL_BUDGET_MAX);
		config.setVramProtectionThresholdPercent(VoltPerformanceConfig.VRAM_PROTECTION_THRESHOLD_MAX);
		config.setVramCriticalThresholdPercent(VoltPerformanceConfig.VRAM_CRITICAL_THRESHOLD_MAX);
		config.setVramSafetyHeadroomPercent(VoltPerformanceConfig.VRAM_SAFETY_HEADROOM_MIN);
		config.setVramFixedReserveMib(VoltPerformanceConfig.VRAM_FIXED_RESERVE_MIN);
		config.setVramMinimumSafeRenderDistance(VoltPerformanceConfig.VRAM_MIN_SAFE_RENDER_DISTANCE_MIN);
		config.setVramSampleIntervalSeconds(VoltPerformanceConfig.VRAM_SAMPLE_INTERVAL_MAX);
		config.setVramSustainedSamples(VoltPerformanceConfig.VRAM_SUSTAINED_SAMPLES_MAX);
		config.setVramRenderDistanceStepIntervalSeconds(
				VoltPerformanceConfig.VRAM_RENDER_STEP_INTERVAL_MAX
		);
		config.setVramRecoveryDelaySeconds(VoltPerformanceConfig.VRAM_RECOVERY_DELAY_MAX);
		config.setVramLargeAllocationSpikeMib(VoltPerformanceConfig.VRAM_ALLOCATION_SPIKE_MAX);

		// Recovery is a stability feature; enable it so the max-FPS preset can
		// safely recover from an unstable graphics configuration.
		recovery.setVoltRecoveryEnabled(true);
		recovery.setDetectUncleanSessions(true);
		recovery.setAutomaticSafeMode(true);
		recovery.setForceSafeModeNextLaunch(false);
		recovery.setCrashStreakThreshold(VoltRecoveryConfig.CRASH_STREAK_MIN);
		recovery.setMaximumRecoveryAttempts(VoltRecoveryConfig.MAXIMUM_ATTEMPTS_MAX);
		recovery.setApplySafeGraphicsProfile(true);
		recovery.setSafeRenderDistance(VoltRecoveryConfig.SAFE_RENDER_DISTANCE_MIN);
		recovery.setSafeEntityDistancePercent(VoltRecoveryConfig.SAFE_ENTITY_DISTANCE_MIN);
		recovery.setReduceExpensiveGraphics(true);
		recovery.setLimitFpsDuringRecovery(false);
		recovery.setRecoveryFpsCap(VoltRecoveryConfig.RECOVERY_FPS_MAX);
		recovery.setSuspendAdaptiveController(false);
		recovery.setRestoreOwnedSettingsAfterStableSession(true);
		recovery.setStableSessionDurationSeconds(VoltRecoveryConfig.STABLE_DURATION_MIN);
		recovery.setShowRecoveryNotifications(false);
		recovery.setWriteSanitizedLocalRecoveryReport(true);
		recovery.setShowRecoveryStatsInInspector(false);
		applyAggressiveGuard(guard);
		applyAggressiveSmartFps(smartFps);
		disableInspector(inspector);
	}

	private static void applyCompetitiveGuard(VoltGuardConfig config) {
		config.setVoltGuardEnabled(true);
		config.setAdaptiveWorkloadControl(true);
		config.setPrioritizeVisibleEffects(true);
		config.setPreserveGameplayCriticalEffects(true);
		config.setShowProtectionNotifications(true);
		config.setTargetFps(VoltGuardConfig.TARGET_FPS_DEFAULT);
		config.setParticleRenderBudget(VoltGuardConfig.PARTICLE_BUDGET_DEFAULT);
		config.setBlockEntityRenderBudget(VoltGuardConfig.BLOCK_ENTITY_BUDGET_DEFAULT);
		config.setDisplayEntityRenderBudget(VoltGuardConfig.DISPLAY_ENTITY_BUDGET_DEFAULT);
	}

	private static void applyAggressiveGuard(VoltGuardConfig config) {
		config.setVoltGuardEnabled(true);
		config.setAdaptiveWorkloadControl(true);
		config.setPrioritizeVisibleEffects(true);
		config.setPreserveGameplayCriticalEffects(false);
		config.setShowProtectionNotifications(false);
		config.setTargetFps(VoltGuardConfig.TARGET_FPS_MAX);
		config.setParticleRenderBudget(VoltGuardConfig.PARTICLE_BUDGET_MIN);
		config.setBlockEntityRenderBudget(VoltGuardConfig.BLOCK_ENTITY_BUDGET_MIN);
		config.setDisplayEntityRenderBudget(VoltGuardConfig.DISPLAY_ENTITY_BUDGET_MIN);
	}

	private static void applyCompetitiveSmartFps(SmartFpsConfig config) {
		config.setSmartFpsEnabled(true);
		config.setMinimizedTargetFps(SmartFpsConfig.MINIMIZED_TARGET_DEFAULT);
		config.setThrottleWhenMinimized(true);
		config.setThrottleWhenUnfocused(true);
		config.setUnfocusedTargetFps(SmartFpsConfig.UNFOCUSED_TARGET_DEFAULT);
		config.setBackgroundActivationDelaySeconds(SmartFpsConfig.BACKGROUND_DELAY_DEFAULT);
		// Battery throttling can cap focused gameplay, so both presets keep it off.
		config.setBatteryMode(false);
		config.setBatteryTargetFps(SmartFpsConfig.BATTERY_TARGET_DEFAULT);
		config.setBypassBatteryLimitWhileCharging(true);
		config.setLowBatteryProtection(false);
		config.setLowBatteryThresholdPercent(SmartFpsConfig.LOW_BATTERY_THRESHOLD_DEFAULT);
		config.setLowBatteryTargetFps(SmartFpsConfig.LOW_BATTERY_TARGET_DEFAULT);
		config.setPowerPollIntervalSeconds(SmartFpsConfig.POWER_POLL_INTERVAL_DEFAULT);
		config.setShowStatusNotifications(true);
		config.setShowInspectorStatistics(true);
	}

	private static void applyAggressiveSmartFps(SmartFpsConfig config) {
		config.setSmartFpsEnabled(true);
		config.setMinimizedTargetFps(SmartFpsConfig.MINIMIZED_TARGET_MIN);
		config.setThrottleWhenMinimized(true);
		config.setThrottleWhenUnfocused(true);
		config.setUnfocusedTargetFps(SmartFpsConfig.UNFOCUSED_TARGET_MIN);
		config.setBackgroundActivationDelaySeconds(SmartFpsConfig.BACKGROUND_DELAY_MIN);
		config.setBatteryMode(false);
		config.setBatteryTargetFps(SmartFpsConfig.BATTERY_TARGET_MIN);
		config.setBypassBatteryLimitWhileCharging(false);
		config.setLowBatteryProtection(false);
		config.setLowBatteryThresholdPercent(SmartFpsConfig.LOW_BATTERY_THRESHOLD_MAX);
		config.setLowBatteryTargetFps(SmartFpsConfig.LOW_BATTERY_TARGET_MIN);
		config.setPowerPollIntervalSeconds(SmartFpsConfig.POWER_POLL_INTERVAL_MIN);
		config.setShowStatusNotifications(false);
		config.setShowInspectorStatistics(false);
	}

	private static void disableInspector(VoltInspectorConfig config) {
		config.setVoltInspectorEnabled(false);
		config.setShowInspectorOverlay(false);
		config.setFrameTimeStatistics(false);
		config.setChunkActivity(false);
		config.setSceneComplexity(false);
		config.setParticleBreakdown(false);
		config.setAnimatedTextureCount(false);
		config.setGcPauseMonitor(false);
		config.setBottleneckEstimate(false);
		config.setRendererGpuDetails(false);
		config.setResourceReloadTiming(false);
		config.setSmartRecommendations(false);
		config.setRefreshIntervalMs(VoltInspectorConfig.REFRESH_INTERVAL_MAX);
		config.setFrameSampleWindow(VoltInspectorConfig.SAMPLE_WINDOW_MIN);
		// The inspector is disabled; retain the default diagnostic threshold so
		// re-enabling it later does not inherit an arbitrary aggressive value.
		config.setSpikeThresholdMs(VoltInspectorConfig.SPIKE_THRESHOLD_DEFAULT);
	}
}
