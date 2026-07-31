package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.privacy.PrivacyScreenshotEngine;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class PrivacyHudMixin {
	@Inject(method = "extractChat", at = @At("HEAD"), cancellable = true)
	private void sodiumVolt$hideChat(
			GuiGraphicsExtractor graphics,
			DeltaTracker deltaTracker,
			CallbackInfo callbackInfo
	) {
		if (PrivacyScreenshotEngine.hidesChat()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "extractDebugOverlay", at = @At("HEAD"), cancellable = true)
	private void sodiumVolt$hideDebugOverlay(
			GuiGraphicsExtractor graphics,
			CallbackInfo callbackInfo
	) {
		if (PrivacyScreenshotEngine.hidesDebugOverlay()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "extractTabList", at = @At("HEAD"), cancellable = true)
	private void sodiumVolt$hidePlayerList(
			GuiGraphicsExtractor graphics,
			DeltaTracker deltaTracker,
			CallbackInfo callbackInfo
	) {
		if (PrivacyScreenshotEngine.hidesPlayerList()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
	private void sodiumVolt$hideScoreboard(
			GuiGraphicsExtractor graphics,
			DeltaTracker deltaTracker,
			CallbackInfo callbackInfo
	) {
		if (PrivacyScreenshotEngine.hidesScoreboard()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true)
	private void sodiumVolt$hideBossBars(
			GuiGraphicsExtractor graphics,
			DeltaTracker deltaTracker,
			CallbackInfo callbackInfo
	) {
		if (PrivacyScreenshotEngine.hidesBossBars()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void sodiumVolt$hideActionBar(
			GuiGraphicsExtractor graphics,
			DeltaTracker deltaTracker,
			CallbackInfo callbackInfo
	) {
		if (PrivacyScreenshotEngine.hidesTitlesAndActionBar()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true)
	private void sodiumVolt$hideTitles(
			GuiGraphicsExtractor graphics,
			DeltaTracker deltaTracker,
			CallbackInfo callbackInfo
	) {
		if (PrivacyScreenshotEngine.hidesTitlesAndActionBar()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "extractSubtitleOverlay", at = @At("HEAD"), cancellable = true)
	private void sodiumVolt$hideSubtitles(
			GuiGraphicsExtractor graphics,
			boolean deferRendering,
			CallbackInfo callbackInfo
	) {
		if (PrivacyScreenshotEngine.hidesSubtitles()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "extractDeferredSubtitles", at = @At("HEAD"), cancellable = true)
	private void sodiumVolt$hideDeferredSubtitles(CallbackInfo callbackInfo) {
		if (PrivacyScreenshotEngine.hidesSubtitles()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "extractSavingIndicator", at = @At("HEAD"), cancellable = true)
	private void sodiumVolt$hideSavingIndicator(
			GuiGraphicsExtractor graphics,
			DeltaTracker deltaTracker,
			CallbackInfo callbackInfo
	) {
		if (PrivacyScreenshotEngine.hidesToastsAndSavingIndicator()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
	private void sodiumVolt$hideCrosshair(
			GuiGraphicsExtractor graphics,
			DeltaTracker deltaTracker,
			CallbackInfo callbackInfo
	) {
		if (PrivacyScreenshotEngine.hidesGameplayHud()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
	private void sodiumVolt$hideEffects(
			GuiGraphicsExtractor graphics,
			DeltaTracker deltaTracker,
			CallbackInfo callbackInfo
	) {
		if (PrivacyScreenshotEngine.hidesGameplayHud()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
	private void sodiumVolt$hideHotbar(
			GuiGraphicsExtractor graphics,
			DeltaTracker deltaTracker,
			CallbackInfo callbackInfo
	) {
		if (PrivacyScreenshotEngine.hidesGameplayHud()) {
			callbackInfo.cancel();
		}
	}
}
