package com.ragedriver.sodiumvolt.client.performance;

import com.ragedriver.sodiumvolt.client.config.VoltPerformanceConfig;

/** Reusable frame snapshot owned by the client/render thread. */
final class BerpRuntimeSettings {
	static final BerpRuntimeSettings DISABLED = new BerpRuntimeSettings(
			false, false, false, false, false, false, false, false,
			false, 0, 0, 1, 1, 0, 1, 1, 0
	);

	private boolean active;
	private boolean collectStatistics;
	private boolean prioritizeNearby;
	private boolean recentInteractionGrace;
	private boolean distanceAwareStateUpdates;
	private boolean cacheFarRenderStates;
	private boolean perTypeRenderLimits;
	private boolean cullBeyondFarDistance;
	private boolean includeModdedBlockEntities;
	private int nearDistance;
	private int mediumDistance;
	private int mediumUpdateInterval = 1;
	private int farUpdateInterval = 1;
	private int farRenderDistance;
	private int globalRenderBudget = 1;
	private int perTypeRenderLimit = 1;
	private int cacheCapacity;

	BerpRuntimeSettings() {
	}

	BerpRuntimeSettings(
			boolean active,
			boolean collectStatistics,
			boolean prioritizeNearby,
			boolean recentInteractionGrace,
			boolean distanceAwareStateUpdates,
			boolean cacheFarRenderStates,
			boolean perTypeRenderLimits,
			boolean cullBeyondFarDistance,
			boolean includeModdedBlockEntities,
			int nearDistance,
			int mediumDistance,
			int mediumUpdateInterval,
			int farUpdateInterval,
			int farRenderDistance,
			int globalRenderBudget,
			int perTypeRenderLimit,
			int cacheCapacity
	) {
		this.capture(
				active, collectStatistics, prioritizeNearby, recentInteractionGrace,
				distanceAwareStateUpdates, cacheFarRenderStates, perTypeRenderLimits,
				cullBeyondFarDistance, includeModdedBlockEntities, nearDistance,
				mediumDistance, mediumUpdateInterval, farUpdateInterval,
				farRenderDistance, globalRenderBudget, perTypeRenderLimit, cacheCapacity
		);
	}

	BerpRuntimeSettings capture(VoltPerformanceConfig config, boolean collectStatistics) {
		return this.capture(
				true,
				collectStatistics,
				config.isBerpPrioritizeNearby(),
				config.isBerpRecentInteractionGrace(),
				config.isBerpDistanceAwareStateUpdates(),
				config.isBerpCacheFarRenderStates(),
				config.isBerpPerTypeRenderLimits(),
				config.isBerpCullBeyondFarDistance(),
				config.isBerpIncludeModdedBlockEntities(),
				config.getBerpNearDistance(),
				config.getBerpMediumDistance(),
				config.getBerpMediumUpdateInterval(),
				config.getBerpFarUpdateInterval(),
				config.getBerpFarRenderDistance(),
				config.getBerpGlobalRenderBudget(),
				config.getBerpPerTypeRenderLimit(),
				config.getBerpCacheCapacity()
		);
	}

	BerpRuntimeSettings capture(BerpRuntimeSettings source) {
		return this.capture(
				source.active(), source.collectStatistics(), source.prioritizeNearby(),
				source.recentInteractionGrace(), source.distanceAwareStateUpdates(),
				source.cacheFarRenderStates(), source.perTypeRenderLimits(),
				source.cullBeyondFarDistance(), source.includeModdedBlockEntities(),
				source.nearDistance(), source.mediumDistance(),
				source.mediumUpdateInterval(), source.farUpdateInterval(),
				source.farRenderDistance(), source.globalRenderBudget(),
				source.perTypeRenderLimit(), source.cacheCapacity()
		);
	}

	BerpRuntimeSettings capture(
			boolean active,
			boolean collectStatistics,
			boolean prioritizeNearby,
			boolean recentInteractionGrace,
			boolean distanceAwareStateUpdates,
			boolean cacheFarRenderStates,
			boolean perTypeRenderLimits,
			boolean cullBeyondFarDistance,
			boolean includeModdedBlockEntities,
			int nearDistance,
			int mediumDistance,
			int mediumUpdateInterval,
			int farUpdateInterval,
			int farRenderDistance,
			int globalRenderBudget,
			int perTypeRenderLimit,
			int cacheCapacity
	) {
		this.active = active;
		this.collectStatistics = collectStatistics;
		this.prioritizeNearby = prioritizeNearby;
		this.recentInteractionGrace = recentInteractionGrace;
		this.distanceAwareStateUpdates = distanceAwareStateUpdates;
		this.cacheFarRenderStates = cacheFarRenderStates;
		this.perTypeRenderLimits = perTypeRenderLimits;
		this.cullBeyondFarDistance = cullBeyondFarDistance;
		this.includeModdedBlockEntities = includeModdedBlockEntities;
		this.nearDistance = nearDistance;
		this.mediumDistance = mediumDistance;
		this.mediumUpdateInterval = mediumUpdateInterval;
		this.farUpdateInterval = farUpdateInterval;
		this.farRenderDistance = farRenderDistance;
		this.globalRenderBudget = globalRenderBudget;
		this.perTypeRenderLimit = perTypeRenderLimit;
		this.cacheCapacity = cacheCapacity;
		return this;
	}

	boolean active() {
		return this.active;
	}

	boolean collectStatistics() {
		return this.collectStatistics;
	}

	boolean prioritizeNearby() {
		return this.prioritizeNearby;
	}

	boolean recentInteractionGrace() {
		return this.recentInteractionGrace;
	}

	boolean distanceAwareStateUpdates() {
		return this.distanceAwareStateUpdates;
	}

	boolean cacheFarRenderStates() {
		return this.cacheFarRenderStates;
	}

	boolean perTypeRenderLimits() {
		return this.perTypeRenderLimits;
	}

	boolean cullBeyondFarDistance() {
		return this.cullBeyondFarDistance;
	}

	boolean includeModdedBlockEntities() {
		return this.includeModdedBlockEntities;
	}

	int nearDistance() {
		return this.nearDistance;
	}

	int mediumDistance() {
		return this.mediumDistance;
	}

	int mediumUpdateInterval() {
		return this.mediumUpdateInterval;
	}

	int farUpdateInterval() {
		return this.farUpdateInterval;
	}

	int farRenderDistance() {
		return this.farRenderDistance;
	}

	int globalRenderBudget() {
		return this.globalRenderBudget;
	}

	int perTypeRenderLimit() {
		return this.perTypeRenderLimit;
	}

	int cacheCapacity() {
		return this.cacheCapacity;
	}

	boolean cacheFeaturesEnabled() {
		return BlockEntityCadenceLogic.shouldUseCache(
				this.cacheFarRenderStates,
				this.distanceAwareStateUpdates
		);
	}

	int cacheTtlTicks(int minimum) {
		return Math.max(minimum, this.farUpdateInterval * 3);
	}
}
