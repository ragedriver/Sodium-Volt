package com.ragedriver.sodiumvolt.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.ragedriver.sodiumvolt.client.config.SodiumVoltConfigEntryPoint.DependentMinimumRange;
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption;
import net.caffeinemc.mods.sodium.client.config.structure.StatefulOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StatefulOption.class, remap = false)
public abstract class SodiumStatefulOptionMixin<V> {
	@Shadow
	private V modifiedValue;

	@Inject(
			method = "getValidatedValue",
			at = @At(
					value = "FIELD",
					target = "Lnet/caffeinemc/mods/sodium/client/config/structure/StatefulOption;modifiedValue:Ljava/lang/Object;",
					opcode = Opcodes.PUTFIELD
			),
			cancellable = true
	)
	private void sodiumVolt$previewDependentMinimum(CallbackInfoReturnable<V> cir, @Local(ordinal = 0) V validated) {
		if ((Object) this instanceof IntegerOption integerOption
				&& integerOption.getSteppedValidator() instanceof DependentMinimumRange range
				&& this.modifiedValue instanceof Integer draft && draft < range.min()) {
			// A draft parent may move back down before Apply. Preview its bound
			// without replacing an explicit draft or marking this option edited.
			cir.setReturnValue(validated);
		}
	}
}
