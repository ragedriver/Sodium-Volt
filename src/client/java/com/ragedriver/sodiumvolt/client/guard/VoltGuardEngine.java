package com.ragedriver.sodiumvolt.client.guard;

import com.ragedriver.sodiumvolt.client.config.VoltGuardConfig;
import com.ragedriver.sodiumvolt.client.mixin.ParticleGroupAccessor;
import com.ragedriver.sodiumvolt.client.mixin.ParticlePositionAccessor;
import com.ragedriver.sodiumvolt.client.performance.VisibilityAwareParticleScheduler;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class VoltGuardEngine {
	private static final double CRITICAL_EFFECT_DISTANCE_SQUARED = 8.0D * 8.0D;
	private static final double TARGET_MATCH_DISTANCE_SQUARED = 4.0D;
	private static final long NOTIFICATION_INTERVAL_NANOS = 10_000_000_000L;
	private static final int MATERIAL_PARTICLE_REDUCTION = 32;
	private static final int MATERIAL_ENTITY_REDUCTION = 4;
	private static final Queue<Particle> EMPTY_PARTICLE_QUEUE = new ArrayDeque<>(0);

	private static final VoltGuardConfig CONFIG = VoltGuardConfig.getInstance();
	private static final AdaptiveBudgetController ADAPTIVE_BUDGET = new AdaptiveBudgetController();
	private static final ThreadLocal<FrameState> FRAME_STATE = ThreadLocal.withInitial(FrameState::new);
	private static final AtomicLong LAST_NOTIFICATION_NANOS = new AtomicLong();

	private VoltGuardEngine() {
	}

	public static void register() {
		LevelExtractionEvents.END_EXTRACTION.register(VoltGuardEngine::finishLevelExtraction);
	}

	public static void beginParticleExtraction(
			Map<ParticleRenderType, ParticleGroup<?>> particleGroups,
			Frustum frustum,
			Camera camera
	) {
		FrameState frame = FRAME_STATE.get();
		if (!CONFIG.isVoltGuardEnabled()) {
			frame.disable();
			ADAPTIVE_BUDGET.disable();
			return;
		}

		double scale = ADAPTIVE_BUDGET.update(
				System.nanoTime(),
				CONFIG.getTargetFps(),
				CONFIG.isAdaptiveWorkloadControl()
		);
		frame.configure(
				effectiveBudget(CONFIG.getParticleRenderBudget(), scale),
				effectiveBudget(CONFIG.getBlockEntityRenderBudget(), scale),
				effectiveBudget(CONFIG.getDisplayEntityRenderBudget(), scale),
				CONFIG.isPrioritizeVisibleEffects(),
				CONFIG.isPreserveGameplayCriticalEffects()
		);
		selectParticles(frame, particleGroups, frustum, camera.position());
	}

	public static void endParticleExtraction() {
		FRAME_STATE.get().endParticleExtraction();
	}

	public static boolean isEnabled() {
		return CONFIG.isVoltGuardEnabled();
	}

	public static boolean shouldExtractParticle(Particle particle) {
		if (!CONFIG.isVoltGuardEnabled()) {
			return true;
		}

		FrameState frame = FRAME_STATE.get();
		return !frame.particleExtractionActive
				|| frame.allParticlesSelected
				|| frame.selectedParticles.contains(particle);
	}

	private static void selectParticles(
			FrameState frame,
			Map<ParticleRenderType, ParticleGroup<?>> particleGroups,
			Frustum frustum,
			Vec3 cameraPosition
	) {
		Queue<Particle> quadParticles = particlesFor(particleGroups, ParticleRenderType.SINGLE_QUADS);
		Queue<Particle> itemPickupParticles = particlesFor(particleGroups, ParticleRenderType.ITEM_PICKUP);
		Queue<Particle> elderGuardianParticles = particlesFor(
				particleGroups,
				ParticleRenderType.ELDER_GUARDIANS
		);

		int visibleQuadCount = 0;
		for (Particle particle : quadParticles) {
			if (particle instanceof SingleQuadParticle
					&& isVisible(particle, frustum)
					&& VisibilityAwareParticleScheduler.shouldRenderParticle(particle)) {
				visibleQuadCount++;
			}
		}
		int specialCount = 0;
		for (Particle particle : itemPickupParticles) {
			if (VisibilityAwareParticleScheduler.shouldRenderParticle(particle)) {
				specialCount = saturatingAdd(specialCount, 1);
			}
		}
		for (Particle particle : elderGuardianParticles) {
			if (VisibilityAwareParticleScheduler.shouldRenderParticle(particle)) {
				specialCount = saturatingAdd(specialCount, 1);
			}
		}
		int eligibleCount = saturatingAdd(visibleQuadCount, specialCount);
		if (eligibleCount <= frame.particleBudget) {
			frame.allParticlesSelected = true;
			frame.suppressedParticles = 0;
			return;
		}

		ParticleBudgetPlan plan = ParticleBudgetPlan.create(
				frame.particleBudget,
				specialCount,
				frame.preserveCritical
		);
		Set<Particle> selected = Collections.newSetFromMap(new IdentityHashMap<>(frame.particleBudget));
		if (plan.specialReserveCapacity() > 0) {
			BoundedTopK<Particle> specialReserve = new BoundedTopK<>(
					plan.specialReserveCapacity(),
					frame.prioritizeNearby
			);
			int originalIndex = quadParticles.size();
			for (Particle particle : itemPickupParticles) {
				if (VisibilityAwareParticleScheduler.shouldRenderParticle(particle)) {
					specialReserve.offer(
							particle,
							false,
							true,
							particleDistanceSquared(particle, cameraPosition),
							originalIndex
					);
				}
				originalIndex++;
			}
			for (Particle particle : elderGuardianParticles) {
				if (VisibilityAwareParticleScheduler.shouldRenderParticle(particle)) {
					specialReserve.offer(
							particle,
							false,
							true,
							particleDistanceSquared(particle, cameraPosition),
							originalIndex
					);
				}
				originalIndex++;
			}
			specialReserve.addTo(selected);
		}

		int remainingCapacity = plan.remainingCapacityAfter(selected.size());
		BoundedTopK<Particle> generalSelection = new BoundedTopK<>(
				remainingCapacity,
				frame.prioritizeNearby
		);
		int originalIndex = 0;
		for (Particle particle : quadParticles) {
			if (particle instanceof SingleQuadParticle
					&& isVisible(particle, frustum)
					&& VisibilityAwareParticleScheduler.shouldRenderParticle(particle)) {
				double distanceSquared = particleDistanceSquared(particle, cameraPosition);
				generalSelection.offer(
						particle,
						false,
						frame.preserveCritical && distanceSquared <= CRITICAL_EFFECT_DISTANCE_SQUARED,
						distanceSquared,
						originalIndex
				);
			}
			originalIndex++;
		}
		for (Particle particle : itemPickupParticles) {
			if (!selected.contains(particle)
					&& VisibilityAwareParticleScheduler.shouldRenderParticle(particle)) {
				double distanceSquared = particleDistanceSquared(particle, cameraPosition);
				generalSelection.offer(
						particle,
						false,
						frame.preserveCritical && distanceSquared <= CRITICAL_EFFECT_DISTANCE_SQUARED,
						distanceSquared,
						originalIndex
				);
			}
			originalIndex++;
		}
		for (Particle particle : elderGuardianParticles) {
			if (!selected.contains(particle)
					&& VisibilityAwareParticleScheduler.shouldRenderParticle(particle)) {
				double distanceSquared = particleDistanceSquared(particle, cameraPosition);
				generalSelection.offer(
						particle,
						false,
						frame.preserveCritical && distanceSquared <= CRITICAL_EFFECT_DISTANCE_SQUARED,
						distanceSquared,
						originalIndex
				);
			}
			originalIndex++;
		}
		generalSelection.addTo(selected);

		frame.allParticlesSelected = false;
		frame.selectedParticles = selected;
		frame.suppressedParticles = Math.max(0, eligibleCount - selected.size());
	}

	private static Queue<Particle> particlesFor(
			Map<ParticleRenderType, ParticleGroup<?>> particleGroups,
			ParticleRenderType renderType
	) {
		ParticleGroup<?> group = particleGroups.get(renderType);
		if (group == null) {
			return EMPTY_PARTICLE_QUEUE;
		}
		return ((ParticleGroupAccessor) group).sodiumVolt$getParticles();
	}

	private static boolean isVisible(Particle particle, Frustum frustum) {
		ParticlePositionAccessor position = (ParticlePositionAccessor) particle;
		return frustum.pointInFrustum(
				position.sodiumVolt$getX(),
				position.sodiumVolt$getY(),
				position.sodiumVolt$getZ()
		);
	}

	private static double particleDistanceSquared(Particle particle, Vec3 cameraPosition) {
		ParticlePositionAccessor position = (ParticlePositionAccessor) particle;
		double xDistance = position.sodiumVolt$getX() - cameraPosition.x;
		double yDistance = position.sodiumVolt$getY() - cameraPosition.y;
		double zDistance = position.sodiumVolt$getZ() - cameraPosition.z;
		return xDistance * xDistance + yDistance * yDistance + zDistance * zDistance;
	}

	private static void finishLevelExtraction(LevelExtractionContext context) {
		if (!CONFIG.isVoltGuardEnabled()) {
			FRAME_STATE.get().disable();
			ADAPTIVE_BUDGET.disable();
			return;
		}

		FrameState frame = FRAME_STATE.get();
		if (!frame.configured) {
			double scale = ADAPTIVE_BUDGET.update(
					System.nanoTime(),
					CONFIG.getTargetFps(),
					CONFIG.isAdaptiveWorkloadControl()
			);
			frame.configure(
					effectiveBudget(CONFIG.getParticleRenderBudget(), scale),
					effectiveBudget(CONFIG.getBlockEntityRenderBudget(), scale),
					effectiveBudget(CONFIG.getDisplayEntityRenderBudget(), scale),
					CONFIG.isPrioritizeVisibleEffects(),
					CONFIG.isPreserveGameplayCriticalEffects()
			);
			frame.particleExtractionActive = false;
		}

		LevelRenderState levelState = context.levelState();
		HitResult hitResult = Minecraft.getInstance().hitResult;
		int removedBlockEntities = limitBlockEntities(
				levelState.blockEntityRenderStates,
				frame.blockEntityBudget,
				frame.prioritizeNearby,
				frame.preserveCritical,
				context.camera().position(),
				hitResult
		);
		int removedDisplayEntities = limitDisplayEntities(
				levelState.entityRenderStates,
				frame.displayEntityBudget,
				frame.prioritizeNearby,
				frame.preserveCritical,
				hitResult
		);
		showProtectionNotificationIfNeeded(
				frame.suppressedParticles,
				removedBlockEntities,
				removedDisplayEntities
		);
		frame.finishFrame();
	}

	private static int limitBlockEntities(
			List<BlockEntityRenderState> states,
			int budget,
			boolean prioritizeNearby,
			boolean preserveCritical,
			Vec3 cameraPosition,
			@Nullable HitResult hitResult
	) {
		int stateCount = states.size();
		if (stateCount <= budget) {
			return 0;
		}
		if (!prioritizeNearby && !preserveCritical) {
			states.subList(budget, stateCount).clear();
			return stateCount - budget;
		}

		BlockPos targetedBlock = preserveCritical && hitResult instanceof BlockHitResult blockHit
				? blockHit.getBlockPos()
				: null;
		BoundedTopK<BlockEntityRenderState> selection = new BoundedTopK<>(budget, prioritizeNearby);
		for (int index = 0; index < stateCount; index++) {
			BlockEntityRenderState state = states.get(index);
			double distanceSquared = blockDistanceSquared(state.blockPos, cameraPosition);
			selection.offer(
					state,
					targetedBlock != null && targetedBlock.equals(state.blockPos),
					preserveCritical && distanceSquared <= CRITICAL_EFFECT_DISTANCE_SQUARED,
					distanceSquared,
					index
			);
		}
		Set<BlockEntityRenderState> selected = selection.toIdentitySet();
		states.removeIf(state -> !selected.contains(state));
		return stateCount - states.size();
	}

	private static int limitDisplayEntities(
			List<EntityRenderState> states,
			int budget,
			boolean prioritizeNearby,
			boolean preserveCritical,
			@Nullable HitResult hitResult
	) {
		int displayCount = 0;
		for (EntityRenderState state : states) {
			if (state instanceof DisplayEntityRenderState) {
				displayCount++;
			}
		}
		if (displayCount <= budget) {
			return 0;
		}
		if (!prioritizeNearby && !preserveCritical) {
			int accepted = 0;
			Iterator<EntityRenderState> iterator = states.iterator();
			while (iterator.hasNext()) {
				if (iterator.next() instanceof DisplayEntityRenderState && accepted++ >= budget) {
					iterator.remove();
				}
			}
			return displayCount - budget;
		}

		Vec3 targetedPosition = preserveCritical && hitResult instanceof EntityHitResult
				? hitResult.getLocation()
				: null;
		BoundedTopK<EntityRenderState> selection = new BoundedTopK<>(budget, prioritizeNearby);
		for (int index = 0; index < states.size(); index++) {
			EntityRenderState state = states.get(index);
			if (state instanceof DisplayEntityRenderState) {
				selection.offer(
						state,
						targetedPosition != null && matchesTarget(state, targetedPosition),
						preserveCritical && state.distanceToCameraSq <= CRITICAL_EFFECT_DISTANCE_SQUARED,
						state.distanceToCameraSq,
						index
				);
			}
		}
		Set<EntityRenderState> selected = selection.toIdentitySet();
		states.removeIf(state -> state instanceof DisplayEntityRenderState && !selected.contains(state));
		return displayCount - budget;
	}

	private static double blockDistanceSquared(@Nullable BlockPos blockPos, Vec3 cameraPosition) {
		if (blockPos == null) {
			return Double.POSITIVE_INFINITY;
		}
		double xDistance = blockPos.getX() + 0.5D - cameraPosition.x;
		double yDistance = blockPos.getY() + 0.5D - cameraPosition.y;
		double zDistance = blockPos.getZ() + 0.5D - cameraPosition.z;
		return xDistance * xDistance + yDistance * yDistance + zDistance * zDistance;
	}

	private static boolean matchesTarget(EntityRenderState state, Vec3 targetPosition) {
		double xDistance = state.x - targetPosition.x;
		double yDistance = state.y - targetPosition.y;
		double zDistance = state.z - targetPosition.z;
		return xDistance * xDistance + yDistance * yDistance + zDistance * zDistance
				<= TARGET_MATCH_DISTANCE_SQUARED;
	}

	private static void showProtectionNotificationIfNeeded(
			int suppressedParticles,
			int removedBlockEntities,
			int removedDisplayEntities
	) {
		if (!CONFIG.isShowProtectionNotifications()) {
			return;
		}
		if (suppressedParticles < MATERIAL_PARTICLE_REDUCTION
				&& removedBlockEntities < MATERIAL_ENTITY_REDUCTION
				&& removedDisplayEntities < MATERIAL_ENTITY_REDUCTION) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		long now = System.nanoTime();
		long previous = LAST_NOTIFICATION_NANOS.get();
		if (previous != 0L && now - previous < NOTIFICATION_INTERVAL_NANOS
				|| !LAST_NOTIFICATION_NANOS.compareAndSet(previous, now)) {
			return;
		}

		int totalReduced = saturatingAdd(
				suppressedParticles,
				saturatingAdd(removedBlockEntities, removedDisplayEntities)
		);
		minecraft.execute(() -> {
			if (CONFIG.isVoltGuardEnabled() && CONFIG.isShowProtectionNotifications()) {
				minecraft.gui.hud.setOverlayMessage(
						Component.translatable("sodium-volt.notification.protection_active", totalReduced),
						false
				);
			}
		});
	}

	private static int effectiveBudget(int configuredBudget, double scale) {
		return Math.max(1, (int) Math.floor(configuredBudget * Math.clamp(
				scale,
				AdaptiveBudgetController.MINIMUM_SCALE,
				1.0D
		)));
	}

	private static int saturatingAdd(int first, int second) {
		if (first >= Integer.MAX_VALUE - second) {
			return Integer.MAX_VALUE;
		}
		return first + second;
	}

	private static final class FrameState {
		private boolean configured;
		private boolean particleExtractionActive;
		private boolean allParticlesSelected;
		private boolean prioritizeNearby;
		private boolean preserveCritical;
		private int particleBudget;
		private int blockEntityBudget;
		private int displayEntityBudget;
		private int suppressedParticles;
		private Set<Particle> selectedParticles = Collections.emptySet();

		private void configure(
				int particleBudget,
				int blockEntityBudget,
				int displayEntityBudget,
				boolean prioritizeNearby,
				boolean preserveCritical
		) {
			this.configured = true;
			this.particleExtractionActive = true;
			this.allParticlesSelected = true;
			this.prioritizeNearby = prioritizeNearby;
			this.preserveCritical = preserveCritical;
			this.particleBudget = particleBudget;
			this.blockEntityBudget = blockEntityBudget;
			this.displayEntityBudget = displayEntityBudget;
			this.suppressedParticles = 0;
			this.selectedParticles = Collections.emptySet();
		}

		private void endParticleExtraction() {
			this.particleExtractionActive = false;
			this.selectedParticles = Collections.emptySet();
		}

		private void finishFrame() {
			this.configured = false;
			this.particleExtractionActive = false;
			this.selectedParticles = Collections.emptySet();
		}

		private void disable() {
			this.configured = false;
			this.particleExtractionActive = false;
			this.allParticlesSelected = true;
			this.suppressedParticles = 0;
			this.selectedParticles = Collections.emptySet();
		}
	}
}
