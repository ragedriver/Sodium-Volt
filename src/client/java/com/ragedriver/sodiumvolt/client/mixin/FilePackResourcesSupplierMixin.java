package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.resourcepack.ResourcePackShieldEngine;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

@Mixin(FilePackResources.FileResourcesSupplier.class)
public abstract class FilePackResourcesSupplierMixin {
	@Shadow
	@Final
	private File content;

	@Inject(
			method = "openFull",
			at = @At("RETURN"),
			cancellable = true
	)
	private void sodiumVolt$guardOpenedArchive(
			PackLocationInfo location,
			Pack.Metadata metadata,
			CallbackInfoReturnable<PackResources> callbackInfo
	) {
		callbackInfo.setReturnValue(ResourcePackShieldEngine.guardArchive(
				this.content.toPath(),
				metadata,
				location,
				callbackInfo.getReturnValue()
		));
	}
}
