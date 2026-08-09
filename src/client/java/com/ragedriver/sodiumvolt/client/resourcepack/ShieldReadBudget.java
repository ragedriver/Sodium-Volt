package com.ragedriver.sodiumvolt.client.resourcepack;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class ShieldReadBudget {
	private final long maximumBytes;
	private final AtomicLong consumedBytes = new AtomicLong();
	private final AtomicLong claimedBytes = new AtomicLong();
	private final AtomicBoolean detectionReserved = new AtomicBoolean();
	private final AtomicBoolean exceeded = new AtomicBoolean();
	private final AtomicInteger exhaustedWaiters = new AtomicInteger();
	private final AtomicLong slowPathEntries = new AtomicLong();
	private final Object exhaustionMonitor = new Object();

	public ShieldReadBudget(long maximumBytes) {
		this.maximumBytes = Math.max(1L, maximumBytes);
	}

	Reservation newReservation() {
		return new Reservation(this);
	}

	private void acquire(
			Reservation reservation,
			long requestedBytes
	) throws InterruptedException {
		if (requestedBytes <= 0L) {
			throw new IllegalArgumentException("A read reservation must be positive");
		}
		while (true) {
			if (this.exceeded.get()) {
				reservation.activate(0L, false, true);
				return;
			}

			long claimed = this.claimedBytes.get();
			if (claimed < this.maximumBytes) {
				long granted = Math.min(requestedBytes, this.maximumBytes - claimed);
				if (this.claimedBytes.compareAndSet(claimed, claimed + granted)) {
					reservation.activate(granted, false, false);
					return;
				}
				continue;
			}

			if (awaitCapacityOrReserveDetection(reservation)) {
				return;
			}
		}
	}

	private boolean awaitCapacityOrReserveDetection(
			Reservation reservation
	) throws InterruptedException {
		this.slowPathEntries.incrementAndGet();
		this.exhaustedWaiters.incrementAndGet();
		try {
			synchronized (this.exhaustionMonitor) {
				while (true) {
					if (this.exceeded.get()) {
						reservation.activate(0L, false, true);
						return true;
					}
					long claimed = this.claimedBytes.get();
					if (claimed < this.maximumBytes) {
						return false;
					}
					if (this.consumedBytes.get() == this.maximumBytes
							&& this.detectionReserved.compareAndSet(false, true)) {
						// One bounded byte proves that aggregate progress continued past
						// the exact maximum. Other exhausted readers wait for its result.
						reservation.activate(1L, true, false);
						return true;
					}
					this.exhaustionMonitor.wait();
				}
			}
		} finally {
			this.exhaustedWaiters.decrementAndGet();
		}
	}

	public long consumedBytes() {
		return this.consumedBytes.get();
	}

	public long remainingBytes() {
		return Math.max(0L, this.maximumBytes - this.claimedBytes.get());
	}

	boolean exceeded() {
		return this.exceeded.get();
	}

	long slowPathEntriesForTesting() {
		return this.slowPathEntries.get();
	}

	private boolean reconcile(
			long allowance,
			boolean detection,
			long actualBytes
	) {
		if (actualBytes < 0L || actualBytes > allowance) {
			throw new IllegalArgumentException("Read progress exceeds its reservation");
		}

		if (detection) {
			if (!this.detectionReserved.get()) {
				throw new IllegalStateException("Detection reservation was already released");
			}
			boolean accepted = actualBytes == 0L;
			if (!accepted) {
				this.exceeded.set(true);
			}
			this.detectionReserved.set(false);
			signalExhaustedWaiters();
			return accepted;
		}

		this.consumedBytes.addAndGet(actualBytes);
		long unused = allowance - actualBytes;
		if (unused != 0L) {
			long claimed = this.claimedBytes.addAndGet(-unused);
			if (claimed < 0L) {
				throw new IllegalStateException("Read reservation was already released");
			}
		}
		// When the limit is fully committed this wakes one detector; when a
		// short read returns allowance it wakes all contenders for the CAS path.
		signalExhaustedWaiters();
		return true;
	}

	private void signalExhaustedWaiters() {
		if (this.exhaustedWaiters.get() == 0) {
			return;
		}
		synchronized (this.exhaustionMonitor) {
			this.exhaustionMonitor.notifyAll();
		}
	}

	static final class Reservation implements AutoCloseable {
		private final ShieldReadBudget budget;
		private long allowance;
		private boolean detection;
		private boolean exhausted;
		private boolean active;
		private long consumed;

		private Reservation(ShieldReadBudget budget) {
			this.budget = budget;
		}

		Reservation acquire(long requestedBytes) throws InterruptedException {
			if (this.active) {
				throw new IllegalStateException("Read reservation is already active");
			}
			this.budget.acquire(this, requestedBytes);
			return this;
		}

		private void activate(long allowance, boolean detection, boolean exhausted) {
			this.allowance = allowance;
			this.detection = detection;
			this.exhausted = exhausted;
			this.active = true;
			this.consumed = 0L;
		}

		boolean exhausted() {
			ensureActive();
			return this.exhausted;
		}

		boolean commit(long actualBytes) {
			boolean accepted = consume(actualBytes);
			if (this.active) {
				close();
			}
			return accepted;
		}

		boolean consume(long actualBytes) {
			ensureActive();
			if (this.exhausted) {
				deactivate();
				return false;
			}
			if (actualBytes < 0L || actualBytes > this.allowance - this.consumed) {
				throw new IllegalArgumentException("Read progress exceeds its reservation");
			}
			this.consumed += actualBytes;
			if (this.detection || this.consumed == this.allowance) {
				boolean accepted = this.budget.reconcile(
						this.allowance, this.detection, this.consumed
				);
				deactivate();
				return accepted;
			}
			return true;
		}

		long remainingAllowance() {
			ensureActive();
			return this.allowance - this.consumed;
		}

		boolean active() {
			return this.active;
		}

		@Override
		public void close() {
			if (this.active) {
				if (!this.exhausted) {
					this.budget.reconcile(
							this.allowance, this.detection, this.consumed
					);
				}
				deactivate();
			}
		}

		private void ensureActive() {
			if (!this.active) {
				throw new IllegalStateException("Read reservation is not active");
			}
		}

		private void deactivate() {
			this.allowance = 0L;
			this.detection = false;
			this.exhausted = false;
			this.active = false;
			this.consumed = 0L;
		}
	}
}
