package com.ragedriver.sodiumvolt.client.guard;

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
		if (specialCandidate) {
			this.special.offer(value, false, true, distanceSquared, originalIndex);
		}
		this.spill.offer(value, false, critical, distanceSquared, originalIndex);
	}

	/**
	 * Finalizes from bounded spill storage when possible. A false result leaves
	 * only the reserved identities selected and prepares the general selector
	 * for an exact replay of non-reserved candidates.
	 */
	boolean finishFromSpill(Set<T> selected, int specialCandidateCount) {
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

	void abort() {
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
	}

	boolean retainsReferenceForTesting(T value) {
		return this.special.retainsValueForTesting(value)
				|| this.spill.retainsValueForTesting(value);
	}

	int allocatedEntriesForTesting() {
		return this.special.allocatedEntryCount()
				+ this.spill.allocatedEntryCount();
	}
}
