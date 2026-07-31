package com.ragedriver.sodiumvolt.client.performance;

import java.util.List;
import java.util.function.Predicate;

public final class AttStateSpriteMapping<S, P> {
	private Object[] states;
	private Object[] sprites;
	private int size;
	private boolean valid;
	private boolean rawOverflow;

	public static <S, P> AttStateSpriteMapping<S, P> build(
			List<P> rawSprites,
			List<S> states,
			Predicate<P> animatedPredicate,
			int maximumRawSprites,
			int maximumStates
	) {
		AttStateSpriteMapping<S, P> mapping = new AttStateSpriteMapping<>();
		if (rawSprites == null || states == null || animatedPredicate == null
				|| states.size() > maximumStates
				|| rawSprites.size() > maximumRawSprites) {
			mapping.rawOverflow = rawSprites != null && rawSprites.size() > maximumRawSprites
					|| states != null && states.size() > maximumStates;
			return mapping;
		}
		mapping.states = new Object[states.size()];
		mapping.sprites = new Object[states.size()];
		int animationIndex = 0;
		int rawVisits = 0;
		for (P sprite : rawSprites) {
			if (rawVisits++ >= maximumRawSprites) {
				mapping.rawOverflow = true;
				mapping.release();
				return mapping;
			}
			if (!animatedPredicate.test(sprite)) {
				continue;
			}
			if (animationIndex >= states.size()) {
				mapping.release();
				return mapping;
			}
			mapping.states[animationIndex] = states.get(animationIndex);
			mapping.sprites[animationIndex] = sprite;
			animationIndex++;
		}
		if (animationIndex != states.size()) {
			mapping.release();
			return mapping;
		}
		mapping.size = animationIndex;
		mapping.valid = true;
		return mapping;
	}

	public boolean isValid() {
		return this.valid;
	}

	public boolean hadRawOverflow() {
		return this.rawOverflow;
	}

	public int size() {
		return this.size;
	}

	@SuppressWarnings("unchecked")
	public S stateAt(int index) {
		return (S) this.states[index];
	}

	@SuppressWarnings("unchecked")
	public P spriteAt(int index) {
		return (P) this.sprites[index];
	}

	public void release() {
		if (this.states != null) {
			for (int index = 0; index < this.states.length; index++) {
				this.states[index] = null;
				this.sprites[index] = null;
			}
		}
		this.states = null;
		this.sprites = null;
		this.size = 0;
		this.valid = false;
	}

	boolean retainsForTesting(Object value) {
		if (this.states == null) {
			return false;
		}
		for (int index = 0; index < this.states.length; index++) {
			if (this.states[index] == value || this.sprites[index] == value) {
				return true;
			}
		}
		return false;
	}
}
