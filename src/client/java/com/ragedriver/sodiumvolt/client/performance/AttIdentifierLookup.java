package com.ragedriver.sodiumvolt.client.performance;

import net.minecraft.resources.Identifier;

final class AttIdentifierLookup {
	private static final int TABLE_SIZE = 256;
	private static final int TABLE_MASK = TABLE_SIZE - 1;
	private static final int MAX_PROBES = 32;

	private final Identifier[] values = new Identifier[TABLE_SIZE];
	private int size;
	private boolean saturated;

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
		int index = mix(identifier.hashCode()) & TABLE_MASK;
		for (int probe = 0; probe < MAX_PROBES; probe++) {
			Identifier existing = this.values[index];
			if (existing == null) {
				this.values[index] = identifier;
				this.size++;
				return true;
			}
			if (existing.equals(identifier)) {
				return false;
			}
			index = index + 1 & TABLE_MASK;
		}
		this.saturated = true;
		return false;
	}

	boolean contains(Identifier identifier) {
		if (identifier == null) {
			return false;
		}
		int index = mix(identifier.hashCode()) & TABLE_MASK;
		for (int probe = 0; probe < MAX_PROBES; probe++) {
			Identifier existing = this.values[index];
			if (existing == null) {
				return false;
			}
			if (existing.equals(identifier)) {
				return true;
			}
			index = index + 1 & TABLE_MASK;
		}
		return false;
	}

	int size() {
		return this.size;
	}

	boolean isSaturated() {
		return this.saturated;
	}

	private static int mix(int value) {
		value ^= value >>> 16;
		value *= 0x7FEB352D;
		value ^= value >>> 15;
		value *= 0x846CA68B;
		return value ^ value >>> 16;
	}
}
