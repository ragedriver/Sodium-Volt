package com.ragedriver.sodiumvolt.client.recovery;

public final class RecoverySessionLogic {
	private RecoverySessionLogic() {
	}

	public static StartupPlan planStartup(
			RecoveryPersistentState previous,
			boolean detectUncleanSessions,
			boolean automaticSafeMode,
			boolean forceNextLaunch,
			int crashStreakThreshold,
			int maximumAttempts
	) {
		int streak = previous.crashStreak();
		boolean uncleanPreviousSession = previous.sessionActive();
		if (uncleanPreviousSession && detectUncleanSessions) {
			streak = RecoveryPersistentState.saturatingIncrement(streak);
		}
		boolean automaticRequest = detectUncleanSessions
				&& automaticSafeMode
				&& streak >= Math.max(1, crashStreakThreshold);
		boolean requested = forceNextLaunch || automaticRequest;
		boolean loopGuard = requested && previous.recoveryAttempts() >= Math.max(1, maximumAttempts);
		boolean activate = requested && !loopGuard;
		int attempts = activate
				? RecoveryPersistentState.saturatingIncrement(previous.recoveryAttempts())
				: previous.recoveryAttempts();
		RecoveryPersistentState staged = new RecoveryPersistentState(
				true,
				streak,
				attempts,
				activate,
				activate && forceNextLaunch,
				previous.hasBackup(),
				previous.profileApplied(),
				previous.original(),
				previous.lastApplied()
		);
		return new StartupPlan(
				staged,
				uncleanPreviousSession,
				automaticRequest,
				forceNextLaunch,
				activate,
				loopGuard
		);
	}

	public static int composeFpsLimit(
			int currentLimit,
			boolean recoveryActive,
			boolean limitEnabled,
			int recoveryCap
	) {
		return recoveryActive && limitEnabled
				? Math.min(currentLimit, Math.max(1, recoveryCap))
				: currentLimit;
	}

	public static boolean suspendApc(
			boolean recoveryActive,
			boolean retainedOwnedProfile,
			boolean suspendConfigured
	) {
		return (recoveryActive || retainedOwnedProfile) && suspendConfigured;
	}

	public static ExternalRequestPlan planExternalRequest(
			boolean pendingRequest,
			boolean forceRequestAlreadyStaged
	) {
		return new ExternalRequestPlan(
				pendingRequest && !forceRequestAlreadyStaged,
				pendingRequest && forceRequestAlreadyStaged
		);
	}

	public record StartupPlan(
			RecoveryPersistentState stagedState,
			boolean uncleanPreviousSession,
			boolean automaticRequest,
			boolean forceRequest,
			boolean activateRecovery,
			boolean loopGuardActive
	) {
		public boolean mayMutateOptions(boolean stateWriteSucceeded) {
			return stateWriteSucceeded && this.activateRecovery;
		}

		public boolean consumeForceRequest(boolean recoveryStagingSucceeded) {
			return this.forceRequest && this.activateRecovery && recoveryStagingSucceeded;
		}
	}

	public record ExternalRequestPlan(
			boolean requestRecovery,
			boolean acknowledgePreviouslyStagedRequest
	) {
		public boolean shouldAcknowledge(
				boolean stateWriteSucceeded,
				boolean recoveryActivationStaged
		) {
			return stateWriteSucceeded
					&& (this.acknowledgePreviouslyStagedRequest
							|| (this.requestRecovery && recoveryActivationStaged));
		}
	}
}
