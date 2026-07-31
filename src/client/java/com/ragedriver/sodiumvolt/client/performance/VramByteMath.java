package com.ragedriver.sodiumvolt.client.performance;

public final class VramByteMath {
	public static final int MAXIMUM_MIP_LEVELS = 32;

	private VramByteMath() {
	}

	public static long textureBytes(
			int width,
			int height,
			int layers,
			int mipLevels,
			int blockSize
	) {
		if (width <= 0 || height <= 0 || layers <= 0 || mipLevels <= 0
				|| mipLevels > MAXIMUM_MIP_LEVELS || blockSize <= 0) {
			return -1L;
		}
		long total = 0L;
		int mipWidth = width;
		int mipHeight = height;
		for (int level = 0; level < mipLevels; level++) {
			long pixels = saturatingMultiply(mipWidth, mipHeight);
			long layerPixels = saturatingMultiply(pixels, layers);
			long bytes = saturatingMultiply(layerPixels, blockSize);
			total = saturatingAdd(total, bytes);
			mipWidth = Math.max(1, mipWidth >>> 1);
			mipHeight = Math.max(1, mipHeight >>> 1);
		}
		return total;
	}

	public static long mibToBytes(int mib) {
		return mib <= 0 ? 0L : saturatingMultiply(mib, 1_048_576L);
	}

	public static long addHeadroom(long tracked, int percent, long fixedReserve) {
		if (tracked < 0L || percent < 0 || fixedReserve < 0L) {
			return -1L;
		}
		long percentage = saturatingMultiply(tracked, percent) / 100L;
		return saturatingAdd(saturatingAdd(tracked, percentage), fixedReserve);
	}

	public static long saturatingAdd(long left, long right) {
		if (left < 0L || right < 0L || left > Long.MAX_VALUE - right) {
			return Long.MAX_VALUE;
		}
		return left + right;
	}

	public static long saturatingMultiply(long left, long right) {
		if (left < 0L || right < 0L) {
			return Long.MAX_VALUE;
		}
		if (left == 0L || right == 0L) {
			return 0L;
		}
		return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
	}
}
