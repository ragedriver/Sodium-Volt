package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.debug.SodiumVoltDebugLine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
	@Inject(method = "extractLines", at = @At("HEAD"))
	private void sodiumVolt$insertLoadedLine(
			GuiGraphicsExtractor graphics,
			List<String> lines,
			boolean alignLeft,
			CallbackInfo callbackInfo
	) {
		SodiumVoltDebugLine.insertIfDebugVisible(
				Minecraft.getInstance().debugEntries.isOverlayVisible(),
				lines
		);
	}
}
