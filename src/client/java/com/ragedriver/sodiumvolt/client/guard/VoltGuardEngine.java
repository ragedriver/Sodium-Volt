package com.ragedriver.sodiumvolt.client.guard;

import com.ragedriver.sodiumvolt.client.config.VoltGuardConfig;
import com.ragedriver.sodiumvolt.client.mixin.ParticleGroupAccessor;
import com.ragedriver.sodiumvolt.client.mixin.ParticlePositionAccessor;
import com.ragedriver.sodiumvolt.client.performance.ParticleEligibilityHandoff;
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
		ParticleEligibilityHandoff<Particle> eligibilityHandoff =
				VisibilityAwareParticleScheduler.particleEligibilityHandoff();
		if (eligibilityHandoff != null) {
			selectParticles(frame, eligibilityHandoff, camera.position());
		} else {
			selectParticles(frame, particleGroups, frustum, camera.position());
		}
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
			ParticleEligibilityHandoff<Particle> eligibilityHandoff,
			Vec3 cameraPosition
	) {
		int eligibleCount = eligibilityHandoff.candidateCount();
		if (eligibleCount <= frame.particleBudget) {
			frame.allParticlesSelected = true;
			frame.suppressedParticles = 0;
			return;
		}

		ParticleBudgetPlan plan = ParticleBudgetPlan.create(
				frame.particleBudget,
				eligibilityHandoff.specialCount(),
				frame.preserveCritical
		);
		Set<Particle> selected = frame.selectedParticles;
		if (plan.specialReserveCapacity() > 0) {
			BoundedTopK<Particle> specialReserve = frame.specialParticleSelection;
			specialReserve.reset(plan.specialReserveCapacity(), frame.prioritizeNearby);
			for (int index = 0; index < eligibilityHandoff.candidateCount(); index++) {
				if (!eligibilityHandoff.isSpecial(index)) {
					continue;
				}
				Particle particle = eligibilityHandoff.candidateAt(index);
				specialReserve.offer(
						particle,
						false,
						true,
						particleDistanceSquared(particle, cameraPosition),
						eligibilityHandoff.originalIndexAt(index)
				);
			}
			specialReserve.addTo(selected);
			specialReserve.clear();
		}

		BoundedTopK<Particle> generalSelection = frame.generalParticleSelection;
		generalSelection.reset(
				plan.remainingCapacityAfter(selected.size()),
				frame.prioritizeNearby
		);
		for (int index = 0; index < eligibilityHandoff.candidateCount(); index++) {
			Particle particle = eligibilityHandoff.candidateAt(index);
			if (selected.contains(particle)) {
				continue;
			}
			double distanceSquared = particleDistanceSquared(particle, cameraPosition);
			generalSelection.offer(
					particle,
					false,
					frame.preserveCritical && distanceSquared <= CRITICAL_EFFECT_DISTANCE_SQUARED,
					distanceSquared,
					eligibilityHandoff.originalIndexAt(index)
			);
		}
		generalSelection.addTo(selected);
		generalSelection.clear();

		frame.allParticlesSelected = false;
		frame.suppressedParticles = Math.max(0, eligibleCount - selected.size());
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
		Set<Particle> selected = frame.selectedParticles;
		if (plan.specialReserveCapacity() > 0) {
			BoundedTopK<Particle> specialReserve = frame.specialParticleSelection;
			specialReserve.reset(
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
			specialReserve.clear();
		}

		int remainingCapacity = plan.remainingCapacityAfter(selected.size());
		BoundedTopK<Particle> generalSelection = frame.generalParticleSelection;
		generalSelection.reset(
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
		generalSelection.clear();

		frame.allParticlesSelected = false;
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

	public static boolean beginBlockEntityHandoff(
			List<BlockEntityRenderState> states,
			Vec3 cameraPosition,
			@Nullable HitResult hitResult
	) {
		FrameState frame = FRAME_STATE.get();
		if (!CONFIG.isVoltGuardEnabled()) {
			frame.abortBlockEntityHandoff();
			return false;
		}
		ensureConfigured(frame);
		frame.beginBlockEntityHandoff(states, cameraPosition, hitResult);
		return true;
	}

	public static void offerBlockEntitySurvivor(BlockEntityRenderState state, int originalIndex) {
		FRAME_STATE.get().offerBlockEntitySurvivor(state, originalIndex);
	}

	public static void completeBlockEntityHandoff(List<BlockEntityRenderState> states) {
		FRAME_STATE.get().completeBlockEntityHandoff(states);
	}

	public static void abortBlockEntityHandoff() {
		FRAME_STATE.get().abortBlockEntityHandoff();
	}

	private static void finishLevelExtraction(LevelExtractionContext context) {
		if (!CONFIG.isVoltGuardEnabled()) {
			FRAME_STATE.get().disable();
			ADAPTIVE_BUDGET.disable();
			return;
		}

		FrameState frame = FRAME_STATE.get();
		if (!frame.configured) {
			ensureConfigured(frame);
			frame.particleExtractionActive = false;
		}

		LevelRenderState levelState = context.levelState();
		HitResult hitResult = Minecraft.getInstance().hitResult;
		List<BlockEntityRenderState> blockEntityStates = levelState.blockEntityRenderStates;
		int removedBlockEntities;
		if (frame.hasCompletedBlockEntityHandoff(blockEntityStates)) {
			removedBlockEntities = frame.consumeBlockEntityHandoff(blockEntityStates);
		} else {
			frame.abortBlockEntityHandoff();
			removedBlockEntities = limitBlockEntities(
					frame,
					blockEntityStates,
					frame.blockEntityBudget,
					frame.prioritizeNearby,
					frame.preserveCritical,
					context.camera().position(),
					hitResult
			);
		}
		int removedDisplayEntities = limitDisplayEntities(
				frame,
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

	private static void ensureConfigured(FrameState frame) {
		if (frame.configured) {
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
	}

	private static int limitBlockEntities(
			FrameState frame,
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
		BlockEntityGuardHandoff<BlockEntityRenderState> handoff = frame.blockEntityHandoff;
		handoff.begin(stateCount, budget, prioritizeNearby, preserveCritical);
		for (int index = 0; index < stateCount; index++) {
			BlockEntityRenderState state = states.get(index);
			double distanceSquared = blockDistanceSquared(state.blockPos, cameraPosition);
			handoff.offer(
					state,
					targetedBlock != null && targetedBlock.equals(state.blockPos),
					preserveCritical && distanceSquared <= CRITICAL_EFFECT_DISTANCE_SQUARED,
					distanceSquared,
					index
			);
		}
		handoff.complete();
		return handoff.applyTo(states);
	}

	private static int limitDisplayEntities(
			FrameState frame,
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
		BoundedTopK<EntityRenderState> selection = frame.displayEntitySelection;
		Set<EntityRenderState> selected = frame.selectedDisplayEntities;
		selection.reset(budget, prioritizeNearby);
		selected.clear();
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
		selection.addTo(selected);
		selection.clear();
		Iterator<EntityRenderState> iterator = states.iterator();
		while (iterator.hasNext()) {
			EntityRenderState state = iterator.next();
			if (state instanceof DisplayEntityRenderState && !selected.contains(state)) {
				iterator.remove();
			}
		}
		selected.clear();
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
		private final BoundedTopK<Particle> specialParticleSelection = new BoundedTopK<>();
		private final BoundedTopK<Particle> generalParticleSelection = new BoundedTopK<>();
		private final BlockEntityGuardHandoff<BlockEntityRenderState> blockEntityHandoff =
				new BlockEntityGuardHandoff<>();
		private final BoundedTopK<EntityRenderState> displayEntitySelection = new BoundedTopK<>();
		private final Set<Particle> selectedParticles = newIdentitySet();
		private final Set<EntityRenderState> selectedDisplayEntities = newIdentitySet();
		private boolean configured;
		private boolean particleExtractionActive;
		private boolean allParticlesSelected;
		private boolean prioritizeNearby;
		private boolean preserveCritical;
		private int particleBudget;
		private int blockEntityBudget;
		private int displayEntityBudget;
		private int suppressedParticles;
		private List<BlockEntityRenderState> blockEntityHandoffSource;
		private Vec3 blockEntityHandoffCamera = Vec3.ZERO;
		private BlockPos blockEntityHandoffTarget;
		private boolean blockEntityHandoffRecording;
		private boolean blockEntityHandoffComplete;
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
			this.clearSelections();
		}

		private void endParticleExtraction() {
			this.particleExtractionActive = false;
			this.clearParticleSelection();
		}

		private void finishFrame() {
			this.configured = false;
			this.particleExtractionActive = false;
			this.clearSelections();
		}

		private void disable() {
			this.configured = false;
			this.particleExtractionActive = false;
			this.allParticlesSelected = true;
			this.suppressedParticles = 0;
			this.clearSelections();
		}

		private void clearSelections() {
			this.clearParticleSelection();
			this.abortBlockEntityHandoff();
			this.displayEntitySelection.clear();
			this.selectedDisplayEntities.clear();
		}

		private void clearParticleSelection() {
			this.specialParticleSelection.clear();
			this.generalParticleSelection.clear();
			this.selectedParticles.clear();
		}

		private void beginBlockEntityHandoff(
				List<BlockEntityRenderState> states,
				Vec3 cameraPosition,
				@Nullable HitResult hitResult
		) {
			this.abortBlockEntityHandoff();
			this.blockEntityHandoffSource = states;
			this.blockEntityHandoffCamera = cameraPosition;
			this.blockEntityHandoffTarget = this.preserveCritical
					&& hitResult instanceof BlockHitResult blockHit
					? blockHit.getBlockPos()
					: null;
			this.blockEntityHandoff.begin(
					states.size(),
					this.blockEntityBudget,
					this.prioritizeNearby,
					this.preserveCritical
			);
			this.blockEntityHandoffRecording = true;
		}

		private void offerBlockEntitySurvivor(BlockEntityRenderState state, int originalIndex) {
			if (!this.blockEntityHandoffRecording) {
				return;
			}
			if (!this.blockEntityHandoff.requiresRanking()) {
				this.blockEntityHandoff.offerUnranked();
				return;
			}
			double distanceSquared = blockDistanceSquared(state.blockPos, this.blockEntityHandoffCamera);
			this.blockEntityHandoff.offer(
					state,
					this.blockEntityHandoffTarget != null
							&& this.blockEntityHandoffTarget.equals(state.blockPos),
					this.preserveCritical
							&& distanceSquared <= CRITICAL_EFFECT_DISTANCE_SQUARED,
					distanceSquared,
					originalIndex
			);
		}

		private void completeBlockEntityHandoff(List<BlockEntityRenderState> states) {
			if (!this.blockEntityHandoffRecording || this.blockEntityHandoffSource != states) {
				this.abortBlockEntityHandoff();
				return;
			}
			this.blockEntityHandoff.complete();
			this.blockEntityHandoffRecording = false;
			this.blockEntityHandoffComplete = true;
		}

		private boolean hasCompletedBlockEntityHandoff(List<BlockEntityRenderState> states) {
			return this.blockEntityHandoffComplete
					&& this.blockEntityHandoffSource == states
					&& this.blockEntityHandoff.isCompleteForSize(states.size());
		}

		private int consumeBlockEntityHandoff(List<BlockEntityRenderState> states) {
			try {
				return this.blockEntityHandoff.applyTo(states);
			} finally {
				this.clearBlockEntityHandoffMetadata();
			}
		}

		private void abortBlockEntityHandoff() {
			this.blockEntityHandoff.abort();
			this.clearBlockEntityHandoffMetadata();
		}

		private void clearBlockEntityHandoffMetadata() {
			this.blockEntityHandoffSource = null;
			this.blockEntityHandoffCamera = Vec3.ZERO;
			this.blockEntityHandoffTarget = null;
			this.blockEntityHandoffRecording = false;
			this.blockEntityHandoffComplete = false;
		}

		private static <T> Set<T> newIdentitySet() {
			return Collections.newSetFromMap(new IdentityHashMap<>());
		}
	}
}
