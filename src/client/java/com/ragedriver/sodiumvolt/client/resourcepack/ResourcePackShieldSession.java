package com.ragedriver.sodiumvolt.client.resourcepack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class ResourcePackShieldSession {
	private static final int MAXIMUM_DIRECTORY_CACHE_ENTRIES = 2_048;
	private final ShieldSourceKind source;
	private final ResourcePackShieldPolicy policy;
	private final long configRevision;
	private final long controlGeneration;
	private final ResourcePackShieldEngine.ReloadToken reloadToken;
	private final boolean metadataOnly;
	private final Path archivePath;
	private final ShieldArchiveOpenGate archiveOpenGate;
	private final DirectoryCandidateValidator.Cache directoryValidationCache;
	private final ShieldReadBudget readBudget;
	private final AtomicInteger resourceOutputs = new AtomicInteger();
	private final AtomicBoolean rejected = new AtomicBoolean();
	private final AtomicBoolean closed = new AtomicBoolean();

	ResourcePackShieldSession(
			ShieldSourceKind source,
			ResourcePackShieldPolicy policy,
			long configRevision,
			long controlGeneration,
			ResourcePackShieldEngine.ReloadToken reloadToken,
			boolean metadataOnly,
			Path archivePath,
			List<Path> directoryRoots
	) {
		this.source = source;
		this.policy = policy;
		this.configRevision = configRevision;
		this.controlGeneration = controlGeneration;
		this.reloadToken = reloadToken == null
				? ResourcePackShieldEngine.ReloadToken.DISABLED
				: reloadToken;
		this.metadataOnly = metadataOnly;
		this.archivePath = archivePath;
		this.archiveOpenGate = archivePath == null ? null : new ShieldArchiveOpenGate();
		this.directoryValidationCache = DirectoryCandidateValidator.newCache(
				directoryRoots,
				Math.min(MAXIMUM_DIRECTORY_CACHE_ENTRIES, policy.maximumEntries())
		);
		this.readBudget = new ShieldReadBudget(policy.maximumTotalResourceBytes());
	}

	ShieldSourceKind source() {
		return this.source;
	}

	ResourcePackShieldPolicy policy() {
		return this.policy;
	}

	long controlGeneration() {
		return this.controlGeneration;
	}

	boolean metadataOnly() {
		return this.metadataOnly;
	}

	ShieldReadBudget readBudget() {
		return this.readBudget;
	}

	boolean isDirectorySource() {
		return this.archivePath == null;
	}

	InputStream openDirectoryResource(String logicalPath, boolean primaryOnly)
			throws IOException {
		DirectoryCandidateValidator.SecureOpenResult result;
		try {
			result = this.directoryValidationCache.openFile(logicalPath, primaryOnly);
		} catch (IOException | RuntimeException exception) {
			if (onViolation(ShieldReason.MONITOR_FAILURE)) {
				throw new IOException("Directory resource provider failed secure open", exception);
			}
			return null;
		}
		if (result.reason() != ShieldReason.NONE) {
			if (onViolation(result.reason())) {
				throw new IOException(
						"Directory resource could not be opened through a secure identity-bound handle"
				);
			}
			return null;
		}
		if (result.input() == null) {
			if (onViolation(ShieldReason.UNSAFE_PATH)) {
				throw new IOException("Directory resource changed before its secure open");
			}
			return null;
		}
		return result.input();
	}

	boolean isCurrent() {
		boolean current = !this.closed.get() && ResourcePackShieldEngine.isCurrent(
				this.source
		);
		if (!current) {
			this.directoryValidationCache.clear();
		}
		return current;
	}

	boolean onViolation(ShieldReason reason) {
		return ResourcePackShieldEngine.recordEvent(this, reason);
	}

	void markRejected() {
		this.rejected.set(true);
		this.reloadToken.markRejected(this.controlGeneration);
	}

	boolean isRejected() {
		if (this.closed.get()) {
			return true;
		}
		if (!isCurrent()) {
			return false;
		}
		// Validators and aggregate reservations belong to this immutable policy.
		// Retire the session instead of resetting its budgets or losing inspection
		// history. The engine schedules a reload to create the replacement.
		if (this.configRevision != ResourcePackShieldEngine.enforcementRevision()) {
			markRejected();
		}
		return this.rejected.get();
	}

	boolean validateLogicalPath(String logicalPath) {
		if (!isCurrent()) {
			return false;
		}
		if (!ShieldPathPolicy.isStructurallySafeRelative(logicalPath)
				|| this.policy.detectUnsafePaths()
						&& !ShieldPathPolicy.isSafe(
						logicalPath,
						this.policy.maximumPathLength(),
						this.policy.maximumPathDepth()
				)) {
			return onViolation(ShieldReason.UNSAFE_PATH);
		}
		return this.policy.blockCoreShaderOverrides()
				&& ShieldPathPolicy.isCoreShader(logicalPath)
				&& onViolation(ShieldReason.CORE_SHADER_OVERRIDE);
	}

	boolean validateDirectoryCandidates(String logicalPath) {
		return validateDirectoryPath(logicalPath, false);
	}

	boolean validateDirectoryPrefix(String logicalPath) {
		return validateDirectoryPath(logicalPath, true);
	}

	private boolean validateDirectoryPath(String logicalPath, boolean directoryPrefix) {
		if (!isCurrent()) {
			return false;
		}
		try {
			ShieldReason reason = directoryPrefix
					? this.directoryValidationCache.validateDirectory(logicalPath)
					: this.directoryValidationCache.validateFile(logicalPath);
			if (reason != ShieldReason.NONE) {
				return onViolation(reason);
			}
		} catch (IOException | RuntimeException exception) {
			return onViolation(ShieldReason.MONITOR_FAILURE);
		}
		return false;
	}

	ShieldArchiveOpenGate.Lease beginArchiveAccess() {
		return this.archiveOpenGate == null
				? ShieldArchiveOpenGate.noopLease()
				: this.archiveOpenGate.acquire();
	}

	boolean validateArchiveSource(ShieldArchiveOpenGate.Lease lease) {
		if (!isCurrent() || this.archivePath == null || !lease.validationRequired()) {
			return false;
		}
		try {
			if (Files.isSymbolicLink(this.archivePath)) {
				return onViolation(ShieldReason.SYMLINK);
			}
			BasicFileAttributes attributes = Files.readAttributes(
					this.archivePath,
					BasicFileAttributes.class,
					LinkOption.NOFOLLOW_LINKS
			);
			if (!attributes.isRegularFile()) {
				return onViolation(ShieldReason.SPECIAL_FILE);
			}
			if (attributes.size() < 0L
					|| attributes.size() > this.policy.maximumArchiveBytes()) {
				return onViolation(ShieldReason.ARCHIVE_SIZE);
			}
		} catch (IOException | RuntimeException exception) {
			return onViolation(ShieldReason.MONITOR_FAILURE);
		}
		return false;
	}

	boolean claimResourceOutput() {
		if (!isCurrent()) {
			return false;
		}
		int count = this.resourceOutputs.updateAndGet(current ->
				current == Integer.MAX_VALUE ? Integer.MAX_VALUE : current + 1
		);
		ResourcePackShieldEngine.recordResourceOutput();
		return count > this.policy.maximumEntries()
				&& onViolation(ShieldReason.RESOURCE_OUTPUT_LIMIT);
	}

	InputStream wrap(
			InputStream input,
			ShieldedInputStream.ContentKind contentKind
	) {
		if (isRejected()) {
			try {
				input.close();
			} catch (IOException ignored) {
				// Rejection must not leak the newly opened delegate stream.
			}
			throw new ResourcePackShieldRejectedException();
		}
		if (!isCurrent()) {
			return input;
		}
		return new ShieldedInputStream(
				input,
				this.policy.maximumSingleResourceBytes(),
				this.readBudget,
				contentKind,
				this.policy,
				this::onViolation,
				this::isRejected
		);
	}

	void close() {
		if (this.closed.compareAndSet(false, true)) {
			this.directoryValidationCache.clear();
		}
	}
}
