package com.ragedriver.sodiumvolt.client.profile;

import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.config.VoltConfigFactoryReset;
import com.ragedriver.sodiumvolt.client.performance.AdaptivePerformanceController;
import com.ragedriver.sodiumvolt.client.performance.VramPressureProtectionEngine;
import com.ragedriver.sodiumvolt.client.privacy.PrivacyScreenshotEngine;
import com.ragedriver.sodiumvolt.client.recovery.RecoveryStateStore;
import com.ragedriver.sodiumvolt.client.recovery.VoltRecoveryEngine;
import com.ragedriver.sodiumvolt.client.watchdog.GpuTimeoutWatchdogEngine;
import com.ragedriver.sodiumvolt.client.watchdog.WatchdogRecoveryRequestStore;
import net.minecraft.client.Minecraft;

public final class VoltFactoryReset {
	private VoltFactoryReset() {
	}

	public static boolean reset(Minecraft minecraft) {
		ProfileSettings baseline = ProfileSettings.globalDefaults();
		boolean resetComplete = true;
		try {
			baseline = PerformanceProfileEngine.prepareFactoryReset(minecraft);
		} catch (RuntimeException | LinkageError exception) {
			resetComplete = false;
			SodiumVolt.LOGGER.warn("Could not snapshot the Profiles global baseline");
		}
		try {
			VoltConfigFactoryReset.resetInMemory();
		} catch (RuntimeException | LinkageError exception) {
			resetComplete = false;
			SodiumVolt.LOGGER.error("Could not copy every Sodium Volt factory default");
		} finally {
			// A failed subordinate copy must never leave a feature master enabled.
			VoltConfigFactoryReset.disableAllFeatureMasters();
		}

		long nowNanos = System.nanoTime();
		// Attempt every owner independently; one broken subsystem cannot skip another.
		resetComplete &= attempt(() -> VoltRecoveryEngine.onRenderFrame(minecraft, nowNanos));
		resetComplete &= attempt(() ->
				AdaptivePerformanceController.onRenderFrame(minecraft, nowNanos));
		resetComplete &= attempt(() ->
				VramPressureProtectionEngine.onRenderFrame(minecraft, nowNanos));
		resetComplete &= attempt(() ->
				GpuTimeoutWatchdogEngine.onRenderFrameBegin(minecraft, nowNanos));
		resetComplete &= attempt(PrivacyScreenshotEngine::resetForFactoryDefaults);
		ProfileSettings finalBaseline = baseline;
		resetComplete &= attempt(() ->
				PerformanceProfileEngine.finishFactoryReset(minecraft, finalBaseline));

		boolean stateSaved = result(RecoveryStateStore::resetToFactoryDefaults);
		boolean requestCleared = result(WatchdogRecoveryRequestStore::acknowledge);
		VoltConfigFactoryReset.disableAllFeatureMasters();
		boolean configsSaved = result(VoltConfigFactoryReset::persistAll);
		boolean mastersDisabled = VoltConfigFactoryReset.allFeatureMastersDisabled();
		boolean succeeded = resetComplete && stateSaved && requestCleared
				&& configsSaved && mastersDisabled;
		if (!succeeded) {
			SodiumVolt.LOGGER.error(
					"Sodium Volt factory reset is safe in memory but did not fully persist"
			);
		}
		return succeeded;
	}

	private static boolean attempt(Runnable action) {
		try {
			action.run();
			return true;
		} catch (RuntimeException | LinkageError exception) {
			SodiumVolt.LOGGER.warn("A Sodium Volt factory-reset cleanup step failed safely");
			return false;
		}
	}

	private static boolean result(java.util.function.BooleanSupplier action) {
		try {
			return action.getAsBoolean();
		} catch (RuntimeException | LinkageError exception) {
			SodiumVolt.LOGGER.warn("A Sodium Volt factory-reset persistence step failed safely");
			return false;
		}
	}
}
