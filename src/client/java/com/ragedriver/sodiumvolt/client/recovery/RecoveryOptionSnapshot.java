package com.ragedriver.sodiumvolt.client.recovery;

public record RecoveryOptionSnapshot(
		int renderDistance,
		int entityDistancePercent,
		int particleMode,
		int cloudMode,
		boolean ambientOcclusion,
		boolean entityShadows,
		int biomeBlendRadius,
		int graphicsPreset
) {
	public static final int PARTICLES_ALL = 0;
	public static final int PARTICLES_DECREASED = 1;
	public static final int PARTICLES_MINIMAL = 2;
	public static final int CLOUDS_OFF = 0;
	public static final int CLOUDS_FAST = 1;
	public static final int CLOUDS_FANCY = 2;
	public static final int GRAPHICS_FAST = 0;
	public static final int GRAPHICS_FANCY = 1;
	public static final int GRAPHICS_FABULOUS = 2;
	public static final int GRAPHICS_CUSTOM = 3;

	public RecoveryOptionSnapshot safeProfile(
			int safeRenderDistance,
			int safeEntityDistancePercent,
			boolean reduceExpensiveGraphics
	) {
		RecoveryOptionSnapshot desired = new RecoveryOptionSnapshot(
				Math.min(this.renderDistance, safeRenderDistance),
				Math.min(this.entityDistancePercent, safeEntityDistancePercent),
				reduceExpensiveGraphics ? PARTICLES_MINIMAL : this.particleMode,
				reduceExpensiveGraphics ? CLOUDS_OFF : this.cloudMode,
				reduceExpensiveGraphics ? false : this.ambientOcclusion,
				reduceExpensiveGraphics ? false : this.entityShadows,
				reduceExpensiveGraphics ? 0 : this.biomeBlendRadius,
				this.graphicsPreset
		);
		boolean changesGraphicsOptions =
				desired.renderDistance != this.renderDistance
						|| desired.entityDistancePercent != this.entityDistancePercent
						|| desired.particleMode != this.particleMode
						|| desired.cloudMode != this.cloudMode
						|| desired.ambientOcclusion != this.ambientOcclusion
						|| desired.entityShadows != this.entityShadows
						|| desired.biomeBlendRadius != this.biomeBlendRadius;
		return changesGraphicsOptions
				? new RecoveryOptionSnapshot(
						desired.renderDistance,
						desired.entityDistancePercent,
						desired.particleMode,
						desired.cloudMode,
						desired.ambientOcclusion,
						desired.entityShadows,
						desired.biomeBlendRadius,
						GRAPHICS_CUSTOM
				)
				: desired;
	}

	public RecoveryOptionSnapshot rebase(
			RecoveryOptionSnapshot actual,
			RecoveryOptionSnapshot lastApplied
	) {
		boolean externalOwnedField = hasExternalNonPresetField(actual, lastApplied);
		return new RecoveryOptionSnapshot(
				actual.renderDistance != lastApplied.renderDistance
						? actual.renderDistance : this.renderDistance,
				actual.entityDistancePercent != lastApplied.entityDistancePercent
						? actual.entityDistancePercent : this.entityDistancePercent,
				actual.particleMode != lastApplied.particleMode
						? actual.particleMode : this.particleMode,
				actual.cloudMode != lastApplied.cloudMode
						? actual.cloudMode : this.cloudMode,
				actual.ambientOcclusion != lastApplied.ambientOcclusion
						? actual.ambientOcclusion : this.ambientOcclusion,
				actual.entityShadows != lastApplied.entityShadows
						? actual.entityShadows : this.entityShadows,
				actual.biomeBlendRadius != lastApplied.biomeBlendRadius
						? actual.biomeBlendRadius : this.biomeBlendRadius,
				externalOwnedField || actual.graphicsPreset != lastApplied.graphicsPreset
						? actual.graphicsPreset : this.graphicsPreset
		);
	}

	public RestoreResult restoreOwned(
			RecoveryOptionSnapshot actual,
			RecoveryOptionSnapshot lastApplied
	) {
		boolean preservedOwnedField = hasExternalNonPresetField(actual, lastApplied);
		RecoveryOptionSnapshot restored = new RecoveryOptionSnapshot(
				actual.renderDistance == lastApplied.renderDistance
						? this.renderDistance : actual.renderDistance,
				actual.entityDistancePercent == lastApplied.entityDistancePercent
						? this.entityDistancePercent : actual.entityDistancePercent,
				actual.particleMode == lastApplied.particleMode
						? this.particleMode : actual.particleMode,
				actual.cloudMode == lastApplied.cloudMode
						? this.cloudMode : actual.cloudMode,
				actual.ambientOcclusion == lastApplied.ambientOcclusion
						? this.ambientOcclusion : actual.ambientOcclusion,
				actual.entityShadows == lastApplied.entityShadows
						? this.entityShadows : actual.entityShadows,
				actual.biomeBlendRadius == lastApplied.biomeBlendRadius
						? this.biomeBlendRadius : actual.biomeBlendRadius,
				preservedOwnedField || actual.graphicsPreset != lastApplied.graphicsPreset
						? actual.graphicsPreset : this.graphicsPreset
		);
		return new RestoreResult(restored, !restored.equals(actual));
	}

	private static boolean hasExternalNonPresetField(
			RecoveryOptionSnapshot actual,
			RecoveryOptionSnapshot lastApplied
	) {
		return actual.renderDistance != lastApplied.renderDistance
				|| actual.entityDistancePercent != lastApplied.entityDistancePercent
				|| actual.particleMode != lastApplied.particleMode
				|| actual.cloudMode != lastApplied.cloudMode
				|| actual.ambientOcclusion != lastApplied.ambientOcclusion
				|| actual.entityShadows != lastApplied.entityShadows
				|| actual.biomeBlendRadius != lastApplied.biomeBlendRadius;
	}

	public boolean isValid() {
		return this.renderDistance >= 2 && this.renderDistance <= 64
				&& this.entityDistancePercent >= 25 && this.entityDistancePercent <= 500
				&& this.particleMode >= PARTICLES_ALL && this.particleMode <= PARTICLES_MINIMAL
				&& this.cloudMode >= CLOUDS_OFF && this.cloudMode <= CLOUDS_FANCY
				&& this.biomeBlendRadius >= 0 && this.biomeBlendRadius <= 7
				&& this.graphicsPreset >= GRAPHICS_FAST
				&& this.graphicsPreset <= GRAPHICS_CUSTOM;
	}

	public record RestoreResult(RecoveryOptionSnapshot snapshot, boolean changed) {
	}
}
