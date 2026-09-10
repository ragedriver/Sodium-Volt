package com.ragedriver.sodiumvolt.client.performance;

import net.minecraft.resources.Identifier;

/** Bounded, sorted exact-ID lookup; hash collisions cannot affect scheduling. */
final class AttIdentifierLookup {
	private static final int CAPACITY = AttExemptionParsing.MAX_USER_ENTRIES
			+ AttExemptionParsing.MAX_RESOURCE_ENTRIES;

	private final Identifier[] values = new Identifier[CAPACITY];
	private int size;

	boolean add(String value) {
		if (!AttExemptionParsing.isValidIdentifier(value)) {
			return false;
		}
		Identifier identifier;
		try {
			identifier = Identifier.parse(value);
		} catch (RuntimeException exception) {
			return false;
		}
		int index = find(identifier);
		if (index >= 0 || this.size == this.values.length) {
			return false;
		}
		index = -index - 1;
		System.arraycopy(this.values, index, this.values, index + 1, this.size - index);
		this.values[index] = identifier;
		this.size++;
		return true;
	}

	boolean contains(Identifier identifier) {
		return identifier != null && find(identifier) >= 0;
	}

	int size() {
		return this.size;
	}

	private int find(Identifier identifier) {
		int low = 0;
		int high = this.size - 1;
		while (low <= high) {
			int middle = (low + high) >>> 1;
			int comparison = this.values[middle].compareTo(identifier);
			if (comparison < 0) {
				low = middle + 1;
			} else if (comparison > 0) {
				high = middle - 1;
			} else {
				return middle;
			}
		}
		return -low - 1;
	}
}
