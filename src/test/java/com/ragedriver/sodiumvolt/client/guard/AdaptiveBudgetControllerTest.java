package com.ragedriver.sodiumvolt.client.guard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@SuppressWarnings("unused")
public final class AdaptiveBudgetControllerTest {
	private AdaptiveBudgetControllerTest() {
	}

	public static void main(String[] arguments) {
		reducesAndRecoversWithHysteresis();
		fixedModeRestoresFullScale();
		selectsNearestWhilePreservingOriginalOrder();
		ranksTargetedAndCriticalWorkFirst();
		usesStableFirstKWithoutPrioritization();
		accountsForOneStrictParticleBudget();
		usesNormalLoadUpperBoundFastPaths();
		fusesReservedAndGeneralParticleSelection();
		selectsUnrankedOccurrencesWithoutHeapOrReplay();
		defersFallbackRankingUntilEligibleOverflow();
		fallsBackExactlyForDuplicateGeneralIdentities();
		recyclesBoundedSelectorEntries();
		keepsRankStorageLazyUntilARealCandidate();
		reusesSelectorStorageAcrossFrames();
		releasesSelectorStorageOnDurableDisable();
		releasesThreadStateAfterFinishedFrameDisable();
		selectsExactDuplicateOccurrencesByOriginalIndex();
		preservesIdentityMembershipAcrossResets();
		coordinatesBlockEntityRankingWithoutASurvivorRescan();
		preservesBlockEntityHandoffSemanticsAndLifecycle();
		compactsDisplayStatesWithoutChangingOrderOrOccurrences();
		boundsDisplayCompactionWorkUnderOverload();
		boundsBlockEntityCompactionWorkUnderOverload();
	}

	private static void reducesAndRecoversWithHysteresis() {
		AdaptiveBudgetController controller = new AdaptiveBudgetController();
		long now = 1_000_000_000L;
		require(controller.update(now, 60, true) == 1.0D, "The first adaptive sample must use full scale");

		for (int frame = 0; frame < 80; frame++) {
			now += 40_000_000L;
			controller.update(now, 60, true);
		}
		double reducedScale = controller.update(now += 40_000_000L, 60, true);
		require(reducedScale < 1.0D, "Sustained slow frames must reduce the workload scale");
		require(
				reducedScale >= AdaptiveBudgetController.MINIMUM_SCALE,
				"The workload scale must remain above its safety floor"
		);

		for (int frame = 0; frame < 180; frame++) {
			now += 5_000_000L;
			controller.update(now, 60, true);
		}
		double recoveredScale = controller.update(now + 5_000_000L, 60, true);
		require(recoveredScale > reducedScale, "Sustained fast frames must recover render capacity");
		require(recoveredScale <= 1.0D, "Recovery must never exceed the configured budgets");
	}

	private static void fixedModeRestoresFullScale() {
		AdaptiveBudgetController controller = new AdaptiveBudgetController();
		long now = 2_000_000_000L;
		controller.update(now, 120, true);
		for (int frame = 0; frame < 40; frame++) {
			now += 40_000_000L;
			controller.update(now, 120, true);
		}
		require(controller.update(now += 40_000_000L, 120, true) < 1.0D, "Test setup must reduce scale");
		require(controller.update(now + 10_000_000L, 120, false) == 1.0D, "Fixed mode must use exact caps");
	}

	private static void selectsNearestWhilePreservingOriginalOrder() {
		Object first = new Object();
		Object second = new Object();
		Object third = new Object();
		Object fourth = new Object();
		List<Object> original = List.of(first, second, third, fourth);
		BoundedTopK<Object> selector = new BoundedTopK<>(2, true);
		selector.offer(first, false, false, 100.0D, 0);
		selector.offer(second, false, false, 4.0D, 1);
		selector.offer(third, false, false, 9.0D, 2);
		selector.offer(fourth, false, false, 1.0D, 3);

		Set<Object> selected = newIdentitySet();
		selector.addTo(selected);
		List<Object> stableResult = new ArrayList<>();
		for (Object value : original) {
			if (selected.contains(value)) {
				stableResult.add(value);
			}
		}
		require(stableResult.equals(List.of(second, fourth)), "Nearest selection must retain vanilla order");
	}

	private static void ranksTargetedAndCriticalWorkFirst() {
		Object targeted = new Object();
		Object critical = new Object();
		Object nearestDecorative = new Object();
		BoundedTopK<Object> selector = new BoundedTopK<>(2, true);
		selector.offer(nearestDecorative, false, false, 1.0D, 0);
		selector.offer(critical, false, true, 400.0D, 1);
		selector.offer(targeted, true, false, 900.0D, 2);

		Set<Object> selected = newIdentitySet();
		selector.addTo(selected);
		require(selected.contains(targeted), "Targeted work must rank first");
		require(selected.contains(critical), "Critical work must rank before decorative work");
		require(!selected.contains(nearestDecorative), "Decorative distance must not override protection tiers");
	}

	private static void usesStableFirstKWithoutPrioritization() {
		Object first = new Object();
		Object second = new Object();
		Object third = new Object();
		BoundedTopK<Object> selector = new BoundedTopK<>(2, false);
		selector.offer(first, false, false, 900.0D, 0);
		selector.offer(second, false, false, 400.0D, 1);
		selector.offer(third, false, false, 1.0D, 2);

		Set<Object> selected = newIdentitySet();
		selector.addTo(selected);
		require(selected.contains(first) && selected.contains(second), "Disabled prioritization must keep first-k");
		require(!selected.contains(third), "Distance must be ignored when prioritization is disabled");
	}

	private static void accountsForOneStrictParticleBudget() {
		ParticleBudgetPlan preserved = ParticleBudgetPlan.create(10, 100, true);
		require(preserved.specialReserveCapacity() == 1, "Special reserve must be bounded");
		require(
				preserved.specialReserveCapacity()
						+ preserved.remainingCapacityAfter(preserved.specialReserveCapacity()) == 10,
				"Reserved and general particles must share one strict budget"
		);

		ParticleBudgetPlan unpreserved = ParticleBudgetPlan.create(10, 100, false);
		require(unpreserved.specialReserveCapacity() == 0, "Preservation off must not reserve capacity");
		require(unpreserved.remainingCapacityAfter(0) == 10, "All groups must share the full fixed budget");

		ParticleBudgetPlan minimum = ParticleBudgetPlan.create(1, 100, true);
		require(minimum.specialReserveCapacity() == 1, "A one-particle budget must remain strict");
		require(minimum.remainingCapacityAfter(1) == 0, "Special reservations must never exceed the cap");
	}

	private static void usesNormalLoadUpperBoundFastPaths() {
		require(GuardWorkloadBounds.particleQueuesFitBudget(30, 2, 1, 33),
				"raw particle queue sizes at the budget must bypass visibility and VAPS scans");
		require(!GuardWorkloadBounds.particleQueuesFitBudget(30, 2, 2, 33),
				"one raw particle beyond the budget must retain the cheap eligibility count");
		require(GuardWorkloadBounds.eligibleParticlesFitBudget(33, 33)
					&& !GuardWorkloadBounds.eligibleParticlesFitBudget(34, 33),
				"filtered raw loads must rank only when their exact eligible count exceeds the budget");
		require(GuardWorkloadBounds.relevantParticleUpperBound(
				Integer.MAX_VALUE, 1, 1) == Integer.MAX_VALUE,
				"adversarial raw queue sizes must saturate instead of wrapping into a fast path");
		require(GuardWorkloadBounds.statesFitBudget(128, 128)
					&& !GuardWorkloadBounds.statesFitBudget(129, 128),
				"display and BERP handoff bounds must use an inclusive budget boundary");
	}

	private static void selectsUnrankedOccurrencesWithoutHeapOrReplay() {
		Object duplicate = new Object();
		ParticleCandidate[] candidates = {
				new ParticleCandidate(duplicate, true, 100.0D, 0),
				new ParticleCandidate(duplicate, false, 1.0D, 1),
				new ParticleCandidate(new Object(), true, 0.0D, 3),
				new ParticleCandidate(new Object(), false, 0.0D, 7)
		};
		SpillAwareParticleSelector<Object> selector = new SpillAwareParticleSelector<>();
		for (int budget = 0; budget <= 5; budget++) {
			selector.begin(budget, false, false);
			require(!selector.requiresRanking(), "unranked selection must not request distance calculations");
			for (ParticleCandidate candidate : candidates) {
				selector.offer(candidate.value(), candidate.special(), false, Double.NaN, candidate.originalIndex());
			}
			Set<Object> actual = newIdentitySet();
			require(selector.finishFromSpill(actual, 2), "unranked duplicates must never require replay");
			require(sameIdentityMembership(legacyParticleSelection(candidates, budget, false, false), actual, candidates),
					"unranked selection must retain first budget occurrences rather than fill unique identities");
			for (ParticleCandidate candidate : candidates) {
				require(!selector.retainsReferenceForTesting(candidate.value()), "finalization must release unranked references");
			}
		}
		require(selector.allocatedEntriesForTesting() <= 5, "unranked selection must allocate only a bounded reference array");
		selector.begin(2, false, false);
		selector.offer(duplicate, false, false, 0, 0);
		selector.abort();
		require(!selector.retainsReferenceForTesting(duplicate), "abort must clear unranked references");
		selector.releaseStorage();
		require(selector.allocatedEntriesForTesting() == 0, "disable must release unranked storage");
	}

	private static void defersFallbackRankingUntilEligibleOverflow() {
		ParticleSelectionPrefix<Object> prefix = new ParticleSelectionPrefix<>();
		Object first = new Object();
		List<Integer> ranked = new ArrayList<>();
		ParticleSelectionPrefix.CandidateConsumer<Object> consumer = (value, special, index) -> {
			require(special == (index == 5), "prefix must preserve special metadata");
			ranked.add(index);
		};
		prefix.begin(2);
		prefix.offer(first, false, 1, consumer);
		prefix.offer(first, true, 5, consumer);
		require(ranked.isEmpty(), "eligible-under-budget workloads must never rank or calculate distances");
		prefix.offer(new Object(), false, 8, consumer);
		prefix.offer(new Object(), false, 11, consumer);
		require(ranked.equals(List.of(1, 5, 8, 11)), "overflow must offer each eligible occurrence once in original order");
		require(prefix.allocatedEntriesForTesting() == 2, "prefix storage must stay bounded by budget");
		require(!prefix.retainsReferenceForTesting(first), "overflow must release buffered identities");
		prefix.begin(2);
		prefix.offer(first, false, 1, consumer);
		prefix.clear();
		require(!prefix.retainsReferenceForTesting(first), "under-budget completion must clear buffered identities");
		prefix.releaseStorage();
		require(prefix.allocatedEntriesForTesting() == 0, "disable must release prefix storage");
	}

	private static void fusesReservedAndGeneralParticleSelection() {
		Object special = new Object();
		Object near = new Object();
		Object medium = new Object();
		Object far = new Object();
		ParticleCandidate[] candidates = {
				new ParticleCandidate(far, false, 100.0D, 0),
				new ParticleCandidate(special, true, 50.0D, 1),
				new ParticleCandidate(medium, false, 9.0D, 2),
				new ParticleCandidate(near, false, 1.0D, 3)
		};
		Set<Object> expected = legacyParticleSelection(candidates, 3, true, true);
		Set<Object> actual = newIdentitySet();
		SpillAwareParticleSelector<Object> selector = new SpillAwareParticleSelector<>();
		selector.begin(3, true, true);
		int specialCount = 0;
		for (ParticleCandidate candidate : candidates) {
			selector.offer(
					candidate.value(),
					candidate.special(),
					candidate.distanceSquared() <= 64.0D,
					candidate.distanceSquared(),
					candidate.originalIndex()
			);
			if (candidate.special()) {
				specialCount++;
			}
		}
		require(selector.finishFromSpill(actual, specialCount),
				"identity-unique input must finalize after one shared candidate traversal");
		require(sameIdentityMembership(expected, actual, candidates),
				"fused reserve/general selection must match the legacy two-pass membership");
		for (ParticleCandidate candidate : candidates) {
			require(!selector.retainsReferenceForTesting(candidate.value()),
					"successful fused finalization must release candidate references");
		}
		int allocatedEntries = selector.allocatedEntriesForTesting();
		require(allocatedEntries == 4,
				"a three-particle budget with one reserve must retain only special plus spill pools");
		actual.clear();
		selector.begin(3, true, true);
		for (ParticleCandidate candidate : candidates) {
			selector.offer(
					candidate.value(),
					candidate.special(),
					candidate.distanceSquared() <= 64.0D,
					candidate.distanceSquared(),
					candidate.originalIndex()
			);
		}
		require(selector.finishFromSpill(actual, specialCount)
					&& selector.allocatedEntriesForTesting() == allocatedEntries,
				"later frames must reuse bounded selector storage without per-frame entry allocation");
	}

	private static void fallsBackExactlyForDuplicateGeneralIdentities() {
		Object special = new Object();
		Object duplicateGeneral = new Object();
		Object next = new Object();
		Object later = new Object();
		ParticleCandidate[] candidates = {
				new ParticleCandidate(special, true, 100.0D, 0),
				new ParticleCandidate(duplicateGeneral, false, 1.0D, 1),
				new ParticleCandidate(duplicateGeneral, false, 1.5D, 2),
				new ParticleCandidate(next, false, 2.0D, 3),
				new ParticleCandidate(later, false, 3.0D, 4)
		};
		Set<Object> expected = legacyParticleSelection(candidates, 4, true, true);
		Set<Object> actual = newIdentitySet();
		SpillAwareParticleSelector<Object> selector = new SpillAwareParticleSelector<>();
		selector.begin(4, true, true);
		for (ParticleCandidate candidate : candidates) {
			selector.offer(
					candidate.value(),
					candidate.special(),
					candidate.distanceSquared() <= 64.0D,
					candidate.distanceSquared(),
					candidate.originalIndex()
			);
		}
		require(!selector.finishFromSpill(actual, 1),
				"duplicate general identities must request an exact bounded replay");
		for (ParticleCandidate candidate : candidates) {
			selector.offerReplay(
					candidate.value(),
					candidate.distanceSquared() <= 64.0D,
					candidate.distanceSquared(),
					candidate.originalIndex(),
					actual
			);
		}
		selector.completeReplay(actual);
		require(sameIdentityMembership(expected, actual, candidates),
				"duplicate replay must preserve the legacy identity-underfill behavior exactly");
		for (ParticleCandidate candidate : candidates) {
			require(!selector.retainsReferenceForTesting(candidate.value()),
					"duplicate fallback completion must release every internal reference");
		}

		Object aborted = new Object();
		selector.begin(2, true, true);
		selector.offer(aborted, true, true, 1.0D, 0);
		require(selector.retainsReferenceForTesting(aborted),
				"active fused selection must retain its bounded candidate");
		selector.abort();
		require(!selector.retainsReferenceForTesting(aborted),
				"failure and lifecycle aborts must release fused-selection references");
	}

	private static Set<Object> legacyParticleSelection(
			ParticleCandidate[] candidates,
			int budget,
			boolean prioritizeDistance,
			boolean preserveCritical
	) {
		int specialCount = 0;
		for (ParticleCandidate candidate : candidates) {
			if (candidate.special()) {
				specialCount++;
			}
		}
		ParticleBudgetPlan plan = ParticleBudgetPlan.create(budget, specialCount, preserveCritical);
		Set<Object> selected = newIdentitySet();
		BoundedTopK<Object> special = new BoundedTopK<>(
				plan.specialReserveCapacity(),
				prioritizeDistance
		);
		for (ParticleCandidate candidate : candidates) {
			if (candidate.special()) {
				special.offer(
						candidate.value(), false, true,
						candidate.distanceSquared(), candidate.originalIndex()
				);
			}
		}
		special.addTo(selected);
		special.clear();
		BoundedTopK<Object> general = new BoundedTopK<>(
				plan.remainingCapacityAfter(selected.size()),
				prioritizeDistance
		);
		for (ParticleCandidate candidate : candidates) {
			if (!selected.contains(candidate.value())) {
				general.offer(
						candidate.value(), false,
						preserveCritical && candidate.distanceSquared() <= 64.0D,
						candidate.distanceSquared(), candidate.originalIndex()
				);
			}
		}
		general.addTo(selected);
		general.clear();
		return selected;
	}

	private static boolean sameIdentityMembership(
			Set<Object> expected,
			Set<Object> actual,
			ParticleCandidate[] candidates
	) {
		if (expected.size() != actual.size()) {
			return false;
		}
		for (ParticleCandidate candidate : candidates) {
			if (expected.contains(candidate.value()) != actual.contains(candidate.value())) {
				return false;
			}
		}
		return true;
	}

	private static void recyclesBoundedSelectorEntries() {
		BoundedTopK<Object> selector = new BoundedTopK<>(3, true);
		for (int index = 0; index < 1_000; index++) {
			selector.offer(new Object(), false, false, 1_000.0D - index, index);
		}
		require(selector.size() == 3, "Selector must retain only its bounded capacity");
		require(selector.allocatedEntryCount() == 3, "Selector must recycle entries instead of allocating per offer");
	}

	private static void keepsRankStorageLazyUntilARealCandidate() {
		BoundedTopK<Object> selector = new BoundedTopK<>();
		selector.reset(4_096, true);
		require(selector.allocatedEntryCount() == 0,
				"a large potential budget with zero eligible candidates must allocate no rank storage");
		Object candidate = new Object();
		selector.offer(candidate, false, false, 1.0D, 0);
		int smallInitialCapacity = selector.allocatedEntryCount();
		require(smallInitialCapacity > 0 && smallInitialCapacity <= 8,
				"one eligible candidate after a coarse large bound must retain only small storage");
		for (int index = 1; index < 5; index++) {
			selector.offer(new Object(), false, false, index + 1.0D, index);
		}
		require(selector.allocatedEntryCount() == smallInitialCapacity,
				"a few eligible candidates must reuse the small initial array group");
		selector.clear();
		require(!selector.retainsValueForTesting(candidate),
				"clearing lazy rank storage must release its candidate reference");
		selector.reset(32, true);
		for (int index = 0; index < 96; index++) {
			selector.offer(new Object(), false, false, 96.0D - index, index);
		}
		require(selector.size() == 32 && selector.allocatedEntryCount() == 32,
				"a proven K-overload must grow geometrically to the exact bounded capacity");
		int[] exactIndices = new int[32];
		selector.copyOriginalIndices(exactIndices);
		Arrays.sort(exactIndices);
		for (int index = 0; index < exactIndices.length; index++) {
			require(exactIndices[index] == index + 64,
					"geometric growth must preserve exact nearest-candidate selection");
		}
		selector.clear();
		int highWater = selector.allocatedEntryCount();
		Object reused = new Object();
		selector.reset(32, true);
		selector.offer(reused, false, false, 1.0D, 0);
		require(selector.allocatedEntryCount() == highWater,
				"later cycles must reuse high-water storage without reallocating");
		selector.clear();
		require(!selector.retainsValueForTesting(reused),
				"reused high-water storage must still release candidate references");
	}

	private static void reusesSelectorStorageAcrossFrames() {
		BoundedTopK<Object> selector = new BoundedTopK<>();
		Set<Object> selected = newIdentitySet();
		for (int frame = 0; frame < 250; frame++) {
			selector.reset(4, true);
			for (int index = 0; index < 100; index++) {
				selector.offer(new Object(), false, false, 100.0D - index, index);
			}
			selected.clear();
			selector.addTo(selected);
			require(selected.size() == 4, "Every reset must preserve the configured strict capacity");
			selector.clear();
			require(selector.size() == 0, "Clearing a frame must release the active heap contents");
		}
		require(
				selector.allocatedEntryCount() == 4,
				"Repeated frames must reuse selector entries instead of allocating per frame"
		);

		selector.reset(6, true);
		for (int index = 0; index < 20; index++) {
			selector.offer(new Object(), false, false, index, index);
		}
		require(selector.allocatedEntryCount() == 6, "The reusable pool may grow only to a larger budget");
		selector.clear();
		selector.reset(2, false);
		Object first = new Object();
		Object second = new Object();
		Object nearestButLast = new Object();
		selector.offer(first, false, false, 10.0D, 0);
		selector.offer(second, false, false, 20.0D, 1);
		selector.offer(nearestButLast, false, false, 1.0D, 2);
		require(selector.allocatedEntryCount() == 6, "Smaller later budgets must reuse high-water storage");
		selected.clear();
		selector.addTo(selected);
		require(
				selected.contains(first) && selected.contains(second) && !selected.contains(nearestButLast),
				"Changing reset policy must rebuild heap ordering without changing stable first-k semantics"
		);
	}

	private static void releasesSelectorStorageOnDurableDisable() {
		BoundedTopK<Object> selector = new BoundedTopK<>(4_096, true);
		for (int index = 0; index < 4_096; index++) {
			selector.offer(new Object(), false, false, index, index);
		}
		require(selector.allocatedEntryCount() == 4_096,
				"test setup must grow the reusable arrays to their high-water mark");
		selector.releaseStorage();
		require(selector.allocatedEntryCount() == 0 && selector.size() == 0,
				"durable disable must release grown selector arrays");
		selector.reset(2, true);
		selector.offer(new Object(), false, false, 1.0D, 0);
		require(selector.allocatedEntryCount() > 0 && selector.allocatedEntryCount() <= 2,
				"later re-enable must recreate only the storage actually needed");
	}

	private static void releasesThreadStateAfterFinishedFrameDisable() {
		GuardThreadState<Object> state = new GuardThreadState<>(Object::new);
		require(state.peek() == null,
				"disabled Guard must not initialize render-thread storage");
		Object enabledFrame = state.acquire();
		require(state.peek() == enabledFrame,
				"an enabled frame lazily owns one render-thread state");
		// Finishing a frame clears hot gates but intentionally retains reusable state.
		require(state.peek() == enabledFrame,
				"normal enabled frames must reuse high-water storage");
		require(state.release() == enabledFrame && state.peek() == null,
				"the first disabled frame must release state even after hot gates were cleared");
		require(state.release() == null && state.peek() == null,
				"stable disabled frames must neither allocate nor repeat cleanup");
		require(state.acquire() != enabledFrame,
				"re-enable must lazily create a fresh state");
		state.release();
	}

	private static void preservesIdentityMembershipAcrossResets() {
		EqualValue first = new EqualValue(1);
		EqualValue equalButDistinct = new EqualValue(1);
		BoundedTopK<EqualValue> selector = new BoundedTopK<>(2, false);
		Set<EqualValue> selected = newIdentitySet();
		selector.offer(first, false, false, 0.0D, 0);
		selector.offer(equalButDistinct, false, false, 0.0D, 1);
		selector.addTo(selected);
		require(first.equals(equalButDistinct), "Test setup must use equal values");
		require(selected.size() == 2, "Selection membership must continue using object identity");

		selector.clear();
		selected.clear();
		selector.reset(1, false);
		selector.offer(equalButDistinct, false, false, 0.0D, 0);
		selector.addTo(selected);
		require(!selected.contains(first), "Reset membership must not retain equal objects from an older frame");
		require(selected.contains(equalButDistinct), "Reset membership must contain the newly selected identity");
	}

	private static void selectsExactDuplicateOccurrencesByOriginalIndex() {
		Object duplicate = new Object();
		Object targeted = new Object();
		Object equalButDistinct = new EqualValue(1);
		BoundedTopK<Object> selector = new BoundedTopK<>();
		require(selector.allocatedEntryCount() == 0,
				"normal-load display scans must not initialize ranking storage");
		selector.reset(3, true);
		selector.offer(duplicate, false, false, 9.0D, 2);
		selector.offer(equalButDistinct, false, false, 1.0D, 3);
		selector.offer(duplicate, false, false, 4.0D, 4);
		selector.offer(targeted, true, false, 100.0D, 5);
		int[] selectedIndices = new int[3];
		int selectedCount = selector.copyOriginalIndices(selectedIndices);
		Arrays.sort(selectedIndices, 0, selectedCount);
		require(selectedCount == 3 && Arrays.equals(selectedIndices, new int[]{3, 4, 5}),
				"overload selection must enforce an exact occurrence cap with duplicate identities");
		require(selector.allocatedEntryCount() == 3,
				"first overload must use bounded primitive-array storage instead of entry wrappers");
		selector.clear();
		require(!selector.retainsValueForTesting(duplicate)
					&& !selector.retainsValueForTesting(targeted),
				"display selection scratch cleanup must release all candidate identities");
		selector.reset(2, true);
		selector.offer(targeted, true, false, 999.0D, 0);
		selector.offer(equalButDistinct, false, true, 1.0D, 1);
		selector.offer(new Object(), false, false, 0.0D, 2);
		require(selector.allocatedEntryCount() == 3,
				"later overloads must reuse the bounded high-water scratch storage");
		int[] priorities = new int[2];
		selector.copyOriginalIndices(priorities);
		Arrays.sort(priorities);
		require(Arrays.equals(priorities, new int[]{0, 1}),
				"targeted and critical display priorities must remain deterministic");
		selector.clear();
	}

	private static void coordinatesBlockEntityRankingWithoutASurvivorRescan() {
		CountingArrayList<RankedState> states = new CountingArrayList<>();
		for (int index = 0; index < 8; index++) {
			states.add(new RankedState(index));
		}
		BlockEntityGuardHandoff<RankedState> handoff = new BlockEntityGuardHandoff<>();
		handoff.begin(states.size(), 3, true, true);
		for (RankedState state : states) {
			boolean targeted = state.index() == 7;
			boolean critical = state.index() == 6;
			double distanceSquared = state.index() == 7
					? 1_000.0D
					: state.index() == 6 ? 500.0D : state.index() + 1.0D;
			handoff.offer(state, targeted, critical, distanceSquared, state.index());
		}
		handoff.complete();

		int survivors = 8;
		int legacyVisits = survivors * 3;
		require(states.visits() == survivors,
				"Guard ranking must be fused into the producer's existing eight survivor visits");
		int removed = handoff.applyTo(states);
		require(states.visits() == survivors * 2 && states.visits() < legacyVisits,
				"the combined path must visit eight survivors twice instead of three times (16 vs 24)");
		require(removed == 5 && states.size() == 3,
				"the coordinated handoff must enforce the same strict Guard budget");
		require(states.get(0).index() == 0
					&& states.get(1).index() == 6
					&& states.get(2).index() == 7,
				"targeted, critical, and nearest survivors must remain selected in vanilla order");
	}

	private static void preservesBlockEntityHandoffSemanticsAndLifecycle() {
		Object first = new Object();
		Object second = new Object();
		Object third = new Object();
		ArrayList<Object> firstK = new ArrayList<>(List.of(first, second, third));
		BlockEntityGuardHandoff<Object> handoff = new BlockEntityGuardHandoff<>();
		handoff.begin(firstK.size(), 2, false, false);
		for (int index = 0; index < firstK.size(); index++) {
			require(!handoff.requiresRanking(),
					"unprioritized candidates must not request distance or priority computation");
			handoff.offerUnranked();
		}
		handoff.complete();
		require(handoff.allocatedSelectionEntriesForTesting() == 0,
				"unprioritized Guard mode must retain its allocation-free first-k fast path");
		require(handoff.applyTo(firstK) == 1 && firstK.equals(List.of(first, second)),
				"unprioritized coordination must keep the exact first-k order");

		EqualValue equalFirst = new EqualValue(9);
		EqualValue equalSecond = new EqualValue(9);
		ArrayList<EqualValue> identities = new ArrayList<>(List.of(equalFirst, equalSecond));
		BlockEntityGuardHandoff<EqualValue> identityHandoff = new BlockEntityGuardHandoff<>();
		identityHandoff.begin(identities.size(), 1, true, false);
		identityHandoff.offer(equalFirst, false, false, 10.0D, 0);
		identityHandoff.offer(equalSecond, false, false, 1.0D, 1);
		identityHandoff.complete();
		require(equalFirst.equals(equalSecond), "test values must compare equal while remaining distinct");
		require(identityHandoff.applyTo(identities) == 1
					&& identities.size() == 1
					&& identities.getFirst() == equalSecond,
				"coordinated membership must continue using identity rather than equals");

		Object retainedDuringPlanning = new Object();
		BlockEntityGuardHandoff<Object> aborted = new BlockEntityGuardHandoff<>();
		aborted.begin(4, 1, true, true);
		aborted.offer(retainedDuringPlanning, false, false, 1.0D, 0);
		require(aborted.retainsReferenceForTesting(retainedDuringPlanning),
				"the active handoff must retain its current-frame candidate");
		aborted.abort();
		require(!aborted.isCompleteForSize(1)
					&& !aborted.retainsReferenceForTesting(retainedDuringPlanning),
				"failure, disable, and lifecycle aborts must release candidates and force fallback");

		BlockEntityGuardHandoff<Object> mismatched = new BlockEntityGuardHandoff<>();
		mismatched.begin(2, 1, true, false);
		mismatched.offer(retainedDuringPlanning, false, false, 1.0D, 0);
		mismatched.complete();
		boolean mismatchRejected = false;
		try {
			mismatched.applyTo(new ArrayList<>());
		} catch (IllegalStateException expected) {
			mismatchRejected = true;
		}
		require(mismatchRejected && !mismatched.retainsReferenceForTesting(retainedDuringPlanning),
				"a mismatched consumer list must clear references before standalone fallback");
	}

	private static void compactsDisplayStatesWithoutChangingOrderOrOccurrences() {
		Object nonDisplay = new Object();
		EqualValue first = new EqualValue(1);
		EqualValue second = new EqualValue(1);
		List<Object> input = List.of(nonDisplay, first, second, nonDisplay, first, second, nonDisplay);
		for (int budget = 0; budget <= 5; budget++) {
			List<Object> expected = new ArrayList<>();
			int accepted = 0;
			for (Object state : input) {
				if (!(state instanceof EqualValue) || accepted++ < budget) {
					expected.add(state);
				}
			}
			ArrayList<Object> actual = new ArrayList<>(input);
			int removed = DisplayEntityCompaction.limitFirst(actual, budget, state -> state instanceof EqualValue);
			require(removed == input.size() - expected.size(), "first-k compaction must report exact rejections");
			requireSameOccurrences(actual, expected);
		}
		for (int[] indices : List.of(new int[0], new int[]{2, 4}, new int[]{1, 2, 4, 5})) {
			ArrayList<Object> actual = new ArrayList<>(input);
			List<Object> expected = new ArrayList<>();
			for (int index = 0; index < input.size(); index++) {
				if (!(input.get(index) instanceof EqualValue) || Arrays.binarySearch(indices, index) >= 0) {
					expected.add(input.get(index));
				}
			}
			int removed = DisplayEntityCompaction.retainSelected(
					actual, indices, indices.length, state -> state instanceof EqualValue);
			require(removed == input.size() - expected.size(), "ranked compaction must report exact rejections");
			requireSameOccurrences(actual, expected);
		}
		CountingArrayList<Object> empty = new CountingArrayList<>();
		require(DisplayEntityCompaction.limitFirst(empty, 0, state -> true) == 0,
				"empty first-k input must be a no-op");
		require(DisplayEntityCompaction.retainSelected(empty, new int[0], 0, state -> true) == 0,
				"empty ranked input must be a no-op");
		require(empty.tailClears == 0 && empty.writes == 0, "empty inputs must not be mutated");
	}

	private static void boundsDisplayCompactionWorkUnderOverload() {
		for (int count : new int[]{10_000, 80_000}) {
			for (boolean ranked : new boolean[]{false, true}) {
				CountingArrayList<RankedState> states = new CountingArrayList<>();
				List<RankedState> expected = new ArrayList<>();
				int budget = 256;
				int[] indices = new int[budget];
				for (int index = 0; index < budget; index++) {
					indices[index] = (count - budget + index) * 2;
				}
				for (int index = 0; index < count * 2; index++) {
					RankedState state = new RankedState(index);
					states.add(state);
					if (index % 2 != 0 || (ranked ? index >= indices[0] : index < budget * 2)) {
						expected.add(state);
					}
				}
				int removed = ranked
						? DisplayEntityCompaction.retainSelected(states, indices, budget, state -> state.index() % 2 == 0)
						: DisplayEntityCompaction.limitFirst(states, budget, state -> state.index() % 2 == 0);
				require(removed == count - budget, "both display paths must enforce the exact display budget");
				requireLinearCompaction(states, count * 2);
				requireSameOccurrences(states, expected);
			}
		}
	}

	private static void boundsBlockEntityCompactionWorkUnderOverload() {
		for (int count : new int[]{10_000, 80_000}) {
			CountingArrayList<RankedState> states = new CountingArrayList<>();
			List<RankedState> expected = new ArrayList<>();
			BlockEntityGuardHandoff<RankedState> handoff = new BlockEntityGuardHandoff<>();
			int budget = 256;
			handoff.begin(count, budget, true, false);
			for (int index = 0; index < count; index++) {
				RankedState state = new RankedState(index);
				states.add(state);
				handoff.offer(state, false, false, count - index, index);
				if (index >= count - budget) {
					expected.add(state);
				}
			}
			handoff.complete();
			require(handoff.applyTo(states) == count - budget, "handoff must retain only the nearest budget");
			requireLinearCompaction(states, count);
			requireSameOccurrences(states, expected);
			require(!handoff.retainsReferenceForTesting(expected.getFirst()),
					"compaction must release handoff membership after consumption");
		}
	}

	private static void requireLinearCompaction(CountingArrayList<?> states, int originalSize) {
		require(states.visits() == originalSize, "compaction must read each original state exactly once");
		require(states.writes <= originalSize, "compaction must perform at most one write per state");
		require(states.individualRemovals == 0 && states.shiftedReferences == 0,
				"overload must never trigger individual removals or shift an ArrayList suffix");
		require(states.tailClears == 1, "overload must clear the discarded tail exactly once");
	}

	private static void requireSameOccurrences(List<?> actual, List<?> expected) {
		require(actual.size() == expected.size(), "compaction must retain the expected number of states");
		for (int index = 0; index < expected.size(); index++) {
			require(actual.get(index) == expected.get(index),
					"compaction must preserve exact occurrences and non-display order at " + index);
		}
	}

	private static <T> Set<T> newIdentitySet() {
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}

	private record EqualValue(int value) {
	}

	private record RankedState(int index) {
	}

	private record ParticleCandidate(
			Object value,
			boolean special,
			double distanceSquared,
			int originalIndex
	) {
	}

	private static final class CountingArrayList<T> extends ArrayList<T> {
		private int visits;
		private int writes;
		private int individualRemovals;
		private int tailClears;
		private long shiftedReferences;

		@Override
		public T get(int index) {
			this.visits++;
			return super.get(index);
		}

		@Override
		public T set(int index, T value) {
			this.writes++;
			return super.set(index, value);
		}

		@Override
		public T remove(int index) {
			this.individualRemovals++;
			this.shiftedReferences += this.size() - index - 1;
			return super.remove(index);
		}

		@Override
		protected void removeRange(int fromIndex, int toIndex) {
			this.tailClears++;
			this.shiftedReferences += this.size() - toIndex;
			super.removeRange(fromIndex, toIndex);
		}

		private int visits() {
			return this.visits;
		}

		@Override
		public Iterator<T> iterator() {
			Iterator<T> delegate = super.iterator();
			return new Iterator<>() {
				@Override
				public boolean hasNext() {
					return delegate.hasNext();
				}

				@Override
				public T next() {
					CountingArrayList.this.visits++;
					return delegate.next();
				}

				@Override
				public void remove() {
					delegate.remove();
				}
			};
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
