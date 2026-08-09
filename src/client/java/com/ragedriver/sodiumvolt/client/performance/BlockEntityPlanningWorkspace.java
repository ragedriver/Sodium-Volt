package com.ragedriver.sodiumvolt.client.performance;

import java.util.Arrays;

/**
 * Fixed reusable storage for one raw-prefix classification pass. Per-priority
 * linked buckets preserve extraction order without allocating candidate nodes.
 */
final class BlockEntityPlanningWorkspace<T> {
	private final Object[] values;
	private final byte[] priorities;
	private final int[] nextInPriority;
	private final int[] firstByPriority;
	private final int[] lastByPriority;
	private int size;

	BlockEntityPlanningWorkspace(int capacity, int priorityCount) {
		if (capacity < 1 || priorityCount < 1 || priorityCount > Byte.MAX_VALUE) {
			throw new IllegalArgumentException("capacity and priority count must be positive");
		}
		this.values = new Object[capacity];
		this.priorities = new byte[capacity];
		this.nextInPriority = new int[capacity];
		this.firstByPriority = new int[priorityCount];
		this.lastByPriority = new int[priorityCount];
		this.resetBuckets();
	}

	void begin() {
		this.clear();
	}

	boolean add(T value, int priority) {
		if (this.size >= this.values.length
				|| priority < 0
				|| priority >= this.firstByPriority.length) {
			return false;
		}
		int index = this.size++;
		this.values[index] = value;
		this.priorities[index] = (byte) priority;
		this.nextInPriority[index] = -1;
		int previous = this.lastByPriority[priority];
		if (previous < 0) {
			this.firstByPriority[priority] = index;
		} else {
			this.nextInPriority[previous] = index;
		}
		this.lastByPriority[priority] = index;
		return true;
	}

	int size() {
		return this.size;
	}

	int firstInPriority(int priority) {
		return this.firstByPriority[priority];
	}

	int nextInPriority(int index) {
		return this.nextInPriority[index];
	}

	int priorityAt(int index) {
		return Byte.toUnsignedInt(this.priorities[index]);
	}

	@SuppressWarnings("unchecked")
	T valueAt(int index) {
		return (T) this.values[index];
	}

	void clear() {
		for (int index = 0; index < this.size; index++) {
			this.values[index] = null;
			this.priorities[index] = 0;
			this.nextInPriority[index] = -1;
		}
		this.size = 0;
		this.resetBuckets();
	}

	boolean retainsReferenceForTesting(T value) {
		for (int index = 0; index < this.size; index++) {
			if (this.values[index] == value) {
				return true;
			}
		}
		return false;
	}

	private void resetBuckets() {
		Arrays.fill(this.firstByPriority, -1);
		Arrays.fill(this.lastByPriority, -1);
	}
}
