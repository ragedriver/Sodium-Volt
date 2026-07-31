package com.ragedriver.sodiumvolt.client.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = RenderRegion.class, remap = false)
public interface RenderRegionAccessor {
	@Accessor("sections")
	RenderSection[] sodiumVolt$getSections();
}
