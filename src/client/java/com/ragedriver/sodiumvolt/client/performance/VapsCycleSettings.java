package com.ragedriver.sodiumvolt.client.performance;

/** Mutable cycle snapshots reused under VAPS' client/render-thread ownership. */
final class VapsCycleSettings {
	private VapsCycleSettings() {
	}

	static final class Simulation {
		private boolean active;
		private boolean worldEligible;
		private boolean collectStatistics;
		private boolean preserveCritical;
		private double playerX;
		private double playerY;
		private double playerZ;
		private double fullRateDistanceSquared;
		private int farTickInterval = 1;
		private int criticalReserve;

		Simulation() {
		}

		Simulation(
				boolean active,
				boolean worldEligible,
				boolean collectStatistics,
				boolean preserveCritical,
				double playerX,
				double playerY,
				double playerZ,
				double fullRateDistanceSquared,
				int farTickInterval,
				int criticalReserve
		) {
			this.capture(
					active, worldEligible, collectStatistics, preserveCritical,
					playerX, playerY, playerZ, fullRateDistanceSquared,
					farTickInterval, criticalReserve
			);
		}

		Simulation capture(
				boolean active,
				boolean worldEligible,
				boolean collectStatistics,
				boolean preserveCritical,
				double playerX,
				double playerY,
				double playerZ,
				double fullRateDistanceSquared,
				int farTickInterval,
				int criticalReserve
		) {
			this.active = active;
			this.worldEligible = worldEligible;
			this.collectStatistics = collectStatistics;
			this.preserveCritical = preserveCritical;
			this.playerX = playerX;
			this.playerY = playerY;
			this.playerZ = playerZ;
			this.fullRateDistanceSquared = fullRateDistanceSquared;
			this.farTickInterval = farTickInterval;
			this.criticalReserve = criticalReserve;
			return this;
		}

		void disable() {
			this.active = false;
			this.worldEligible = false;
			this.collectStatistics = false;
		}

		boolean active() {
			return this.active;
		}

		boolean worldEligible() {
			return this.worldEligible;
		}

		boolean collectStatistics() {
			return this.collectStatistics;
		}

		boolean preserveCritical() {
			return this.preserveCritical;
		}

		double playerX() {
			return this.playerX;
		}

		double playerY() {
			return this.playerY;
		}

		double playerZ() {
			return this.playerZ;
		}

		double fullRateDistanceSquared() {
			return this.fullRateDistanceSquared;
		}

		int farTickInterval() {
			return this.farTickInterval;
		}

		int criticalReserve() {
			return this.criticalReserve;
		}
	}

	static final class Render {
		private boolean active;
		private boolean collectStatistics;
		private boolean prioritizeInFrustum;
		private boolean skipBehindCamera;
		private boolean preserveCritical;
		private boolean coalesceAmbient;
		private boolean perTypeLimits;
		private int criticalReserve;
		private int perTypeLimit;
		private int ambientLimit;

		Render capture(
				boolean active,
				boolean collectStatistics,
				boolean prioritizeInFrustum,
				boolean skipBehindCamera,
				boolean preserveCritical,
				boolean coalesceAmbient,
				boolean perTypeLimits,
				int criticalReserve,
				int perTypeLimit,
				int ambientLimit
		) {
			this.active = active;
			this.collectStatistics = collectStatistics;
			this.prioritizeInFrustum = prioritizeInFrustum;
			this.skipBehindCamera = skipBehindCamera;
			this.preserveCritical = preserveCritical;
			this.coalesceAmbient = coalesceAmbient;
			this.perTypeLimits = perTypeLimits;
			this.criticalReserve = criticalReserve;
			this.perTypeLimit = perTypeLimit;
			this.ambientLimit = ambientLimit;
			return this;
		}

		void disable() {
			this.active = false;
			this.collectStatistics = false;
		}

		boolean active() {
			return this.active;
		}

		boolean collectStatistics() {
			return this.collectStatistics;
		}

		boolean prioritizeInFrustum() {
			return this.prioritizeInFrustum;
		}

		boolean skipBehindCamera() {
			return this.skipBehindCamera;
		}

		boolean preserveCritical() {
			return this.preserveCritical;
		}

		boolean coalesceAmbient() {
			return this.coalesceAmbient;
		}

		boolean perTypeLimits() {
			return this.perTypeLimits;
		}

		int criticalReserve() {
			return this.criticalReserve;
		}

		int perTypeLimit() {
			return this.perTypeLimit;
		}

		int ambientLimit() {
			return this.ambientLimit;
		}
	}
}
