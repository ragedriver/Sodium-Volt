package com.ragedriver.sodiumvolt.client.inspector;

/** Allocation-free cache key for avoiding unchanged HUD string reconstruction. */
final class InspectorHudContentGate {
	private long fingerprint;
	private Object exactKey;
	private boolean ready;
	private int buildsForTesting;

	boolean shouldBuild(long currentFingerprint, Object currentExactKey) {
		if (this.ready && this.fingerprint == currentFingerprint
				&& java.util.Objects.equals(this.exactKey, currentExactKey)) {
			return false;
		}
		this.fingerprint = currentFingerprint;
		this.exactKey = currentExactKey;
		this.ready = true;
		this.buildsForTesting++;
		return true;
	}

	void invalidate() {
		this.ready = false;
		this.fingerprint = 0L;
		this.exactKey = null;
	}

	static long mix(long hash, Object value) {
		return (hash ^ (value == null ? 0 : value.hashCode())) * 0x100000001b3L;
	}

	static long mix(long hash, long value) {
		return (hash ^ value) * 0x100000001b3L;
	}

	int buildsForTesting() {
		return this.buildsForTesting;
	}
}

record InspectorHudContentKey(
		Object smartFps,
		Object recovery,
		Object watchdog,
		Object shield,
		Object frameTimes,
		Object chunks,
		Object vulkanUploads,
		Object scene,
		Object particles,
		Object scheduler,
		Object blockEntities,
		Object textures,
		Object vram,
		Object garbageCollection,
		long gpuUtilizationBits,
		int animatedTextures,
		String renderer,
		String driver,
		Object reload,
		long optionBits,
		int spikeThresholdMs
) {
}
