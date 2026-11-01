package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.performance.BerpExtractionRouter;
import com.ragedriver.sodiumvolt.client.performance.BlockEntityRenderBudgetEngine;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.SortedSet;

/** Sodium cancels vanilla block-entity extraction, so its actual extraction path needs the same backend. */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class SodiumWorldRendererBlockEntityMixin {
	@Shadow
	private ClientLevel level;

	@Inject(method = "extractBlockEntities", at = @At("HEAD"), require = 1)
	private void sodiumVolt$beginBlockEntityExtraction(
			Camera camera,
			float partialTick,
			Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress,
			LevelRenderState levelRenderState,
			CallbackInfo callbackInfo
	) {
		BlockEntityRenderBudgetEngine.beginFrame(this.level, camera);
	}

	@Redirect(
			method = "extractBlockEntity",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;"
							+ "tryExtractRenderState("
							+ "Lnet/minecraft/world/level/block/entity/BlockEntity;F"
							+ "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Z)"
							+ "Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;"
			),
			require = 1
	)
	private BlockEntityRenderState sodiumVolt$scheduleBlockEntityExtraction(
			BlockEntityRenderDispatcher dispatcher,
			BlockEntity blockEntity,
			float partialTick,
			ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
			boolean offscreen
	) {
		return BerpExtractionRouter.route(dispatcher, blockEntity, partialTick, crumblingOverlay, offscreen);
	}
}
