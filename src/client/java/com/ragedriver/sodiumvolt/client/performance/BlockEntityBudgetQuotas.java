package com.ragedriver.sodiumvolt.client.performance;

import java.util.Arrays;

final class BlockEntityBudgetQuotas {
	private static final int TYPE_TABLE_SIZE = 512;
	private static final int TYPE_TABLE_MASK = TYPE_TABLE_SIZE - 1;
	private static final int MAX_PROBES = 32;
	private static final int BREAKING_RESERVE = 16;

	private final Object[] types = new Object[TYPE_TABLE_SIZE];
	private final int[] counts = new int[TYPE_TABLE_SIZE];
	private int selected;
	private int breakingSelected;
	private int absoluteSelected;
	private boolean saturated;

	void reset() {
		Arrays.fill(this.types, null);
		this.selected = 0;
		this.breakingSelected = 0;
		this.absoluteSelected = 0;
		this.saturated = false;
	}

	Decision trySelect(Object type, int globalBudget, boolean perTypeEnabled, int perTypeLimit, Priority priority) {
		if (priority == Priority.TARGET_OR_RECENT) {
			if (this.absoluteSelected < 2) {
				this.absoluteSelected++;
				return Decision.SELECTED_ABSOLUTE;
			}
			priority = Priority.NEAR;
		}
		if (this.selected >= Math.max(1, globalBudget)) {
			return Decision.GLOBAL_LIMIT;
		}
		if (priority == Priority.BREAKING && this.breakingSelected < BREAKING_RESERVE) {
			this.breakingSelected++;
			this.selected++;
			return Decision.SELECTED;
		}
		if (perTypeEnabled && !claimType(type, Math.max(1, perTypeLimit))) {
			return Decision.PER_TYPE_LIMIT;
		}
		this.selected++;
		return Decision.SELECTED;
	}

	boolean isSaturated() {
		return this.saturated;
	}

	int selected() {
		return this.selected;
	}

	private boolean claimType(Object type, int limit) {
		int index = mix(System.identityHashCode(type)) & TYPE_TABLE_MASK;
		for (int probe = 0; probe < MAX_PROBES; probe++) {
			Object existing = this.types[index];
			if (existing == null) {
				this.types[index] = type;
				this.counts[index] = 1;
				return true;
			}
			if (existing == type) {
				if (this.counts[index] >= limit) {
					return false;
				}
				this.counts[index]++;
				return true;
			}
			index = (index + 1) & TYPE_TABLE_MASK;
		}
		this.saturated = true;
		return true;
	}

	private static int mix(int value) {
		value ^= value >>> 16;
		value *= 0x7FEB352D;
		value ^= value >>> 15;
		value *= 0x846CA68B;
		return value ^ value >>> 16;
	}

	enum Priority {
		TARGET_OR_RECENT,
		BREAKING,
		NEAR,
		MEDIUM,
		FAR
	}

	enum Decision {
		SELECTED,
		SELECTED_ABSOLUTE,
		GLOBAL_LIMIT,
		PER_TYPE_LIMIT
	}
}
