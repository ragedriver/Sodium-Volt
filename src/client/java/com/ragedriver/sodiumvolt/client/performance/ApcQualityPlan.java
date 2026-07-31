package com.ragedriver.sodiumvolt.client.performance;

final class ApcQualityPlan {
	private static final int PARTICLE_LEVELS = 2;
	private static final int ENTITY_LEVELS = 2;
	private static final int VISUAL_LEVELS = 3;

	private ApcQualityPlan() {
	}

	static int maximumLevel(
			int startingRenderDistance,
			int minimumRenderDistance,
			boolean adaptiveParticles,
			boolean adaptiveEntities,
			boolean adaptiveRenderDistance,
			boolean adaptiveVisualEffects
	) {
		int level = 0;
		if (adaptiveParticles) {
			level += PARTICLE_LEVELS;
		}
		if (adaptiveEntities) {
			level += ENTITY_LEVELS;
		}
		if (adaptiveRenderDistance) {
			level += renderSteps(startingRenderDistance, minimumRenderDistance);
		}
		if (adaptiveVisualEffects) {
			level += VISUAL_LEVELS;
		}
		return level;
	}

	static Stages stages(
			int requestedLevel,
			int startingRenderDistance,
			int minimumRenderDistance,
			boolean adaptiveParticles,
			boolean adaptiveEntities,
			boolean adaptiveRenderDistance,
			boolean adaptiveVisualEffects
	) {
		int remaining = Math.max(0, requestedLevel);
		int particleStage = adaptiveParticles ? Math.min(PARTICLE_LEVELS, remaining) : 0;
		remaining -= particleStage;
		int entityStage = adaptiveEntities ? Math.min(ENTITY_LEVELS, remaining) : 0;
		remaining -= entityStage;
		int maximumRenderSteps = adaptiveRenderDistance
				? renderSteps(startingRenderDistance, minimumRenderDistance)
				: 0;
		int renderStage = Math.min(maximumRenderSteps, remaining);
		remaining -= renderStage;
		int visualStage = adaptiveVisualEffects ? Math.min(VISUAL_LEVELS, remaining) : 0;
		int renderDistance = Math.max(
				Math.min(startingRenderDistance, minimumRenderDistance),
				startingRenderDistance - renderStage * 2
		);
		return new Stages(particleStage, entityStage, renderStage, renderDistance, visualStage);
	}

	static int initialLevel(ProfilePolicy profile, int maximumLevel) {
		return profile == ProfilePolicy.MAX_PERFORMANCE ? Math.max(0, maximumLevel) : 0;
	}

	static int recoveryFloor(ProfilePolicy profile, int maximumLevel) {
		return profile == ProfilePolicy.MAX_PERFORMANCE
				? Math.max(0, maximumLevel - VISUAL_LEVELS)
				: 0;
	}

	static boolean shouldThrottleAnimations(int maximumLevel, int currentLevel) {
		if (maximumLevel <= 0 || currentLevel <= 0) {
			return false;
		}
		int severePressureThreshold = Math.max(1, maximumLevel - 2);
		return currentLevel >= severePressureThreshold;
	}

	private static int renderSteps(int startingRenderDistance, int minimumRenderDistance) {
		int delta = Math.max(0, startingRenderDistance - minimumRenderDistance);
		return (delta + 1) / 2;
	}

	enum ProfilePolicy {
		BALANCED,
		MAX_QUALITY,
		MAX_PERFORMANCE
	}

	record Stages(
			int particleStage,
			int entityStage,
			int renderStage,
			int renderDistance,
			int visualStage
	) {
	}
}
