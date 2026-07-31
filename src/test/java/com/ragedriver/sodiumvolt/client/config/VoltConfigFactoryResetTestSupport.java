package com.ragedriver.sodiumvolt.client.config;

import com.ragedriver.sodiumvolt.client.profile.ProfileIdentity;
import com.ragedriver.sodiumvolt.client.profile.ProfileSettings;

public final class VoltConfigFactoryResetTestSupport {
	private VoltConfigFactoryResetTestSupport() {
	}

	public static void run() {
		VoltGuardConfig guard = VoltGuardConfig.createForTest();
		VoltInspectorConfig inspector = VoltInspectorConfig.createForTest();
		VoltPerformanceConfig performance = VoltPerformanceConfig.createForTest();
		SmartFpsConfig smartFps = SmartFpsConfig.createForTest();
		VoltRecoveryConfig recovery = VoltRecoveryConfig.createForTest();
		GpuWatchdogConfig watchdog = GpuWatchdogConfig.createForTest();
		ResourcePackShieldConfig shield = ResourcePackShieldConfig.createForTest();
		PrivacyScreenshotConfig privacy = PrivacyScreenshotConfig.createForTest();
		ProfilesConfig profiles = ProfilesConfig.createForTest();
		VoltConfigFactoryReset.ConfigSet configs = new VoltConfigFactoryReset.ConfigSet(
				guard, inspector, performance, smartFps, recovery,
				watchdog, shield, privacy, profiles
		);

		guard.setVoltGuardEnabled(true);
		inspector.setVoltInspectorEnabled(true);
		performance.setAdaptivePerformanceControllerEnabled(true);
		performance.setVisibilityAwareParticleSchedulerEnabled(true);
		performance.setBlockEntityRenderBudgetingEnabled(true);
		performance.setAnimatedTextureThrottlingEnabled(true);
		performance.setVramPressureProtectionEnabled(true);
		smartFps.setSmartFpsEnabled(true);
		recovery.setVoltRecoveryEnabled(true);
		watchdog.setGpuTimeoutWatchdogEnabled(true);
		shield.setResourcePackShieldEnabled(true);
		privacy.setEnabled(true);
		profiles.setProfilesEnabled(true);

		guard.setAdaptiveWorkloadControl(false);
		guard.setTargetFps(VoltGuardConfig.TARGET_FPS_MAX);
		inspector.setShowInspectorOverlay(false);
		inspector.setRefreshIntervalMs(VoltInspectorConfig.REFRESH_INTERVAL_MAX);
		performance.setAdaptiveRenderDistance(false);
		performance.setMinimumRenderDistance(VoltPerformanceConfig.MIN_RENDER_DISTANCE_MAX);
		smartFps.setThrottleWhenMinimized(false);
		smartFps.setMinimizedTargetFps(SmartFpsConfig.MINIMIZED_TARGET_MAX);
		recovery.setDetectUncleanSessions(false);
		recovery.setSafeRenderDistance(VoltRecoveryConfig.SAFE_RENDER_DISTANCE_MAX);
		watchdog.setIgnorePausedLoading(false);
		watchdog.setWarningStallThresholdSeconds(GpuWatchdogConfig.WARNING_THRESHOLD_MAX);
		shield.setMonitorLocalPacks(false);
		shield.setMaximumEntries(ResourcePackShieldConfig.ENTRY_LIMIT_MIN);
		privacy.setHideChat(false);
		profiles.setGlobalRenderDistance(ProfileSettings.RENDER_DISTANCE_MIN);
		profiles.storeServerProfile(
				ProfileIdentity.serverKey("reset.example", profiles.identitySalt()).orElseThrow(),
				ProfileSettings.serverDefaults()
		);

		long shieldRevision = shield.revision();
		long watchdogRevision = watchdog.revision();
		VoltConfigFactoryReset.resetInMemory(configs);
		check(VoltConfigFactoryReset.allFeatureMastersDisabled(configs),
				"all 13 feature master toggles are disabled");
		check(guard.isAdaptiveWorkloadControl()
					&& guard.getTargetFps() == VoltGuardConfig.TARGET_FPS_DEFAULT,
				"Volt Guard subordinate defaults copied");
		check(inspector.isShowInspectorOverlay()
					&& inspector.getRefreshIntervalMs()
					== VoltInspectorConfig.REFRESH_INTERVAL_DEFAULT,
				"Inspector subordinate defaults copied");
		check(performance.isAdaptiveRenderDistance()
					&& performance.getMinimumRenderDistance()
					== VoltPerformanceConfig.MIN_RENDER_DISTANCE_DEFAULT,
				"performance subordinate defaults copied");
		check(smartFps.isThrottleWhenMinimized()
					&& smartFps.getMinimizedTargetFps() == SmartFpsConfig.MINIMIZED_TARGET_DEFAULT,
				"Smart FPS subordinate defaults copied");
		check(recovery.isDetectUncleanSessions()
					&& recovery.getSafeRenderDistance() == VoltRecoveryConfig.SAFE_RENDER_DISTANCE_DEFAULT,
				"Recovery subordinate defaults copied");
		check(watchdog.isIgnorePausedLoading()
					&& watchdog.getWarningStallThresholdSeconds()
					== GpuWatchdogConfig.WARNING_THRESHOLD_DEFAULT,
				"Watchdog subordinate defaults copied");
		check(shield.isMonitorLocalPacks()
					&& shield.getMaximumEntries() == ResourcePackShieldConfig.ENTRY_LIMIT_DEFAULT,
				"Shield subordinate defaults copied");
		check(privacy.isHideChat(), "Privacy subordinate defaults copied");
		check(!profiles.isGlobalDefaultsInitialized()
					&& profiles.serverProfileCount() == 0
					&& profiles.getGlobalDefaults().equals(ProfileSettings.globalDefaults()),
				"Profiles records and subordinate defaults reset");
		check(shield.revision() > shieldRevision && watchdog.revision() > watchdogRevision,
				"revision-bearing runtime configs remain monotonic");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("Factory reset: " + message);
		}
	}
}
