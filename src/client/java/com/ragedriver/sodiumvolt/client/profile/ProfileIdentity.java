package com.ragedriver.sodiumvolt.client.profile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

public final class ProfileIdentity {
	public static final int SALT_BYTES = 16;
	public static final int HASH_HEX_LENGTH = 64;
	public static final int MAXIMUM_IDENTITY_CHARACTERS = 512;
	private static final HexFormat HEX = HexFormat.of();

	private ProfileIdentity() {
	}

	public static Optional<String> serverKey(String address, byte[] salt) {
		return key("server", address, salt, true);
	}

	public static Optional<String> singlePlayerKey(String worldName, byte[] salt) {
		return key("single-player", worldName, salt, false);
	}

	public static boolean isValidStoredKey(String value) {
		if (value == null || value.length() != HASH_HEX_LENGTH) {
			return false;
		}
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (character < '0' || character > '9'
					&& (character < 'a' || character > 'f')) {
				return false;
			}
		}
		return true;
	}

	public static byte[] parseSalt(String value) {
		if (value == null || value.length() != SALT_BYTES * 2) {
			throw new IllegalArgumentException("Invalid profile identity salt");
		}
		try {
			return HEX.parseHex(value);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid profile identity salt", exception);
		}
	}

	public static String formatSalt(byte[] salt) {
		validateSalt(salt);
		return HEX.formatHex(salt);
	}

	private static Optional<String> key(
			String context,
			String rawIdentity,
			byte[] salt,
			boolean caseFold
	) {
		validateSalt(salt);
		Optional<String> normalized = normalize(rawIdentity, caseFold);
		if (normalized.isEmpty()) {
			return Optional.empty();
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(salt);
			digest.update((byte) 0);
			digest.update(context.getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 0);
			digest.update(normalized.get().getBytes(StandardCharsets.UTF_8));
			return Optional.of(HEX.formatHex(digest.digest()));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	static Optional<String> normalize(String rawIdentity, boolean caseFold) {
		if (rawIdentity == null
				|| rawIdentity.length() > MAXIMUM_IDENTITY_CHARACTERS) {
			return Optional.empty();
		}
		String normalized = Normalizer.normalize(
				rawIdentity.strip(), Normalizer.Form.NFKC
		);
		if (caseFold) {
			normalized = normalized.toLowerCase(Locale.ROOT);
			while (normalized.endsWith(".")) {
				normalized = normalized.substring(0, normalized.length() - 1);
			}
		}
		if (normalized.isEmpty()
				|| normalized.length() > MAXIMUM_IDENTITY_CHARACTERS) {
			return Optional.empty();
		}
		for (int offset = 0; offset < normalized.length();) {
			int codePoint = normalized.codePointAt(offset);
			if (Character.isISOControl(codePoint)) {
				return Optional.empty();
			}
			offset += Character.charCount(codePoint);
		}
		return Optional.of(normalized);
	}

	private static void validateSalt(byte[] salt) {
		if (salt == null || salt.length != SALT_BYTES) {
			throw new IllegalArgumentException("Invalid profile identity salt");
		}
	}
}
