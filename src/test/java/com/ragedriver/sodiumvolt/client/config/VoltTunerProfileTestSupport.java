package com.ragedriver.sodiumvolt.client.config;

import com.ragedriver.sodiumvolt.client.performance.VoltTunerProfile;
import com.ragedriver.sodiumvolt.client.performance.VoltTunerProfileController;

public final class VoltTunerProfileTestSupport {
	private VoltTunerProfileTestSupport() {
	}

	public static void run() {
		testAlreadyDefaultIsNonDestructiveAndNullSafe();
		testCompetitiveToVoltDefaultRebaselinesStableFeatures();
		testAggressiveToVoltDefaultRebaselinesStableFeatures();
		testCompetitiveUsesOnlyStableConservativeFeatures();
		testAggressiveCoversPerformanceConfigsWithoutForegroundBatteryCap();
		testAggressiveToCompetitiveRebaselinesExperimentalAndRecoverySettings();
		testFactoryResetRestoresVoltDefault();
	}

	private static void testAlreadyDefaultIsNonDestructiveAndNullSafe() {
		Configs configs = createConfigs();
		configs.performance.setTargetFps(170);
		configs.guard.setParticleRenderBudget(512);
		configs.smartFps.setThrottleWhenMinimized(false);
		configs.recovery.setVoltRecoveryEnabled(true);

		check(!select(VoltTunerProfile.VOLT_DEFAULT, configs),
				"the default selection is already current");
		check(configs.performance.getTargetFps() == 170
					&& configs.guard.getParticleRenderBudget() == 512
					&& !configs.smartFps.isThrottleWhenMinimized()
					&& configs.recovery.isVoltRecoveryEnabled(),
				"Volt Default must leave every config untouched");
		expectIllegalArgument(() -> VoltTunerProfileController.select(
				VoltTunerProfile.COMPETITIVE, null, configs.recovery, configs.guard,
				configs.smartFps, configs.inspector
		), "null config must be rejected");

	}

	private static void testCompetitiveToVoltDefaultRebaselinesStableFeatures() {
		Configs configs = createConfigs();
		select(VoltTunerProfile.COMPETITIVE, configs);
		configs.performance.setTargetFps(190);
		configs.guard.setParticleRenderBudget(512);
		configs.smartFps.setThrottleWhenMinimized(false);
		check(select(VoltTunerProfile.VOLT_DEFAULT, configs),
				"Competitive to Volt Default must normalize tuner-owned configs");
		checkVoltDefaultTransition(configs, "Competitive to Volt Default");
	}

	private static void testAggressiveToVoltDefaultRebaselinesStableFeatures() {
		Configs configs = createConfigs();
		select(VoltTunerProfile.AGGRESSIVE, configs);
		check(select(VoltTunerProfile.VOLT_DEFAULT, configs),
				"Aggressive to Volt Default must normalize tuner-owned configs");
		checkVoltDefaultTransition(configs, "Aggressive to Volt Default");
	}

	private static void testCompetitiveUsesOnlyStableConservativeFeatures() {
		Configs configs = createConfigs();
		check(select(VoltTunerProfile.COMPETITIVE, configs), "Competitive applies from default");
		VoltPerformanceConfig performance = configs.performance;
		check(performance.getTunerProfile() == VoltTunerProfile.COMPETITIVE
					&& performance.isAdaptivePerformanceControllerEnabled()
					&& performance.isVisibilityAwareParticleSchedulerEnabled()
					&& performance.isBlockEntityRenderBudgetingEnabled(),
				"Competitive enables APC, VAPS, and conservative BERP");
		check(performance.getProfile() == VoltPerformanceConfig.Profile.BALANCED
					&& !performance.isAdaptiveAnimationThrottling()
					&& !performance.isBerpCullBeyondFarDistance()
					&& !performance.isBerpIncludeModdedBlockEntities(),
				"Competitive keeps APC and BERP non-experimental");
		check(!performance.isAnimatedTextureThrottlingEnabled()
					&& performance.isAttKeepInterfaceAtlasesFullSpeed()
					&& performance.getAttPerAtlasAnimationBudget()
					== VoltPerformanceConfig.ATT_PER_ATLAS_BUDGET_DEFAULT
					&& !performance.isVramPressureProtectionEnabled()
					&& performance.isVramAutoDetectBudget()
					&& performance.getVramManualBudgetMib()
					== VoltPerformanceConfig.VRAM_MANUAL_BUDGET_DEFAULT,
				"Competitive disables and resets each experimental ATT and VRAM control");
		check(configs.guard.isVoltGuardEnabled()
					&& configs.guard.isAdaptiveWorkloadControl()
					&& configs.guard.isPrioritizeVisibleEffects()
					&& configs.guard.isPreserveGameplayCriticalEffects()
					&& configs.guard.getParticleRenderBudget()
					== VoltGuardConfig.PARTICLE_BUDGET_DEFAULT,
				"Competitive enables Volt Guard with safe default budgets");
		check(configs.smartFps.isSmartFpsEnabled()
					&& configs.smartFps.isThrottleWhenMinimized()
					&& configs.smartFps.isThrottleWhenUnfocused()
					&& configs.smartFps.getMinimizedTargetFps()
					== SmartFpsConfig.MINIMIZED_TARGET_DEFAULT
					&& configs.smartFps.getUnfocusedTargetFps()
					== SmartFpsConfig.UNFOCUSED_TARGET_DEFAULT
					&& !configs.smartFps.isBatteryMode()
					&& !configs.smartFps.isLowBatteryProtection(),
				"Competitive throttles only minimized or background sessions");
		check(!configs.recovery.isVoltRecoveryEnabled(),
				"Competitive keeps Volt Recovery at factory defaults and disabled");
		check(!configs.inspector.isVoltInspectorEnabled()
					&& configs.inspector.isShowInspectorOverlay()
					&& configs.inspector.isFrameTimeStatistics()
					&& configs.inspector.isSmartRecommendations()
					&& configs.inspector.getRefreshIntervalMs()
					== VoltInspectorConfig.REFRESH_INTERVAL_DEFAULT
					&& configs.inspector.getFrameSampleWindow()
					== VoltInspectorConfig.SAMPLE_WINDOW_DEFAULT
					&& configs.inspector.getSpikeThresholdMs()
					== VoltInspectorConfig.SPIKE_THRESHOLD_DEFAULT,
				"Competitive rebaselines Volt Inspector to its factory state");

		performance.setVapsFarTickInterval(2);
		check(!select(VoltTunerProfile.COMPETITIVE, configs),
				"same profile selection is a no-op");
		check(performance.getVapsFarTickInterval() == 2,
				"same profile selection cannot overwrite manual edits");
	}

	private static void testAggressiveCoversPerformanceConfigsWithoutForegroundBatteryCap() {
		Configs configs = createConfigs();
		check(select(VoltTunerProfile.AGGRESSIVE, configs), "Aggressive applies from default");
		VoltPerformanceConfig performance = configs.performance;
		check(performance.getProfile() == VoltPerformanceConfig.Profile.MAX_PERFORMANCE
					&& performance.getTargetFps() == VoltPerformanceConfig.TARGET_FPS_MAX
					&& performance.isAnimatedTextureThrottlingEnabled()
					&& performance.isVramPressureProtectionEnabled()
					&& performance.isBerpIncludeModdedBlockEntities(),
				"Aggressive enables every APC and experimental performance family");
		check(configs.recovery.isVoltRecoveryEnabled()
					&& configs.recovery.getSafeRenderDistance() == VoltRecoveryConfig.SAFE_RENDER_DISTANCE_MIN,
				"Aggressive enables Volt Recovery");
		check(configs.guard.isVoltGuardEnabled()
					&& configs.guard.isAdaptiveWorkloadControl()
					&& configs.guard.isPrioritizeVisibleEffects()
					&& !configs.guard.isPreserveGameplayCriticalEffects()
					&& !configs.guard.isShowProtectionNotifications()
					&& configs.guard.getTargetFps() == VoltGuardConfig.TARGET_FPS_MAX
					&& configs.guard.getParticleRenderBudget() == VoltGuardConfig.PARTICLE_BUDGET_MIN
					&& configs.guard.getBlockEntityRenderBudget() == VoltGuardConfig.BLOCK_ENTITY_BUDGET_MIN
					&& configs.guard.getDisplayEntityRenderBudget() == VoltGuardConfig.DISPLAY_ENTITY_BUDGET_MIN,
				"Aggressive configures every Volt Guard control for maximum FPS");
		check(configs.smartFps.isSmartFpsEnabled()
					&& configs.smartFps.getMinimizedTargetFps() == SmartFpsConfig.MINIMIZED_TARGET_MIN
					&& configs.smartFps.getUnfocusedTargetFps() == SmartFpsConfig.UNFOCUSED_TARGET_MIN
					&& configs.smartFps.getBackgroundActivationDelaySeconds() == SmartFpsConfig.BACKGROUND_DELAY_MIN
					&& !configs.smartFps.isBatteryMode()
					&& !configs.smartFps.isLowBatteryProtection()
					&& configs.smartFps.getBatteryTargetFps() == SmartFpsConfig.BATTERY_TARGET_MIN
					&& !configs.smartFps.isBypassBatteryLimitWhileCharging()
					&& configs.smartFps.getLowBatteryTargetFps() == SmartFpsConfig.LOW_BATTERY_TARGET_MIN
					&& !configs.smartFps.isShowStatusNotifications()
					&& !configs.smartFps.isShowInspectorStatistics(),
				"Aggressive minimizes only background resource use without a foreground battery cap");
		check(!configs.inspector.isVoltInspectorEnabled()
					&& !configs.inspector.isShowInspectorOverlay()
					&& !configs.inspector.isFrameTimeStatistics()
					&& !configs.inspector.isChunkActivity()
					&& !configs.inspector.isSceneComplexity()
					&& !configs.inspector.isParticleBreakdown()
					&& !configs.inspector.isAnimatedTextureCount()
					&& !configs.inspector.isGcPauseMonitor()
					&& !configs.inspector.isBottleneckEstimate()
					&& !configs.inspector.isRendererGpuDetails()
					&& !configs.inspector.isResourceReloadTiming()
					&& !configs.inspector.isSmartRecommendations()
					&& configs.inspector.getRefreshIntervalMs()
					== VoltInspectorConfig.REFRESH_INTERVAL_MAX
					&& configs.inspector.getFrameSampleWindow()
					== VoltInspectorConfig.SAMPLE_WINDOW_MIN
					&& configs.inspector.getSpikeThresholdMs()
					== VoltInspectorConfig.SPIKE_THRESHOLD_DEFAULT,
				"Aggressive disables and minimizes Volt Inspector work deterministically");
	}

	private static void testAggressiveToCompetitiveRebaselinesExperimentalAndRecoverySettings() {
		Configs configs = createConfigs();
		select(VoltTunerProfile.AGGRESSIVE, configs);
		check(select(VoltTunerProfile.COMPETITIVE, configs),
				"switching from Aggressive to Competitive reapplies the stable profile");
		check(!configs.performance.isAnimatedTextureThrottlingEnabled()
					&& configs.performance.getAttFullSpeedDistance()
					== VoltPerformanceConfig.ATT_FULL_SPEED_DISTANCE_DEFAULT
					&& !configs.performance.isVramPressureProtectionEnabled()
					&& configs.performance.getVramProtectionThresholdPercent()
					== VoltPerformanceConfig.VRAM_PROTECTION_THRESHOLD_DEFAULT,
				"Aggressive experimental tuning cannot remain latent in Competitive");
		check(!configs.recovery.isVoltRecoveryEnabled()
					&& configs.recovery.getSafeRenderDistance()
					== VoltRecoveryConfig.SAFE_RENDER_DISTANCE_DEFAULT
					&& configs.recovery.isLimitFpsDuringRecovery(),
				"Competitive clears Aggressive Volt Recovery configuration");
		check(!configs.inspector.isVoltInspectorEnabled()
					&& configs.inspector.isShowInspectorOverlay()
					&& configs.inspector.isFrameTimeStatistics()
					&& configs.inspector.isSmartRecommendations()
					&& configs.inspector.getRefreshIntervalMs()
					== VoltInspectorConfig.REFRESH_INTERVAL_DEFAULT
					&& configs.inspector.getFrameSampleWindow()
					== VoltInspectorConfig.SAMPLE_WINDOW_DEFAULT
					&& configs.inspector.getSpikeThresholdMs()
					== VoltInspectorConfig.SPIKE_THRESHOLD_DEFAULT,
				"Aggressive inspector tuning is cleared when switching to Competitive");
	}

	private static void testFactoryResetRestoresVoltDefault() {
		Configs configs = createConfigs();
		select(VoltTunerProfile.AGGRESSIVE, configs);
		configs.performance.resetToFactoryDefaults();
		configs.recovery.resetToFactoryDefaults();
		configs.guard.resetToFactoryDefaults();
		configs.smartFps.resetToFactoryDefaults();
		configs.inspector.resetToFactoryDefaults();
		check(configs.performance.getTunerProfile() == VoltTunerProfile.VOLT_DEFAULT
					&& !configs.recovery.isVoltRecoveryEnabled()
					&& !configs.guard.isVoltGuardEnabled()
					&& !configs.smartFps.isSmartFpsEnabled()
					&& !configs.inspector.isVoltInspectorEnabled(),
				"factory reset restores Volt Default and disables tuner-controlled masters");
	}

	private static void checkVoltDefaultTransition(Configs configs, String transition) {
		VoltPerformanceConfig performance = configs.performance;
		check(performance.getTunerProfile() == VoltTunerProfile.VOLT_DEFAULT
					&& !performance.isAdaptivePerformanceControllerEnabled()
					&& !performance.isVisibilityAwareParticleSchedulerEnabled()
					&& !performance.isBlockEntityRenderBudgetingEnabled()
					&& !performance.isAnimatedTextureThrottlingEnabled()
					&& !performance.isVramPressureProtectionEnabled()
					&& !performance.isAdaptiveAnimationThrottling()
					&& !performance.isBerpIncludeModdedBlockEntities()
					&& !performance.isBerpCullBeyondFarDistance(),
				transition + " disables every Performance master and experimental control");
		check(performance.getTargetFps() == VoltPerformanceConfig.TARGET_FPS_DEFAULT
					&& performance.getVapsFarTickInterval()
					== VoltPerformanceConfig.VAPS_FAR_TICK_INTERVAL_DEFAULT
					&& performance.getBerpGlobalRenderBudget()
					== VoltPerformanceConfig.BERP_GLOBAL_BUDGET_DEFAULT
					&& performance.getAttPerAtlasAnimationBudget()
					== VoltPerformanceConfig.ATT_PER_ATLAS_BUDGET_DEFAULT
					&& performance.getVramManualBudgetMib()
					== VoltPerformanceConfig.VRAM_MANUAL_BUDGET_DEFAULT,
				transition + " restores performance fine tuning to factory defaults");
		check(configs.guard.isVoltGuardEnabled()
					&& configs.guard.isPreserveGameplayCriticalEffects()
					&& configs.guard.getParticleRenderBudget() == VoltGuardConfig.PARTICLE_BUDGET_DEFAULT
					&& !configs.smartFps.isSmartFpsEnabled()
					&& configs.smartFps.isBatteryMode()
					&& configs.smartFps.getMinimizedTargetFps()
					== SmartFpsConfig.MINIMIZED_TARGET_DEFAULT,
				transition + " enables only Volt Guard and restores its Smart FPS defaults");
		check(!configs.recovery.isVoltRecoveryEnabled()
					&& configs.recovery.getSafeRenderDistance()
					== VoltRecoveryConfig.SAFE_RENDER_DISTANCE_DEFAULT
					&& !configs.inspector.isVoltInspectorEnabled()
					&& configs.inspector.isShowInspectorOverlay()
					&& configs.inspector.getRefreshIntervalMs()
					== VoltInspectorConfig.REFRESH_INTERVAL_DEFAULT,
				transition + " restores Recovery and Inspector defaults with their masters disabled");
	}

	private static Configs createConfigs() {
		return new Configs(
				VoltPerformanceConfig.createForTest(),
				VoltRecoveryConfig.createForTest(),
				VoltGuardConfig.createForTest(),
				SmartFpsConfig.createForTest(),
				VoltInspectorConfig.createForTest()
		);
	}

	private static boolean select(VoltTunerProfile profile, Configs configs) {
		return VoltTunerProfileController.select(
				profile, configs.performance, configs.recovery, configs.guard,
				configs.smartFps, configs.inspector
		);
	}

	private static void expectIllegalArgument(Runnable action, String message) {
		try {
			action.run();
			throw new AssertionError("Volt Tuner Profile: " + message);
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("Volt Tuner Profile: " + message);
		}
	}

	private record Configs(
			VoltPerformanceConfig performance,
			VoltRecoveryConfig recovery,
			VoltGuardConfig guard,
			SmartFpsConfig smartFps,
			VoltInspectorConfig inspector
	) {
	}
}
