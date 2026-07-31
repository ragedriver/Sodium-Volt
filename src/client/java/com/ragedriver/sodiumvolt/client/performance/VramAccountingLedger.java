package com.ragedriver.sodiumvolt.client.performance;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class VramAccountingLedger {
	private final AtomicLong textureBytes = new AtomicLong();
	private final AtomicLong bufferBytes = new AtomicLong();
	private final AtomicLong renderAttachmentBytes = new AtomicLong();
	private final AtomicLong textureCount = new AtomicLong();
	private final AtomicLong bufferCount = new AtomicLong();
	private final AtomicLong renderAttachmentCount = new AtomicLong();
	private final AtomicLong peakBytes = new AtomicLong();
	private final AtomicLong allocationCount = new AtomicLong();
	private final AtomicLong spikeCount = new AtomicLong();
	private final AtomicLong spikeThresholdBytes = new AtomicLong(Long.MAX_VALUE);
	private final AtomicBoolean spikeSignal = new AtomicBoolean();

	public Allocation allocate(long bytes, boolean texture, boolean renderAttachment) {
		long safeBytes = Math.max(0L, bytes);
		add(safeBytes, texture, renderAttachment);
		return new Allocation(this, safeBytes, texture, renderAttachment);
	}

	public void add(long bytes, boolean texture, boolean renderAttachment) {
		if (bytes < 0L) {
			return;
		}
		if (texture) {
			saturatingAdd(this.textureBytes, bytes);
			saturatingAdd(this.textureCount, 1L);
			if (renderAttachment) {
				saturatingAdd(this.renderAttachmentBytes, bytes);
				saturatingAdd(this.renderAttachmentCount, 1L);
			}
		} else {
			saturatingAdd(this.bufferBytes, bytes);
			saturatingAdd(this.bufferCount, 1L);
		}
		saturatingAdd(this.allocationCount, 1L);
		updatePeak();
		if (bytes >= this.spikeThresholdBytes.get()) {
			saturatingAdd(this.spikeCount, 1L);
			this.spikeSignal.set(true);
		}
	}

	public void release(long bytes, boolean texture, boolean renderAttachment) {
		if (bytes < 0L) {
			return;
		}
		if (texture) {
			nonnegativeSubtract(this.textureBytes, bytes);
			nonnegativeSubtract(this.textureCount, 1L);
			if (renderAttachment) {
				nonnegativeSubtract(this.renderAttachmentBytes, bytes);
				nonnegativeSubtract(this.renderAttachmentCount, 1L);
			}
		} else {
			nonnegativeSubtract(this.bufferBytes, bytes);
			nonnegativeSubtract(this.bufferCount, 1L);
		}
	}

	public void setSpikeThresholdBytes(long bytes) {
		this.spikeThresholdBytes.set(Math.max(1L, bytes));
	}

	public boolean consumeSpikeSignal() {
		return this.spikeSignal.getAndSet(false);
	}

	public boolean hasSpikeSignal() {
		return this.spikeSignal.get();
	}

	public Snapshot snapshot() {
		return new Snapshot(
				this.textureBytes.get(),
				this.bufferBytes.get(),
				this.renderAttachmentBytes.get(),
				this.textureCount.get(),
				this.bufferCount.get(),
				this.renderAttachmentCount.get(),
				this.peakBytes.get(),
				this.allocationCount.get(),
				this.spikeCount.get()
		);
	}

	private void updatePeak() {
		long total = VramByteMath.saturatingAdd(this.textureBytes.get(), this.bufferBytes.get());
		long previous = this.peakBytes.get();
		while (total > previous && !this.peakBytes.compareAndSet(previous, total)) {
			previous = this.peakBytes.get();
		}
	}

	private static void saturatingAdd(AtomicLong counter, long increment) {
		long previous = counter.get();
		while (true) {
			long next = previous > Long.MAX_VALUE - increment
					? Long.MAX_VALUE
					: previous + increment;
			if (counter.compareAndSet(previous, next)) {
				return;
			}
			previous = counter.get();
		}
	}

	private static void nonnegativeSubtract(AtomicLong counter, long decrement) {
		long previous = counter.get();
		while (true) {
			long next = Math.max(0L, previous - Math.min(previous, decrement));
			if (counter.compareAndSet(previous, next)) {
				return;
			}
			previous = counter.get();
		}
	}

	public record Snapshot(
			long textureBytes,
			long bufferBytes,
			long renderAttachmentBytes,
			long textureCount,
			long bufferCount,
			long renderAttachmentCount,
			long peakBytes,
			long allocationCount,
			long spikeCount
	) {
		public long totalBytes() {
			return VramByteMath.saturatingAdd(this.textureBytes, this.bufferBytes);
		}
	}

	public static final class Allocation {
		private final VramAccountingLedger ledger;
		private final long bytes;
		private final boolean texture;
		private final boolean renderAttachment;
		private final AtomicBoolean released = new AtomicBoolean();

		private Allocation(
				VramAccountingLedger ledger,
				long bytes,
				boolean texture,
				boolean renderAttachment
		) {
			this.ledger = ledger;
			this.bytes = bytes;
			this.texture = texture;
			this.renderAttachment = renderAttachment;
		}

		public void release() {
			if (this.released.compareAndSet(false, true)) {
				this.ledger.release(this.bytes, this.texture, this.renderAttachment);
			}
		}
	}
}
