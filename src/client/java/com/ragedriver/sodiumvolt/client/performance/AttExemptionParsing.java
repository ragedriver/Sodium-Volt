package com.ragedriver.sodiumvolt.client.performance;

import java.util.Arrays;

/**
 * Dependency-free, bounded validation shared by config and resource exemption
 * loading. Minecraft identifiers are deliberately accepted only in their
 * canonical lowercase namespace:path form.
 */
public final class AttExemptionParsing {
	public static final int MAX_USER_ENTRIES = 64;
	public static final int MAX_RESOURCE_ENTRIES = 64;
	public static final int MAX_IDENTIFIER_LENGTH = 128;
	public static final int MAX_RESOURCE_FILES = 64;
	public static final int MAX_FILE_BYTES = 16 * 1024;
	public static final int MAX_TOTAL_BYTES = 128 * 1024;

	private AttExemptionParsing() {
	}

	public static String[] normalizeUserEntries(String[] values) {
		return normalize(values, MAX_USER_ENTRIES);
	}

	public static String[] normalize(String[] values, int maximumEntries) {
		if (values == null || values.length == 0 || maximumEntries <= 0) {
			return new String[0];
		}
		int limit = Math.min(values.length, maximumEntries);
		String[] accepted = new String[limit];
		int count = 0;
		for (int index = 0; index < values.length && count < maximumEntries; index++) {
			String value = values[index];
			if (value == null) {
				continue;
			}
			value = value.trim();
			if (!isValidIdentifier(value) || contains(accepted, count, value)) {
				continue;
			}
			accepted[count++] = value;
		}
		return count == accepted.length ? accepted : Arrays.copyOf(accepted, count);
	}

	public static boolean isValidIdentifier(String value) {
		if (value == null || value.isEmpty() || value.length() > MAX_IDENTIFIER_LENGTH) {
			return false;
		}
		int separator = value.indexOf(':');
		if (separator <= 0 || separator != value.lastIndexOf(':') || separator == value.length() - 1) {
			return false;
		}
		for (int index = 0; index < separator; index++) {
			char character = value.charAt(index);
			if (!isNamespaceCharacter(character)) {
				return false;
			}
		}
		for (int index = separator + 1; index < value.length(); index++) {
			char character = value.charAt(index);
			if (!isPathCharacter(character)) {
				return false;
			}
		}
		return true;
	}

	private static boolean contains(String[] values, int count, String candidate) {
		for (int index = 0; index < count; index++) {
			if (candidate.equals(values[index])) {
				return true;
			}
		}
		return false;
	}

	private static boolean isNamespaceCharacter(char character) {
		return character >= 'a' && character <= 'z'
				|| character >= '0' && character <= '9'
				|| character == '_'
				|| character == '-'
				|| character == '.';
	}

	private static boolean isPathCharacter(char character) {
		return isNamespaceCharacter(character) || character == '/';
	}
}
