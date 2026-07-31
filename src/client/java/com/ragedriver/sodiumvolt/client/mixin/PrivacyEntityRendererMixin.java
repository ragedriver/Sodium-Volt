package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.privacy.PrivacyScreenshotEngine;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class PrivacyEntityRendererMixin {
	@Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)"
			+ "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
			at = @At("RETURN"))
	private void sodiumVolt$removeNameTags(
			Entity entity,
			float partialTicks,
			CallbackInfoReturnable<EntityRenderState> callbackInfo
	) {
		if (!PrivacyScreenshotEngine.hidesNameTags()) {
			return;
		}
		EntityRenderState state = callbackInfo.getReturnValue();
		if (state != null) {
			state.nameTag = null;
			state.scoreText = null;
			state.nameTagAttachment = null;
		}
	}
}
