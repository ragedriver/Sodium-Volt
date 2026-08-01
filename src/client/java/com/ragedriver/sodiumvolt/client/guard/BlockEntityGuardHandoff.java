package com.ragedriver.sodiumvolt.client.guard;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Reusable producer/consumer handoff which lets an upstream block-entity
 * budgeting pass rank Volt Guard candidates while it is already visiting the
 * surviving states. The consumer therefore only needs the final removal pass.
 */
final class BlockEntityGuardHandoff<T> {
	private final BoundedTopK<T> selection = new BoundedTopK<>();
	private final Set<T> selected = Collections.newSetFromMap(new IdentityHashMap<>());
	private int budget;
	private int offeredCount;
	private boolean prioritized;
	private boolean rankingRequired;
	private boolean recording;
	private boolean complete;

	void begin(int sourceUpperBound, int budget, boolean prioritizeDistance, boolean preserveCritical) {
		this.clear();
		this.budget = Math.max(0, budget);
		this.prioritized = prioritizeDistance || preserveCritical;
		this.rankingRequired = this.prioritized && sourceUpperBound > this.budget;
		this.recording = true;
		if (this.rankingRequired) {
			this.selection.reset(this.budget, prioritizeDistance);
		}
	}

	void offer(
			T value,
			boolean targeted,
			boolean critical,
			double distanceSquared,
			int originalIndex
	) {
		if (!this.recording) {
			return;
		}
		this.recordOffer();
		if (this.rankingRequired) {
			this.selection.offer(value, targeted, critical, distanceSquared, originalIndex);
		}
	}

	void offerUnranked() {
		if (this.recording) {
			this.recordOffer();
		}
	}

	boolean requiresRanking() {
		return this.recording && this.rankingRequired;
	}

	void complete() {
		if (!this.recording) {
			return;
		}
		this.recording = false;
		try {
			if (this.rankingRequired && this.offeredCount > this.budget) {
				this.selection.addTo(this.selected);
			}
		} finally {
			this.selection.clear();
		}
		this.complete = true;
	}

	boolean isCompleteForSize(int currentSize) {
		return this.complete && currentSize == this.offeredCount;
	}

	int applyTo(List<T> states) {
		if (!this.isCompleteForSize(states.size())) {
			this.clear();
			throw new IllegalStateException("incomplete or mismatched block-entity handoff");
		}
		int originalSize = states.size();
		try {
			if (originalSize > this.budget) {
				if (!this.prioritized) {
					states.subList(this.budget, originalSize).clear();
				} else {
					Iterator<T> iterator = states.iterator();
					while (iterator.hasNext()) {
						if (!this.selected.contains(iterator.next())) {
							iterator.remove();
						}
					}
				}
			}
			return originalSize - states.size();
		} finally {
			this.clear();
		}
	}

	void abort() {
		this.clear();
	}

	int allocatedSelectionEntriesForTesting() {
		return this.selection.allocatedEntryCount();
	}

	boolean retainsReferenceForTesting(T value) {
		return this.selected.contains(value) || this.selection.retainsValueForTesting(value);
	}

	private void recordOffer() {
		if (this.offeredCount < Integer.MAX_VALUE) {
			this.offeredCount++;
		}
	}

	private void clear() {
		this.selection.clear();
		this.selected.clear();
		this.budget = 0;
		this.offeredCount = 0;
		this.prioritized = false;
		this.rankingRequired = false;
		this.recording = false;
		this.complete = false;
	}
}
