package com.ragedriver.sodiumvolt.client.performance;

/**
 * Bounded, reusable handoff containing the VAPS-approved particle candidates
 * that Volt Guard would otherwise rediscover by walking the raw particle
 * queues. A handoff is usable only after {@link #complete()}.
 */
public final class ParticleEligibilityHandoff<T> {
	private final Object[] candidates;
	private final int[] originalIndices;
	private final boolean[] special;
	private boolean recording;
	private boolean complete;
	private int candidateCount;
	private int specialCount;
	private int rawSourceVisits;

	ParticleEligibilityHandoff(int capacity) {
		if (capacity < 1) {
			throw new IllegalArgumentException("capacity must be positive");
		}
		this.candidates = new Object[capacity];
		this.originalIndices = new int[capacity];
		this.special = new boolean[capacity];
	}

	void begin() {
		this.clearReferences();
		this.recording = true;
		this.complete = false;
		this.rawSourceVisits = 0;
	}

	boolean add(T candidate, boolean specialCandidate, int originalIndex) {
		if (!this.recording || this.candidateCount >= this.candidates.length) {
			this.abort();
			return false;
		}
		int index = this.candidateCount++;
		this.candidates[index] = candidate;
		this.originalIndices[index] = originalIndex;
		this.special[index] = specialCandidate;
		if (specialCandidate) {
			this.specialCount++;
		}
		return true;
	}

	void complete(int rawSourceVisits) {
		if (this.recording) {
			this.recording = false;
			this.complete = true;
			this.rawSourceVisits = Math.max(0, rawSourceVisits);
		}
	}

	void abort() {
		this.recording = false;
		this.complete = false;
		this.rawSourceVisits = 0;
		this.clearReferences();
	}

	public boolean isComplete() {
		return this.complete;
	}

	boolean isRecording() {
		return this.recording;
	}

	public int candidateCount() {
		return this.complete ? this.candidateCount : 0;
	}

	public int specialCount() {
		return this.complete ? this.specialCount : 0;
	}

	@SuppressWarnings("unchecked")
	public T candidateAt(int index) {
		this.checkReadableIndex(index);
		return (T) this.candidates[index];
	}

	public int originalIndexAt(int index) {
		this.checkReadableIndex(index);
		return this.originalIndices[index];
	}

	public boolean isSpecial(int index) {
		this.checkReadableIndex(index);
		return this.special[index];
	}

	int rawSourceVisitsForTesting() {
		return this.rawSourceVisits;
	}

	boolean retainsReferenceForTesting(T candidate) {
		for (int index = 0; index < this.candidateCount; index++) {
			if (this.candidates[index] == candidate) {
				return true;
			}
		}
		return false;
	}

	private void checkReadableIndex(int index) {
		if (!this.complete || index < 0 || index >= this.candidateCount) {
			throw new IndexOutOfBoundsException(index);
		}
	}

	private void clearReferences() {
		for (int index = 0; index < this.candidateCount; index++) {
			this.candidates[index] = null;
			this.originalIndices[index] = 0;
			this.special[index] = false;
		}
		this.candidateCount = 0;
		this.specialCount = 0;
	}
}
