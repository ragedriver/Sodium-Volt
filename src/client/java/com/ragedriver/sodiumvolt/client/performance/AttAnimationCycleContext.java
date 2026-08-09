package com.ragedriver.sodiumvolt.client.performance;

import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded render-thread context for animation-state ticks. Dormant hooks first
 * read {@link #hasActiveCycle()}, so they never touch or initialize a ThreadLocal.
 */
public final class AttAnimationCycleContext {
	private static final int MAXIMUM_NESTING = 8;
	private static final ThreadLocal<ContextStack> CURRENT = new ThreadLocal<>();
	private static final AtomicInteger ACTIVE_CYCLES = new AtomicInteger();

	private AttAnimationCycleContext() {
	}

	static boolean push(AttRuntimeSettings.AtlasCycle cycle) {
		return pushCaptured(
				cycle.atlasLocation(), cycle.warmup(), cycle.collectStatistics(),
				cycle.keepInterfaceAtlasesFullSpeed(), cycle.screenOpen(),
				cycle.pauseInvisibleAnimations(), cycle.unseenKeepaliveTicks(),
				cycle.distanceAwareCadence(), cycle.fullSpeedDistanceSquared(),
				cycle.distantUpdateInterval(), cycle.immediateSmoothResume(),
				cycle.apcThrottleAnimations(), cycle.exemptCriticalTextures(),
				cycle.honorExemptionLists(), cycle.exemptionLookup()
		);
	}

	static boolean pushCaptured(
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
		ContextStack stack = CURRENT.get();
		if (stack == null) {
			stack = new ContextStack();
			CURRENT.set(stack);
		}
		int slot = stack.reserve();
		if (slot < 0) {
			return false;
		}
		stack.cycle(slot).capture(
				atlasLocation, warmup, collectStatistics,
				keepInterfaceAtlasesFullSpeed, screenOpen,
				pauseInvisibleAnimations, unseenKeepaliveTicks,
				distanceAwareCadence, fullSpeedDistanceSquared,
				distantUpdateInterval, immediateSmoothResume,
				apcThrottleAnimations, exemptCriticalTextures,
				honorExemptionLists, exemptionLookup
		);
		ACTIVE_CYCLES.incrementAndGet();
		return true;
	}

	public static boolean hasActiveCycle() {
		return ACTIVE_CYCLES.get() != 0;
	}

	public static void pop(boolean installed) {
		ContextStack stack = CURRENT.get();
		if (stack == null) {
			return;
		}
		if (!installed) {
			if (stack.overflowDepth > 0) {
				stack.overflowDepth--;
			}
			return;
		}
		if (stack.depth <= 0) {
			return;
		}
		stack.cycles[--stack.depth].releaseReferences();
		ACTIVE_CYCLES.decrementAndGet();
	}

	static AttRuntimeSettings.AtlasCycle current() {
		ContextStack stack = CURRENT.get();
		if (stack == null || stack.overflowDepth > 0 || stack.depth <= 0) {
			return null;
		}
		return stack.cycles[stack.depth - 1];
	}

	static boolean push(Identifier atlasLocation, boolean warmup) {
		return pushCaptured(
				atlasLocation, warmup, false, false, false,
				false, 0, false, 0.0D, 1, false,
				false, false, false, new AttIdentifierLookup()
		);
	}

	public static Identifier atlasLocation() {
		AttRuntimeSettings.AtlasCycle cycle = current();
		return cycle == null ? null : cycle.atlasLocation();
	}

	public static boolean isWarmup() {
		AttRuntimeSettings.AtlasCycle cycle = current();
		return cycle != null && cycle.warmup();
	}

	static int depthForTesting() {
		ContextStack stack = CURRENT.get();
		return stack == null
				? 0
				: stack.overflowDepth > 0 ? -stack.overflowDepth : stack.depth;
	}

	static boolean initializedForTesting() {
		return CURRENT.get() != null;
	}

	static void clearForTesting() {
		ContextStack stack = CURRENT.get();
		if (stack != null) {
			ACTIVE_CYCLES.addAndGet(-stack.depth);
		}
		CURRENT.remove();
	}

	private static final class ContextStack {
		private final AttRuntimeSettings.AtlasCycle[] cycles =
				new AttRuntimeSettings.AtlasCycle[MAXIMUM_NESTING];
		private int depth;
		private int overflowDepth;

		private int reserve() {
			if (this.overflowDepth > 0 || this.depth >= MAXIMUM_NESTING) {
				this.overflowDepth++;
				return -1;
			}
			return this.depth++;
		}

		private AttRuntimeSettings.AtlasCycle cycle(int index) {
			AttRuntimeSettings.AtlasCycle cycle = this.cycles[index];
			if (cycle == null) {
				cycle = new AttRuntimeSettings.AtlasCycle();
				this.cycles[index] = cycle;
			}
			return cycle;
		}
	}
}
