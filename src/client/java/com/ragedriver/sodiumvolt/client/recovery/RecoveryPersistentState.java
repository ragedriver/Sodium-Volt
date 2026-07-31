package com.ragedriver.sodiumvolt.client.recovery;

public record RecoveryPersistentState(
		boolean sessionActive,
		int crashStreak,
		int recoveryAttempts,
		boolean recoveryActive,
		boolean forceRequestStaged,
		boolean hasBackup,
		boolean profileApplied,
		RecoveryOptionSnapshot original,
		RecoveryOptionSnapshot lastApplied
) {
	public static final int MAXIMUM_COUNTER = 1_000_000;
	public static final RecoveryPersistentState EMPTY =
			new RecoveryPersistentState(
					false, 0, 0, false, false, false, false, null, null
			);

	public RecoveryPersistentState {
		crashStreak = boundedCounter(crashStreak);
		recoveryAttempts = boundedCounter(recoveryAttempts);
		if (!hasBackup || original == null || lastApplied == null
				|| !original.isValid() || !lastApplied.isValid()) {
			hasBackup = false;
			profileApplied = false;
			original = null;
			lastApplied = null;
		}
	}

	public RecoveryPersistentState withBackup(
			RecoveryOptionSnapshot original,
			RecoveryOptionSnapshot lastApplied
	) {
		return new RecoveryPersistentState(
				this.sessionActive,
				this.crashStreak,
				this.recoveryAttempts,
				this.recoveryActive,
				this.forceRequestStaged,
				true,
				false,
				original,
				lastApplied
		);
	}

	public RecoveryPersistentState withAppliedBackup(
			RecoveryOptionSnapshot original,
			RecoveryOptionSnapshot lastApplied
	) {
		return new RecoveryPersistentState(
				this.sessionActive,
				this.crashStreak,
				this.recoveryAttempts,
				this.recoveryActive,
				this.forceRequestStaged,
				true,
				true,
				original,
				lastApplied
		);
	}

	public RecoveryPersistentState withForceRequestStaged(boolean staged) {
		return new RecoveryPersistentState(
				this.sessionActive,
				this.crashStreak,
				this.recoveryAttempts,
				this.recoveryActive,
				staged,
				this.hasBackup,
				this.profileApplied,
				this.original,
				this.lastApplied
		);
	}

	public RecoveryPersistentState stable(boolean retainBackup) {
		return new RecoveryPersistentState(
				this.sessionActive,
				0,
				0,
				false,
				false,
				retainBackup && this.hasBackup,
				retainBackup && this.profileApplied,
				retainBackup ? this.original : null,
				retainBackup ? this.lastApplied : null
		);
	}

	public RecoveryPersistentState clean() {
		return EMPTY;
	}

	public static int saturatingIncrement(int value) {
		return value >= MAXIMUM_COUNTER ? MAXIMUM_COUNTER : Math.max(0, value) + 1;
	}

	private static int boundedCounter(int value) {
		return Math.max(0, Math.min(MAXIMUM_COUNTER, value));
	}
}
