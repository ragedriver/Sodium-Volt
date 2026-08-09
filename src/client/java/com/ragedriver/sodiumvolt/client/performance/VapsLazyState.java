package com.ragedriver.sodiumvolt.client.performance;

import java.util.Objects;
import java.util.function.Supplier;

/** Render-thread-owned lazy state holder with an explicit durable-release edge. */
final class VapsLazyState<T> {
	private final Supplier<T> factory;
	private T state;

	VapsLazyState(Supplier<T> factory) {
		this.factory = Objects.requireNonNull(factory, "factory");
	}

	T acquire() {
		if (this.state == null) {
			this.state = this.factory.get();
		}
		return this.state;
	}

	T peek() {
		return this.state;
	}

	T release() {
		T released = this.state;
		this.state = null;
		return released;
	}
}
