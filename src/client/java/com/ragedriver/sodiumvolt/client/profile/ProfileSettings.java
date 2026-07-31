package com.ragedriver.sodiumvolt.client.profile;

import java.util.Objects;

public record ProfileSettings(
		int renderDistance,
		int simulationDistance,
		int entityDistancePercent,
		int framerateLimit,
		ProfileParticleMode particleMode
) {
	public static final int RENDER_DISTANCE_MIN = 2;
	public static final int RENDER_DISTANCE_MAX = 32;
	public static final int SIMULATION_DISTANCE_MIN = 5;
	public static final int SIMULATION_DISTANCE_MAX = 32;
	public static final int ENTITY_DISTANCE_MIN = 50;
	public static final int ENTITY_DISTANCE_MAX = 500;
	public static final int ENTITY_DISTANCE_STEP = 25;
	public static final int FRAMERATE_LIMIT_MIN = 30;
	public static final int FRAMERATE_LIMIT_MAX = 260;
	public static final int FRAMERATE_LIMIT_STEP = 5;

	public ProfileSettings {
		particleMode = Objects.requireNonNull(particleMode, "particleMode");
	}

	public static ProfileSettings globalDefaults() {
		return new ProfileSettings(12, 12, 100, 120, ProfileParticleMode.ALL);
	}

	public static ProfileSettings singlePlayerDefaults() {
		return new ProfileSettings(16, 12, 125, 120, ProfileParticleMode.ALL);
	}

	public static ProfileSettings serverDefaults() {
		return new ProfileSettings(12, 8, 100, 120, ProfileParticleMode.DECREASED);
	}

	public ProfileSettings sanitized() {
		return new ProfileSettings(
				clamp(this.renderDistance, RENDER_DISTANCE_MIN, RENDER_DISTANCE_MAX),
				clamp(this.simulationDistance,
						SIMULATION_DISTANCE_MIN, SIMULATION_DISTANCE_MAX),
				clampStep(this.entityDistancePercent,
						ENTITY_DISTANCE_MIN, ENTITY_DISTANCE_MAX, ENTITY_DISTANCE_STEP),
				clampStep(this.framerateLimit,
						FRAMERATE_LIMIT_MIN, FRAMERATE_LIMIT_MAX, FRAMERATE_LIMIT_STEP),
				this.particleMode
		);
	}

	public boolean isSanitized() {
		return equals(sanitized());
	}

	public ProfileSettings rebase(ProfileSettings actual, ProfileSettings lastApplied) {
		Objects.requireNonNull(actual, "actual");
		Objects.requireNonNull(lastApplied, "lastApplied");
		return new ProfileSettings(
				actual.renderDistance != lastApplied.renderDistance
						? actual.renderDistance : this.renderDistance,
				actual.simulationDistance != lastApplied.simulationDistance
						? actual.simulationDistance : this.simulationDistance,
				actual.entityDistancePercent != lastApplied.entityDistancePercent
						? actual.entityDistancePercent : this.entityDistancePercent,
				actual.framerateLimit != lastApplied.framerateLimit
						? actual.framerateLimit : this.framerateLimit,
				actual.particleMode != lastApplied.particleMode
						? actual.particleMode : this.particleMode
		).sanitized();
	}

	public RestoreResult restoreOwned(
			ProfileSettings actual,
			ProfileSettings lastApplied
	) {
		Objects.requireNonNull(actual, "actual");
		Objects.requireNonNull(lastApplied, "lastApplied");
		ProfileSettings restored = new ProfileSettings(
				actual.renderDistance == lastApplied.renderDistance
						? this.renderDistance : actual.renderDistance,
				actual.simulationDistance == lastApplied.simulationDistance
						? this.simulationDistance : actual.simulationDistance,
				actual.entityDistancePercent == lastApplied.entityDistancePercent
						? this.entityDistancePercent : actual.entityDistancePercent,
				actual.framerateLimit == lastApplied.framerateLimit
						? this.framerateLimit : actual.framerateLimit,
				actual.particleMode == lastApplied.particleMode
						? this.particleMode : actual.particleMode
		).sanitized();
		return new RestoreResult(restored, !restored.equals(actual));
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static int clampStep(int value, int minimum, int maximum, int step) {
		int clamped = clamp(value, minimum, maximum);
		int offset = clamped - minimum;
		return Math.min(maximum, minimum + ((offset + step / 2) / step) * step);
	}

	public record RestoreResult(ProfileSettings settings, boolean changed) {
	}
}
