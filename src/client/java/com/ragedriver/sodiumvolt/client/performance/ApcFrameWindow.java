package com.ragedriver.sodiumvolt.client.performance;

final class ApcFrameWindow {
	private static final long MINIMUM_FRAME_NANOS = 100_000L;
	private static final long MAXIMUM_FRAME_NANOS = 1_000_000_000L;

	private final long[] samples;
	private int nextIndex;
	private int size;

	ApcFrameWindow(int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be positive");
		}
		this.samples = new long[capacity];
	}

	void addNanos(long frameNanos) {
		if (frameNanos < MINIMUM_FRAME_NANOS || frameNanos > MAXIMUM_FRAME_NANOS) {
			return;
		}
		this.samples[this.nextIndex] = frameNanos;
		this.nextIndex = (this.nextIndex + 1) % this.samples.length;
		if (this.size < this.samples.length) {
			this.size++;
		}
	}

	int size(int requestedWindow) {
		return Math.min(this.size, clampWindow(requestedWindow));
	}

	double p95Milliseconds(int requestedWindow, long[] sortingBuffer) {
		int count = size(requestedWindow);
		if (count == 0) {
			return 0.0D;
		}
		if (sortingBuffer.length < count) {
			throw new IllegalArgumentException("sorting buffer is too small");
		}
		int first = Math.floorMod(this.nextIndex - count, this.samples.length);
		for (int index = 0; index < count; index++) {
			sortingBuffer[index] = this.samples[(first + index) % this.samples.length];
		}
		int percentileIndex = Math.max(0, (int) Math.ceil(count * 0.95D) - 1);
		return select(sortingBuffer, 0, count, percentileIndex) / 1_000_000.0D;
	}

	void clear() {
		this.nextIndex = 0;
		this.size = 0;
	}

	private int clampWindow(int requestedWindow) {
		return Math.max(1, Math.min(this.samples.length, requestedWindow));
	}

	/** Exact bounded linear-time selection using median-of-medians pivots. */
	private static long select(long[] values, int from, int to, int target) {
		int left = from;
		int right = to;
		while (true) {
			int length = right - left;
			if (length <= 5) {
				insertionSort(values, left, right);
				return values[target];
			}
			long pivot = medianOfMedians(values, left, right);
			int less = left;
			int scan = left;
			int greater = right;
			while (scan < greater) {
				long value = values[scan];
				if (value < pivot) {
					swap(values, less++, scan++);
				} else if (value > pivot) {
					swap(values, scan, --greater);
				} else {
					scan++;
				}
			}
			if (target < less) {
				right = less;
			} else if (target >= greater) {
				left = greater;
			} else {
				return pivot;
			}
		}
	}

	private static long medianOfMedians(long[] values, int from, int to) {
		int medians = 0;
		for (int group = from; group < to; group += 5) {
			int groupEnd = Math.min(group + 5, to);
			insertionSort(values, group, groupEnd);
			int median = group + (groupEnd - group - 1) / 2;
			swap(values, from + medians, median);
			medians++;
		}
		int medianTarget = from + (medians - 1) / 2;
		return select(values, from, from + medians, medianTarget);
	}

	private static void insertionSort(long[] values, int from, int to) {
		for (int index = from + 1; index < to; index++) {
			long value = values[index];
			int insertion = index;
			while (insertion > from && values[insertion - 1] > value) {
				values[insertion] = values[insertion - 1];
				insertion--;
			}
			values[insertion] = value;
		}
	}

	private static void swap(long[] values, int first, int second) {
		long value = values[first];
		values[first] = values[second];
		values[second] = value;
	}
}
