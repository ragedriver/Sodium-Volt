package com.ragedriver.sodiumvolt.client.resourcepack;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.ResourceMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

final class ShieldedPackResources implements PackResources {
	private final PackResources delegate;
	private final ResourcePackShieldSession session;
	private final AtomicBoolean closed = new AtomicBoolean();
	private volatile ResourceMetadata metadata;

	ShieldedPackResources(PackResources delegate, ResourcePackShieldSession session) {
		this.delegate = delegate;
		this.session = session;
	}

	@Override
	public IoSupplier<InputStream> getRootResource(String... elements) {
		String logicalPath = String.join("/", elements);
		try (ShieldArchiveOpenGate.Lease lease = this.session.beginArchiveAccess()) {
			if (this.session.isRejected()
					|| this.session.validateArchiveSource(lease)
					|| this.session.validateLogicalPath(logicalPath)) {
				throw new ResourcePackShieldRejectedException();
			}
			IoSupplier<InputStream> supplier = this.delegate.getRootResource(elements);
			if (supplier != null) {
				lease.markOpened();
			}
			return wrapSupplier(supplier, logicalPath);
		}
	}

	@Override
	public IoSupplier<InputStream> getResource(PackType type, Identifier identifier) {
		String logicalPath = type.getDirectory() + "/" + identifier.getNamespace()
				+ "/" + identifier.getPath();
		try (ShieldArchiveOpenGate.Lease lease = this.session.beginArchiveAccess()) {
			if (this.session.isRejected()
					|| this.session.validateArchiveSource(lease)
					|| this.session.validateLogicalPath(logicalPath)) {
				throw new ResourcePackShieldRejectedException();
			}
			IoSupplier<InputStream> supplier = this.delegate.getResource(type, identifier);
			if (supplier != null) {
				lease.markOpened();
			}
			return wrapSupplier(supplier, logicalPath);
		}
	}

	@Override
	public void listResources(
			PackType type,
			String namespace,
			String prefix,
			ResourceOutput output
	) {
		String requestedPath = type.getDirectory() + "/" + namespace + "/" + prefix;
		try (ShieldArchiveOpenGate.Lease lease = this.session.beginArchiveAccess()) {
			if (this.session.isRejected()
					|| this.session.validateArchiveSource(lease)
					|| this.session.validateLogicalPath(requestedPath)
					|| this.session.validateDirectoryPrefix(requestedPath)) {
				throw new ResourcePackShieldRejectedException();
			}
			this.delegate.listResources(type, namespace, prefix, (identifier, supplier) -> {
				String logicalPath = type.getDirectory() + "/" + identifier.getNamespace()
						+ "/" + identifier.getPath();
				if (this.session.validateLogicalPath(logicalPath)
						|| this.session.claimResourceOutput()) {
					throw new ResourcePackShieldRejectedException();
				}
				output.accept(identifier, wrapSupplier(supplier, logicalPath));
			});
			lease.markOpened();
		}
	}

	@Override
	public Set<String> getNamespaces(PackType type) {
		try (ShieldArchiveOpenGate.Lease lease = this.session.beginArchiveAccess()) {
			if (this.session.isRejected()
					|| this.session.validateArchiveSource(lease)
					|| this.session.validateDirectoryPrefix(type.getDirectory())) {
				return Set.of();
			}
			Set<String> namespaces = this.delegate.getNamespaces(type);
			lease.markOpened();
			if (namespaces.size() > this.session.policy().maximumEntries()) {
				if (this.session.onViolation(ShieldReason.RESOURCE_OUTPUT_LIMIT)) {
					return Set.of();
				}
			}
			for (String namespace : namespaces) {
				String namespacePath = type.getDirectory() + "/" + namespace;
				if (this.session.validateLogicalPath(namespacePath)
						|| this.session.validateDirectoryPrefix(namespacePath)) {
					return Set.of();
				}
			}
			return namespaces;
		}
	}

	@Override
	public <T> T getMetadataSection(MetadataSectionType<T> type) throws IOException {
		try (ShieldArchiveOpenGate.Lease lease = this.session.beginArchiveAccess()) {
			if (this.session.isRejected() || this.session.validateArchiveSource(lease)) {
				return null;
			}
			ResourceMetadata current = this.metadata;
			if (current == null) {
				synchronized (this) {
					current = this.metadata;
					if (current == null) {
						current = AbstractPackResources.loadMetadata(this);
						this.metadata = current;
					}
				}
			}
			lease.markOpened();
			return current.getSection(type).orElse(null);
		}
	}

	@Override
	public PackLocationInfo location() {
		return this.delegate.location();
	}

	@Override
	public Optional<KnownPack> knownPackInfo() {
		return this.delegate.knownPackInfo();
	}

	@Override
	public void close() {
		if (this.closed.compareAndSet(false, true)) {
			this.delegate.close();
		}
	}

	private IoSupplier<InputStream> wrapSupplier(
			IoSupplier<InputStream> supplier,
			String logicalPath
	) {
		if (supplier == null) {
			return null;
		}
		ShieldedInputStream.ContentKind contentKind = contentKind(logicalPath);
		return () -> {
			if (!this.session.isCurrent()) {
				return supplier.get();
			}
			if (this.session.isRejected()
					|| this.session.validateLogicalPath(logicalPath)
					|| this.session.validateDirectoryCandidates(logicalPath)) {
				throw new IOException("Resource pack rejected by Resource-Pack Shield policy");
			}
			InputStream input = supplier.get();
			return this.session.wrap(input, contentKind);
		};
	}

	private static ShieldedInputStream.ContentKind contentKind(String logicalPath) {
		String lower = logicalPath.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".png")) {
			return ShieldedInputStream.ContentKind.PNG;
		}
		if (lower.endsWith(".json") || lower.endsWith(".mcmeta")) {
			return ShieldedInputStream.ContentKind.JSON;
		}
		return ShieldedInputStream.ContentKind.OTHER;
	}
}
