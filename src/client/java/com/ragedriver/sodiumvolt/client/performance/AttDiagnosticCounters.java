package com.ragedriver.sodiumvolt.client.performance;

final class AttDiagnosticCounters {
	private long ticked;
	private long skippedInvisible;
	private long skippedCadence;
	private long skippedBudget;
	private long protectedTicks;
	private long exemptTicks;
	private long mappingFallbacks;
	private long mutationRevision;

	void recordTicked(boolean collect) {
		if (collect) {
			this.ticked = increment(this.ticked);
			this.mutationRevision++;
		}
	}

	void recordSkippedInvisible(boolean collect) {
		if (collect) {
			this.skippedInvisible = increment(this.skippedInvisible);
			this.mutationRevision++;
		}
	}

	void recordSkippedCadence(boolean collect) {
		if (collect) {
			this.skippedCadence = increment(this.skippedCadence);
			this.mutationRevision++;
		}
	}

	void recordSkippedBudget(boolean collect) {
		if (collect) {
			this.skippedBudget = increment(this.skippedBudget);
			this.mutationRevision++;
		}
	}

	void recordProtected(boolean collect) {
		if (collect) {
			this.protectedTicks = increment(this.protectedTicks);
			this.mutationRevision++;
		}
	}

	void recordExempt(boolean collect) {
		if (collect) {
			this.exemptTicks = increment(this.exemptTicks);
			this.mutationRevision++;
		}
	}

	void recordMappingFallback(boolean collect) {
		if (collect) {
			this.mappingFallbacks = increment(this.mappingFallbacks);
			this.mutationRevision++;
		}
	}

	long ticked() {
		return this.ticked;
	}

	long skippedInvisible() {
		return this.skippedInvisible;
	}

	long skippedCadence() {
		return this.skippedCadence;
	}

	long skippedBudget() {
		return this.skippedBudget;
	}

	long protectedTicks() {
		return this.protectedTicks;
	}

	long exemptTicks() {
		return this.exemptTicks;
	}

	long mappingFallbacks() {
		return this.mappingFallbacks;
	}

	long mutationRevision() {
		return this.mutationRevision;
	}

	void clear() {
		this.ticked = 0L;
		this.skippedInvisible = 0L;
		this.skippedCadence = 0L;
		this.skippedBudget = 0L;
		this.protectedTicks = 0L;
		this.exemptTicks = 0L;
		this.mappingFallbacks = 0L;
		this.mutationRevision = 0L;
	}

	private static long increment(long value) {
		return AttPolicy.saturatingAdd(value, 1L);
	}
}
