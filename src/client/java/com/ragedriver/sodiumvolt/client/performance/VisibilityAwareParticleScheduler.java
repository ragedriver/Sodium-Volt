package com.ragedriver.sodiumvolt.client.performance;

import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.config.VoltPerformanceConfig;
import com.ragedriver.sodiumvolt.client.mixin.ParticleGroupAccessor;
import com.ragedriver.sodiumvolt.client.mixin.ParticlePositionAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.AshParticle;
import net.minecraft.client.particle.AttackSweepParticle;
import net.minecraft.client.particle.BreakingItemParticle;
import net.minecraft.client.particle.CritParticle;
import net.minecraft.client.particle.ElderGuardianParticle;
import net.minecraft.client.particle.FallingDustParticle;
import net.minecraft.client.particle.FallingLeavesParticle;
import net.minecraft.client.particle.FireflyParticle;
import net.minecraft.client.particle.ItemPickupParticle;
import net.minecraft.client.particle.LargeSmokeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.ShriekParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SmokeParticle;
import net.minecraft.client.particle.SnowflakeParticle;
import net.minecraft.client.particle.SonicBoomParticle;
import net.minecraft.client.particle.SuspendedParticle;
import net.minecraft.client.particle.SuspendedTownParticle;
import net.minecraft.client.particle.TotemParticle;
import net.minecraft.client.particle.TrialSpawnerDetectionParticle;
import net.minecraft.client.particle.VibrationSignalParticle;
import net.minecraft.client.particle.WhiteAshParticle;
import net.minecraft.client.particle.WhiteSmokeParticle;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Client-side particle render and simulation scheduler.
 *
 * <p>Render controls only suppress extraction for this frame; they never remove
 * particle objects. Distance-aware simulation runs custom particle logic less
 * often while advancing the vanilla base age every client tick, freezing
 * interpolation on age-only ticks, and expiring at the same lifetime boundary.
 */
public final class VisibilityAwareParticleScheduler {
	private static final int MAXIMUM_PARTICLES_SCANNED = 32_768;
	private static final int PARTICLE_DECISION_TABLE_SIZE = 65_536;
	private static final int AMBIENT_CELL_SIZE = 4;
	private static final int MAXIMUM_STAT_TYPES = 64;
	private static final int MAXIMUM_INSPECTOR_TYPE_LINES = 3;
	private static final VoltPerformanceConfig CONFIG = VoltPerformanceConfig.getInstance();
	private static final Stats STATS = new Stats();
	private static FrameState frameState;
	private static boolean renderFailureLogged;
	private static boolean simulationFailureLogged;
	private static boolean schedulerActive;
	private static int criticalFullRateTicks;

	private VisibilityAwareParticleScheduler() {
	}

	public static boolean isEnabled() {
		return CONFIG.isVisibilityAwareParticleSchedulerEnabled();
	}

	public static void beginParticleExtraction(
			Map<ParticleRenderType, ParticleGroup<?>> particleGroups,
			Frustum frustum,
			Camera camera
	) {
		beginParticleExtraction(particleGroups, frustum, camera, false);
	}

	public static void beginParticleExtraction(
			Map<ParticleRenderType, ParticleGroup<?>> particleGroups,
			Frustum frustum,
			Camera camera,
			boolean prepareVoltGuardHandoff
	) {
		if (!isEnabled()) {
			disableIfNeeded();
			return;
		}

		schedulerActive = true;
		FrameState frame = frameState();
		try {
			frame.begin(
					prepareVoltGuardHandoff,
					particleCountFor(particleGroups, ParticleRenderType.SINGLE_QUADS),
					particleCountFor(particleGroups, ParticleRenderType.ITEM_PICKUP)
			);
			Vec3 cameraPosition = camera.position();
			Vector3fc forward = camera.forwardVector();
			boolean prioritizeInFrustum = CONFIG.isVapsPrioritizeInFrustum();
			boolean skipBehind = CONFIG.isVapsSkipBehindCamera();
			boolean preserveCritical = CONFIG.isVapsPreserveCriticalParticles();
			boolean coalesceAmbient = CONFIG.isVapsCoalesceAmbientParticles();
			boolean limitPerType = CONFIG.isVapsPerTypeRenderLimits();
			int criticalReserve = CONFIG.getVapsCriticalReserve();
			int perTypeLimit = CONFIG.getVapsPerTypeRenderLimit();
			int ambientLimit = CONFIG.getVapsAmbientPerCell();

			particleGroups:
			for (Map.Entry<ParticleRenderType, ParticleGroup<?>> entry : particleGroups.entrySet()) {
				ParticleRenderType renderType = entry.getKey();
				ParticleGroup<?> group = entry.getValue();
				Queue<Particle> particles = ((ParticleGroupAccessor) group).sodiumVolt$getParticles();
				int sourceIndex = 0;
				for (Particle particle : particles) {
					int particleSourceIndex = sourceIndex++;
					if (frame.scannedCount >= MAXIMUM_PARTICLES_SCANNED) {
						frame.truncated = true;
						break particleGroups;
					}
					frame.scannedCount++;
					if (!frame.addScanned(particle)) {
						frame.truncated = true;
						STATS.markSaturated();
						break particleGroups;
					}
					ParticlePositionAccessor position = (ParticlePositionAccessor) particle;
					double x = position.sodiumVolt$getX();
					double y = position.sodiumVolt$getY();
					double z = position.sodiumVolt$getZ();
					boolean visible = !(particle instanceof SingleQuadParticle)
							|| frustum.pointInFrustum(x, y, z);
					if (prioritizeInFrustum && !visible) {
						// Minecraft already omits this quad. Keeping it out of our
						// quotas reserves capacity for particles that can render.
						continue;
					}

					boolean critical = visible && preserveCritical && isCriticalParticle(particle);
					if (critical && frame.limiter.tryCritical(criticalReserve)) {
						selectParticleForFrame(
								frame,
								particle,
								renderType,
								visible,
								particleSourceIndex
						);
						continue;
					}
					if (critical) {
						STATS.addCriticalOverflow();
					}

					double xDistance = x - cameraPosition.x;
					double yDistance = y - cameraPosition.y;
					double zDistance = z - cameraPosition.z;
					if (skipBehind && isFinite(xDistance, yDistance, zDistance)
							&& xDistance * forward.x()
							+ yDistance * forward.y()
							+ zDistance * forward.z() < 0.0D) {
						if (visible) {
							STATS.addBehind(particle.getClass());
						}
						continue;
					}

					if (limitPerType && !frame.limiter.tryType(particle.getClass(), perTypeLimit)) {
						if (visible) {
							STATS.addPerType(particle.getClass());
						}
						continue;
					}
					if (coalesceAmbient && isAmbientParticle(particle) && isFinite(x, y, z)) {
						int cellX = cellCoordinate(x);
						int cellY = cellCoordinate(y);
						int cellZ = cellCoordinate(z);
						if (!frame.limiter.tryAmbientCell(
								particle.getClass(),
								cellX,
								cellY,
								cellZ,
								ambientLimit
						)) {
							if (visible) {
								STATS.addCoalesced(particle.getClass());
							}
							continue;
						}
					}
					selectParticleForFrame(
							frame,
							particle,
							renderType,
							visible,
							particleSourceIndex
					);
				}
			}
			if (frame.truncated) {
				STATS.addTruncatedFrame();
			}
			if (frame.limiter.isSaturated()) {
				frame.saturated = true;
				STATS.markSaturated();
			}
			if (frame.truncated) {
				frame.abortGuardHandoff();
			} else {
				frame.completeGuardHandoff(frame.scannedCount);
			}
		} catch (RuntimeException | LinkageError exception) {
			frame.disable();
			if (!renderFailureLogged) {
				renderFailureLogged = true;
				SodiumVolt.LOGGER.warn("VAPS render scheduling failed open; vanilla extraction will continue", exception);
			}
		}
	}

	public static ParticleEligibilityHandoff<Particle> particleEligibilityHandoff() {
		FrameState frame = frameState;
		if (frame == null || !frame.active || frame.guardHandoff == null
				|| !frame.guardHandoff.isComplete()) {
			return null;
		}
		return frame.guardHandoff;
	}

	public static boolean shouldRenderParticle(Particle particle) {
		if (!isEnabled()) {
			return true;
		}
		FrameState frame = frameState;
		return frame == null
				|| !frame.active
				|| !frame.decisions.isScanned(particle)
				|| frame.decisions.isSelected(particle);
	}

	public static void endParticleExtraction() {
		if (frameState != null) {
			frameState.end();
		}
	}

	public static void tickParticle(Particle particle) {
		if (!isEnabled() || !CONFIG.isVapsDistanceAwareSimulation()) {
			particle.tick();
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null
				|| minecraft.player == null
				|| minecraft.isPaused()
				|| minecraft.gui.screen() != null
				|| minecraft.gui.overlay() != null) {
			particle.tick();
			return;
		}

		ParticlePositionAccessor position;
		VapsDecisionLogic.AgeStep step;
		double x;
		double y;
		double z;
		try {
			position = (ParticlePositionAccessor) particle;
			double xDistance = position.sodiumVolt$getX() - minecraft.player.getX();
			double yDistance = position.sodiumVolt$getY() - minecraft.player.getY();
			double zDistance = position.sodiumVolt$getZ() - minecraft.player.getZ();
			double distanceSquared = xDistance * xDistance + yDistance * yDistance + zDistance * zDistance;
			int fullRateDistance = CONFIG.getVapsFullRateDistance();
			double fullRateDistanceSquared = (double) fullRateDistance * fullRateDistance;
			boolean critical = CONFIG.isVapsPreserveCriticalParticles()
					&& isCriticalParticle(particle)
					&& Double.isFinite(distanceSquared)
					&& distanceSquared > fullRateDistanceSquared
					&& claimCriticalFullRateSlot();
			if (VapsDecisionLogic.shouldRunFullTick(
					true,
					critical,
					distanceSquared,
					fullRateDistanceSquared,
					CONFIG.getVapsFarTickInterval(),
					position.sodiumVolt$getAge()
			)) {
				particle.tick();
				return;
			}

			step = VapsDecisionLogic.ageOnlyStep(
					position.sodiumVolt$getAge(),
					position.sodiumVolt$getLifetime()
			);
			x = position.sodiumVolt$getX();
			y = position.sodiumVolt$getY();
			z = position.sodiumVolt$getZ();
		} catch (RuntimeException | LinkageError exception) {
			if (!simulationFailureLogged) {
				simulationFailureLogged = true;
				SodiumVolt.LOGGER.warn("VAPS simulation scheduling failed open; vanilla ticking will continue", exception);
			}
			particle.tick();
			return;
		}

		// From this point forward, never call Particle.tick() as a fallback: doing
		// so after a partial write could advance age twice in one client tick.
		try {
			position.sodiumVolt$setPreviousX(x);
			position.sodiumVolt$setPreviousY(y);
			position.sodiumVolt$setPreviousZ(z);
			position.sodiumVolt$setAge(step.age());
			if (step.expires()) {
				particle.remove();
			}
			STATS.addSimulationSkip(particle.getClass());
		} catch (RuntimeException | LinkageError exception) {
			if (!simulationFailureLogged) {
				simulationFailureLogged = true;
				SodiumVolt.LOGGER.warn(
						"VAPS could not finish an age-only tick; skipping fallback to avoid double advancement",
						exception
				);
			}
		}
	}

	public static StatisticsSnapshot snapshotStatistics() {
		if (!isEnabled() || !CONFIG.isVapsShowInspectorStatistics()) {
			return StatisticsSnapshot.EMPTY;
		}
		return STATS.snapshot(MAXIMUM_INSPECTOR_TYPE_LINES);
	}

	public static void beginSimulationTick() {
		if (isEnabled()) {
			schedulerActive = true;
			criticalFullRateTicks = 0;
		} else {
			disableIfNeeded();
		}
	}

	public static void onLevelChanged() {
		if (frameState != null) {
			frameState.disable();
		}
		schedulerActive = false;
		criticalFullRateTicks = 0;
		STATS.reset();
	}

	private static void disableIfNeeded() {
		if (!schedulerActive) {
			return;
		}
		schedulerActive = false;
		criticalFullRateTicks = 0;
		if (frameState != null) {
			frameState.disable();
		}
	}

	private static FrameState frameState() {
		if (frameState == null) {
			frameState = new FrameState();
		}
		return frameState;
	}

	private static boolean claimCriticalFullRateSlot() {
		if (criticalFullRateTicks >= CONFIG.getVapsCriticalReserve()) {
			return false;
		}
		criticalFullRateTicks++;
		return true;
	}

	private static int particleCountFor(
			Map<ParticleRenderType, ParticleGroup<?>> particleGroups,
			ParticleRenderType renderType
	) {
		ParticleGroup<?> group = particleGroups.get(renderType);
		return group == null
				? 0
				: ((ParticleGroupAccessor) group).sodiumVolt$getParticles().size();
	}

	private static void selectParticleForFrame(
			FrameState frame,
			Particle particle,
			ParticleRenderType renderType,
			boolean visible,
			int sourceIndex
	) {
		frame.decisions.select(particle);
		if (frame.guardHandoff == null || !frame.guardHandoff.isRecording()) {
			return;
		}
		if (renderType == ParticleRenderType.SINGLE_QUADS) {
			if (visible && particle instanceof SingleQuadParticle) {
				frame.guardHandoff.add(particle, false, sourceIndex);
			}
		} else if (renderType == ParticleRenderType.ITEM_PICKUP) {
			frame.guardHandoff.add(particle, true, frame.quadSourceCount + sourceIndex);
		} else if (renderType == ParticleRenderType.ELDER_GUARDIANS) {
			frame.guardHandoff.add(
					particle,
					true,
					frame.quadSourceCount + frame.itemPickupSourceCount + sourceIndex
			);
		}
	}

	private static boolean isCriticalParticle(Particle particle) {
		return particle instanceof AttackSweepParticle
				|| particle instanceof ItemPickupParticle
				|| particle instanceof ElderGuardianParticle
				|| particle instanceof TotemParticle
				|| particle instanceof SonicBoomParticle
				|| particle instanceof CritParticle
				|| particle instanceof BreakingItemParticle
				|| particle instanceof VibrationSignalParticle
				|| particle instanceof TrialSpawnerDetectionParticle
				|| particle instanceof ShriekParticle;
	}

	private static boolean isAmbientParticle(Particle particle) {
		return particle instanceof AshParticle
				|| particle instanceof SmokeParticle
				|| particle instanceof LargeSmokeParticle
				|| particle instanceof WhiteSmokeParticle
				|| particle instanceof WhiteAshParticle
				|| particle instanceof FireflyParticle
				|| particle instanceof SuspendedParticle
				|| particle instanceof SuspendedTownParticle
				|| particle instanceof FallingLeavesParticle
				|| particle instanceof FallingDustParticle
				|| particle instanceof SnowflakeParticle;
	}

	private static boolean isFinite(double x, double y, double z) {
		return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
	}

	private static int cellCoordinate(double value) {
		if (!Double.isFinite(value)) {
			return 0;
		}
		double cell = Math.floor(value / AMBIENT_CELL_SIZE);
		return (int) Math.clamp(cell, Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	private static String particleLabel(Class<?> particleClass) {
		String name = particleClass.getSimpleName();
		if (name.isEmpty()) {
			name = particleClass.getName();
			int separator = name.lastIndexOf('.');
			if (separator >= 0) {
				name = name.substring(separator + 1);
			}
		}
		StringBuilder sanitized = new StringBuilder(Math.min(40, name.length()));
		for (int index = 0; index < name.length() && sanitized.length() < 40; index++) {
			char character = name.charAt(index);
			if (!Character.isISOControl(character)) {
				sanitized.append(character);
			}
		}
		return sanitized.isEmpty() ? "unknown" : sanitized.toString();
	}

	private static final class FrameState {
		private final VapsIdentityDecisionTable<Particle> decisions =
				new VapsIdentityDecisionTable<>(PARTICLE_DECISION_TABLE_SIZE);
		private final VapsFrameLimiter limiter = new VapsFrameLimiter();
		private boolean active;
		private boolean truncated;
		private boolean saturated;
		private int scannedCount;
		private ParticleEligibilityHandoff<Particle> guardHandoff;
		private int quadSourceCount;
		private int itemPickupSourceCount;

		private void begin(
				boolean prepareGuardHandoff,
				int quadSourceCount,
				int itemPickupSourceCount
		) {
			this.decisions.nextFrame();
			this.limiter.reset();
			this.active = true;
			this.truncated = false;
			this.saturated = false;
			this.scannedCount = 0;
			this.quadSourceCount = quadSourceCount;
			this.itemPickupSourceCount = itemPickupSourceCount;
			if (prepareGuardHandoff) {
				if (this.guardHandoff == null) {
					this.guardHandoff = new ParticleEligibilityHandoff<>(MAXIMUM_PARTICLES_SCANNED);
				}
				this.guardHandoff.begin();
			} else if (this.guardHandoff != null) {
				this.guardHandoff.abort();
			}
		}

		private void completeGuardHandoff(int rawSourceVisits) {
			if (this.guardHandoff != null) {
				this.guardHandoff.complete(rawSourceVisits);
			}
		}

		private boolean addScanned(Particle particle) {
			if (this.guardHandoff == null || !this.guardHandoff.isRecording()) {
				return this.decisions.addScanned(particle);
			}
			VapsIdentityDecisionTable.AddResult result = this.decisions.addScannedResult(particle);
			if (result == VapsIdentityDecisionTable.AddResult.EXISTING) {
				// A later occurrence can retroactively select an earlier one.
				// Preserve exact identity semantics through Guard's raw fallback.
				this.abortGuardHandoff();
			}
			return result != VapsIdentityDecisionTable.AddResult.SATURATED;
		}

		private void abortGuardHandoff() {
			if (this.guardHandoff != null) {
				this.guardHandoff.abort();
			}
		}

		private void end() {
			this.active = false;
			this.decisions.releaseFrame();
			this.abortGuardHandoff();
		}

		private void disable() {
			this.active = false;
			this.decisions.clear();
			this.scannedCount = 0;
			this.truncated = false;
			this.saturated = false;
			this.quadSourceCount = 0;
			this.itemPickupSourceCount = 0;
			this.abortGuardHandoff();
		}
	}

	private static final class Stats {
		private final Class<?>[] types = new Class<?>[MAXIMUM_STAT_TYPES];
		private final long[] limitedByType = new long[MAXIMUM_STAT_TYPES];
		private final long[] simulationSkipsByType = new long[MAXIMUM_STAT_TYPES];
		private long behind;
		private long coalesced;
		private long perType;
		private long criticalOverflow;
		private long simulationSkips;
		private long truncatedFrames;
		private boolean saturated;

		private void addBehind(Class<?> type) {
			this.behind = VapsDecisionLogic.saturatingAdd(this.behind, 1L);
			addLimitedType(type);
		}

		private void addCoalesced(Class<?> type) {
			this.coalesced = VapsDecisionLogic.saturatingAdd(this.coalesced, 1L);
			addLimitedType(type);
		}

		private void addPerType(Class<?> type) {
			this.perType = VapsDecisionLogic.saturatingAdd(this.perType, 1L);
			addLimitedType(type);
		}

		private void addCriticalOverflow() {
			this.criticalOverflow = VapsDecisionLogic.saturatingAdd(this.criticalOverflow, 1L);
		}

		private void addSimulationSkip(Class<?> type) {
			this.simulationSkips = VapsDecisionLogic.saturatingAdd(this.simulationSkips, 1L);
			int index = typeIndex(type);
			if (index >= 0) {
				this.simulationSkipsByType[index] =
						VapsDecisionLogic.saturatingAdd(this.simulationSkipsByType[index], 1L);
			}
		}

		private void addLimitedType(Class<?> type) {
			int index = typeIndex(type);
			if (index >= 0) {
				this.limitedByType[index] =
						VapsDecisionLogic.saturatingAdd(this.limitedByType[index], 1L);
			}
		}

		private int typeIndex(Class<?> type) {
			int firstEmpty = -1;
			for (int index = 0; index < this.types.length; index++) {
				if (this.types[index] == type) {
					return index;
				}
				if (firstEmpty < 0 && this.types[index] == null) {
					firstEmpty = index;
				}
			}
			if (firstEmpty >= 0) {
				this.types[firstEmpty] = type;
				return firstEmpty;
			}
			this.saturated = true;
			return -1;
		}

		private void markSaturated() {
			this.saturated = true;
		}

		private void addTruncatedFrame() {
			this.truncatedFrames = VapsDecisionLogic.saturatingAdd(this.truncatedFrames, 1L);
		}

		private StatisticsSnapshot snapshot(int maximumTypes) {
			ArrayList<TypeStatistic> top = new ArrayList<>(maximumTypes);
			boolean[] selected = new boolean[MAXIMUM_STAT_TYPES];
			for (int output = 0; output < maximumTypes; output++) {
				int best = -1;
				long bestTotal = 0L;
				for (int index = 0; index < this.types.length; index++) {
					long total = VapsDecisionLogic.saturatingAdd(
							this.limitedByType[index],
							this.simulationSkipsByType[index]
					);
					if (!selected[index] && this.types[index] != null && total > bestTotal) {
						best = index;
						bestTotal = total;
					}
				}
				if (best < 0) {
					break;
				}
				selected[best] = true;
				top.add(new TypeStatistic(
						particleLabel(this.types[best]),
						this.limitedByType[best],
						this.simulationSkipsByType[best]
				));
			}
			long renderLimited = VapsDecisionLogic.saturatingAdd(
					VapsDecisionLogic.saturatingAdd(this.behind, this.coalesced),
					this.perType
			);
			return new StatisticsSnapshot(
					renderLimited,
					this.simulationSkips,
					this.behind,
					this.coalesced,
					this.perType,
					this.criticalOverflow,
					this.truncatedFrames,
					this.saturated,
					List.copyOf(top)
			);
		}

		private void reset() {
			for (int index = 0; index < this.types.length; index++) {
				this.types[index] = null;
				this.limitedByType[index] = 0L;
				this.simulationSkipsByType[index] = 0L;
			}
			this.behind = 0L;
			this.coalesced = 0L;
			this.perType = 0L;
			this.criticalOverflow = 0L;
			this.simulationSkips = 0L;
			this.truncatedFrames = 0L;
			this.saturated = false;
		}
	}

	public record StatisticsSnapshot(
			long renderLimited,
			long simulationSkips,
			long behind,
			long coalesced,
			long perType,
			long criticalOverflow,
			long truncatedFrames,
			boolean saturated,
			List<TypeStatistic> topTypes
	) {
		public static final StatisticsSnapshot EMPTY =
				new StatisticsSnapshot(0L, 0L, 0L, 0L, 0L, 0L, 0L, false, List.of());
	}

	public record TypeStatistic(String label, long renderLimited, long simulationSkips) {
	}
}
