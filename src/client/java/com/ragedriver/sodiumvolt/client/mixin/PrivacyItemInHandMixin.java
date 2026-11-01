package com.ragedriver.sodiumvolt.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ragedriver.sodiumvolt.client.privacy.PrivacyScreenshotEngine;
import com.ragedriver.sodiumvolt.client.privacy.PrivacyCaptureFastGate;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class PrivacyItemInHandMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void sodiumVolt$hideHeldItem(
			float frameInterp,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			PlayerRenderState player,
			FirstPersonHandsAndItemsRenderState hands,
			CallbackInfo callbackInfo
	) {
		if (PrivacyCaptureFastGate.isCaptureActive()
				&& PrivacyScreenshotEngine.hidesHeldItem()) {
			callbackInfo.cancel();
		}
	}
}
