package com.ragedriver.sodiumvolt.client.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor<T> {
	@Accessor("value")
	void sodiumVolt$setValueWithoutCallback(T value);
}
