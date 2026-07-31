package com.ragedriver.sodiumvolt.client.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

final class ConfigFactoryDefaults {
	private ConfigFactoryDefaults() {
	}

	static void copyMutableFields(Object target, Object defaults) {
		if (target == null || defaults == null || target.getClass() != defaults.getClass()) {
			throw new IllegalArgumentException("Factory default types must match");
		}
		List<FieldValue> values = new ArrayList<>();
		for (Field field : target.getClass().getDeclaredFields()) {
			int modifiers = field.getModifiers();
			if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
				continue;
			}
			if (!field.trySetAccessible()) {
				throw new IllegalStateException("Could not access a config field");
			}
			try {
				values.add(new FieldValue(field, field.get(target), field.get(defaults)));
			} catch (IllegalAccessException exception) {
				throw new IllegalStateException("Could not inspect config defaults", exception);
			}
		}
		int copied = 0;
		try {
			for (FieldValue value : values) {
				value.field.set(target, value.factoryValue);
				copied++;
			}
		} catch (IllegalAccessException | RuntimeException exception) {
			for (int index = copied - 1; index >= 0; index--) {
				FieldValue value = values.get(index);
				try {
					value.field.set(target, value.originalValue);
				} catch (IllegalAccessException | RuntimeException ignored) {
					// Accessibility was validated before mutation; rollback is best effort.
				}
			}
			throw new IllegalStateException("Could not copy config defaults", exception);
		}
	}

	static long nextRevision(long revision) {
		return revision == Long.MAX_VALUE ? Long.MAX_VALUE : revision + 1L;
	}

	private record FieldValue(Field field, Object originalValue, Object factoryValue) {
	}
}
