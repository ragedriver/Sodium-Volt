package com.ragedriver.sodiumvolt.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ragedriver.sodiumvolt.client.privacy.PrivacyScreenshotEngine;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftPrivacyScreenshotMixin {
	@WrapOperation(
			method = "handleGlobalKeyPress(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)Z",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/Screenshot;grab(Lnet/minecraft/client/Minecraft;Z)V"
			)
	)
	private void sodiumVolt$interceptPrivacyScreenshot(
			Minecraft minecraft,
			boolean debugPanoramaRequested,
			Operation<Void> original
	) {
		if (!PrivacyScreenshotEngine.interceptVanillaCapture(
				minecraft,
				debugPanoramaRequested
		)) {
			original.call(minecraft, debugPanoramaRequested);
		}
	}

	@WrapMethod(method = "renderFrame(Z)V")
	private void sodiumVolt$scopePrivacyCaptureFrame(
			boolean advanceGameTime,
			Operation<Void> original
	) {
		PrivacyScreenshotEngine.FrameScope scope =
				PrivacyScreenshotEngine.beginRenderFrame((Minecraft) (Object) this);
		try {
			original.call(advanceGameTime);
		} finally {
			scope.close();
		}
	}

	@Inject(
			method = "renderFrame(Z)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V",
					shift = At.Shift.AFTER
			)
	)
	private void sodiumVolt$captureBeforePresentation(
			boolean advanceGameTime,
			CallbackInfo callbackInfo
	) {
		PrivacyScreenshotEngine.captureRenderedFrame((Minecraft) (Object) this);
	}
}
