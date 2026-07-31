package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.performance.AdaptivePerformanceController;
import com.ragedriver.sodiumvolt.client.performance.VramPressureProtectionEngine;
import com.ragedriver.sodiumvolt.client.recovery.VoltRecoveryEngine;
import com.ragedriver.sodiumvolt.client.watchdog.GpuTimeoutWatchdogEngine;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftRenderMixin {
	@Inject(method = "renderFrame(Z)V", at = @At("HEAD"))
	private void sodiumVolt$beginGpuTimeoutWatchdogHeartbeat(
			boolean renderLevel,
			CallbackInfo callbackInfo
	) {
		GpuTimeoutWatchdogEngine.onRenderFrameBegin(
				(Minecraft) (Object) this,
				System.nanoTime()
		);
	}

	@Inject(method = "renderFrame(Z)V", at = @At("TAIL"))
	private void sodiumVolt$sampleAdaptivePerformanceController(
			boolean renderLevel,
			CallbackInfo callbackInfo
	) {
		GpuTimeoutWatchdogEngine.onRenderFrameEnd(
				(Minecraft) (Object) this,
				System.nanoTime()
		);
		VoltRecoveryEngine.onRenderFrame((Minecraft) (Object) this, System.nanoTime());
		AdaptivePerformanceController.onRenderFrame((Minecraft) (Object) this, System.nanoTime());
		VramPressureProtectionEngine.onRenderFrame(
				(Minecraft) (Object) this,
				System.nanoTime()
		);
	}
}
