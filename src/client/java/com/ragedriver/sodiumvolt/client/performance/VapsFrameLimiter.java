package com.ragedriver.sodiumvolt.client.performance;

import java.util.Arrays;

/**
 * Fixed-capacity primitive quota tables. Saturation always fails open, so a
 * crowded or unusual modded particle scene cannot accidentally hide everything.
 */
public final class VapsFrameLimiter {
	private static final int TYPE_TABLE_SIZE = 256;
	private static final int TYPE_TABLE_MASK = TYPE_TABLE_SIZE - 1;
	private static final int CELL_TABLE_SIZE = 8192;
	private static final int CELL_TABLE_MASK = CELL_TABLE_SIZE - 1;
	private static final int MAX_PROBES = 16;

	private final Class<?>[] typeClasses = new Class<?>[TYPE_TABLE_SIZE];
	private final int[] typeCounts = new int[TYPE_TABLE_SIZE];
	private final Class<?>[] cellClasses = new Class<?>[CELL_TABLE_SIZE];
	private final int[] cellX = new int[CELL_TABLE_SIZE];
	private final int[] cellY = new int[CELL_TABLE_SIZE];
	private final int[] cellZ = new int[CELL_TABLE_SIZE];
	private final int[] cellCounts = new int[CELL_TABLE_SIZE];
	private final int[] cellGenerations = new int[CELL_TABLE_SIZE];
	private int generation = 1;
	private int criticalCount;
	private boolean saturated;

	public void reset() {
		Arrays.fill(this.typeClasses, null);
		Arrays.fill(this.typeCounts, 0);
		this.criticalCount = 0;
		this.saturated = false;
		if (++this.generation == 0) {
			Arrays.fill(this.cellGenerations, 0);
			this.generation = 1;
		}
	}

	public boolean tryCritical(int limit) {
		if (this.criticalCount >= Math.max(0, limit)) {
			return false;
		}
		this.criticalCount++;
		return true;
	}

	public boolean tryType(Class<?> type, int limit) {
		int boundedLimit = Math.max(1, limit);
		int index = mix(System.identityHashCode(type)) & TYPE_TABLE_MASK;
		for (int probe = 0; probe < MAX_PROBES; probe++) {
			Class<?> existing = this.typeClasses[index];
			if (existing == null) {
				this.typeClasses[index] = type;
				this.typeCounts[index] = 1;
				return true;
			}
			if (existing == type) {
				if (this.typeCounts[index] >= boundedLimit) {
					return false;
				}
				this.typeCounts[index]++;
				return true;
			}
			index = (index + 1) & TYPE_TABLE_MASK;
		}
		this.saturated = true;
		return true;
	}

	public boolean tryAmbientCell(Class<?> type, int x, int y, int z, int limit) {
		int boundedLimit = Math.max(1, limit);
		int index = cellHash(type, x, y, z) & CELL_TABLE_MASK;
		for (int probe = 0; probe < MAX_PROBES; probe++) {
			if (this.cellGenerations[index] != this.generation) {
				this.cellGenerations[index] = this.generation;
				this.cellClasses[index] = type;
				this.cellX[index] = x;
				this.cellY[index] = y;
				this.cellZ[index] = z;
				this.cellCounts[index] = 1;
				return true;
			}
			if (this.cellClasses[index] == type
					&& this.cellX[index] == x
					&& this.cellY[index] == y
					&& this.cellZ[index] == z) {
				if (this.cellCounts[index] >= boundedLimit) {
					return false;
				}
				this.cellCounts[index]++;
				return true;
			}
			index = (index + 1) & CELL_TABLE_MASK;
		}
		this.saturated = true;
		return true;
	}

	public boolean isSaturated() {
		return this.saturated;
	}

	private static int cellHash(Class<?> type, int x, int y, int z) {
		int hash = System.identityHashCode(type);
		hash = 31 * hash + x;
		hash = 31 * hash + y;
		hash = 31 * hash + z;
		return mix(hash);
	}

	private static int mix(int value) {
		value ^= value >>> 16;
		value *= 0x7FEB352D;
		value ^= value >>> 15;
		value *= 0x846CA68B;
		return value ^ value >>> 16;
	}
}
