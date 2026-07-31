package com.ragedriver.sodiumvolt.client.guard;

import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.PriorityQueue;
import java.util.Set;

final class BoundedTopK<T> {
	private final int capacity;
	private final boolean prioritizeDistance;
	private final Comparator<Entry<T>> bestFirst;
	private final PriorityQueue<Entry<T>> worstFirst;
	private int allocatedEntryCount;

	BoundedTopK(int capacity, boolean prioritizeDistance) {
		this.capacity = Math.max(0, capacity);
		this.prioritizeDistance = prioritizeDistance;
		this.bestFirst = (left, right) -> compare(left, right, prioritizeDistance);
		this.worstFirst = new PriorityQueue<>(Math.max(1, this.capacity), this.bestFirst.reversed());
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
			this.worstFirst.add(new Entry<>(
					value,
					targeted,
					critical,
					safeDistanceSquared,
					originalIndex
			));
			this.allocatedEntryCount++;
			return;
		}

		Entry<T> currentWorst = this.worstFirst.peek();
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

	Set<T> toIdentitySet() {
		Set<T> selected = Collections.newSetFromMap(new IdentityHashMap<>(this.worstFirst.size()));
		for (Entry<T> entry : this.worstFirst) {
			selected.add(entry.value());
		}
		return selected;
	}

	void addTo(Set<T> destination) {
		for (Entry<T> entry : this.worstFirst) {
			destination.add(entry.value());
		}
	}

	int size() {
		return this.worstFirst.size();
	}

	int allocatedEntryCount() {
		return this.allocatedEntryCount;
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

		private Entry(
				T value,
				boolean targeted,
				boolean critical,
				double distanceSquared,
				int originalIndex
		) {
			this.replace(value, targeted, critical, distanceSquared, originalIndex);
		}

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
