package com.ragedriver.sodiumvolt.client.resourcepack;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-reload rejection state. It deliberately has no global registry, so overlapping
 * reloads cannot observe one another's policy decisions.
 */
public final class ShieldReloadRejection {
	private final long controlGeneration;
	private final boolean counted;
	private final AtomicBoolean rejected = new AtomicBoolean();

	public ShieldReloadRejection(long controlGeneration, boolean counted) {
		this.controlGeneration = controlGeneration;
		this.counted = counted;
	}

	public void reject(long currentControlGeneration) {
		if (this.counted && this.controlGeneration == currentControlGeneration) {
			this.rejected.set(true);
		}
	}

	public <T> CompletableFuture<T> guardInitialTask(
			CompletableFuture<T> initialTask,
			long currentControlGeneration
	) {
		if (initialTask == null || !this.counted
				|| this.controlGeneration != currentControlGeneration
				|| !this.rejected.get()) {
			return initialTask;
		}
		return ShieldReloadGate.guardInitialTask(initialTask, true);
	}

	public boolean isRejected() {
		return this.rejected.get();
	}
}
