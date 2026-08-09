package com.ragedriver.sodiumvolt.client.recovery;

final class RecoveryStatisticsGate {
	private RecoveryStatisticsGate() {
	}

	static boolean shouldClear(
			boolean collectStatistics,
			VoltRecoveryEngine.StatisticsSnapshot current
	) {
		return !collectStatistics
				&& current != VoltRecoveryEngine.StatisticsSnapshot.EMPTY;
	}

	static boolean matches(
			VoltRecoveryEngine.StatisticsSnapshot current,
			VoltRecoveryEngine.RecoveryStatus status,
			int crashStreak,
			int recoveryAttempts,
			int maximumAttempts,
			boolean ownedProfile,
			long secondsToStable,
			int recoveryFpsCap,
			boolean apcSuspended
	) {
		return current.status() == status
				&& current.crashStreak() == crashStreak
				&& current.recoveryAttempts() == recoveryAttempts
				&& current.maximumAttempts() == maximumAttempts
				&& current.ownedProfile() == ownedProfile
				&& current.secondsToStable() == secondsToStable
				&& current.recoveryFpsCap() == recoveryFpsCap
				&& current.apcSuspended() == apcSuspended;
	}
}
