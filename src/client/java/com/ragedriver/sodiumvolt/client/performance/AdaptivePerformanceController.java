package com.ragedriver.sodiumvolt.client.performance;

import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.config.VoltPerformanceConfig;
import com.ragedriver.sodiumvolt.client.mixin.OptionInstanceAccessor;
import com.ragedriver.sodiumvolt.client.smartfps.SmartFpsEngine;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Util;

import java.util.Objects;

public final class AdaptivePerformanceController {
	private static final long NOTIFICATION_INTERVAL_NANOS = 10_000_000_000L;
	private static final int MINIMUM_EVALUATION_SAMPLES = 60;
	private static final ApcFrameWindow FRAME_TIMES =
			new ApcFrameWindow(VoltPerformanceConfig.SAMPLE_WINDOW_MAX);
	private static final long[] SORTING_BUFFER = new long[VoltPerformanceConfig.SAMPLE_WINDOW_MAX];
	private static final ApcControllerLogic CONTROLLER = new ApcControllerLogic();
	private static final VoltPerformanceConfig CONFIG = VoltPerformanceConfig.getInstance();

	private static boolean active;
	private static boolean runtimeFailed;
	private static boolean wrongThreadLogged;
	private static long previousFrameNanos;
	private static long nextEvaluationNanos;
	private static long lastNotificationNanos;
	private static ClientLevel previousLevel;
	private static int maximumLevel;
	private static VoltPerformanceConfig.Profile activeProfile;
	private static OptionSnapshot originalSnapshot;
	private static OptionSnapshot lastAppliedSnapshot;
	private static GraphicsPreset originalGraphicsPreset;
	private static GraphicsPreset lastAppliedGraphicsPreset;
	private static volatile boolean animationThrottleActive;

	private AdaptivePerformanceController() {
	}

	public static void register() {
		ClientLifecycleEvents.CLIENT_STOPPING.register(AdaptivePerformanceController::onClientStopping);
	}

	public static void onRenderFrame(Minecraft minecraft, long nowNanos) {
		if (runtimeFailed) {
			return;
		}
		if (Thread.currentThread() != minecraft.getRunningThread()) {
			if (!wrongThreadLogged) {
				wrongThreadLogged = true;
				SodiumVolt.LOGGER.warn("Volt APC ignored a render sample from a non-client thread");
			}
			return;
		}
		try {
			update(minecraft, nowNanos);
		} catch (RuntimeException | LinkageError exception) {
			runtimeFailed = true;
			animationThrottleActive = false;
			SodiumVolt.LOGGER.error("Volt APC failed safely and has stopped for this session", exception);
			restoreAfterFailure(minecraft);
		}
	}

	public static boolean shouldThrottleAtlasAnimations() {
		return animationThrottleActive;
	}

	public static boolean isActive() {
		return active;
	}

	public static void acceptVramOwnedRenderDistance(int value) {
		if (active && lastAppliedSnapshot != null) {
			lastAppliedSnapshot = lastAppliedSnapshot.withRenderDistance(value);
		}
	}

	public static void acceptRecoveryOwnedOptions(Options options) {
		if (active && lastAppliedSnapshot != null && options != null) {
			ApcOptionOwnership ownership = ownershipState();
			if (ownership != null) {
				storeOwnership(ownership.alignAfterOwnedMutation(
						OptionSnapshot.capture(options),
						options.graphicsPreset().get()
				));
			}
		}
	}

	public static void acceptProfileOwnedOptions(Options options) {
		acceptRecoveryOwnedOptions(options);
	}

	public static void prepareForRecoveryOwnedOptions(Options options) {
		if (active && lastAppliedSnapshot != null && options != null) {
			rebaseExternalChanges(options);
		}
	}

	public static void prepareForProfileOwnedOptions(Options options) {
		prepareForRecoveryOwnedOptions(options);
	}

	private static void update(Minecraft minecraft, long nowNanos) {
		if (!CONFIG.isAdaptivePerformanceControllerEnabled()) {
			if (active) {
				deactivate(minecraft, true);
			}
			return;
		}
		if (SmartFpsEngine.shouldSuspendApcSampling()
				|| com.ragedriver.sodiumvolt.client.recovery.VoltRecoveryEngine
						.shouldSuspendApcSampling()) {
			animationThrottleActive = false;
			resetFrameSampling();
			return;
		}

		boolean validWorldFrame = minecraft.level != null
				&& minecraft.player != null
				&& !minecraft.isPaused()
				&& minecraft.gui.screen() == null
				&& minecraft.gui.overlay() == null
				&& minecraft.isGameLoadFinished();
		if (!active) {
			if (!validWorldFrame) {
				resetFrameSampling();
				return;
			}
			activate(minecraft, CONFIG.getProfile(), nowNanos);
		} else if (activeProfile != CONFIG.getProfile()) {
			if (!validWorldFrame) {
				animationThrottleActive = false;
				resetFrameSampling();
				return;
			}
			transitionProfile(minecraft, CONFIG.getProfile(), nowNanos);
		}

		if (!validWorldFrame) {
			animationThrottleActive = false;
			resetFrameSampling();
			return;
		}

		if (minecraft.level != previousLevel) {
			FRAME_TIMES.clear();
			previousFrameNanos = 0L;
			previousLevel = minecraft.level;
		}
		if (previousFrameNanos != 0L) {
			FRAME_TIMES.addNanos(nowNanos - previousFrameNanos);
		}
		previousFrameNanos = nowNanos;
		updateAnimationThrottle();

		if (nowNanos < nextEvaluationNanos) {
			return;
		}
		nextEvaluationNanos = saturatedAdd(
				nowNanos,
				CONFIG.getAdjustmentIntervalSeconds() * 1_000_000_000L
		);
		evaluate(minecraft, nowNanos);
	}

	private static void activate(
			Minecraft minecraft,
			VoltPerformanceConfig.Profile profile,
			long nowNanos
	) {
		originalSnapshot = OptionSnapshot.capture(minecraft.options);
		originalGraphicsPreset = minecraft.options.graphicsPreset().get();
		activeProfile = profile;
		OptionSnapshot base = profileBase(originalSnapshot, profile);
		maximumLevel = maximumLevel(base);
		int initialLevel = ApcQualityPlan.initialLevel(policy(profile), maximumLevel);
		CONTROLLER.reset(initialLevel, nowNanos);
		applyLevel(minecraft.options, base, initialLevel);
		active = true;
		previousLevel = minecraft.level;
		FRAME_TIMES.clear();
		previousFrameNanos = 0L;
		nextEvaluationNanos = saturatedAdd(
				nowNanos,
				CONFIG.getAdjustmentIntervalSeconds() * 1_000_000_000L
		);
		updateAnimationThrottle();
		notify(
				minecraft,
				Component.translatable(
						"sodium-volt.notification.apc.profile",
						Component.translatable(profileTranslationKey(profile))
				),
				nowNanos
		);
	}

	private static void transitionProfile(
			Minecraft minecraft,
			VoltPerformanceConfig.Profile profile,
			long nowNanos
	) {
		rebaseExternalChanges(minecraft.options);
		if (originalSnapshot != null) {
			restoreOriginalOptions(minecraft.options);
		}
		active = false;
		animationThrottleActive = false;
		activate(minecraft, profile, nowNanos);
	}

	private static void evaluate(Minecraft minecraft, long nowNanos) {
		int sampleWindow = CONFIG.getSampleWindow();
		int availableSamples = FRAME_TIMES.size(sampleWindow);
		if (availableSamples < Math.min(MINIMUM_EVALUATION_SAMPLES, sampleWindow / 2)) {
			return;
		}

		boolean externalChange = rebaseExternalChanges(minecraft.options);
		OptionSnapshot base = profileBase(originalSnapshot, activeProfile);
		int recalculatedMaximum = maximumLevel(base);
		maximumLevel = recalculatedMaximum;
		int effectiveTarget = effectiveTarget(minecraft);
		double p95Milliseconds = FRAME_TIMES.p95Milliseconds(sampleWindow, SORTING_BUFFER);
		ApcControllerLogic.Decision decision = CONTROLLER.evaluate(
				p95Milliseconds,
				effectiveTarget,
				CONFIG.getFpsTolerance(),
				ApcQualityPlan.recoveryFloor(policy(activeProfile), maximumLevel),
				maximumLevel,
				CONFIG.getQualityRecoveryDelaySeconds() * 1_000_000_000L,
				nowNanos
		);
		if (decision.action() != ApcControllerLogic.Action.HOLD || externalChange) {
			applyLevel(minecraft.options, base, decision.level());
		}
		updateAnimationThrottle();
		if (decision.action() == ApcControllerLogic.Action.DOWNSHIFT) {
			notify(
					minecraft,
					Component.translatable(
							"sodium-volt.notification.apc.downshift",
							effectiveTarget == ApcControllerLogic.UNLIMITED_TARGET
									? Component.translatable("sodium-volt.options.performance.target_fps.max")
									: effectiveTarget
					),
					nowNanos
			);
		} else if (decision.action() == ApcControllerLogic.Action.RECOVER) {
			notify(
					minecraft,
					Component.translatable("sodium-volt.notification.apc.recovery"),
					nowNanos
			);
		}
	}

	private static int effectiveTarget(Minecraft minecraft) {
		Options options = minecraft.options;
		return ApcControllerLogic.effectiveTargetFps(
				CONFIG.getTargetFps(),
				options.framerateLimit().get(),
				options.enableVsync().get(),
				minecraft.getWindow().getRefreshRate()
		);
	}

	private static void applyLevel(Options options, OptionSnapshot base, int level) {
		ApcQualityPlan.Stages stages = ApcQualityPlan.stages(
				level,
				base.renderDistance,
				Math.min(CONFIG.getMinimumRenderDistance(), base.renderDistance),
				CONFIG.isAdaptiveParticleQuality(),
				CONFIG.isAdaptiveEntityDistance(),
				CONFIG.isAdaptiveRenderDistance(),
				CONFIG.isAdaptiveVisualEffects()
		);
		OptionSnapshot desired = desiredSnapshot(originalSnapshot, base, stages);
		int vramCap = VramPressureProtectionEngine.currentMaximumRenderDistanceCap();
		if (vramCap != Integer.MAX_VALUE && desired.renderDistance > vramCap) {
			desired = desired.withRenderDistance(vramCap);
		}
		desired.apply(options);
		lastAppliedSnapshot = OptionSnapshot.capture(options);
		lastAppliedGraphicsPreset = options.graphicsPreset().get();
	}

	private static OptionSnapshot desiredSnapshot(
			OptionSnapshot original,
			OptionSnapshot base,
			ApcQualityPlan.Stages stages
	) {
		int renderDistance = CONFIG.isAdaptiveRenderDistance()
				? stages.renderDistance()
				: original.renderDistance;
		double entityDistance = CONFIG.isAdaptiveEntityDistance()
				? switch (stages.entityStage()) {
					case 0 -> base.entityDistance;
					case 1 -> Math.max(0.5D, Math.min(base.entityDistance, 0.75D));
					default -> Math.max(0.5D, Math.min(base.entityDistance, 0.5D));
				}
				: original.entityDistance;
		ParticleStatus particles = CONFIG.isAdaptiveParticleQuality()
				? degradeParticles(base.particles, stages.particleStage())
				: original.particles;

		if (!CONFIG.isAdaptiveVisualEffects()) {
			return new OptionSnapshot(
					renderDistance,
					entityDistance,
					particles,
					original.clouds,
					original.cloudRange,
					original.weatherRadius,
					original.cutoutLeaves,
					original.vignette,
					original.improvedTransparency,
					original.ambientOcclusion,
					original.chunkSectionFade,
					original.prioritizeChunkUpdates,
					original.entityShadows,
					original.biomeBlendRadius
			);
		}
		return visualStage(base, renderDistance, entityDistance, particles, stages.visualStage());
	}

	private static OptionSnapshot visualStage(
			OptionSnapshot base,
			int renderDistance,
			double entityDistance,
			ParticleStatus particles,
			int stage
	) {
		if (stage <= 0) {
			return base.withCore(renderDistance, entityDistance, particles);
		}
		if (stage == 1) {
			return new OptionSnapshot(
					renderDistance,
					entityDistance,
					particles,
					base.clouds,
					Math.min(base.cloudRange, 64),
					Math.min(base.weatherRadius, 10),
					base.cutoutLeaves,
					base.vignette,
					base.improvedTransparency,
					base.ambientOcclusion,
					Math.min(base.chunkSectionFade, 0.5D),
					base.prioritizeChunkUpdates,
					base.entityShadows,
					Math.min(base.biomeBlendRadius, 2)
			);
		}
		if (stage == 2) {
			return new OptionSnapshot(
					renderDistance,
					entityDistance,
					particles,
					base.clouds == CloudStatus.OFF ? CloudStatus.OFF : CloudStatus.FAST,
					Math.min(base.cloudRange, 32),
					Math.min(base.weatherRadius, 5),
					false,
					base.vignette,
					false,
					base.ambientOcclusion,
					0.0D,
					PrioritizeChunkUpdates.NONE,
					false,
					Math.min(base.biomeBlendRadius, 1)
			);
		}
		return new OptionSnapshot(
				renderDistance,
				entityDistance,
				particles,
				CloudStatus.OFF,
				32,
				5,
				false,
				false,
				false,
				false,
				0.0D,
				PrioritizeChunkUpdates.NONE,
				false,
				0
		);
	}

	private static ParticleStatus degradeParticles(ParticleStatus base, int stage) {
		if (stage <= 0) {
			return base;
		}
		if (stage == 1 && base == ParticleStatus.ALL) {
			return ParticleStatus.DECREASED;
		}
		return ParticleStatus.MINIMAL;
	}

	private static OptionSnapshot profileBase(
			OptionSnapshot original,
			VoltPerformanceConfig.Profile profile
	) {
		if (profile == VoltPerformanceConfig.Profile.BALANCED) {
			return original.withRenderDistance(Math.min(
					original.renderDistance,
					CONFIG.getMaximumRenderDistance()
			));
		}
		return new OptionSnapshot(
				CONFIG.getMaximumRenderDistance(),
				1.25D,
				ParticleStatus.ALL,
				CloudStatus.FANCY,
				128,
				10,
				true,
				true,
				Util.getPlatform() != Util.OS.OSX,
				true,
				1.0D,
				PrioritizeChunkUpdates.PLAYER_AFFECTED,
				true,
				7
		);
	}

	private static int maximumLevel(OptionSnapshot base) {
		return ApcQualityPlan.maximumLevel(
				base.renderDistance,
				Math.min(CONFIG.getMinimumRenderDistance(), base.renderDistance),
				CONFIG.isAdaptiveParticleQuality(),
				CONFIG.isAdaptiveEntityDistance(),
				CONFIG.isAdaptiveRenderDistance(),
				CONFIG.isAdaptiveVisualEffects()
		);
	}

	private static ApcQualityPlan.ProfilePolicy policy(VoltPerformanceConfig.Profile profile) {
		return switch (profile) {
			case BALANCED -> ApcQualityPlan.ProfilePolicy.BALANCED;
			case MAX_QUALITY -> ApcQualityPlan.ProfilePolicy.MAX_QUALITY;
			case MAX_PERFORMANCE -> ApcQualityPlan.ProfilePolicy.MAX_PERFORMANCE;
		};
	}

	private static String profileTranslationKey(VoltPerformanceConfig.Profile profile) {
		return "sodium-volt.options.performance.profile." + profile.name().toLowerCase(java.util.Locale.ROOT);
	}

	private static boolean rebaseExternalChanges(Options options) {
		ApcOptionOwnership ownership = ownershipState();
		if (ownership == null) {
			return false;
		}
		OptionSnapshot actual = OptionSnapshot.capture(options);
		GraphicsPreset actualGraphicsPreset = options.graphicsPreset().get();
		ApcOptionOwnership prepared = ownership.prepareForOwnedMutation(
				actual,
				actualGraphicsPreset
		);
		if (prepared == ownership) {
			return false;
		}
		storeOwnership(prepared);
		return true;
	}

	private static ApcOptionOwnership ownershipState() {
		if (originalSnapshot == null || lastAppliedSnapshot == null
				|| originalGraphicsPreset == null || lastAppliedGraphicsPreset == null) {
			return null;
		}
		return new ApcOptionOwnership(
				originalSnapshot,
				lastAppliedSnapshot,
				originalGraphicsPreset,
				lastAppliedGraphicsPreset
		);
	}

	private static void storeOwnership(ApcOptionOwnership ownership) {
		originalSnapshot = ownership.original();
		lastAppliedSnapshot = ownership.lastApplied();
		originalGraphicsPreset = ownership.originalGraphicsPreset();
		lastAppliedGraphicsPreset = ownership.lastAppliedGraphicsPreset();
	}

	private static void updateAnimationThrottle() {
		animationThrottleActive = active
				&& CONFIG.isAdaptiveAnimationThrottling()
				&& ApcQualityPlan.shouldThrottleAnimations(
						maximumLevel,
						CONTROLLER.currentLevel()
				);
	}

	private static void deactivate(Minecraft minecraft, boolean saveRestoration) {
		animationThrottleActive = false;
		if (CONFIG.isRestoreOriginalSettings() && originalSnapshot != null) {
			rebaseExternalChanges(minecraft.options);
			restoreOriginalOptions(minecraft.options);
			if (saveRestoration) {
				minecraft.options.save();
			}
		}
		active = false;
		activeProfile = null;
		originalSnapshot = null;
		lastAppliedSnapshot = null;
		originalGraphicsPreset = null;
		lastAppliedGraphicsPreset = null;
		maximumLevel = 0;
		resetFrameSampling();
	}

	private static void onClientStopping(Minecraft minecraft) {
		if (!active) {
			return;
		}
		try {
			deactivate(minecraft, true);
		} catch (RuntimeException exception) {
			animationThrottleActive = false;
			SodiumVolt.LOGGER.error("Volt APC could not restore graphics settings while the client stopped", exception);
		}
	}

	private static void restoreAfterFailure(Minecraft minecraft) {
		try {
			if (active && CONFIG.isRestoreOriginalSettings() && originalSnapshot != null) {
				restoreOriginalOptions(minecraft.options);
				minecraft.options.save();
			}
		} catch (RuntimeException restoreException) {
			SodiumVolt.LOGGER.error("Volt APC could not restore graphics settings after its failure", restoreException);
		} finally {
			active = false;
			originalSnapshot = null;
			lastAppliedSnapshot = null;
			originalGraphicsPreset = null;
			lastAppliedGraphicsPreset = null;
		}
	}

	private static void resetFrameSampling() {
		previousFrameNanos = 0L;
		previousLevel = null;
		FRAME_TIMES.clear();
	}

	@SuppressWarnings("unchecked")
	private static void restoreOriginalOptions(Options options) {
		originalSnapshot.apply(options);
		if (originalGraphicsPreset != null) {
			((OptionInstanceAccessor<GraphicsPreset>) (Object) options.graphicsPreset())
					.sodiumVolt$setValueWithoutCallback(originalGraphicsPreset);
		}
		lastAppliedSnapshot = OptionSnapshot.capture(options);
		lastAppliedGraphicsPreset = options.graphicsPreset().get();
	}

	private static void notify(Minecraft minecraft, Component message, long nowNanos) {
		if (!CONFIG.isShowControllerNotifications()
				|| minecraft.level == null
				|| (lastNotificationNanos != 0L
						&& nowNanos - lastNotificationNanos < NOTIFICATION_INTERVAL_NANOS)) {
			return;
		}
		lastNotificationNanos = nowNanos;
		minecraft.gui.hud.setOverlayMessage(message, false);
	}

	private static long saturatedAdd(long left, long right) {
		if (right > 0L && left > Long.MAX_VALUE - right) {
			return Long.MAX_VALUE;
		}
		return left + right;
	}

	record OptionSnapshot(
			int renderDistance,
			double entityDistance,
			ParticleStatus particles,
			CloudStatus clouds,
			int cloudRange,
			int weatherRadius,
			boolean cutoutLeaves,
			boolean vignette,
			boolean improvedTransparency,
			boolean ambientOcclusion,
			double chunkSectionFade,
			PrioritizeChunkUpdates prioritizeChunkUpdates,
			boolean entityShadows,
			int biomeBlendRadius
	) {
		static OptionSnapshot capture(Options options) {
			return new OptionSnapshot(
					options.renderDistance().get(),
					options.entityDistanceScaling().get(),
					options.particles().get(),
					options.cloudStatus().get(),
					options.cloudRange().get(),
					options.weatherRadius().get(),
					options.cutoutLeaves().get(),
					options.vignette().get(),
					options.improvedTransparency().get(),
					options.ambientOcclusion().get(),
					options.chunkSectionFadeInTime().get(),
					options.prioritizeChunkUpdates().get(),
					options.entityShadows().get(),
					options.biomeBlendRadius().get()
			);
		}

		void apply(Options options) {
			setIfDifferent(options.renderDistance(), this.renderDistance);
			setIfDifferent(options.entityDistanceScaling(), this.entityDistance);
			setIfDifferent(options.particles(), this.particles);
			setIfDifferent(options.cloudStatus(), this.clouds);
			setIfDifferent(options.cloudRange(), this.cloudRange);
			setIfDifferent(options.weatherRadius(), this.weatherRadius);
			setIfDifferent(options.cutoutLeaves(), this.cutoutLeaves);
			setIfDifferent(options.vignette(), this.vignette);
			setIfDifferent(options.improvedTransparency(), this.improvedTransparency);
			setIfDifferent(options.ambientOcclusion(), this.ambientOcclusion);
			setIfDifferent(options.chunkSectionFadeInTime(), this.chunkSectionFade);
			setIfDifferent(options.prioritizeChunkUpdates(), this.prioritizeChunkUpdates);
			setIfDifferent(options.entityShadows(), this.entityShadows);
			setIfDifferent(options.biomeBlendRadius(), this.biomeBlendRadius);
		}

		OptionSnapshot withRenderDistance(int value) {
			return new OptionSnapshot(
					value, this.entityDistance, this.particles, this.clouds, this.cloudRange,
					this.weatherRadius, this.cutoutLeaves, this.vignette, this.improvedTransparency,
					this.ambientOcclusion, this.chunkSectionFade, this.prioritizeChunkUpdates,
					this.entityShadows, this.biomeBlendRadius
			);
		}

		OptionSnapshot withCore(int render, double entity, ParticleStatus particleStatus) {
			return new OptionSnapshot(
					render, entity, particleStatus, this.clouds, this.cloudRange, this.weatherRadius,
					this.cutoutLeaves, this.vignette, this.improvedTransparency, this.ambientOcclusion,
					this.chunkSectionFade, this.prioritizeChunkUpdates, this.entityShadows,
					this.biomeBlendRadius
			);
		}

		OptionSnapshot rebase(OptionSnapshot actual, OptionSnapshot expected) {
			return new OptionSnapshot(
					changed(actual.renderDistance, expected.renderDistance)
							? actual.renderDistance : this.renderDistance,
					changed(actual.entityDistance, expected.entityDistance)
							? actual.entityDistance : this.entityDistance,
					changed(actual.particles, expected.particles) ? actual.particles : this.particles,
					changed(actual.clouds, expected.clouds) ? actual.clouds : this.clouds,
					changed(actual.cloudRange, expected.cloudRange) ? actual.cloudRange : this.cloudRange,
					changed(actual.weatherRadius, expected.weatherRadius)
							? actual.weatherRadius : this.weatherRadius,
					changed(actual.cutoutLeaves, expected.cutoutLeaves)
							? actual.cutoutLeaves : this.cutoutLeaves,
					changed(actual.vignette, expected.vignette) ? actual.vignette : this.vignette,
					changed(actual.improvedTransparency, expected.improvedTransparency)
							? actual.improvedTransparency : this.improvedTransparency,
					changed(actual.ambientOcclusion, expected.ambientOcclusion)
							? actual.ambientOcclusion : this.ambientOcclusion,
					changed(actual.chunkSectionFade, expected.chunkSectionFade)
							? actual.chunkSectionFade : this.chunkSectionFade,
					changed(actual.prioritizeChunkUpdates, expected.prioritizeChunkUpdates)
							? actual.prioritizeChunkUpdates : this.prioritizeChunkUpdates,
					changed(actual.entityShadows, expected.entityShadows)
							? actual.entityShadows : this.entityShadows,
					changed(actual.biomeBlendRadius, expected.biomeBlendRadius)
							? actual.biomeBlendRadius : this.biomeBlendRadius
			);
		}

		private static boolean changed(Object actual, Object expected) {
			return !Objects.equals(actual, expected);
		}

		private static boolean changed(double actual, double expected) {
			return Double.compare(actual, expected) != 0;
		}

		private static boolean changed(int actual, int expected) {
			return actual != expected;
		}

		private static boolean changed(boolean actual, boolean expected) {
			return actual != expected;
		}

		private static <T> void setIfDifferent(net.minecraft.client.OptionInstance<T> option, T value) {
			if (!Objects.equals(option.get(), value)) {
				option.set(value);
			}
		}
	}
}
