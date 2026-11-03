package com.ragedriver.sodiumvolt.client.resourcepack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicInteger;

/** A private copy shared by the scanner and the vanilla archive readers. */
public final class ShieldArchiveSnapshot implements AutoCloseable {
	private final Path directory;
	private final Path path;
	private final ShieldReason reason;
	private final long deadlineNanos;
	private final AtomicInteger references = new AtomicInteger(1);

	private ShieldArchiveSnapshot(Path directory, Path path, ShieldReason reason, long deadlineNanos) {
		this.directory = directory;
		this.path = path;
		this.reason = reason;
		this.deadlineNanos = deadlineNanos;
	}

	static ShieldArchiveSnapshot copy(Path source, long maximumBytes, long deadlineNanos) {
		Path directory = null;
		Path snapshot = null;
		boolean complete = false;
		try {
			if (ZipPackScanner.expired(deadlineNanos)) {
				return failed(ShieldReason.SCAN_TIME, deadlineNanos);
			}
			BasicFileAttributes attributes = Files.readAttributes(
					source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
			);
			if (attributes.isSymbolicLink()) {
				return failed(ShieldReason.SYMLINK, deadlineNanos);
			}
			if (!attributes.isRegularFile()) {
				return failed(ShieldReason.SPECIAL_FILE, deadlineNanos);
			}
			if (attributes.size() > maximumBytes) {
				return failed(ShieldReason.ARCHIVE_SIZE, deadlineNanos);
			}
			ShieldArchiveOrphanCleanup.Destination destination = ShieldArchiveOrphanCleanup.createDestination();
			directory = destination.directory();
			snapshot = destination.archive();
			try (FileChannel input = FileChannel.open(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
					FileChannel output = FileChannel.open(snapshot, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
				ByteBuffer buffer = ByteBuffer.allocate(64 * 1_024);
				long copied = 0L;
				while (true) {
					if (ZipPackScanner.expired(deadlineNanos)) {
						return failed(ShieldReason.SCAN_TIME, deadlineNanos);
					}
					buffer.clear();
					buffer.limit((int) Math.min(buffer.capacity(), maximumBytes - copied + 1L));
					int read = input.read(buffer);
					if (read < 0) {
						break;
					}
					if (read == 0) {
						return failed(ShieldReason.MONITOR_FAILURE, deadlineNanos);
					}
					copied += read;
					if (copied > maximumBytes) {
						return failed(ShieldReason.ARCHIVE_SIZE, deadlineNanos);
					}
					buffer.flip();
					while (buffer.hasRemaining()) {
						if (output.write(buffer) == 0) {
							return failed(ShieldReason.MONITOR_FAILURE, deadlineNanos);
						}
					}
				}
			}
			complete = true;
			return new ShieldArchiveSnapshot(directory, snapshot, ShieldReason.NONE, deadlineNanos);
		} catch (IOException | RuntimeException exception) {
			return failed(ShieldReason.MONITOR_FAILURE, deadlineNanos);
		} finally {
			if (!complete) {
				delete(snapshot);
				delete(directory);
			}
		}
	}

	private static ShieldArchiveSnapshot failed(ShieldReason reason, long deadlineNanos) {
		return new ShieldArchiveSnapshot(null, null, reason, deadlineNanos);
	}

	public Path path() {
		return this.path;
	}

	ShieldReason reason() {
		return this.reason;
	}

	long deadlineNanos() {
		return this.deadlineNanos;
	}

	void retain() {
		this.references.incrementAndGet();
	}

	@Override
	public void close() {
		if (this.references.decrementAndGet() == 0) {
			delete(this.path);
			delete(this.directory);
		}
	}

	private static void delete(Path path) {
		if (path != null) {
			try {
				Files.deleteIfExists(path);
			} catch (IOException | RuntimeException ignored) {
				// The archive remains private if filesystem cleanup fails.
			}
		}
	}
}
