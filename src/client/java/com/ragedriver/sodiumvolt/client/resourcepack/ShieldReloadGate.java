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

	public static PrivateTokenSource privateTokenSource(
			int privateReloadDepth,
			int publicReloadOverflowDepth,
			boolean publicTokenAvailable
	) {
		if (publicReloadOverflowDepth > 0) {
			return PrivateTokenSource.DISABLED;
		}
		if (privateReloadDepth > 1) {
			return PrivateTokenSource.PRIVATE;
		}
		return publicTokenAvailable
				? PrivateTokenSource.PUBLIC
				: PrivateTokenSource.OWNED;
	}

	public enum PrivateTokenSource {
		DISABLED,
		PUBLIC,
		PRIVATE,
		OWNED;

		public boolean ownsToken() {
			return this == OWNED;
		}
	}
}
