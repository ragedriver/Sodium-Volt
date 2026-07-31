package com.ragedriver.sodiumvolt.client.resourcepack;

public record ResourcePackShieldPolicy(
		boolean detectUnsafePaths,
		boolean blockCoreShaderOverrides,
		boolean rejectViolations,
		int maximumEntries,
		long maximumArchiveBytes,
		long maximumSingleResourceBytes,
		long maximumTotalResourceBytes,
		int maximumCompressionRatio,
		int maximumPngDimension,
		long maximumPngPixels,
		int maximumJsonDepth,
		int maximumPathLength,
		int maximumPathDepth,
		long maximumScanNanos
) {
	public ResourcePackShieldPolicy {
		long hardTotalMaximum = 8L * 1024L * 1024L * 1024L;
		maximumEntries = clamp(maximumEntries, 1, 100_000);
		maximumArchiveBytes = clamp(maximumArchiveBytes, 1L, 4L * 1024L * 1024L * 1024L);
		maximumSingleResourceBytes = clamp(
				maximumSingleResourceBytes,
				1L,
				hardTotalMaximum
		);
		maximumTotalResourceBytes = clamp(
				maximumTotalResourceBytes,
				maximumSingleResourceBytes,
				hardTotalMaximum
		);
		maximumCompressionRatio = clamp(maximumCompressionRatio, 2, 2_000);
		maximumPngDimension = clamp(maximumPngDimension, 256, 65_536);
		maximumPngPixels = clamp(maximumPngPixels, 1L, 1L << 32);
		maximumJsonDepth = clamp(maximumJsonDepth, 8, 1_024);
		maximumPathLength = clamp(maximumPathLength, 64, 4_096);
		maximumPathDepth = clamp(maximumPathDepth, 4, 256);
		maximumScanNanos = clamp(
				maximumScanNanos, 1L, 10_000_000_000L
		);
	}

	public static ResourcePackShieldPolicy defaults() {
		return new ResourcePackShieldPolicy(
				true,
				true,
				true,
				16_384,
				256L * 1024L * 1024L,
				64L * 1024L * 1024L,
				512L * 1024L * 1024L,
				200,
				16_384,
				128L * 1024L * 1024L,
				128,
				512,
				32,
				2_000_000_000L
		);
	}

	public ResourcePackShieldPolicy withMaximumScanNanos(long maximumNanos) {
		return new ResourcePackShieldPolicy(
				this.detectUnsafePaths,
				this.blockCoreShaderOverrides,
				this.rejectViolations,
				this.maximumEntries,
				this.maximumArchiveBytes,
				this.maximumSingleResourceBytes,
				this.maximumTotalResourceBytes,
				this.maximumCompressionRatio,
				this.maximumPngDimension,
				this.maximumPngPixels,
				this.maximumJsonDepth,
				this.maximumPathLength,
				this.maximumPathDepth,
				Math.min(this.maximumScanNanos, maximumNanos)
		);
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static long clamp(long value, long minimum, long maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
