package com.ragedriver.sodiumvolt.client.resourcepack;

import java.util.concurrent.CompletableFuture;

public final class ShieldReloadGate {
	private ShieldReloadGate() {
	}

	public static <T> CompletableFuture<T> guardInitialTask(
			CompletableFuture<T> initialTask,
			boolean rejected
	) {
		if (!rejected) {
			return initialTask;
		}
		return initialTask.thenCompose(ignored -> CompletableFuture.failedFuture(
				new ResourcePackShieldRejectedException()
		));
	}
}
