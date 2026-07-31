package com.ragedriver.sodiumvolt.client.performance;

/**
 * Per-atlas state which prevents a stable invalid mapping from being rebuilt
 * on every animation cycle.
 */
public final class AttMappingFailOpenLatch {
	private boolean invalid;

	public boolean canBuild() {
		return !this.invalid;
	}

	/**
	 * @return true only for the transition into fail-open, for bounded stats.
	 */
	public boolean failOpen() {
		if (this.invalid) {
			return false;
		}
		this.invalid = true;
		return true;
	}

	public void blockUntilUpload() {
		this.invalid = true;
	}

	public void resetForUpload() {
		this.invalid = false;
	}

	public void observeMasterDisabled() {
		this.invalid = false;
	}
}
