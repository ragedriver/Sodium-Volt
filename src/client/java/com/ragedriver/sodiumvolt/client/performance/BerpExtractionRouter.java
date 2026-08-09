package com.ragedriver.sodiumvolt.client.performance;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Routes the extraction redirect without consulting configuration on its hot path. */
public final class BerpExtractionRouter {
	private static final ExtractionPath<
			BlockEntityRenderDispatcher,
			BlockEntity,
			ModelFeatureRenderer.CrumblingOverlay,
			BlockEntityRenderState
	> BACKEND_PATH = BlockEntityRenderBudgetEngine::extract;
	private static final ExtractionPath<
			BlockEntityRenderDispatcher,
			BlockEntity,
			ModelFeatureRenderer.CrumblingOverlay,
			BlockEntityRenderState
	> VANILLA_PATH = BlockEntityRenderDispatcher::tryExtractRenderState;

	private BerpExtractionRouter() {
	}

	public static BlockEntityRenderState route(
			BlockEntityRenderDispatcher dispatcher,
			BlockEntity blockEntity,
			float partialTick,
			ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
			boolean offscreen
	) {
		return routeWithGate(
				dispatcher,
				blockEntity,
				partialTick,
				crumblingOverlay,
				offscreen,
				BACKEND_PATH,
				VANILLA_PATH
		);
	}

	static <D, E, O, R> R routeWithGate(
			D dispatcher,
			E blockEntity,
			float partialTick,
			O crumblingOverlay,
			boolean offscreen,
			ExtractionPath<D, E, O, R> backendPath,
			ExtractionPath<D, E, O, R> vanillaPath
	) {
		ExtractionPath<D, E, O, R> path = BerpHotPathGate.extractionActive()
				? backendPath
				: vanillaPath;
		return path.extract(
				dispatcher,
				blockEntity,
				partialTick,
				crumblingOverlay,
				offscreen
		);
	}

	@FunctionalInterface
	interface ExtractionPath<D, E, O, R> {
		R extract(D dispatcher, E blockEntity, float partialTick, O crumblingOverlay, boolean offscreen);
	}
}
