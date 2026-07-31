package com.ragedriver.sodiumvolt.client.config;

public final class VoltConfigFactoryReset {
	private VoltConfigFactoryReset() {
	}

	public static synchronized void resetInMemory() {
		resetInMemory(runtimeConfigs());
	}

	static void resetInMemory(ConfigSet configs) {
		configs.guard.resetToFactoryDefaults();
		configs.inspector.resetToFactoryDefaults();
		configs.performance.resetToFactoryDefaults();
		configs.smartFps.resetToFactoryDefaults();
		configs.recovery.resetToFactoryDefaults();
		configs.watchdog.resetToFactoryDefaults();
		configs.shield.resetToFactoryDefaults();
		configs.privacy.resetToFactoryDefaults();
		configs.profiles.resetToFactoryDefaults();

		disableAllFeatureMasters(configs);

		if (!allFeatureMastersDisabled(configs)) {
			throw new IllegalStateException("Factory reset master-toggle invariant failed");
		}
	}

	public static synchronized void disableAllFeatureMasters() {
		disableAllFeatureMasters(runtimeConfigs());
	}

	static void disableAllFeatureMasters(ConfigSet configs) {
		// Keep this explicit invariant even if a future config changes a default.
		configs.guard.setVoltGuardEnabled(false);
		configs.inspector.setVoltInspectorEnabled(false);
		configs.performance.setAdaptivePerformanceControllerEnabled(false);
		configs.performance.setVisibilityAwareParticleSchedulerEnabled(false);
		configs.performance.setBlockEntityRenderBudgetingEnabled(false);
		configs.performance.setAnimatedTextureThrottlingEnabled(false);
		configs.performance.setVramPressureProtectionEnabled(false);
		configs.smartFps.setSmartFpsEnabled(false);
		configs.recovery.setVoltRecoveryEnabled(false);
		configs.watchdog.setGpuTimeoutWatchdogEnabled(false);
		configs.shield.setResourcePackShieldEnabled(false);
		configs.privacy.setEnabled(false);
		configs.profiles.setProfilesEnabled(false);
	}

	public static synchronized boolean persistAll() {
		boolean saved = true;
		saved &= VoltGuardConfig.getInstance().saveChecked();
		saved &= VoltInspectorConfig.getInstance().saveChecked();
		saved &= VoltPerformanceConfig.getInstance().saveChecked();
		saved &= SmartFpsConfig.getInstance().saveChecked();
		saved &= VoltRecoveryConfig.getInstance().saveChecked();
		saved &= GpuWatchdogConfig.getInstance().saveChecked();
		saved &= ResourcePackShieldConfig.getInstance().saveChecked();
		saved &= PrivacyScreenshotConfig.getInstance().saveChecked();
		saved &= ProfilesConfig.getInstance().saveChecked();
		return saved;
	}

	public static synchronized boolean allFeatureMastersDisabled() {
		return allFeatureMastersDisabled(runtimeConfigs());
	}

	static boolean allFeatureMastersDisabled(ConfigSet configs) {
		return !configs.guard.isVoltGuardEnabled()
				&& !configs.inspector.isVoltInspectorEnabled()
				&& !configs.performance.isAdaptivePerformanceControllerEnabled()
				&& !configs.performance.isVisibilityAwareParticleSchedulerEnabled()
				&& !configs.performance.isBlockEntityRenderBudgetingEnabled()
				&& !configs.performance.isAnimatedTextureThrottlingEnabled()
				&& !configs.performance.isVramPressureProtectionEnabled()
				&& !configs.smartFps.isSmartFpsEnabled()
				&& !configs.recovery.isVoltRecoveryEnabled()
				&& !configs.watchdog.isGpuTimeoutWatchdogEnabled()
				&& !configs.shield.isResourcePackShieldEnabled()
				&& !configs.privacy.isEnabled()
				&& !configs.profiles.isProfilesEnabled();
	}

	private static ConfigSet runtimeConfigs() {
		return new ConfigSet(
				VoltGuardConfig.getInstance(),
				VoltInspectorConfig.getInstance(),
				VoltPerformanceConfig.getInstance(),
				SmartFpsConfig.getInstance(),
				VoltRecoveryConfig.getInstance(),
				GpuWatchdogConfig.getInstance(),
				ResourcePackShieldConfig.getInstance(),
				PrivacyScreenshotConfig.getInstance(),
				ProfilesConfig.getInstance()
		);
	}

	record ConfigSet(
			VoltGuardConfig guard,
			VoltInspectorConfig inspector,
			VoltPerformanceConfig performance,
			SmartFpsConfig smartFps,
			VoltRecoveryConfig recovery,
			GpuWatchdogConfig watchdog,
			ResourcePackShieldConfig shield,
			PrivacyScreenshotConfig privacy,
			ProfilesConfig profiles
	) {
	}
}
