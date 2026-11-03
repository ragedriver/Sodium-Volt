package com.ragedriver.sodiumvolt.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ragedriver.sodiumvolt.client.resourcepack.ResourcePackShieldEngine;
import com.ragedriver.sodiumvolt.client.resourcepack.ShieldArchiveSnapshot;
import com.ragedriver.sodiumvolt.client.resourcepack.ShieldResourceStream;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.io.File;
import java.util.Map;
import java.util.stream.Stream;

@Mixin(FilePackResources.FileResourcesSupplier.class)
public abstract class FilePackResourcesSupplierMixin {
	@Shadow
	@Final
	private File content;

	@Unique
	private static final ScopedValue<Map.Entry<Object, File>> SODIUM_VOLT_ARCHIVE = ScopedValue.newInstance();

	@WrapOperation(
			method = {"openMetadata", "openResources"},
			at = @At(value = "FIELD", target = "Lnet/minecraft/server/packs/FilePackResources$FileResourcesSupplier;content:Ljava/io/File;"),
			require = 2
	)
	private File sodiumVolt$useSnapshot(FilePackResources.FileResourcesSupplier supplier, Operation<File> original) {
		Map.Entry<Object, File> binding = SODIUM_VOLT_ARCHIVE.isBound() ? SODIUM_VOLT_ARCHIVE.get() : null;
		return binding != null && binding.getKey() == supplier ? binding.getValue() : original.call(supplier);
	}

	@WrapMethod(method = "openMetadata")
	private PackMetadataResources sodiumVolt$guardMetadata(
			PackLocationInfo location, Operation<PackMetadataResources> original
	) {
		try (ShieldArchiveSnapshot snapshot = ResourcePackShieldEngine.prepareArchive(this.content.toPath(), location, true)) {
			PackMetadataResources metadata = snapshot == null || snapshot.path() == null
					? original.call(location)
					: ScopedValue.where(SODIUM_VOLT_ARCHIVE, Map.entry(this, snapshot.path().toFile()))
							.call(() -> original.call(location));
			return ResourcePackShieldEngine.guardMetadata(this.content.toPath(), location, metadata, true, snapshot);
		}
	}

	@WrapMethod(method = "openResources")
	private Stream<PackResources> sodiumVolt$guardOpenedArchive(
			PackLocationInfo location, Pack.Metadata metadata, Operation<Stream<PackResources>> original
	) {
		try (ShieldArchiveSnapshot snapshot = ResourcePackShieldEngine.prepareArchive(this.content.toPath(), location, false)) {
			Stream<PackResources> guarded;
			if (snapshot == null || snapshot.path() == null) {
				guarded = sodiumVolt$guardResources(original.call(location, metadata), metadata, location, snapshot);
			} else {
				guarded = ScopedValue.where(SODIUM_VOLT_ARCHIVE, Map.entry(this, snapshot.path().toFile())).call(
						() -> sodiumVolt$guardResources(original.call(location, metadata), metadata, location, snapshot)
				);
			}
			return guarded;
		}
	}

	@Unique
	private Stream<PackResources> sodiumVolt$guardResources(
			Stream<PackResources> resources, Pack.Metadata metadata, PackLocationInfo location,
			ShieldArchiveSnapshot snapshot
	) {
		return ShieldResourceStream.guard(resources, delegate -> ResourcePackShieldEngine.guardArchive(
				this.content.toPath(), metadata, location, delegate, snapshot
		));
	}
}
