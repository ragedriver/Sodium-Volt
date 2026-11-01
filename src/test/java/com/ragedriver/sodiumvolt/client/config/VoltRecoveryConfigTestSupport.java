package com.ragedriver.sodiumvolt.client.config;

import java.util.concurrent.Phaser;

public final class VoltRecoveryConfigTestSupport {
	private VoltRecoveryConfigTestSupport() {
	}

	public static void run() throws Exception {
		VoltRecoveryConfig config = VoltRecoveryConfig.createForTest();
		var everEnabled = VoltRecoveryConfig.class.getDeclaredField("wasEverEnabled");
		everEnabled.setAccessible(true);
		int rounds = 20_000;
		Phaser phase = new Phaser(3);
		Thread enable = new Thread(() -> {
			for (int index = 0; index < rounds; index++) {
				phase.arriveAndAwaitAdvance();
				config.setVoltRecoveryEnabled(true);
				phase.arriveAndAwaitAdvance();
			}
		}, "recovery-config-enable-test");
		Thread disable = new Thread(() -> {
			for (int index = 0; index < rounds; index++) {
				phase.arriveAndAwaitAdvance();
				config.setVoltRecoveryEnabled(false);
				phase.arriveAndAwaitAdvance();
			}
		}, "recovery-config-disable-test");
		enable.start();
		disable.start();
		int lostEnableHistory = 0;
		try {
			for (int index = 0; index < rounds; index++) {
				// Reset only this disposable instance; no singleton or configuration file is touched.
				everEnabled.setBoolean(config, false);
				phase.arriveAndAwaitAdvance();
				phase.arriveAndAwaitAdvance();
				if (!everEnabled.getBoolean(config)) {
					lostEnableHistory++;
				}
			}
		} finally {
			phase.forceTermination();
			enable.join(10_000L);
			disable.join(10_000L);
		}
		if (enable.isAlive() || disable.isAlive() || lostEnableHistory != 0) {
			throw new AssertionError("Concurrent toggles must preserve Recovery enable history: lost=" + lostEnableHistory);
		}
	}
}
