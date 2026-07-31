package com.ragedriver.sodiumvolt.client.guard;

import java.util.ArrayList;
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

		Set<Object> selected = selector.toIdentitySet();
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

		Set<Object> selected = selector.toIdentitySet();
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

		Set<Object> selected = selector.toIdentitySet();
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

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
