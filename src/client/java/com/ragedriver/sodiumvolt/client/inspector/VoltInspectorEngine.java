package com.ragedriver.sodiumvolt.client.inspector;

import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.config.VoltInspectorConfig;
import com.ragedriver.sodiumvolt.client.performance.BlockEntityRenderBudgetEngine;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VoltInspectorEngine {
	private static final int MAXIMUM_FRAME_SAMPLES = VoltInspectorConfig.SAMPLE_WINDOW_MAX;
	private static final int MAXIMUM_PARTICLE_LABELS = 256;
	private static final int MAXIMUM_PARTICLE_LINES = 5;
	private static final int MAXIMUM_PARTICLES_INSPECTED = 32_768;
	private static final int MAXIMUM_DISPLAY_ENTITIES_INSPECTED = 8_192;
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

	private static volatile List<String> hudLines = List.of("Volt Inspector", "Collecting samples...");
	private static volatile int visibleEntities;
	private static volatile int visibleBlockEntities;
	private static volatile int visibleDisplayEntities;
	private static volatile boolean visibleDisplaysTruncated;
	private static final AtomicLong RELOAD_GENERATION = new AtomicLong();
	private static volatile ReloadResult lastReloadResult = ReloadResult.NONE;

	private static boolean active;
	private static boolean wasPaused;
	private static long previousFrameNanos;
	private static long nextRefreshNanos;
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

	public static ReloadObservation beginResourceReload() {
		if (!CONFIG.isVoltInspectorEnabled() || !CONFIG.isResourceReloadTiming()) {
			return ReloadObservation.DISABLED;
		}
		return new ReloadObservation(RELOAD_GENERATION.incrementAndGet(), System.nanoTime());
	}

	public static void watchResourceReload(CompletableFuture<Void> future, ReloadObservation observation) {
		if (observation == null || observation.startNanos == 0L || future == null) {
			return;
		}
		future.whenComplete((ignored, throwable) -> {
			long elapsed = Math.max(0L, System.nanoTime() - observation.startNanos);
			ReloadResult completed = new ReloadResult(
					observation.generation,
					Math.min(elapsed / 1_000_000L, Integer.MAX_VALUE),
					throwable != null
			);
			publishReloadResult(completed);
		});
	}

	private static synchronized void publishReloadResult(ReloadResult completed) {
		if (completed.generation >= lastReloadResult.generation) {
			lastReloadResult = completed;
		}
	}

	private static void captureSceneComplexity(LevelExtractionContext context) {
		if (!CONFIG.isVoltInspectorEnabled() || !CONFIG.isSceneComplexity()) {
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
		visibleEntities = entities;
		visibleBlockEntities = context.levelState().blockEntityRenderStates.size();
		visibleDisplayEntities = displays;
		visibleDisplaysTruncated = entities > displayScanLimit;
	}

	private static void extractHud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		update(minecraft, System.nanoTime());
		if (!CONFIG.isVoltInspectorEnabled()
				|| !CONFIG.isShowInspectorOverlay()
				|| PrivacyScreenshotEngine.isCaptureActive()
				|| minecraft.level == null) {
			return;
		}
		drawHud(graphics, minecraft.font, hudLines);
	}

	private static void update(Minecraft minecraft, long nowNanos) {
		if (!CONFIG.isVoltInspectorEnabled()) {
			if (active) {
				reset();
			}
			return;
		}
		active = true;

		int levelIdentity = minecraft.level == null ? 0 : System.identityHashCode(minecraft.level);
		boolean paused = minecraft.isPaused();
		if (levelIdentity != previousLevelIdentity) {
			previousFrameNanos = 0L;
			FRAME_TIMES.clear();
			previousLevelIdentity = levelIdentity;
		}
		if (paused) {
			previousFrameNanos = 0L;
			wasPaused = true;
		} else if (wasPaused) {
			// Establish a new baseline and strictly skip the first frame after a pause.
			previousFrameNanos = 0L;
			wasPaused = false;
		}

		if (previousFrameNanos != 0L && CONFIG.isFrameTimeStatistics()) {
			FRAME_TIMES.addNanos(nowNanos - previousFrameNanos, CONFIG.getFrameSampleWindow());
		} else if (!CONFIG.isFrameTimeStatistics()) {
			FRAME_TIMES.clear();
		}
		previousFrameNanos = nowNanos;

		if (nowNanos < nextRefreshNanos) {
			return;
		}
		nextRefreshNanos = nowNanos + CONFIG.getRefreshIntervalMs() * 1_000_000L;
		refresh(minecraft);
	}

	private static void refresh(Minecraft minecraft) {
		ArrayList<String> lines = new ArrayList<>(MAXIMUM_HUD_LINES);
		addLine(lines, "Volt Inspector");

		SmartFpsEngine.StatisticsSnapshot smartFps = SmartFpsEngine.snapshotStatistics();
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

		VoltRecoveryEngine.StatisticsSnapshot recovery =
				VoltRecoveryEngine.snapshotStatistics();
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

		GpuTimeoutWatchdogEngine.StatisticsSnapshot watchdog =
				GpuTimeoutWatchdogEngine.snapshotStatistics();
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

		ResourcePackShieldEngine.StatisticsSnapshot shield =
				ResourcePackShieldEngine.snapshotStatistics();
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

		FrameTimeWindow.Statistics statistics = CONFIG.isFrameTimeStatistics()
				? FRAME_TIMES.statistics(CONFIG.getFrameSampleWindow(), SORTING_BUFFER)
				: new FrameTimeWindow.Statistics(0, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
		if (CONFIG.isFrameTimeStatistics()) {
			if (statistics.sampleCount() == 0) {
				addLine(lines, "Frame times: collecting...");
			} else {
				addLine(lines, String.format(
						Locale.ROOT,
						"Frame ms  avg %.1f  median %.1f  (%d samples)",
						statistics.averageMs(),
						statistics.medianMs(),
						statistics.sampleCount()
				));
				addLine(lines, String.format(
						Locale.ROOT,
						"Percentiles  p95 %.1f  p99 %.1f  p99.5 %.1f",
						statistics.p95Ms(),
						statistics.p99Ms(),
						statistics.p995Ms()
				));
			}
		}

		ChunkSnapshot chunks = CONFIG.isChunkActivity() ? sampleChunks() : ChunkSnapshot.EMPTY;
		if (CONFIG.isChunkActivity()) {
			addLine(lines, "Chunks: visible " + chunks.visibleChunks
					+ ", terrain " + (chunks.terrainComplete ? "settled" : "updating"));
			addLine(lines, chunks.activity);
		}

		if (CONFIG.isSceneComplexity()) {
			addLine(lines, InspectorFormatting.sceneSummary(
					visibleEntities,
					visibleBlockEntities,
					visibleDisplayEntities,
					visibleDisplaysTruncated
			));
		}

		ParticleSnapshot particles = CONFIG.isParticleBreakdown()
				? sampleParticles(minecraft)
				: ParticleSnapshot.EMPTY;
		if (CONFIG.isParticleBreakdown()) {
			addLine(lines, InspectorFormatting.particleSummary(
					particles.total,
					particles.truncated,
					Math.min(MAXIMUM_PARTICLE_LINES, particles.top.size())
			));
			for (BoundedLabelCounter.Entry entry : particles.top) {
				addLine(lines, "  " + entry.label() + ": " + entry.count());
			}
		}

		VisibilityAwareParticleScheduler.StatisticsSnapshot schedulerStats =
				VisibilityAwareParticleScheduler.snapshotStatistics();
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

		BlockEntityRenderBudgetEngine.StatisticsSnapshot blockEntityStats =
				BlockEntityRenderBudgetEngine.snapshotStatistics();
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

		AnimatedTextureThrottleEngine.StatisticsSnapshot textureStats =
				AnimatedTextureThrottleEngine.snapshotStatistics();
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

		VramPressureProtectionEngine.StatisticsSnapshot vramStats =
				VramPressureProtectionEngine.snapshotStatistics();
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
			addLine(lines, "Animated texture states: " + sampleAnimatedTextures(minecraft));
		}

		GcSnapshot gc;
		if (CONFIG.isGcPauseMonitor()) {
			gc = sampleGarbageCollection();
		} else {
			previousGcCount = -1L;
			previousGcTimeMs = -1L;
			gc = GcSnapshot.EMPTY;
		}
		if (CONFIG.isGcPauseMonitor()) {
			addLine(lines, "JVM collection: +" + gc.timeDeltaMs + " ms, +" + gc.countDelta
					+ " collections (reported)");
		}

		double gpuUtilization = CONFIG.isBottleneckEstimate() || CONFIG.isSmartRecommendations()
				? sampleGpuUtilization(minecraft)
				: -1.0D;
		if (CONFIG.isBottleneckEstimate()) {
			InspectorAnalysis.Bottleneck bottleneck = InspectorAnalysis.estimateBottleneck(
					gpuUtilization,
					statistics.p95Ms(),
					CONFIG.getSpikeThresholdMs()
			);
			addLine(lines, "Bottleneck: " + bottleneck.label() + ", GPU "
					+ formatGpuUtilization(gpuUtilization));
		}

		if (CONFIG.isRendererGpuDetails()) {
			sampleRendererDetails();
			addLine(lines, rendererLine);
			if (!driverLine.isEmpty()) {
				addLine(lines, driverLine);
			}
		}

		ReloadResult reload = lastReloadResult;
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
					CONFIG.isSceneComplexity() ? visibleBlockEntities : 0,
					CONFIG.isSceneComplexity() ? visibleDisplayEntities : 0,
					gc.timeDeltaMs,
					gpuUtilization,
					chunks.scheduledBuilds,
					CONFIG.isResourceReloadTiming() ? reload.durationMs : -1L
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
		BoundedLabelCounter counter = new BoundedLabelCounter(MAXIMUM_PARTICLE_LABELS);
		IdentityHashMap<Class<?>, String> labels = new IdentityHashMap<>(MAXIMUM_PARTICLE_LABELS);
		boolean truncated = false;
		try {
			Map<ParticleRenderType, ParticleGroup<?>> groups =
					((ParticleEngineAccessor) minecraft.particleEngine).sodiumVolt$getParticleGroups();
			particleGroups:
			for (ParticleGroup<?> group : groups.values()) {
				for (Particle particle : ((ParticleGroupAccessor) group).sodiumVolt$getParticles()) {
					if (counter.total() >= MAXIMUM_PARTICLES_INSPECTED) {
						truncated = true;
						break particleGroups;
					}
					Class<?> particleClass = particle.getClass();
					String label = labels.get(particleClass);
					if (label == null) {
						if (labels.size() < MAXIMUM_PARTICLE_LABELS) {
							label = particleLabel(particleClass);
							labels.put(particleClass, label);
						} else {
							label = "other implementations";
						}
					}
					counter.add(label);
				}
			}
		} catch (RuntimeException | LinkageError exception) {
			if (!particleFailureLogged) {
				particleFailureLogged = true;
				SodiumVolt.LOGGER.debug("Volt Inspector could not sample particle implementations", exception);
			}
		}
		return new ParticleSnapshot(counter.total(), truncated, counter.top(MAXIMUM_PARTICLE_LINES));
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
		int visibleLineCount = Math.min(lines.size(), Math.max(0, (graphics.guiHeight() - 8) / 10));
		int availableTextWidth = Math.max(0, graphics.guiWidth() - 16);
		if (visibleLineCount == 0 || availableTextWidth == 0) {
			return;
		}
		int width = 0;
		for (int index = 0; index < visibleLineCount; index++) {
			width = Math.max(width, Math.min(font.width(lines.get(index)), availableTextWidth));
		}
		int left = Math.max(4, graphics.guiWidth() - width - 12);
		int top = 4;
		int height = visibleLineCount * 10 + 8;
		graphics.fill(left - 4, top, graphics.guiWidth() - 4, top + height, BACKGROUND_COLOR);
		graphics.outline(left - 4, top, graphics.guiWidth() - 4, top + height, 0x8059DCEC);
		for (int index = 0; index < visibleLineCount; index++) {
			String line = lines.get(index);
			int color = index == 0 ? TITLE_COLOR : line.startsWith("  ") ? MUTED_COLOR : TEXT_COLOR;
			graphics.text(
					font,
					truncateToWidth(font, line, availableTextWidth),
					left,
					top + 4 + index * 10,
					color,
					true
			);
		}
	}

	private static String truncateToWidth(Font font, String value, int maximumWidth) {
		if (maximumWidth <= 0) {
			return "";
		}
		if (font.width(value) <= maximumWidth) {
			return value;
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

	private static void reset() {
		active = false;
		wasPaused = false;
		previousFrameNanos = 0L;
		nextRefreshNanos = 0L;
		previousLevelIdentity = 0;
		visibleEntities = 0;
		visibleBlockEntities = 0;
		visibleDisplayEntities = 0;
		visibleDisplaysTruncated = false;
		previousGcCount = -1L;
		previousGcTimeMs = -1L;
		FRAME_TIMES.clear();
		invalidateReloadResults();
		rendererLine = "Renderer unavailable";
		driverLine = "";
		hudLines = List.of("Volt Inspector", "Collecting samples...");
	}

	private static synchronized void invalidateReloadResults() {
		long generation = RELOAD_GENERATION.incrementAndGet();
		lastReloadResult = new ReloadResult(generation, -1L, false);
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

	public record ReloadObservation(long generation, long startNanos) {
		private static final ReloadObservation DISABLED = new ReloadObservation(0L, 0L);
	}

	private record ReloadResult(long generation, long durationMs, boolean failed) {
		private static final ReloadResult NONE = new ReloadResult(0L, -1L, false);
	}
}
