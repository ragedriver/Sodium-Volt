package com.ragedriver.sodiumvolt.client.resourcepack;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.function.LongPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ShieldArchiveOrphanCleanup {
	private static final String PREFIX = "sodium-volt-pack-";
	private static final Pattern OWNED_PATH = Pattern.compile(PREFIX + "([1-9][0-9]*)-[0-9]+(\\.zip)?");
	private static final Path ARCHIVE_NAME = Path.of("pack.zip");
	private static final int MAXIMUM_CANDIDATES = 512;
	private static final long MAXIMUM_SWEEP_NANOS = 50_000_000L;

	private ShieldArchiveOrphanCleanup() {
	}

	static Destination createDestination() throws IOException {
		Path root = TemporaryDirectory.PATH;
		String prefix = PREFIX + ProcessHandle.current().pid() + "-";
		if (!TemporaryDirectory.SUPPORTS_SECURE_DIRECTORIES) {
			// A flat file can be reclaimed without resolving a child through an untrusted directory.
			return new Destination(null, Files.createTempFile(root, prefix, ".zip"));
		}
		Path directory = Files.createTempDirectory(root, prefix);
		try {
			return new Destination(directory, Files.createFile(directory.resolve(ARCHIVE_NAME)));
		} catch (IOException | RuntimeException exception) {
			try {
				Files.deleteIfExists(directory);
			} catch (IOException | RuntimeException cleanupFailure) {
				exception.addSuppressed(cleanupFailure);
			}
			throw exception;
		}
	}

	private static final class TemporaryDirectory {
		private static final Path PATH = Path.of(System.getProperty("java.io.tmpdir"));
		private static final boolean SUPPORTS_SECURE_DIRECTORIES = supportsSecureDirectories(PATH);

		private static boolean supportsSecureDirectories(Path path) {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
				return stream instanceof SecureDirectoryStream<?>;
			} catch (IOException | RuntimeException ignored) {
				return false;
			}
		}
	}

	record Destination(Path directory, Path archive) {
	}

	static void reclaimOrphans() {
		try {
			Path temporaryDirectory = TemporaryDirectory.PATH;
			UserPrincipal owner = FileSystems.getDefault().getUserPrincipalLookupService()
					.lookupPrincipalByName(System.getProperty("user.name"));
			reclaimOrphans(temporaryDirectory, owner,
					pid -> ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
		} catch (IOException | RuntimeException ignored) {
			// Reclamation is optional when process ownership or safe traversal is unavailable.
		}
	}

	static void reclaimOrphans(Path temporaryDirectory, UserPrincipal owner, LongPredicate activeProcess)
			throws IOException {
		long startedNanos = System.nanoTime();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(temporaryDirectory, PREFIX + "*")) {
			SecureDirectoryStream<Path> secureRoot =
					stream instanceof SecureDirectoryStream<Path> secure ? secure : null;
			int inspected = 0;
			for (Path candidate : stream) {
				if (++inspected > MAXIMUM_CANDIDATES
						|| System.nanoTime() - startedNanos >= MAXIMUM_SWEEP_NANOS) {
					break;
				}
				Path name = candidate.getFileName();
				Matcher matcher = OWNED_PATH.matcher(name.toString());
				if (!matcher.matches()) {
					continue;
				}
				try {
					long pid = Long.parseLong(matcher.group(1));
					// A reused PID is intentionally treated as active.
					if (activeProcess.test(pid)) {
						continue;
					}
					if (matcher.group(2) != null) {
						reclaimFlatArchive(candidate, owner);
					} else if (secureRoot != null) {
						reclaimCandidate(secureRoot, name, owner);
					}
				} catch (IOException | RuntimeException ignored) {
					// Leave entries whose process, ownership, or shape cannot be established.
				}
			}
		}
	}

	private static void reclaimFlatArchive(Path archive, UserPrincipal owner) throws IOException {
		BasicFileAttributes attributes = Files.readAttributes(
				archive, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
		);
		if (attributes.isRegularFile() && owner.equals(Files.getOwner(archive, LinkOption.NOFOLLOW_LINKS))) {
			Files.deleteIfExists(archive);
		}
	}

	private static void reclaimCandidate(SecureDirectoryStream<Path> root, Path name, UserPrincipal owner)
			throws IOException {
		try (SecureDirectoryStream<Path> directory = root.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
			PosixFileAttributeView directoryAttributes = directory.getFileAttributeView(PosixFileAttributeView.class);
			if (directoryAttributes == null || !owner.equals(directoryAttributes.getOwner())) {
				return;
			}
			var children = directory.iterator();
			if (children.hasNext()) {
				if (!ARCHIVE_NAME.equals(children.next().getFileName()) || children.hasNext()) {
					return;
				}
				BasicFileAttributeView archive = directory.getFileAttributeView(
						ARCHIVE_NAME, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS
				);
				if (archive == null || !archive.readAttributes().isRegularFile()) {
					return;
				}
				directory.deleteFile(ARCHIVE_NAME);
			}
		}
		root.deleteDirectory(name);
	}
}
