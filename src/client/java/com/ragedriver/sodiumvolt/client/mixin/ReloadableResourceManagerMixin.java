package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.resourcepack.ResourcePackShieldEngine;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.CompletableFuture;

@Mixin(ReloadableResourceManager.class)
public abstract class ReloadableResourceManagerMixin {
	@ModifyArg(
			method = "createReload(Ljava/util/concurrent/Executor;"
					+ "Ljava/util/concurrent/Executor;"
					+ "Ljava/util/concurrent/CompletableFuture;"
					+ "Ljava/util/List;)"
					+ "Lnet/minecraft/server/packs/resources/ReloadInstance;",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/packs/resources/SimpleReloadInstance;"
							+ "create(Lnet/minecraft/server/packs/resources/ResourceManager;"
							+ "Ljava/util/List;"
							+ "Ljava/util/concurrent/Executor;"
							+ "Ljava/util/concurrent/Executor;"
							+ "Ljava/util/concurrent/CompletableFuture;"
							+ "Z)Lnet/minecraft/server/packs/resources/ReloadInstance;"
			),
			index = 4
	)
	private CompletableFuture<Unit> sodiumVolt$failConstructorTimePackRejection(
			CompletableFuture<Unit> initialTask
	) {
		return ResourcePackShieldEngine.guardInitialReloadTask(
				initialTask,
				ResourcePackShieldEngine.currentSynchronousReloadToken()
		);
	}
}
