package com.ragedriver.sodiumvolt.client.resourcepack;

import com.ragedriver.sodiumvolt.client.config.ResourcePackShieldConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.util.Unit;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ResourcePackShieldEngine {
	private static final int MAXIMUM_RELOAD_NESTING = 32;
	private static final long NOTIFICATION_INTERVAL_NANOS = 10_000_000_000L;
	private static final long NOTIFICATION_EXPIRY_NANOS = 30_000_000_000L;
	private static final ResourcePackShieldConfig CONFIG =
			ResourcePackShieldConfig.getInstance();
	private static final AtomicLong CONTROL_GENERATION = new AtomicLong(1L);
	private static final AtomicLong EVENT_SEQUENCE = new AtomicLong();
	private static final AtomicLong READY_EVENT_SEQUENCE = new AtomicLong();
	private static final AtomicLong AGGREGATE_SCAN_DEADLINE_NANOS = new AtomicLong();
	private static final AtomicInteger ACTIVE_RELOADS = new AtomicInteger();
	private static final AtomicReference<PendingNotice> PENDING_NOTICE =
			new AtomicReference<>();
	private static final AtomicLong PACKS_SCANNED = new AtomicLong();
	private static final AtomicLong LOCAL_PACKS = new AtomicLong();
	private static final AtomicLong SERVER_PACKS = new AtomicLong();
	private static final AtomicLong RESOURCES_SEEN = new AtomicLong();
	private static final AtomicLong ENTRIES_SCANNED = new AtomicLong();
	private static final AtomicLong DECLARED_BYTES = new AtomicLong();
	private static final AtomicLong VIOLATIONS = new AtomicLong();
	private static final AtomicLong REJECTIONS = new AtomicLong();
	private static final AtomicLong MONITOR_FAILURES = new AtomicLong();
	private static final AtomicLong LAST_REPORT_NANOS = new AtomicLong();
	private static final ThreadLocal<ShieldReloadContextStack<ReloadToken>>
			SYNCHRONOUS_RELOAD_CONTEXT = new ThreadLocal<>();

	private static volatile long observedRevision = Long.MIN_VALUE;
	private static volatile boolean runtimeEnabled;
	private static volatile ShieldReason lastReason = ShieldReason.NONE;
	private static volatile ShieldSourceKind lastSource = ShieldSourceKind.IGNORED;
	private static volatile long lastEventLiveBytes;
	private static long lastNotificationNanos;

	private ResourcePackShieldEngine() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ResourcePackShieldEngine::onClientTick);
	}

	public static PackResources guardArchive(
			Path archive,
			Pack.Metadata metadata,
			PackLocationInfo location,
			PackResources delegate
	) {
		return guard(archive, metadata, location, delegate, true);
	}

	public static PackResources guardDirectory(
			Path root,
			Pack.Metadata metadata,
			PackLocationInfo location,
			PackResources delegate
	) {
		return guard(root, metadata, location, delegate, false);
	}

	public static ReloadToken beginResourceReload() {
		ResourcePackShieldConfig.RuntimeSnapshot config = CONFIG.runtimeSnapshot();
		refreshControl(config);
		if (!runtimeEnabled) {
			return ReloadToken.DISABLED;
		}
		while (true) {
			int current = ACTIVE_RELOADS.get();
			if (current >= MAXIMUM_RELOAD_NESTING) {
				return ReloadToken.DISABLED;
			}
			if (ACTIVE_RELOADS.compareAndSet(current, current + 1)) {
				if (current == 0) {
					AGGREGATE_SCAN_DEADLINE_NANOS.set(deadline(
							config.policy().maximumScanNanos()
					));
				}
				return new ReloadToken(
						CONTROL_GENERATION.get(),
						EVENT_SEQUENCE.get(),
						true
				);
			}
		}
	}

	public static CompletableFuture<Unit> guardInitialReloadTask(
			CompletableFuture<Unit> initialTask,
			ReloadToken token
	) {
		return guardInitialReloadTaskForGeneration(
				initialTask, token, CONTROL_GENERATION.get()
		);
	}

	static CompletableFuture<Unit> guardInitialReloadTaskForGeneration(
			CompletableFuture<Unit> initialTask,
			ReloadToken token,
			long currentControlGeneration
	) {
		return token == null
				? initialTask
				: token.rejection().guardInitialTask(
						initialTask, currentControlGeneration
				);
	}

	public static void enterSynchronousReload(ReloadToken token) {
		ShieldReloadContextStack<ReloadToken> context =
				SYNCHRONOUS_RELOAD_CONTEXT.get();
		if (context == null) {
			context = new ShieldReloadContextStack<>(MAXIMUM_RELOAD_NESTING);
			SYNCHRONOUS_RELOAD_CONTEXT.set(context);
		}
		context.begin(
				null,
				false,
				false,
				() -> token == null ? ReloadToken.DISABLED : token,
				ReloadToken.DISABLED
		);
	}

	public static ReloadToken currentSynchronousReloadToken() {
		ShieldReloadContextStack<ReloadToken> context =
				SYNCHRONOUS_RELOAD_CONTEXT.get();
		return context == null
				? ReloadToken.DISABLED
				: context.currentOr(ReloadToken.DISABLED);
	}

	public static void exitSynchronousReload() {
		ShieldReloadContextStack<ReloadToken> context =
				SYNCHRONOUS_RELOAD_CONTEXT.get();
		if (context == null) {
			return;
		}
		context.finish();
		if (context.isEmpty()) {
			SYNCHRONOUS_RELOAD_CONTEXT.remove();
		}
	}

	public static void watchResourceReload(
			CompletableFuture<Void> future,
			ReloadToken token
	) {
		if (token == null || !token.counted()) {
			return;
		}
		if (future == null) {
			completeReload(token);
			return;
		}
		future.whenComplete((ignored, throwable) -> completeReload(token));
	}

	public static StatisticsSnapshot snapshotStatistics() {
		ResourcePackShieldConfig.RuntimeSnapshot config = CONFIG.runtimeSnapshot();
		refreshControl(config);
		if (!runtimeEnabled || !config.showInspectorStatistics()) {
			return StatisticsSnapshot.EMPTY;
		}
		return new StatisticsSnapshot(
				config.policy().rejectViolations(),
				PACKS_SCANNED.get(),
				LOCAL_PACKS.get(),
				SERVER_PACKS.get(),
				RESOURCES_SEEN.get(),
				ENTRIES_SCANNED.get(),
				DECLARED_BYTES.get(),
				VIOLATIONS.get(),
				REJECTIONS.get(),
				MONITOR_FAILURES.get(),
				lastReason,
				lastSource,
				lastEventLiveBytes
		);
	}

	private static PackResources guard(
			Path sourcePath,
			Pack.Metadata metadata,
			PackLocationInfo location,
			PackResources delegate,
			boolean archive
	) {
		ResourcePackShieldConfig.RuntimeSnapshot config = CONFIG.runtimeSnapshot();
		refreshControl(config);
		ShieldSourceKind source = ShieldSourceScope.classify(location.source());
		if (!runtimeEnabled || !scopeEnabled(source, config)) {
			return delegate;
		}
		long revision = config.revision();
		ShieldScanBudget.Allowance allowance = scanAllowance(config.policy());
		ResourcePackShieldPolicy policy = allowance.policy();
		ShieldOverlayPlan.Validation overlayValidation = allowance.expired()
				? new ShieldOverlayPlan.Validation(
						ShieldReason.SCAN_TIME, ShieldOverlayPlan.EMPTY
				)
				: ShieldOverlayPlan.validate(
						metadata == null ? null : metadata.overlays(),
						policy,
						allowance.deadlineNanos()
				);
		ShieldOverlayPlan overlays = overlayValidation.plan();
		ResourcePackShieldSession session = new ResourcePackShieldSession(
				source,
				policy,
				revision,
				CONTROL_GENERATION.get(),
				currentSynchronousReloadToken(),
				archive ? sourcePath : null,
				archive ? List.of() : directoryRoots(sourcePath, overlays)
		);
		ShieldScanResult result;
		try {
			result = allowance.expired()
					? new ShieldScanResult(ShieldReason.SCAN_TIME, 0, 0L, 0L)
					: overlayValidation.accepted()
					? archive
							? ZipPackScanner.scan(
									sourcePath,
									session.policy(),
									overlays,
									allowance.deadlineNanos()
							)
							: DirectoryPackScanner.scan(
									sourcePath,
									session.policy(),
									overlays,
									allowance.deadlineNanos()
							)
					: new ShieldScanResult(
							overlayValidation.reason(), 0, 0L, 0L
					);
		} catch (RuntimeException | java.io.IOException | StackOverflowError exception) {
			result = new ShieldScanResult(ShieldReason.MONITOR_FAILURE, 0, 0L, 0L);
		}
		recordScan(source, result);
		if (!result.accepted()) {
			session.onViolation(result.reason());
		}
		return new ShieldedPackResources(delegate, session);
	}

	private static void recordScan(ShieldSourceKind source, ShieldScanResult result) {
		saturatingIncrement(PACKS_SCANNED);
		if (source == ShieldSourceKind.LOCAL) {
			saturatingIncrement(LOCAL_PACKS);
		} else if (source == ShieldSourceKind.SERVER) {
			saturatingIncrement(SERVER_PACKS);
		}
		saturatingAdd(ENTRIES_SCANNED, result.entries());
		saturatingAdd(DECLARED_BYTES, result.declaredBytes());
	}

	static void recordResourceOutput() {
		saturatingIncrement(RESOURCES_SEEN);
	}

	static boolean recordEvent(
			ResourcePackShieldSession session,
			ShieldReason reason
	) {
		if (!session.isCurrent()) {
			return false;
		}
		boolean monitorFailure = reason == ShieldReason.MONITOR_FAILURE;
		boolean rejected = ShieldEnforcement.shouldReject(session.policy(), reason);
		if (rejected) {
			session.markRejected();
		}
		if (monitorFailure) {
			saturatingIncrement(MONITOR_FAILURES);
		} else {
			saturatingIncrement(VIOLATIONS);
			if (rejected) {
				saturatingIncrement(REJECTIONS);
			}
		}
		lastReason = reason;
		lastSource = session.source();
		lastEventLiveBytes = session.readBudget().consumedBytes();
		long sequence = nextSequence(EVENT_SEQUENCE);
		PENDING_NOTICE.set(new PendingNotice(
				session.controlGeneration(),
				sequence,
				System.nanoTime(),
				rejected,
				monitorFailure
		));
		if (CONFIG.isWriteSanitizedLocalReport() && claimReportWrite(System.nanoTime())) {
			ResourcePackShieldReportStore.write(new ResourcePackShieldReport(
					reason,
					session.source(),
					rejected,
					toBoundedInt(VIOLATIONS.get()),
					toBoundedInt(PACKS_SCANNED.get()),
					toBoundedInt(RESOURCES_SEEN.get()),
					DECLARED_BYTES.get(),
					session.readBudget().consumedBytes(),
					toBoundedInt(MONITOR_FAILURES.get())
			));
		}
		return rejected;
	}

	private static void completeReload(ReloadToken token) {
		if (!token.completed().compareAndSet(false, true)) {
			return;
		}
		if (token.controlGeneration() != CONTROL_GENERATION.get()) {
			return;
		}
		ACTIVE_RELOADS.updateAndGet(value -> Math.max(0, value - 1));
		if (ACTIVE_RELOADS.get() == 0) {
			AGGREGATE_SCAN_DEADLINE_NANOS.set(0L);
		}
		long event = EVENT_SEQUENCE.get();
		if (event > token.eventBaseline()) {
			READY_EVENT_SEQUENCE.accumulateAndGet(event, Math::max);
		}
	}

	private static void onClientTick(Minecraft minecraft) {
		ResourcePackShieldConfig.RuntimeSnapshot config = CONFIG.runtimeSnapshot();
		refreshControl(config);
		if (!runtimeEnabled || !config.showTransitionNotifications()) {
			PENDING_NOTICE.set(null);
			return;
		}
		PendingNotice notice = PENDING_NOTICE.get();
		if (notice == null
				|| notice.controlGeneration() != CONTROL_GENERATION.get()
				|| notice.sequence() > READY_EVENT_SEQUENCE.get()
				|| ACTIVE_RELOADS.get() != 0) {
			return;
		}
		long now = System.nanoTime();
		if (now - notice.createdNanos() > NOTIFICATION_EXPIRY_NANOS) {
			PENDING_NOTICE.compareAndSet(notice, null);
			return;
		}
		if (minecraft.level == null || minecraft.player == null
				|| minecraft.gui.screen() != null || minecraft.gui.overlay() != null) {
			return;
		}
		if (lastNotificationNanos != 0L && now < lastNotificationNanos) {
			lastNotificationNanos = 0L;
		}
		if (lastNotificationNanos != 0L
				&& now - lastNotificationNanos < NOTIFICATION_INTERVAL_NANOS) {
			return;
		}
		Component message = Component.translatable(
				notice.monitorFailure()
						? "sodium-volt.notification.resource_pack_shield.monitor_failure"
						: notice.rejected()
								? "sodium-volt.notification.resource_pack_shield.rejected"
								: "sodium-volt.notification.resource_pack_shield.monitored"
		);
		minecraft.gui.hud.setOverlayMessage(message, false);
		lastNotificationNanos = now;
		PENDING_NOTICE.compareAndSet(notice, null);
	}

	private static synchronized void refreshControl(
			ResourcePackShieldConfig.RuntimeSnapshot config
	) {
		long revision = config.revision();
		boolean enabled = config.enabled();
		if (observedRevision != Long.MIN_VALUE && revision < observedRevision) {
			return;
		}
		if (revision == observedRevision && enabled == runtimeEnabled) {
			return;
		}
		observedRevision = revision;
		runtimeEnabled = enabled;
		nextSequence(CONTROL_GENERATION);
		ACTIVE_RELOADS.set(0);
		AGGREGATE_SCAN_DEADLINE_NANOS.set(0L);
		READY_EVENT_SEQUENCE.set(0L);
		PENDING_NOTICE.set(null);
	}

	private static ShieldScanBudget.Allowance scanAllowance(
			ResourcePackShieldPolicy policy
	) {
		long aggregateDeadline = AGGREGATE_SCAN_DEADLINE_NANOS.get();
		return ShieldScanBudget.allowance(
				policy,
				aggregateDeadline,
				System.nanoTime(),
				ACTIVE_RELOADS.get() != 0
		);
	}

	private static long deadline(long durationNanos) {
		long now = System.nanoTime();
		return now >= Long.MAX_VALUE - durationNanos
				? Long.MAX_VALUE
				: now + durationNanos;
	}

	private static List<Path> directoryRoots(
			Path root,
			ShieldOverlayPlan overlays
	) {
		Path normalizedRoot = root.toAbsolutePath().normalize();
		LinkedHashSet<Path> roots = new LinkedHashSet<>();
		roots.add(normalizedRoot);
		for (String overlay : overlays.prefixes()) {
			Path candidate = normalizedRoot.resolve(overlay).normalize();
			if (candidate.startsWith(normalizedRoot)) {
				roots.add(candidate);
			}
		}
		return List.copyOf(roots);
	}

	private static boolean claimReportWrite(long nowNanos) {
		while (true) {
			long previous = LAST_REPORT_NANOS.get();
			if (previous != 0L && nowNanos >= previous
					&& nowNanos - previous < NOTIFICATION_INTERVAL_NANOS) {
				return false;
			}
			if (LAST_REPORT_NANOS.compareAndSet(previous, nowNanos)) {
				return true;
			}
		}
	}

	private static boolean scopeEnabled(ShieldSourceKind source) {
		return source == ShieldSourceKind.LOCAL && CONFIG.isMonitorLocalPacks()
				|| source == ShieldSourceKind.SERVER && CONFIG.isMonitorServerPacks();
	}

	private static boolean scopeEnabled(
			ShieldSourceKind source,
			ResourcePackShieldConfig.RuntimeSnapshot config
	) {
		return source == ShieldSourceKind.LOCAL && config.monitorLocalPacks()
				|| source == ShieldSourceKind.SERVER && config.monitorServerPacks();
	}

	static boolean isCurrent(
			long revision,
			long controlGeneration,
			ShieldSourceKind source
	) {
		return CONFIG.isResourcePackShieldEnabled()
				&& CONFIG.revision() == revision
				&& CONTROL_GENERATION.get() == controlGeneration
				&& scopeEnabled(source);
	}

	private static long nextSequence(AtomicLong value) {
		return value.updateAndGet(current -> current == Long.MAX_VALUE ? 1L : current + 1L);
	}

	private static void saturatingIncrement(AtomicLong value) {
		saturatingAdd(value, 1L);
	}

	private static void saturatingAdd(AtomicLong value, long increment) {
		if (increment <= 0L) {
			return;
		}
		value.updateAndGet(current ->
				increment > Long.MAX_VALUE - current ? Long.MAX_VALUE : current + increment
		);
	}

	private static int toBoundedInt(long value) {
		return (int) Math.min(1_000_000_000L, Math.max(0L, value));
	}

	public static final class ReloadToken {
		public static final ReloadToken DISABLED =
				new ReloadToken(0L, 0L, false);
		private final long controlGeneration;
		private final long eventBaseline;
		private final boolean counted;
		private final ShieldReloadRejection rejection;
		private final AtomicBoolean completed;

		private ReloadToken(
				long controlGeneration,
				long eventBaseline,
				boolean counted
		) {
			this.controlGeneration = controlGeneration;
			this.eventBaseline = eventBaseline;
			this.counted = counted;
			this.rejection = new ShieldReloadRejection(controlGeneration, counted);
			this.completed = new AtomicBoolean(!counted);
		}

		public long controlGeneration() {
			return this.controlGeneration;
		}

		public long eventBaseline() {
			return this.eventBaseline;
		}

		public boolean counted() {
			return this.counted;
		}

		ShieldReloadRejection rejection() {
			return this.rejection;
		}

		AtomicBoolean completed() {
			return this.completed;
		}

		void markRejected(long currentControlGeneration) {
			this.rejection.reject(currentControlGeneration);
		}
	}

	private record PendingNotice(
			long controlGeneration,
			long sequence,
			long createdNanos,
			boolean rejected,
			boolean monitorFailure
	) {
	}

	public record StatisticsSnapshot(
			boolean rejectMode,
			long packsScanned,
			long localPacks,
			long serverPacks,
			long resourcesSeen,
			long entriesScanned,
			long declaredBytes,
			long violations,
			long rejections,
			long monitorFailures,
			ShieldReason lastReason,
			ShieldSourceKind lastSource,
			long lastEventLiveBytes
	) {
		public static final StatisticsSnapshot EMPTY = new StatisticsSnapshot(
				false, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
				ShieldReason.NONE, ShieldSourceKind.IGNORED, 0L
		);

		public String fixedReason() {
			return this.lastReason.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		}
	}
}
