package com.ragedriver.sodiumvolt.client.performance;

/** Reusable direct filter applied while Sodium already traverses visible particles. */
final class VapsDirectVisibilityState {
	private static final int DECISION_CAPACITY = 1_024;
	private VapsIdentityDecisionTable<Object> decisions;
	private boolean active;
	private boolean preserveCritical;
	private int criticalReserve;
	private int criticalAccepted;
	private double cameraX;
	private double cameraY;
	private double cameraZ;
	private float forwardX;
	private float forwardY;
	private float forwardZ;

	void begin(
			boolean preserveCritical,
			int criticalReserve,
			double cameraX,
			double cameraY,
			double cameraZ,
			float forwardX,
			float forwardY,
			float forwardZ
	) {
		if (this.decisions == null) {
			this.decisions = new VapsIdentityDecisionTable<>(DECISION_CAPACITY);
		}
		this.decisions.nextFrame();
		this.active = true;
		this.preserveCritical = preserveCritical;
		this.criticalReserve = Math.max(0, criticalReserve);
		this.criticalAccepted = 0;
		this.cameraX = cameraX;
		this.cameraY = cameraY;
		this.cameraZ = cameraZ;
		this.forwardX = forwardX;
		this.forwardY = forwardY;
		this.forwardZ = forwardZ;
	}

	boolean shouldRender(Object identity, boolean critical, double x, double y, double z) {
		if (!this.active) {
			return true;
		}
		int token = this.decisions.record(identity);
		if (token == VapsIdentityDecisionTable.SATURATED_TOKEN) {
			return true;
		}
		if (!VapsIdentityDecisionTable.wasInserted(token)) {
			return this.decisions.shouldRender(identity);
		}
		boolean render;
		if (critical && this.preserveCritical
				&& this.criticalAccepted < this.criticalReserve) {
			this.criticalAccepted++;
			render = true;
		} else {
			double xDistance = x - this.cameraX;
			double yDistance = y - this.cameraY;
			double zDistance = z - this.cameraZ;
			render = !Double.isFinite(xDistance)
					|| !Double.isFinite(yDistance)
					|| !Double.isFinite(zDistance)
					|| xDistance * this.forwardX
					+ yDistance * this.forwardY
					+ zDistance * this.forwardZ >= 0.0D;
		}
		if (render) {
			this.decisions.selectRecorded(token);
		}
		return render;
	}

	void disable() {
		this.active = false;
		this.criticalAccepted = 0;
		if (this.decisions != null) {
			this.decisions.releaseFrame();
		}
	}

	void releaseStorage() {
		this.disable();
		if (this.decisions != null) {
			this.decisions.clear();
			this.decisions = null;
		}
	}

	boolean active() {
		return this.active;
	}
}
