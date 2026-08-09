package com.ragedriver.sodiumvolt.client.performance;

/**
 * Fixed-capacity identity cache for immutable block-entity type classifications.
 * Saturation deliberately falls back to the classifier instead of guessing.
 */
final class BlockEntityTypeClassificationCache<T> {
    private static final byte NON_VANILLA = 1;
    private static final byte VANILLA = 2;

    private final Object[] keys;
    private final byte[] classifications;
    private final int maximumEntries;
    private final int mask;
    private int size;

    BlockEntityTypeClassificationCache(int maximumEntries) {
        if (maximumEntries < 1 || maximumEntries > (1 << 29)) {
            throw new IllegalArgumentException("maximumEntries is outside the bounded table range");
        }
        int tableCapacity = 2;
        while (tableCapacity < maximumEntries * 2) {
            tableCapacity <<= 1;
        }
        this.keys = new Object[tableCapacity];
        this.classifications = new byte[tableCapacity];
        this.maximumEntries = maximumEntries;
        this.mask = tableCapacity - 1;
    }

    boolean isVanilla(T type, Classifier<T> classifier) {
        if (type == null) {
            return false;
        }
        int slot = mix(System.identityHashCode(type)) & this.mask;
        for (int probe = 0; probe < this.keys.length; probe++) {
            Object existing = this.keys[slot];
            if (existing == type) {
                return this.classifications[slot] == VANILLA;
            }
            if (existing == null) {
                boolean vanilla = classifier.isVanilla(type);
                if (this.size < this.maximumEntries) {
                    this.keys[slot] = type;
                    this.classifications[slot] = vanilla ? VANILLA : NON_VANILLA;
                    this.size++;
                }
                return vanilla;
            }
            slot = (slot + 1) & this.mask;
        }
        return classifier.isVanilla(type);
    }

    int size() {
        return this.size;
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        return value ^ value >>> 16;
    }

    @FunctionalInterface
    interface Classifier<T> {
        boolean isVanilla(T type);
    }
}
