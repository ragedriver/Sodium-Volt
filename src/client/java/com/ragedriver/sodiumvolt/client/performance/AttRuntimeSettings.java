package com.ragedriver.sodiumvolt.client.performance;

import net.minecraft.resources.Identifier;

final class AttRuntimeSettings {
	private AttRuntimeSettings() {
	}

	/** One independently reusable snapshot for each supported nesting depth. */
	static final class AtlasCycle {
		private Identifier atlasLocation;
		private boolean warmup;
		private boolean collectStatistics;
		private boolean keepInterfaceAtlasesFullSpeed;
		private boolean interfaceProtected;
		private boolean screenOpen;
		private boolean pauseInvisibleAnimations;
		private int unseenKeepaliveTicks;
		private boolean distanceAwareCadence;
		private double fullSpeedDistanceSquared;
		private int distantUpdateInterval = 1;
		private boolean immediateSmoothResume;
		private boolean apcThrottleAnimations;
		private boolean exemptCriticalTextures;
		private boolean honorExemptionLists;
		private AttIdentifierLookup exemptionLookup;

		AtlasCycle capture(
				Identifier atlasLocation,
				boolean warmup,
				boolean collectStatistics,
				boolean keepInterfaceAtlasesFullSpeed,
				boolean screenOpen,
				boolean pauseInvisibleAnimations,
				int unseenKeepaliveTicks,
				boolean distanceAwareCadence,
				double fullSpeedDistanceSquared,
				int distantUpdateInterval,
				boolean immediateSmoothResume,
				boolean apcThrottleAnimations,
				boolean exemptCriticalTextures,
				boolean honorExemptionLists,
				AttIdentifierLookup exemptionLookup
		) {
			this.atlasLocation = atlasLocation;
			this.warmup = warmup;
			this.collectStatistics = collectStatistics;
			this.keepInterfaceAtlasesFullSpeed = keepInterfaceAtlasesFullSpeed;
			this.interfaceProtected = keepInterfaceAtlasesFullSpeed
					&& !AttAtlasPolicy.isWorldAtlasTexture(atlasLocation);
			this.screenOpen = screenOpen;
			this.pauseInvisibleAnimations = pauseInvisibleAnimations;
			this.unseenKeepaliveTicks = unseenKeepaliveTicks;
			this.distanceAwareCadence = distanceAwareCadence;
			this.fullSpeedDistanceSquared = fullSpeedDistanceSquared;
			this.distantUpdateInterval = distantUpdateInterval;
			this.immediateSmoothResume = immediateSmoothResume;
			this.apcThrottleAnimations = apcThrottleAnimations;
			this.exemptCriticalTextures = exemptCriticalTextures;
			this.honorExemptionLists = honorExemptionLists;
			this.exemptionLookup = exemptionLookup;
			return this;
		}

		Identifier atlasLocation() {
			return this.atlasLocation;
		}

		boolean warmup() {
			return this.warmup;
		}

		boolean collectStatistics() {
			return this.collectStatistics;
		}

		boolean keepInterfaceAtlasesFullSpeed() {
			return this.keepInterfaceAtlasesFullSpeed;
		}

		boolean interfaceProtected() {
			return this.interfaceProtected;
		}

		boolean screenOpen() {
			return this.screenOpen;
		}

		boolean pauseInvisibleAnimations() {
			return this.pauseInvisibleAnimations;
		}

		int unseenKeepaliveTicks() {
			return this.unseenKeepaliveTicks;
		}

		boolean distanceAwareCadence() {
			return this.distanceAwareCadence;
		}

		double fullSpeedDistanceSquared() {
			return this.fullSpeedDistanceSquared;
		}

		int distantUpdateInterval() {
			return this.distantUpdateInterval;
		}

		boolean immediateSmoothResume() {
			return this.immediateSmoothResume;
		}

		boolean apcThrottleAnimations() {
			return this.apcThrottleAnimations;
		}

		boolean exemptCriticalTextures() {
			return this.exemptCriticalTextures;
		}

		boolean honorExemptionLists() {
			return this.honorExemptionLists;
		}

		AttIdentifierLookup exemptionLookup() {
			return this.exemptionLookup;
		}

		void releaseReferences() {
			this.atlasLocation = null;
			this.exemptionLookup = null;
		}
	}
}
