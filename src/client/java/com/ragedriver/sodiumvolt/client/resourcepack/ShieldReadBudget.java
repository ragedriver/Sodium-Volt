package com.ragedriver.sodiumvolt.client.resourcepack;

public final class ShieldReadBudget {
	private final long maximumBytes;
	private long consumedBytes;
	private long reservedBytes;
	private boolean detectionReserved;
	private boolean exceeded;

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
		synchronized (this) {
			while (true) {
				if (this.exceeded) {
					reservation.activate(0L, false, true);
					return;
				}

				long remaining = this.maximumBytes - this.consumedBytes;
				long available = remaining - this.reservedBytes;
				if (available > 0L) {
					long granted = Math.min(requestedBytes, available);
					this.reservedBytes += granted;
					reservation.activate(granted, false, false);
					return;
				}

				if (this.reservedBytes > 0L || this.detectionReserved) {
					wait();
					continue;
				}

				// All allowed bytes were committed. Exactly one stream may read the
				// detection byte that proves the aggregate limit was exceeded.
				this.detectionReserved = true;
				reservation.activate(1L, true, false);
				return;
			}
		}
	}

	public synchronized long consumedBytes() {
		return this.consumedBytes;
	}

	public synchronized long remainingBytes() {
		return Math.max(
				0L,
				this.maximumBytes - this.consumedBytes - this.reservedBytes
		);
	}

	synchronized boolean exceeded() {
		return this.exceeded;
	}

	private synchronized boolean reconcile(
			long allowance,
			boolean detection,
			long actualBytes
	) {
		if (actualBytes < 0L || actualBytes > allowance) {
			throw new IllegalArgumentException("Read progress exceeds its reservation");
		}

		boolean accepted = true;
		if (detection) {
			if (!this.detectionReserved) {
				throw new IllegalStateException("Detection reservation was already released");
			}
			this.detectionReserved = false;
			if (actualBytes > 0L) {
				this.consumedBytes = this.maximumBytes;
				this.exceeded = true;
				accepted = false;
			}
		} else {
			if (allowance > this.reservedBytes) {
				throw new IllegalStateException("Read reservation was already released");
			}
			this.reservedBytes -= allowance;
			this.consumedBytes += actualBytes;
		}
		notifyAll();
		return accepted;
	}

	static final class Reservation implements AutoCloseable {
		private final ShieldReadBudget budget;
		private long allowance;
		private boolean detection;
		private boolean exhausted;
		private boolean active;

		private Reservation(ShieldReadBudget budget) {
			this.budget = budget;
		}

		synchronized Reservation acquire(long requestedBytes) throws InterruptedException {
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
		}

		synchronized long allowance() {
			ensureActive();
			return this.allowance;
		}

		synchronized boolean exhausted() {
			ensureActive();
			return this.exhausted;
		}

		synchronized boolean commit(long actualBytes) {
			ensureActive();
			if (this.exhausted) {
				deactivate();
				return false;
			}
			boolean accepted = this.budget.reconcile(
					this.allowance, this.detection, actualBytes
			);
			deactivate();
			return accepted;
		}

		@Override
		public synchronized void close() {
			if (this.active) {
				if (!this.exhausted) {
					this.budget.reconcile(this.allowance, this.detection, 0L);
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
		}
	}
}
