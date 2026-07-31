package com.ragedriver.sodiumvolt.client.resourcepack;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ShieldedInputStream extends InputStream {
	public enum ContentKind {
		OTHER,
		PNG,
		JSON
	}

	@FunctionalInterface
	public interface ViolationHandler {
		/**
		 * @return true when the current operation must fail closed.
		 */
		boolean onViolation(ShieldReason reason);
	}

	private final InputStream delegate;
	private final long maximumResourceBytes;
	private final ShieldReadBudget aggregateBudget;
	private final ViolationHandler violationHandler;
	private final ContentKind contentKind;
	private final ResourcePackShieldPolicy policy;
	private final byte[] pngPrefix;
	private final ShieldContentValidators.JsonLexicalValidator jsonValidator;
	private final AtomicBoolean closed = new AtomicBoolean();
	private final EnumSet<ShieldReason> reportedReasons = EnumSet.noneOf(ShieldReason.class);
	private long resourceBytes;
	private int pngPrefixLength;
	private boolean contentValidated;
	private boolean eof;
	private boolean rejected;
	private boolean budgetExceeded;

	public ShieldedInputStream(
			InputStream delegate,
			long maximumResourceBytes,
			ShieldReadBudget aggregateBudget,
			ContentKind contentKind,
			ResourcePackShieldPolicy policy,
			ViolationHandler violationHandler
	) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.maximumResourceBytes = Math.max(1L, maximumResourceBytes);
		this.aggregateBudget = Objects.requireNonNull(aggregateBudget, "aggregateBudget");
		this.contentKind = contentKind == null ? ContentKind.OTHER : contentKind;
		this.policy = Objects.requireNonNull(policy, "policy");
		this.violationHandler = Objects.requireNonNull(violationHandler, "violationHandler");
		this.pngPrefix = this.contentKind == ContentKind.PNG ? new byte[24] : null;
		this.jsonValidator = this.contentKind == ContentKind.JSON
				? new ShieldContentValidators.JsonLexicalValidator(policy.maximumJsonDepth())
				: null;
	}

	@Override
	public int read() throws IOException {
		ensureReadable();
		int value = this.delegate.read();
		if (value < 0) {
			finishContent();
			return -1;
		}
		account(1L);
		inspect(value);
		return value;
	}

	@Override
	public int read(byte[] bytes, int offset, int length) throws IOException {
		Objects.checkFromIndexSize(offset, length, bytes.length);
		ensureReadable();
		if (length == 0) {
			return 0;
		}
		int boundedLength = boundedReadLength(length);
		int read = this.delegate.read(bytes, offset, boundedLength);
		if (read < 0) {
			finishContent();
			return -1;
		}
		if (read == 0) {
			return 0;
		}
		account(read);
		for (int index = 0; index < read; index++) {
			inspect(bytes[offset + index] & 0xFF);
		}
		return read;
	}

	@Override
	public long skip(long amount) throws IOException {
		ensureReadable();
		if (amount <= 0L) {
			return 0L;
		}
		byte[] buffer = new byte[(int) Math.min(8_192L, amount)];
		long skipped = 0L;
		while (skipped < amount) {
			int read = read(buffer, 0, (int) Math.min(buffer.length, amount - skipped));
			if (read < 0) {
				break;
			}
			if (read == 0) {
				int value = read();
				if (value < 0) {
					break;
				}
				skipped++;
			} else {
				skipped += read;
			}
		}
		return skipped;
	}

	@Override
	public int available() throws IOException {
		ensureReadable();
		if (!this.policy.rejectViolations() && this.budgetExceeded) {
			return this.delegate.available();
		}
		long remaining = Math.min(
				Math.max(0L, this.maximumResourceBytes - this.resourceBytes),
				this.aggregateBudget.remainingBytes()
		);
		return (int) Math.min(this.delegate.available(), Math.min(remaining, Integer.MAX_VALUE));
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	@Override
	public synchronized void mark(int readLimit) {
		// Deliberately unsupported: replay would make accounting ambiguous.
	}

	@Override
	public synchronized void reset() throws IOException {
		throw new IOException("Resource-Pack Shield streams do not support reset");
	}

	@Override
	public void close() throws IOException {
		if (this.closed.compareAndSet(false, true)) {
			this.delegate.close();
		}
	}

	public long resourceBytes() {
		return this.resourceBytes;
	}

	private int boundedReadLength(int requested) {
		if (!this.policy.rejectViolations() && this.budgetExceeded) {
			return requested;
		}
		long localRemaining = Math.max(0L, this.maximumResourceBytes - this.resourceBytes);
		long remaining = Math.min(localRemaining, this.aggregateBudget.remainingBytes());
		long detectionRead = remaining == Long.MAX_VALUE ? remaining : remaining + 1L;
		return (int) Math.max(1L, Math.min(requested, detectionRead));
	}

	private void account(long bytes) throws IOException {
		boolean localAccepted = this.resourceBytes <= this.maximumResourceBytes - bytes;
		this.resourceBytes = localAccepted
				? this.resourceBytes + bytes
				: this.maximumResourceBytes;
		boolean aggregateAccepted = this.aggregateBudget.consume(bytes);
		if (!localAccepted || !aggregateAccepted) {
			this.budgetExceeded = true;
			reportIfNeeded(ShieldReason.LIVE_READ_LIMIT);
		}
	}

	private void inspect(int value) throws IOException {
		if (this.contentKind == ContentKind.PNG && !this.contentValidated) {
			if (this.pngPrefixLength < this.pngPrefix.length) {
				this.pngPrefix[this.pngPrefixLength++] = (byte) value;
			}
			if (this.pngPrefixLength == this.pngPrefix.length) {
				this.contentValidated = true;
				reportIfNeeded(ShieldContentValidators.validatePngPrefix(
						this.pngPrefix, this.pngPrefixLength, this.policy
				));
			}
		} else if (this.contentKind == ContentKind.JSON && !this.contentValidated) {
			ShieldReason reason = this.jsonValidator.accept(value);
			if (reason != ShieldReason.NONE) {
				this.contentValidated = true;
				reportIfNeeded(reason);
			}
		}
	}

	private void finishContent() throws IOException {
		if (this.eof) {
			return;
		}
		this.eof = true;
		if (this.contentKind == ContentKind.PNG && !this.contentValidated) {
			this.contentValidated = true;
			reportIfNeeded(ShieldContentValidators.validatePngPrefix(
					this.pngPrefix, this.pngPrefixLength, this.policy
			));
		} else if (this.contentKind == ContentKind.JSON && !this.contentValidated) {
			this.contentValidated = true;
			reportIfNeeded(this.jsonValidator.finish());
		}
	}

	private void reportIfNeeded(ShieldReason reason) throws IOException {
		if (reason != ShieldReason.NONE
				&& this.reportedReasons.add(reason)
				&& this.violationHandler.onViolation(reason)) {
			this.rejected = true;
			throw new IOException("Resource pack rejected by Resource-Pack Shield policy");
		}
	}

	private void ensureReadable() throws IOException {
		if (this.closed.get()) {
			throw new IOException("Stream is closed");
		}
		if (this.rejected) {
			throw new IOException("Resource pack rejected by Resource-Pack Shield policy");
		}
	}
}
