package com.ragedriver.sodiumvolt.client.guard;

import java.util.ArrayList;
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
		recyclesBoundedSelectorEntries();
		reusesSelectorStorageAcrossFrames();
		preservesIdentityMembershipAcrossResets();
		coordinatesBlockEntityRankingWithoutASurvivorRescan();
		preservesBlockEntityHandoffSemanticsAndLifecycle();
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

	private static void recyclesBoundedSelectorEntries() {
		BoundedTopK<Object> selector = new BoundedTopK<>(3, true);
		for (int index = 0; index < 1_000; index++) {
			selector.offer(new Object(), false, false, 1_000.0D - index, index);
		}
		require(selector.size() == 3, "Selector must retain only its bounded capacity");
		require(selector.allocatedEntryCount() == 3, "Selector must recycle entries instead of allocating per offer");
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

	private static <T> Set<T> newIdentitySet() {
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}

	private record EqualValue(int value) {
	}

	private record RankedState(int index) {
	}

	private static final class CountingArrayList<T> extends ArrayList<T> {
		private int visits;

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
