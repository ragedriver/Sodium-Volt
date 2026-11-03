package com.ragedriver.sodiumvolt.client.performance;

/**
 * Reusable fixed-capacity identity table with an allocation-free occupied-slot
 * ledger. A missing entry always means fail open.
 */
final class VapsIdentityDecisionTable<T> {
	static final int UNKNOWN = 0;
	static final int SCANNED = 1;
	static final int SELECTED = 2;
	static final int SATURATED_TOKEN = -1;
	private static final int INSERTED_TOKEN_FLAG = 1 << 30;
	private static final int MAX_PROBES = 64;

	private final Object[] keys;
	private final byte[] decisions;
	private final int[] occupiedSlots;
	private final int mask;
	private int occupiedCount;

	VapsIdentityDecisionTable(int capacity) {
		if (capacity < 2 || capacity >= INSERTED_TOKEN_FLAG
				|| Integer.bitCount(capacity) != 1) {
			throw new IllegalArgumentException(
					"capacity must be a power of two below the token flag"
			);
		}
		this.keys = new Object[capacity];
		this.decisions = new byte[capacity];
		this.occupiedSlots = new int[capacity];
		this.mask = capacity - 1;
	}

	void nextFrame() {
		// Recover safely if extraction failed before its RETURN injection.
		releaseFrame();
	}

	int record(T value) {
		int index = mix(System.identityHashCode(value)) & this.mask;
		for (int probe = 0; probe < MAX_PROBES; probe++) {
			if (this.decisions[index] == UNKNOWN) {
				this.keys[index] = value;
				this.decisions[index] = SCANNED;
				this.occupiedSlots[this.occupiedCount++] = index;
				return index | INSERTED_TOKEN_FLAG;
			}
			if (this.keys[index] == value) {
				return index;
			}
			index = (index + 1) & this.mask;
		}
		return SATURATED_TOKEN;
	}

	void selectRecorded(int token) {
		if (token != SATURATED_TOKEN) {
			this.decisions[token & ~INSERTED_TOKEN_FLAG] = SELECTED;
		}
	}

	int decision(T value) {
		int index = find(value);
		return index < 0 ? UNKNOWN : this.decisions[index];
	}

	boolean shouldRender(T value) {
		int decision = decision(value);
		return decision == UNKNOWN || decision == SELECTED;
	}

	static boolean wasInserted(int token) {
		return token != SATURATED_TOKEN && (token & INSERTED_TOKEN_FLAG) != 0;
	}

	void releaseFrame() {
		for (int occupiedIndex = 0; occupiedIndex < this.occupiedCount; occupiedIndex++) {
			int slot = this.occupiedSlots[occupiedIndex];
			this.keys[slot] = null;
			this.decisions[slot] = UNKNOWN;
		}
		this.occupiedCount = 0;
	}

	void clear() {
		releaseFrame();
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
			if (this.decisions[index] == UNKNOWN) {
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

}
