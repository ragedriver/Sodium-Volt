package com.ragedriver.sodiumvolt.client.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

final class BoundedTopK<T> {
	private final List<Entry<T>> entryPool = new ArrayList<>();
	private final PriorityQueue<Entry<T>> worstFirst;
	private int capacity;
	private boolean prioritizeDistance;
	private int activeEntryCount;
	private int allocatedEntryCount;

	BoundedTopK() {
		this.worstFirst = new PriorityQueue<>(
				1,
				(left, right) -> compare(right, left, this.prioritizeDistance)
		);
	}

	BoundedTopK(int capacity, boolean prioritizeDistance) {
		this();
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
		if (this.worstFirst.size() < this.capacity) {
			Entry<T> entry = this.acquireEntry();
			entry.replace(value, targeted, critical, safeDistanceSquared, originalIndex);
			this.worstFirst.add(entry);
			return;
		}

		Entry<T> currentWorst = Objects.requireNonNull(this.worstFirst.peek());
		if (compareCandidate(
				targeted,
				critical,
				safeDistanceSquared,
				originalIndex,
				currentWorst,
				this.prioritizeDistance
		) < 0) {
			this.worstFirst.poll();
			currentWorst.replace(value, targeted, critical, safeDistanceSquared, originalIndex);
			this.worstFirst.add(currentWorst);
		}
	}

	void addTo(Set<T> destination) {
		for (Entry<T> entry : this.worstFirst) {
			destination.add(entry.value());
		}
	}

	void clear() {
		this.worstFirst.clear();
		for (int index = 0; index < this.activeEntryCount; index++) {
			this.entryPool.get(index).clearValue();
		}
		this.activeEntryCount = 0;
	}

	int size() {
		return this.worstFirst.size();
	}

	int allocatedEntryCount() {
		return this.allocatedEntryCount;
	}

	boolean retainsValueForTesting(T value) {
		for (Entry<T> entry : this.entryPool) {
			if (entry.value() == value) {
				return true;
			}
		}
		return false;
	}

	private Entry<T> acquireEntry() {
		if (this.activeEntryCount < this.entryPool.size()) {
			return this.entryPool.get(this.activeEntryCount++);
		}

		Entry<T> entry = new Entry<>();
		this.entryPool.add(entry);
		this.activeEntryCount++;
		this.allocatedEntryCount++;
		return entry;
	}

	private static int compare(Entry<?> left, Entry<?> right, boolean prioritizeDistance) {
		int comparison = Boolean.compare(right.targeted(), left.targeted());
		if (comparison != 0) {
			return comparison;
		}

		comparison = Boolean.compare(right.critical(), left.critical());
		if (comparison != 0) {
			return comparison;
		}

		if (prioritizeDistance) {
			comparison = Double.compare(left.distanceSquared(), right.distanceSquared());
			if (comparison != 0) {
				return comparison;
			}
		}

		return Integer.compare(left.originalIndex(), right.originalIndex());
	}

	private static int compareCandidate(
			boolean targeted,
			boolean critical,
			double distanceSquared,
			int originalIndex,
			Entry<?> currentWorst,
			boolean prioritizeDistance
	) {
		int comparison = Boolean.compare(currentWorst.targeted(), targeted);
		if (comparison != 0) {
			return comparison;
		}

		comparison = Boolean.compare(currentWorst.critical(), critical);
		if (comparison != 0) {
			return comparison;
		}

		if (prioritizeDistance) {
			comparison = Double.compare(distanceSquared, currentWorst.distanceSquared());
			if (comparison != 0) {
				return comparison;
			}
		}

		return Integer.compare(originalIndex, currentWorst.originalIndex());
	}

	private static double sanitizeDistance(double distanceSquared) {
		return Double.isFinite(distanceSquared) && distanceSquared >= 0.0D
				? distanceSquared
				: Double.POSITIVE_INFINITY;
	}

	private static final class Entry<T> {
		private T value;
		private boolean targeted;
		private boolean critical;
		private double distanceSquared;
		private int originalIndex;

		private void replace(
				T value,
				boolean targeted,
				boolean critical,
				double distanceSquared,
				int originalIndex
		) {
			this.value = value;
			this.targeted = targeted;
			this.critical = critical;
			this.distanceSquared = distanceSquared;
			this.originalIndex = originalIndex;
		}

		private void clearValue() {
			this.value = null;
		}

		private T value() {
			return this.value;
		}

		private boolean targeted() {
			return this.targeted;
		}

		private boolean critical() {
			return this.critical;
		}

		private double distanceSquared() {
			return this.distanceSquared;
		}

		private int originalIndex() {
			return this.originalIndex;
		}
	}
}
