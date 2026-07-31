package com.ragedriver.sodiumvolt.client.resourcepack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

final class ResourcePackShieldSession {
	private final ShieldSourceKind source;
	private final ResourcePackShieldPolicy policy;
	private final long configRevision;
	private final long controlGeneration;
	private final ResourcePackShieldEngine.ReloadToken reloadToken;
	private final Path archivePath;
	private final ShieldArchiveOpenGate archiveOpenGate;
	private final List<Path> directoryRoots;
	private final ShieldReadBudget readBudget;
	private final AtomicInteger resourceOutputs = new AtomicInteger();
	private final AtomicBoolean rejected = new AtomicBoolean();

	ResourcePackShieldSession(
			ShieldSourceKind source,
			ResourcePackShieldPolicy policy,
			long configRevision,
			long controlGeneration,
			ResourcePackShieldEngine.ReloadToken reloadToken,
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
		this.archivePath = archivePath;
		this.archiveOpenGate = archivePath == null ? null : new ShieldArchiveOpenGate();
		this.directoryRoots = directoryRoots;
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

	ShieldReadBudget readBudget() {
		return this.readBudget;
	}

	boolean isCurrent() {
		return ResourcePackShieldEngine.isCurrent(
				this.configRevision, this.controlGeneration, this.source
		);
	}

	boolean onViolation(ShieldReason reason) {
		return ResourcePackShieldEngine.recordEvent(this, reason);
	}

	void markRejected() {
		this.rejected.set(true);
		this.reloadToken.markRejected(this.controlGeneration);
	}

	boolean isRejected() {
		return isCurrent() && this.rejected.get();
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
		if (!isCurrent() || this.directoryRoots.isEmpty()) {
			return false;
		}
		try {
			ShieldReason reason = directoryPrefix
					? DirectoryCandidateValidator.validateDirectoryPrefix(
							this.directoryRoots, logicalPath
					)
					: DirectoryCandidateValidator.validate(
							this.directoryRoots, logicalPath
					);
			if (reason != ShieldReason.NONE) {
				return onViolation(reason);
			}
		} catch (IOException | RuntimeException exception) {
			onViolation(ShieldReason.MONITOR_FAILURE);
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
			onViolation(ShieldReason.MONITOR_FAILURE);
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
		if (!isCurrent()) {
			return input;
		}
		return new ShieldedInputStream(
				input,
				this.policy.maximumSingleResourceBytes(),
				this.readBudget,
				contentKind,
				this.policy,
				this::onViolation
		);
	}
}
