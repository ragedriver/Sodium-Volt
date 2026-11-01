package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.performance.BlockEntityRenderBudgetEngine;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorBlockEntityMixin {
	@Inject(method = "setLevel", at = @At("HEAD"))
	private void sodiumVolt$clearBlockEntityCache(ClientLevel level, CallbackInfo callbackInfo) {
		BlockEntityRenderBudgetEngine.onLevelChanged(level);
	}
}
