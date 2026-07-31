package com.ragedriver.sodiumvolt.client.privacy;

/**
 * A bounded, generation-checked capture lifecycle. The game integration owns it
 * from the client thread; synchronization also makes lifecycle resets safe.
 */
public final class PrivacyCaptureStateMachine {
	private State state = State.IDLE;
	private long generation;

	public synchronized RequestResult request() {
		if (this.state != State.IDLE) {
			return RequestResult.COALESCED;
		}
		this.state = State.PENDING;
		this.generation++;
		return RequestResult.ACCEPTED;
	}

	public synchronized CaptureScope beginFrame() {
		if (this.state != State.PENDING) {
			return CaptureScope.INACTIVE;
		}
		this.state = State.ACTIVE;
		return new CaptureScope(this, this.generation, true);
	}

	public synchronized boolean isActive() {
		return this.state == State.ACTIVE;
	}

	public synchronized State state() {
		return this.state;
	}

	public synchronized void reset() {
		this.generation++;
		this.state = State.IDLE;
	}

	private synchronized void finish(long expectedGeneration) {
		if (this.generation == expectedGeneration && this.state == State.ACTIVE) {
			this.state = State.IDLE;
		}
	}

	public enum State {
		IDLE,
		PENDING,
		ACTIVE
	}

	public enum RequestResult {
		ACCEPTED,
		COALESCED
	}

	public static final class CaptureScope implements AutoCloseable {
		private static final CaptureScope INACTIVE =
				new CaptureScope(null, Long.MIN_VALUE, false);

		private final PrivacyCaptureStateMachine owner;
		private final long generation;
		private final boolean active;
		private boolean closed;

		private CaptureScope(
				PrivacyCaptureStateMachine owner,
				long generation,
				boolean active
		) {
			this.owner = owner;
			this.generation = generation;
			this.active = active;
		}

		public boolean active() {
			return this.active;
		}

		@Override
		public void close() {
			if (!this.active || this.closed) {
				return;
			}
			this.closed = true;
			this.owner.finish(this.generation);
		}
	}
}
