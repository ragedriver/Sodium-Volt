package com.ragedriver.sodiumvolt.client.performance;

/**
 * Reusable fixed-capacity identity table with an allocation-free occupied-slot
 * ledger. A missing entry always means fail open.
 */
final class VapsIdentityDecisionTable<T> {
	private static final byte SCANNED = 1;
	private static final byte SELECTED = 2;
	private static final int MAX_PROBES = 64;

	private final Object[] keys;
	private final byte[] decisions;
	private final int[] generations;
	private final int[] occupiedSlots;
	private final int mask;
	private int generation = 1;
	private int occupiedCount;

	VapsIdentityDecisionTable(int capacity) {
		if (capacity < 2 || Integer.bitCount(capacity) != 1) {
			throw new IllegalArgumentException("capacity must be a power of two");
		}
		this.keys = new Object[capacity];
		this.decisions = new byte[capacity];
		this.generations = new int[capacity];
		this.occupiedSlots = new int[capacity];
		this.mask = capacity - 1;
	}

	void nextFrame() {
		// Recover safely if extraction failed before its RETURN injection.
		releaseFrame();
		if (++this.generation == 0) {
			this.generation = 1;
		}
	}

	boolean addScanned(T value) {
		return this.addScannedResult(value) != AddResult.SATURATED;
	}

	AddResult addScannedResult(T value) {
		int index = mix(System.identityHashCode(value)) & this.mask;
		for (int probe = 0; probe < MAX_PROBES; probe++) {
			if (this.generations[index] != this.generation) {
				this.generations[index] = this.generation;
				this.keys[index] = value;
				this.decisions[index] = SCANNED;
				this.occupiedSlots[this.occupiedCount++] = index;
				return AddResult.INSERTED;
			}
			if (this.keys[index] == value) {
				return AddResult.EXISTING;
			}
			index = (index + 1) & this.mask;
		}
		return AddResult.SATURATED;
	}

	void select(T value) {
		int index = find(value);
		if (index >= 0) {
			this.decisions[index] = SELECTED;
		}
	}

	boolean isScanned(T value) {
		return find(value) >= 0;
	}

	boolean isSelected(T value) {
		int index = find(value);
		return index >= 0 && this.decisions[index] == SELECTED;
	}

	void releaseFrame() {
		for (int occupiedIndex = 0; occupiedIndex < this.occupiedCount; occupiedIndex++) {
			int slot = this.occupiedSlots[occupiedIndex];
			this.keys[slot] = null;
			this.decisions[slot] = 0;
			this.generations[slot] = 0;
			this.occupiedSlots[occupiedIndex] = 0;
		}
		this.occupiedCount = 0;
	}

	void clear() {
		releaseFrame();
		this.generation = 1;
	}

	int occupiedCountForTesting() {
		return this.occupiedCount;
	}

	boolean retainsReferenceForTesting(T value) {
		for (int occupiedIndex = 0; occupiedIndex < this.occupiedCount; occupiedIndex++) {
			if (this.keys[this.occupiedSlots[occupiedIndex]] == value) {
				return true;
			}
		}
		return false;
	}

	private int find(T value) {
		int index = mix(System.identityHashCode(value)) & this.mask;
		for (int probe = 0; probe < MAX_PROBES; probe++) {
			if (this.generations[index] != this.generation) {
				return -1;
			}
			if (this.keys[index] == value) {
				return index;
			}
			index = (index + 1) & this.mask;
		}
		return -1;
	}

	private static int mix(int value) {
		value ^= value >>> 16;
		value *= 0x7FEB352D;
		value ^= value >>> 15;
		value *= 0x846CA68B;
		return value ^ value >>> 16;
	}

	enum AddResult {
		INSERTED,
		EXISTING,
		SATURATED
	}
}
