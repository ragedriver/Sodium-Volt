package com.ragedriver.sodiumvolt.client.performance;

import java.util.Arrays;

/** Fixed-capacity identity index for allocation-free hot-path counter lookup. */
final class BoundedIdentityIndex<T> {
	private static final int MAXIMUM_LOAD_DENOMINATOR = 2;

	private final Object[] tableKeys;
	private final int[] tableIndices;
	private final Object[] indexedKeys;
	private final int mask;
	private int size;

	BoundedIdentityIndex(int maximumEntries) {
		if (maximumEntries < 1 || maximumEntries > (1 << 29)) {
			throw new IllegalArgumentException("maximumEntries is outside the bounded table range");
		}
		int tableCapacity = 2;
		while (tableCapacity < maximumEntries * MAXIMUM_LOAD_DENOMINATOR) {
			tableCapacity <<= 1;
		}
		this.tableKeys = new Object[tableCapacity];
		this.tableIndices = new int[tableCapacity];
		this.indexedKeys = new Object[maximumEntries];
		this.mask = tableCapacity - 1;
	}

	int index(T key) {
		if (key == null) {
			return -1;
		}
		int slot = mix(System.identityHashCode(key)) & this.mask;
		for (int probe = 0; probe < this.tableKeys.length; probe++) {
			Object existing = this.tableKeys[slot];
			if (existing == key) {
				return this.tableIndices[slot];
			}
			if (existing == null) {
				if (this.size >= this.indexedKeys.length) {
					return -1;
				}
				int index = this.size++;
				this.tableKeys[slot] = key;
				this.tableIndices[slot] = index;
				this.indexedKeys[index] = key;
				return index;
			}
			slot = (slot + 1) & this.mask;
		}
		return -1;
	}

	@SuppressWarnings("unchecked")
	T keyAt(int index) {
		return index < 0 || index >= this.size ? null : (T) this.indexedKeys[index];
	}

	int size() {
		return this.size;
	}

	void clear() {
		Arrays.fill(this.tableKeys, null);
		Arrays.fill(this.indexedKeys, 0, this.size, null);
		this.size = 0;
	}

	boolean retainsReferenceForTesting(T key) {
		for (int index = 0; index < this.size; index++) {
			if (this.indexedKeys[index] == key) {
				return true;
			}
		}
		return false;
	}

	private static int mix(int value) {
		value ^= value >>> 16;
		value *= 0x7FEB352D;
		value ^= value >>> 15;
		value *= 0x846CA68B;
		return value ^ value >>> 16;
	}
}
