package com.ragedriver.sodiumvolt.client.watchdog;

public final class GpuWatchdogPolicy {
	public static final int EVENT_NONE = 0;
	public static final int EVENT_WARNING = 1;
	public static final int EVENT_CRITICAL = 2;
	public static final int MAXIMUM_REPORTED_MILLIS = 300_000;

	private long observedSequence = Long.MIN_VALUE;
	private long lastIncidentNanos = Long.MIN_VALUE;
	private int criticalConfirmations;
	private int incidents;
	private boolean warningIssued;
	private boolean criticalHandled;
	private int latestDurationMillis;

	public int evaluate(
			long nowNanos,
			boolean inFrame,
			boolean monitoringAllowed,
			long frameStartNanos,
			long frameSequence,
			Settings settings
	) {
		if (!inFrame || !monitoringAllowed || frameSequence <= 0L || frameStartNanos <= 0L) {
			resetCurrentFrame();
			return EVENT_NONE;
		}
		if (nowNanos < frameStartNanos) {
			this.observedSequence = frameSequence;
			this.latestDurationMillis = 0;
			this.criticalConfirmations = 0;
			this.warningIssued = false;
			this.criticalHandled = false;
			return EVENT_NONE;
		}
		if (this.observedSequence != frameSequence) {
			this.observedSequence = frameSequence;
			this.criticalConfirmations = 0;
			this.warningIssued = false;
			this.criticalHandled = false;
		}
		long elapsedNanos = saturatedSubtract(nowNanos, frameStartNanos);
		this.latestDurationMillis = boundedMillis(elapsedNanos);
		if (elapsedNanos < settings.warningThresholdNanos()) {
			return EVENT_NONE;
		}
		if (elapsedNanos >= settings.criticalThresholdNanos()) {
			this.criticalConfirmations = saturatingIncrement(
					this.criticalConfirmations,
					settings.criticalConfirmationCount()
			);
			if (!this.criticalHandled
					&& this.criticalConfirmations >= settings.criticalConfirmationCount()) {
				this.criticalHandled = true;
				this.warningIssued = true;
				if (this.incidents >= settings.maximumIncidents()) {
					return EVENT_NONE;
				}
				if (this.lastIncidentNanos != Long.MIN_VALUE) {
					if (nowNanos < this.lastIncidentNanos) {
						this.lastIncidentNanos = nowNanos;
						return EVENT_NONE;
					}
					if (saturatedSubtract(nowNanos, this.lastIncidentNanos)
							< settings.incidentCooldownNanos()) {
						return EVENT_NONE;
					}
				}
				this.lastIncidentNanos = nowNanos;
				this.incidents = saturatingIncrement(
						this.incidents,
						settings.maximumIncidents()
				);
				return EVENT_CRITICAL;
			}
		}
		if (!this.warningIssued) {
			this.warningIssued = true;
			return EVENT_WARNING;
		}
		return EVENT_NONE;
	}

	public int incidents() {
		return this.incidents;
	}

	public int latestDurationMillis() {
		return this.latestDurationMillis;
	}

	public boolean capReached(Settings settings) {
		return this.incidents >= settings.maximumIncidents();
	}

	public void resetMonitoring() {
		resetCurrentFrame();
	}

	private void resetCurrentFrame() {
		this.observedSequence = Long.MIN_VALUE;
		this.criticalConfirmations = 0;
		this.warningIssued = false;
		this.criticalHandled = false;
		this.latestDurationMillis = 0;
	}

	private static int boundedMillis(long nanoseconds) {
		if (nanoseconds <= 0L) {
			return 0;
		}
		long milliseconds = nanoseconds / 1_000_000L;
		return (int) Math.min(milliseconds, MAXIMUM_REPORTED_MILLIS);
	}

	private static int saturatingIncrement(int value, int maximum) {
		return value >= maximum ? maximum : value + 1;
	}

	private static long saturatedSubtract(long current, long previous) {
		long result = current - previous;
		return result < 0L ? Long.MAX_VALUE : result;
	}

	public record Settings(
			long warningThresholdNanos,
			long criticalThresholdNanos,
			int criticalConfirmationCount,
			long incidentCooldownNanos,
			int maximumIncidents,
			int sampleIntervalMillis,
			boolean armRecoveryNextLaunch,
			boolean writeReport
	) {
		public Settings {
			warningThresholdNanos = Math.max(1_000_000L, warningThresholdNanos);
			criticalThresholdNanos = Math.max(
					warningThresholdNanos + 1_000_000L,
					criticalThresholdNanos
			);
			criticalConfirmationCount = Math.max(1, Math.min(5, criticalConfirmationCount));
			incidentCooldownNanos = Math.max(0L, incidentCooldownNanos);
			maximumIncidents = Math.max(1, Math.min(10, maximumIncidents));
			sampleIntervalMillis = Math.max(100, Math.min(1_000, sampleIntervalMillis));
		}
	}
}
