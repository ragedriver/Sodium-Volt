package com.ragedriver.sodiumvolt.client.guard;

import java.util.Arrays;

/** Defers ranking until eligible work exceeds the budget; storage is budget bounded. */
final class ParticleSelectionPrefix<T> {
	@FunctionalInterface
	interface CandidateConsumer<T> {
		void accept(T value, boolean special, int originalIndex);
	}

	private Object[] values = new Object[0];
	private boolean[] special = new boolean[0];
	private int[] originalIndices = new int[0];
	private int size;
	private int budget;
	private boolean overloaded;

	void begin(int budget) {
		this.clear();
		this.budget = Math.max(0, budget);
	}

	@SuppressWarnings("unchecked")
	void offer(T value, boolean isSpecial, int originalIndex, CandidateConsumer<T> consumer) {
		if (!this.overloaded && this.size < this.budget) {
			if (this.size == this.values.length) {
				int capacity = (int) Math.min(this.budget, Math.max(8L, this.size * 2L));
				this.values = Arrays.copyOf(this.values, capacity);
				this.special = Arrays.copyOf(this.special, capacity);
				this.originalIndices = Arrays.copyOf(this.originalIndices, capacity);
			}
			this.values[this.size] = value;
			this.special[this.size] = isSpecial;
			this.originalIndices[this.size++] = originalIndex;
			return;
		}
		if (!this.overloaded) {
			this.overloaded = true;
			try {
				for (int index = 0; index < this.size; index++) {
					consumer.accept((T) this.values[index], this.special[index], this.originalIndices[index]);
				}
			} finally {
				this.clearReferences();
			}
		}
		consumer.accept(value, isSpecial, originalIndex);
	}

	private void clearReferences() {
		Arrays.fill(this.values, 0, this.size, null);
		this.size = 0;
	}

	void clear() {
		this.clearReferences();
		this.overloaded = false;
	}

	void releaseStorage() {
		this.clear();
		this.values = new Object[0];
		this.special = new boolean[0];
		this.originalIndices = new int[0];
	}

	int allocatedEntriesForTesting() {
		return this.values.length;
	}

	boolean retainsReferenceForTesting(T value) {
		for (Object retained : this.values) {
			if (retained == value) {
				return true;
			}
		}
		return false;
	}
}
