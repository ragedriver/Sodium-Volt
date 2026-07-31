package com.ragedriver.sodiumvolt.client.resourcepack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipPackScanner {
	private static final int END_HEADER_LENGTH = 22;
	private static final int MAXIMUM_END_SEARCH = 65_535 + END_HEADER_LENGTH;
	private static final int CENTRAL_HEADER_LENGTH = 46;
	private static final int END_SIGNATURE = 0x06054B50;
	private static final int CENTRAL_SIGNATURE = 0x02014B50;

	private ZipPackScanner() {
	}

	public static ShieldScanResult scan(
			Path archive,
			ResourcePackShieldPolicy policy
	) throws IOException {
		return scan(archive, policy, ShieldOverlayPlan.EMPTY);
	}

	public static ShieldScanResult scan(
			Path archive,
			ResourcePackShieldPolicy policy,
			ShieldOverlayPlan overlays
	) throws IOException {
		return scan(
				archive,
				policy,
				overlays,
				deadline(policy.maximumScanNanos())
		);
	}

	static ShieldScanResult scan(
			Path archive,
			ResourcePackShieldPolicy policy,
			ShieldOverlayPlan overlays,
			long deadline
	) throws IOException {
		if (Files.isSymbolicLink(archive)) {
			return failed(ShieldReason.SYMLINK, 0, 0L, 0L);
		}
		BasicFileAttributes attributes = Files.readAttributes(
				archive, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
		);
		if (!attributes.isRegularFile()) {
			return failed(ShieldReason.SPECIAL_FILE, 0, 0L, 0L);
		}
		long archiveBytes = attributes.size();
		if (archiveBytes < 0L || archiveBytes > policy.maximumArchiveBytes()) {
			return failed(ShieldReason.ARCHIVE_SIZE, 0, 0L, 0L);
		}
		ShieldReason centralReason = inspectCentralDirectory(
				archive, archiveBytes, policy, deadline
		);
		if (centralReason != ShieldReason.NONE) {
			return failed(centralReason, 0, 0L, 0L);
		}

		int entries = 0;
		long total = 0L;
		long inspected = 0L;
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			Enumeration<? extends ZipEntry> enumeration = zip.entries();
			while (enumeration.hasMoreElements()) {
				if (expired(deadline)) {
					return failed(ShieldReason.SCAN_TIME, entries, total, inspected);
				}
				ZipEntry entry = enumeration.nextElement();
				if (++entries > policy.maximumEntries()) {
					return failed(ShieldReason.ENTRY_LIMIT, entries, total, inspected);
				}
				String name = entry.getName();
				if (!ShieldPathPolicy.isStructurallySafeRelative(name)
						|| policy.detectUnsafePaths()
								&& !ShieldPathPolicy.isSafe(
								name, policy.maximumPathLength(), policy.maximumPathDepth()
						)) {
					return failed(ShieldReason.UNSAFE_PATH, entries, total, inspected);
				}
				if (entry.isDirectory()) {
					continue;
				}
				String logicalPath = overlays.effectivePath(name);
				if (policy.detectUnsafePaths() && !logicalPath.isEmpty()
						&& !ShieldPathPolicy.isSafe(
								logicalPath,
								policy.maximumPathLength(),
								policy.maximumPathDepth()
						)) {
					return failed(ShieldReason.UNSAFE_PATH, entries, total, inspected);
				}
				long size = entry.getSize();
				long compressed = entry.getCompressedSize();
				ShieldReason metadataReason = evaluateMetadata(size, compressed, total, policy);
				if (metadataReason != ShieldReason.NONE) {
					return failed(metadataReason, entries, total, inspected);
				}
				total += size;
				if (policy.blockCoreShaderOverrides()
						&& ShieldPathPolicy.isCoreShader(logicalPath)) {
					return failed(
							ShieldReason.CORE_SHADER_OVERRIDE, entries, total, inspected
					);
				}
				String lower = logicalPath.toLowerCase(Locale.ROOT);
				if (lower.endsWith(".png")) {
					try (InputStream input = zip.getInputStream(entry)) {
						ShieldReason reason = ShieldContentValidators.validatePng(input, policy);
						inspected = saturatedAdd(inspected, Math.min(size, 24L));
						if (reason != ShieldReason.NONE) {
							return failed(reason, entries, total, inspected);
						}
					}
				} else if (lower.endsWith(".json") || lower.endsWith(".mcmeta")) {
					try (InputStream input = zip.getInputStream(entry)) {
						ShieldReason reason = ShieldContentValidators.validateJson(
								input,
								policy.maximumSingleResourceBytes(),
								policy.maximumJsonDepth(),
								deadline
						);
						inspected = saturatedAdd(inspected, size);
						if (reason != ShieldReason.NONE) {
							return failed(reason, entries, total, inspected);
						}
					}
				}
			}
		}
		return new ShieldScanResult(ShieldReason.NONE, entries, total, inspected);
	}

	static ShieldReason evaluateMetadata(
			long size,
			long compressedSize,
			long currentTotal,
			ResourcePackShieldPolicy policy
	) {
		if (size < 0L || compressedSize < 0L || currentTotal < 0L) {
			return ShieldReason.UNKNOWN_METADATA;
		}
		if (size > policy.maximumSingleResourceBytes()) {
			return ShieldReason.SINGLE_RESOURCE_SIZE;
		}
		if (size > policy.maximumTotalResourceBytes() - currentTotal) {
			return ShieldReason.TOTAL_RESOURCE_SIZE;
		}
		if (size > 0L) {
			if (compressedSize == 0L) {
				return ShieldReason.COMPRESSION_RATIO;
			}
			long ratio = policy.maximumCompressionRatio();
			if (compressedSize <= Long.MAX_VALUE / ratio
					&& size > compressedSize * ratio) {
				return ShieldReason.COMPRESSION_RATIO;
			}
		}
		return ShieldReason.NONE;
	}

	/**
	 * Reads only ZIP central-directory metadata. This detects Unix symlink mode bits which
	 * {@link ZipEntry}'s public API does not expose and rejects ZIP64/multi-disk ambiguity.
	 */
	static ShieldReason inspectCentralDirectory(
			Path archive,
			long fileSize,
			ResourcePackShieldPolicy policy,
			long deadline
	) throws IOException {
		if (fileSize < END_HEADER_LENGTH) {
			return ShieldReason.UNKNOWN_METADATA;
		}
		if (expired(deadline)) {
			return ShieldReason.SCAN_TIME;
		}
		try (FileChannel channel = FileChannel.open(
				archive, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
		)) {
			int tailLength = (int) Math.min(fileSize, MAXIMUM_END_SEARCH);
			long tailOffset = fileSize - tailLength;
			ByteBuffer tail = ByteBuffer.allocate(tailLength).order(ByteOrder.LITTLE_ENDIAN);
			readFully(channel, tail, tailOffset);
			tail.flip();
			int end = -1;
			for (int index = tailLength - END_HEADER_LENGTH; index >= 0; index--) {
				if (tail.getInt(index) == END_SIGNATURE) {
					int commentLength = Short.toUnsignedInt(tail.getShort(index + 20));
					if (index + END_HEADER_LENGTH + commentLength == tailLength) {
						end = index;
						break;
					}
				}
			}
			if (end < 0
					|| Short.toUnsignedInt(tail.getShort(end + 4)) != 0
					|| Short.toUnsignedInt(tail.getShort(end + 6)) != 0) {
				return ShieldReason.UNKNOWN_METADATA;
			}
			int entriesOnDisk = Short.toUnsignedInt(tail.getShort(end + 8));
			int entries = Short.toUnsignedInt(tail.getShort(end + 10));
			long centralSize = Integer.toUnsignedLong(tail.getInt(end + 12));
			long centralOffset = Integer.toUnsignedLong(tail.getInt(end + 16));
			if (entries == 0xFFFF || centralSize == 0xFFFF_FFFFL
					|| centralOffset == 0xFFFF_FFFFL || entries != entriesOnDisk) {
				return ShieldReason.UNKNOWN_METADATA;
			}
			if (entries > policy.maximumEntries()) {
				return ShieldReason.ENTRY_LIMIT;
			}
			if (centralOffset > fileSize || centralSize > fileSize - centralOffset
					|| centralOffset + centralSize > tailOffset + end) {
				return ShieldReason.UNKNOWN_METADATA;
			}
			long position = centralOffset;
			long centralEnd = centralOffset + centralSize;
			ByteBuffer fixed = ByteBuffer.allocate(CENTRAL_HEADER_LENGTH)
					.order(ByteOrder.LITTLE_ENDIAN);
			for (int index = 0; index < entries; index++) {
				if (expired(deadline)) {
					return ShieldReason.SCAN_TIME;
				}
				if (position > centralEnd - CENTRAL_HEADER_LENGTH) {
					return ShieldReason.UNKNOWN_METADATA;
				}
				fixed.clear();
				readFully(channel, fixed, position);
				fixed.flip();
				if (fixed.getInt(0) != CENTRAL_SIGNATURE) {
					return ShieldReason.UNKNOWN_METADATA;
				}
				int flags = Short.toUnsignedInt(fixed.getShort(8));
				int method = Short.toUnsignedInt(fixed.getShort(10));
				if ((flags & 1) != 0
						|| method != ZipEntry.STORED && method != ZipEntry.DEFLATED) {
					return ShieldReason.UNKNOWN_METADATA;
				}
				int nameLength = Short.toUnsignedInt(fixed.getShort(28));
				int extraLength = Short.toUnsignedInt(fixed.getShort(30));
				int commentLength = Short.toUnsignedInt(fixed.getShort(32));
				int madeByHost = Short.toUnsignedInt(fixed.getShort(4)) >>> 8;
				int unixMode = fixed.getInt(38) >>> 16;
				if ((madeByHost == 3 || madeByHost == 19)
						&& (unixMode & 0xF000) == 0xA000) {
					return ShieldReason.SYMLINK;
				}
				long variableLength = (long) nameLength + extraLength + commentLength;
				if (variableLength > centralEnd - position - CENTRAL_HEADER_LENGTH) {
					return ShieldReason.UNKNOWN_METADATA;
				}
				position += CENTRAL_HEADER_LENGTH + variableLength;
			}
			return position == centralEnd ? ShieldReason.NONE : ShieldReason.UNKNOWN_METADATA;
		}
	}

	private static void readFully(FileChannel channel, ByteBuffer buffer, long position)
			throws IOException {
		while (buffer.hasRemaining()) {
			int read = channel.read(buffer, position);
			if (read < 0) {
				throw new IOException("Unexpected end of ZIP metadata");
			}
			if (read == 0) {
				continue;
			}
			position += read;
		}
	}

	private static ShieldScanResult failed(
			ShieldReason reason,
			int entries,
			long declared,
			long inspected
	) {
		return new ShieldScanResult(reason, entries, declared, inspected);
	}

	static long deadline(long durationNanos) {
		long now = System.nanoTime();
		return now >= Long.MAX_VALUE - durationNanos
				? Long.MAX_VALUE
				: now + durationNanos;
	}

	static boolean expired(long deadline) {
		return deadline != Long.MAX_VALUE && System.nanoTime() - deadline >= 0L;
	}

	static long saturatedAdd(long left, long right) {
		return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
	}
}
