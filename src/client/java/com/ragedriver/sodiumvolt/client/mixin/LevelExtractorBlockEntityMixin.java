package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.performance.BlockEntityRenderBudgetEngine;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorBlockEntityMixin {
	@Shadow
	private ClientLevel level;

	@Inject(method = "extractVisibleBlockEntities", at = @At("HEAD"))
	private void sodiumVolt$beginBlockEntityExtraction(
			Camera camera,
			float partialTick,
			LevelRenderState levelRenderState,
			CallbackInfo callbackInfo
	) {
		BlockEntityRenderBudgetEngine.beginFrame(this.level, camera);
	}

	@Redirect(
			method = "extractVisibleBlockEntities",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;"
							+ "tryExtractRenderState("
							+ "Lnet/minecraft/world/level/block/entity/BlockEntity;F"
							+ "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Z)"
							+ "Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;"
			),
			require = 2
	)
	private BlockEntityRenderState sodiumVolt$scheduleBlockEntityExtraction(
			BlockEntityRenderDispatcher dispatcher,
			BlockEntity blockEntity,
			float partialTick,
			ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
			boolean offscreen
	) {
		return BlockEntityRenderBudgetEngine.extract(
				dispatcher,
				blockEntity,
				partialTick,
				crumblingOverlay,
				offscreen
		);
	}

	@Inject(method = "setLevel", at = @At("HEAD"))
	private void sodiumVolt$clearBlockEntityCache(ClientLevel level, CallbackInfo callbackInfo) {
		BlockEntityRenderBudgetEngine.onLevelChanged(level);
	}
}
