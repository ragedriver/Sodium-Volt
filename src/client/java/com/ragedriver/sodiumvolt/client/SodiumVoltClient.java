package com.ragedriver.sodiumvolt.client;

import com.ragedriver.sodiumvolt.client.guard.VoltGuardEngine;
import com.ragedriver.sodiumvolt.client.input.VoltKeyBindings;
import com.ragedriver.sodiumvolt.client.inspector.VoltInspectorEngine;
import com.ragedriver.sodiumvolt.client.performance.AdaptivePerformanceController;
import com.ragedriver.sodiumvolt.client.performance.AnimatedTextureThrottleEngine;
import com.ragedriver.sodiumvolt.client.performance.BlockEntityRenderBudgetEngine;
import com.ragedriver.sodiumvolt.client.performance.VramPressureProtectionEngine;
import com.ragedriver.sodiumvolt.client.privacy.PrivacyScreenshotEngine;
import com.ragedriver.sodiumvolt.client.profile.PerformanceProfileEngine;
import com.ragedriver.sodiumvolt.client.recovery.VoltRecoveryEngine;
import com.ragedriver.sodiumvolt.client.resourcepack.ResourcePackShieldEngine;
import com.ragedriver.sodiumvolt.client.smartfps.SmartFpsEngine;
import com.ragedriver.sodiumvolt.client.watchdog.GpuTimeoutWatchdogEngine;
import net.fabricmc.api.ClientModInitializer;

public class SodiumVoltClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Watchdog owns the first permanent CLIENT_STOPPING latch.
		GpuTimeoutWatchdogEngine.register();
		VoltRecoveryEngine.register();
		ResourcePackShieldEngine.register();
		PrivacyScreenshotEngine.register();
		VoltKeyBindings.register();
		// Registered first so scene counts describe pre-Volt-Guard extracted workload.
		VoltInspectorEngine.register();
		// Registered before Volt Guard so Guard only budgets this engine's survivors.
		BlockEntityRenderBudgetEngine.register();
		VoltGuardEngine.register();
		AdaptivePerformanceController.register();
		AnimatedTextureThrottleEngine.register();
		VramPressureProtectionEngine.register();
		SmartFpsEngine.register();
		// Register last so temporary option owners unwind before Profiles restores globals.
		PerformanceProfileEngine.register();
	}
}
