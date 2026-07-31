package com.ragedriver.sodiumvolt.client.resourcepack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Set;

public final class DirectoryPackScanner {
	private DirectoryPackScanner() {
	}

	public static ShieldScanResult scan(
			Path suppliedRoot,
			ResourcePackShieldPolicy policy
	) throws IOException {
		return scan(suppliedRoot, policy, ShieldOverlayPlan.EMPTY);
	}

	public static ShieldScanResult scan(
			Path suppliedRoot,
			ResourcePackShieldPolicy policy,
			ShieldOverlayPlan overlays
	) throws IOException {
		return scan(
				suppliedRoot,
				policy,
				overlays,
				ZipPackScanner.deadline(policy.maximumScanNanos())
		);
	}

	static ShieldScanResult scan(
			Path suppliedRoot,
			ResourcePackShieldPolicy policy,
			ShieldOverlayPlan overlays,
			long deadline
	) throws IOException {
		if (Files.isSymbolicLink(suppliedRoot)) {
			return failed(ShieldReason.SYMLINK, 0, 0L, 0L);
		}
		BasicFileAttributes rootAttributes = Files.readAttributes(
				suppliedRoot, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
		);
		if (!rootAttributes.isDirectory()) {
			return failed(ShieldReason.SPECIAL_FILE, 0, 0L, 0L);
		}
		Path root = suppliedRoot.toAbsolutePath().normalize();
		ShieldReason overlayReason = validateOverlayRoots(root, overlays, deadline);
		if (overlayReason != ShieldReason.NONE) {
			return failed(overlayReason, 0, 0L, 0L);
		}
		ArrayDeque<Path> directories = new ArrayDeque<>();
		directories.push(root);
		int entries = 0;
		long total = 0L;
		long inspected = 0L;

		while (!directories.isEmpty()) {
			if (ZipPackScanner.expired(deadline)) {
				return failed(ShieldReason.SCAN_TIME, entries, total, inspected);
			}
			Path directory = directories.pop();
			if (Files.isSymbolicLink(directory)) {
				return failed(ShieldReason.SYMLINK, entries, total, inspected);
			}
			BasicFileAttributes directoryAttributes = Files.readAttributes(
					directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
			);
			if (!directoryAttributes.isDirectory()) {
				return failed(ShieldReason.SPECIAL_FILE, entries, total, inspected);
			}
			try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
				for (Path child : children) {
					if (ZipPackScanner.expired(deadline)) {
						return failed(ShieldReason.SCAN_TIME, entries, total, inspected);
					}
					if (++entries > policy.maximumEntries()) {
						return failed(ShieldReason.ENTRY_LIMIT, entries, total, inspected);
					}
					Path normalized = child.toAbsolutePath().normalize();
					if (!normalized.startsWith(root)) {
						return failed(ShieldReason.UNSAFE_PATH, entries, total, inspected);
					}
					Path relative = root.relativize(normalized);
					String physicalPath = logicalPath(relative);
					if (!ShieldPathPolicy.isStructurallySafeRelative(physicalPath)) {
						return failed(ShieldReason.UNSAFE_PATH, entries, total, inspected);
					}
					if (policy.detectUnsafePaths()
							&& !ShieldPathPolicy.isSafeRelativePath(
									relative,
									policy.maximumPathLength(),
									policy.maximumPathDepth()
							)) {
						return failed(ShieldReason.UNSAFE_PATH, entries, total, inspected);
					}
					if (Files.isSymbolicLink(child)) {
						return failed(ShieldReason.SYMLINK, entries, total, inspected);
					}
					BasicFileAttributes attributes = Files.readAttributes(
							child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
					);
					if (attributes.isDirectory()) {
						directories.push(child);
						continue;
					}
					if (!attributes.isRegularFile()) {
						return failed(ShieldReason.SPECIAL_FILE, entries, total, inspected);
					}
					long size = attributes.size();
					ShieldReason metadataReason = evaluateSize(size, total, policy);
					if (metadataReason != ShieldReason.NONE) {
						return failed(metadataReason, entries, total, inspected);
					}
					total += size;
					String logicalPath = overlays.effectivePath(physicalPath);
					if (policy.detectUnsafePaths() && !logicalPath.isEmpty()
							&& !ShieldPathPolicy.isSafe(
									logicalPath,
									policy.maximumPathLength(),
									policy.maximumPathDepth()
							)) {
						return failed(ShieldReason.UNSAFE_PATH, entries, total, inspected);
					}
					if (policy.blockCoreShaderOverrides()
							&& ShieldPathPolicy.isCoreShader(logicalPath)) {
						return failed(
								ShieldReason.CORE_SHADER_OVERRIDE, entries, total, inspected
						);
					}
					String lower = logicalPath.toLowerCase(Locale.ROOT);
					if (lower.endsWith(".png")) {
						try (InputStream input = openNoFollow(child)) {
							ShieldReason reason = ShieldContentValidators.validatePng(input, policy);
							inspected = ZipPackScanner.saturatedAdd(
									inspected, Math.min(size, 24L)
							);
							if (reason != ShieldReason.NONE) {
								return failed(reason, entries, total, inspected);
							}
						}
					} else if (lower.endsWith(".json") || lower.endsWith(".mcmeta")) {
						try (InputStream input = openNoFollow(child)) {
							ShieldReason reason = ShieldContentValidators.validateJson(
									input,
									policy.maximumSingleResourceBytes(),
									policy.maximumJsonDepth(),
									deadline
							);
							inspected = ZipPackScanner.saturatedAdd(inspected, size);
							if (reason != ShieldReason.NONE) {
								return failed(reason, entries, total, inspected);
							}
						}
					}
				}
			}
		}
		return new ShieldScanResult(ShieldReason.NONE, entries, total, inspected);
	}

	private static ShieldReason validateOverlayRoots(
			Path root,
			ShieldOverlayPlan overlays,
			long deadline
	) throws IOException {
		for (String prefix : overlays.prefixes()) {
			if (ZipPackScanner.expired(deadline)) {
				return ShieldReason.SCAN_TIME;
			}
			Path candidate = root.resolve(prefix).normalize();
			if (!candidate.startsWith(root)) {
				return ShieldReason.UNSAFE_PATH;
			}
			Path cursor = root;
			boolean missing = false;
			for (Path component : root.relativize(candidate)) {
				cursor = cursor.resolve(component);
				if (Files.isSymbolicLink(cursor)) {
					return ShieldReason.SYMLINK;
				}
				BasicFileAttributes attributes;
				try {
					attributes = Files.readAttributes(
							cursor,
							BasicFileAttributes.class,
							LinkOption.NOFOLLOW_LINKS
					);
				} catch (NoSuchFileException exception) {
					missing = true;
					break;
				}
				if (!attributes.isDirectory()) {
					return ShieldReason.SPECIAL_FILE;
				}
			}
			if (missing) {
				continue;
			}
		}
		return ShieldReason.NONE;
	}

	static ShieldReason evaluateSize(
			long size,
			long currentTotal,
			ResourcePackShieldPolicy policy
	) {
		if (size < 0L || currentTotal < 0L) {
			return ShieldReason.UNKNOWN_METADATA;
		}
		if (size > policy.maximumSingleResourceBytes()) {
			return ShieldReason.SINGLE_RESOURCE_SIZE;
		}
		if (size > policy.maximumTotalResourceBytes() - currentTotal) {
			return ShieldReason.TOTAL_RESOURCE_SIZE;
		}
		return ShieldReason.NONE;
	}

	private static InputStream openNoFollow(Path path) throws IOException {
		return Channels.newInputStream(FileChannel.open(
				path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
		));
	}

	private static String logicalPath(Path relative) {
		StringBuilder result = new StringBuilder();
		for (Path component : relative) {
			if (!result.isEmpty()) {
				result.append('/');
			}
			result.append(component);
		}
		return result.toString();
	}

	private static ShieldScanResult failed(
			ShieldReason reason,
			int entries,
			long declared,
			long inspected
	) {
		return new ShieldScanResult(reason, entries, declared, inspected);
	}
}
