package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.resourcepack.ResourcePackShieldEngine;
import com.ragedriver.sodiumvolt.client.resourcepack.ShieldResourceStream;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
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
import java.util.stream.Stream;

@Mixin(PathPackResources.PathResourcesSupplier.class)
public abstract class PathPackResourcesSupplierMixin {
	@Shadow
	@Final
	private Path content;

	@Inject(method = "openMetadata", at = @At("RETURN"), cancellable = true)
	private void sodiumVolt$guardMetadata(
			PackLocationInfo location, CallbackInfoReturnable<PackMetadataResources> callbackInfo
	) {
		callbackInfo.setReturnValue(ResourcePackShieldEngine.guardMetadata(
				this.content, location, callbackInfo.getReturnValue(), false, null
		));
	}

	@Inject(
			method = "openResources",
			at = @At("RETURN"),
			cancellable = true
	)
	private void sodiumVolt$guardOpenedDirectory(
			PackLocationInfo location,
			Pack.Metadata metadata,
			CallbackInfoReturnable<Stream<PackResources>> callbackInfo
	) {
		callbackInfo.setReturnValue(ShieldResourceStream.guard(callbackInfo.getReturnValue(), delegate -> ResourcePackShieldEngine.guardDirectory(
				this.content,
				metadata,
				location,
				delegate
		)));
	}
}
