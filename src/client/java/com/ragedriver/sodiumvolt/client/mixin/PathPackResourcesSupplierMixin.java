package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.resourcepack.ResourcePackShieldEngine;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

@Mixin(PathPackResources.PathResourcesSupplier.class)
public abstract class PathPackResourcesSupplierMixin {
	@Shadow
	@Final
	private Path content;

	@Inject(
			method = "openFull",
			at = @At("RETURN"),
			cancellable = true
	)
	private void sodiumVolt$guardOpenedDirectory(
			PackLocationInfo location,
			Pack.Metadata metadata,
			CallbackInfoReturnable<PackResources> callbackInfo
	) {
		callbackInfo.setReturnValue(ResourcePackShieldEngine.guardDirectory(
				this.content,
				metadata,
				location,
				callbackInfo.getReturnValue()
		));
	}
}
