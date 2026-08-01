package com.ragedriver.sodiumvolt.client.resourcepack;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ShieldedInputStream extends InputStream {
	private static final int SKIP_BUFFER_SIZE = 8_192;

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
	private final ShieldReadBudget.Reservation readReservation;
	private final byte[] pngPrefix;
	private final ShieldContentValidators.JsonLexicalValidator jsonValidator;
	private final AtomicBoolean closed = new AtomicBoolean();
	private final EnumSet<ShieldReason> reportedReasons = EnumSet.noneOf(ShieldReason.class);
	private long resourceBytes;
	private int pngPrefixLength;
	private byte[] validationSkipBuffer;
	private boolean contentValidated;
	private boolean eof;
	private boolean rejected;
	private boolean localLimitExceeded;

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
		this.readReservation = this.aggregateBudget.newReservation();
		this.pngPrefix = this.contentKind == ContentKind.PNG ? new byte[24] : null;
		this.jsonValidator = this.contentKind == ContentKind.JSON
				? new ShieldContentValidators.JsonLexicalValidator(policy.maximumJsonDepth())
				: null;
	}

	@Override
	public int read() throws IOException {
		ensureReadable();
		long localAllowance = boundedLocalOperationLength(1L);
		try (ShieldReadBudget.Reservation reservation = reserve(localAllowance)) {
			int value = this.delegate.read();
			if (value < 0) {
				finishContent();
				return -1;
			}
			account(1L, reservation);
			inspect(value);
			return value;
		}
	}

	@Override
	public int read(byte[] bytes, int offset, int length) throws IOException {
		Objects.checkFromIndexSize(offset, length, bytes.length);
		ensureReadable();
		if (length == 0) {
			return 0;
		}
		long localAllowance = boundedLocalOperationLength(length);
		try (ShieldReadBudget.Reservation reservation = reserve(localAllowance)) {
			int boundedLength = (int) operationAllowance(reservation, localAllowance);
			int read = this.delegate.read(bytes, offset, boundedLength);
			if (read < 0) {
				finishContent();
				return -1;
			}
			if (read == 0) {
				return 0;
			}
			if (read > boundedLength) {
				this.rejected = true;
				throw new IOException("Resource pack stream returned an invalid read count");
			}
			account(read, reservation);
			inspect(bytes, offset, read);
			return read;
		}
	}

	@Override
	public long skip(long amount) throws IOException {
		ensureReadable();
		if (amount <= 0L) {
			return 0L;
		}
		if (!contentInspectionPending()) {
			return skipDirectly(amount);
		}
		return skipWithValidation(amount);
	}

	private long skipWithValidation(long amount) throws IOException {
		byte[] buffer = validationSkipBuffer();
		long skipped = 0L;
		while (skipped < amount) {
			if (!contentInspectionPending()) {
				return skipped + skipDirectly(amount - skipped);
			}
			int requested = (int) Math.min(buffer.length, amount - skipped);
			if (this.contentKind == ContentKind.PNG) {
				requested = Math.min(requested, this.pngPrefix.length - this.pngPrefixLength);
			}
			int read;
			try {
				read = read(buffer, 0, requested);
			} finally {
				// Do not retain bytes from a resource pack in the reusable buffer.
				Arrays.fill(buffer, 0, requested, (byte) 0);
			}
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

	private long skipDirectly(long amount) throws IOException {
		long skipped = 0L;
		while (skipped < amount) {
			long localAllowance = boundedLocalOperationLength(amount - skipped);
			try (ShieldReadBudget.Reservation reservation = reserve(localAllowance)) {
				long requested = operationAllowance(reservation, localAllowance);
				long current = this.delegate.skip(requested);
				if (current < 0L || current > requested) {
					this.rejected = true;
					throw new IOException("Resource pack stream returned an invalid skip count");
				}
				if (current > 0L) {
					account(current, reservation);
					skipped += current;
					continue;
				}

				// Some InputStreams return zero even when data remains. Force one bounded
				// byte of progress so callers cannot spin indefinitely.
				int value = this.delegate.read();
				if (value < 0) {
					finishContent();
					break;
				}
				account(1L, reservation);
				skipped++;
			}
		}
		return skipped;
	}

	@Override
	public int available() throws IOException {
		ensureReadable();
		if (!this.policy.rejectViolations()
				&& (this.localLimitExceeded || this.aggregateBudget.exceeded())) {
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
			try {
				this.delegate.close();
			} finally {
				if (this.validationSkipBuffer != null) {
					Arrays.fill(this.validationSkipBuffer, (byte) 0);
					this.validationSkipBuffer = null;
				}
			}
		}
	}

	public long resourceBytes() {
		return this.resourceBytes;
	}

	private long boundedLocalOperationLength(long requested) {
		if (!this.policy.rejectViolations() && this.localLimitExceeded) {
			return requested;
		}
		long localRemaining = Math.max(0L, this.maximumResourceBytes - this.resourceBytes);
		long detectionRead = localRemaining == Long.MAX_VALUE
				? localRemaining
				: localRemaining + 1L;
		return Math.max(1L, Math.min(requested, detectionRead));
	}

	private void account(
			long bytes,
			ShieldReadBudget.Reservation reservation
	) throws IOException {
		boolean aggregateAccepted = reservation.commit(bytes);
		boolean localAccepted = this.resourceBytes <= this.maximumResourceBytes - bytes;
		this.resourceBytes = localAccepted
				? this.resourceBytes + bytes
				: this.maximumResourceBytes;
		if (!localAccepted || !aggregateAccepted) {
			this.localLimitExceeded |= !localAccepted;
			reportIfNeeded(ShieldReason.LIVE_READ_LIMIT);
		}
	}

	private ShieldReadBudget.Reservation reserve(long requested) throws IOException {
		ShieldReadBudget.Reservation reservation;
		try {
			reservation = this.readReservation.acquire(requested);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while reserving resource-pack read budget", exception);
		}
		if (reservation.exhausted()) {
			try {
				reportIfNeeded(ShieldReason.LIVE_READ_LIMIT);
			} catch (IOException | RuntimeException exception) {
				reservation.close();
				throw exception;
			}
		}
		return reservation;
	}

	private static long operationAllowance(
			ShieldReadBudget.Reservation reservation,
			long localAllowance
	) {
		return reservation.exhausted() ? localAllowance : reservation.allowance();
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

	private void inspect(byte[] bytes, int offset, int length) throws IOException {
		if (this.contentValidated || this.contentKind == ContentKind.OTHER) {
			return;
		}
		if (this.contentKind == ContentKind.PNG) {
			int copied = Math.min(length, this.pngPrefix.length - this.pngPrefixLength);
			System.arraycopy(bytes, offset, this.pngPrefix, this.pngPrefixLength, copied);
			this.pngPrefixLength += copied;
			if (this.pngPrefixLength == this.pngPrefix.length) {
				this.contentValidated = true;
				reportIfNeeded(ShieldContentValidators.validatePngPrefix(
						this.pngPrefix, this.pngPrefixLength, this.policy
				));
			}
			return;
		}

		ShieldReason reason = this.jsonValidator.accept(bytes, offset, length);
		if (reason != ShieldReason.NONE) {
			this.contentValidated = true;
			reportIfNeeded(reason);
		}
	}

	boolean contentInspectionPending() {
		return !this.contentValidated && this.contentKind != ContentKind.OTHER;
	}

	private byte[] validationSkipBuffer() {
		if (this.validationSkipBuffer == null) {
			this.validationSkipBuffer = new byte[
					this.contentKind == ContentKind.PNG ? 24 : SKIP_BUFFER_SIZE
			];
		}
		return this.validationSkipBuffer;
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
