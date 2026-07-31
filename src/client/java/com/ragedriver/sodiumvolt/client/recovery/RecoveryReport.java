package com.ragedriver.sodiumvolt.client.recovery;

import com.google.gson.JsonObject;

public record RecoveryReport(
		Reason reason,
		int crashStreak,
		int recoveryAttempt,
		boolean profileApplied,
		Restoration restoration
) {
	public RecoveryReport {
		reason = reason == null ? Reason.POSSIBLE_RENDERER_FAILURE : reason;
		crashStreak = Math.max(0, Math.min(
				RecoveryPersistentState.MAXIMUM_COUNTER,
				crashStreak
		));
		recoveryAttempt = Math.max(0, Math.min(
				RecoveryPersistentState.MAXIMUM_COUNTER,
				recoveryAttempt
		));
		restoration = restoration == null ? Restoration.NOT_REQUESTED : restoration;
	}

	JsonObject toJson() {
		JsonObject object = new JsonObject();
		object.addProperty("version", 1);
		object.addProperty("reason", this.reason.serialized());
		object.addProperty("crash_streak", this.crashStreak);
		object.addProperty("recovery_attempt", this.recoveryAttempt);
		object.addProperty("profile_applied", this.profileApplied);
		object.addProperty("restoration", this.restoration.serialized());
		return object;
	}

	public enum Reason {
		POSSIBLE_RENDERER_FAILURE("possible_renderer_failure"),
		POSSIBLE_GPU_RENDER_STALL("possible_gpu_render_stall"),
		MANUAL_REQUEST("manual_request"),
		ATTEMPT_LIMIT_REACHED("attempt_limit_reached"),
		STABLE_SESSION("stable_session"),
		RUNTIME_DISABLED("runtime_disabled"),
		CLEAN_STOP("clean_stop");

		private final String serialized;

		Reason(String serialized) {
			this.serialized = serialized;
		}

		String serialized() {
			return this.serialized;
		}
	}

	public enum Restoration {
		NOT_REQUESTED("not_requested"),
		RESTORED("restored"),
		PRESERVED_EXTERNAL_CHANGES("preserved_external_changes"),
		PENDING("pending");

		private final String serialized;

		Restoration(String serialized) {
			this.serialized = serialized;
		}

		String serialized() {
			return this.serialized;
		}
	}
}
