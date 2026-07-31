package com.ragedriver.sodiumvolt.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.ragedriver.sodiumvolt.client.recovery.VoltRecoveryEngine;
import com.ragedriver.sodiumvolt.client.smartfps.SmartFpsEngine;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {
	@Inject(
			method = "getFramerateLimit()I",
			at = @At("RETURN"),
			cancellable = true,
			require = 1
	)
	private void sodiumVolt$applySmartFpsCap(CallbackInfoReturnable<Integer> callbackInfo) {
		int smartFpsLimit = SmartFpsEngine.applyFramerateLimit(
				Minecraft.getInstance(),
				callbackInfo.getReturnValueI(),
				System.nanoTime()
		);
		callbackInfo.setReturnValue(VoltRecoveryEngine.applyFramerateLimit(smartFpsLimit));
	}
}
