package com.ragedriver.sodiumvolt.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ragedriver.sodiumvolt.client.performance.AdaptivePerformanceController;
import com.ragedriver.sodiumvolt.client.performance.VramPressureProtectionEngine;
import com.ragedriver.sodiumvolt.client.inspector.VoltInspectorEngine;
import com.ragedriver.sodiumvolt.client.recovery.VoltRecoveryEngine;
import com.ragedriver.sodiumvolt.client.watchdog.GpuTimeoutWatchdogEngine;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftRenderMixin {
	@WrapMethod(method = "runTick(Z)V")
	private void sodiumVolt$monitorClientTick(boolean runGameTick, Operation<Void> original) {
		Minecraft minecraft = (Minecraft) (Object) this;
		if (GpuTimeoutWatchdogEngine.requiresRenderFrameBeginTimestamp()) {
			GpuTimeoutWatchdogEngine.onRenderFrameBegin(minecraft, System.nanoTime());
		}
		try {
			original.call(runGameTick);
		} finally {
			if (GpuTimeoutWatchdogEngine.requiresRenderFrameEndTimestamp()) {
				GpuTimeoutWatchdogEngine.onRenderFrameEnd(minecraft, System.nanoTime());
			}
		}
	}

	@Inject(method = "renderFrame(Z)V", at = @At("HEAD"))
	private void sodiumVolt$refreshRenderContext(boolean renderLevel, CallbackInfo callbackInfo) {
		Minecraft minecraft = (Minecraft) (Object) this;
		if (GpuTimeoutWatchdogEngine.requiresRenderContextRefresh()) {
			GpuTimeoutWatchdogEngine.refreshRenderContext(minecraft, System.nanoTime());
		}
		VoltInspectorEngine.publishStatisticsDemand(minecraft);
	}

	@Inject(method = "renderFrame(Z)V", at = @At("TAIL"))
	private void sodiumVolt$sampleAdaptivePerformanceController(
			boolean renderLevel,
			CallbackInfo callbackInfo
	) {
		boolean recovery = VoltRecoveryEngine.requiresRenderFrameTimestamp();
		boolean adaptive = AdaptivePerformanceController.requiresRenderFrameTimestamp();
		boolean vram = VramPressureProtectionEngine.requiresRenderFrameTimestamp();
		if (!recovery && !adaptive && !vram) {
			return;
		}
		Minecraft minecraft = (Minecraft) (Object) this;
		long nowNanos = System.nanoTime();
		if (recovery) {
			VoltRecoveryEngine.onRenderFrame(minecraft, nowNanos);
		}
		if (adaptive) {
			AdaptivePerformanceController.onRenderFrame(minecraft, nowNanos);
		}
		if (vram) {
			VramPressureProtectionEngine.onRenderFrame(minecraft, nowNanos);
		}
	}
}
