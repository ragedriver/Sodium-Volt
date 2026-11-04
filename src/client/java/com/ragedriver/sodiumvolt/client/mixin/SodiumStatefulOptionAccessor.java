package com.ragedriver.sodiumvolt.client.mixin;

import net.caffeinemc.mods.sodium.client.config.structure.StatefulOption;
import net.caffeinemc.mods.sodium.client.config.value.DynamicValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Collection;

@Mixin(value = StatefulOption.class, remap = false)
public interface SodiumStatefulOptionAccessor<V> {
	@Accessor("value")
	void sodiumVolt$setAppliedValue(V value);

	@Accessor("modifiedValue")
	V sodiumVolt$getModifiedValue();

	@Accessor("applyDependents")
	Collection<DynamicValue<?>> sodiumVolt$getApplyDependents();
}
