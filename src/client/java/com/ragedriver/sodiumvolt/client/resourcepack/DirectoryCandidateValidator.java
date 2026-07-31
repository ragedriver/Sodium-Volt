package com.ragedriver.sodiumvolt.client.resourcepack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * Non-optional directory containment and no-follow validation used immediately before
 * vanilla opens a resource supplier.
 */
public final class DirectoryCandidateValidator {
	private DirectoryCandidateValidator() {
	}

	public static ShieldReason validate(List<Path> declaredRoots, String logicalPath)
			throws IOException {
		return validate(declaredRoots, logicalPath, ExpectedType.REGULAR_FILE);
	}

	public static ShieldReason validateDirectoryPrefix(
			List<Path> declaredRoots,
			String logicalPath
	) throws IOException {
		return validate(declaredRoots, logicalPath, ExpectedType.DIRECTORY);
	}

	private static ShieldReason validate(
			List<Path> declaredRoots,
			String logicalPath,
			ExpectedType expectedType
	) throws IOException {
		if (!ShieldPathPolicy.isStructurallySafeRelative(logicalPath)) {
			return ShieldReason.UNSAFE_PATH;
		}
		if (declaredRoots == null || declaredRoots.isEmpty()) {
			return ShieldReason.NONE;
		}
		Path primaryRoot = declaredRoots.getFirst().toAbsolutePath().normalize();
		for (Path suppliedRoot : declaredRoots) {
			Path root = suppliedRoot.toAbsolutePath().normalize();
			if (!root.startsWith(primaryRoot)) {
				return ShieldReason.UNSAFE_PATH;
			}
			Path rootCursor = primaryRoot;
			if (Files.isSymbolicLink(rootCursor)) {
				return ShieldReason.SYMLINK;
			}
			if (!Files.exists(rootCursor, LinkOption.NOFOLLOW_LINKS)) {
				continue;
			}
			BasicFileAttributes primaryAttributes = Files.readAttributes(
					rootCursor,
					BasicFileAttributes.class,
					LinkOption.NOFOLLOW_LINKS
			);
			if (!primaryAttributes.isDirectory()) {
				return ShieldReason.SPECIAL_FILE;
			}
			boolean missingRoot = false;
			for (Path component : primaryRoot.relativize(root)) {
				rootCursor = rootCursor.resolve(component);
				if (Files.isSymbolicLink(rootCursor)) {
					return ShieldReason.SYMLINK;
				}
				if (!Files.exists(rootCursor, LinkOption.NOFOLLOW_LINKS)) {
					missingRoot = true;
					break;
				}
				BasicFileAttributes rootAttributes = Files.readAttributes(
						rootCursor,
						BasicFileAttributes.class,
						LinkOption.NOFOLLOW_LINKS
				);
				if (!rootAttributes.isDirectory()) {
					return ShieldReason.SPECIAL_FILE;
				}
			}
			if (missingRoot) {
				continue;
			}
			Path candidate = root.resolve(logicalPath).normalize();
			if (!candidate.startsWith(root)) {
				return ShieldReason.UNSAFE_PATH;
			}
			Path relative = root.relativize(candidate);
			Path cursor = root;
			int index = 0;
			for (Path component : relative) {
				cursor = cursor.resolve(component);
				index++;
				if (Files.isSymbolicLink(cursor)) {
					return ShieldReason.SYMLINK;
				}
				if (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
					break;
				}
				BasicFileAttributes attributes = Files.readAttributes(
						cursor,
						BasicFileAttributes.class,
						LinkOption.NOFOLLOW_LINKS
				);
				boolean last = index == relative.getNameCount();
				boolean wrongType = !last && !attributes.isDirectory()
						|| last && expectedType == ExpectedType.REGULAR_FILE
								&& !attributes.isRegularFile()
						|| last && expectedType == ExpectedType.DIRECTORY
								&& !attributes.isDirectory();
				if (wrongType) {
					return ShieldReason.SPECIAL_FILE;
				}
			}
		}
		return ShieldReason.NONE;
	}

	private enum ExpectedType {
		REGULAR_FILE,
		DIRECTORY
	}
}
