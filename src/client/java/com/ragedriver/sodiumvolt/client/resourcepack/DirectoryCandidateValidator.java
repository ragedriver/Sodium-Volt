package com.ragedriver.sodiumvolt.client.resourcepack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Non-optional directory containment and no-follow validation used immediately before
 * vanilla opens a resource supplier.
 *
 * <p>The session cache stores only normalized path plans. A cache hit never authorizes
 * a path. Providers with {@link SecureDirectoryStream} support validate ancestors by
 * pinned no-follow traversal and freshly lstat the final entry; other providers fall
 * back to fresh no-follow attributes for every component. No filesystem facts survive
 * the operation.</p>
 */
public final class DirectoryCandidateValidator {
	private static final int MAXIMUM_OPERATION_ATTRIBUTES = 512;
	private static final int MAXIMUM_CACHED_ROOTS =
			ShieldOverlayPlan.MAXIMUM_OVERLAY_PREFIXES + 1;
	private static final int MAXIMUM_CACHED_PLAN_COMPONENTS = 2_048;
	private static final ThreadLocal<OperationAttributes> OPERATION_ATTRIBUTES =
			ThreadLocal.withInitial(OperationAttributes::new);

	private DirectoryCandidateValidator() {
	}

	public static ShieldReason validate(List<Path> declaredRoots, String logicalPath)
			throws IOException {
		return new Cache(declaredRoots, 0, SecureOpenHook.NONE)
				.validate(logicalPath, ExpectedType.REGULAR_FILE);
	}

	public static ShieldReason validateDirectoryPrefix(
			List<Path> declaredRoots,
			String logicalPath
	) throws IOException {
		return new Cache(declaredRoots, 0, SecureOpenHook.NONE)
				.validate(logicalPath, ExpectedType.DIRECTORY);
	}

	static Cache newCache(List<Path> declaredRoots, int maximumEntries) {
		return new Cache(declaredRoots, maximumEntries, SecureOpenHook.NONE);
	}

	static Cache newCacheForTesting(
			List<Path> declaredRoots,
			int maximumEntries,
			SecureOpenHook hook
	) {
		return new Cache(declaredRoots, maximumEntries, hook);
	}

	static int retainedOperationPathsForTesting() {
		return OPERATION_ATTRIBUTES.get().retainedCount();
	}

	static final class Cache {
		private final Path primaryRoot;
		private final List<RootPlan> roots;
		private final ShieldReason rootReason;
		private final int maximumEntries;
		private final Map<String, PathPlan> filePathPlans;
		private final Map<String, PathPlan> directoryPathPlans;
		private final SecureOpenHook secureOpenHook;

		private Cache(
				List<Path> declaredRoots,
				int maximumEntries,
				SecureOpenHook secureOpenHook
		) {
			this.maximumEntries = Math.max(0, maximumEntries);
			this.filePathPlans = new ConcurrentHashMap<>();
			this.directoryPathPlans = new ConcurrentHashMap<>();
			this.secureOpenHook = secureOpenHook;
			RootPlans planned = planRoots(declaredRoots);
			this.primaryRoot = planned.primaryRoot();
			this.roots = planned.roots();
			this.rootReason = planned.reason();
		}

		ShieldReason validate(String logicalPath, ExpectedType expectedType)
				throws IOException {
			if (!ShieldPathPolicy.isStructurallySafeRelative(logicalPath)) {
				return ShieldReason.UNSAFE_PATH;
			}
			if (this.rootReason != ShieldReason.NONE || this.roots.isEmpty()) {
				return this.rootReason;
			}
			OperationAttributes current = OPERATION_ATTRIBUTES.get();
			try {
				ShieldReason rootValidation = current.begin(
						this.primaryRoot, SecureOpenHook.NONE
				);
				if (rootValidation != ShieldReason.NONE) {
					return rootValidation;
				}
				PathPlan plan = cachedPathPlan(logicalPath, expectedType);
				if (plan != null && plan.unsafe()) {
					return ShieldReason.UNSAFE_PATH;
				}
				if (plan != null && !plan.uncached()) {
					return validateCandidates(plan.candidates(), current, expectedType);
				}
				for (RootPlan root : this.roots) {
					CandidatePlan candidate = candidatePlan(root, logicalPath);
					if (candidate == null) {
						return ShieldReason.UNSAFE_PATH;
					}
					ShieldReason reason = validateCandidate(candidate, current, expectedType);
					if (reason != ShieldReason.NONE) {
						return reason;
					}
				}
				return ShieldReason.NONE;
			} finally {
				current.clear();
			}
		}

		SecureOpenResult openFile(String logicalPath, boolean primaryOnly)
				throws IOException {
			if (!ShieldPathPolicy.isStructurallySafeRelative(logicalPath)
					|| this.rootReason != ShieldReason.NONE || this.roots.isEmpty()) {
				return SecureOpenResult.failed(
						this.rootReason == ShieldReason.NONE
								? ShieldReason.UNSAFE_PATH : this.rootReason
				);
			}
			OperationAttributes current = OPERATION_ATTRIBUTES.get();
			try {
				ShieldReason rootValidation = current.begin(
						this.primaryRoot, this.secureOpenHook
				);
				if (rootValidation != ShieldReason.NONE) {
					return SecureOpenResult.failed(rootValidation);
				}
				if (current.secureRoot() == null) {
					return openFileFallback(logicalPath, primaryOnly);
				}
				if (primaryOnly) {
					return openCandidateSecure(
							candidatePlan(this.roots.getFirst(), logicalPath),
							current.secureRoot()
					);
				}
				for (int index = this.roots.size() - 1; index >= 0; index--) {
					SecureOpenResult result = openCandidateSecure(
							candidatePlan(this.roots.get(index), logicalPath),
							current.secureRoot()
					);
					if (result.input() != null || result.reason() != ShieldReason.NONE) {
						return result;
					}
				}
				return SecureOpenResult.missing();
			} finally {
				current.clear();
			}
		}

		private SecureOpenResult openFileFallback(String logicalPath, boolean primaryOnly)
				throws IOException {
			if (primaryOnly) {
				return openCandidateFallback(candidatePlan(this.roots.getFirst(), logicalPath));
			}
			for (int index = this.roots.size() - 1; index >= 0; index--) {
				SecureOpenResult result = openCandidateFallback(
						candidatePlan(this.roots.get(index), logicalPath)
				);
				if (result.input() != null || result.reason() != ShieldReason.NONE) {
					return result;
				}
			}
			return SecureOpenResult.missing();
		}

		private SecureOpenResult openCandidateFallback(CandidatePlan candidate)
				throws IOException {
			if (candidate == null) {
				return SecureOpenResult.failed(ShieldReason.UNSAFE_PATH);
			}
			List<Path> components = new ArrayList<>(candidate.rootComponents());
			components.addAll(candidate.candidateComponents());
			if (components.isEmpty()) {
				return SecureOpenResult.failed(ShieldReason.SPECIAL_FILE);
			}
			List<FileIdentity> before = snapshotIdentities(components);
			for (FileIdentity identity : before) {
				if (identity.type() == FileType.MISSING) {
					return SecureOpenResult.missing();
				}
			}
			ShieldReason reason = validateIdentityTypes(before);
			if (reason != ShieldReason.NONE) {
				return SecureOpenResult.failed(reason);
			}
			this.secureOpenHook.beforeFinalOpen();
			SeekableByteChannel channel;
			try {
				try {
					channel = Files.newByteChannel(
							components.getLast(),
							Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
					);
				} catch (UnsupportedOperationException | IllegalArgumentException exception) {
					// Some virtual/default providers expose no SecureDirectoryStream and
					// also reject NOFOLLOW as an open option. The pre/post identity chain
					// remains authoritative; never delegate to vanilla's path follower.
					channel = Files.newByteChannel(
							components.getLast(), Set.of(StandardOpenOption.READ)
					);
				}
			} catch (NoSuchFileException exception) {
				return SecureOpenResult.failed(ShieldReason.UNSAFE_PATH);
			} catch (java.nio.file.FileSystemException exception) {
				return SecureOpenResult.failed(ShieldReason.SYMLINK);
			}
			boolean transferred = false;
			try {
				List<FileIdentity> after = snapshotIdentities(components);
				if (!before.equals(after)) {
					return SecureOpenResult.failed(ShieldReason.UNSAFE_PATH);
				}
				transferred = true;
				return SecureOpenResult.opened(Channels.newInputStream(channel));
			} finally {
				if (!transferred) {
					channel.close();
				}
			}
		}

		private static List<FileIdentity> snapshotIdentities(List<Path> components)
				throws IOException {
			List<FileIdentity> identities = new ArrayList<>(components.size());
			for (Path component : components) {
				try {
					BasicFileAttributes attributes = Files.readAttributes(
							component, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
					);
					identities.add(new FileIdentity(
							fileType(attributes), identityToken(attributes)
					));
				} catch (NoSuchFileException exception) {
					identities.add(new FileIdentity(FileType.MISSING, null));
				}
			}
			return List.copyOf(identities);
		}

		private static ShieldReason validateIdentityTypes(List<FileIdentity> identities) {
			for (int index = 0; index < identities.size(); index++) {
				FileType type = identities.get(index).type();
				if (type == FileType.MISSING) {
					return ShieldReason.NONE;
				}
				if (type == FileType.SYMLINK) {
					return ShieldReason.SYMLINK;
				}
				boolean last = index == identities.size() - 1;
				if (!last && type != FileType.DIRECTORY
						|| last && type != FileType.REGULAR_FILE) {
					return ShieldReason.SPECIAL_FILE;
				}
			}
			return ShieldReason.NONE;
		}

		private SecureOpenResult openCandidateSecure(
				CandidatePlan candidate,
				SecureDirectoryStream<Path> secureRoot
		) throws IOException {
			if (candidate == null) {
				return SecureOpenResult.failed(ShieldReason.UNSAFE_PATH);
			}
			List<Path> components = secureComponents(candidate);
			if (components.isEmpty()) {
				return SecureOpenResult.failed(ShieldReason.SPECIAL_FILE);
			}
			List<SecureDirectoryStream<Path>> opened = new ArrayList<>();
			SecureDirectoryStream<Path> current = secureRoot;
			SeekableByteChannel resultChannel = null;
			Throwable operationFailure = null;
			try {
				for (int index = 0; index < components.size() - 1; index++) {
					Path name = components.get(index).getFileName();
					try {
						SecureDirectoryStream<Path> next = current.newDirectoryStream(
								name, LinkOption.NOFOLLOW_LINKS
						);
						opened.add(next);
						current = next;
					} catch (NoSuchFileException exception) {
						return SecureOpenResult.missing();
					} catch (NotDirectoryException exception) {
						return SecureOpenResult.failed(classifyFreshPath(
								components.get(index), ExpectedType.DIRECTORY
						));
					}
				}
				Path finalPath = components.getLast();
				FileType finalType = readFileType(finalPath);
				if (finalType == FileType.MISSING) {
					return SecureOpenResult.missing();
				}
				ShieldReason finalReason = reasonFor(finalType, ExpectedType.REGULAR_FILE);
				if (finalReason != ShieldReason.NONE) {
					return SecureOpenResult.failed(finalReason);
				}
				this.secureOpenHook.beforeFinalOpen();
				Path name = finalPath.getFileName();
				try {
					resultChannel = current.newByteChannel(
							name,
							Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
					);
					return SecureOpenResult.opened(Channels.newInputStream(resultChannel));
				} catch (NoSuchFileException exception) {
					return SecureOpenResult.failed(ShieldReason.UNSAFE_PATH);
				} catch (IOException exception) {
					try {
						ShieldReason reason = classifyFreshPath(
								finalPath, ExpectedType.REGULAR_FILE
						);
						return SecureOpenResult.failed(
								reason == ShieldReason.NONE
										? ShieldReason.UNSAFE_PATH : reason
						);
					} catch (IOException secondary) {
						exception.addSuppressed(secondary);
						return SecureOpenResult.failed(ShieldReason.UNSAFE_PATH);
					}
				}
			} catch (IOException | RuntimeException | Error exception) {
				operationFailure = exception;
				throw exception;
			} finally {
				closeOpenedDirectories(opened, resultChannel, operationFailure);
			}
		}

		private static void closeOpenedDirectories(
				List<SecureDirectoryStream<Path>> opened,
				SeekableByteChannel resultChannel,
				Throwable operationFailure
		) throws IOException {
			Throwable cleanupFailure = null;
			for (int index = opened.size() - 1; index >= 0; index--) {
				try {
					opened.get(index).close();
				} catch (IOException | RuntimeException | Error exception) {
					cleanupFailure = mergeCleanupFailure(cleanupFailure, exception);
				}
			}
			// A failed operation or directory cleanup prevents transferring the returned stream to its caller.
			if (resultChannel != null && (operationFailure != null || cleanupFailure != null)) {
				try {
					resultChannel.close();
				} catch (IOException | RuntimeException | Error exception) {
					cleanupFailure = mergeCleanupFailure(cleanupFailure, exception);
				}
			}
			if (cleanupFailure == null) {
				return;
			}
			if (operationFailure != null) {
				if (operationFailure != cleanupFailure) {
					operationFailure.addSuppressed(cleanupFailure);
				}
				return;
			}
			if (cleanupFailure instanceof IOException exception) {
				throw exception;
			}
			if (cleanupFailure instanceof RuntimeException exception) {
				throw exception;
			}
			throw (Error) cleanupFailure;
		}

		private static Throwable mergeCleanupFailure(Throwable previous, Throwable next) {
			if (previous == null) {
				return next;
			}
			if (previous != next) {
				previous.addSuppressed(next);
			}
			return previous;
		}

		private static List<Path> secureComponents(CandidatePlan candidate) {
			List<Path> components = new ArrayList<>(
					candidate.rootComponents().size()
							+ candidate.candidateComponents().size()
			);
			components.addAll(candidate.rootComponents().subList(
					Math.min(1, candidate.rootComponents().size()),
					candidate.rootComponents().size()
			));
			components.addAll(candidate.candidateComponents());
			return components;
		}

		ShieldReason validateFile(String logicalPath) throws IOException {
			return validate(logicalPath, ExpectedType.REGULAR_FILE);
		}

		ShieldReason validateDirectory(String logicalPath) throws IOException {
			return validate(logicalPath, ExpectedType.DIRECTORY);
		}

		private FileType readFresh(Path path, OperationAttributes current)
				throws IOException {
			FileType existing = current.get(path);
			if (existing != null) {
				return existing;
			}
			FileType state;
			try {
				BasicFileAttributes attributes = Files.readAttributes(
						path,
						BasicFileAttributes.class,
						LinkOption.NOFOLLOW_LINKS
				);
				state = fileType(attributes);
			} catch (NoSuchFileException exception) {
				state = FileType.MISSING;
			}
			current.put(path, state);
			return state;
		}

		private ShieldReason validateCandidates(
				List<CandidatePlan> candidates,
				OperationAttributes current,
				ExpectedType expectedType
		) throws IOException {
			for (CandidatePlan candidate : candidates) {
				ShieldReason reason = validateCandidate(candidate, current, expectedType);
				if (reason != ShieldReason.NONE) {
					return reason;
				}
			}
			return ShieldReason.NONE;
		}

		private ShieldReason validateCandidate(
				CandidatePlan candidate,
				OperationAttributes current,
				ExpectedType expectedType
		) throws IOException {
			if (current.secureRoot() != null) {
				return validateCandidateSecure(candidate, current.secureRoot(), expectedType);
			}
			for (Path component : candidate.rootComponents()) {
				FileType state = readFresh(component, current);
				if (state == FileType.MISSING) {
					return ShieldReason.NONE;
				}
				if (state == FileType.SYMLINK) {
					return ShieldReason.SYMLINK;
				}
				if (state != FileType.DIRECTORY) {
					return ShieldReason.SPECIAL_FILE;
				}
			}

			List<Path> components = candidate.candidateComponents();
			for (int index = 0; index < components.size(); index++) {
				FileType state = readFresh(components.get(index), current);
				if (state == FileType.MISSING) {
					return ShieldReason.NONE;
				}
				FileType type = state;
				if (type == FileType.SYMLINK) {
					return ShieldReason.SYMLINK;
				}
				boolean last = index == components.size() - 1;
				boolean wrongType = !last && type != FileType.DIRECTORY
						|| last && expectedType == ExpectedType.REGULAR_FILE
								&& type != FileType.REGULAR_FILE
						|| last && expectedType == ExpectedType.DIRECTORY
								&& type != FileType.DIRECTORY;
				if (wrongType) {
					return ShieldReason.SPECIAL_FILE;
				}
			}
			return ShieldReason.NONE;
		}

		private ShieldReason validateCandidateSecure(
				CandidatePlan candidate,
				SecureDirectoryStream<Path> secureRoot,
				ExpectedType expectedType
		) throws IOException {
			List<Path> components = secureComponents(candidate);
			if (components.isEmpty()) {
				return expectedType == ExpectedType.DIRECTORY
						? ShieldReason.NONE
						: ShieldReason.SPECIAL_FILE;
			}

			List<SecureDirectoryStream<Path>> opened = new ArrayList<>();
			SecureDirectoryStream<Path> current = secureRoot;
			Throwable operationFailure = null;
			try {
				for (int index = 0; index < components.size() - 1; index++) {
					Path name = components.get(index).getFileName();
					try {
						SecureDirectoryStream<Path> next = current.newDirectoryStream(
								name, LinkOption.NOFOLLOW_LINKS
						);
						opened.add(next);
						current = next;
					} catch (NoSuchFileException exception) {
						return ShieldReason.NONE;
					} catch (NotDirectoryException exception) {
						return classifyFreshPath(
								components.get(index), ExpectedType.DIRECTORY
						);
					}
				}
				// Keep a fresh no-follow check on the final path. Secure handle
				// traversal pins every ancestor, while this final lstat preserves
				// explicit symlink/type diagnostics on providers whose secure
				// attribute view follows the final link despite NOFOLLOW_LINKS.
				return classifyFreshPath(components.getLast(), expectedType);
			} catch (IOException | RuntimeException | Error exception) {
				operationFailure = exception;
				throw exception;
			} finally {
				closeOpenedDirectories(opened, null, operationFailure);
			}
		}

		private ShieldReason classifyFreshPath(Path path, ExpectedType expectedType)
				throws IOException {
			FileType type = readFileType(path);
			if (type == FileType.MISSING) {
				return ShieldReason.NONE;
			}
			return reasonFor(type, expectedType);
		}

		private FileType readFileType(Path path) throws IOException {
			try {
				return fileType(Files.readAttributes(
						path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
				));
			} catch (NoSuchFileException exception) {
				return FileType.MISSING;
			}
		}

		private ShieldReason reasonFor(FileType type, ExpectedType expectedType) {
			if (type == FileType.SYMLINK) {
				return ShieldReason.SYMLINK;
			}
			return expectedType == ExpectedType.REGULAR_FILE
					&& type != FileType.REGULAR_FILE
					|| expectedType == ExpectedType.DIRECTORY
					&& type != FileType.DIRECTORY
					? ShieldReason.SPECIAL_FILE
					: ShieldReason.NONE;
		}

		private PathPlan cachedPathPlan(
				String logicalPath,
				ExpectedType expectedType
		) {
			if (this.maximumEntries == 0 || this.roots.size() > MAXIMUM_CACHED_ROOTS) {
				return null;
			}
			Map<String, PathPlan> plans = expectedType == ExpectedType.REGULAR_FILE
					? this.filePathPlans
					: this.directoryPathPlans;
			PathPlan existing = plans.get(logicalPath);
			if (existing != null) {
				return existing;
			}
			PathPlan created = buildPathPlan(logicalPath);
			if (created.unsafe() || created.uncached()) {
				return created;
			}
			synchronized (this) {
				PathPlan raced = plans.get(logicalPath);
				if (raced != null) {
					return raced;
				}
				if (pathPlanCountUnsafe() >= this.maximumEntries) {
					evictOnePathPlan();
				}
				plans.put(logicalPath, created);
				return created;
			}
		}

		private PathPlan buildPathPlan(String logicalPath) {
			List<CandidatePlan> candidates = new ArrayList<>(this.roots.size());
			long componentCount = 0L;
			for (RootPlan root : this.roots) {
				CandidatePlan candidate = candidatePlan(root, logicalPath);
				if (candidate == null) {
					return PathPlan.UNSAFE;
				}
				componentCount += candidate.rootComponents().size()
						+ candidate.candidateComponents().size();
				if (componentCount > MAXIMUM_CACHED_PLAN_COMPONENTS) {
					return PathPlan.UNCACHED;
				}
				candidates.add(candidate);
			}
			return new PathPlan(List.copyOf(candidates), false, false);
		}

		private CandidatePlan candidatePlan(RootPlan root, String logicalPath) {
			Path candidate = root.root().resolve(logicalPath).normalize();
			if (!candidate.startsWith(root.root())) {
				return null;
			}
			List<Path> rootComponents = new ArrayList<>();
			rootComponents.add(this.primaryRoot);
			rootComponents.addAll(components(this.primaryRoot, root.root()));
			return new CandidatePlan(
					List.copyOf(rootComponents),
					components(root.root(), candidate)
			);
		}

		private void evictOnePathPlan() {
			java.util.Iterator<String> fileKeys = this.filePathPlans.keySet().iterator();
			if (fileKeys.hasNext()) {
				this.filePathPlans.remove(fileKeys.next());
				return;
			}
			java.util.Iterator<String> directoryKeys = this.directoryPathPlans.keySet().iterator();
			if (directoryKeys.hasNext()) {
				this.directoryPathPlans.remove(directoryKeys.next());
			}
		}

		private int pathPlanCountUnsafe() {
			return this.filePathPlans.size() + this.directoryPathPlans.size();
		}

		synchronized void clear() {
			this.filePathPlans.clear();
			this.directoryPathPlans.clear();
		}

		synchronized int pathPlanCount() {
			return pathPlanCountUnsafe();
		}

	}

	private static RootPlans planRoots(List<Path> declaredRoots) {
		if (declaredRoots == null || declaredRoots.isEmpty()) {
			return new RootPlans(null, List.of(), ShieldReason.NONE);
		}
		Path primaryRoot = declaredRoots.getFirst().toAbsolutePath().normalize();
		List<RootPlan> planned = new ArrayList<>(declaredRoots.size());
		for (Path suppliedRoot : declaredRoots) {
			Path root = suppliedRoot.toAbsolutePath().normalize();
			if (!root.startsWith(primaryRoot)) {
				return new RootPlans(null, List.of(), ShieldReason.UNSAFE_PATH);
			}
			planned.add(new RootPlan(root));
		}
		return new RootPlans(primaryRoot, List.copyOf(planned), ShieldReason.NONE);
	}

	private static List<Path> components(Path root, Path target) {
		if (root.equals(target)) {
			return List.of();
		}
		List<Path> result = new ArrayList<>();
		Path cursor = root;
		for (Path component : root.relativize(target)) {
			cursor = cursor.resolve(component);
			result.add(cursor);
		}
		return List.copyOf(result);
	}

	private enum ExpectedType {
		REGULAR_FILE,
		DIRECTORY
	}

	private enum FileType {
		MISSING,
		SYMLINK,
		DIRECTORY,
		REGULAR_FILE,
		SPECIAL
	}

	private static FileType fileType(BasicFileAttributes attributes) {
		return attributes.isSymbolicLink()
				? FileType.SYMLINK
				: attributes.isDirectory()
						? FileType.DIRECTORY
						: attributes.isRegularFile()
								? FileType.REGULAR_FILE
								: FileType.SPECIAL;
	}

	private static Object identityToken(BasicFileAttributes attributes) {
		Object fileKey = attributes.fileKey();
		return fileKey != null
				? fileKey
				: new PortableIdentity(
						fileType(attributes),
						attributes.size(),
						attributes.creationTime(),
						attributes.lastModifiedTime()
				);
	}

	private record RootPlans(
			Path primaryRoot,
			List<RootPlan> roots,
			ShieldReason reason
	) {
	}

	private record RootPlan(Path root) {
	}

	private record CandidatePlan(
			List<Path> rootComponents,
			List<Path> candidateComponents
	) {
	}

	private record PathPlan(
			List<CandidatePlan> candidates,
			boolean unsafe,
			boolean uncached
	) {
		private static final PathPlan UNSAFE = new PathPlan(List.of(), true, false);
		private static final PathPlan UNCACHED = new PathPlan(List.of(), false, true);
	}

	private record FileIdentity(FileType type, Object identity) {
	}

	private record PortableIdentity(
			FileType type,
			long size,
			java.nio.file.attribute.FileTime creationTime,
			java.nio.file.attribute.FileTime modifiedTime
	) {
	}

	private static final class OperationAttributes {
		private static final int TABLE_CAPACITY = 1_024;
		private final Path[] paths = new Path[TABLE_CAPACITY];
		private final FileType[] types = new FileType[TABLE_CAPACITY];
		private final int[] occupiedSlots = new int[MAXIMUM_OPERATION_ATTRIBUTES];
		private int occupiedCount;
		private SecureDirectoryStream<Path> secureRoot;

		private ShieldReason begin(Path primaryRoot, SecureOpenHook hook) throws IOException {
			clear();
			FileType rootType;
			Object expectedKey;
			try {
				BasicFileAttributes attributes = Files.readAttributes(
						primaryRoot,
						BasicFileAttributes.class,
						LinkOption.NOFOLLOW_LINKS
				);
				rootType = fileType(attributes);
				expectedKey = attributes.fileKey();
				put(primaryRoot, rootType);
			} catch (NoSuchFileException exception) {
				return ShieldReason.NONE;
			}
			if (rootType == FileType.SYMLINK) {
				return ShieldReason.SYMLINK;
			}
			if (rootType != FileType.DIRECTORY) {
				return ShieldReason.SPECIAL_FILE;
			}
			hook.afterRootValidation();
			DirectoryStream<Path> opened = Files.newDirectoryStream(primaryRoot);
			if (opened instanceof SecureDirectoryStream<Path> secure) {
				// The enclosing operation's finally block must own the handle before attribute queries can fail.
				this.secureRoot = secure;
				BasicFileAttributes pinned = secure.getFileAttributeView(
						java.nio.file.attribute.BasicFileAttributeView.class
				).readAttributes();
				if (expectedKey == null || pinned.fileKey() == null
						) {
					this.secureRoot = null;
					secure.close();
					return ShieldReason.NONE;
				}
				if (!expectedKey.equals(pinned.fileKey())) {
					this.secureRoot = null;
					secure.close();
					return ShieldReason.UNSAFE_PATH;
				}
			} else {
				opened.close();
			}
			return ShieldReason.NONE;
		}

		private SecureDirectoryStream<Path> secureRoot() {
			return this.secureRoot;
		}

		private FileType get(Path path) {
			int slot = mix(path.hashCode()) & (TABLE_CAPACITY - 1);
			for (int probe = 0; probe < TABLE_CAPACITY; probe++) {
				Path existing = this.paths[slot];
				if (existing == null) {
					return null;
				}
				if (existing.equals(path)) {
					return this.types[slot];
				}
				slot = (slot + 1) & (TABLE_CAPACITY - 1);
			}
			return null;
		}

		private void put(Path path, FileType type) {
			if (this.occupiedCount >= MAXIMUM_OPERATION_ATTRIBUTES) {
				return;
			}
			int slot = mix(path.hashCode()) & (TABLE_CAPACITY - 1);
			for (int probe = 0; probe < TABLE_CAPACITY; probe++) {
				Path existing = this.paths[slot];
				if (existing == null) {
					this.paths[slot] = path;
					this.types[slot] = type;
					this.occupiedSlots[this.occupiedCount++] = slot;
					return;
				}
				if (existing.equals(path)) {
					this.types[slot] = type;
					return;
				}
				slot = (slot + 1) & (TABLE_CAPACITY - 1);
			}
		}

		private void clear() {
			if (this.secureRoot != null) {
				try {
					this.secureRoot.close();
				} catch (IOException ignored) {
					// The next validation still starts from a fresh root handle.
				}
				this.secureRoot = null;
			}
			for (int index = 0; index < this.occupiedCount; index++) {
				int slot = this.occupiedSlots[index];
				this.paths[slot] = null;
				this.types[slot] = null;
				this.occupiedSlots[index] = 0;
			}
			this.occupiedCount = 0;
		}

		private int retainedCount() {
			return this.occupiedCount;
		}

		private static int mix(int value) {
			return value ^ value >>> 16;
		}
	}

	interface SecureOpenHook {
		SecureOpenHook NONE = new SecureOpenHook() {
		};

		default void afterRootValidation() throws IOException {
		}

		default void beforeFinalOpen() throws IOException {
		}
	}

	record SecureOpenResult(InputStream input, ShieldReason reason) {
		static SecureOpenResult opened(InputStream input) {
			return new SecureOpenResult(input, ShieldReason.NONE);
		}

		static SecureOpenResult missing() {
			return new SecureOpenResult(null, ShieldReason.NONE);
		}

		static SecureOpenResult failed(ShieldReason reason) {
			return new SecureOpenResult(null, reason);
		}
	}
}
