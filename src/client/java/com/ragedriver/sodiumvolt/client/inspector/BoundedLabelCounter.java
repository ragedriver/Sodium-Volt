package com.ragedriver.sodiumvolt.client.inspector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BoundedLabelCounter {
	private final int maximumLabels;
	private final HashMap<String, MutableCount> counts;
	private int total;
	private int unclassified;

	public BoundedLabelCounter(int maximumLabels) {
		if (maximumLabels < 1) {
			throw new IllegalArgumentException("maximumLabels must be positive");
		}
		this.maximumLabels = maximumLabels;
		this.counts = new HashMap<>(maximumLabels);
	}

	public void add(String label) {
		this.total = saturatingIncrement(this.total);
		MutableCount count = this.counts.get(label);
		if (count != null) {
			count.value = saturatingIncrement(count.value);
		} else if (this.counts.size() < this.maximumLabels) {
			this.counts.put(label, new MutableCount());
		} else {
			this.unclassified = saturatingIncrement(this.unclassified);
		}
	}

	public List<Entry> top(int maximumEntries) {
		int limit = Math.max(0, maximumEntries);
		ArrayList<Entry> values = new ArrayList<>(Math.min(this.counts.size() + 1, this.maximumLabels + 1));
		for (Map.Entry<String, MutableCount> entry : this.counts.entrySet()) {
			values.add(new Entry(entry.getKey(), entry.getValue().value));
		}
		if (this.unclassified > 0) {
			values.add(new Entry("other implementations", this.unclassified));
		}
		values.sort(Comparator.comparingInt(Entry::count).reversed().thenComparing(Entry::label));
		if (values.size() > limit) {
			values.subList(limit, values.size()).clear();
		}
		return List.copyOf(values);
	}

	public int total() {
		return this.total;
	}

	public int distinctLabels() {
		return this.counts.size();
	}

	private static int saturatingIncrement(int value) {
		return value == Integer.MAX_VALUE ? value : value + 1;
	}

	public record Entry(String label, int count) {
	}

	private static final class MutableCount {
		private int value = 1;
	}
}
