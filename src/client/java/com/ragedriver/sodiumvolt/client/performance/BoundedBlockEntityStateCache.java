package com.ragedriver.sodiumvolt.client.performance;

import java.util.Arrays;

final class BoundedBlockEntityStateCache<E, S, T, B> {
	private static final byte EMPTY = 0;
	private static final byte OCCUPIED = 1;
	private static final byte TOMBSTONE = 2;
	private static final int TABLE_SIZE = 8192;
	private static final int TABLE_MASK = TABLE_SIZE - 1;
	private static final int MAX_PROBES = 64;

	private final byte[] status = new byte[TABLE_SIZE];
	private final long[] positions = new long[TABLE_SIZE];
	private final Object[] entities = new Object[TABLE_SIZE];
	private final Object[] states = new Object[TABLE_SIZE];
	private final Object[] types = new Object[TABLE_SIZE];
	private final Object[] blockStates = new Object[TABLE_SIZE];
	private final long[] freshTicks = new long[TABLE_SIZE];
	private final long[] usedTicks = new long[TABLE_SIZE];
	private final int[] occupiedSlots = new int[4096];
	private final int[] occupiedIndices = new int[TABLE_SIZE];
	private int size;
	private int evictionCursor;
	private int sweepCursor;
	private int lastLookupSlot = -1;

	@SuppressWarnings("unchecked")
	S lookup(long position, E entity, T type, B blockState, long gameTick, int ttlTicks) {
		this.lastLookupSlot = find(position);
		if (this.lastLookupSlot < 0) {
			return null;
		}
		int slot = this.lastLookupSlot;
		if (this.entities[slot] != entity
				|| this.types[slot] != type
				|| this.blockStates[slot] != blockState
				|| BlockEntityCadenceLogic.elapsed(gameTick, this.usedTicks[slot]) > Math.max(1, ttlTicks)) {
			removeSlot(slot);
			this.lastLookupSlot = -1;
			return null;
		}
		this.usedTicks[slot] = gameTick;
		return (S) this.states[slot];
	}

	long lastFreshTickForLookup() {
		return this.lastLookupSlot < 0 ? Long.MIN_VALUE : this.freshTicks[this.lastLookupSlot];
	}

	PutResult put(
			long position,
			E entity,
			S state,
			T type,
			B blockState,
			long gameTick,
			int capacity
	) {
		int boundedCapacity = Math.max(1, Math.min(this.occupiedSlots.length, capacity));
		shrinkTo(boundedCapacity);
		int existing = find(position);
		if (existing >= 0) {
			store(existing, position, entity, state, type, blockState, gameTick);
			return PutResult.STORED;
		}

		boolean evicted = false;
		if (this.size >= boundedCapacity) {
			int occupiedIndex = this.evictionCursor % this.size;
			removeSlot(this.occupiedSlots[occupiedIndex]);
			this.evictionCursor = this.size == 0 ? 0 : (occupiedIndex + 1) % this.size;
			evicted = true;
		}
		int slot = insertionSlot(position);
		if (slot < 0) {
			return PutResult.SATURATED;
		}
		this.status[slot] = OCCUPIED;
		this.occupiedIndices[slot] = this.size;
		this.occupiedSlots[this.size++] = slot;
		store(slot, position, entity, state, type, blockState, gameTick);
		return evicted ? PutResult.EVICTED : PutResult.STORED;
	}

	int expire(long gameTick, int ttlTicks, int maximumChecks) {
		int expired = 0;
		int checked = 0;
		int limit = Math.max(1, maximumChecks);
		while (this.size > 0 && checked++ < limit) {
			if (this.sweepCursor >= this.size) {
				this.sweepCursor = 0;
			}
			int slot = this.occupiedSlots[this.sweepCursor];
			if (BlockEntityCadenceLogic.elapsed(gameTick, this.usedTicks[slot]) > Math.max(1, ttlTicks)) {
				removeSlot(slot);
				expired++;
			} else {
				this.sweepCursor++;
			}
		}
		return expired;
	}

	void shrinkTo(int capacity) {
		int boundedCapacity = Math.max(0, Math.min(this.occupiedSlots.length, capacity));
		while (this.size > boundedCapacity) {
			removeSlot(this.occupiedSlots[this.size - 1]);
		}
	}

	void clear() {
		while (this.size > 0) {
			removeSlot(this.occupiedSlots[this.size - 1]);
		}
		Arrays.fill(this.status, EMPTY);
		this.evictionCursor = 0;
		this.sweepCursor = 0;
		this.lastLookupSlot = -1;
	}

	int size() {
		return this.size;
	}

	boolean retainsForTesting(E entity, S state) {
		for (int index = 0; index < this.size; index++) {
			int slot = this.occupiedSlots[index];
			if (this.entities[slot] == entity || this.states[slot] == state) {
				return true;
			}
		}
		return false;
	}

	private void store(
			int slot,
			long position,
			E entity,
			S state,
			T type,
			B blockState,
			long gameTick
	) {
		this.positions[slot] = position;
		this.entities[slot] = entity;
		this.states[slot] = state;
		this.types[slot] = type;
		this.blockStates[slot] = blockState;
		this.freshTicks[slot] = gameTick;
		this.usedTicks[slot] = gameTick;
		this.lastLookupSlot = slot;
	}

	private int find(long position) {
		int index = mix(position) & TABLE_MASK;
		for (int probe = 0; probe < MAX_PROBES; probe++) {
			byte slotStatus = this.status[index];
			if (slotStatus == EMPTY) {
				return -1;
			}
			if (slotStatus == OCCUPIED && this.positions[index] == position) {
				return index;
			}
			index = (index + 1) & TABLE_MASK;
		}
		return -1;
	}

	private int insertionSlot(long position) {
		int index = mix(position) & TABLE_MASK;
		int tombstone = -1;
		for (int probe = 0; probe < MAX_PROBES; probe++) {
			byte slotStatus = this.status[index];
			if (slotStatus == EMPTY) {
				return tombstone >= 0 ? tombstone : index;
			}
			if (slotStatus == TOMBSTONE && tombstone < 0) {
				tombstone = index;
			}
			index = (index + 1) & TABLE_MASK;
		}
		return tombstone;
	}

	private void removeSlot(int slot) {
		int occupiedIndex = this.occupiedIndices[slot];
		int lastSlot = this.occupiedSlots[this.size - 1];
		this.occupiedSlots[occupiedIndex] = lastSlot;
		this.occupiedIndices[lastSlot] = occupiedIndex;
		this.occupiedSlots[this.size - 1] = 0;
		this.size--;
		this.status[slot] = TOMBSTONE;
		this.positions[slot] = 0L;
		this.entities[slot] = null;
		this.states[slot] = null;
		this.types[slot] = null;
		this.blockStates[slot] = null;
		this.freshTicks[slot] = 0L;
		this.usedTicks[slot] = 0L;
		this.occupiedIndices[slot] = 0;
		this.lastLookupSlot = -1;
		if (this.sweepCursor > this.size) {
			this.sweepCursor = this.size;
		}
	}

	private static int mix(long value) {
		value ^= value >>> 33;
		value *= 0xFF51AFD7ED558CCDL;
		value ^= value >>> 33;
		value *= 0xC4CEB9FE1A85EC53L;
		value ^= value >>> 33;
		return (int) value;
	}

	enum PutResult {
		STORED,
		EVICTED,
		SATURATED
	}
}
