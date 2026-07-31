package com.ragedriver.sodiumvolt.client.performance;

import net.minecraft.resources.Identifier;

/**
 * Allocation-free render-thread context for animation-state ticks. Targeting
 * the state method instead of TextureAtlas's invocation makes ATT compose with
 * animation mods which redirect that invocation, including Sodium Extra.
 */
public final class AttAnimationCycleContext {
	private static final int MAXIMUM_NESTING = 8;
	private static final ThreadLocal<ContextStack> CURRENT =
			ThreadLocal.withInitial(ContextStack::new);

	private AttAnimationCycleContext() {
	}

	/**
	 * Pushes an atlas context. The returned token must be passed to {@link
	 * #pop(boolean)} from a finally block.
	 */
	public static boolean push(Identifier atlasLocation, boolean warmup) {
		ContextStack stack = CURRENT.get();
		if (stack.overflowDepth > 0 || stack.depth >= MAXIMUM_NESTING) {
			stack.overflowDepth++;
			return false;
		}
		stack.atlasLocations[stack.depth] = atlasLocation;
		stack.warmup[stack.depth] = warmup;
		stack.depth++;
		return true;
	}

	/**
	 * Pops exactly one context. Overflowed scopes deliberately expose no
	 * current context, causing animation ticks to fail open until they unwind.
	 */
	public static void pop(boolean installed) {
		ContextStack stack = CURRENT.get();
		if (!installed) {
			if (stack.overflowDepth > 0) {
				stack.overflowDepth--;
			}
			return;
		}
		if (stack.depth <= 0) {
			return;
		}
		int index = --stack.depth;
		stack.atlasLocations[index] = null;
		stack.warmup[index] = false;
	}

	public static Identifier atlasLocation() {
		ContextStack stack = CURRENT.get();
		if (stack.overflowDepth > 0 || stack.depth <= 0) {
			return null;
		}
		return stack.atlasLocations[stack.depth - 1];
	}

	public static boolean isWarmup() {
		ContextStack stack = CURRENT.get();
		return stack.overflowDepth == 0
				&& stack.depth > 0
				&& stack.warmup[stack.depth - 1];
	}

	static int depthForTesting() {
		ContextStack stack = CURRENT.get();
		return stack.overflowDepth > 0 ? -stack.overflowDepth : stack.depth;
	}

	static void clearForTesting() {
		CURRENT.remove();
	}

	private static final class ContextStack {
		private final Identifier[] atlasLocations = new Identifier[MAXIMUM_NESTING];
		private final boolean[] warmup = new boolean[MAXIMUM_NESTING];
		private int depth;
		private int overflowDepth;
	}
}
