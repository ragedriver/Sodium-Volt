package com.ragedriver.sodiumvolt.client.profile;

import java.util.Objects;

public final class FactoryResetDecision {
	private FactoryResetDecision() {
	}

	public static void handle(
			boolean confirmed,
			Runnable reset,
			Runnable closeAfterReset,
			Runnable cancel
	) {
		Objects.requireNonNull(reset, "reset");
		Objects.requireNonNull(closeAfterReset, "closeAfterReset");
		Objects.requireNonNull(cancel, "cancel");
		if (!confirmed) {
			cancel.run();
			return;
		}
		reset.run();
		closeAfterReset.run();
	}
}
