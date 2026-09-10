package com.ragedriver.sodiumvolt.client.resourcepack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

public final class ZipPackScanner {
	private static final int END_HEADER_LENGTH = 22;
	private static final int MAXIMUM_END_SEARCH = 65_535 + END_HEADER_LENGTH;
	private static final int CENTRAL_HEADER_LENGTH = 46;
	private static final int CENTRAL_READ_BUFFER_SIZE = 64 * 1_024;
	private static final int END_SIGNATURE = 0x06054B50;
	private static final int CENTRAL_SIGNATURE = 0x02014B50;
	private static final int LOCAL_SIGNATURE = 0x04034B50;
	private static final int LOCAL_HEADER_LENGTH = 30;

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
				deadline(policy.maximumScanNanos()), ZipScanHook.NONE
		);
	}

	static ShieldScanResult scan(
			Path archive,
			ResourcePackShieldPolicy policy,
			ShieldOverlayPlan overlays,
			long deadline
	) throws IOException {
		return scan(archive, policy, overlays, deadline, ZipScanHook.NONE);
	}

	static ShieldScanResult scanForTesting(
			Path archive,
			ResourcePackShieldPolicy policy,
			ShieldOverlayPlan overlays,
			long deadline,
			ZipScanHook hook
	) throws IOException {
		return scan(archive, policy, overlays, deadline, hook);
	}

	private static ShieldScanResult scan(
			Path archive,
			ResourcePackShieldPolicy policy,
			ShieldOverlayPlan overlays,
			long deadline,
			ZipScanHook hook
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
		try (FileChannel channel = FileChannel.open(
				archive, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
		)) {
			CentralDirectoryMetadata central = readCentralDirectory(
					channel, archiveBytes, policy, deadline
			);
			if (central.reason() != ShieldReason.NONE) {
				return failed(central.reason(), 0, 0L, 0L);
			}
			hook.afterCentralDirectory();
			int entries = 0;
			long total = 0L;
			long inspected = 0L;
			for (CentralEntry entry : central.metadata()) {
				if (expired(deadline)) {
					return failed(ShieldReason.SCAN_TIME, entries, total, inspected);
				}
				if (++entries > policy.maximumEntries()) {
					return failed(ShieldReason.ENTRY_LIMIT, entries, total, inspected);
				}
				String name = entry.name();
				if (!ShieldPathPolicy.isStructurallySafeRelative(name)
						|| policy.detectUnsafePaths()
								&& !ShieldPathPolicy.isSafe(
								name, policy.maximumPathLength(), policy.maximumPathDepth()
						)) {
					return failed(ShieldReason.UNSAFE_PATH, entries, total, inspected);
				}
				if (entry.directory()) {
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
				long size = entry.size();
				long compressed = entry.compressedSize();
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
					try (InputStream input = openEntry(channel, entry, central.offset())) {
						ShieldReason reason = ShieldContentValidators.validatePng(input, policy);
						inspected = saturatedAdd(inspected, Math.min(size, 24L));
						if (reason != ShieldReason.NONE) {
							return failed(reason, entries, total, inspected);
						}
					}
				} else if (name.equals("pack.mcmeta")) {
					// Root metadata is a bootstrap exception: repository discovery may parse it
					// before the wrapped live resource path exists. It is deliberately checked
					// again live because this scan cannot prove later ZIP byte identity.
					try (InputStream input = openEntry(channel, entry, central.offset())) {
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
			return new ShieldScanResult(ShieldReason.NONE, entries, total, inspected);
		}
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
		return readCentralDirectory(archive, fileSize, policy, deadline).reason();
	}

	private static CentralDirectoryMetadata readCentralDirectory(
			Path archive,
			long fileSize,
			ResourcePackShieldPolicy policy,
			long deadline
	) throws IOException {
		try (FileChannel channel = FileChannel.open(
				archive, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
		)) {
			return readCentralDirectory(channel, fileSize, policy, deadline);
		}
	}

	private static CentralDirectoryMetadata readCentralDirectory(
			FileChannel channel,
			long fileSize,
			ResourcePackShieldPolicy policy,
			long deadline
	) throws IOException {
		if (fileSize < END_HEADER_LENGTH) {
			return CentralDirectoryMetadata.failed(ShieldReason.UNKNOWN_METADATA);
		}
		if (expired(deadline)) {
			return CentralDirectoryMetadata.failed(ShieldReason.SCAN_TIME);
		}
		{
			int tailLength = (int) Math.min(fileSize, MAXIMUM_END_SEARCH);
			long tailOffset = fileSize - tailLength;
			byte[] tail = new byte[tailLength];
			readFully(channel, tail, tailOffset);
			int end = -1;
			for (int index = tailLength - END_HEADER_LENGTH; index >= 0; index--) {
				if (littleInt(tail, index) == END_SIGNATURE) {
					int commentLength = littleUnsignedShort(tail, index + 20);
					if (index + END_HEADER_LENGTH + commentLength == tailLength) {
						end = index;
						break;
					}
				}
			}
			if (end < 0
					|| littleUnsignedShort(tail, end + 4) != 0
					|| littleUnsignedShort(tail, end + 6) != 0) {
				return CentralDirectoryMetadata.failed(ShieldReason.UNKNOWN_METADATA);
			}
			int entriesOnDisk = littleUnsignedShort(tail, end + 8);
			int entries = littleUnsignedShort(tail, end + 10);
			long centralSize = Integer.toUnsignedLong(littleInt(tail, end + 12));
			long centralOffset = Integer.toUnsignedLong(littleInt(tail, end + 16));
			if (entries == 0xFFFF || centralSize == 0xFFFF_FFFFL
					|| centralOffset == 0xFFFF_FFFFL || entries != entriesOnDisk) {
				return CentralDirectoryMetadata.failed(ShieldReason.UNKNOWN_METADATA);
			}
			if (entries > policy.maximumEntries()) {
				return CentralDirectoryMetadata.failed(ShieldReason.ENTRY_LIMIT);
			}
			// ZipFile locates the directory relative to END. Require that view to
			// coincide with the declared offset; gaps permit conflicting directories.
			if (centralOffset > fileSize || centralSize > fileSize - centralOffset
					|| centralOffset + centralSize != tailOffset + end) {
				return CentralDirectoryMetadata.failed(ShieldReason.UNKNOWN_METADATA);
			}
			CentralEntry[] metadata = new CentralEntry[entries];
			BufferedCentralReader reader = new BufferedCentralReader(
					channel, centralOffset, centralSize
			);
			for (int index = 0; index < entries; index++) {
				if (expired(deadline)) {
					return CentralDirectoryMetadata.failed(ShieldReason.SCAN_TIME);
				}
				if (reader.remaining() < CENTRAL_HEADER_LENGTH) {
					return CentralDirectoryMetadata.failed(ShieldReason.UNKNOWN_METADATA);
				}
				if (reader.readInt() != CENTRAL_SIGNATURE) {
					return CentralDirectoryMetadata.failed(ShieldReason.UNKNOWN_METADATA);
				}
				int madeByHost = reader.readUnsignedShort() >>> 8;
				reader.readUnsignedShort();
				int flags = reader.readUnsignedShort();
				int method = reader.readUnsignedShort();
				if ((flags & 1) != 0
						|| method != ZipEntry.STORED && method != ZipEntry.DEFLATED) {
					return CentralDirectoryMetadata.failed(ShieldReason.UNKNOWN_METADATA);
				}
				reader.skip(4L);
				long crc = reader.readUnsignedInt();
				long compressedSize = reader.readUnsignedInt();
				long size = reader.readUnsignedInt();
				int nameLength = reader.readUnsignedShort();
				int extraLength = reader.readUnsignedShort();
				int commentLength = reader.readUnsignedShort();
				int diskStart = reader.readUnsignedShort();
				reader.readUnsignedShort();
				if (diskStart != 0) {
					return CentralDirectoryMetadata.failed(ShieldReason.UNKNOWN_METADATA);
				}
				int unixMode = reader.readInt() >>> 16;
				long localOffset = reader.readUnsignedInt();
				if ((madeByHost == 3 || madeByHost == 19)
						&& (unixMode & 0xF000) == 0xA000) {
					return CentralDirectoryMetadata.failed(ShieldReason.SYMLINK);
				}
				if (compressedSize == 0xFFFF_FFFFL || size == 0xFFFF_FFFFL
						|| localOffset == 0xFFFF_FFFFL) {
					return CentralDirectoryMetadata.failed(ShieldReason.UNKNOWN_METADATA);
				}
				long variableLength = (long) nameLength + extraLength + commentLength;
				if (variableLength > reader.remaining()) {
					return CentralDirectoryMetadata.failed(ShieldReason.UNKNOWN_METADATA);
				}
				byte[] nameBytes = reader.readBytes(nameLength);
				reader.skip((long) extraLength + commentLength);
				String name = decodeName(nameBytes);
				if (name == null) {
					return CentralDirectoryMetadata.failed(ShieldReason.UNKNOWN_METADATA);
				}
				metadata[index] = new CentralEntry(
						name, flags, method, crc, compressedSize, size,
						localOffset, name.endsWith("/")
				);
			}
			return reader.remaining() == 0L
					? new CentralDirectoryMetadata(
							ShieldReason.NONE, entries, centralOffset, metadata
					)
					: CentralDirectoryMetadata.failed(ShieldReason.UNKNOWN_METADATA);
		}
	}

	private static void readFully(FileChannel channel, byte[] bytes, long position)
			throws IOException {
		java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
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

	private static int littleUnsignedShort(byte[] bytes, int offset) {
		return Byte.toUnsignedInt(bytes[offset])
				| Byte.toUnsignedInt(bytes[offset + 1]) << 8;
	}

	private static int littleInt(byte[] bytes, int offset) {
		return littleUnsignedShort(bytes, offset)
				| littleUnsignedShort(bytes, offset + 2) << 16;
	}

	private record CentralDirectoryMetadata(
			ShieldReason reason,
			int entries,
			long offset,
			CentralEntry[] metadata
	) {
		private static CentralDirectoryMetadata failed(ShieldReason reason) {
			return new CentralDirectoryMetadata(
					reason, 0, 0L, new CentralEntry[0]
			);
		}
	}

	private record CentralEntry(
			String name,
			int flags,
			int method,
			long crc,
			long compressedSize,
			long size,
			long localOffset,
			boolean directory
	) {
	}

	private static final class BufferedCentralReader {
		private final FileChannel channel;
		private final byte[] buffer = new byte[CENTRAL_READ_BUFFER_SIZE];
		private long remaining;
		private int offset;
		private int limit;

		private BufferedCentralReader(FileChannel channel, long position, long length)
				throws IOException {
			this.channel = channel;
			this.channel.position(position);
			this.remaining = length;
		}

		private long remaining() {
			return this.remaining;
		}

		private int readUnsignedShort() throws IOException {
			return readByte() | readByte() << 8;
		}

		private int readInt() throws IOException {
			return readUnsignedShort() | readUnsignedShort() << 16;
		}

		private long readUnsignedInt() throws IOException {
			return Integer.toUnsignedLong(readInt());
		}

		private int readByte() throws IOException {
			if (this.remaining == 0L) {
				throw new IOException("Unexpected end of ZIP central directory");
			}
			if (this.offset == this.limit) {
				refill();
			}
			this.remaining--;
			return Byte.toUnsignedInt(this.buffer[this.offset++]);
		}

		private void skip(long count) throws IOException {
			if (count < 0L || count > this.remaining) {
				throw new IOException("Invalid ZIP central-directory field length");
			}
			while (count > 0L) {
				if (this.offset == this.limit) {
					refill();
				}
				int chunk = (int) Math.min(count, this.limit - this.offset);
				this.offset += chunk;
				this.remaining -= chunk;
				count -= chunk;
			}
		}

		private byte[] readBytes(int count) throws IOException {
			byte[] result = new byte[count];
			for (int index = 0; index < count; index++) {
				result[index] = (byte) readByte();
			}
			return result;
		}

		private void refill() throws IOException {
			int requested = (int) Math.min(this.buffer.length, this.remaining);
			java.nio.ByteBuffer target = java.nio.ByteBuffer.wrap(this.buffer, 0, requested);
			int read;
			do {
				read = this.channel.read(target);
				if (read < 0) {
					throw new IOException("Unexpected end of ZIP central directory");
				}
			} while (target.hasRemaining() && read != 0);
			this.offset = 0;
			this.limit = target.position();
			if (this.limit == 0) {
				throw new IOException("ZIP central-directory read made no progress");
			}
		}
	}

	private static String decodeName(byte[] bytes) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
					.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
					.decode(java.nio.ByteBuffer.wrap(bytes))
					.toString();
		} catch (java.nio.charset.CharacterCodingException exception) {
			return null;
		}
	}

	private static InputStream openEntry(
			FileChannel channel,
			CentralEntry entry,
			long centralOffset
	) throws IOException {
		byte[] header = new byte[LOCAL_HEADER_LENGTH];
		readFully(channel, header, entry.localOffset());
		if (littleInt(header, 0) != LOCAL_SIGNATURE
				|| littleUnsignedShort(header, 6) != entry.flags()
				|| littleUnsignedShort(header, 8) != entry.method()) {
			throw new IOException("ZIP local header does not match central metadata");
		}
		int nameLength = littleUnsignedShort(header, 26);
		int extraLength = littleUnsignedShort(header, 28);
		long dataOffset = entry.localOffset() + LOCAL_HEADER_LENGTH
				+ (long) nameLength + extraLength;
		if (dataOffset < entry.localOffset()
				|| entry.compressedSize() > centralOffset - dataOffset) {
			throw new IOException("ZIP entry data lies outside the archive payload");
		}
		byte[] localName = new byte[nameLength];
		readFully(channel, localName, entry.localOffset() + LOCAL_HEADER_LENGTH);
		String decodedName = decodeName(localName);
		if (!entry.name().equals(decodedName)) {
			throw new IOException("ZIP local entry name does not match central metadata");
		}
		InputStream slice = new ChannelSliceInputStream(
				channel, dataOffset, entry.compressedSize()
		);
		InputStream decodedStream = entry.method() == ZipEntry.STORED
				? slice
				: new InflaterInputStream(slice, new Inflater(true), 8_192);
		return new EntryVerifyingInputStream(
				decodedStream, entry.size(), entry.crc()
		);
	}

	private static final class EntryVerifyingInputStream extends InputStream {
		private final InputStream delegate;
		private final long expectedSize;
		private final long expectedCrc;
		private final CRC32 crc = new CRC32();
		private long readBytes;
		private boolean verified;

		private EntryVerifyingInputStream(
				InputStream delegate, long expectedSize, long expectedCrc
		) {
			this.delegate = delegate;
			this.expectedSize = expectedSize;
			this.expectedCrc = expectedCrc;
		}

		@Override
		public int read() throws IOException {
			int value = this.delegate.read();
			if (value < 0) {
				verify();
				return -1;
			}
			this.crc.update(value);
			this.readBytes++;
			return value;
		}

		@Override
		public int read(byte[] bytes, int offset, int length) throws IOException {
			int read = this.delegate.read(bytes, offset, length);
			if (read < 0) {
				verify();
				return -1;
			}
			this.crc.update(bytes, offset, read);
			this.readBytes += read;
			return read;
		}

		@Override
		public void close() throws IOException {
			this.delegate.close();
		}

		private void verify() throws IOException {
			if (this.verified) {
				return;
			}
			this.verified = true;
			if (this.readBytes != this.expectedSize
					|| this.crc.getValue() != this.expectedCrc) {
				throw new IOException("ZIP entry content does not match central metadata");
			}
		}
	}

	private static final class ChannelSliceInputStream extends InputStream {
		private final FileChannel channel;
		private long position;
		private long remaining;

		private ChannelSliceInputStream(FileChannel channel, long position, long remaining) {
			this.channel = channel;
			this.position = position;
			this.remaining = remaining;
		}

		@Override
		public int read() throws IOException {
			byte[] one = new byte[1];
			int read = read(one, 0, 1);
			return read < 0 ? -1 : Byte.toUnsignedInt(one[0]);
		}

		@Override
		public int read(byte[] bytes, int offset, int length) throws IOException {
			java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
			if (length == 0) {
				return 0;
			}
			if (this.remaining == 0L) {
				return -1;
			}
			int bounded = (int) Math.min(length, this.remaining);
			java.nio.ByteBuffer target = java.nio.ByteBuffer.wrap(bytes, offset, bounded);
			int read = this.channel.read(target, this.position);
			if (read < 0) {
				throw new IOException("Unexpected end of ZIP entry data");
			}
			if (read > 0) {
				this.position += read;
				this.remaining -= read;
			}
			return read;
		}
	}

	interface ZipScanHook {
		ZipScanHook NONE = () -> {
		};

		void afterCentralDirectory() throws IOException;
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
