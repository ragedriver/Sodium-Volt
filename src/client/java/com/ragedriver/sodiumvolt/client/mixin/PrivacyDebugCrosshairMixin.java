package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.privacy.PrivacyCaptureFastGate;
import com.ragedriver.sodiumvolt.client.privacy.PrivacyScreenshotEngine;
import net.minecraft.client.renderer.DebugCrosshairRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The three-dimensional debug crosshair renders separately from the extracted HUD. */
@Mixin(DebugCrosshairRenderer.class)
public abstract class PrivacyDebugCrosshairMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 1)
	private void sodiumVolt$hideDebugCrosshair(CallbackInfo callbackInfo) {
		if (PrivacyCaptureFastGate.isCaptureActive()
				&& (PrivacyScreenshotEngine.hidesDebugOverlay() || PrivacyScreenshotEngine.hidesGameplayHud())) {
			callbackInfo.cancel();
		}
	}
}
