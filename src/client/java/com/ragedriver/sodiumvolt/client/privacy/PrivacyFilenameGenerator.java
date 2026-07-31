package com.ragedriver.sodiumvolt.client.privacy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

public final class PrivacyFilenameGenerator {
	public static final int MAXIMUM_ATTEMPTS = 8;
	private static final int TOKEN_BYTES = 16;
	private static final String PREFIX = "privacy-";
	private static final String SUFFIX = ".png";
	private static final SecureRandom RANDOM = new SecureRandom();

	private PrivacyFilenameGenerator() {
	}

	public static Optional<String> choose(Path screenshotsDirectory) {
		return choose(screenshotsDirectory, RANDOM::nextBytes, MAXIMUM_ATTEMPTS);
	}

	static Optional<String> choose(
			Path screenshotsDirectory,
			TokenSource tokenSource,
			int maximumAttempts
	) {
		if (screenshotsDirectory == null
				|| maximumAttempts < 1
				|| maximumAttempts > MAXIMUM_ATTEMPTS) {
			return Optional.empty();
		}
		Path normalizedDirectory = screenshotsDirectory.toAbsolutePath().normalize();
		try {
			if (Files.exists(normalizedDirectory, LinkOption.NOFOLLOW_LINKS)
					&& (Files.isSymbolicLink(normalizedDirectory)
							|| !Files.isDirectory(
									normalizedDirectory, LinkOption.NOFOLLOW_LINKS
							))) {
				return Optional.empty();
			}
			for (int attempt = 0; attempt < maximumAttempts; attempt++) {
				byte[] token = new byte[TOKEN_BYTES];
				tokenSource.nextBytes(token);
				String filename = PREFIX + HexFormat.of().formatHex(token) + SUFFIX;
				if (!isSafeFilename(filename)) {
					continue;
				}
				Path candidate = normalizedDirectory.resolve(filename).normalize();
				if (!normalizedDirectory.equals(candidate.getParent())) {
					continue;
				}
				if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
					return Optional.of(filename);
				}
			}
		} catch (IOException | RuntimeException exception) {
			return Optional.empty();
		}
		return Optional.empty();
	}

	public static boolean isSafeFilename(String filename) {
		if (filename == null || filename.length() != PREFIX.length() + TOKEN_BYTES * 2
				+ SUFFIX.length() || !filename.startsWith(PREFIX)
				|| !filename.endsWith(SUFFIX)) {
			return false;
		}
		int tokenEnd = filename.length() - SUFFIX.length();
		for (int index = PREFIX.length(); index < tokenEnd; index++) {
			char character = filename.charAt(index);
			if (!((character >= '0' && character <= '9')
					|| (character >= 'a' && character <= 'f'))) {
				return false;
			}
		}
		return true;
	}

	@FunctionalInterface
	interface TokenSource {
		void nextBytes(byte[] destination) throws IOException;
	}
}
