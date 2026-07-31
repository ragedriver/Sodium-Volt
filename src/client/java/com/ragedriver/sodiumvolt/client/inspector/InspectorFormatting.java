package com.ragedriver.sodiumvolt.client.inspector;

public final class InspectorFormatting {
	private InspectorFormatting() {
	}

	public static String particleSummary(int sampled, boolean truncated, int topCount) {
		String count = truncated
				? "at least " + Math.max(0, sampled) + " active instances (sample capped)"
				: Math.max(0, sampled) + " active instances";
		return "Particles: " + count + "; top implementations: " + Math.max(0, topCount);
	}

	public static String sceneSummary(
			int entities,
			int blockEntities,
			int sampledDisplays,
			boolean displaysTruncated
	) {
		String displays = displaysTruncated
				? "at least " + Math.max(0, sampledDisplays) + " sampled (cap reached)"
				: Integer.toString(Math.max(0, sampledDisplays));
		return "Visible scene: entities " + Math.max(0, entities)
				+ ", block entities " + Math.max(0, blockEntities)
				+ ", displays " + displays;
	}
}
