package com.ragedriver.sodiumvolt.client.profile;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class BoundedProfileStore {
	private final int capacity;
	private final LinkedHashMap<String, ProfileSettings> records;

	public BoundedProfileStore(int capacity) {
		if (capacity < 1) {
			throw new IllegalArgumentException("capacity must be positive");
		}
		this.capacity = capacity;
		this.records = new LinkedHashMap<>(capacity, 0.75F, true);
	}

	public ProfileSettings get(String key) {
		return ProfileIdentity.isValidStoredKey(key) ? this.records.get(key) : null;
	}

	public void put(String key, ProfileSettings settings) {
		if (!ProfileIdentity.isValidStoredKey(key)) {
			throw new IllegalArgumentException("Invalid profile key");
		}
		ProfileSettings sanitized = Objects.requireNonNull(settings, "settings").sanitized();
		if (!this.records.containsKey(key) && this.records.size() >= this.capacity) {
			String eldest = this.records.keySet().iterator().next();
			this.records.remove(eldest);
		}
		this.records.put(key, sanitized);
	}

	public boolean remove(String key) {
		return ProfileIdentity.isValidStoredKey(key) && this.records.remove(key) != null;
	}

	public int size() {
		return this.records.size();
	}

	public void clear() {
		this.records.clear();
	}

	public Map<String, ProfileSettings> snapshot() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(this.records));
	}
}
