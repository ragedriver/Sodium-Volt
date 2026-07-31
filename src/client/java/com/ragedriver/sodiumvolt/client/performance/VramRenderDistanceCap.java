package com.ragedriver.sodiumvolt.client.performance;

public final class VramRenderDistanceCap {
	private volatile int cap = Integer.MAX_VALUE;
	private int recoveryCeiling = -1;
	private int lastApplied = -1;

	public int lower(int current, int minimum, int chunks) {
		int safeCurrent = Math.max(2, current);
		int safeMinimum = Math.max(2, Math.min(minimum, safeCurrent));
		if (this.cap == Integer.MAX_VALUE) {
			this.cap = safeCurrent;
			this.recoveryCeiling = safeCurrent;
		} else {
			observeExternal(safeCurrent);
		}
		int lowered = Math.max(safeMinimum, this.cap - Math.max(1, chunks));
		this.cap = lowered;
		if (safeCurrent > lowered) {
			this.lastApplied = lowered;
			return lowered;
		}
		return safeCurrent;
	}

	public int recover(int current) {
		if (this.cap == Integer.MAX_VALUE || this.recoveryCeiling < 0) {
			return current;
		}
		observeExternal(current);
		int raisedCap = Math.min(this.recoveryCeiling, this.cap + 1);
		this.cap = raisedCap;
		int result = current;
		if (this.lastApplied >= 0 && current == this.lastApplied) {
			result = raisedCap;
			this.lastApplied = result;
		}
		if (raisedCap >= this.recoveryCeiling) {
			this.cap = Integer.MAX_VALUE;
			this.recoveryCeiling = -1;
			this.lastApplied = -1;
		}
		return result;
	}

	public int disable(int current, boolean restoreOwnedValue, boolean mayRaise) {
		int result = current;
		if (restoreOwnedValue && mayRaise && this.lastApplied >= 0
				&& current == this.lastApplied && this.recoveryCeiling >= 0) {
			result = this.recoveryCeiling;
		}
		this.cap = Integer.MAX_VALUE;
		this.recoveryCeiling = -1;
		this.lastApplied = -1;
		return result;
	}

	public int currentCap() {
		return this.cap;
	}

	public boolean isActive() {
		return this.cap != Integer.MAX_VALUE;
	}

	public boolean ownsCurrent(int current) {
		return this.lastApplied >= 0 && current == this.lastApplied;
	}

	private void observeExternal(int current) {
		if (this.lastApplied < 0 || current == this.lastApplied) {
			return;
		}
		if (current > this.cap) {
			this.recoveryCeiling = Math.max(this.recoveryCeiling, current);
		}
		this.lastApplied = -1;
	}
}
