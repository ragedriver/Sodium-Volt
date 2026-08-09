package com.ragedriver.sodiumvolt.client.inspector;

import java.util.List;

@SuppressWarnings("unused")
public final class InspectorLogicTest {
	private InspectorLogicTest() {
	}

	public static void main(String[] arguments) {
		gatesSamplingOnEveryVisibilityCondition();
		resetsHiddenIntervalsAndRefreshCadence();
		staggeredRefreshesHaveBoundedFreshness();
		consumesOneSceneScanPerRequest();
		cancelsStaleSceneScanRequests();
		rejectsConsumedSceneScanAfterEligibilityChanges();
		gatesAsynchronousReloadCompletions();
		saturatesRefreshDeadlines();
		cachesAndBoundsHudLayout();
		cachesUnchangedHudContent();
		computesBoundedPercentiles();
		rejectsInvalidFrameIntervals();
		classifiesBottlenecksConservatively();
		boundsParticleLabelsAndTopN();
		clearsReusableParticleScratch();
		disclosesCappedSamples();
		boundsRecommendations();
		gatesRecommendationsOnAvailableSamples();
	}

	private static void gatesSamplingOnEveryVisibilityCondition() {
		require(InspectorSamplingSchedule.shouldSample(true, true, false, true),
				"sampling requires an enabled, visible overlay in a live level");
		require(!InspectorSamplingSchedule.shouldSample(false, true, false, true),
				"the Inspector master toggle must gate all sampling");
		require(!InspectorSamplingSchedule.shouldSample(true, false, false, true),
				"a hidden overlay must gate all sampling");
		require(!InspectorSamplingSchedule.shouldSample(true, true, true, true),
				"Privacy Screenshot capture must gate all sampling");
		require(!InspectorSamplingSchedule.shouldSample(true, true, false, false),
				"sampling must stop when no live level exists");
	}

	private static void resetsHiddenIntervalsAndRefreshCadence() {
		InspectorSamplingSchedule schedule = new InspectorSamplingSchedule();
		require(!schedule.claimRefresh(100L, 500L),
				"a hidden schedule must never refresh");
		require(schedule.show() && !schedule.show(),
				"showing must establish state exactly once");
		require(schedule.recordFrame(100L, false) == 0L,
				"the first visible frame must establish a fresh baseline");
		require(schedule.recordFrame(116L, false) == 16L,
				"later visible frames must retain their exact interval");
		require(schedule.claimRefresh(116L, 500L),
				"showing must make the first refresh immediate");
		require(!schedule.claimRefresh(615L, 500L),
				"refreshes before the configured deadline must be rejected");

		require(schedule.hide() && !schedule.hide(),
				"hiding must suspend state exactly once");
		require(schedule.recordFrame(10_000L, false) == 0L,
				"hidden frames must not record samples");
		require(schedule.show(), "re-showing must create a fresh visible session");
		require(schedule.recordFrame(10_000L, false) == 0L,
				"re-show must not fold the hidden interval into frame statistics");
		require(schedule.recordFrame(10_016L, false) == 16L,
				"sampling must resume from the new visible baseline");
		require(schedule.claimRefresh(10_016L, 500L),
				"re-show must schedule an immediate refresh");

		require(schedule.recordFrame(10_032L, true) == 0L,
				"paused frames must suspend the baseline");
		require(schedule.recordFrame(20_000L, false) == 0L,
				"the first unpaused frame must skip the pause interval");
	}

	private static void consumesOneSceneScanPerRequest() {
		InspectorSceneScanGate gate = new InspectorSceneScanGate();
		gate.resume();
		require(gate.request(), "a visible generation must accept a scene-scan request");
		long generation = gate.consume();
		require(generation != 0L && gate.canPublish(generation, true),
				"the extraction callback must consume the current request once");
		require(gate.consume() == 0L,
				"later extraction callbacks must not repeat a consumed scan");
		gate.cancel();
	}

	private static void staggeredRefreshesHaveBoundedFreshness() {
		InspectorRefreshCadence cadence = new InspectorRefreshCadence();
		for (int refresh = 0; refresh < InspectorRefreshCadence.PHASE_COUNT * 3; refresh++) {
			int phase = cadence.beginRefresh();
			require(phase == refresh % InspectorRefreshCadence.PHASE_COUNT,
					"Inspector heavy phases must advance in deterministic round-robin order");
			cadence.markSampled(phase);
			for (int sampled = 0; sampled < InspectorRefreshCadence.PHASE_COUNT; sampled++) {
				int age = cadence.age(sampled);
				if (refresh >= sampled) {
					require(age >= 0
							&& age <= InspectorRefreshCadence.MAXIMUM_COMPLETED_REFRESH_AGE,
							"every enabled heavy metric must refresh within one complete phase cycle");
				}
			}
		}
		cadence.reset();
		require(cadence.age(InspectorRefreshCadence.PARTICLES) == -1
					&& cadence.beginRefresh() == InspectorRefreshCadence.FRAME_AND_CHUNKS,
				"hide, disable, privacy, level, and reload resets must invalidate stale phase samples");
		cadence.markSampled(InspectorRefreshCadence.FRAME_AND_CHUNKS);
		require(cadence.beginRefresh() == InspectorRefreshCadence.SCENE
					&& cadence.age(InspectorRefreshCadence.SCENE) == -1,
				"requesting a scene scan must not label the old scene snapshot fresh");
		cadence.beginRefresh();
		cadence.markSampled(InspectorRefreshCadence.SCENE);
		require(cadence.age(InspectorRefreshCadence.SCENE) == 0,
				"only successful extraction publication may mark the scene sample fresh");
	}

	private static void cancelsStaleSceneScanRequests() {
		InspectorSceneScanGate gate = new InspectorSceneScanGate();
		gate.resume();
		require(gate.request(), "a visible scene request must be accepted");
		gate.suspend();
		require(gate.consume() == 0L,
				"hiding must cancel an unconsumed scene request");
		require(!gate.request(), "a hidden generation must reject new requests");

		gate.resume();
		gate.request();
		long priorGeneration = gate.consume();
		gate.restart();
		require(!gate.canPublish(priorGeneration, true),
				"a level restart must prevent stale scan results from publishing");
	}

	private static void rejectsConsumedSceneScanAfterEligibilityChanges() {
		InspectorSceneScanGate gate = new InspectorSceneScanGate();
		gate.resume();
		gate.request();
		long consumedGeneration = gate.consume();
		require(consumedGeneration != 0L,
				"the deterministic publish test requires a consumed request");
		require(!gate.canPublish(consumedGeneration, false),
				"a consumed scan must not publish after its option or HUD eligibility is disabled");
	}

	private static void gatesAsynchronousReloadCompletions() {
		InspectorReloadGate gate = new InspectorReloadGate();
		gate.resume();
		long visibleGeneration = gate.begin();
		require(gate.mayComplete(visibleGeneration),
				"a visible reload observation must be eligible before timing work");
		require(gate.acceptMeasuredCompletion(visibleGeneration)
					&& gate.consumeLayoutInvalidation(),
				"a legitimate visible completion must publish and invalidate cached glyph widths");

		long hiddenGeneration = gate.begin();
		require(gate.isAccepting() && gate.suspendIfAccepting() != 0L,
				"a hidden transition must invalidate even an observation started before HUD activation");
		require(!gate.mayComplete(hiddenGeneration)
					&& !gate.acceptMeasuredCompletion(hiddenGeneration)
					&& !gate.acceptLayoutCompletion(hiddenGeneration),
				"hiding must reject an already-started completion before it performs timing or publish work");
		require(!gate.consumeLayoutInvalidation(),
				"a rejected hidden completion must not wake render-thread layout work");
		require(gate.suspendIfAccepting() == 0L,
				"repeated hidden frames must use the no-transition fast path");

		gate.resume();
		long layoutOnlyGeneration = gate.begin();
		require(gate.acceptLayoutCompletion(layoutOnlyGeneration)
					&& gate.consumeLayoutInvalidation(),
				"reloads must invalidate same-identity font layouts even when timing is not sampled");
	}

	private static void saturatesRefreshDeadlines() {
		require(InspectorSamplingSchedule.saturatingDeadline(Long.MAX_VALUE - 5L, 10L)
					== Long.MAX_VALUE,
				"refresh deadline arithmetic must saturate instead of wrapping");
		require(InspectorSamplingSchedule.saturatingDeadline(100L, -10L) == 100L,
				"invalid negative refresh intervals must not move a deadline backwards");
		InspectorSamplingSchedule schedule = new InspectorSamplingSchedule();
		schedule.show();
		require(schedule.claimRefresh(Long.MAX_VALUE - 5L, 10L)
					&& schedule.nextRefreshNanosForTesting() == Long.MAX_VALUE,
				"the schedule must retain the saturated deadline");
		require(!schedule.claimRefresh(Long.MAX_VALUE - 1L, 10L),
				"a saturated future deadline must still enforce cadence");
	}

	private static void cachesAndBoundsHudLayout() {
		InspectorHudLayoutCache layout = new InspectorHudLayoutCache(2);
		CountingTextMetrics metrics = new CountingTextMetrics();
		Object font = new Object();
		String title = "Volt Inspector";
		List<String> lines = List.of(title, "0123456789abcdef", "bounded away");
		require(layout.prepare(lines, font, 26, 100, metrics),
				"the first HUD layout must be measured");
		require(layout.visibleLineCount() == 2,
				"layout storage must enforce its fixed line bound");
		require(layout.lineAt(1).length() <= 10,
				"overflow text must be truncated to the available width once");
		int measured = metrics.operations;
		require(!layout.prepare(lines, font, 26, 100, metrics)
					&& metrics.operations == measured,
				"an unchanged line/dimension/font key must reuse cached measurements");
		require(layout.prepare(lines, font, 20, 100, metrics)
					&& metrics.operations > measured,
				"a GUI dimension change must rebuild the bounded layout");
		require(layout.prepare(lines, font, 26, 21, metrics)
					&& layout.visibleLineCount() == 0,
				"a window shorter than one complete panel line must draw no panel");
		require(layout.prepare(lines, font, Integer.MIN_VALUE, Integer.MIN_VALUE, metrics)
					&& layout.visibleLineCount() == 0,
				"adversarial negative dimensions must not overflow into a visible panel");
		require(layout.prepare(lines, font, 26, 22, metrics)
					&& layout.visibleLineCount() == 1
					&& layout.bottom() <= 22,
				"the one-line height boundary must keep the background inside the GUI");
		require(layout.prepare(lines, font, 26, 32, metrics)
					&& layout.visibleLineCount() == 2
					&& layout.bottom() <= 32,
				"small multi-line windows must never place the background below the GUI");
		layout.invalidate();
		require(layout.visibleLineCount() == 0 && !layout.retainsLineForTesting(title),
				"hidden/reset invalidation must release cached line references");
	}

	private static void cachesUnchangedHudContent() {
		InspectorHudContentGate gate = new InspectorHudContentGate();
		long first = InspectorHudContentGate.mix(0xcbf29ce484222325L, "stable");
		require(gate.shouldBuild(first, "stable") && !gate.shouldBuild(first, "stable"),
				"unchanged immutable snapshots must skip HUD string reconstruction");
		require(gate.buildsForTesting() == 1,
				"one small exact key per refresh must still avoid the larger line/layout rebuild");
		long changed = InspectorHudContentGate.mix(first, 2L);
		require(gate.shouldBuild(changed, "changed"),
				"a changed snapshot must rebuild the HUD promptly");
		gate.invalidate();
		require(gate.shouldBuild(changed, "changed"),
				"hide, level, reload, and reset generations must reject stale cached content");
		String collisionA = "Aa";
		String collisionB = "BB";
		require(collisionA.hashCode() == collisionB.hashCode(),
				"test setup must use an adversarial Java hash collision");
		long collisionFingerprint = InspectorHudContentGate.mix(17L, collisionA);
		require(gate.shouldBuild(collisionFingerprint, collisionA)
					&& gate.shouldBuild(collisionFingerprint, collisionB),
				"a fingerprint collision must not suppress distinct immutable HUD content");
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

	private static void clearsReusableParticleScratch() {
		BoundedLabelCounter counter = new BoundedLabelCounter(2);
		counter.add("first");
		counter.add("second");
		counter.add("overflow");
		counter.clear();
		require(counter.total() == 0 && counter.distinctLabels() == 0 && counter.top(2).isEmpty(),
				"particle scratch cleanup must release labels and reset every counter");
		counter.add("reused");
		require(counter.total() == 1 && counter.distinctLabels() == 1,
				"the bounded counter must remain reusable after explicit cleanup");
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
				90.0D, 50, 10_000, 500, 200, 200L, 99.0D, 40, 20_000L,
				true, true, true, true, true, true
		);
		require(recommendations.size() == 3, "Recommendations must never exceed three entries");
		require(
				InspectorAnalysis.recommendations(
						10.0D, 50, 1, 1, 1, 0L, 10.0D, 0, 10L,
						true, true, true, true, true, true
				).isEmpty(),
				"Healthy samples should not create speculative warnings"
		);
	}

	private static void gatesRecommendationsOnAvailableSamples() {
		require(
				InspectorAnalysis.recommendations(
						90.0D, 50, 10_000, 500, 200, 200L, 99.0D, 40, -1L,
						false, false, false, false, false, false
				).isEmpty(),
				"Unavailable phased samples must not emit recommendations"
		);
		require(
				!InspectorAnalysis.recommendations(
						90.0D, 50, 0, 0, 0, 0L, 99.0D, 0, -1L,
						true, false, false, false, false, true
				).isEmpty(),
				"Ready phased samples must still emit applicable recommendations"
		);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class CountingTextMetrics implements InspectorHudLayoutCache.TextMetrics {
		private int operations;

		@Override
		public int width(String value) {
			this.operations++;
			return value.length();
		}

		@Override
		public String truncateOverflow(String value, int maximumWidth) {
			this.operations++;
			return value.substring(0, Math.min(value.length(), Math.max(0, maximumWidth)));
		}
	}
}
