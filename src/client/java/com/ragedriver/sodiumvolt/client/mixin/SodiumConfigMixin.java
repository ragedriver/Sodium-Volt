package com.ragedriver.sodiumvolt.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.ragedriver.sodiumvolt.client.config.SodiumVoltConfigEntryPoint;
import net.caffeinemc.mods.sodium.client.config.structure.Config;
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.config.structure.StatefulOption;
import net.caffeinemc.mods.sodium.client.config.value.DynamicValue;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

@Mixin(value = Config.class, remap = false)
public abstract class SodiumConfigMixin {
	@Shadow
	@Final
	private Map<Identifier, Option> options;

	@Shadow
	abstract void invalidateDependents(Collection<DynamicValue<?>> changed);

	@ModifyExpressionValue(
			method = "applyAllOptions",
			at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;")
	)
	private Collection<Option> sodiumVolt$orderVoltEdits(Collection<Option> options) {
		Option preset = this.options.get(SodiumVoltConfigEntryPoint.VOLT_TUNER_PROFILE);
		boolean applyPreset = preset instanceof StatefulOption<?> stateful && stateful.hasChanged();
		Option minimum = this.options.get(SodiumVoltConfigEntryPoint.APC_MIN_RENDER_DISTANCE);
		Option maximum = this.options.get(SodiumVoltConfigEntryPoint.APC_MAX_RENDER_DISTANCE);
		boolean applyMaximumFirst = minimum instanceof IntegerOption min && min.hasChanged()
				&& maximum instanceof IntegerOption max && max.hasChanged()
				&& min.getValidatedValue() <= max.getValidatedValue()
				// Every preset uses a minimum below the lowest selectable maximum.
				&& (applyPreset || min.getValidatedValue() > max.getAppliedValue());
		if (!applyPreset && !applyMaximumFirst) {
			return options;
		}

		// Keep Sodium's storage and flag handling, but let later manual edits override the preset.
		var ordered = new ArrayList<>(options);
		if (applyPreset) {
			ordered.remove(preset);
			ordered.addFirst(preset);
		}
		if (applyMaximumFirst) {
			Collections.swap(ordered, ordered.indexOf(minimum), ordered.indexOf(maximum));
		}
		return ordered;
	}

	@Inject(
			method = "applyAllOptions",
			at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/config/structure/Config;flushStorageHandlers()V", shift = At.Shift.AFTER)
	)
	private void sodiumVolt$refreshAfterApplyAll(CallbackInfo ci) {
		sodiumVolt$refreshVoltOptions(null);
	}

	@Inject(
			method = "applyOption",
			at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/config/structure/Config;flushStorageHandlers()V", shift = At.Shift.AFTER)
	)
	private void sodiumVolt$refreshAfterApplyOption(Identifier appliedId, CallbackInfo ci) {
		if (appliedId != null && "sodium-volt".equals(appliedId.getNamespace())) {
			sodiumVolt$refreshVoltOptions(appliedId);
		}
	}

	@Unique
	private void sodiumVolt$refreshVoltOptions(Identifier appliedId) {
		for (var entry : this.options.entrySet()) {
			Identifier id = entry.getKey();
			if ("sodium-volt".equals(id.getNamespace()) && entry.getValue() instanceof StatefulOption<?> stateful) {
				boolean preserveDraft = appliedId != null && !appliedId.equals(id);
				sodiumVolt$refreshFromBinding(stateful, preserveDraft);
			}
		}
	}

	@Unique
	@SuppressWarnings("unchecked")
	private <V> void sodiumVolt$refreshFromBinding(StatefulOption<V> option, boolean preserveDraft) {
		V effectiveValue = option.getBinding().load();
		if (Objects.equals(option.getAppliedValue(), effectiveValue)) {
			return;
		}

		var accessor = (SodiumStatefulOptionAccessor<V>) option;
		// The backing setter already normalized the value. Resetting from the binding
		// would run Sodium's validator against any pending edits and could write again.
		boolean keepDraft = preserveDraft && option.hasChanged()
				&& !Objects.equals(accessor.sodiumVolt$getModifiedValue(), effectiveValue);
		accessor.sodiumVolt$setAppliedValue(effectiveValue);
		if (!keepDraft) {
			option.modifyValue(effectiveValue);
		}
		this.invalidateDependents(accessor.sodiumVolt$getApplyDependents());
	}
}
