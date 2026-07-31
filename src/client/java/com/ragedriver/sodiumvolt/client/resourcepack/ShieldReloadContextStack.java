package com.ragedriver.sodiumvolt.client.resourcepack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A small dependency-free state machine for pairing nested reload contexts. Calls beyond
 * the configured depth are represented by an overflow counter instead of growing the stack.
 */
public final class ShieldReloadContextStack<T> {
	private final int maximumDepth;
	private final Deque<Frame<T>> frames;
	private int overflowDepth;

	public ShieldReloadContextStack(int maximumDepth) {
		if (maximumDepth < 1) {
			throw new IllegalArgumentException("maximumDepth must be positive");
		}
		this.maximumDepth = maximumDepth;
		this.frames = new ArrayDeque<>(Math.min(maximumDepth, 4));
	}

	public Frame<T> begin(
			T sharedToken,
			boolean sharedTokenAvailable,
			boolean externallyOverflowing,
			Supplier<T> ownedTokenFactory,
			T disabledToken
	) {
		Objects.requireNonNull(ownedTokenFactory, "ownedTokenFactory");
		Objects.requireNonNull(disabledToken, "disabledToken");
		if (this.overflowDepth != 0 || this.frames.size() >= this.maximumDepth) {
			this.overflowDepth++;
			return null;
		}

		Frame<T> frame;
		if (externallyOverflowing) {
			frame = new Frame<>(disabledToken, Selection.DISABLED);
		} else if (sharedTokenAvailable) {
			frame = new Frame<>(
					Objects.requireNonNull(sharedToken, "sharedToken"),
					Selection.BORROWED
			);
		} else {
			frame = new Frame<>(
					Objects.requireNonNull(ownedTokenFactory.get(), "ownedToken"),
					Selection.OWNED
			);
		}
		this.frames.push(frame);
		return frame;
	}

	public T currentOr(T fallback) {
		Frame<T> frame = this.overflowDepth == 0 ? this.frames.peek() : null;
		return frame == null ? fallback : frame.token();
	}

	public Frame<T> finish() {
		if (this.overflowDepth != 0) {
			this.overflowDepth--;
			return null;
		}
		return this.frames.poll();
	}

	public boolean isEmpty() {
		return this.frames.isEmpty() && this.overflowDepth == 0;
	}

	public int depth() {
		return this.frames.size();
	}

	public int overflowDepth() {
		return this.overflowDepth;
	}

	public enum Selection {
		OWNED,
		BORROWED,
		DISABLED
	}

	public record Frame<T>(T token, Selection selection) {
		public boolean ownsToken() {
			return this.selection == Selection.OWNED;
		}
	}
}
