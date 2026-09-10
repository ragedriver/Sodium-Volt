package com.ragedriver.sodiumvolt.client.guard;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Reusable reserved/general selector. Candidates enter the reserve and a
 * budget-sized general spill heap in one traversal. The spill is sufficient in
 * normal identity-unique input; an adversarial duplicate identity can request
 * one exact general-only replay instead of changing selection semantics.
 */
final class SpillAwareParticleSelector<T> {
	private final BoundedTopK<T> special = new BoundedTopK<>();
	private final BoundedTopK<T> spill = new BoundedTopK<>();
	private final Set<T> identityScratch =
			Collections.newSetFromMap(new IdentityHashMap<>());
	private Object[] unrankedValues = new Object[0];
	private int unrankedSize;
	private int budget;
	private boolean prioritizeDistance;
	private boolean preserveCritical;

	void begin(int budget, boolean prioritizeDistance, boolean preserveCritical) {
		this.abort();
		this.budget = Math.max(0, budget);
		this.prioritizeDistance = prioritizeDistance;
		this.preserveCritical = preserveCritical;
		int maximumReserve = ParticleBudgetPlan.maximumSpecialReserveCapacity(
				this.budget,
				preserveCritical
		);
		this.special.reset(maximumReserve, prioritizeDistance);
		this.spill.reset(this.budget, prioritizeDistance);
	}

	void offer(
			T value,
			boolean specialCandidate,
			boolean critical,
			double distanceSquared,
			int originalIndex
	) {
		// Callers supply candidates in ascending original-index order. Count
		// occurrences, not unique identities, to preserve legacy underfill.
		if (!this.requiresRanking()) {
			if (this.unrankedSize < this.budget) {
				if (this.unrankedSize == this.unrankedValues.length) {
					int capacity = (int) Math.min(this.budget, Math.max(8L, this.unrankedSize * 2L));
					this.unrankedValues = Arrays.copyOf(this.unrankedValues, capacity);
				}
				this.unrankedValues[this.unrankedSize++] = value;
			}
			return;
		}
		if (specialCandidate) {
			this.special.offer(value, false, true, distanceSquared, originalIndex);
		}
		this.spill.offer(value, false, critical, distanceSquared, originalIndex);
	}

	boolean requiresRanking() {
		return this.prioritizeDistance || this.preserveCritical;
	}

	/**
	 * Finalizes from bounded spill storage when possible. A false result leaves
	 * only the reserved identities selected and prepares the general selector
	 * for an exact replay of non-reserved candidates.
	 */
	@SuppressWarnings("unchecked")
	boolean finishFromSpill(Set<T> selected, int specialCandidateCount) {
		if (!this.requiresRanking()) {
			for (int index = 0; index < this.unrankedSize; index++) {
				selected.add((T) this.unrankedValues[index]);
			}
			this.clearUnranked();
			return true;
		}
		this.special.addTo(selected);
		this.special.clear();
		ParticleBudgetPlan plan = ParticleBudgetPlan.create(
				this.budget,
				specialCandidateCount,
				this.preserveCritical
		);
		int remaining = plan.remainingCapacityAfter(selected.size());
		if (remaining == 0) {
			this.spill.clear();
			return true;
		}
		if (this.spill.countExcluding(selected) < remaining
				|| this.spill.hasDuplicateExcluding(selected, this.identityScratch)) {
			this.spill.reset(remaining, this.prioritizeDistance);
			return false;
		}
		this.spill.removeExcludedAndTrim(selected, remaining);
		this.spill.addTo(selected);
		this.spill.clear();
		return true;
	}

	void offerReplay(
			T value,
			boolean critical,
			double distanceSquared,
			int originalIndex,
			Set<T> selected
	) {
		if (!selected.contains(value)) {
			this.spill.offer(value, false, critical, distanceSquared, originalIndex);
		}
	}

	void completeReplay(Set<T> selected) {
		this.spill.addTo(selected);
		this.spill.clear();
	}

	private void clearUnranked() {
		Arrays.fill(this.unrankedValues, 0, this.unrankedSize, null);
		this.unrankedSize = 0;
	}

	void abort() {
		this.clearUnranked();
		this.special.clear();
		this.spill.clear();
		this.identityScratch.clear();
		this.budget = 0;
		this.prioritizeDistance = false;
		this.preserveCritical = false;
	}

	void releaseStorage() {
		this.abort();
		this.special.releaseStorage();
		this.spill.releaseStorage();
		this.unrankedValues = new Object[0];
	}

	boolean retainsReferenceForTesting(T value) {
		for (Object retained : this.unrankedValues) {
			if (retained == value) {
				return true;
			}
		}
		return this.special.retainsValueForTesting(value)
				|| this.spill.retainsValueForTesting(value);
	}

	int allocatedEntriesForTesting() {
		return this.special.allocatedEntryCount()
				+ this.spill.allocatedEntryCount()
				+ this.unrankedValues.length;
	}
}
