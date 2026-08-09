package com.ragedriver.sodiumvolt.client.profile;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * One-entry context cache owned by the serialized Profiles engine.
 *
 * <p>Runtime identity tokens are weak so this cache cannot extend the lifetime of a
 * client level, connection, server descriptor, address string, or integrated server.
 * The cached value must contain only the already-derived privacy-preserving identity.</p>
 */
final class ProfileContextIdentityCache<T> {
	private SourceKind sourceKind;
	private WeakReference<Object> levelToken;
	private WeakReference<Object> connectionToken;
	private WeakReference<Object> sourceToken;
	private WeakReference<Object> detailToken;
	private long contextRevision = Long.MIN_VALUE;
	private boolean cached;
	private T value;

	boolean matches(
			SourceKind expectedKind,
			Object expectedLevel,
			Object expectedConnection,
			Object expectedSource,
			Object expectedDetail,
			long expectedRevision
	) {
		if (!this.cached) {
			return false;
		}
		if (this.sourceKind == expectedKind
				&& matches(this.levelToken, expectedLevel)
				&& matches(this.connectionToken, expectedConnection)
				&& matches(this.sourceToken, expectedSource)
				&& matches(this.detailToken, expectedDetail)
				&& this.contextRevision == expectedRevision) {
			return true;
		}
		// A transition must discard the old derived key before new identity work starts.
		clear();
		return false;
	}

	T value() {
		return this.value;
	}

	void store(
			SourceKind newKind,
			Object newLevel,
			Object newConnection,
			Object newSource,
			Object newDetail,
			long newRevision,
			T newValue
	) {
		this.sourceKind = Objects.requireNonNull(newKind, "newKind");
		this.levelToken = weak(Objects.requireNonNull(newLevel, "newLevel"));
		this.connectionToken = weak(newConnection);
		this.sourceToken = weak(Objects.requireNonNull(newSource, "newSource"));
		this.detailToken = weak(newDetail);
		this.contextRevision = newRevision;
		this.value = newValue;
		this.cached = true;
	}

	void clear() {
		this.sourceKind = null;
		this.levelToken = null;
		this.connectionToken = null;
		this.sourceToken = null;
		this.detailToken = null;
		this.contextRevision = Long.MIN_VALUE;
		this.cached = false;
		this.value = null;
	}

	boolean isCached() {
		return this.cached;
	}

	private static WeakReference<Object> weak(Object value) {
		return value == null ? null : new WeakReference<>(value);
	}

	private static boolean matches(WeakReference<Object> cached, Object current) {
		return current == null ? cached == null : cached != null && cached.get() == current;
	}

	enum SourceKind {
		SINGLE_PLAYER,
		SERVER
	}
}
