package com.ragedriver.sodiumvolt.client.inspector;

import java.util.List;

@SuppressWarnings("unused")
public final class InspectorLogicTest {
	private InspectorLogicTest() {
	}

	public static void main(String[] arguments) {
		computesBoundedPercentiles();
		rejectsInvalidFrameIntervals();
		classifiesBottlenecksConservatively();
		boundsParticleLabelsAndTopN();
		disclosesCappedSamples();
		boundsRecommendations();
	}

	private static void computesBoundedPercentiles() {
		FrameTimeWindow window = new FrameTimeWindow(240);
		for (int sample = 1; sample <= 240; sample++) {
			require(window.addNanos(sample * 1_000_000L, 240), "Valid frame samples must be accepted");
		}
		FrameTimeWindow.Statistics statistics = window.statistics(240, new long[240]);
		require(statistics.sampleCount() == 240, "The configured window must remain bounded");
		require(statistics.medianMs() == 120.0D, "Median must use nearest-rank semantics");
		require(statistics.p95Ms() == 228.0D, "p95 must use nearest-rank semantics");
		require(statistics.p99Ms() == 238.0D, "p99 must use nearest-rank semantics");
		require(statistics.p995Ms() == 239.0D, "p99.5 must be meaningful at the minimum window");
	}

	private static void rejectsInvalidFrameIntervals() {
		FrameTimeWindow window = new FrameTimeWindow(240);
		require(!window.addNanos(-1L, 240), "Negative intervals must be rejected");
		require(!window.addNanos(50_000L, 240), "Sub-frame clock noise must be rejected");
		require(!window.addNanos(1_000_000_001L, 240), "Long pauses must not poison percentiles");
		require(window.size() == 0, "Rejected samples must not enter the ring");
	}

	private static void classifiesBottlenecksConservatively() {
		require(
				InspectorAnalysis.estimateBottleneck(95.0D, 55.0D, 50)
						== InspectorAnalysis.Bottleneck.GPU_LIKELY,
				"High GPU utilization and slow p95 should produce a GPU estimate"
		);
		require(
				InspectorAnalysis.estimateBottleneck(40.0D, 55.0D, 50)
						== InspectorAnalysis.Bottleneck.CPU_LIKELY,
				"Low GPU utilization and slow p95 should produce a CPU estimate"
		);
		require(
				InspectorAnalysis.estimateBottleneck(Double.NaN, 55.0D, 50)
						== InspectorAnalysis.Bottleneck.UNKNOWN,
				"Invalid GPU samples must remain unknown"
		);
		require(
				InspectorAnalysis.estimateBottleneck(99.0D, 12.0D, 50)
						== InspectorAnalysis.Bottleneck.NO_CLEAR_LIMIT,
				"Fast frames must not be labeled bottlenecked"
		);
	}

	private static void boundsParticleLabelsAndTopN() {
		BoundedLabelCounter counter = new BoundedLabelCounter(3);
		for (int index = 0; index < 10; index++) {
			counter.add("frequent");
		}
		counter.add("second");
		counter.add("third");
		counter.add("overflow-a");
		counter.add("overflow-b");
		List<BoundedLabelCounter.Entry> top = counter.top(2);
		require(counter.distinctLabels() == 3, "Label storage must respect its fixed bound");
		require(counter.total() == 14, "Total particle instances must include overflow labels");
		require(top.size() == 2, "Top-N output must respect its requested bound");
		require(top.getFirst().label().equals("frequent"), "Top-N must rank by instance count");
	}

	private static void disclosesCappedSamples() {
		String particles = InspectorFormatting.particleSummary(32_768, true, 5);
		require(particles.contains("at least 32768"), "Capped particle samples must not claim an exact total");
		require(particles.contains("sample capped"), "Capped particle samples must be explicitly disclosed");
		String exactParticles = InspectorFormatting.particleSummary(40, false, 3);
		require(!exactParticles.contains("at least"), "Complete particle samples may report an exact count");

		String scene = InspectorFormatting.sceneSummary(20_000, 200, 80, true);
		require(scene.contains("entities 20000"), "Total extracted entity count must remain exact");
		require(scene.contains("at least 80 sampled"), "Capped display classification must be disclosed");
		String exactScene = InspectorFormatting.sceneSummary(30, 4, 2, false);
		require(exactScene.endsWith("displays 2"), "Complete display classification may report an exact count");
	}

	private static void boundsRecommendations() {
		List<String> recommendations = InspectorAnalysis.recommendations(
				90.0D, 50, 10_000, 500, 200, 200L, 99.0D, 40, 20_000L
		);
		require(recommendations.size() == 3, "Recommendations must never exceed three entries");
		require(
				InspectorAnalysis.recommendations(10.0D, 50, 1, 1, 1, 0L, 10.0D, 0, 10L).isEmpty(),
				"Healthy samples should not create speculative warnings"
		);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
