package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.privacy.PrivacyScreenshotEngine;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public abstract class PrivacyToastMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void sodiumVolt$hideToasts(
			GuiGraphicsExtractor graphics,
			CallbackInfo callbackInfo
	) {
		if (PrivacyScreenshotEngine.hidesToastsAndSavingIndicator()) {
			callbackInfo.cancel();
		}
	}
}
