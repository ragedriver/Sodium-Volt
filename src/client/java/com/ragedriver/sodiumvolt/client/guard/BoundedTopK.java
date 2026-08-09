package com.ragedriver.sodiumvolt.client.guard;

import java.util.Arrays;
import java.util.Set;

/**
 * Reusable bounded best-candidate heap. Candidate metadata is stored in
 * parallel arrays so the first overloaded frame grows a small fixed number of
 * arrays instead of allocating one wrapper object per retained candidate.
 */
final class BoundedTopK<T> {
	private Object[] values = new Object[0];
	private boolean[] targeted = new boolean[0];
	private boolean[] critical = new boolean[0];
	private double[] distanceSquared = new double[0];
	private int[] originalIndices = new int[0];
	private int capacity;
	private boolean prioritizeDistance;
	private int size;

	BoundedTopK() {
	}

	BoundedTopK(int capacity, boolean prioritizeDistance) {
		this.reset(capacity, prioritizeDistance);
	}

	void reset(int capacity, boolean prioritizeDistance) {
		this.clear();
		this.capacity = Math.max(0, capacity);
		this.prioritizeDistance = prioritizeDistance;
	}

	void offer(
			T value,
			boolean targeted,
			boolean critical,
			double distanceSquared,
			int originalIndex
	) {
		if (this.capacity == 0) {
			return;
		}

		double safeDistanceSquared = sanitizeDistance(distanceSquared);
		if (this.size < this.capacity) {
			this.ensureCapacity(this.size + 1);
			int inserted = this.size++;
			this.replace(inserted, value, targeted, critical, safeDistanceSquared, originalIndex);
			this.siftUp(inserted);
			return;
		}

		if (compareCandidate(
				targeted,
				critical,
				safeDistanceSquared,
				originalIndex,
				this.targeted[0],
				this.critical[0],
				this.distanceSquared[0],
				this.originalIndices[0],
				this.prioritizeDistance
		) < 0) {
			this.replace(0, value, targeted, critical, safeDistanceSquared, originalIndex);
			this.siftDown(0);
		}
	}

	void addTo(Set<T> destination) {
		for (int index = 0; index < this.size; index++) {
			destination.add(this.valueAt(index));
		}
	}

	int copyOriginalIndices(int[] destination) {
		if (destination.length < this.size) {
			throw new IllegalArgumentException("destination is smaller than the selection");
		}
		System.arraycopy(this.originalIndices, 0, destination, 0, this.size);
		return this.size;
	}

	int countExcluding(Set<T> excluded) {
		int count = 0;
		for (int index = 0; index < this.size; index++) {
			if (!excluded.contains(this.valueAt(index))) {
				count++;
			}
		}
		return count;
	}

	boolean hasDuplicateExcluding(Set<T> excluded, Set<T> identityScratch) {
		identityScratch.clear();
		try {
			for (int index = 0; index < this.size; index++) {
				T value = this.valueAt(index);
				if (!excluded.contains(value) && !identityScratch.add(value)) {
					return true;
				}
			}
			return false;
		} finally {
			identityScratch.clear();
		}
	}

	void removeExcludedAndTrim(Set<T> excluded, int retainedCapacity) {
		int retained = 0;
		for (int index = 0; index < this.size; index++) {
			if (!excluded.contains(this.valueAt(index))) {
				if (retained != index) {
					this.copy(index, retained);
				}
				retained++;
			}
		}
		this.clearRange(retained, this.size);
		this.size = retained;
		this.heapify();
		int boundedCapacity = Math.max(0, retainedCapacity);
		while (this.size > boundedCapacity) {
			this.removeWorst();
		}
	}

	void clear() {
		this.clearRange(0, this.size);
		this.size = 0;
	}

	void releaseStorage() {
		this.clear();
		this.values = new Object[0];
		this.targeted = new boolean[0];
		this.critical = new boolean[0];
		this.distanceSquared = new double[0];
		this.originalIndices = new int[0];
		this.capacity = 0;
	}

	int size() {
		return this.size;
	}

	int allocatedEntryCount() {
		return this.values.length;
	}

	boolean retainsValueForTesting(T value) {
		for (Object retained : this.values) {
			if (retained == value) {
				return true;
			}
		}
		return false;
	}

	private void ensureCapacity(int requested) {
		if (requested <= this.values.length) {
			return;
		}
		int expanded = this.values.length == 0 ? Math.min(8, this.capacity) : this.values.length;
		while (expanded < requested) {
			expanded = expanded >= this.capacity - expanded
					? this.capacity
					: expanded * 2;
		}
		this.values = Arrays.copyOf(this.values, expanded);
		this.targeted = Arrays.copyOf(this.targeted, expanded);
		this.critical = Arrays.copyOf(this.critical, expanded);
		this.distanceSquared = Arrays.copyOf(this.distanceSquared, expanded);
		this.originalIndices = Arrays.copyOf(this.originalIndices, expanded);
	}

	private void heapify() {
		for (int index = this.size / 2 - 1; index >= 0; index--) {
			this.siftDown(index);
		}
	}

	private void siftUp(int index) {
		while (index > 0) {
			int parent = (index - 1) >>> 1;
			if (!this.isWorse(index, parent)) {
				return;
			}
			this.swap(index, parent);
			index = parent;
		}
	}

	private void siftDown(int index) {
		while (true) {
			int left = index * 2 + 1;
			if (left >= this.size) {
				return;
			}
			int right = left + 1;
			int worst = right < this.size && this.isWorse(right, left) ? right : left;
			if (!this.isWorse(worst, index)) {
				return;
			}
			this.swap(index, worst);
			index = worst;
		}
	}

	private boolean isWorse(int left, int right) {
		return compareCandidate(
				this.targeted[right],
				this.critical[right],
				this.distanceSquared[right],
				this.originalIndices[right],
				this.targeted[left],
				this.critical[left],
				this.distanceSquared[left],
				this.originalIndices[left],
				this.prioritizeDistance
		) < 0;
	}

	private void removeWorst() {
		int last = --this.size;
		if (last > 0) {
			this.copy(last, 0);
		}
		this.clearSlot(last);
		if (this.size > 1) {
			this.siftDown(0);
		}
	}

	private void replace(
			int index,
			T value,
			boolean targeted,
			boolean critical,
			double distanceSquared,
			int originalIndex
	) {
		this.values[index] = value;
		this.targeted[index] = targeted;
		this.critical[index] = critical;
		this.distanceSquared[index] = distanceSquared;
		this.originalIndices[index] = originalIndex;
	}

	private void copy(int source, int destination) {
		this.values[destination] = this.values[source];
		this.targeted[destination] = this.targeted[source];
		this.critical[destination] = this.critical[source];
		this.distanceSquared[destination] = this.distanceSquared[source];
		this.originalIndices[destination] = this.originalIndices[source];
	}

	private void swap(int first, int second) {
		Object value = this.values[first];
		this.values[first] = this.values[second];
		this.values[second] = value;
		boolean targetedValue = this.targeted[first];
		this.targeted[first] = this.targeted[second];
		this.targeted[second] = targetedValue;
		boolean criticalValue = this.critical[first];
		this.critical[first] = this.critical[second];
		this.critical[second] = criticalValue;
		double distance = this.distanceSquared[first];
		this.distanceSquared[first] = this.distanceSquared[second];
		this.distanceSquared[second] = distance;
		int originalIndex = this.originalIndices[first];
		this.originalIndices[first] = this.originalIndices[second];
		this.originalIndices[second] = originalIndex;
	}

	private void clearRange(int start, int end) {
		Arrays.fill(this.values, start, end, null);
		Arrays.fill(this.targeted, start, end, false);
		Arrays.fill(this.critical, start, end, false);
		Arrays.fill(this.distanceSquared, start, end, 0.0D);
		Arrays.fill(this.originalIndices, start, end, 0);
	}

	private void clearSlot(int index) {
		this.values[index] = null;
		this.targeted[index] = false;
		this.critical[index] = false;
		this.distanceSquared[index] = 0.0D;
		this.originalIndices[index] = 0;
	}

	@SuppressWarnings("unchecked")
	private T valueAt(int index) {
		return (T) this.values[index];
	}

	private static int compareCandidate(
			boolean targeted,
			boolean critical,
			double distanceSquared,
			int originalIndex,
			boolean retainedTargeted,
			boolean retainedCritical,
			double retainedDistanceSquared,
			int retainedOriginalIndex,
			boolean prioritizeDistance
	) {
		int comparison = Boolean.compare(retainedTargeted, targeted);
		if (comparison != 0) {
			return comparison;
		}

		comparison = Boolean.compare(retainedCritical, critical);
		if (comparison != 0) {
			return comparison;
		}

		if (prioritizeDistance) {
			comparison = Double.compare(distanceSquared, retainedDistanceSquared);
			if (comparison != 0) {
				return comparison;
			}
		}

		return Integer.compare(originalIndex, retainedOriginalIndex);
	}

	private static double sanitizeDistance(double distanceSquared) {
		return Double.isFinite(distanceSquared) && distanceSquared >= 0.0D
				? distanceSquared
				: Double.POSITIVE_INFINITY;
	}
}
