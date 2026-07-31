package com.ragedriver.sodiumvolt.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ragedriver.sodiumvolt.client.privacy.PrivacyScreenshotEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class PrivacyItemInHandMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void sodiumVolt$hideHeldItem(
			float frameInterp,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			LocalPlayer player,
			int lightCoords,
			CallbackInfo callbackInfo
	) {
		if (PrivacyScreenshotEngine.hidesHeldItem()) {
			callbackInfo.cancel();
		}
	}
}
