package com.ragedriver.sodiumvolt.client.inspector;

public final class FrameTimeWindow {
	private static final double NANOS_PER_MILLISECOND = 1_000_000.0D;
	private static final long MINIMUM_FRAME_NANOS = 100_000L;
	private static final long MAXIMUM_FRAME_NANOS = 1_000_000_000L;

	private final long[] samples;
	private int writeIndex;
	private int size;

	public FrameTimeWindow(int maximumSamples) {
		if (maximumSamples < 1) {
			throw new IllegalArgumentException("maximumSamples must be positive");
		}
		this.samples = new long[maximumSamples];
	}

	public boolean addNanos(long nanoseconds, int activeWindow) {
		if (nanoseconds < MINIMUM_FRAME_NANOS || nanoseconds > MAXIMUM_FRAME_NANOS) {
			return false;
		}
		this.samples[this.writeIndex] = nanoseconds;
		this.writeIndex = (this.writeIndex + 1) % this.samples.length;
		this.size = Math.min(this.size + 1, Math.min(this.samples.length, Math.max(1, activeWindow)));
		return true;
	}

	public Statistics statistics(int activeWindow, long[] sortingBuffer) {
		int count = Math.min(this.size, Math.min(activeWindow, this.samples.length));
		if (count == 0) {
			return Statistics.EMPTY;
		}
		if (sortingBuffer.length < count) {
			throw new IllegalArgumentException("sortingBuffer is smaller than the active sample count");
		}

		long sum = 0L;
		int start = Math.floorMod(this.writeIndex - count, this.samples.length);
		for (int index = 0; index < count; index++) {
			long value = this.samples[(start + index) % this.samples.length];
			sortingBuffer[index] = value;
			sum = saturatingAdd(sum, value);
		}
		return new Statistics(
				count,
				(sum / (double) count) / NANOS_PER_MILLISECOND,
				percentileUnsorted(sortingBuffer, count, 0.50D),
				percentileUnsorted(sortingBuffer, count, 0.95D),
				percentileUnsorted(sortingBuffer, count, 0.99D),
				percentileUnsorted(sortingBuffer, count, 0.995D)
		);
	}

	public void clear() {
		this.writeIndex = 0;
		this.size = 0;
	}

	public int size() {
		return this.size;
	}

	static double percentile(long[] sortedNanos, int count, double percentile) {
		if (count <= 0) {
			return 0.0D;
		}
		int rank = (int) Math.ceil(Math.clamp(percentile, 0.0D, 1.0D) * count);
		int index = Math.clamp(rank - 1, 0, count - 1);
		return sortedNanos[index] / NANOS_PER_MILLISECOND;
	}

	private static double percentileUnsorted(long[] values, int count, double percentile) {
		int rank = (int) Math.ceil(Math.clamp(percentile, 0.0D, 1.0D) * count);
		return select(values, 0, count - 1, Math.clamp(rank - 1, 0, count - 1))
				/ NANOS_PER_MILLISECOND;
	}

	private static long select(long[] values, int left, int right, int target) {
		while (left < right) {
			long pivot = values[(left + right) >>> 1];
			int lower = left;
			int cursor = left;
			int upper = right;
			while (cursor <= upper) {
				long value = values[cursor];
				if (value < pivot) {
					swap(values, lower++, cursor++);
				} else if (value > pivot) {
					swap(values, cursor, upper--);
				} else {
					cursor++;
				}
			}
			if (target < lower) {
				right = lower - 1;
			} else if (target > upper) {
				left = upper + 1;
			} else {
				return values[target];
			}
		}
		return values[left];
	}

	private static void swap(long[] values, int first, int second) {
		long value = values[first];
		values[first] = values[second];
		values[second] = value;
	}

	private static long saturatingAdd(long first, long second) {
		return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
	}

	public record Statistics(
			int sampleCount,
			double averageMs,
			double medianMs,
			double p95Ms,
			double p99Ms,
			double p995Ms
	) {
		private static final Statistics EMPTY = new Statistics(0, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
	}
}
