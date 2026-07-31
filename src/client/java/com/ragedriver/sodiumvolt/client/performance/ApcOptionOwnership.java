package com.ragedriver.sodiumvolt.client.performance;

import net.minecraft.client.GraphicsPreset;

import java.util.Objects;

record ApcOptionOwnership(
		AdaptivePerformanceController.OptionSnapshot original,
		AdaptivePerformanceController.OptionSnapshot lastApplied,
		GraphicsPreset originalGraphicsPreset,
		GraphicsPreset lastAppliedGraphicsPreset
) {
	ApcOptionOwnership {
		Objects.requireNonNull(original, "original");
		Objects.requireNonNull(lastApplied, "lastApplied");
		Objects.requireNonNull(originalGraphicsPreset, "originalGraphicsPreset");
		Objects.requireNonNull(lastAppliedGraphicsPreset, "lastAppliedGraphicsPreset");
	}

	ApcOptionOwnership prepareForOwnedMutation(
			AdaptivePerformanceController.OptionSnapshot actual,
			GraphicsPreset actualGraphicsPreset
	) {
		Objects.requireNonNull(actual, "actual");
		Objects.requireNonNull(actualGraphicsPreset, "actualGraphicsPreset");
		if (actual.equals(this.lastApplied)
				&& actualGraphicsPreset == this.lastAppliedGraphicsPreset) {
			return this;
		}
		return new ApcOptionOwnership(
				this.original.rebase(actual, this.lastApplied),
				actual,
				actualGraphicsPreset == this.lastAppliedGraphicsPreset
						? this.originalGraphicsPreset
						: actualGraphicsPreset,
				actualGraphicsPreset
		);
	}

	ApcOptionOwnership alignAfterOwnedMutation(
			AdaptivePerformanceController.OptionSnapshot actual,
			GraphicsPreset actualGraphicsPreset
	) {
		return new ApcOptionOwnership(
				this.original,
				actual,
				this.originalGraphicsPreset,
				actualGraphicsPreset
		);
	}
}
