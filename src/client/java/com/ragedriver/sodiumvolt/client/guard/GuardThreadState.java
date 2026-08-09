package com.ragedriver.sodiumvolt.client.guard;

import java.util.Objects;
import java.util.function.Supplier;

/** Nullable ThreadLocal owner: release never initializes a missing value. */
final class GuardThreadState<T> {
	private final ThreadLocal<T> storage = new ThreadLocal<>();
	private final Supplier<T> factory;

	GuardThreadState(Supplier<T> factory) {
		this.factory = Objects.requireNonNull(factory, "factory");
	}

	T acquire() {
		T current = this.storage.get();
		if (current == null) {
			current = this.factory.get();
			this.storage.set(current);
		}
		return current;
	}

	T peek() {
		return this.storage.get();
	}

	T release() {
		T released = this.storage.get();
		this.storage.remove();
		return released;
	}
}
