package com.ragedriver.sodiumvolt.client.resourcepack;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes only the first archive delegate call. Once FilePackResources has cached its
 * ZipFile descriptor, later path replacement cannot change that already-open descriptor.
 * The preceding Java attribute check and vanilla ZipFile open are separate operations,
 * so this does not claim atomic protection from a concurrent local filesystem mutator.
 */
public final class ShieldArchiveOpenGate {
	private static final Lease NOOP_LEASE = new Lease(null, false);
	private final ReentrantLock lock = new ReentrantLock();
	private volatile boolean opened;

	public Lease acquire() {
		if (this.opened) {
			return NOOP_LEASE;
		}
		this.lock.lock();
		if (this.opened) {
			this.lock.unlock();
			return NOOP_LEASE;
		}
		return new Lease(this, true);
	}

	public boolean isOpened() {
		return this.opened;
	}

	public static Lease noopLease() {
		return NOOP_LEASE;
	}

	public static final class Lease implements AutoCloseable {
		private final ShieldArchiveOpenGate owner;
		private final boolean validationRequired;
		private boolean markedOpened;
		private boolean closed;

		private Lease(ShieldArchiveOpenGate owner, boolean validationRequired) {
			this.owner = owner;
			this.validationRequired = validationRequired;
		}

		public boolean validationRequired() {
			return this.validationRequired;
		}

		public void markOpened() {
			if (this.owner != null && !this.closed) {
				this.owner.opened = true;
				this.markedOpened = true;
			}
		}

		@Override
		public void close() {
			if (this.owner != null && !this.closed) {
				this.closed = true;
				this.owner.lock.unlock();
			}
		}
	}
}
