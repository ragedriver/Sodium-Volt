package com.ragedriver.sodiumvolt.client.performance;

import java.util.Arrays;

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
		Arrays.sort(sortingBuffer, 0, count);
		int percentileIndex = Math.max(0, (int) Math.ceil(count * 0.95D) - 1);
		return sortingBuffer[percentileIndex] / 1_000_000.0D;
	}

	void clear() {
		this.nextIndex = 0;
		this.size = 0;
	}

	private int clampWindow(int requestedWindow) {
		return Math.max(1, Math.min(this.samples.length, requestedWindow));
	}
}
