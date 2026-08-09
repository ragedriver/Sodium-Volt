package com.ragedriver.sodiumvolt.client.performance;

/**
 * Published allocation-hook lifecycle. Any dormant interval permanently makes a
 * later estimate incomplete because resources created during it were not observed.
 */
final class VramAccountingGate {
	private static final int TRACKING = 1;
	private static final int COMPLETE_BASELINE = 2;
	private static final int REQUESTED = 4;

	private volatile int state;

	synchronized void initialize(boolean enabledAtStartup) {
		this.state = enabledAtStartup
				? TRACKING | COMPLETE_BASELINE | REQUESTED
				: 0;
	}

	boolean publishEnabled(boolean enabled) {
		int observed = this.state;
		if (enabled == ((observed & REQUESTED) != 0)) {
			return false;
		}
		return transition(enabled);
	}

	boolean hasCompleteBaseline() {
		return (this.state & COMPLETE_BASELINE) != 0;
	}

	boolean isTracking() {
		return (this.state & TRACKING) != 0;
	}

	boolean requiresTransition(boolean enabled) {
		return enabled != ((this.state & REQUESTED) != 0);
	}

	private synchronized boolean transition(boolean enabled) {
		if (enabled) {
			if ((this.state & REQUESTED) == 0) {
				// A late enable cannot reconstruct resources created while dormant.
				// Publish the request edge for one diagnostic frame, but leave the
				// allocation hooks genuinely dormant until a new process session.
				this.state |= REQUESTED;
				if ((this.state & COMPLETE_BASELINE) != 0) {
					this.state |= TRACKING;
				}
				return true;
			}
		} else if ((this.state & REQUESTED) != 0) {
			// Clearing COMPLETE_BASELINE is irreversible for this process session.
			this.state = 0;
			return true;
		}
		return false;
	}
}
