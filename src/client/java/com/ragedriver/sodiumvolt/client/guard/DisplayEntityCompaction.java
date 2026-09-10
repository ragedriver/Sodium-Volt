package com.ragedriver.sodiumvolt.client.guard;

import java.util.List;
import java.util.function.Predicate;

/** Stable, allocation-free compaction of Minecraft's random-access render-state lists. */
final class DisplayEntityCompaction {
	private DisplayEntityCompaction() {
	}

	static <T> int limitFirst(List<T> states, int budget, Predicate<T> isDisplay) {
		int originalSize = states.size();
		int accepted = 0;
		int retained = 0;
		for (int index = 0; index < originalSize; index++) {
			T state = states.get(index);
			if (isDisplay.test(state) && accepted++ >= budget) {
				continue;
			}
			if (retained != index) {
				states.set(retained, state);
			}
			retained++;
		}
		return clearTail(states, retained, originalSize);
	}

	/** selectedIndices contains sorted original indices for the selected display occurrences. */
	static <T> int retainSelected(
			List<T> states, int[] selectedIndices, int selectedCount, Predicate<T> isDisplay
	) {
		int originalSize = states.size();
		int selectedCursor = 0;
		int retained = 0;
		for (int index = 0; index < originalSize; index++) {
			T state = states.get(index);
			if (isDisplay.test(state)) {
				if (selectedCursor < selectedCount && selectedIndices[selectedCursor] == index) {
					selectedCursor++;
				} else {
					continue;
				}
			}
			if (retained != index) {
				states.set(retained, state);
			}
			retained++;
		}
		return clearTail(states, retained, originalSize);
	}

	private static <T> int clearTail(List<T> states, int retained, int originalSize) {
		// Repeated individual removals cause quadratic ArrayList shifts under overload.
		if (retained < originalSize) {
			states.subList(retained, originalSize).clear();
		}
		return originalSize - retained;
	}
}
