package com.ragedriver.sodiumvolt.client.inspector;

import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.config.VoltInspectorConfig;
import com.ragedriver.sodiumvolt.client.performance.BlockEntityRenderBudgetEngine;
import com.ragedriver.sodiumvolt.client.performance.InspectorStatisticsDemand;
import com.ragedriver.sodiumvolt.client.performance.AnimatedTextureThrottleEngine;
import com.ragedriver.sodiumvolt.client.performance.VisibilityAwareParticleScheduler;
import com.ragedriver.sodiumvolt.client.performance.VramPressureProtectionEngine;
import com.ragedriver.sodiumvolt.client.privacy.PrivacyScreenshotEngine;
import com.ragedriver.sodiumvolt.client.recovery.VoltRecoveryEngine;
import com.ragedriver.sodiumvolt.client.resourcepack.ResourcePackShieldEngine;
import com.ragedriver.sodiumvolt.client.smartfps.SmartFpsEngine;
import com.ragedriver.sodiumvolt.client.smartfps.SmartFpsPolicy;
import com.ragedriver.sodiumvolt.client.smartfps.SmartFpsPowerSnapshot;
import com.ragedriver.sodiumvolt.client.watchdog.GpuTimeoutWatchdogEngine;
import com.ragedriver.sodiumvolt.client.mixin.ParticleEngineAccessor;
import com.ragedriver.sodiumvolt.client.mixin.ParticleGroupAccessor;
import com.ragedriver.sodiumvolt.client.mixin.TextureAtlasAccessor;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import net.minecraft.resources.Identifier;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VoltInspectorEngine {
	private static final int MAXIMUM_FRAME_SAMPLES = VoltInspectorConfig.SAMPLE_WINDOW_MAX;
	private static final int MAXIMUM_PARTICLE_LABELS = 256;
	private static final int MAXIMUM_PARTICLE_LINES = 5;
	private static final int MAXIMUM_PARTICLES_INSPECTED = 4_096;
	private static final int MAXIMUM_DISPLAY_ENTITIES_INSPECTED = 2_048;
	private static final int MAXIMUM_HUD_LINES = 54;
	private static final int MAXIMUM_DISPLAY_STRING = 96;
	private static final int BACKGROUND_COLOR = 0xB0101218;
	private static final int TITLE_COLOR = 0xFF64E8FF;
	private static final int TEXT_COLOR = 0xFFE6EDF3;
	private static final int MUTED_COLOR = 0xFFB5C0CC;
	private static final Pattern CHUNK_QUEUE_PATTERN = Pattern.compile("\\bB:\\s*S(\\d+)/B(\\d+)/T(\\d+)");
	private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(
			"sodium-volt",
			"volt_inspector"
	);

	private static final VoltInspectorConfig CONFIG = VoltInspectorConfig.getInstance();
	private static final FrameTimeWindow FRAME_TIMES = new FrameTimeWindow(MAXIMUM_FRAME_SAMPLES);
	private static final long[] SORTING_BUFFER = new long[MAXIMUM_FRAME_SAMPLES];
	private static final InspectorSamplingSchedule SAMPLING_SCHEDULE = new InspectorSamplingSchedule();
	private static final InspectorRefreshCadence REFRESH_CADENCE = new InspectorRefreshCadence();
	private static final InspectorSceneScanGate SCENE_SCAN_GATE = new InspectorSceneScanGate();
	private static final InspectorReloadGate RELOAD_GATE = new InspectorReloadGate();
	private static final InspectorHudLayoutCache HUD_LAYOUT =
			new InspectorHudLayoutCache(MAXIMUM_HUD_LINES);
	private static final InspectorHudContentGate HUD_CONTENT =
			new InspectorHudContentGate();
	private static final FontTextMetrics FONT_METRICS = new FontTextMetrics();
	private static final BoundedLabelCounter PARTICLE_COUNTS =
			new BoundedLabelCounter(MAXIMUM_PARTICLE_LABELS);
	private static final IdentityHashMap<Class<?>, String> PARTICLE_LABELS =
			new IdentityHashMap<>(MAXIMUM_PARTICLE_LABELS);

	private static volatile List<String> hudLines = List.of("Volt Inspector", "Collecting samples...");
	private static volatile SceneSnapshot sceneSnapshot = SceneSnapshot.EMPTY;
	private static volatile ReloadResult lastReloadResult = ReloadResult.NONE;
	private static FrameTimeWindow.Statistics frameStatistics = new FrameTimeWindow.Statistics(
			0, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D
	);
	private static ChunkSnapshot chunkSnapshot = ChunkSnapshot.EMPTY;
	private static ParticleSnapshot particleSnapshot = ParticleSnapshot.EMPTY;
	private static GcSnapshot gcSnapshot = GcSnapshot.EMPTY;
	private static double gpuUtilizationSnapshot = -1.0D;
	private static int animatedTextureSnapshot;
	private static boolean frameStatisticsReady;
	private static boolean chunkSnapshotReady;
	private static boolean sceneSnapshotReady;
	private static boolean particleSnapshotReady;
	private static boolean animatedTextureSnapshotReady;
	private static boolean gcSnapshotReady;
	private static boolean gpuUtilizationSnapshotReady;
	private static boolean rendererSnapshotReady;

	private static int previousLevelIdentity;
	private static List<GarbageCollectorMXBean> garbageCollectors;
	private static long previousGcCount = -1L;
	private static long previousGcTimeMs = -1L;
	private static String rendererLine = "Renderer unavailable";
	private static String driverLine = "";
	private static boolean chunkFailureLogged;
	private static boolean particleFailureLogged;
	private static boolean animationFailureLogged;
	private static boolean gcFailureLogged;

	private VoltInspectorEngine() {
	}

	public static void register() {
		LevelExtractionEvents.END_EXTRACTION.register(VoltInspectorEngine::captureSceneComplexity);
		HudElementRegistry.addLast(HUD_ID, VoltInspectorEngine::extractHud);
	}

	public static void publishStatisticsDemand(Minecraft minecraft) {
		InspectorStatisticsDemand.publish(isSamplingVisible(minecraft));
	}

	public static ReloadObservation beginResourceReload() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!isSamplingVisible(minecraft)) {
			return ReloadObservation.DISABLED;
		}
		RELOAD_GATE.resume();
		long generation = RELOAD_GATE.begin();
		if (generation == 0L) {
			return ReloadObservation.DISABLED;
		}
		long startNanos = CONFIG.isResourceReloadTiming() ? System.nanoTime() : 0L;
		return new ReloadObservation(generation, startNanos);
	}

	public static void watchResourceReload(CompletableFuture<Void> future, ReloadObservation observation) {
		if (observation == null
				|| observation.generation == 0L
				|| future == null
				|| !RELOAD_GATE.mayComplete(observation.generation)) {
			return;
		}
		future.whenComplete((ignored, throwable) -> {
			if (!RELOAD_GATE.mayComplete(observation.generation)) {
				return;
			}
			if (observation.startNanos == 0L || !CONFIG.isResourceReloadTiming()) {
				RELOAD_GATE.acceptLayoutCompletion(observation.generation);
				return;
			}
			long elapsed = Math.max(0L, System.nanoTime() - observation.startNanos);
			if (!RELOAD_GATE.mayComplete(observation.generation)) {
				return;
			}
			ReloadResult completed = new ReloadResult(
					observation.generation,
					Math.min(elapsed / 1_000_000L, Integer.MAX_VALUE),
					throwable != null
			);
			publishReloadResult(completed);
		});
	}

	private static synchronized void publishReloadResult(ReloadResult completed) {
		if (RELOAD_GATE.acceptMeasuredCompletion(completed.generation)) {
			lastReloadResult = completed;
		}
	}

	private static void captureSceneComplexity(LevelExtractionContext context) {
		long generation = SCENE_SCAN_GATE.consume();
		if (generation == 0L) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (!isSceneSamplingEligible(minecraft)) {
			return;
		}
		int entities = context.levelState().entityRenderStates.size();
		int displays = 0;
		int displayScanLimit = Math.min(entities, MAXIMUM_DISPLAY_ENTITIES_INSPECTED);
		for (int index = 0; index < displayScanLimit; index++) {
			if (context.levelState().entityRenderStates.get(index) instanceof DisplayEntityRenderState) {
				displays++;
			}
		}
		if (!SCENE_SCAN_GATE.canPublish(generation, isSceneSamplingEligible(minecraft))) {
			return;
		}
		sceneSnapshot = new SceneSnapshot(
				entities,
				context.levelState().blockEntityRenderStates.size(),
				displays,
				entities > displayScanLimit
		);
		sceneSnapshotReady = true;
		REFRESH_CADENCE.markSampled(InspectorRefreshCadence.SCENE);
	}

	private static void extractHud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		boolean samplingVisible = InspectorStatisticsDemand.isRequested()
				&& minecraft.level != null;
		InspectorStatisticsDemand.publish(samplingVisible);
		if (!samplingVisible) {
			suspendSamplingIfNeeded();
			return;
		}
		if (SAMPLING_SCHEDULE.show()) {
			SCENE_SCAN_GATE.resume();
			RELOAD_GATE.resume();
		}
		if (RELOAD_GATE.consumeLayoutInvalidation()) {
			HUD_LAYOUT.invalidate();
			HUD_CONTENT.invalidate();
			SCENE_SCAN_GATE.restart();
			resetHeavySnapshots();
			SAMPLING_SCHEDULE.resetForLevel();
		}
		update(minecraft, System.nanoTime());
		drawHud(graphics, minecraft.font, hudLines);
	}

	private static void update(Minecraft minecraft, long nowNanos) {
		int levelIdentity = System.identityHashCode(minecraft.level);
		boolean paused = minecraft.isPaused();
		if (levelIdentity != previousLevelIdentity) {
			SAMPLING_SCHEDULE.resetForLevel();
			FRAME_TIMES.clear();
			previousLevelIdentity = levelIdentity;
			SCENE_SCAN_GATE.restart();
			sceneSnapshot = SceneSnapshot.EMPTY;
			resetHeavySnapshots();
			HUD_LAYOUT.invalidate();
			HUD_CONTENT.invalidate();
		}

		boolean collectFrameTimes = CONFIG.isFrameTimeStatistics();
		long frameNanos = SAMPLING_SCHEDULE.recordFrame(nowNanos, paused);
		if (frameNanos != 0L && collectFrameTimes) {
			FRAME_TIMES.addNanos(frameNanos, CONFIG.getFrameSampleWindow());
		} else if (!collectFrameTimes) {
			FRAME_TIMES.clear();
		}

		long refreshIntervalNanos = CONFIG.getRefreshIntervalMs() * 1_000_000L;
		if (!SAMPLING_SCHEDULE.claimRefresh(nowNanos, refreshIntervalNanos)) {
			return;
		}
		int phase = REFRESH_CADENCE.beginRefresh();
		samplePhase(minecraft, phase);
		refresh(minecraft);
	}

	private static void samplePhase(Minecraft minecraft, int phase) {
		if (!CONFIG.isFrameTimeStatistics()) {
			frameStatisticsReady = false;
		}
		if (!CONFIG.isChunkActivity()) {
			chunkSnapshot = ChunkSnapshot.EMPTY;
			chunkSnapshotReady = false;
		}
		if (!CONFIG.isSceneComplexity()) {
			SCENE_SCAN_GATE.cancel();
			sceneSnapshot = SceneSnapshot.EMPTY;
			sceneSnapshotReady = false;
		}
		if (!CONFIG.isParticleBreakdown()) {
			particleSnapshot = ParticleSnapshot.EMPTY;
			particleSnapshotReady = false;
		}
		if (!CONFIG.isAnimatedTextureCount()) {
			animatedTextureSnapshot = 0;
			animatedTextureSnapshotReady = false;
		}
		if (!CONFIG.isGcPauseMonitor()) {
			previousGcCount = -1L;
			previousGcTimeMs = -1L;
			gcSnapshot = GcSnapshot.EMPTY;
			gcSnapshotReady = false;
		}
		boolean gpuRequested = CONFIG.isBottleneckEstimate() || CONFIG.isSmartRecommendations();
		if (!gpuRequested) {
			gpuUtilizationSnapshot = -1.0D;
			gpuUtilizationSnapshotReady = false;
		}
		if (!CONFIG.isRendererGpuDetails()) {
			rendererLine = "Renderer unavailable";
			driverLine = "";
			rendererSnapshotReady = false;
		}

		boolean sampled = false;
		switch (phase) {
			case InspectorRefreshCadence.FRAME_AND_CHUNKS -> {
				if (CONFIG.isFrameTimeStatistics()) {
					frameStatistics = FRAME_TIMES.statistics(
							CONFIG.getFrameSampleWindow(),
							SORTING_BUFFER
					);
					frameStatisticsReady = true;
					sampled = true;
				}
				if (CONFIG.isChunkActivity()) {
					chunkSnapshot = sampleChunks();
					chunkSnapshotReady = true;
					sampled = true;
				}
			}
			case InspectorRefreshCadence.SCENE -> {
				if (CONFIG.isSceneComplexity()) {
					SCENE_SCAN_GATE.request();
				}
			}
			case InspectorRefreshCadence.PARTICLES -> {
				if (CONFIG.isParticleBreakdown()) {
					particleSnapshot = sampleParticles(minecraft);
					particleSnapshotReady = true;
					sampled = true;
				}
			}
			case InspectorRefreshCadence.TEXTURES -> {
				if (CONFIG.isAnimatedTextureCount()) {
					animatedTextureSnapshot = sampleAnimatedTextures(minecraft);
					animatedTextureSnapshotReady = true;
					sampled = true;
				}
			}
			case InspectorRefreshCadence.SYSTEM -> {
				if (CONFIG.isGcPauseMonitor()) {
					gcSnapshot = sampleGarbageCollection();
					gcSnapshotReady = true;
					sampled = true;
				}
				if (gpuRequested) {
					gpuUtilizationSnapshot = sampleGpuUtilization(minecraft);
					gpuUtilizationSnapshotReady = true;
					sampled = true;
				}
				if (CONFIG.isRendererGpuDetails()) {
					sampleRendererDetails();
					rendererSnapshotReady = true;
					sampled = true;
				}
			}
			default -> throw new IllegalStateException("Unknown Inspector refresh phase " + phase);
		}
		if (sampled) {
			REFRESH_CADENCE.markSampled(phase);
		}
	}

	private static void refresh(Minecraft minecraft) {
		SceneSnapshot scene = sceneSnapshot;
		FrameTimeWindow.Statistics statistics = frameStatistics;
		ChunkSnapshot chunks = chunkSnapshot;
		ParticleSnapshot particles = particleSnapshot;
		GcSnapshot gc = gcSnapshot;
		double gpuUtilization = gpuUtilizationSnapshot;
		ReloadResult reload = lastReloadResult;
		SmartFpsEngine.StatisticsSnapshot smartFps = SmartFpsEngine.snapshotStatistics();
		VoltRecoveryEngine.StatisticsSnapshot recovery = VoltRecoveryEngine.snapshotStatistics();
		GpuTimeoutWatchdogEngine.StatisticsSnapshot watchdog =
				GpuTimeoutWatchdogEngine.snapshotStatistics();
		ResourcePackShieldEngine.StatisticsSnapshot shield =
				ResourcePackShieldEngine.snapshotStatistics();
		VisibilityAwareParticleScheduler.StatisticsSnapshot schedulerStats =
				VisibilityAwareParticleScheduler.snapshotStatistics();
		BlockEntityRenderBudgetEngine.StatisticsSnapshot blockEntityStats =
				BlockEntityRenderBudgetEngine.snapshotStatistics();
		AnimatedTextureThrottleEngine.StatisticsSnapshot textureStats =
				AnimatedTextureThrottleEngine.snapshotStatistics();
		VramPressureProtectionEngine.StatisticsSnapshot vramStats =
				VramPressureProtectionEngine.snapshotStatistics();
		long fingerprint = 0xcbf29ce484222325L;
		fingerprint = InspectorHudContentGate.mix(fingerprint, smartFps);
		fingerprint = InspectorHudContentGate.mix(fingerprint, recovery);
		fingerprint = InspectorHudContentGate.mix(fingerprint, watchdog);
		fingerprint = InspectorHudContentGate.mix(fingerprint, shield);
		fingerprint = InspectorHudContentGate.mix(fingerprint, statistics);
		fingerprint = InspectorHudContentGate.mix(fingerprint, chunks);
		fingerprint = InspectorHudContentGate.mix(fingerprint, scene);
		fingerprint = InspectorHudContentGate.mix(fingerprint, particles);
		fingerprint = InspectorHudContentGate.mix(fingerprint, schedulerStats);
		fingerprint = InspectorHudContentGate.mix(fingerprint, blockEntityStats);
		fingerprint = InspectorHudContentGate.mix(fingerprint, textureStats);
		fingerprint = InspectorHudContentGate.mix(fingerprint, vramStats);
		fingerprint = InspectorHudContentGate.mix(fingerprint, gc);
		fingerprint = InspectorHudContentGate.mix(fingerprint, Double.doubleToLongBits(gpuUtilization));
		fingerprint = InspectorHudContentGate.mix(fingerprint, animatedTextureSnapshot);
		fingerprint = InspectorHudContentGate.mix(fingerprint, rendererLine);
		fingerprint = InspectorHudContentGate.mix(fingerprint, driverLine);
		fingerprint = InspectorHudContentGate.mix(fingerprint, reload);
		long optionBits = (CONFIG.isFrameTimeStatistics() ? 1L : 0L)
				| (CONFIG.isChunkActivity() ? 1L << 1 : 0L)
				| (CONFIG.isSceneComplexity() ? 1L << 2 : 0L)
				| (CONFIG.isParticleBreakdown() ? 1L << 3 : 0L)
				| (CONFIG.isAnimatedTextureCount() ? 1L << 4 : 0L)
				| (CONFIG.isGcPauseMonitor() ? 1L << 5 : 0L)
				| (CONFIG.isBottleneckEstimate() ? 1L << 6 : 0L)
				| (CONFIG.isRendererGpuDetails() ? 1L << 7 : 0L)
				| (CONFIG.isResourceReloadTiming() ? 1L << 8 : 0L)
				| (CONFIG.isSmartRecommendations() ? 1L << 9 : 0L)
				| (frameStatisticsReady ? 1L << 10 : 0L)
				| (chunkSnapshotReady ? 1L << 11 : 0L)
				| (sceneSnapshotReady ? 1L << 12 : 0L)
				| (particleSnapshotReady ? 1L << 13 : 0L)
				| (animatedTextureSnapshotReady ? 1L << 14 : 0L)
				| (gcSnapshotReady ? 1L << 15 : 0L)
				| (gpuUtilizationSnapshotReady ? 1L << 16 : 0L)
				| (rendererSnapshotReady ? 1L << 17 : 0L);
		fingerprint = InspectorHudContentGate.mix(fingerprint, optionBits);
		fingerprint = InspectorHudContentGate.mix(fingerprint, CONFIG.getSpikeThresholdMs());
		InspectorHudContentKey exactKey = new InspectorHudContentKey(
				smartFps, recovery, watchdog, shield, statistics, chunks, scene, particles,
				schedulerStats, blockEntityStats, textureStats, vramStats, gc,
				Double.doubleToLongBits(gpuUtilization), animatedTextureSnapshot,
				rendererLine, driverLine, reload, optionBits, CONFIG.getSpikeThresholdMs()
		);
		if (!HUD_CONTENT.shouldBuild(fingerprint, exactKey)) {
			return;
		}
		ArrayList<String> lines = new ArrayList<>(MAXIMUM_HUD_LINES);
		addLine(lines, "Volt Inspector");

		if (smartFps != SmartFpsEngine.StatisticsSnapshot.EMPTY) {
			addLine(lines, "Smart FPS: effective " + formatSmartFpsLimit(smartFps.effectiveLimit())
					+ ", Smart cap " + formatSmartFpsLimit(smartFps.smartCap())
					+ " (" + smartFpsReasonSummary(smartFps.reasons()) + ")"
					+ (smartFps.apcSamplingSuspended() ? "; APC sampling paused" : ""));
			addLine(lines, "  Window: " + (smartFps.minimized() ? "minimized" : "not minimized")
					+ ", " + (smartFps.focused() ? "focused" : "unfocused"));
			addLine(lines, "  Power: " + smartFpsPowerSummary(
					smartFps.powerState(),
					smartFps.batteryPercentage()
			));
		}

		if (recovery != VoltRecoveryEngine.StatisticsSnapshot.EMPTY) {
			addLine(lines, "Volt Recovery: "
					+ recovery.status().name().toLowerCase(Locale.ROOT).replace('_', ' ')
					+ ", streak " + recovery.crashStreak()
					+ ", attempt " + recovery.recoveryAttempts()
					+ "/" + recovery.maximumAttempts());
			addLine(lines, "  Owned profile: " + (recovery.ownedProfile() ? "yes" : "no")
					+ (recovery.secondsToStable() > 0L
							? ", stable in " + recovery.secondsToStable() + "s"
							: ""));
			addLine(lines, "  Recovery cap: "
					+ (recovery.recoveryFpsCap() == Integer.MAX_VALUE
							? "inactive"
							: recovery.recoveryFpsCap() + " FPS")
					+ ", APC sampling "
					+ (recovery.apcSuspended() ? "paused" : "available"));
		}

		if (watchdog != GpuTimeoutWatchdogEngine.StatisticsSnapshot.EMPTY) {
			addLine(lines, "GPU Watchdog: "
					+ watchdog.status().name().toLowerCase(Locale.ROOT).replace('_', ' ')
					+ ", last " + watchdog.latestDurationMillis() + " ms");
			addLine(lines, "  Incidents: " + watchdog.incidentCount()
					+ "/" + watchdog.maximumIncidents()
					+ (watchdog.capReached() ? " (session cap reached)" : ""));
			addLine(lines, "  Recovery next launch: "
					+ (watchdog.recoveryRequestStaged() ? "staged" : "not staged"));
		}

		if (shield != ResourcePackShieldEngine.StatisticsSnapshot.EMPTY) {
			addLine(lines, "Resource-Pack Shield: "
					+ (shield.rejectMode() ? "reject" : "monitor")
					+ ", packs " + shield.packsScanned()
					+ " (local " + shield.localPacks()
					+ ", server " + shield.serverPacks() + ")");
			addLine(lines, "  Policy events: " + shield.violations()
					+ ", rejected " + shield.rejections()
					+ ", monitor failures " + shield.monitorFailures());
			addLine(lines, "  Bounded resources " + shield.resourcesSeen()
					+ ", declared/read "
					+ shield.declaredBytes() / (1024L * 1024L) + "/"
					+ shield.lastEventLiveBytes() / (1024L * 1024L) + " MiB"
					+ (shield.lastReason()
									== com.ragedriver.sodiumvolt.client.resourcepack.ShieldReason.NONE
							? ""
							: ", last " + shield.fixedReason()));
		}

		if (CONFIG.isFrameTimeStatistics()) {
			if (!frameStatisticsReady || statistics.sampleCount() == 0) {
				addLine(lines, "Frame times: collecting..."
						+ freshness(InspectorRefreshCadence.FRAME_AND_CHUNKS, frameStatisticsReady));
			} else {
				addLine(lines, String.format(
						Locale.ROOT,
						"Frame ms  avg %.1f  median %.1f  (%d samples)",
						statistics.averageMs(),
						statistics.medianMs(),
						statistics.sampleCount()
				) + freshness(InspectorRefreshCadence.FRAME_AND_CHUNKS, true));
				addLine(lines, String.format(
						Locale.ROOT,
						"Percentiles  p95 %.1f  p99 %.1f  p99.5 %.1f",
						statistics.p95Ms(),
						statistics.p99Ms(),
						statistics.p995Ms()
				));
			}
		}

		if (CONFIG.isChunkActivity()) {
			addLine(lines, "Chunks: visible " + chunks.visibleChunks
					+ ", terrain " + (chunks.terrainComplete ? "settled" : "updating")
					+ freshness(InspectorRefreshCadence.FRAME_AND_CHUNKS, chunkSnapshotReady));
			addLine(lines, chunks.activity);
		}

		if (CONFIG.isSceneComplexity()) {
			addLine(lines, InspectorFormatting.sceneSummary(
					scene.entities,
					scene.blockEntities,
					scene.displayEntities,
					scene.displaysTruncated
			) + freshness(InspectorRefreshCadence.SCENE, sceneSnapshotReady));
		}

		if (CONFIG.isParticleBreakdown()) {
			addLine(lines, InspectorFormatting.particleSummary(
					particles.total,
					particles.truncated,
					Math.min(MAXIMUM_PARTICLE_LINES, particles.top.size())
			) + freshness(InspectorRefreshCadence.PARTICLES, particleSnapshotReady));
			for (BoundedLabelCounter.Entry entry : particles.top) {
				addLine(lines, "  " + entry.label() + ": " + entry.count());
			}
		}

		if (schedulerStats != VisibilityAwareParticleScheduler.StatisticsSnapshot.EMPTY) {
			addLine(lines, "VAPS: render-limited " + schedulerStats.renderLimited()
					+ ", age-only skips " + schedulerStats.simulationSkips());
			addLine(lines, "  Behind " + schedulerStats.behind()
					+ ", coalesced " + schedulerStats.coalesced()
					+ ", type-cap " + schedulerStats.perType()
					+ ", critical overflow " + schedulerStats.criticalOverflow());
			if (schedulerStats.truncatedFrames() > 0L || schedulerStats.saturated()) {
				addLine(lines, "  Fail-open bounds: scan-cap frames "
						+ schedulerStats.truncatedFrames()
						+ (schedulerStats.saturated() ? ", table saturation observed" : ""));
			}
			for (VisibilityAwareParticleScheduler.TypeStatistic type : schedulerStats.topTypes()) {
				addLine(lines, "  " + type.label() + ": render " + type.renderLimited()
						+ ", sim " + type.simulationSkips());
			}
		}

		if (blockEntityStats != BlockEntityRenderBudgetEngine.StatisticsSnapshot.EMPTY) {
			addLine(lines, "Block entities: fresh " + blockEntityStats.freshExtractions()
					+ ", cache hits " + blockEntityStats.cacheHits()
					+ ", cached " + blockEntityStats.cacheSize());
			addLine(lines, "  Limited: far " + blockEntityStats.farCulled()
					+ ", global " + blockEntityStats.globalLimited()
					+ ", type " + blockEntityStats.perTypeLimited()
					+ ", protected " + blockEntityStats.protectedStates());
			addLine(lines, "  Cache churn: evicted " + blockEntityStats.cacheEvictions()
					+ ", expired " + blockEntityStats.cacheExpirations());
			if (blockEntityStats.truncatedFrames() > 0L || blockEntityStats.saturated()) {
				addLine(lines, "  Fail-open bounds: scan-cap frames "
						+ blockEntityStats.truncatedFrames()
						+ (blockEntityStats.saturated() ? ", table saturation observed" : ""));
			}
			for (BlockEntityRenderBudgetEngine.TypeStatistic type : blockEntityStats.topTypes()) {
				addLine(lines, "  " + type.label() + ": limited " + type.limited()
						+ ", cache " + type.cacheHits());
			}
		}

		if (textureStats != AnimatedTextureThrottleEngine.StatisticsSnapshot.EMPTY) {
			addLine(lines, "ATT: ticked " + textureStats.ticked()
					+ ", paused " + textureStats.skippedInvisible()
					+ ", cadence " + textureStats.skippedCadence());
			addLine(lines, "  Budget skips " + textureStats.skippedBudget()
					+ ", protected " + textureStats.protectedTicks()
					+ ", exempt " + textureStats.exemptTicks());
			addLine(lines, "  Visible sections " + textureStats.visibleSections()
					+ ", sprites " + textureStats.visibleSprites()
					+ ", map fail-open " + textureStats.mappingFallbacks()
					+ (textureStats.scanTruncated() ? " (scan cap; fail-open)" : ""));
		}

		if (vramStats != VramPressureProtectionEngine.StatisticsSnapshot.EMPTY) {
			if (vramStats.metricFailed()) {
				addLine(lines, "VRAM estimate: unavailable (fail-open)");
			} else {
				addLine(lines, "VRAM estimate: " + formatMib(vramStats.estimatedBytes())
						+ " / " + formatMib(vramStats.budgetBytes())
						+ " (" + vramStats.pressurePercent() + "%, "
						+ vramStats.level().name().toLowerCase(Locale.ROOT) + ", "
						+ vramStats.budgetSource().label() + ")");
			}
			addLine(lines, "  Textures " + formatMib(vramStats.textureBytes())
					+ " (" + vramStats.textureCount() + "), buffers "
					+ formatMib(vramStats.bufferBytes())
					+ " (" + vramStats.bufferCount() + ")");
			addLine(lines, "  Render-target estimate "
					+ formatMib(vramStats.renderAttachmentBytes())
					+ " (" + vramStats.renderAttachmentCount() + "), peak "
					+ formatMib(vramStats.peakTrackedBytes()));
			addLine(lines, "  Safe cap "
					+ (vramStats.safeRenderDistanceCap() < 0
							? "inactive"
							: vramStats.safeRenderDistanceCap() + " chunks")
					+ ", actions " + vramStats.actionCount()
					+ ", spikes " + vramStats.spikeCount());
		}

		if (CONFIG.isAnimatedTextureCount()) {
			addLine(lines, "Animated texture states: " + animatedTextureSnapshot
					+ freshness(InspectorRefreshCadence.TEXTURES, animatedTextureSnapshotReady));
		}

		if (CONFIG.isGcPauseMonitor()) {
			addLine(lines, "JVM collection: +" + gc.timeDeltaMs + " ms, +" + gc.countDelta
					+ " collections (reported)"
					+ freshness(InspectorRefreshCadence.SYSTEM, gcSnapshotReady));
		}

		if (CONFIG.isBottleneckEstimate()) {
			InspectorAnalysis.Bottleneck bottleneck = InspectorAnalysis.estimateBottleneck(
					gpuUtilization,
					statistics.p95Ms(),
					CONFIG.getSpikeThresholdMs()
			);
			addLine(lines, "Bottleneck: " + bottleneck.label() + ", GPU "
					+ formatGpuUtilization(gpuUtilization)
					+ freshness(InspectorRefreshCadence.SYSTEM, gpuUtilizationSnapshotReady));
		}

		if (CONFIG.isRendererGpuDetails()) {
			addLine(lines, rendererLine
					+ freshness(InspectorRefreshCadence.SYSTEM, rendererSnapshotReady));
			if (!driverLine.isEmpty()) {
				addLine(lines, driverLine);
			}
		}

		if (CONFIG.isResourceReloadTiming()) {
			if (reload.durationMs < 0L) {
				addLine(lines, "Resource reload: no observed reload yet");
			} else {
				addLine(lines, "Resource reload: " + reload.durationMs + " ms"
						+ (reload.failed ? " (failed)" : " (completed)"));
			}
		}

		if (CONFIG.isSmartRecommendations()) {
			List<String> recommendations = InspectorAnalysis.recommendations(
					statistics.p95Ms(),
					CONFIG.getSpikeThresholdMs(),
					particles.total,
					CONFIG.isSceneComplexity() ? scene.blockEntities : 0,
					CONFIG.isSceneComplexity() ? scene.displayEntities : 0,
					gc.timeDeltaMs,
					gpuUtilization,
					chunks.scheduledBuilds,
					CONFIG.isResourceReloadTiming() ? reload.durationMs : -1L,
					CONFIG.isFrameTimeStatistics() && frameStatisticsReady,
					CONFIG.isChunkActivity() && chunkSnapshotReady,
					CONFIG.isParticleBreakdown() && particleSnapshotReady,
					CONFIG.isSceneComplexity() && sceneSnapshotReady,
					CONFIG.isGcPauseMonitor() && gcSnapshotReady,
					CONFIG.isSmartRecommendations() && gpuUtilizationSnapshotReady
			);
			if (recommendations.isEmpty()) {
				addLine(lines, "Suggestion: no sustained pressure detected in current samples.");
			} else {
				addLine(lines, "Suggestions:");
				for (String recommendation : recommendations) {
					addLine(lines, "  • " + recommendation);
				}
			}
		}
		hudLines = List.copyOf(lines);
		HUD_LAYOUT.invalidate();
	}

	private static String formatMib(long bytes) {
		if (bytes < 0L) {
			return "unknown";
		}
		return String.format(Locale.ROOT, "%.1f MiB", bytes / 1_048_576.0D);
	}

	private static String formatSmartFpsLimit(int limit) {
		return limit == SmartFpsPolicy.NO_CAP ? "none" : Math.max(1, limit) + " FPS";
	}

	private static String smartFpsReasonSummary(int reasons) {
		if (reasons == 0) {
			return "inactive";
		}
		StringBuilder result = new StringBuilder(48);
		appendSmartFpsReason(result, reasons, SmartFpsPolicy.REASON_MINIMIZED, "minimized");
		appendSmartFpsReason(result, reasons, SmartFpsPolicy.REASON_UNFOCUSED, "unfocused");
		appendSmartFpsReason(result, reasons, SmartFpsPolicy.REASON_BATTERY, "battery");
		appendSmartFpsReason(result, reasons, SmartFpsPolicy.REASON_LOW_BATTERY, "low battery");
		return result.isEmpty() ? "unknown" : result.toString();
	}

	private static void appendSmartFpsReason(
			StringBuilder result,
			int reasons,
			int flag,
			String label
	) {
		if ((reasons & flag) == 0) {
			return;
		}
		if (!result.isEmpty()) {
			result.append(" + ");
		}
		result.append(label);
	}

	private static String smartFpsPowerSummary(
			SmartFpsPowerSnapshot.PowerState state,
			int percentage
	) {
		if (state == null || state == SmartFpsPowerSnapshot.PowerState.UNKNOWN) {
			return "unknown";
		}
		String label = state == SmartFpsPowerSnapshot.PowerState.CHARGING
				? "charging"
				: "discharging";
		return percentage >= 0 && percentage <= 100
				? label + " (" + percentage + "%)"
				: label;
	}

	private static ChunkSnapshot sampleChunks() {
		try {
			SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();
			if (renderer == null) {
				return ChunkSnapshot.EMPTY;
			}
			int visible = Math.max(0, renderer.getVisibleChunkCount());
			boolean complete = renderer.isTerrainRenderComplete();
			Collection<String> debugStrings = renderer.getDebugStrings(false);
			String activity = "Chunk builder/transfer activity unavailable";
			int scheduled = -1;
			int inspected = 0;
			boolean transferReported = false;
			for (String value : debugStrings) {
				if (value == null || inspected++ >= 4) {
					break;
				}
				String sanitized = sanitize(value, MAXIMUM_DISPLAY_STRING);
				Matcher matcher = CHUNK_QUEUE_PATTERN.matcher(sanitized);
				if (matcher.find()) {
					scheduled = parseBoundedInt(matcher.group(1));
					activity = "Chunk builder: scheduled " + scheduled
							+ ", busy " + parseBoundedInt(matcher.group(2))
							+ "/" + parseBoundedInt(matcher.group(3));
				} else if (sanitized.contains("TQ:")) {
					transferReported = true;
				}
			}
			if (transferReported) {
				activity += "; transfer queue reported";
			}
			return new ChunkSnapshot(visible, complete, scheduled, activity);
		} catch (RuntimeException | LinkageError exception) {
			if (!chunkFailureLogged) {
				chunkFailureLogged = true;
				SodiumVolt.LOGGER.debug("Volt Inspector could not sample Sodium chunk diagnostics", exception);
			}
			return ChunkSnapshot.EMPTY;
		}
	}

	private static ParticleSnapshot sampleParticles(Minecraft minecraft) {
		boolean truncated = false;
		try {
			truncated = collectParticleCounts(minecraft);
		} catch (RuntimeException | LinkageError exception) {
			if (!particleFailureLogged) {
				particleFailureLogged = true;
				SodiumVolt.LOGGER.debug(
						"Volt Inspector could not sample particle implementations",
						exception
				);
			}
		}
		try {
			return new ParticleSnapshot(
					PARTICLE_COUNTS.total(),
					truncated,
					PARTICLE_COUNTS.top(MAXIMUM_PARTICLE_LINES)
			);
		} finally {
			PARTICLE_COUNTS.clear();
			PARTICLE_LABELS.clear();
		}
	}

	private static boolean collectParticleCounts(Minecraft minecraft) {
		Map<ParticleRenderType, ParticleGroup<?>> groups =
				((ParticleEngineAccessor) minecraft.particleEngine).sodiumVolt$getParticleGroups();
		for (ParticleGroup<?> group : groups.values()) {
			for (Particle particle : ((ParticleGroupAccessor) group).sodiumVolt$getParticles()) {
				if (PARTICLE_COUNTS.total() >= MAXIMUM_PARTICLES_INSPECTED) {
					return true;
				}
				Class<?> particleClass = particle.getClass();
				String label = PARTICLE_LABELS.get(particleClass);
				if (label == null) {
					if (PARTICLE_LABELS.size() < MAXIMUM_PARTICLE_LABELS) {
						label = particleLabel(particleClass);
						PARTICLE_LABELS.put(particleClass, label);
					} else {
						label = "other implementations";
					}
				}
				PARTICLE_COUNTS.add(label);
			}
		}
		return false;
	}

	private static int sampleAnimatedTextures(Minecraft minecraft) {
		int[] count = {0};
		try {
			minecraft.getAtlasManager().forEach((id, atlas) -> {
				List<?> animations = ((TextureAtlasAccessor) atlas).sodiumVolt$getAnimatedTextureStates();
				if (animations != null) {
					count[0] = saturatingAdd(count[0], animations.size());
				}
			});
		} catch (RuntimeException | LinkageError exception) {
			if (!animationFailureLogged) {
				animationFailureLogged = true;
				SodiumVolt.LOGGER.debug("Volt Inspector could not sample animated texture states", exception);
			}
		}
		return count[0];
	}

	private static GcSnapshot sampleGarbageCollection() {
		try {
			if (garbageCollectors == null) {
				garbageCollectors = List.copyOf(ManagementFactory.getGarbageCollectorMXBeans());
			}
			long totalCount = 0L;
			long totalTime = 0L;
			for (GarbageCollectorMXBean bean : garbageCollectors) {
				long count = bean.getCollectionCount();
				long time = bean.getCollectionTime();
				if (count >= 0L) {
					totalCount = saturatingAdd(totalCount, count);
				}
				if (time >= 0L) {
					totalTime = saturatingAdd(totalTime, time);
				}
			}
			long countDelta = previousGcCount < 0L ? 0L : Math.max(0L, totalCount - previousGcCount);
			long timeDelta = previousGcTimeMs < 0L ? 0L : Math.max(0L, totalTime - previousGcTimeMs);
			previousGcCount = totalCount;
			previousGcTimeMs = totalTime;
			return new GcSnapshot(countDelta, timeDelta);
		} catch (RuntimeException | LinkageError exception) {
			if (!gcFailureLogged) {
				gcFailureLogged = true;
				SodiumVolt.LOGGER.debug("Volt Inspector could not sample JVM collection metrics", exception);
			}
			previousGcCount = -1L;
			previousGcTimeMs = -1L;
			return GcSnapshot.EMPTY;
		}
	}

	private static double sampleGpuUtilization(Minecraft minecraft) {
		try {
			double utilization = minecraft.getGpuUtilization();
			return Double.isFinite(utilization) ? Math.clamp(utilization, 0.0D, 100.0D) : -1.0D;
		} catch (RuntimeException exception) {
			return -1.0D;
		}
	}

	private static void sampleRendererDetails() {
		try {
			GpuDevice device = RenderSystem.tryGetDevice();
			if (device == null) {
				rendererLine = "Renderer unavailable";
				driverLine = "";
				return;
			}
			DeviceInfo info = device.getDeviceInfo();
			rendererLine = "Backend " + sanitize(info.backendName(), 20)
					+ ": " + sanitize(info.name(), 54)
					+ " (" + sanitize(info.vendorName(), 30) + ")";
			driverLine = "Driver: " + sanitize(info.driverInfo(), 78);
		} catch (RuntimeException | LinkageError exception) {
			rendererLine = "Renderer details unavailable";
			driverLine = "";
		}
	}

	private static void drawHud(GuiGraphicsExtractor graphics, Font font, List<String> lines) {
		FONT_METRICS.use(font);
		HUD_LAYOUT.prepare(
				lines,
				font,
				graphics.guiWidth(),
				graphics.guiHeight(),
				FONT_METRICS
		);
		int visibleLineCount = HUD_LAYOUT.visibleLineCount();
		if (visibleLineCount == 0) {
			return;
		}
		int left = HUD_LAYOUT.left();
		int top = HUD_LAYOUT.top();
		graphics.fill(left - 4, top, HUD_LAYOUT.right(), HUD_LAYOUT.bottom(), BACKGROUND_COLOR);
		graphics.outline(left - 4, top, HUD_LAYOUT.right(), HUD_LAYOUT.bottom(), 0x8059DCEC);
		for (int index = 0; index < visibleLineCount; index++) {
			String line = HUD_LAYOUT.lineAt(index);
			int color = index == 0 ? TITLE_COLOR : line.startsWith("  ") ? MUTED_COLOR : TEXT_COLOR;
			graphics.text(
					font,
					line,
					left,
					top + 4 + index * 10,
					color,
					true
			);
		}
	}

	private static String truncateOverflowToWidth(Font font, String value, int maximumWidth) {
		if (maximumWidth <= 0) {
			return "";
		}
		String ellipsis = "…";
		int ellipsisWidth = font.width(ellipsis);
		if (ellipsisWidth >= maximumWidth) {
			return font.plainSubstrByWidth(value, maximumWidth);
		}
		return font.plainSubstrByWidth(value, maximumWidth - ellipsisWidth) + ellipsis;
	}

	private static String particleLabel(Class<?> particleClass) {
		String name = particleClass.getSimpleName();
		if (name.isEmpty()) {
			name = particleClass.getName();
			int separator = name.lastIndexOf('.');
			if (separator >= 0) {
				name = name.substring(separator + 1);
			}
		}
		return sanitize(name, 40);
	}

	private static String sanitize(String value, int maximumLength) {
		if (value == null || value.isBlank()) {
			return "unknown";
		}
		StringBuilder sanitized = new StringBuilder(Math.min(value.length(), maximumLength));
		for (int index = 0; index < value.length() && sanitized.length() < maximumLength; index++) {
			char character = value.charAt(index);
			if (!Character.isISOControl(character)) {
				sanitized.append(character);
			}
		}
		return sanitized.isEmpty() ? "unknown" : sanitized.toString();
	}

	private static String formatGpuUtilization(double value) {
		return value < 0.0D ? "unavailable" : String.format(Locale.ROOT, "%.0f%%", value);
	}

	private static void addLine(ArrayList<String> lines, String line) {
		if (lines.size() < MAXIMUM_HUD_LINES) {
			lines.add(sanitize(line, 180));
		}
	}

	private static String freshness(int phase, boolean ready) {
		if (!ready) {
			return " [awaiting phased sample]";
		}
		// Ready values are rebuilt only when their immutable snapshot changes.
		// Avoid a per-refresh age string that would defeat content caching.
		return "";
	}

	private static void resetHeavySnapshots() {
		REFRESH_CADENCE.reset();
		frameStatistics = new FrameTimeWindow.Statistics(0, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
		chunkSnapshot = ChunkSnapshot.EMPTY;
		sceneSnapshot = SceneSnapshot.EMPTY;
		particleSnapshot = ParticleSnapshot.EMPTY;
		gcSnapshot = GcSnapshot.EMPTY;
		gpuUtilizationSnapshot = -1.0D;
		animatedTextureSnapshot = 0;
		frameStatisticsReady = false;
		chunkSnapshotReady = false;
		sceneSnapshotReady = false;
		particleSnapshotReady = false;
		animatedTextureSnapshotReady = false;
		gcSnapshotReady = false;
		gpuUtilizationSnapshotReady = false;
		rendererSnapshotReady = false;
		rendererLine = "Renderer unavailable";
		driverLine = "";
		PARTICLE_COUNTS.clear();
		PARTICLE_LABELS.clear();
	}

	private static int parseBoundedInt(String value) {
		try {
			return Math.clamp(Integer.parseInt(value), 0, 1_000_000);
		} catch (NumberFormatException exception) {
			return -1;
		}
	}

	private static int saturatingAdd(int first, int second) {
		return first > Integer.MAX_VALUE - second ? Integer.MAX_VALUE : first + second;
	}

	private static long saturatingAdd(long first, long second) {
		return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
	}

	private static boolean isSamplingVisible(Minecraft minecraft) {
		if (!CONFIG.isSamplingRequested()) {
			return false;
		}
		return InspectorSamplingSchedule.shouldSample(
				true,
				true,
				PrivacyScreenshotEngine.isCaptureActive(),
				minecraft != null && minecraft.level != null
		);
	}

	private static boolean isSceneSamplingEligible(Minecraft minecraft) {
		return CONFIG.isSceneComplexity() && isSamplingVisible(minecraft);
	}

	private static void suspendSamplingIfNeeded() {
		InspectorStatisticsDemand.publish(false);
		boolean samplingTransition = SAMPLING_SCHEDULE.hide();
		boolean reloadObservationActive = RELOAD_GATE.isAccepting();
		if (!samplingTransition && !reloadObservationActive) {
			return;
		}
		if (samplingTransition) {
			SCENE_SCAN_GATE.suspend();
			resetHeavySnapshots();
		}
		previousLevelIdentity = 0;
		sceneSnapshot = SceneSnapshot.EMPTY;
		previousGcCount = -1L;
		previousGcTimeMs = -1L;
		FRAME_TIMES.clear();
		PARTICLE_COUNTS.clear();
		PARTICLE_LABELS.clear();
		invalidateReloadResultsIfNeeded();
		rendererLine = "Renderer unavailable";
		driverLine = "";
		hudLines = List.of("Volt Inspector", "Collecting samples...");
		HUD_LAYOUT.invalidate();
		HUD_CONTENT.invalidate();
		FONT_METRICS.clear();
	}

	private static synchronized void invalidateReloadResultsIfNeeded() {
		long generation = RELOAD_GATE.suspendIfAccepting();
		if (generation != 0L) {
			lastReloadResult = new ReloadResult(generation, -1L, false);
		}
	}

	private record ChunkSnapshot(int visibleChunks, boolean terrainComplete, int scheduledBuilds, String activity) {
		private static final ChunkSnapshot EMPTY =
				new ChunkSnapshot(0, true, -1, "Chunk builder/transfer activity unavailable");
	}

	private record ParticleSnapshot(int total, boolean truncated, List<BoundedLabelCounter.Entry> top) {
		private static final ParticleSnapshot EMPTY = new ParticleSnapshot(0, false, List.of());
	}

	private record GcSnapshot(long countDelta, long timeDeltaMs) {
		private static final GcSnapshot EMPTY = new GcSnapshot(0L, 0L);
	}

	private record SceneSnapshot(
			int entities,
			int blockEntities,
			int displayEntities,
			boolean displaysTruncated
	) {
		private static final SceneSnapshot EMPTY = new SceneSnapshot(0, 0, 0, false);
	}

	private static final class FontTextMetrics implements InspectorHudLayoutCache.TextMetrics {
		private Font font;

		private void use(Font font) {
			this.font = font;
		}

		private void clear() {
			this.font = null;
		}

		@Override
		public int width(String value) {
			return this.font.width(value);
		}

		@Override
		public String truncateOverflow(String value, int maximumWidth) {
			return truncateOverflowToWidth(this.font, value, maximumWidth);
		}
	}

	public record ReloadObservation(long generation, long startNanos) {
		private static final ReloadObservation DISABLED = new ReloadObservation(0L, 0L);
	}

	private record ReloadResult(long generation, long durationMs, boolean failed) {
		private static final ReloadResult NONE = new ReloadResult(0L, -1L, false);
	}
}
