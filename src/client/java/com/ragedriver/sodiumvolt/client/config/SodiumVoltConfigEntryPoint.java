package com.ragedriver.sodiumvolt.client.config;

import com.ragedriver.sodiumvolt.client.profile.FactoryResetDecision;
import com.ragedriver.sodiumvolt.client.profile.PerformanceProfileEngine;
import com.ragedriver.sodiumvolt.client.profile.ProfileParticleMode;
import com.ragedriver.sodiumvolt.client.profile.ProfileSettings;
import com.ragedriver.sodiumvolt.client.profile.VoltFactoryReset;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.EnumOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ExternalButtonOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.IntegerOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SodiumVoltConfigEntryPoint implements ConfigEntryPoint {
	private static final Identifier ICON = Identifier.parse("sodium-volt:icon.png");
	private static final Identifier VOLT_GUARD_ENABLED = optionId("volt_guard_enabled");
	private static final Identifier ADAPTIVE_WORKLOAD_CONTROL = optionId("adaptive_workload_control");
	private static final Identifier PRIORITIZE_VISIBLE_EFFECTS = optionId("prioritize_visible_effects");
	private static final Identifier PRESERVE_GAMEPLAY_CRITICAL_EFFECTS =
			optionId("preserve_gameplay_critical_effects");
	private static final Identifier SHOW_PROTECTION_NOTIFICATIONS = optionId("show_protection_notifications");
	private static final Identifier TARGET_FPS = optionId("target_fps");
	private static final Identifier PARTICLE_RENDER_BUDGET = optionId("particle_render_budget");
	private static final Identifier BLOCK_ENTITY_RENDER_BUDGET = optionId("block_entity_render_budget");
	private static final Identifier DISPLAY_ENTITY_RENDER_BUDGET = optionId("display_entity_render_budget");
	private static final Identifier VOLT_INSPECTOR_ENABLED = optionId("volt_inspector_enabled");
	private static final Identifier SHOW_INSPECTOR_OVERLAY = optionId("show_inspector_overlay");
	private static final Identifier FRAME_TIME_STATISTICS = optionId("frame_time_statistics");
	private static final Identifier CHUNK_ACTIVITY = optionId("chunk_activity");
	private static final Identifier SCENE_COMPLEXITY = optionId("scene_complexity");
	private static final Identifier PARTICLE_BREAKDOWN = optionId("particle_breakdown");
	private static final Identifier ANIMATED_TEXTURE_COUNT = optionId("animated_texture_count");
	private static final Identifier GC_PAUSE_MONITOR = optionId("gc_pause_monitor");
	private static final Identifier BOTTLENECK_ESTIMATE = optionId("bottleneck_estimate");
	private static final Identifier RENDERER_GPU_DETAILS = optionId("renderer_gpu_details");
	private static final Identifier RESOURCE_RELOAD_TIMING = optionId("resource_reload_timing");
	private static final Identifier SMART_RECOMMENDATIONS = optionId("smart_recommendations");
	private static final Identifier INSPECTOR_REFRESH_INTERVAL = optionId("inspector_refresh_interval");
	private static final Identifier FRAME_SAMPLE_WINDOW = optionId("frame_sample_window");
	private static final Identifier SPIKE_THRESHOLD = optionId("spike_threshold");
	private static final Identifier APC_ENABLED = optionId("apc_enabled");
	private static final Identifier APC_PROFILE = optionId("apc_profile");
	private static final Identifier APC_TARGET_FPS = optionId("apc_target_fps");
	private static final Identifier APC_RENDER_DISTANCE = optionId("apc_render_distance");
	private static final Identifier APC_ENTITY_DISTANCE = optionId("apc_entity_distance");
	private static final Identifier APC_PARTICLE_QUALITY = optionId("apc_particle_quality");
	private static final Identifier APC_VISUAL_EFFECTS = optionId("apc_visual_effects");
	private static final Identifier APC_ANIMATION_THROTTLING = optionId("apc_animation_throttling");
	private static final Identifier APC_RESTORE_SETTINGS = optionId("apc_restore_settings");
	private static final Identifier APC_NOTIFICATIONS = optionId("apc_notifications");
	private static final Identifier APC_MIN_RENDER_DISTANCE = optionId("apc_min_render_distance");
	private static final Identifier APC_MAX_RENDER_DISTANCE = optionId("apc_max_render_distance");
	private static final Identifier APC_ADJUSTMENT_INTERVAL = optionId("apc_adjustment_interval");
	private static final Identifier APC_RECOVERY_DELAY = optionId("apc_recovery_delay");
	private static final Identifier APC_FPS_TOLERANCE = optionId("apc_fps_tolerance");
	private static final Identifier APC_SAMPLE_WINDOW = optionId("apc_sample_window");
	private static final Identifier VAPS_ENABLED = optionId("vaps_enabled");
	private static final Identifier VAPS_PRIORITIZE_IN_FRUSTUM = optionId("vaps_prioritize_in_frustum");
	private static final Identifier VAPS_SKIP_BEHIND_CAMERA = optionId("vaps_skip_behind_camera");
	private static final Identifier VAPS_DISTANCE_AWARE_SIMULATION =
			optionId("vaps_distance_aware_simulation");
	private static final Identifier VAPS_PRESERVE_CRITICAL = optionId("vaps_preserve_critical");
	private static final Identifier VAPS_COALESCE_AMBIENT = optionId("vaps_coalesce_ambient");
	private static final Identifier VAPS_PER_TYPE_LIMITS = optionId("vaps_per_type_limits");
	private static final Identifier VAPS_INSPECTOR_STATS = optionId("vaps_inspector_stats");
	private static final Identifier VAPS_FULL_RATE_DISTANCE = optionId("vaps_full_rate_distance");
	private static final Identifier VAPS_FAR_TICK_INTERVAL = optionId("vaps_far_tick_interval");
	private static final Identifier VAPS_PER_TYPE_LIMIT = optionId("vaps_per_type_limit");
	private static final Identifier VAPS_AMBIENT_PER_CELL = optionId("vaps_ambient_per_cell");
	private static final Identifier VAPS_CRITICAL_RESERVE = optionId("vaps_critical_reserve");
	private static final Identifier BERP_ENABLED = optionId("berp_enabled");
	private static final Identifier BERP_PRIORITIZE_NEARBY = optionId("berp_prioritize_nearby");
	private static final Identifier BERP_RECENT_INTERACTION = optionId("berp_recent_interaction");
	private static final Identifier BERP_DISTANCE_AWARE_UPDATES = optionId("berp_distance_aware_updates");
	private static final Identifier BERP_CACHE_FAR_STATES = optionId("berp_cache_far_states");
	private static final Identifier BERP_PER_TYPE_LIMITS = optionId("berp_per_type_limits");
	private static final Identifier BERP_CULL_BEYOND_FAR = optionId("berp_cull_beyond_far");
	private static final Identifier BERP_INCLUDE_MODDED = optionId("berp_include_modded");
	private static final Identifier BERP_INSPECTOR_STATS = optionId("berp_inspector_stats");
	private static final Identifier BERP_NEAR_DISTANCE = optionId("berp_near_distance");
	private static final Identifier BERP_MEDIUM_DISTANCE = optionId("berp_medium_distance");
	private static final Identifier BERP_MEDIUM_INTERVAL = optionId("berp_medium_interval");
	private static final Identifier BERP_FAR_INTERVAL = optionId("berp_far_interval");
	private static final Identifier BERP_FAR_DISTANCE = optionId("berp_far_distance");
	private static final Identifier BERP_GLOBAL_BUDGET = optionId("berp_global_budget");
	private static final Identifier BERP_PER_TYPE_LIMIT = optionId("berp_per_type_limit");
	private static final Identifier BERP_GRACE_SECONDS = optionId("berp_grace_seconds");
	private static final Identifier BERP_CACHE_CAPACITY = optionId("berp_cache_capacity");
	private static final Identifier ATT_ENABLED = optionId("att_enabled");
	private static final Identifier ATT_PAUSE_INVISIBLE = optionId("att_pause_invisible");
	private static final Identifier ATT_DISTANCE_AWARE = optionId("att_distance_aware");
	private static final Identifier ATT_INTERFACE_FULL_SPEED = optionId("att_interface_full_speed");
	private static final Identifier ATT_CRITICAL_VANILLA = optionId("att_critical_vanilla");
	private static final Identifier ATT_HONOR_EXEMPTIONS = optionId("att_honor_exemptions");
	private static final Identifier ATT_IMMEDIATE_RESUME = optionId("att_immediate_resume");
	private static final Identifier ATT_INSPECTOR_STATS = optionId("att_inspector_stats");
	private static final Identifier ATT_FULL_SPEED_DISTANCE = optionId("att_full_speed_distance");
	private static final Identifier ATT_DISTANT_INTERVAL = optionId("att_distant_interval");
	private static final Identifier ATT_UNSEEN_KEEPALIVE = optionId("att_unseen_keepalive");
	private static final Identifier ATT_PER_ATLAS_BUDGET = optionId("att_per_atlas_budget");
	private static final Identifier VRAM_ENABLED = optionId("vram_enabled");
	private static final Identifier VRAM_AUTO_BUDGET = optionId("vram_auto_budget");
	private static final Identifier VRAM_SAFE_PROFILE = optionId("vram_safe_profile");
	private static final Identifier VRAM_SPIKE_RESPONSE = optionId("vram_spike_response");
	private static final Identifier VRAM_RESTORE_QUALITY = optionId("vram_restore_quality");
	private static final Identifier VRAM_WARNINGS = optionId("vram_warnings");
	private static final Identifier VRAM_HEADROOM = optionId("vram_headroom");
	private static final Identifier VRAM_INSPECTOR_STATS = optionId("vram_inspector_stats");
	private static final Identifier VRAM_MANUAL_BUDGET = optionId("vram_manual_budget");
	private static final Identifier VRAM_PROTECTION_THRESHOLD = optionId("vram_protection_threshold");
	private static final Identifier VRAM_CRITICAL_THRESHOLD = optionId("vram_critical_threshold");
	private static final Identifier VRAM_HEADROOM_PERCENT = optionId("vram_headroom_percent");
	private static final Identifier VRAM_FIXED_RESERVE = optionId("vram_fixed_reserve");
	private static final Identifier VRAM_MIN_RENDER_DISTANCE = optionId("vram_min_render_distance");
	private static final Identifier VRAM_SAMPLE_INTERVAL = optionId("vram_sample_interval");
	private static final Identifier VRAM_SUSTAINED_SAMPLES = optionId("vram_sustained_samples");
	private static final Identifier VRAM_STEP_INTERVAL = optionId("vram_step_interval");
	private static final Identifier VRAM_RECOVERY_DELAY = optionId("vram_recovery_delay");
	private static final Identifier VRAM_SPIKE_MIB = optionId("vram_spike_mib");
	private static final Identifier SMART_FPS_ENABLED = optionId("smart_fps_enabled");
	private static final Identifier SMART_FPS_MINIMIZED_TARGET = optionId("smart_fps_minimized_target");
	private static final Identifier SMART_FPS_THROTTLE_MINIMIZED = optionId("smart_fps_throttle_minimized");
	private static final Identifier SMART_FPS_THROTTLE_UNFOCUSED = optionId("smart_fps_throttle_unfocused");
	private static final Identifier SMART_FPS_UNFOCUSED_TARGET = optionId("smart_fps_unfocused_target");
	private static final Identifier SMART_FPS_BACKGROUND_DELAY = optionId("smart_fps_background_delay");
	private static final Identifier SMART_FPS_BATTERY_MODE = optionId("smart_fps_battery_mode");
	private static final Identifier SMART_FPS_BATTERY_TARGET = optionId("smart_fps_battery_target");
	private static final Identifier SMART_FPS_BYPASS_CHARGING = optionId("smart_fps_bypass_charging");
	private static final Identifier SMART_FPS_LOW_BATTERY = optionId("smart_fps_low_battery");
	private static final Identifier SMART_FPS_LOW_BATTERY_THRESHOLD =
			optionId("smart_fps_low_battery_threshold");
	private static final Identifier SMART_FPS_LOW_BATTERY_TARGET = optionId("smart_fps_low_battery_target");
	private static final Identifier SMART_FPS_POWER_POLL_INTERVAL = optionId("smart_fps_power_poll_interval");
	private static final Identifier SMART_FPS_NOTIFICATIONS = optionId("smart_fps_notifications");
	private static final Identifier SMART_FPS_INSPECTOR_STATS = optionId("smart_fps_inspector_stats");
	private static final Identifier RECOVERY_ENABLED = optionId("recovery_enabled");
	private static final Identifier RECOVERY_DETECT_UNCLEAN = optionId("recovery_detect_unclean");
	private static final Identifier RECOVERY_AUTOMATIC = optionId("recovery_automatic");
	private static final Identifier RECOVERY_FORCE_NEXT = optionId("recovery_force_next");
	private static final Identifier RECOVERY_STREAK_THRESHOLD = optionId("recovery_streak_threshold");
	private static final Identifier RECOVERY_MAXIMUM_ATTEMPTS = optionId("recovery_maximum_attempts");
	private static final Identifier RECOVERY_SAFE_PROFILE = optionId("recovery_safe_profile");
	private static final Identifier RECOVERY_RENDER_DISTANCE = optionId("recovery_render_distance");
	private static final Identifier RECOVERY_ENTITY_DISTANCE = optionId("recovery_entity_distance");
	private static final Identifier RECOVERY_REDUCE_GRAPHICS = optionId("recovery_reduce_graphics");
	private static final Identifier RECOVERY_LIMIT_FPS = optionId("recovery_limit_fps");
	private static final Identifier RECOVERY_FPS_CAP = optionId("recovery_fps_cap");
	private static final Identifier RECOVERY_SUSPEND_APC = optionId("recovery_suspend_apc");
	private static final Identifier RECOVERY_RESTORE_STABLE = optionId("recovery_restore_stable");
	private static final Identifier RECOVERY_STABLE_DURATION = optionId("recovery_stable_duration");
	private static final Identifier RECOVERY_NOTIFICATIONS = optionId("recovery_notifications");
	private static final Identifier RECOVERY_REPORT = optionId("recovery_report");
	private static final Identifier RECOVERY_INSPECTOR_STATS = optionId("recovery_inspector_stats");
	private static final Identifier WATCHDOG_ENABLED = optionId("gpu_watchdog_enabled");
	private static final Identifier WATCHDOG_WARNING_THRESHOLD = optionId("gpu_watchdog_warning_threshold");
	private static final Identifier WATCHDOG_CRITICAL_THRESHOLD = optionId("gpu_watchdog_critical_threshold");
	private static final Identifier WATCHDOG_CONFIRMATIONS = optionId("gpu_watchdog_confirmations");
	private static final Identifier WATCHDOG_STARTUP_GRACE = optionId("gpu_watchdog_startup_grace");
	private static final Identifier WATCHDOG_RELOAD_GRACE = optionId("gpu_watchdog_reload_grace");
	private static final Identifier WATCHDOG_IGNORE_PAUSED = optionId("gpu_watchdog_ignore_paused");
	private static final Identifier WATCHDOG_IGNORE_UNFOCUSED = optionId("gpu_watchdog_ignore_unfocused");
	private static final Identifier WATCHDOG_SAMPLE_INTERVAL = optionId("gpu_watchdog_sample_interval");
	private static final Identifier WATCHDOG_COOLDOWN = optionId("gpu_watchdog_cooldown");
	private static final Identifier WATCHDOG_MAXIMUM_INCIDENTS = optionId("gpu_watchdog_maximum_incidents");
	private static final Identifier WATCHDOG_ARM_RECOVERY = optionId("gpu_watchdog_arm_recovery");
	private static final Identifier WATCHDOG_NOTIFICATIONS = optionId("gpu_watchdog_notifications");
	private static final Identifier WATCHDOG_REPORT = optionId("gpu_watchdog_report");
	private static final Identifier WATCHDOG_INSPECTOR_STATS = optionId("gpu_watchdog_inspector_stats");
	private static final Identifier SHIELD_ENABLED = optionId("resource_pack_shield_enabled");
	private static final Identifier SHIELD_LOCAL_PACKS = optionId("resource_pack_shield_local_packs");
	private static final Identifier SHIELD_SERVER_PACKS = optionId("resource_pack_shield_server_packs");
	private static final Identifier SHIELD_UNSAFE_PATHS = optionId("resource_pack_shield_unsafe_paths");
	private static final Identifier SHIELD_CORE_SHADERS = optionId("resource_pack_shield_core_shaders");
	private static final Identifier SHIELD_REJECT = optionId("resource_pack_shield_reject");
	private static final Identifier SHIELD_ENTRY_LIMIT = optionId("resource_pack_shield_entry_limit");
	private static final Identifier SHIELD_ARCHIVE_SIZE = optionId("resource_pack_shield_archive_size");
	private static final Identifier SHIELD_SINGLE_SIZE = optionId("resource_pack_shield_single_size");
	private static final Identifier SHIELD_TOTAL_BUDGET = optionId("resource_pack_shield_total_budget");
	private static final Identifier SHIELD_COMPRESSION_RATIO =
			optionId("resource_pack_shield_compression_ratio");
	private static final Identifier SHIELD_PNG_DIMENSION =
			optionId("resource_pack_shield_png_dimension");
	private static final Identifier SHIELD_PNG_PIXELS = optionId("resource_pack_shield_png_pixels");
	private static final Identifier SHIELD_JSON_DEPTH = optionId("resource_pack_shield_json_depth");
	private static final Identifier SHIELD_PATH_LENGTH = optionId("resource_pack_shield_path_length");
	private static final Identifier SHIELD_PATH_DEPTH = optionId("resource_pack_shield_path_depth");
	private static final Identifier SHIELD_SCAN_TIME = optionId("resource_pack_shield_scan_time");
	private static final Identifier SHIELD_NOTIFICATIONS =
			optionId("resource_pack_shield_notifications");
	private static final Identifier SHIELD_REPORT = optionId("resource_pack_shield_report");
	private static final Identifier SHIELD_INSPECTOR_STATS =
			optionId("resource_pack_shield_inspector_stats");
	private static final Identifier PRIVACY_SCREENSHOT_ENABLED =
			optionId("privacy_screenshot_mode_enabled");
	private static final Identifier PRIVACY_HIDE_CHAT = optionId("privacy_hide_chat");
	private static final Identifier PRIVACY_HIDE_DEBUG = optionId("privacy_hide_debug");
	private static final Identifier PRIVACY_HIDE_PLAYER_LIST = optionId("privacy_hide_player_list");
	private static final Identifier PRIVACY_HIDE_SCOREBOARD = optionId("privacy_hide_scoreboard");
	private static final Identifier PRIVACY_HIDE_BOSS_BARS = optionId("privacy_hide_boss_bars");
	private static final Identifier PRIVACY_HIDE_TITLES = optionId("privacy_hide_titles");
	private static final Identifier PRIVACY_HIDE_SUBTITLES = optionId("privacy_hide_subtitles");
	private static final Identifier PRIVACY_HIDE_TOASTS = optionId("privacy_hide_toasts");
	private static final Identifier PRIVACY_HIDE_NAME_TAGS = optionId("privacy_hide_name_tags");
	private static final Identifier PRIVACY_HIDE_GAMEPLAY_HUD =
			optionId("privacy_hide_gameplay_hud");
	private static final Identifier PRIVACY_HIDE_HELD_ITEM = optionId("privacy_hide_held_item");
	private static final Identifier PRIVACY_BLOCK_SCREENS = optionId("privacy_block_screens");
	private static final Identifier PRIVACY_RANDOM_FILENAME = optionId("privacy_random_filename");
	private static final Identifier PRIVACY_FAIL_CLOSED = optionId("privacy_fail_closed");
	private static final Identifier PRIVACY_NOTIFICATIONS = optionId("privacy_notifications");
	private static final Identifier PROFILES_ENABLED = optionId("profiles_enabled");
	private static final Identifier PROFILES_RESTORE_GLOBAL =
			optionId("profiles_restore_global_defaults");
	private static final Identifier PROFILES_SINGLE_PLAYER_ENABLED =
			optionId("profiles_single_player_enabled");
	private static final Identifier PROFILES_SERVER_ENABLED =
			optionId("profiles_server_enabled");
	private static final Identifier PROFILES_GLOBAL_RENDER_DISTANCE =
			optionId("profiles_global_render_distance");
	private static final Identifier PROFILES_GLOBAL_SIMULATION_DISTANCE =
			optionId("profiles_global_simulation_distance");
	private static final Identifier PROFILES_GLOBAL_ENTITY_DISTANCE =
			optionId("profiles_global_entity_distance");
	private static final Identifier PROFILES_GLOBAL_FRAMERATE_LIMIT =
			optionId("profiles_global_framerate_limit");
	private static final Identifier PROFILES_GLOBAL_PARTICLES =
			optionId("profiles_global_particles");
	private static final Identifier PROFILES_SINGLE_PLAYER_RENDER_DISTANCE =
			optionId("profiles_single_player_render_distance");
	private static final Identifier PROFILES_SINGLE_PLAYER_SIMULATION_DISTANCE =
			optionId("profiles_single_player_simulation_distance");
	private static final Identifier PROFILES_SINGLE_PLAYER_ENTITY_DISTANCE =
			optionId("profiles_single_player_entity_distance");
	private static final Identifier PROFILES_SINGLE_PLAYER_FRAMERATE_LIMIT =
			optionId("profiles_single_player_framerate_limit");
	private static final Identifier PROFILES_SINGLE_PLAYER_PARTICLES =
			optionId("profiles_single_player_particles");
	private static final Identifier PROFILES_SINGLE_PLAYER_CAPTURE =
			optionId("profiles_single_player_capture");
	private static final Identifier PROFILES_SINGLE_PLAYER_FORGET =
			optionId("profiles_single_player_forget");
	private static final Identifier PROFILES_SERVER_RENDER_DISTANCE =
			optionId("profiles_server_render_distance");
	private static final Identifier PROFILES_SERVER_SIMULATION_DISTANCE =
			optionId("profiles_server_simulation_distance");
	private static final Identifier PROFILES_SERVER_ENTITY_DISTANCE =
			optionId("profiles_server_entity_distance");
	private static final Identifier PROFILES_SERVER_FRAMERATE_LIMIT =
			optionId("profiles_server_framerate_limit");
	private static final Identifier PROFILES_SERVER_PARTICLES =
			optionId("profiles_server_particles");
	private static final Identifier PROFILES_SERVER_CAPTURE =
			optionId("profiles_server_capture");
	private static final Identifier PROFILES_SERVER_FORGET =
			optionId("profiles_server_forget");
	private static final Identifier PROFILES_FACTORY_RESET =
			optionId("profiles_factory_reset");

	private static final VoltGuardConfig CONFIG = VoltGuardConfig.getInstance();
	private static final StorageEventHandler STORAGE_HANDLER = CONFIG::save;
	private static final VoltInspectorConfig INSPECTOR_CONFIG = VoltInspectorConfig.getInstance();
	private static final StorageEventHandler INSPECTOR_STORAGE_HANDLER = INSPECTOR_CONFIG::save;
	private static final VoltPerformanceConfig PERFORMANCE_CONFIG = VoltPerformanceConfig.getInstance();
	private static final StorageEventHandler PERFORMANCE_STORAGE_HANDLER = PERFORMANCE_CONFIG::save;
	private static final SmartFpsConfig SMART_FPS_CONFIG = SmartFpsConfig.getInstance();
	private static final StorageEventHandler SMART_FPS_STORAGE_HANDLER = SMART_FPS_CONFIG::save;
	private static final VoltRecoveryConfig RECOVERY_CONFIG = VoltRecoveryConfig.getInstance();
	private static final StorageEventHandler RECOVERY_STORAGE_HANDLER = RECOVERY_CONFIG::save;
	private static final GpuWatchdogConfig WATCHDOG_CONFIG = GpuWatchdogConfig.getInstance();
	private static final StorageEventHandler WATCHDOG_STORAGE_HANDLER = WATCHDOG_CONFIG::save;
	private static final ResourcePackShieldConfig SHIELD_CONFIG =
			ResourcePackShieldConfig.getInstance();
	private static final StorageEventHandler SHIELD_STORAGE_HANDLER = SHIELD_CONFIG::save;
	private static final PrivacyScreenshotConfig PRIVACY_CONFIG =
			PrivacyScreenshotConfig.getInstance();
	private static final StorageEventHandler PRIVACY_STORAGE_HANDLER = PRIVACY_CONFIG::save;
	private static final ProfilesConfig PROFILES_CONFIG = ProfilesConfig.getInstance();
	private static final StorageEventHandler PROFILES_STORAGE_HANDLER = PROFILES_CONFIG::save;

	@Override
	public void registerConfigLate(ConfigBuilder builder) {
		builder.registerOwnModOptions()
				.setName("Sodium Volt")
				.setNonTintedIcon(ICON)
				.addPage(builder.createOptionPage()
						.setName(Component.translatable("sodium-volt.options.page"))
						.addOptionGroup(builder.createOptionGroup()
								.setName(Component.translatable("sodium-volt.options.group.volt_guard"))
								.addOption(builder.createBooleanOption(VOLT_GUARD_ENABLED)
										.setName(Component.translatable(
												"sodium-volt.options.volt_guard_enabled"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.volt_guard_enabled.tooltip"
										))
										.setImpact(OptionImpact.VARIES)
										.setDefaultValue(false)
										.setStorageHandler(STORAGE_HANDLER)
										.setBinding(
												CONFIG::setVoltGuardEnabled,
												CONFIG::isVoltGuardEnabled
										))
								.addOption(builder.createBooleanOption(ADAPTIVE_WORKLOAD_CONTROL)
										.setName(Component.translatable(
												"sodium-volt.options.adaptive_workload_control"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.adaptive_workload_control.tooltip"
										))
										.setImpact(OptionImpact.HIGH)
										.setDefaultValue(true)
										.setStorageHandler(STORAGE_HANDLER)
										.setBinding(
												CONFIG::setAdaptiveWorkloadControl,
												CONFIG::isAdaptiveWorkloadControl
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(VOLT_GUARD_ENABLED),
												VOLT_GUARD_ENABLED
										))
								.addOption(builder.createBooleanOption(PRIORITIZE_VISIBLE_EFFECTS)
										.setName(Component.translatable(
												"sodium-volt.options.prioritize_visible_effects"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.prioritize_visible_effects.tooltip"
										))
										.setImpact(OptionImpact.HIGH)
										.setDefaultValue(true)
										.setStorageHandler(STORAGE_HANDLER)
										.setBinding(
												CONFIG::setPrioritizeVisibleEffects,
												CONFIG::isPrioritizeVisibleEffects
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(VOLT_GUARD_ENABLED),
												VOLT_GUARD_ENABLED
										))
								.addOption(builder.createBooleanOption(PRESERVE_GAMEPLAY_CRITICAL_EFFECTS)
										.setName(Component.translatable(
												"sodium-volt.options.preserve_gameplay_critical_effects"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.preserve_gameplay_critical_effects.tooltip"
										))
										.setImpact(OptionImpact.MEDIUM)
										.setDefaultValue(true)
										.setStorageHandler(STORAGE_HANDLER)
										.setBinding(
												CONFIG::setPreserveGameplayCriticalEffects,
												CONFIG::isPreserveGameplayCriticalEffects
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(VOLT_GUARD_ENABLED),
												VOLT_GUARD_ENABLED
										))
								.addOption(builder.createBooleanOption(SHOW_PROTECTION_NOTIFICATIONS)
										.setName(Component.translatable(
												"sodium-volt.options.show_protection_notifications"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.show_protection_notifications.tooltip"
										))
										.setImpact(OptionImpact.LOW)
										.setDefaultValue(true)
										.setStorageHandler(STORAGE_HANDLER)
										.setBinding(
												CONFIG::setShowProtectionNotifications,
												CONFIG::isShowProtectionNotifications
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(VOLT_GUARD_ENABLED),
												VOLT_GUARD_ENABLED
										))
								.addOption(builder.createIntegerOption(TARGET_FPS)
										.setName(Component.translatable("sodium-volt.options.target_fps"))
										.setTooltip(Component.translatable(
												"sodium-volt.options.target_fps.tooltip"
										))
										.setImpact(OptionImpact.VARIES)
										.setRange(
												VoltGuardConfig.TARGET_FPS_MIN,
												VoltGuardConfig.TARGET_FPS_MAX,
												VoltGuardConfig.TARGET_FPS_STEP
										)
										.setValueFormatter(value -> Component.translatable(
												"sodium-volt.options.target_fps.value",
												value
										))
										.setDefaultValue(VoltGuardConfig.TARGET_FPS_DEFAULT)
										.setStorageHandler(STORAGE_HANDLER)
										.setBinding(CONFIG::setTargetFps, CONFIG::getTargetFps)
										.setEnabledProvider(
												state -> state.readBooleanOption(VOLT_GUARD_ENABLED)
														&& state.readBooleanOption(ADAPTIVE_WORKLOAD_CONTROL),
												VOLT_GUARD_ENABLED,
												ADAPTIVE_WORKLOAD_CONTROL
										))
								.addOption(builder.createIntegerOption(PARTICLE_RENDER_BUDGET)
										.setName(Component.translatable(
												"sodium-volt.options.particle_render_budget"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.particle_render_budget.tooltip"
										))
										.setImpact(OptionImpact.HIGH)
										.setRange(
												VoltGuardConfig.PARTICLE_BUDGET_MIN,
												VoltGuardConfig.PARTICLE_BUDGET_MAX,
												VoltGuardConfig.PARTICLE_BUDGET_STEP
										)
										.setValueFormatter(value -> Component.translatable(
												"sodium-volt.options.render_budget.value",
												value
										))
										.setDefaultValue(VoltGuardConfig.PARTICLE_BUDGET_DEFAULT)
										.setStorageHandler(STORAGE_HANDLER)
										.setBinding(
												CONFIG::setParticleRenderBudget,
												CONFIG::getParticleRenderBudget
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(VOLT_GUARD_ENABLED),
												VOLT_GUARD_ENABLED
										))
								.addOption(builder.createIntegerOption(BLOCK_ENTITY_RENDER_BUDGET)
										.setName(Component.translatable(
												"sodium-volt.options.block_entity_render_budget"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.block_entity_render_budget.tooltip"
										))
										.setImpact(OptionImpact.HIGH)
										.setRange(
												VoltGuardConfig.BLOCK_ENTITY_BUDGET_MIN,
												VoltGuardConfig.BLOCK_ENTITY_BUDGET_MAX,
												VoltGuardConfig.BLOCK_ENTITY_BUDGET_STEP
										)
										.setValueFormatter(value -> Component.translatable(
												"sodium-volt.options.render_budget.value",
												value
										))
										.setDefaultValue(VoltGuardConfig.BLOCK_ENTITY_BUDGET_DEFAULT)
										.setStorageHandler(STORAGE_HANDLER)
										.setBinding(
												CONFIG::setBlockEntityRenderBudget,
												CONFIG::getBlockEntityRenderBudget
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(VOLT_GUARD_ENABLED),
												VOLT_GUARD_ENABLED
										))
								.addOption(builder.createIntegerOption(DISPLAY_ENTITY_RENDER_BUDGET)
										.setName(Component.translatable(
												"sodium-volt.options.display_entity_render_budget"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.display_entity_render_budget.tooltip"
										))
										.setImpact(OptionImpact.HIGH)
										.setRange(
												VoltGuardConfig.DISPLAY_ENTITY_BUDGET_MIN,
												VoltGuardConfig.DISPLAY_ENTITY_BUDGET_MAX,
												VoltGuardConfig.DISPLAY_ENTITY_BUDGET_STEP
										)
										.setValueFormatter(value -> Component.translatable(
												"sodium-volt.options.render_budget.value",
												value
										))
										.setDefaultValue(VoltGuardConfig.DISPLAY_ENTITY_BUDGET_DEFAULT)
										.setStorageHandler(STORAGE_HANDLER)
										.setBinding(
												CONFIG::setDisplayEntityRenderBudget,
												CONFIG::getDisplayEntityRenderBudget
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(VOLT_GUARD_ENABLED),
												VOLT_GUARD_ENABLED
										))))
				.addPage(builder.createOptionPage()
						.setName(Component.translatable("sodium-volt.options.inspector.page"))
						.addOptionGroup(builder.createOptionGroup()
								.setName(Component.translatable("sodium-volt.options.inspector.group"))
								.addOption(builder.createBooleanOption(VOLT_INSPECTOR_ENABLED)
										.setName(Component.translatable(
												"sodium-volt.options.inspector.enabled"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.inspector.enabled.tooltip"
										))
										.setImpact(OptionImpact.LOW)
										.setDefaultValue(false)
										.setStorageHandler(INSPECTOR_STORAGE_HANDLER)
										.setBinding(
												INSPECTOR_CONFIG::setVoltInspectorEnabled,
												INSPECTOR_CONFIG::isVoltInspectorEnabled
										))
								.addOption(builder.createBooleanOption(SHOW_INSPECTOR_OVERLAY)
										.setName(Component.translatable(
												"sodium-volt.options.inspector.show_overlay"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.inspector.show_overlay.tooltip"
										))
										.setImpact(OptionImpact.LOW)
										.setDefaultValue(true)
										.setStorageHandler(INSPECTOR_STORAGE_HANDLER)
										.setBinding(
												INSPECTOR_CONFIG::setShowInspectorOverlay,
												INSPECTOR_CONFIG::isShowInspectorOverlay
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(VOLT_INSPECTOR_ENABLED),
												VOLT_INSPECTOR_ENABLED
										))
								.addOption(inspectorToggle(
										builder, FRAME_TIME_STATISTICS, "frame_times", OptionImpact.LOW,
										INSPECTOR_CONFIG::setFrameTimeStatistics,
										INSPECTOR_CONFIG::isFrameTimeStatistics
								))
								.addOption(inspectorToggle(
										builder, CHUNK_ACTIVITY, "chunk_activity", OptionImpact.LOW,
										INSPECTOR_CONFIG::setChunkActivity,
										INSPECTOR_CONFIG::isChunkActivity
								))
								.addOption(inspectorToggle(
										builder, SCENE_COMPLEXITY, "scene_complexity", OptionImpact.LOW,
										INSPECTOR_CONFIG::setSceneComplexity,
										INSPECTOR_CONFIG::isSceneComplexity
								))
								.addOption(inspectorToggle(
										builder, PARTICLE_BREAKDOWN, "particle_breakdown", OptionImpact.MEDIUM,
										INSPECTOR_CONFIG::setParticleBreakdown,
										INSPECTOR_CONFIG::isParticleBreakdown
								))
								.addOption(inspectorToggle(
										builder, ANIMATED_TEXTURE_COUNT, "animated_textures", OptionImpact.LOW,
										INSPECTOR_CONFIG::setAnimatedTextureCount,
										INSPECTOR_CONFIG::isAnimatedTextureCount
								))
								.addOption(inspectorToggle(
										builder, GC_PAUSE_MONITOR, "gc_monitor", OptionImpact.LOW,
										INSPECTOR_CONFIG::setGcPauseMonitor,
										INSPECTOR_CONFIG::isGcPauseMonitor
								))
								.addOption(inspectorFrameDependentToggle(
										builder, BOTTLENECK_ESTIMATE, "bottleneck", OptionImpact.LOW,
										INSPECTOR_CONFIG::setBottleneckEstimate,
										INSPECTOR_CONFIG::isBottleneckEstimate
								))
								.addOption(inspectorToggle(
										builder, RENDERER_GPU_DETAILS, "renderer_details", OptionImpact.LOW,
										INSPECTOR_CONFIG::setRendererGpuDetails,
										INSPECTOR_CONFIG::isRendererGpuDetails
								))
								.addOption(inspectorToggle(
										builder, RESOURCE_RELOAD_TIMING, "reload_timing", OptionImpact.LOW,
										INSPECTOR_CONFIG::setResourceReloadTiming,
										INSPECTOR_CONFIG::isResourceReloadTiming
								))
								.addOption(inspectorToggle(
										builder, SMART_RECOMMENDATIONS, "recommendations", OptionImpact.LOW,
										INSPECTOR_CONFIG::setSmartRecommendations,
										INSPECTOR_CONFIG::isSmartRecommendations
								))
								.addOption(builder.createIntegerOption(INSPECTOR_REFRESH_INTERVAL)
										.setName(Component.translatable(
												"sodium-volt.options.inspector.refresh_interval"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.inspector.refresh_interval.tooltip"
										))
										.setImpact(OptionImpact.LOW)
										.setRange(
												VoltInspectorConfig.REFRESH_INTERVAL_MIN,
												VoltInspectorConfig.REFRESH_INTERVAL_MAX,
												VoltInspectorConfig.REFRESH_INTERVAL_STEP
										)
										.setValueFormatter(value -> Component.translatable(
												"sodium-volt.options.inspector.milliseconds.value", value
										))
										.setDefaultValue(VoltInspectorConfig.REFRESH_INTERVAL_DEFAULT)
										.setStorageHandler(INSPECTOR_STORAGE_HANDLER)
										.setBinding(
												INSPECTOR_CONFIG::setRefreshIntervalMs,
												INSPECTOR_CONFIG::getRefreshIntervalMs
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(VOLT_INSPECTOR_ENABLED),
												VOLT_INSPECTOR_ENABLED
										))
								.addOption(builder.createIntegerOption(FRAME_SAMPLE_WINDOW)
										.setName(Component.translatable(
												"sodium-volt.options.inspector.sample_window"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.inspector.sample_window.tooltip"
										))
										.setImpact(OptionImpact.LOW)
										.setRange(
												VoltInspectorConfig.SAMPLE_WINDOW_MIN,
												VoltInspectorConfig.SAMPLE_WINDOW_MAX,
												VoltInspectorConfig.SAMPLE_WINDOW_STEP
										)
										.setValueFormatter(value -> Component.translatable(
												"sodium-volt.options.inspector.frames.value", value
										))
										.setDefaultValue(VoltInspectorConfig.SAMPLE_WINDOW_DEFAULT)
										.setStorageHandler(INSPECTOR_STORAGE_HANDLER)
										.setBinding(
												INSPECTOR_CONFIG::setFrameSampleWindow,
												INSPECTOR_CONFIG::getFrameSampleWindow
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(VOLT_INSPECTOR_ENABLED)
														&& state.readBooleanOption(FRAME_TIME_STATISTICS),
												VOLT_INSPECTOR_ENABLED,
												FRAME_TIME_STATISTICS
										))
								.addOption(builder.createIntegerOption(SPIKE_THRESHOLD)
										.setName(Component.translatable(
												"sodium-volt.options.inspector.spike_threshold"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.inspector.spike_threshold.tooltip"
										))
										.setImpact(OptionImpact.LOW)
										.setRange(
												VoltInspectorConfig.SPIKE_THRESHOLD_MIN,
												VoltInspectorConfig.SPIKE_THRESHOLD_MAX,
												VoltInspectorConfig.SPIKE_THRESHOLD_STEP
										)
										.setValueFormatter(value -> Component.translatable(
												"sodium-volt.options.inspector.milliseconds.value", value
										))
										.setDefaultValue(VoltInspectorConfig.SPIKE_THRESHOLD_DEFAULT)
										.setStorageHandler(INSPECTOR_STORAGE_HANDLER)
										.setBinding(
												INSPECTOR_CONFIG::setSpikeThresholdMs,
												INSPECTOR_CONFIG::getSpikeThresholdMs
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(VOLT_INSPECTOR_ENABLED)
														&& state.readBooleanOption(FRAME_TIME_STATISTICS),
												VOLT_INSPECTOR_ENABLED,
												FRAME_TIME_STATISTICS
										))))
				.addPage(builder.createOptionPage()
						.setName(Component.translatable("sodium-volt.options.performance.page"))
						.addOptionGroup(builder.createOptionGroup()
								.setName(Component.translatable("sodium-volt.options.performance.group"))
								.addOption(builder.createBooleanOption(APC_ENABLED)
										.setName(Component.translatable(
												"sodium-volt.options.performance.enabled"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.performance.enabled.tooltip"
										))
										.setImpact(OptionImpact.VARIES)
										.setDefaultValue(false)
										.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
										.setBinding(
												PERFORMANCE_CONFIG::setAdaptivePerformanceControllerEnabled,
												PERFORMANCE_CONFIG::isAdaptivePerformanceControllerEnabled
										))
								.addOption(builder.createEnumOption(
												APC_PROFILE,
												VoltPerformanceConfig.Profile.class
										)
										.setName(Component.translatable(
												"sodium-volt.options.performance.profile"
										))
										.setTooltip(profile -> Component.translatable(
												"sodium-volt.options.performance.profile."
														+ profile.name().toLowerCase(java.util.Locale.ROOT)
														+ ".tooltip"
										))
										.setImpact(OptionImpact.VARIES)
										.setDefaultValue(VoltPerformanceConfig.Profile.BALANCED)
										.setElementNameProvider(profile -> Component.translatable(
												"sodium-volt.options.performance.profile."
														+ profile.name().toLowerCase(java.util.Locale.ROOT)
										))
										.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
										.setBinding(
												PERFORMANCE_CONFIG::setProfile,
												PERFORMANCE_CONFIG::getProfile
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(APC_ENABLED),
												APC_ENABLED
										))
								.addOption(builder.createIntegerOption(APC_TARGET_FPS)
										.setName(Component.translatable(
												"sodium-volt.options.performance.target_fps"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.performance.target_fps.tooltip"
										))
										.setImpact(OptionImpact.VARIES)
										.setRange(
												VoltPerformanceConfig.TARGET_FPS_MIN,
												VoltPerformanceConfig.TARGET_FPS_MAX,
												VoltPerformanceConfig.TARGET_FPS_STEP
										)
										.setValueFormatter(value -> value == VoltPerformanceConfig.TARGET_FPS_MAX
												? Component.translatable(
														"sodium-volt.options.performance.target_fps.max"
												)
												: Component.translatable(
														"sodium-volt.options.performance.fps.value",
														value
												))
										.setDefaultValue(VoltPerformanceConfig.TARGET_FPS_DEFAULT)
										.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
										.setBinding(
												PERFORMANCE_CONFIG::setTargetFps,
												PERFORMANCE_CONFIG::getTargetFps
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(APC_ENABLED),
												APC_ENABLED
										))
								.addOption(performanceToggle(
										builder, APC_RENDER_DISTANCE, "render_distance", OptionImpact.HIGH,
										PERFORMANCE_CONFIG::setAdaptiveRenderDistance,
										PERFORMANCE_CONFIG::isAdaptiveRenderDistance
								))
								.addOption(performanceToggle(
										builder, APC_ENTITY_DISTANCE, "entity_distance", OptionImpact.HIGH,
										PERFORMANCE_CONFIG::setAdaptiveEntityDistance,
										PERFORMANCE_CONFIG::isAdaptiveEntityDistance
								))
								.addOption(performanceToggle(
										builder, APC_PARTICLE_QUALITY, "particle_quality", OptionImpact.MEDIUM,
										PERFORMANCE_CONFIG::setAdaptiveParticleQuality,
										PERFORMANCE_CONFIG::isAdaptiveParticleQuality
								))
								.addOption(performanceToggle(
										builder, APC_VISUAL_EFFECTS, "visual_effects", OptionImpact.HIGH,
										PERFORMANCE_CONFIG::setAdaptiveVisualEffects,
										PERFORMANCE_CONFIG::isAdaptiveVisualEffects
								))
								.addOption(builder.createBooleanOption(APC_ANIMATION_THROTTLING)
										.setName(Component.translatable(
												"sodium-volt.options.performance.animation_throttling"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.performance.animation_throttling.tooltip"
										))
										.setImpact(OptionImpact.MEDIUM)
										.setDefaultValue(false)
										.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
										.setBinding(
												PERFORMANCE_CONFIG::setAdaptiveAnimationThrottling,
												PERFORMANCE_CONFIG::isAdaptiveAnimationThrottling
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(APC_ENABLED),
												APC_ENABLED
										))
								.addOption(performanceToggle(
										builder, APC_RESTORE_SETTINGS, "restore_settings", OptionImpact.LOW,
										PERFORMANCE_CONFIG::setRestoreOriginalSettings,
										PERFORMANCE_CONFIG::isRestoreOriginalSettings
								))
								.addOption(performanceToggle(
										builder, APC_NOTIFICATIONS, "notifications", OptionImpact.LOW,
										PERFORMANCE_CONFIG::setShowControllerNotifications,
										PERFORMANCE_CONFIG::isShowControllerNotifications
								))
								.addOption(builder.createIntegerOption(APC_MIN_RENDER_DISTANCE)
										.setName(Component.translatable(
												"sodium-volt.options.performance.min_render_distance"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.performance.min_render_distance.tooltip"
										))
										.setImpact(OptionImpact.HIGH)
										.setRange(
												VoltPerformanceConfig.MIN_RENDER_DISTANCE_MIN,
												VoltPerformanceConfig.MIN_RENDER_DISTANCE_MAX,
												1
										)
										.setValueFormatter(value -> Component.translatable(
												"sodium-volt.options.performance.chunks.value", value
										))
										.setDefaultValue(VoltPerformanceConfig.MIN_RENDER_DISTANCE_DEFAULT)
										.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
										.setBinding(
												PERFORMANCE_CONFIG::setMinimumRenderDistance,
												PERFORMANCE_CONFIG::getMinimumRenderDistance
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(APC_ENABLED)
														&& state.readBooleanOption(APC_RENDER_DISTANCE),
												APC_ENABLED,
												APC_RENDER_DISTANCE
										))
								.addOption(builder.createIntegerOption(APC_MAX_RENDER_DISTANCE)
										.setName(Component.translatable(
												"sodium-volt.options.performance.max_render_distance"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.performance.max_render_distance.tooltip"
										))
										.setImpact(OptionImpact.HIGH)
										.setRange(
												VoltPerformanceConfig.MAX_RENDER_DISTANCE_MIN,
												VoltPerformanceConfig.MAX_RENDER_DISTANCE_MAX,
												1
										)
										.setValueFormatter(value -> Component.translatable(
												"sodium-volt.options.performance.chunks.value", value
										))
										.setDefaultValue(VoltPerformanceConfig.MAX_RENDER_DISTANCE_DEFAULT)
										.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
										.setBinding(
												PERFORMANCE_CONFIG::setMaximumRenderDistance,
												PERFORMANCE_CONFIG::getMaximumRenderDistance
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(APC_ENABLED)
														&& state.readBooleanOption(APC_RENDER_DISTANCE),
												APC_ENABLED,
												APC_RENDER_DISTANCE
										))
								.addOption(performanceSecondsOption(
										builder,
										APC_ADJUSTMENT_INTERVAL,
										"adjustment_interval",
										VoltPerformanceConfig.ADJUSTMENT_INTERVAL_MIN,
										VoltPerformanceConfig.ADJUSTMENT_INTERVAL_MAX,
										VoltPerformanceConfig.ADJUSTMENT_INTERVAL_DEFAULT,
										PERFORMANCE_CONFIG::setAdjustmentIntervalSeconds,
										PERFORMANCE_CONFIG::getAdjustmentIntervalSeconds
								))
								.addOption(performanceSecondsOption(
										builder,
										APC_RECOVERY_DELAY,
										"recovery_delay",
										VoltPerformanceConfig.RECOVERY_DELAY_MIN,
										VoltPerformanceConfig.RECOVERY_DELAY_MAX,
										VoltPerformanceConfig.RECOVERY_DELAY_DEFAULT,
										PERFORMANCE_CONFIG::setQualityRecoveryDelaySeconds,
										PERFORMANCE_CONFIG::getQualityRecoveryDelaySeconds
								))
								.addOption(builder.createIntegerOption(APC_FPS_TOLERANCE)
										.setName(Component.translatable(
												"sodium-volt.options.performance.fps_tolerance"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.performance.fps_tolerance.tooltip"
										))
										.setImpact(OptionImpact.LOW)
										.setRange(
												VoltPerformanceConfig.FPS_TOLERANCE_MIN,
												VoltPerformanceConfig.FPS_TOLERANCE_MAX,
												1
										)
										.setValueFormatter(value -> Component.translatable(
												"sodium-volt.options.performance.fps.value", value
										))
										.setDefaultValue(VoltPerformanceConfig.FPS_TOLERANCE_DEFAULT)
										.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
										.setBinding(
												PERFORMANCE_CONFIG::setFpsTolerance,
												PERFORMANCE_CONFIG::getFpsTolerance
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(APC_ENABLED),
												APC_ENABLED
										))
								.addOption(builder.createIntegerOption(APC_SAMPLE_WINDOW)
										.setName(Component.translatable(
												"sodium-volt.options.performance.sample_window"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.performance.sample_window.tooltip"
										))
										.setImpact(OptionImpact.LOW)
										.setRange(
												VoltPerformanceConfig.SAMPLE_WINDOW_MIN,
												VoltPerformanceConfig.SAMPLE_WINDOW_MAX,
												VoltPerformanceConfig.SAMPLE_WINDOW_STEP
										)
										.setValueFormatter(value -> Component.translatable(
												"sodium-volt.options.performance.frames.value", value
										))
										.setDefaultValue(VoltPerformanceConfig.SAMPLE_WINDOW_DEFAULT)
										.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
										.setBinding(
												PERFORMANCE_CONFIG::setSampleWindow,
												PERFORMANCE_CONFIG::getSampleWindow
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(APC_ENABLED),
												APC_ENABLED
										)))
						.addOptionGroup(builder.createOptionGroup()
								.setName(Component.translatable(
										"sodium-volt.options.performance.vaps.group"
								))
								.addOption(builder.createBooleanOption(VAPS_ENABLED)
										.setName(Component.translatable(
												"sodium-volt.options.performance.vaps.enabled"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.performance.vaps.enabled.tooltip"
										))
										.setImpact(OptionImpact.VARIES)
										.setDefaultValue(false)
										.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
										.setBinding(
												PERFORMANCE_CONFIG::setVisibilityAwareParticleSchedulerEnabled,
												PERFORMANCE_CONFIG::isVisibilityAwareParticleSchedulerEnabled
										))
								.addOption(vapsToggle(
										builder,
										VAPS_PRIORITIZE_IN_FRUSTUM,
										"prioritize_in_frustum",
										OptionImpact.MEDIUM,
										PERFORMANCE_CONFIG::setVapsPrioritizeInFrustum,
										PERFORMANCE_CONFIG::isVapsPrioritizeInFrustum
								))
								.addOption(vapsToggle(
										builder,
										VAPS_SKIP_BEHIND_CAMERA,
										"skip_behind_camera",
										OptionImpact.MEDIUM,
										PERFORMANCE_CONFIG::setVapsSkipBehindCamera,
										PERFORMANCE_CONFIG::isVapsSkipBehindCamera
								))
								.addOption(vapsToggle(
										builder,
										VAPS_DISTANCE_AWARE_SIMULATION,
										"distance_aware_simulation",
										OptionImpact.HIGH,
										PERFORMANCE_CONFIG::setVapsDistanceAwareSimulation,
										PERFORMANCE_CONFIG::isVapsDistanceAwareSimulation
								))
								.addOption(vapsToggle(
										builder,
										VAPS_PRESERVE_CRITICAL,
										"preserve_critical",
										OptionImpact.MEDIUM,
										PERFORMANCE_CONFIG::setVapsPreserveCriticalParticles,
										PERFORMANCE_CONFIG::isVapsPreserveCriticalParticles
								))
								.addOption(vapsToggle(
										builder,
										VAPS_COALESCE_AMBIENT,
										"coalesce_ambient",
										OptionImpact.MEDIUM,
										PERFORMANCE_CONFIG::setVapsCoalesceAmbientParticles,
										PERFORMANCE_CONFIG::isVapsCoalesceAmbientParticles
								))
								.addOption(vapsToggle(
										builder,
										VAPS_PER_TYPE_LIMITS,
										"per_type_limits",
										OptionImpact.HIGH,
										PERFORMANCE_CONFIG::setVapsPerTypeRenderLimits,
										PERFORMANCE_CONFIG::isVapsPerTypeRenderLimits
								))
								.addOption(vapsToggle(
										builder,
										VAPS_INSPECTOR_STATS,
										"inspector_stats",
										OptionImpact.LOW,
										PERFORMANCE_CONFIG::setVapsShowInspectorStatistics,
										PERFORMANCE_CONFIG::isVapsShowInspectorStatistics
								))
								.addOption(builder.createIntegerOption(VAPS_FULL_RATE_DISTANCE)
										.setName(Component.translatable(
												"sodium-volt.options.performance.vaps.full_rate_distance"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.performance.vaps.full_rate_distance.tooltip"
										))
										.setImpact(OptionImpact.HIGH)
										.setRange(
												VoltPerformanceConfig.VAPS_FULL_RATE_DISTANCE_MIN,
												VoltPerformanceConfig.VAPS_FULL_RATE_DISTANCE_MAX,
												VoltPerformanceConfig.VAPS_FULL_RATE_DISTANCE_STEP
										)
										.setValueFormatter(value -> Component.translatable(
												"sodium-volt.options.performance.vaps.blocks.value", value
										))
										.setDefaultValue(VoltPerformanceConfig.VAPS_FULL_RATE_DISTANCE_DEFAULT)
										.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
										.setBinding(
												PERFORMANCE_CONFIG::setVapsFullRateDistance,
												PERFORMANCE_CONFIG::getVapsFullRateDistance
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(VAPS_ENABLED)
														&& state.readBooleanOption(VAPS_DISTANCE_AWARE_SIMULATION),
												VAPS_ENABLED,
												VAPS_DISTANCE_AWARE_SIMULATION
										))
								.addOption(builder.createIntegerOption(VAPS_FAR_TICK_INTERVAL)
										.setName(Component.translatable(
												"sodium-volt.options.performance.vaps.far_tick_interval"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.performance.vaps.far_tick_interval.tooltip"
										))
										.setImpact(OptionImpact.HIGH)
										.setRange(
												VoltPerformanceConfig.VAPS_FAR_TICK_INTERVAL_MIN,
												VoltPerformanceConfig.VAPS_FAR_TICK_INTERVAL_MAX,
												1
										)
										.setValueFormatter(value -> Component.translatable(
												"sodium-volt.options.performance.vaps.ticks.value", value
										))
										.setDefaultValue(VoltPerformanceConfig.VAPS_FAR_TICK_INTERVAL_DEFAULT)
										.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
										.setBinding(
												PERFORMANCE_CONFIG::setVapsFarTickInterval,
												PERFORMANCE_CONFIG::getVapsFarTickInterval
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(VAPS_ENABLED)
														&& state.readBooleanOption(VAPS_DISTANCE_AWARE_SIMULATION),
												VAPS_ENABLED,
												VAPS_DISTANCE_AWARE_SIMULATION
										))
								.addOption(builder.createIntegerOption(VAPS_PER_TYPE_LIMIT)
										.setName(Component.translatable(
												"sodium-volt.options.performance.vaps.per_type_limit"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.performance.vaps.per_type_limit.tooltip"
										))
										.setImpact(OptionImpact.HIGH)
										.setRange(
												VoltPerformanceConfig.VAPS_PER_TYPE_RENDER_LIMIT_MIN,
												VoltPerformanceConfig.VAPS_PER_TYPE_RENDER_LIMIT_MAX,
												VoltPerformanceConfig.VAPS_PER_TYPE_RENDER_LIMIT_STEP
										)
										.setValueFormatter(value -> Component.translatable(
												"sodium-volt.options.performance.vaps.particles.value", value
										))
										.setDefaultValue(VoltPerformanceConfig.VAPS_PER_TYPE_RENDER_LIMIT_DEFAULT)
										.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
										.setBinding(
												PERFORMANCE_CONFIG::setVapsPerTypeRenderLimit,
												PERFORMANCE_CONFIG::getVapsPerTypeRenderLimit
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(VAPS_ENABLED)
														&& state.readBooleanOption(VAPS_PER_TYPE_LIMITS),
												VAPS_ENABLED,
												VAPS_PER_TYPE_LIMITS
										))
								.addOption(builder.createIntegerOption(VAPS_AMBIENT_PER_CELL)
										.setName(Component.translatable(
												"sodium-volt.options.performance.vaps.ambient_per_cell"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.performance.vaps.ambient_per_cell.tooltip"
										))
										.setImpact(OptionImpact.MEDIUM)
										.setRange(
												VoltPerformanceConfig.VAPS_AMBIENT_PER_CELL_MIN,
												VoltPerformanceConfig.VAPS_AMBIENT_PER_CELL_MAX,
												1
										)
										.setValueFormatter(value -> Component.translatable(
												"sodium-volt.options.performance.vaps.particles.value", value
										))
										.setDefaultValue(VoltPerformanceConfig.VAPS_AMBIENT_PER_CELL_DEFAULT)
										.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
										.setBinding(
												PERFORMANCE_CONFIG::setVapsAmbientPerCell,
												PERFORMANCE_CONFIG::getVapsAmbientPerCell
										)
										.setEnabledProvider(
												state -> state.readBooleanOption(VAPS_ENABLED)
														&& state.readBooleanOption(VAPS_COALESCE_AMBIENT),
												VAPS_ENABLED,
												VAPS_COALESCE_AMBIENT
										))
								.addOption(builder.createIntegerOption(VAPS_CRITICAL_RESERVE)
										.setName(Component.translatable(
												"sodium-volt.options.performance.vaps.critical_reserve"
										))
										.setTooltip(Component.translatable(
												"sodium-volt.options.performance.vaps.critical_reserve.tooltip"
										))
										.setImpact(OptionImpact.MEDIUM)
										.setRange(
												VoltPerformanceConfig.VAPS_CRITICAL_RESERVE_MIN,
												VoltPerformanceConfig.VAPS_CRITICAL_RESERVE_MAX,
												VoltPerformanceConfig.VAPS_CRITICAL_RESERVE_STEP
										)
										.setValueFormatter(value -> Component.translatable(
												"sodium-volt.options.performance.vaps.particles.value", value
										))
										.setDefaultValue(VoltPerformanceConfig.VAPS_CRITICAL_RESERVE_DEFAULT)
										.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
										.setBinding(
												PERFORMANCE_CONFIG::setVapsCriticalReserve,
												PERFORMANCE_CONFIG::getVapsCriticalReserve
										)
											.setEnabledProvider(
													state -> state.readBooleanOption(VAPS_ENABLED)
															&& state.readBooleanOption(VAPS_PRESERVE_CRITICAL),
													VAPS_ENABLED,
													VAPS_PRESERVE_CRITICAL
											)))
							.addOptionGroup(builder.createOptionGroup()
									.setName(Component.translatable(
											"sodium-volt.options.performance.berp.group"
									))
									.addOption(builder.createBooleanOption(BERP_ENABLED)
											.setName(Component.translatable(
													"sodium-volt.options.performance.berp.enabled"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.berp.enabled.tooltip"
											))
											.setImpact(OptionImpact.VARIES)
											.setDefaultValue(false)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setBlockEntityRenderBudgetingEnabled,
													PERFORMANCE_CONFIG::isBlockEntityRenderBudgetingEnabled
											))
									.addOption(berpToggle(
											builder,
											BERP_PRIORITIZE_NEARBY,
											"prioritize_nearby",
											OptionImpact.HIGH,
											true,
											PERFORMANCE_CONFIG::setBerpPrioritizeNearby,
											PERFORMANCE_CONFIG::isBerpPrioritizeNearby
									))
									.addOption(berpToggle(
											builder,
											BERP_RECENT_INTERACTION,
											"recent_interaction",
											OptionImpact.MEDIUM,
											true,
											PERFORMANCE_CONFIG::setBerpRecentInteractionGrace,
											PERFORMANCE_CONFIG::isBerpRecentInteractionGrace
									))
									.addOption(berpToggle(
											builder,
											BERP_DISTANCE_AWARE_UPDATES,
											"distance_aware_updates",
											OptionImpact.HIGH,
											true,
											PERFORMANCE_CONFIG::setBerpDistanceAwareStateUpdates,
											PERFORMANCE_CONFIG::isBerpDistanceAwareStateUpdates
									))
										.addOption(berpToggle(
														builder,
														BERP_CACHE_FAR_STATES,
														"cache_far_states",
														OptionImpact.HIGH,
														true,
														PERFORMANCE_CONFIG::setBerpCacheFarRenderStates,
														PERFORMANCE_CONFIG::isBerpCacheFarRenderStates
												)
												.setEnabledProvider(
														state -> state.readBooleanOption(BERP_ENABLED)
																&& state.readBooleanOption(BERP_DISTANCE_AWARE_UPDATES),
														BERP_ENABLED,
														BERP_DISTANCE_AWARE_UPDATES
												))
									.addOption(berpToggle(
											builder,
											BERP_PER_TYPE_LIMITS,
											"per_type_limits",
											OptionImpact.HIGH,
											true,
											PERFORMANCE_CONFIG::setBerpPerTypeRenderLimits,
											PERFORMANCE_CONFIG::isBerpPerTypeRenderLimits
									))
									.addOption(berpToggle(
											builder,
											BERP_CULL_BEYOND_FAR,
											"cull_beyond_far",
											OptionImpact.HIGH,
											false,
											PERFORMANCE_CONFIG::setBerpCullBeyondFarDistance,
											PERFORMANCE_CONFIG::isBerpCullBeyondFarDistance
									))
									.addOption(berpToggle(
													builder,
													BERP_INCLUDE_MODDED,
													"include_modded",
													OptionImpact.VARIES,
													false,
													PERFORMANCE_CONFIG::setBerpIncludeModdedBlockEntities,
													PERFORMANCE_CONFIG::isBerpIncludeModdedBlockEntities
											)
												.setEnabledProvider(
														state -> state.readBooleanOption(BERP_ENABLED)
																&& state.readBooleanOption(BERP_DISTANCE_AWARE_UPDATES)
																&& state.readBooleanOption(BERP_CACHE_FAR_STATES),
														BERP_ENABLED,
														BERP_DISTANCE_AWARE_UPDATES,
														BERP_CACHE_FAR_STATES
												))
									.addOption(berpToggle(
											builder,
											BERP_INSPECTOR_STATS,
											"inspector_stats",
											OptionImpact.LOW,
											true,
											PERFORMANCE_CONFIG::setBerpShowInspectorStatistics,
											PERFORMANCE_CONFIG::isBerpShowInspectorStatistics
									))
									.addOption(builder.createIntegerOption(BERP_NEAR_DISTANCE)
											.setName(Component.translatable(
													"sodium-volt.options.performance.berp.near_distance"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.berp.near_distance.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltPerformanceConfig.BERP_NEAR_DISTANCE_MIN,
													VoltPerformanceConfig.BERP_NEAR_DISTANCE_MAX,
													VoltPerformanceConfig.BERP_NEAR_DISTANCE_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.berp.blocks.value", value
											))
											.setDefaultValue(VoltPerformanceConfig.BERP_NEAR_DISTANCE_DEFAULT)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setBerpNearDistance,
													PERFORMANCE_CONFIG::getBerpNearDistance
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(BERP_ENABLED)
															&& state.readBooleanOption(BERP_DISTANCE_AWARE_UPDATES),
													BERP_ENABLED,
													BERP_DISTANCE_AWARE_UPDATES
											))
									.addOption(builder.createIntegerOption(BERP_MEDIUM_DISTANCE)
											.setName(Component.translatable(
													"sodium-volt.options.performance.berp.medium_distance"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.berp.medium_distance.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltPerformanceConfig.BERP_MEDIUM_DISTANCE_MIN,
													VoltPerformanceConfig.BERP_MEDIUM_DISTANCE_MAX,
													VoltPerformanceConfig.BERP_MEDIUM_DISTANCE_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.berp.blocks.value", value
											))
											.setDefaultValue(VoltPerformanceConfig.BERP_MEDIUM_DISTANCE_DEFAULT)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setBerpMediumDistance,
													PERFORMANCE_CONFIG::getBerpMediumDistance
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(BERP_ENABLED)
															&& state.readBooleanOption(BERP_DISTANCE_AWARE_UPDATES),
													BERP_ENABLED,
													BERP_DISTANCE_AWARE_UPDATES
											))
									.addOption(builder.createIntegerOption(BERP_MEDIUM_INTERVAL)
											.setName(Component.translatable(
													"sodium-volt.options.performance.berp.medium_interval"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.berp.medium_interval.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltPerformanceConfig.BERP_MEDIUM_INTERVAL_MIN,
													VoltPerformanceConfig.BERP_MEDIUM_INTERVAL_MAX,
													1
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.berp.ticks.value", value
											))
											.setDefaultValue(VoltPerformanceConfig.BERP_MEDIUM_INTERVAL_DEFAULT)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setBerpMediumUpdateInterval,
													PERFORMANCE_CONFIG::getBerpMediumUpdateInterval
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(BERP_ENABLED)
															&& state.readBooleanOption(BERP_DISTANCE_AWARE_UPDATES)
															&& state.readBooleanOption(BERP_CACHE_FAR_STATES),
													BERP_ENABLED,
													BERP_DISTANCE_AWARE_UPDATES,
													BERP_CACHE_FAR_STATES
											))
									.addOption(builder.createIntegerOption(BERP_FAR_INTERVAL)
											.setName(Component.translatable(
													"sodium-volt.options.performance.berp.far_interval"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.berp.far_interval.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltPerformanceConfig.BERP_FAR_INTERVAL_MIN,
													VoltPerformanceConfig.BERP_FAR_INTERVAL_MAX,
													1
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.berp.ticks.value", value
											))
											.setDefaultValue(VoltPerformanceConfig.BERP_FAR_INTERVAL_DEFAULT)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setBerpFarUpdateInterval,
													PERFORMANCE_CONFIG::getBerpFarUpdateInterval
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(BERP_ENABLED)
															&& state.readBooleanOption(BERP_DISTANCE_AWARE_UPDATES)
															&& state.readBooleanOption(BERP_CACHE_FAR_STATES),
													BERP_ENABLED,
													BERP_DISTANCE_AWARE_UPDATES,
													BERP_CACHE_FAR_STATES
											))
									.addOption(builder.createIntegerOption(BERP_FAR_DISTANCE)
											.setName(Component.translatable(
													"sodium-volt.options.performance.berp.far_distance"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.berp.far_distance.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltPerformanceConfig.BERP_FAR_DISTANCE_MIN,
													VoltPerformanceConfig.BERP_FAR_DISTANCE_MAX,
													VoltPerformanceConfig.BERP_FAR_DISTANCE_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.berp.blocks.value", value
											))
											.setDefaultValue(VoltPerformanceConfig.BERP_FAR_DISTANCE_DEFAULT)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setBerpFarRenderDistance,
													PERFORMANCE_CONFIG::getBerpFarRenderDistance
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(BERP_ENABLED),
													BERP_ENABLED
											))
									.addOption(builder.createIntegerOption(BERP_GLOBAL_BUDGET)
											.setName(Component.translatable(
													"sodium-volt.options.performance.berp.global_budget"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.berp.global_budget.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltPerformanceConfig.BERP_GLOBAL_BUDGET_MIN,
													VoltPerformanceConfig.BERP_GLOBAL_BUDGET_MAX,
													VoltPerformanceConfig.BERP_GLOBAL_BUDGET_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.berp.states.value", value
											))
											.setDefaultValue(VoltPerformanceConfig.BERP_GLOBAL_BUDGET_DEFAULT)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setBerpGlobalRenderBudget,
													PERFORMANCE_CONFIG::getBerpGlobalRenderBudget
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(BERP_ENABLED),
													BERP_ENABLED
											))
									.addOption(builder.createIntegerOption(BERP_PER_TYPE_LIMIT)
											.setName(Component.translatable(
													"sodium-volt.options.performance.berp.per_type_limit"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.berp.per_type_limit.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltPerformanceConfig.BERP_PER_TYPE_LIMIT_MIN,
													VoltPerformanceConfig.BERP_PER_TYPE_LIMIT_MAX,
													VoltPerformanceConfig.BERP_PER_TYPE_LIMIT_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.berp.states.value", value
											))
											.setDefaultValue(VoltPerformanceConfig.BERP_PER_TYPE_LIMIT_DEFAULT)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setBerpPerTypeRenderLimit,
													PERFORMANCE_CONFIG::getBerpPerTypeRenderLimit
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(BERP_ENABLED)
															&& state.readBooleanOption(BERP_PER_TYPE_LIMITS),
													BERP_ENABLED,
													BERP_PER_TYPE_LIMITS
											))
									.addOption(builder.createIntegerOption(BERP_GRACE_SECONDS)
											.setName(Component.translatable(
													"sodium-volt.options.performance.berp.grace_seconds"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.berp.grace_seconds.tooltip"
											))
											.setImpact(OptionImpact.LOW)
											.setRange(
													VoltPerformanceConfig.BERP_GRACE_SECONDS_MIN,
													VoltPerformanceConfig.BERP_GRACE_SECONDS_MAX,
													1
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.seconds.value", value
											))
											.setDefaultValue(VoltPerformanceConfig.BERP_GRACE_SECONDS_DEFAULT)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setBerpInteractionGraceSeconds,
													PERFORMANCE_CONFIG::getBerpInteractionGraceSeconds
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(BERP_ENABLED)
															&& state.readBooleanOption(BERP_RECENT_INTERACTION),
													BERP_ENABLED,
													BERP_RECENT_INTERACTION
											))
									.addOption(builder.createIntegerOption(BERP_CACHE_CAPACITY)
											.setName(Component.translatable(
													"sodium-volt.options.performance.berp.cache_capacity"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.berp.cache_capacity.tooltip"
											))
											.setImpact(OptionImpact.MEDIUM)
											.setRange(
													VoltPerformanceConfig.BERP_CACHE_CAPACITY_MIN,
													VoltPerformanceConfig.BERP_CACHE_CAPACITY_MAX,
													VoltPerformanceConfig.BERP_CACHE_CAPACITY_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.berp.states.value", value
											))
											.setDefaultValue(VoltPerformanceConfig.BERP_CACHE_CAPACITY_DEFAULT)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setBerpCacheCapacity,
													PERFORMANCE_CONFIG::getBerpCacheCapacity
											)
												.setEnabledProvider(
														state -> state.readBooleanOption(BERP_ENABLED)
																&& state.readBooleanOption(BERP_CACHE_FAR_STATES),
														BERP_ENABLED,
														BERP_CACHE_FAR_STATES
												)))
							.addOptionGroup(builder.createOptionGroup()
									.setName(Component.translatable(
											"sodium-volt.options.performance.att.group"
									))
									.addOption(builder.createBooleanOption(ATT_ENABLED)
											.setName(Component.translatable(
													"sodium-volt.options.performance.att.enabled"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.att.enabled.tooltip"
											))
											.setImpact(OptionImpact.VARIES)
											.setDefaultValue(false)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setAnimatedTextureThrottlingEnabled,
													PERFORMANCE_CONFIG::isAnimatedTextureThrottlingEnabled
											))
									.addOption(attToggle(
											builder,
											ATT_PAUSE_INVISIBLE,
											"pause_invisible",
											OptionImpact.HIGH,
											PERFORMANCE_CONFIG::setAttPauseInvisibleAnimations,
											PERFORMANCE_CONFIG::isAttPauseInvisibleAnimations
									))
									.addOption(attToggle(
											builder,
											ATT_DISTANCE_AWARE,
											"distance_aware",
											OptionImpact.HIGH,
											PERFORMANCE_CONFIG::setAttDistanceAwareCadence,
											PERFORMANCE_CONFIG::isAttDistanceAwareCadence
									))
									.addOption(attToggle(
											builder,
											ATT_INTERFACE_FULL_SPEED,
											"interface_full_speed",
											OptionImpact.LOW,
											PERFORMANCE_CONFIG::setAttKeepInterfaceAtlasesFullSpeed,
											PERFORMANCE_CONFIG::isAttKeepInterfaceAtlasesFullSpeed
									))
									.addOption(attToggle(
											builder,
											ATT_CRITICAL_VANILLA,
											"critical_vanilla",
											OptionImpact.LOW,
											PERFORMANCE_CONFIG::setAttExemptCriticalVanillaTextures,
											PERFORMANCE_CONFIG::isAttExemptCriticalVanillaTextures
									))
									.addOption(attToggle(
											builder,
											ATT_HONOR_EXEMPTIONS,
											"honor_exemptions",
											OptionImpact.LOW,
											PERFORMANCE_CONFIG::setAttHonorExemptionLists,
											PERFORMANCE_CONFIG::isAttHonorExemptionLists
									))
									.addOption(attToggle(
											builder,
											ATT_IMMEDIATE_RESUME,
											"immediate_resume",
											OptionImpact.LOW,
											PERFORMANCE_CONFIG::setAttImmediateSmoothResume,
											PERFORMANCE_CONFIG::isAttImmediateSmoothResume
									))
									.addOption(attToggle(
											builder,
											ATT_INSPECTOR_STATS,
											"inspector_stats",
											OptionImpact.LOW,
											PERFORMANCE_CONFIG::setAttShowInspectorStatistics,
											PERFORMANCE_CONFIG::isAttShowInspectorStatistics
									))
									.addOption(builder.createIntegerOption(ATT_FULL_SPEED_DISTANCE)
											.setName(Component.translatable(
													"sodium-volt.options.performance.att.full_speed_distance"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.att.full_speed_distance.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltPerformanceConfig.ATT_FULL_SPEED_DISTANCE_MIN,
													VoltPerformanceConfig.ATT_FULL_SPEED_DISTANCE_MAX,
													VoltPerformanceConfig.ATT_FULL_SPEED_DISTANCE_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.att.blocks.value", value
											))
											.setDefaultValue(
													VoltPerformanceConfig.ATT_FULL_SPEED_DISTANCE_DEFAULT
											)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setAttFullSpeedDistance,
													PERFORMANCE_CONFIG::getAttFullSpeedDistance
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(ATT_ENABLED)
															&& state.readBooleanOption(ATT_DISTANCE_AWARE),
													ATT_ENABLED,
													ATT_DISTANCE_AWARE
											))
									.addOption(builder.createIntegerOption(ATT_DISTANT_INTERVAL)
											.setName(Component.translatable(
													"sodium-volt.options.performance.att.distant_interval"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.att.distant_interval.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltPerformanceConfig.ATT_DISTANT_INTERVAL_MIN,
													VoltPerformanceConfig.ATT_DISTANT_INTERVAL_MAX,
													1
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.att.ticks.value", value
											))
											.setDefaultValue(
													VoltPerformanceConfig.ATT_DISTANT_INTERVAL_DEFAULT
											)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setAttDistantUpdateInterval,
													PERFORMANCE_CONFIG::getAttDistantUpdateInterval
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(ATT_ENABLED)
															&& state.readBooleanOption(ATT_DISTANCE_AWARE),
													ATT_ENABLED,
													ATT_DISTANCE_AWARE
											))
									.addOption(builder.createIntegerOption(ATT_UNSEEN_KEEPALIVE)
											.setName(Component.translatable(
													"sodium-volt.options.performance.att.unseen_keepalive"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.att.unseen_keepalive.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltPerformanceConfig.ATT_UNSEEN_KEEPALIVE_MIN,
													VoltPerformanceConfig.ATT_UNSEEN_KEEPALIVE_MAX,
													VoltPerformanceConfig.ATT_UNSEEN_KEEPALIVE_STEP
											)
											.setValueFormatter(value -> value == 0
													? Component.translatable(
															"sodium-volt.options.performance.att.keepalive.paused"
													)
													: Component.translatable(
															"sodium-volt.options.performance.att.keepalive.value",
															value
													))
											.setDefaultValue(
													VoltPerformanceConfig.ATT_UNSEEN_KEEPALIVE_DEFAULT
											)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setAttUnseenKeepaliveTicks,
													PERFORMANCE_CONFIG::getAttUnseenKeepaliveTicks
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(ATT_ENABLED)
															&& state.readBooleanOption(ATT_PAUSE_INVISIBLE),
													ATT_ENABLED,
													ATT_PAUSE_INVISIBLE
											))
									.addOption(builder.createIntegerOption(ATT_PER_ATLAS_BUDGET)
											.setName(Component.translatable(
													"sodium-volt.options.performance.att.per_atlas_budget"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.att.per_atlas_budget.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltPerformanceConfig.ATT_PER_ATLAS_BUDGET_MIN,
													VoltPerformanceConfig.ATT_PER_ATLAS_BUDGET_MAX,
													VoltPerformanceConfig.ATT_PER_ATLAS_BUDGET_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.att.animations.value",
													value
											))
											.setDefaultValue(
													VoltPerformanceConfig.ATT_PER_ATLAS_BUDGET_DEFAULT
											)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setAttPerAtlasAnimationBudget,
													PERFORMANCE_CONFIG::getAttPerAtlasAnimationBudget
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(ATT_ENABLED),
													ATT_ENABLED
											)))
							.addOptionGroup(builder.createOptionGroup()
									.setName(Component.translatable(
											"sodium-volt.options.performance.vram.group"
									))
									.addOption(builder.createBooleanOption(VRAM_ENABLED)
											.setName(Component.translatable(
													"sodium-volt.options.performance.vram.enabled"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.vram.enabled.tooltip"
											))
											.setImpact(OptionImpact.VARIES)
											.setDefaultValue(false)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setVramPressureProtectionEnabled,
													PERFORMANCE_CONFIG::isVramPressureProtectionEnabled
											))
									.addOption(vramToggle(
											builder, VRAM_AUTO_BUDGET, "auto_budget", OptionImpact.LOW,
											PERFORMANCE_CONFIG::setVramAutoDetectBudget,
											PERFORMANCE_CONFIG::isVramAutoDetectBudget
									))
									.addOption(vramToggle(
											builder, VRAM_SAFE_PROFILE, "safe_profile", OptionImpact.HIGH,
											PERFORMANCE_CONFIG::setVramApplySafeRenderDistanceProfile,
											PERFORMANCE_CONFIG::isVramApplySafeRenderDistanceProfile
									))
									.addOption(vramToggle(
											builder, VRAM_SPIKE_RESPONSE, "spike_response", OptionImpact.MEDIUM,
											PERFORMANCE_CONFIG::setVramRespondToAllocationSpikes,
											PERFORMANCE_CONFIG::isVramRespondToAllocationSpikes
									))
									.addOption(vramToggle(
											builder, VRAM_RESTORE_QUALITY, "restore_quality", OptionImpact.MEDIUM,
											PERFORMANCE_CONFIG::setVramRestoreQualityAfterRecovery,
											PERFORMANCE_CONFIG::isVramRestoreQualityAfterRecovery
									))
									.addOption(vramToggle(
											builder, VRAM_WARNINGS, "warnings", OptionImpact.LOW,
											PERFORMANCE_CONFIG::setVramShowPressureWarnings,
											PERFORMANCE_CONFIG::isVramShowPressureWarnings
									))
									.addOption(vramToggle(
											builder, VRAM_HEADROOM, "headroom", OptionImpact.LOW,
											PERFORMANCE_CONFIG::setVramAccountForHeadroom,
											PERFORMANCE_CONFIG::isVramAccountForHeadroom
									))
									.addOption(vramToggle(
											builder, VRAM_INSPECTOR_STATS, "inspector_stats", OptionImpact.LOW,
											PERFORMANCE_CONFIG::setVramShowInspectorStatistics,
											PERFORMANCE_CONFIG::isVramShowInspectorStatistics
									))
									.addOption(builder.createIntegerOption(VRAM_MANUAL_BUDGET)
											.setName(Component.translatable(
													"sodium-volt.options.performance.vram.manual_budget"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.vram.manual_budget.tooltip"
											))
											.setImpact(OptionImpact.LOW)
											.setRange(
													VoltPerformanceConfig.VRAM_MANUAL_BUDGET_MIN,
													VoltPerformanceConfig.VRAM_MANUAL_BUDGET_MAX,
													VoltPerformanceConfig.VRAM_MANUAL_BUDGET_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.vram.mib.value", value
											))
											.setDefaultValue(VoltPerformanceConfig.VRAM_MANUAL_BUDGET_DEFAULT)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setVramManualBudgetMib,
													PERFORMANCE_CONFIG::getVramManualBudgetMib
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(VRAM_ENABLED)
															&& !state.readBooleanOption(VRAM_AUTO_BUDGET),
													VRAM_ENABLED,
													VRAM_AUTO_BUDGET
											))
									.addOption(builder.createIntegerOption(VRAM_PROTECTION_THRESHOLD)
											.setName(Component.translatable(
													"sodium-volt.options.performance.vram.protection_threshold"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.vram.protection_threshold.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltPerformanceConfig.VRAM_PROTECTION_THRESHOLD_MIN,
													VoltPerformanceConfig.VRAM_PROTECTION_THRESHOLD_MAX,
													VoltPerformanceConfig.VRAM_PROTECTION_THRESHOLD_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.vram.percent.value", value
											))
											.setDefaultValue(
													VoltPerformanceConfig.VRAM_PROTECTION_THRESHOLD_DEFAULT
											)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setVramProtectionThresholdPercent,
													PERFORMANCE_CONFIG::getVramProtectionThresholdPercent
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(VRAM_ENABLED), VRAM_ENABLED
											))
									.addOption(builder.createIntegerOption(VRAM_CRITICAL_THRESHOLD)
											.setName(Component.translatable(
													"sodium-volt.options.performance.vram.critical_threshold"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.vram.critical_threshold.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltPerformanceConfig.VRAM_CRITICAL_THRESHOLD_MIN,
													VoltPerformanceConfig.VRAM_CRITICAL_THRESHOLD_MAX,
													1
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.vram.percent.value", value
											))
											.setDefaultValue(
													VoltPerformanceConfig.VRAM_CRITICAL_THRESHOLD_DEFAULT
											)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setVramCriticalThresholdPercent,
													PERFORMANCE_CONFIG::getVramCriticalThresholdPercent
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(VRAM_ENABLED), VRAM_ENABLED
											))
									.addOption(builder.createIntegerOption(VRAM_HEADROOM_PERCENT)
											.setName(Component.translatable(
													"sodium-volt.options.performance.vram.headroom_percent"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.vram.headroom_percent.tooltip"
											))
											.setImpact(OptionImpact.MEDIUM)
											.setRange(
													VoltPerformanceConfig.VRAM_SAFETY_HEADROOM_MIN,
													VoltPerformanceConfig.VRAM_SAFETY_HEADROOM_MAX,
													VoltPerformanceConfig.VRAM_SAFETY_HEADROOM_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.vram.percent.value", value
											))
											.setDefaultValue(VoltPerformanceConfig.VRAM_SAFETY_HEADROOM_DEFAULT)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setVramSafetyHeadroomPercent,
													PERFORMANCE_CONFIG::getVramSafetyHeadroomPercent
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(VRAM_ENABLED)
															&& state.readBooleanOption(VRAM_HEADROOM),
													VRAM_ENABLED,
													VRAM_HEADROOM
											))
									.addOption(builder.createIntegerOption(VRAM_FIXED_RESERVE)
											.setName(Component.translatable(
													"sodium-volt.options.performance.vram.fixed_reserve"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.vram.fixed_reserve.tooltip"
											))
											.setImpact(OptionImpact.MEDIUM)
											.setRange(
													VoltPerformanceConfig.VRAM_FIXED_RESERVE_MIN,
													VoltPerformanceConfig.VRAM_FIXED_RESERVE_MAX,
													VoltPerformanceConfig.VRAM_FIXED_RESERVE_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.vram.mib.value", value
											))
											.setDefaultValue(VoltPerformanceConfig.VRAM_FIXED_RESERVE_DEFAULT)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setVramFixedReserveMib,
													PERFORMANCE_CONFIG::getVramFixedReserveMib
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(VRAM_ENABLED)
															&& state.readBooleanOption(VRAM_HEADROOM),
													VRAM_ENABLED,
													VRAM_HEADROOM
											))
									.addOption(builder.createIntegerOption(VRAM_MIN_RENDER_DISTANCE)
											.setName(Component.translatable(
													"sodium-volt.options.performance.vram.min_render_distance"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.vram.min_render_distance.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltPerformanceConfig.VRAM_MIN_SAFE_RENDER_DISTANCE_MIN,
													VoltPerformanceConfig.VRAM_MIN_SAFE_RENDER_DISTANCE_MAX,
													1
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.vram.chunks.value", value
											))
											.setDefaultValue(
													VoltPerformanceConfig.VRAM_MIN_SAFE_RENDER_DISTANCE_DEFAULT
											)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setVramMinimumSafeRenderDistance,
													PERFORMANCE_CONFIG::getVramMinimumSafeRenderDistance
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(VRAM_ENABLED)
															&& state.readBooleanOption(VRAM_SAFE_PROFILE),
													VRAM_ENABLED,
													VRAM_SAFE_PROFILE
											))
									.addOption(builder.createIntegerOption(VRAM_SAMPLE_INTERVAL)
											.setName(Component.translatable(
													"sodium-volt.options.performance.vram.sample_interval"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.vram.sample_interval.tooltip"
											))
											.setImpact(OptionImpact.LOW)
											.setRange(
													VoltPerformanceConfig.VRAM_SAMPLE_INTERVAL_MIN,
													VoltPerformanceConfig.VRAM_SAMPLE_INTERVAL_MAX,
													1
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.seconds.value", value
											))
											.setDefaultValue(VoltPerformanceConfig.VRAM_SAMPLE_INTERVAL_DEFAULT)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setVramSampleIntervalSeconds,
													PERFORMANCE_CONFIG::getVramSampleIntervalSeconds
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(VRAM_ENABLED), VRAM_ENABLED
											))
									.addOption(builder.createIntegerOption(VRAM_SUSTAINED_SAMPLES)
											.setName(Component.translatable(
													"sodium-volt.options.performance.vram.sustained_samples"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.vram.sustained_samples.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltPerformanceConfig.VRAM_SUSTAINED_SAMPLES_MIN,
													VoltPerformanceConfig.VRAM_SUSTAINED_SAMPLES_MAX,
													1
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.vram.samples.value", value
											))
											.setDefaultValue(VoltPerformanceConfig.VRAM_SUSTAINED_SAMPLES_DEFAULT)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setVramSustainedSamples,
													PERFORMANCE_CONFIG::getVramSustainedSamples
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(VRAM_ENABLED), VRAM_ENABLED
											))
									.addOption(builder.createIntegerOption(VRAM_STEP_INTERVAL)
											.setName(Component.translatable(
													"sodium-volt.options.performance.vram.step_interval"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.vram.step_interval.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltPerformanceConfig.VRAM_RENDER_STEP_INTERVAL_MIN,
													VoltPerformanceConfig.VRAM_RENDER_STEP_INTERVAL_MAX,
													1
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.seconds.value", value
											))
											.setDefaultValue(
													VoltPerformanceConfig.VRAM_RENDER_STEP_INTERVAL_DEFAULT
											)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setVramRenderDistanceStepIntervalSeconds,
													PERFORMANCE_CONFIG::getVramRenderDistanceStepIntervalSeconds
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(VRAM_ENABLED)
															&& state.readBooleanOption(VRAM_SAFE_PROFILE),
													VRAM_ENABLED,
													VRAM_SAFE_PROFILE
											))
									.addOption(builder.createIntegerOption(VRAM_RECOVERY_DELAY)
											.setName(Component.translatable(
													"sodium-volt.options.performance.vram.recovery_delay"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.vram.recovery_delay.tooltip"
											))
											.setImpact(OptionImpact.MEDIUM)
											.setRange(
													VoltPerformanceConfig.VRAM_RECOVERY_DELAY_MIN,
													VoltPerformanceConfig.VRAM_RECOVERY_DELAY_MAX,
													VoltPerformanceConfig.VRAM_RECOVERY_DELAY_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.seconds.value", value
											))
											.setDefaultValue(VoltPerformanceConfig.VRAM_RECOVERY_DELAY_DEFAULT)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setVramRecoveryDelaySeconds,
													PERFORMANCE_CONFIG::getVramRecoveryDelaySeconds
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(VRAM_ENABLED),
													VRAM_ENABLED
											))
									.addOption(builder.createIntegerOption(VRAM_SPIKE_MIB)
											.setName(Component.translatable(
													"sodium-volt.options.performance.vram.spike_mib"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.performance.vram.spike_mib.tooltip"
											))
											.setImpact(OptionImpact.MEDIUM)
											.setRange(
													VoltPerformanceConfig.VRAM_ALLOCATION_SPIKE_MIN,
													VoltPerformanceConfig.VRAM_ALLOCATION_SPIKE_MAX,
													VoltPerformanceConfig.VRAM_ALLOCATION_SPIKE_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.performance.vram.mib.value", value
											))
											.setDefaultValue(VoltPerformanceConfig.VRAM_ALLOCATION_SPIKE_DEFAULT)
											.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
											.setBinding(
													PERFORMANCE_CONFIG::setVramLargeAllocationSpikeMib,
													PERFORMANCE_CONFIG::getVramLargeAllocationSpikeMib
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(VRAM_ENABLED)
															&& state.readBooleanOption(VRAM_SPIKE_RESPONSE),
													VRAM_ENABLED,
													VRAM_SPIKE_RESPONSE
											))))
					.addPage(builder.createOptionPage()
							.setName(Component.translatable("sodium-volt.options.smart_fps.page"))
							.addOptionGroup(builder.createOptionGroup()
									.setName(Component.translatable("sodium-volt.options.smart_fps.group"))
									.addOption(builder.createBooleanOption(SMART_FPS_ENABLED)
											.setName(Component.translatable(
													"sodium-volt.options.smart_fps.enabled"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.smart_fps.enabled.tooltip"
											))
											.setImpact(OptionImpact.VARIES)
											.setDefaultValue(false)
											.setStorageHandler(SMART_FPS_STORAGE_HANDLER)
											.setBinding(
													SMART_FPS_CONFIG::setSmartFpsEnabled,
													SMART_FPS_CONFIG::isSmartFpsEnabled
											))
									.addOption(builder.createIntegerOption(SMART_FPS_MINIMIZED_TARGET)
											.setName(Component.translatable(
													"sodium-volt.options.smart_fps.minimized_target"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.smart_fps.minimized_target.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													SmartFpsConfig.MINIMIZED_TARGET_MIN,
													SmartFpsConfig.MINIMIZED_TARGET_MAX,
													1
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.smart_fps.fps.value", value
											))
											.setDefaultValue(SmartFpsConfig.MINIMIZED_TARGET_DEFAULT)
											.setStorageHandler(SMART_FPS_STORAGE_HANDLER)
											.setBinding(
													SMART_FPS_CONFIG::setMinimizedTargetFps,
													SMART_FPS_CONFIG::getMinimizedTargetFps
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(SMART_FPS_ENABLED)
															&& state.readBooleanOption(
																	SMART_FPS_THROTTLE_MINIMIZED
															),
													SMART_FPS_ENABLED,
													SMART_FPS_THROTTLE_MINIMIZED
											))
									.addOption(smartFpsToggle(
											builder,
											SMART_FPS_THROTTLE_MINIMIZED,
											"throttle_minimized",
											OptionImpact.HIGH,
											SMART_FPS_CONFIG::setThrottleWhenMinimized,
											SMART_FPS_CONFIG::isThrottleWhenMinimized
									))
									.addOption(smartFpsToggle(
											builder,
											SMART_FPS_THROTTLE_UNFOCUSED,
											"throttle_unfocused",
											OptionImpact.HIGH,
											SMART_FPS_CONFIG::setThrottleWhenUnfocused,
											SMART_FPS_CONFIG::isThrottleWhenUnfocused
									))
									.addOption(builder.createIntegerOption(SMART_FPS_UNFOCUSED_TARGET)
											.setName(Component.translatable(
													"sodium-volt.options.smart_fps.unfocused_target"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.smart_fps.unfocused_target.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													SmartFpsConfig.UNFOCUSED_TARGET_MIN,
													SmartFpsConfig.UNFOCUSED_TARGET_MAX,
													SmartFpsConfig.UNFOCUSED_TARGET_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.smart_fps.fps.value", value
											))
											.setDefaultValue(SmartFpsConfig.UNFOCUSED_TARGET_DEFAULT)
											.setStorageHandler(SMART_FPS_STORAGE_HANDLER)
											.setBinding(
													SMART_FPS_CONFIG::setUnfocusedTargetFps,
													SMART_FPS_CONFIG::getUnfocusedTargetFps
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(SMART_FPS_ENABLED)
															&& state.readBooleanOption(
																	SMART_FPS_THROTTLE_UNFOCUSED
															),
													SMART_FPS_ENABLED,
													SMART_FPS_THROTTLE_UNFOCUSED
											))
									.addOption(builder.createIntegerOption(SMART_FPS_BACKGROUND_DELAY)
											.setName(Component.translatable(
													"sodium-volt.options.smart_fps.background_delay"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.smart_fps.background_delay.tooltip"
											))
											.setImpact(OptionImpact.LOW)
											.setRange(
													SmartFpsConfig.BACKGROUND_DELAY_MIN,
													SmartFpsConfig.BACKGROUND_DELAY_MAX,
													1
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.smart_fps.seconds.value", value
											))
											.setDefaultValue(SmartFpsConfig.BACKGROUND_DELAY_DEFAULT)
											.setStorageHandler(SMART_FPS_STORAGE_HANDLER)
											.setBinding(
													SMART_FPS_CONFIG::setBackgroundActivationDelaySeconds,
													SMART_FPS_CONFIG::getBackgroundActivationDelaySeconds
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(SMART_FPS_ENABLED)
															&& state.readBooleanOption(
																	SMART_FPS_THROTTLE_UNFOCUSED
															),
													SMART_FPS_ENABLED,
													SMART_FPS_THROTTLE_UNFOCUSED
											))
									.addOption(smartFpsToggle(
											builder,
											SMART_FPS_BATTERY_MODE,
											"battery_mode",
											OptionImpact.HIGH,
											SMART_FPS_CONFIG::setBatteryMode,
											SMART_FPS_CONFIG::isBatteryMode
									))
									.addOption(builder.createIntegerOption(SMART_FPS_BATTERY_TARGET)
											.setName(Component.translatable(
													"sodium-volt.options.smart_fps.battery_target"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.smart_fps.battery_target.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													SmartFpsConfig.BATTERY_TARGET_MIN,
													SmartFpsConfig.BATTERY_TARGET_MAX,
													SmartFpsConfig.BATTERY_TARGET_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.smart_fps.fps.value", value
											))
											.setDefaultValue(SmartFpsConfig.BATTERY_TARGET_DEFAULT)
											.setStorageHandler(SMART_FPS_STORAGE_HANDLER)
											.setBinding(
													SMART_FPS_CONFIG::setBatteryTargetFps,
													SMART_FPS_CONFIG::getBatteryTargetFps
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(SMART_FPS_ENABLED)
															&& state.readBooleanOption(SMART_FPS_BATTERY_MODE),
													SMART_FPS_ENABLED,
													SMART_FPS_BATTERY_MODE
											))
									.addOption(builder.createBooleanOption(SMART_FPS_BYPASS_CHARGING)
											.setName(Component.translatable(
													"sodium-volt.options.smart_fps.bypass_charging"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.smart_fps.bypass_charging.tooltip"
											))
											.setImpact(OptionImpact.MEDIUM)
											.setDefaultValue(true)
											.setStorageHandler(SMART_FPS_STORAGE_HANDLER)
											.setBinding(
													SMART_FPS_CONFIG::setBypassBatteryLimitWhileCharging,
													SMART_FPS_CONFIG::isBypassBatteryLimitWhileCharging
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(SMART_FPS_ENABLED)
															&& state.readBooleanOption(SMART_FPS_BATTERY_MODE),
													SMART_FPS_ENABLED,
													SMART_FPS_BATTERY_MODE
											))
									.addOption(builder.createBooleanOption(SMART_FPS_LOW_BATTERY)
											.setName(Component.translatable(
													"sodium-volt.options.smart_fps.low_battery"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.smart_fps.low_battery.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setDefaultValue(true)
											.setStorageHandler(SMART_FPS_STORAGE_HANDLER)
											.setBinding(
													SMART_FPS_CONFIG::setLowBatteryProtection,
													SMART_FPS_CONFIG::isLowBatteryProtection
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(SMART_FPS_ENABLED)
															&& state.readBooleanOption(SMART_FPS_BATTERY_MODE),
													SMART_FPS_ENABLED,
													SMART_FPS_BATTERY_MODE
											))
									.addOption(builder.createIntegerOption(SMART_FPS_LOW_BATTERY_THRESHOLD)
											.setName(Component.translatable(
													"sodium-volt.options.smart_fps.low_battery_threshold"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.smart_fps.low_battery_threshold.tooltip"
											))
											.setImpact(OptionImpact.MEDIUM)
											.setRange(
													SmartFpsConfig.LOW_BATTERY_THRESHOLD_MIN,
													SmartFpsConfig.LOW_BATTERY_THRESHOLD_MAX,
													SmartFpsConfig.LOW_BATTERY_THRESHOLD_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.smart_fps.percent.value", value
											))
											.setDefaultValue(
													SmartFpsConfig.LOW_BATTERY_THRESHOLD_DEFAULT
											)
											.setStorageHandler(SMART_FPS_STORAGE_HANDLER)
											.setBinding(
													SMART_FPS_CONFIG::setLowBatteryThresholdPercent,
													SMART_FPS_CONFIG::getLowBatteryThresholdPercent
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(SMART_FPS_ENABLED)
															&& state.readBooleanOption(SMART_FPS_BATTERY_MODE)
															&& state.readBooleanOption(SMART_FPS_LOW_BATTERY),
													SMART_FPS_ENABLED,
													SMART_FPS_BATTERY_MODE,
													SMART_FPS_LOW_BATTERY
											))
									.addOption(builder.createIntegerOption(SMART_FPS_LOW_BATTERY_TARGET)
											.setName(Component.translatable(
													"sodium-volt.options.smart_fps.low_battery_target"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.smart_fps.low_battery_target.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													SmartFpsConfig.LOW_BATTERY_TARGET_MIN,
													SmartFpsConfig.LOW_BATTERY_TARGET_MAX,
													SmartFpsConfig.LOW_BATTERY_TARGET_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.smart_fps.fps.value", value
											))
											.setDefaultValue(SmartFpsConfig.LOW_BATTERY_TARGET_DEFAULT)
											.setStorageHandler(SMART_FPS_STORAGE_HANDLER)
											.setBinding(
													SMART_FPS_CONFIG::setLowBatteryTargetFps,
													SMART_FPS_CONFIG::getLowBatteryTargetFps
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(SMART_FPS_ENABLED)
															&& state.readBooleanOption(SMART_FPS_BATTERY_MODE)
															&& state.readBooleanOption(SMART_FPS_LOW_BATTERY),
													SMART_FPS_ENABLED,
													SMART_FPS_BATTERY_MODE,
													SMART_FPS_LOW_BATTERY
											))
									.addOption(builder.createIntegerOption(SMART_FPS_POWER_POLL_INTERVAL)
											.setName(Component.translatable(
													"sodium-volt.options.smart_fps.power_poll_interval"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.smart_fps.power_poll_interval.tooltip"
											))
											.setImpact(OptionImpact.LOW)
											.setRange(
													SmartFpsConfig.POWER_POLL_INTERVAL_MIN,
													SmartFpsConfig.POWER_POLL_INTERVAL_MAX,
													SmartFpsConfig.POWER_POLL_INTERVAL_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.smart_fps.seconds.value", value
											))
											.setDefaultValue(SmartFpsConfig.POWER_POLL_INTERVAL_DEFAULT)
											.setStorageHandler(SMART_FPS_STORAGE_HANDLER)
											.setBinding(
													SMART_FPS_CONFIG::setPowerPollIntervalSeconds,
													SMART_FPS_CONFIG::getPowerPollIntervalSeconds
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(SMART_FPS_ENABLED)
															&& state.readBooleanOption(SMART_FPS_BATTERY_MODE),
													SMART_FPS_ENABLED,
													SMART_FPS_BATTERY_MODE
											))
									.addOption(smartFpsToggle(
											builder,
											SMART_FPS_NOTIFICATIONS,
											"notifications",
											OptionImpact.LOW,
											SMART_FPS_CONFIG::setShowStatusNotifications,
											SMART_FPS_CONFIG::isShowStatusNotifications
									))
									.addOption(smartFpsToggle(
											builder,
											SMART_FPS_INSPECTOR_STATS,
											"inspector_stats",
											OptionImpact.LOW,
											SMART_FPS_CONFIG::setShowInspectorStatistics,
											SMART_FPS_CONFIG::isShowInspectorStatistics
									))))
					.addPage(builder.createOptionPage()
							.setName(Component.translatable("sodium-volt.options.recovery.page"))
							.addOptionGroup(builder.createOptionGroup()
									.setName(Component.translatable("sodium-volt.options.recovery.group"))
									.addOption(builder.createBooleanOption(RECOVERY_ENABLED)
											.setName(Component.translatable(
													"sodium-volt.options.recovery.enabled"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.recovery.enabled.tooltip"
											))
											.setImpact(OptionImpact.VARIES)
											.setDefaultValue(false)
											.setStorageHandler(RECOVERY_STORAGE_HANDLER)
											.setBinding(
													RECOVERY_CONFIG::setVoltRecoveryEnabled,
													RECOVERY_CONFIG::isVoltRecoveryEnabled
											))
									.addOption(recoveryToggle(
											builder,
											RECOVERY_DETECT_UNCLEAN,
											"detect_unclean",
											OptionImpact.LOW,
											RECOVERY_CONFIG::setDetectUncleanSessions,
											RECOVERY_CONFIG::isDetectUncleanSessions
									))
									.addOption(builder.createBooleanOption(RECOVERY_AUTOMATIC)
											.setName(Component.translatable(
													"sodium-volt.options.recovery.automatic"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.recovery.automatic.tooltip"
											))
											.setImpact(OptionImpact.VARIES)
											.setDefaultValue(true)
											.setStorageHandler(RECOVERY_STORAGE_HANDLER)
											.setBinding(
													RECOVERY_CONFIG::setAutomaticSafeMode,
													RECOVERY_CONFIG::isAutomaticSafeMode
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(RECOVERY_ENABLED)
															&& state.readBooleanOption(
																	RECOVERY_DETECT_UNCLEAN
															),
													RECOVERY_ENABLED,
													RECOVERY_DETECT_UNCLEAN
											))
									.addOption(builder.createBooleanOption(RECOVERY_FORCE_NEXT)
											.setName(Component.translatable(
													"sodium-volt.options.recovery.force_next"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.recovery.force_next.tooltip"
											))
											.setImpact(OptionImpact.VARIES)
											.setDefaultValue(false)
											.setStorageHandler(RECOVERY_STORAGE_HANDLER)
											.setBinding(
													RECOVERY_CONFIG::setForceSafeModeNextLaunch,
													RECOVERY_CONFIG::isForceSafeModeNextLaunch
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(RECOVERY_ENABLED),
													RECOVERY_ENABLED
											))
									.addOption(builder.createIntegerOption(RECOVERY_STREAK_THRESHOLD)
											.setName(Component.translatable(
													"sodium-volt.options.recovery.streak_threshold"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.recovery.streak_threshold.tooltip"
											))
											.setImpact(OptionImpact.LOW)
											.setRange(
													VoltRecoveryConfig.CRASH_STREAK_MIN,
													VoltRecoveryConfig.CRASH_STREAK_MAX,
													1
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.recovery.count.value", value
											))
											.setDefaultValue(VoltRecoveryConfig.CRASH_STREAK_DEFAULT)
											.setStorageHandler(RECOVERY_STORAGE_HANDLER)
											.setBinding(
													RECOVERY_CONFIG::setCrashStreakThreshold,
													RECOVERY_CONFIG::getCrashStreakThreshold
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(RECOVERY_ENABLED)
															&& state.readBooleanOption(
																	RECOVERY_DETECT_UNCLEAN
															)
															&& state.readBooleanOption(RECOVERY_AUTOMATIC),
													RECOVERY_ENABLED,
													RECOVERY_DETECT_UNCLEAN,
													RECOVERY_AUTOMATIC
											))
									.addOption(builder.createIntegerOption(RECOVERY_MAXIMUM_ATTEMPTS)
											.setName(Component.translatable(
													"sodium-volt.options.recovery.maximum_attempts"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.recovery.maximum_attempts.tooltip"
											))
											.setImpact(OptionImpact.LOW)
											.setRange(
													VoltRecoveryConfig.MAXIMUM_ATTEMPTS_MIN,
													VoltRecoveryConfig.MAXIMUM_ATTEMPTS_MAX,
													1
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.recovery.count.value", value
											))
											.setDefaultValue(
													VoltRecoveryConfig.MAXIMUM_ATTEMPTS_DEFAULT
											)
											.setStorageHandler(RECOVERY_STORAGE_HANDLER)
											.setBinding(
													RECOVERY_CONFIG::setMaximumRecoveryAttempts,
													RECOVERY_CONFIG::getMaximumRecoveryAttempts
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(RECOVERY_ENABLED),
													RECOVERY_ENABLED
											))
									.addOption(recoveryToggle(
											builder,
											RECOVERY_SAFE_PROFILE,
											"safe_profile",
											OptionImpact.HIGH,
											RECOVERY_CONFIG::setApplySafeGraphicsProfile,
											RECOVERY_CONFIG::isApplySafeGraphicsProfile
									))
									.addOption(builder.createIntegerOption(RECOVERY_RENDER_DISTANCE)
											.setName(Component.translatable(
													"sodium-volt.options.recovery.render_distance"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.recovery.render_distance.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltRecoveryConfig.SAFE_RENDER_DISTANCE_MIN,
													VoltRecoveryConfig.SAFE_RENDER_DISTANCE_MAX,
													1
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.recovery.chunks.value", value
											))
											.setDefaultValue(
													VoltRecoveryConfig.SAFE_RENDER_DISTANCE_DEFAULT
											)
											.setStorageHandler(RECOVERY_STORAGE_HANDLER)
											.setBinding(
													RECOVERY_CONFIG::setSafeRenderDistance,
													RECOVERY_CONFIG::getSafeRenderDistance
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(RECOVERY_ENABLED)
															&& state.readBooleanOption(
																	RECOVERY_SAFE_PROFILE
															),
													RECOVERY_ENABLED,
													RECOVERY_SAFE_PROFILE
											))
									.addOption(builder.createIntegerOption(RECOVERY_ENTITY_DISTANCE)
											.setName(Component.translatable(
													"sodium-volt.options.recovery.entity_distance"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.recovery.entity_distance.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltRecoveryConfig.SAFE_ENTITY_DISTANCE_MIN,
													VoltRecoveryConfig.SAFE_ENTITY_DISTANCE_MAX,
													VoltRecoveryConfig.SAFE_ENTITY_DISTANCE_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.recovery.percent.value", value
											))
											.setDefaultValue(
													VoltRecoveryConfig.SAFE_ENTITY_DISTANCE_DEFAULT
											)
											.setStorageHandler(RECOVERY_STORAGE_HANDLER)
											.setBinding(
													RECOVERY_CONFIG::setSafeEntityDistancePercent,
													RECOVERY_CONFIG::getSafeEntityDistancePercent
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(RECOVERY_ENABLED)
															&& state.readBooleanOption(
																	RECOVERY_SAFE_PROFILE
															),
													RECOVERY_ENABLED,
													RECOVERY_SAFE_PROFILE
											))
									.addOption(builder.createBooleanOption(RECOVERY_REDUCE_GRAPHICS)
											.setName(Component.translatable(
													"sodium-volt.options.recovery.reduce_graphics"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.recovery.reduce_graphics.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setDefaultValue(true)
											.setStorageHandler(RECOVERY_STORAGE_HANDLER)
											.setBinding(
													RECOVERY_CONFIG::setReduceExpensiveGraphics,
													RECOVERY_CONFIG::isReduceExpensiveGraphics
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(RECOVERY_ENABLED)
															&& state.readBooleanOption(
																	RECOVERY_SAFE_PROFILE
															),
													RECOVERY_ENABLED,
													RECOVERY_SAFE_PROFILE
											))
									.addOption(recoveryToggle(
											builder,
											RECOVERY_LIMIT_FPS,
											"limit_fps",
											OptionImpact.HIGH,
											RECOVERY_CONFIG::setLimitFpsDuringRecovery,
											RECOVERY_CONFIG::isLimitFpsDuringRecovery
									))
									.addOption(builder.createIntegerOption(RECOVERY_FPS_CAP)
											.setName(Component.translatable(
													"sodium-volt.options.recovery.fps_cap"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.recovery.fps_cap.tooltip"
											))
											.setImpact(OptionImpact.HIGH)
											.setRange(
													VoltRecoveryConfig.RECOVERY_FPS_MIN,
													VoltRecoveryConfig.RECOVERY_FPS_MAX,
													VoltRecoveryConfig.RECOVERY_FPS_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.recovery.fps.value", value
											))
											.setDefaultValue(VoltRecoveryConfig.RECOVERY_FPS_DEFAULT)
											.setStorageHandler(RECOVERY_STORAGE_HANDLER)
											.setBinding(
													RECOVERY_CONFIG::setRecoveryFpsCap,
													RECOVERY_CONFIG::getRecoveryFpsCap
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(RECOVERY_ENABLED)
															&& state.readBooleanOption(
																	RECOVERY_LIMIT_FPS
															),
													RECOVERY_ENABLED,
													RECOVERY_LIMIT_FPS
											))
									.addOption(recoveryToggle(
											builder,
											RECOVERY_SUSPEND_APC,
											"suspend_apc",
											OptionImpact.VARIES,
											RECOVERY_CONFIG::setSuspendAdaptiveController,
											RECOVERY_CONFIG::isSuspendAdaptiveController
									))
									.addOption(recoveryToggle(
											builder,
											RECOVERY_RESTORE_STABLE,
											"restore_stable",
											OptionImpact.VARIES,
											RECOVERY_CONFIG::setRestoreOwnedSettingsAfterStableSession,
											RECOVERY_CONFIG::isRestoreOwnedSettingsAfterStableSession
									))
									.addOption(builder.createIntegerOption(RECOVERY_STABLE_DURATION)
											.setName(Component.translatable(
													"sodium-volt.options.recovery.stable_duration"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.recovery.stable_duration.tooltip"
											))
											.setImpact(OptionImpact.LOW)
											.setRange(
													VoltRecoveryConfig.STABLE_DURATION_MIN,
													VoltRecoveryConfig.STABLE_DURATION_MAX,
													VoltRecoveryConfig.STABLE_DURATION_STEP
											)
											.setValueFormatter(value -> Component.translatable(
													"sodium-volt.options.recovery.seconds.value", value
											))
											.setDefaultValue(
													VoltRecoveryConfig.STABLE_DURATION_DEFAULT
											)
											.setStorageHandler(RECOVERY_STORAGE_HANDLER)
											.setBinding(
													RECOVERY_CONFIG::setStableSessionDurationSeconds,
													RECOVERY_CONFIG::getStableSessionDurationSeconds
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(RECOVERY_ENABLED)
															&& state.readBooleanOption(
																	RECOVERY_RESTORE_STABLE
															),
													RECOVERY_ENABLED,
													RECOVERY_RESTORE_STABLE
											))
									.addOption(recoveryToggle(
											builder,
											RECOVERY_NOTIFICATIONS,
											"notifications",
											OptionImpact.LOW,
											RECOVERY_CONFIG::setShowRecoveryNotifications,
											RECOVERY_CONFIG::isShowRecoveryNotifications
									))
									.addOption(recoveryToggle(
											builder,
											RECOVERY_REPORT,
											"report",
											OptionImpact.LOW,
											RECOVERY_CONFIG::setWriteSanitizedLocalRecoveryReport,
											RECOVERY_CONFIG::isWriteSanitizedLocalRecoveryReport
									))
									.addOption(recoveryToggle(
											builder,
											RECOVERY_INSPECTOR_STATS,
											"inspector_stats",
											OptionImpact.LOW,
											RECOVERY_CONFIG::setShowRecoveryStatsInInspector,
											RECOVERY_CONFIG::isShowRecoveryStatsInInspector
									)))
							.addOptionGroup(builder.createOptionGroup()
									.setName(Component.translatable(
											"sodium-volt.options.watchdog.group"
									))
									.addOption(builder.createBooleanOption(WATCHDOG_ENABLED)
											.setName(Component.translatable(
													"sodium-volt.options.watchdog.enabled"
											))
											.setTooltip(Component.translatable(
													"sodium-volt.options.watchdog.enabled.tooltip"
											))
											.setImpact(OptionImpact.LOW)
											.setDefaultValue(false)
											.setStorageHandler(WATCHDOG_STORAGE_HANDLER)
											.setBinding(
													WATCHDOG_CONFIG::setGpuTimeoutWatchdogEnabled,
													WATCHDOG_CONFIG::isGpuTimeoutWatchdogEnabled
											))
									.addOption(watchdogIntegerOption(
											builder,
											WATCHDOG_WARNING_THRESHOLD,
											"warning_threshold",
											GpuWatchdogConfig.WARNING_THRESHOLD_MIN,
											GpuWatchdogConfig.WARNING_THRESHOLD_MAX,
											1,
											GpuWatchdogConfig.WARNING_THRESHOLD_DEFAULT,
											"seconds.value",
											WATCHDOG_CONFIG::setWarningStallThresholdSeconds,
											WATCHDOG_CONFIG::getWarningStallThresholdSeconds
									))
									.addOption(watchdogIntegerOption(
											builder,
											WATCHDOG_CRITICAL_THRESHOLD,
											"critical_threshold",
											GpuWatchdogConfig.CRITICAL_THRESHOLD_MIN,
											GpuWatchdogConfig.CRITICAL_THRESHOLD_MAX,
											1,
											GpuWatchdogConfig.CRITICAL_THRESHOLD_DEFAULT,
											"seconds.value",
											WATCHDOG_CONFIG::setCriticalTimeoutThresholdSeconds,
											WATCHDOG_CONFIG::getCriticalTimeoutThresholdSeconds
									))
									.addOption(watchdogIntegerOption(
											builder,
											WATCHDOG_CONFIRMATIONS,
											"confirmations",
											GpuWatchdogConfig.CONFIRMATION_COUNT_MIN,
											GpuWatchdogConfig.CONFIRMATION_COUNT_MAX,
											1,
											GpuWatchdogConfig.CONFIRMATION_COUNT_DEFAULT,
											"count.value",
											WATCHDOG_CONFIG::setCriticalConfirmationCount,
											WATCHDOG_CONFIG::getCriticalConfirmationCount
									))
									.addOption(watchdogIntegerOption(
											builder,
											WATCHDOG_STARTUP_GRACE,
											"startup_grace",
											GpuWatchdogConfig.STARTUP_GRACE_MIN,
											GpuWatchdogConfig.STARTUP_GRACE_MAX,
											GpuWatchdogConfig.STARTUP_GRACE_STEP,
											GpuWatchdogConfig.STARTUP_GRACE_DEFAULT,
											"seconds.value",
											WATCHDOG_CONFIG::setStartupWorldGraceSeconds,
											WATCHDOG_CONFIG::getStartupWorldGraceSeconds
									))
									.addOption(watchdogIntegerOption(
											builder,
											WATCHDOG_RELOAD_GRACE,
											"reload_grace",
											GpuWatchdogConfig.RELOAD_GRACE_MIN,
											GpuWatchdogConfig.RELOAD_GRACE_MAX,
											GpuWatchdogConfig.RELOAD_GRACE_STEP,
											GpuWatchdogConfig.RELOAD_GRACE_DEFAULT,
											"seconds.value",
											WATCHDOG_CONFIG::setResourceReloadGraceSeconds,
											WATCHDOG_CONFIG::getResourceReloadGraceSeconds
									))
									.addOption(watchdogToggle(
											builder,
											WATCHDOG_IGNORE_PAUSED,
											"ignore_paused",
											OptionImpact.LOW,
											WATCHDOG_CONFIG::setIgnorePausedLoading,
											WATCHDOG_CONFIG::isIgnorePausedLoading
									))
									.addOption(watchdogToggle(
											builder,
											WATCHDOG_IGNORE_UNFOCUSED,
											"ignore_unfocused",
											OptionImpact.LOW,
											WATCHDOG_CONFIG::setIgnoreUnfocusedMinimized,
											WATCHDOG_CONFIG::isIgnoreUnfocusedMinimized
									))
									.addOption(watchdogIntegerOption(
											builder,
											WATCHDOG_SAMPLE_INTERVAL,
											"sample_interval",
											GpuWatchdogConfig.SAMPLE_INTERVAL_MIN,
											GpuWatchdogConfig.SAMPLE_INTERVAL_MAX,
											GpuWatchdogConfig.SAMPLE_INTERVAL_STEP,
											GpuWatchdogConfig.SAMPLE_INTERVAL_DEFAULT,
											"milliseconds.value",
											WATCHDOG_CONFIG::setSampleIntervalMillis,
											WATCHDOG_CONFIG::getSampleIntervalMillis
									))
									.addOption(watchdogIntegerOption(
											builder,
											WATCHDOG_COOLDOWN,
											"cooldown",
											GpuWatchdogConfig.COOLDOWN_MIN,
											GpuWatchdogConfig.COOLDOWN_MAX,
											GpuWatchdogConfig.COOLDOWN_STEP,
											GpuWatchdogConfig.COOLDOWN_DEFAULT,
											"seconds.value",
											WATCHDOG_CONFIG::setIncidentCooldownSeconds,
											WATCHDOG_CONFIG::getIncidentCooldownSeconds
									))
									.addOption(watchdogIntegerOption(
											builder,
											WATCHDOG_MAXIMUM_INCIDENTS,
											"maximum_incidents",
											GpuWatchdogConfig.MAXIMUM_INCIDENTS_MIN,
											GpuWatchdogConfig.MAXIMUM_INCIDENTS_MAX,
											1,
											GpuWatchdogConfig.MAXIMUM_INCIDENTS_DEFAULT,
											"count.value",
											WATCHDOG_CONFIG::setMaximumIncidentsPerSession,
											WATCHDOG_CONFIG::getMaximumIncidentsPerSession
									))
									.addOption(watchdogToggle(
											builder,
											WATCHDOG_ARM_RECOVERY,
											"arm_recovery",
											OptionImpact.VARIES,
											WATCHDOG_CONFIG::setArmRecoveryNextLaunch,
											WATCHDOG_CONFIG::isArmRecoveryNextLaunch
									))
									.addOption(watchdogToggle(
											builder,
											WATCHDOG_NOTIFICATIONS,
											"notifications",
											OptionImpact.LOW,
											WATCHDOG_CONFIG::setShowTransitionNotifications,
											WATCHDOG_CONFIG::isShowTransitionNotifications
									))
									.addOption(watchdogToggle(
											builder,
											WATCHDOG_REPORT,
											"report",
											OptionImpact.LOW,
											WATCHDOG_CONFIG::setWriteSanitizedIncidentReport,
											WATCHDOG_CONFIG::isWriteSanitizedIncidentReport
									))
									.addOption(watchdogToggle(
											builder,
											WATCHDOG_INSPECTOR_STATS,
											"inspector_stats",
											OptionImpact.LOW,
											WATCHDOG_CONFIG::setShowInspectorStatistics,
											WATCHDOG_CONFIG::isShowInspectorStatistics
									))))
					.addPage(builder.createOptionPage()
							.setName(Component.translatable(
									"sodium-volt.options.security.page"
							))
							.addOptionGroup(builder.createOptionGroup()
									.setName(Component.translatable(
											"sodium-volt.options.security.group"
									))
									.addOption(builder.createBooleanOption(SHIELD_ENABLED)
											.setName(Component.translatable(
													"sodium-volt.options.security.enabled"
											))
											.setTooltip(securityTooltip("enabled", "high"))
											.setImpact(OptionImpact.VARIES)
											.setDefaultValue(false)
											.setStorageHandler(SHIELD_STORAGE_HANDLER)
											.setBinding(
													SHIELD_CONFIG::setResourcePackShieldEnabled,
													SHIELD_CONFIG::isResourcePackShieldEnabled
											))
									.addOption(builder.createBooleanOption(SHIELD_LOCAL_PACKS)
											.setName(Component.translatable(
													"sodium-volt.options.security.local_packs"
											))
											.setTooltip(securityTooltip("local_packs", "high"))
											.setImpact(OptionImpact.MEDIUM)
											.setDefaultValue(true)
											.setStorageHandler(SHIELD_STORAGE_HANDLER)
											.setBinding(
													SHIELD_CONFIG::setMonitorLocalPacks,
													SHIELD_CONFIG::isMonitorLocalPacks
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(SHIELD_ENABLED),
													SHIELD_ENABLED
											))
									.addOption(builder.createBooleanOption(SHIELD_SERVER_PACKS)
											.setName(Component.translatable(
													"sodium-volt.options.security.server_packs"
											))
											.setTooltip(securityTooltip("server_packs", "high"))
											.setImpact(OptionImpact.MEDIUM)
											.setDefaultValue(true)
											.setStorageHandler(SHIELD_STORAGE_HANDLER)
											.setBinding(
													SHIELD_CONFIG::setMonitorServerPacks,
													SHIELD_CONFIG::isMonitorServerPacks
											)
											.setEnabledProvider(
													state -> state.readBooleanOption(SHIELD_ENABLED),
													SHIELD_ENABLED
											))
									.addOption(shieldToggle(
											builder,
											SHIELD_UNSAFE_PATHS,
											"unsafe_paths",
											OptionImpact.LOW,
											"high",
											SHIELD_CONFIG::setDetectUnsafePathsAndSymlinks,
											SHIELD_CONFIG::isDetectUnsafePathsAndSymlinks
									))
									.addOption(shieldToggle(
											builder,
											SHIELD_CORE_SHADERS,
											"core_shaders",
											OptionImpact.LOW,
											"high",
											SHIELD_CONFIG::setBlockCoreShaderOverrides,
											SHIELD_CONFIG::isBlockCoreShaderOverrides
									))
									.addOption(shieldToggle(
											builder,
											SHIELD_REJECT,
											"reject",
											OptionImpact.LOW,
											"high",
											SHIELD_CONFIG::setRejectViolations,
											SHIELD_CONFIG::isRejectViolations
									))
									.addOption(shieldIntegerOption(
											builder,
											SHIELD_ENTRY_LIMIT,
											"entry_limit",
											ResourcePackShieldConfig.ENTRY_LIMIT_MIN,
											ResourcePackShieldConfig.ENTRY_LIMIT_MAX,
											ResourcePackShieldConfig.ENTRY_LIMIT_STEP,
											ResourcePackShieldConfig.ENTRY_LIMIT_DEFAULT,
											"count.value",
											OptionImpact.LOW,
											"high",
											SHIELD_CONFIG::setMaximumEntries,
											SHIELD_CONFIG::getMaximumEntries
									))
									.addOption(shieldIntegerOption(
											builder,
											SHIELD_ARCHIVE_SIZE,
											"archive_size",
											ResourcePackShieldConfig.ARCHIVE_MIB_MIN,
											ResourcePackShieldConfig.ARCHIVE_MIB_MAX,
											ResourcePackShieldConfig.ARCHIVE_MIB_STEP,
											ResourcePackShieldConfig.ARCHIVE_MIB_DEFAULT,
											"mib.value",
											OptionImpact.LOW,
											"medium",
											SHIELD_CONFIG::setMaximumArchiveMiB,
											SHIELD_CONFIG::getMaximumArchiveMiB
									))
									.addOption(shieldIntegerOption(
											builder,
											SHIELD_SINGLE_SIZE,
											"single_size",
											ResourcePackShieldConfig.SINGLE_MIB_MIN,
											ResourcePackShieldConfig.SINGLE_MIB_MAX,
											1,
											ResourcePackShieldConfig.SINGLE_MIB_DEFAULT,
											"mib.value",
											OptionImpact.LOW,
											"high",
											SHIELD_CONFIG::setMaximumSingleResourceMiB,
											SHIELD_CONFIG::getMaximumSingleResourceMiB
									))
									.addOption(shieldIntegerOption(
											builder,
											SHIELD_TOTAL_BUDGET,
											"total_budget",
											ResourcePackShieldConfig.TOTAL_MIB_MIN,
											ResourcePackShieldConfig.TOTAL_MIB_MAX,
											ResourcePackShieldConfig.TOTAL_MIB_STEP,
											ResourcePackShieldConfig.TOTAL_MIB_DEFAULT,
											"mib.value",
											OptionImpact.MEDIUM,
											"high",
											SHIELD_CONFIG::setMaximumTotalReadMiB,
											SHIELD_CONFIG::getMaximumTotalReadMiB
									))
									.addOption(shieldIntegerOption(
											builder,
											SHIELD_COMPRESSION_RATIO,
											"compression_ratio",
											ResourcePackShieldConfig.RATIO_MIN,
											ResourcePackShieldConfig.RATIO_MAX,
											ResourcePackShieldConfig.RATIO_STEP,
											ResourcePackShieldConfig.RATIO_DEFAULT,
											"ratio.value",
											OptionImpact.LOW,
											"high",
											SHIELD_CONFIG::setMaximumCompressionRatio,
											SHIELD_CONFIG::getMaximumCompressionRatio
									))
									.addOption(shieldIntegerOption(
											builder,
											SHIELD_PNG_DIMENSION,
											"png_dimension",
											ResourcePackShieldConfig.PNG_DIMENSION_MIN,
											ResourcePackShieldConfig.PNG_DIMENSION_MAX,
											ResourcePackShieldConfig.PNG_DIMENSION_STEP,
											ResourcePackShieldConfig.PNG_DIMENSION_DEFAULT,
											"pixels.value",
											OptionImpact.LOW,
											"high",
											SHIELD_CONFIG::setMaximumPngDimension,
											SHIELD_CONFIG::getMaximumPngDimension
									))
									.addOption(shieldIntegerOption(
											builder,
											SHIELD_PNG_PIXELS,
											"png_pixels",
											ResourcePackShieldConfig.PNG_MEGAPIXELS_MIN,
											ResourcePackShieldConfig.PNG_MEGAPIXELS_MAX,
											ResourcePackShieldConfig.PNG_MEGAPIXELS_STEP,
											ResourcePackShieldConfig.PNG_MEGAPIXELS_DEFAULT,
											"megapixels.value",
											OptionImpact.LOW,
											"high",
											SHIELD_CONFIG::setMaximumPngMegapixels,
											SHIELD_CONFIG::getMaximumPngMegapixels
									))
									.addOption(shieldIntegerOption(
											builder,
											SHIELD_JSON_DEPTH,
											"json_depth",
											ResourcePackShieldConfig.JSON_DEPTH_MIN,
											ResourcePackShieldConfig.JSON_DEPTH_MAX,
											ResourcePackShieldConfig.JSON_DEPTH_STEP,
											ResourcePackShieldConfig.JSON_DEPTH_DEFAULT,
											"levels.value",
											OptionImpact.MEDIUM,
											"high",
											SHIELD_CONFIG::setMaximumJsonDepth,
											SHIELD_CONFIG::getMaximumJsonDepth
									))
									.addOption(shieldIntegerOption(
											builder,
											SHIELD_PATH_LENGTH,
											"path_length",
											ResourcePackShieldConfig.PATH_LENGTH_MIN,
											ResourcePackShieldConfig.PATH_LENGTH_MAX,
											ResourcePackShieldConfig.PATH_LENGTH_STEP,
											ResourcePackShieldConfig.PATH_LENGTH_DEFAULT,
											"characters.value",
											OptionImpact.LOW,
											"medium",
											SHIELD_CONFIG::setMaximumPathLength,
											SHIELD_CONFIG::getMaximumPathLength
									).setEnabledProvider(
											state -> shieldFeatureEnabled(state)
													&& state.readBooleanOption(SHIELD_UNSAFE_PATHS),
											SHIELD_ENABLED,
											SHIELD_LOCAL_PACKS,
											SHIELD_SERVER_PACKS,
											SHIELD_UNSAFE_PATHS
									))
									.addOption(shieldIntegerOption(
											builder,
											SHIELD_PATH_DEPTH,
											"path_depth",
											ResourcePackShieldConfig.PATH_DEPTH_MIN,
											ResourcePackShieldConfig.PATH_DEPTH_MAX,
											ResourcePackShieldConfig.PATH_DEPTH_STEP,
											ResourcePackShieldConfig.PATH_DEPTH_DEFAULT,
											"levels.value",
											OptionImpact.LOW,
											"medium",
											SHIELD_CONFIG::setMaximumPathDepth,
											SHIELD_CONFIG::getMaximumPathDepth
									).setEnabledProvider(
											state -> shieldFeatureEnabled(state)
													&& state.readBooleanOption(SHIELD_UNSAFE_PATHS),
											SHIELD_ENABLED,
											SHIELD_LOCAL_PACKS,
											SHIELD_SERVER_PACKS,
											SHIELD_UNSAFE_PATHS
									))
									.addOption(shieldIntegerOption(
											builder,
											SHIELD_SCAN_TIME,
											"scan_time",
											ResourcePackShieldConfig.SCAN_MILLIS_MIN,
											ResourcePackShieldConfig.SCAN_MILLIS_MAX,
											ResourcePackShieldConfig.SCAN_MILLIS_STEP,
											ResourcePackShieldConfig.SCAN_MILLIS_DEFAULT,
											"milliseconds.value",
											OptionImpact.MEDIUM,
											"medium",
											SHIELD_CONFIG::setMaximumScanMillis,
											SHIELD_CONFIG::getMaximumScanMillis
									))
									.addOption(shieldToggle(
											builder,
											SHIELD_NOTIFICATIONS,
											"notifications",
											OptionImpact.LOW,
											"low",
											SHIELD_CONFIG::setShowTransitionNotifications,
											SHIELD_CONFIG::isShowTransitionNotifications
									))
									.addOption(shieldToggle(
											builder,
											SHIELD_REPORT,
											"report",
											OptionImpact.LOW,
											"low",
											SHIELD_CONFIG::setWriteSanitizedLocalReport,
											SHIELD_CONFIG::isWriteSanitizedLocalReport
									))
									.addOption(shieldToggle(
											builder,
											SHIELD_INSPECTOR_STATS,
											"inspector_stats",
											OptionImpact.LOW,
											"low",
											SHIELD_CONFIG::setShowInspectorStatistics,
											SHIELD_CONFIG::isShowInspectorStatistics
									))))
					.addPage(builder.createOptionPage()
							.setName(Component.translatable(
									"sodium-volt.options.privacy.page"
							))
							.addOptionGroup(builder.createOptionGroup()
									.setName(Component.translatable(
											"sodium-volt.options.privacy.group"
									))
									.addOption(builder.createBooleanOption(PRIVACY_SCREENSHOT_ENABLED)
											.setName(Component.translatable(
													"sodium-volt.options.privacy.enabled"
											))
											.setTooltip(privacyTooltip("enabled", "high"))
											.setImpact(OptionImpact.VARIES)
											.setDefaultValue(false)
											.setStorageHandler(PRIVACY_STORAGE_HANDLER)
											.setBinding(
													PRIVACY_CONFIG::setEnabled,
													PRIVACY_CONFIG::isEnabled
											))
									.addOption(privacyToggle(builder, PRIVACY_HIDE_CHAT,
											"hide_chat", OptionImpact.LOW, true, "high",
											PRIVACY_CONFIG::setHideChat, PRIVACY_CONFIG::isHideChat))
									.addOption(privacyToggle(builder, PRIVACY_HIDE_DEBUG,
											"hide_debug", OptionImpact.LOW, true, "high",
											PRIVACY_CONFIG::setHideDebugOverlay,
											PRIVACY_CONFIG::isHideDebugOverlay))
									.addOption(privacyToggle(builder, PRIVACY_HIDE_PLAYER_LIST,
											"hide_player_list", OptionImpact.LOW, true, "high",
											PRIVACY_CONFIG::setHidePlayerList,
											PRIVACY_CONFIG::isHidePlayerList))
									.addOption(privacyToggle(builder, PRIVACY_HIDE_SCOREBOARD,
											"hide_scoreboard", OptionImpact.LOW, true, "high",
											PRIVACY_CONFIG::setHideScoreboard,
											PRIVACY_CONFIG::isHideScoreboard))
									.addOption(privacyToggle(builder, PRIVACY_HIDE_BOSS_BARS,
											"hide_boss_bars", OptionImpact.LOW, true, "medium",
											PRIVACY_CONFIG::setHideBossBars,
											PRIVACY_CONFIG::isHideBossBars))
									.addOption(privacyToggle(builder, PRIVACY_HIDE_TITLES,
											"hide_titles", OptionImpact.LOW, true, "medium",
											PRIVACY_CONFIG::setHideTitlesAndActionBar,
											PRIVACY_CONFIG::isHideTitlesAndActionBar))
									.addOption(privacyToggle(builder, PRIVACY_HIDE_SUBTITLES,
											"hide_subtitles", OptionImpact.LOW, true, "medium",
											PRIVACY_CONFIG::setHideSubtitles,
											PRIVACY_CONFIG::isHideSubtitles))
									.addOption(privacyToggle(builder, PRIVACY_HIDE_TOASTS,
											"hide_toasts", OptionImpact.LOW, true, "medium",
											PRIVACY_CONFIG::setHideToastsAndSavingIndicator,
											PRIVACY_CONFIG::isHideToastsAndSavingIndicator))
									.addOption(privacyToggle(builder, PRIVACY_HIDE_NAME_TAGS,
											"hide_name_tags", OptionImpact.LOW, true, "high",
											PRIVACY_CONFIG::setHideNameTags,
											PRIVACY_CONFIG::isHideNameTags))
									.addOption(privacyToggle(builder, PRIVACY_HIDE_GAMEPLAY_HUD,
											"hide_gameplay_hud", OptionImpact.LOW, false, "low",
											PRIVACY_CONFIG::setHideGameplayHud,
											PRIVACY_CONFIG::isHideGameplayHud))
									.addOption(privacyToggle(builder, PRIVACY_HIDE_HELD_ITEM,
											"hide_held_item", OptionImpact.LOW, false, "low",
											PRIVACY_CONFIG::setHideHeldItem,
											PRIVACY_CONFIG::isHideHeldItem))
									.addOption(privacyToggle(builder, PRIVACY_BLOCK_SCREENS,
											"block_screens", OptionImpact.LOW, true, "high",
											PRIVACY_CONFIG::setBlockOpenScreens,
											PRIVACY_CONFIG::isBlockOpenScreens))
									.addOption(privacyToggle(builder, PRIVACY_RANDOM_FILENAME,
											"random_filename", OptionImpact.LOW, true, "medium",
											PRIVACY_CONFIG::setRandomizeFilename,
											PRIVACY_CONFIG::isRandomizeFilename))
									.addOption(privacyToggle(builder, PRIVACY_FAIL_CLOSED,
											"fail_closed", OptionImpact.LOW, true, "high",
											PRIVACY_CONFIG::setFailClosed,
											PRIVACY_CONFIG::isFailClosed))
									.addOption(privacyToggle(builder, PRIVACY_NOTIFICATIONS,
											"notifications", OptionImpact.LOW, true, "low",
											PRIVACY_CONFIG::setShowNotifications,
											PRIVACY_CONFIG::isShowNotifications))))
					.addPage(profilesPage(builder));
	}

	private static OptionPageBuilder profilesPage(ConfigBuilder builder) {
		ProfileSettings global = ProfileSettings.globalDefaults();
		ProfileSettings singlePlayer = ProfileSettings.singlePlayerDefaults();
		ProfileSettings server = ProfileSettings.serverDefaults();
		return builder.createOptionPage()
				.setName(Component.translatable("sodium-volt.options.profiles.page"))
				.addOptionGroup(builder.createOptionGroup()
						.setName(Component.translatable(
								"sodium-volt.options.profiles.group.behavior"
						))
						.addOption(builder.createBooleanOption(PROFILES_ENABLED)
								.setName(Component.translatable(
										"sodium-volt.options.profiles.enabled"
								))
								.setTooltip(Component.translatable(
										"sodium-volt.options.profiles.enabled.tooltip"
								))
								.setImpact(OptionImpact.VARIES)
								.setDefaultValue(false)
								.setStorageHandler(PROFILES_STORAGE_HANDLER)
								.setBinding(
										PROFILES_CONFIG::setProfilesEnabled,
										PROFILES_CONFIG::isProfilesEnabled
								))
						.addOption(profileToggle(
								builder,
								PROFILES_RESTORE_GLOBAL,
								"behavior.restore_global",
								true,
								PROFILES_CONFIG::setRestoreGlobalDefaultsOnMenu,
								PROFILES_CONFIG::isRestoreGlobalDefaultsOnMenu,
								PROFILES_ENABLED
						)))
				.addOptionGroup(builder.createOptionGroup()
						.setName(Component.translatable(
								"sodium-volt.options.profiles.group.global"
						))
						.addOption(profileInteger(builder, PROFILES_GLOBAL_RENDER_DISTANCE,
								"global.render_distance", ProfileSettings.RENDER_DISTANCE_MIN,
								ProfileSettings.RENDER_DISTANCE_MAX, 1, global.renderDistance(),
								"chunks", OptionImpact.HIGH,
								PROFILES_CONFIG::setGlobalRenderDistance,
								() -> PROFILES_CONFIG.getGlobalDefaults().renderDistance(),
								PROFILES_ENABLED))
						.addOption(profileInteger(builder, PROFILES_GLOBAL_SIMULATION_DISTANCE,
								"global.simulation_distance", ProfileSettings.SIMULATION_DISTANCE_MIN,
								ProfileSettings.SIMULATION_DISTANCE_MAX, 1,
								global.simulationDistance(), "chunks", OptionImpact.HIGH,
								PROFILES_CONFIG::setGlobalSimulationDistance,
								() -> PROFILES_CONFIG.getGlobalDefaults().simulationDistance(),
								PROFILES_ENABLED))
						.addOption(profileInteger(builder, PROFILES_GLOBAL_ENTITY_DISTANCE,
								"global.entity_distance", ProfileSettings.ENTITY_DISTANCE_MIN,
								ProfileSettings.ENTITY_DISTANCE_MAX, ProfileSettings.ENTITY_DISTANCE_STEP,
								global.entityDistancePercent(), "percent", OptionImpact.MEDIUM,
								PROFILES_CONFIG::setGlobalEntityDistancePercent,
								() -> PROFILES_CONFIG.getGlobalDefaults().entityDistancePercent(),
								PROFILES_ENABLED))
						.addOption(profileInteger(builder, PROFILES_GLOBAL_FRAMERATE_LIMIT,
								"global.framerate_limit", ProfileSettings.FRAMERATE_LIMIT_MIN,
								ProfileSettings.FRAMERATE_LIMIT_MAX, ProfileSettings.FRAMERATE_LIMIT_STEP,
								global.framerateLimit(), "fps", OptionImpact.HIGH,
								PROFILES_CONFIG::setGlobalFramerateLimit,
								() -> PROFILES_CONFIG.getGlobalDefaults().framerateLimit(),
								PROFILES_ENABLED))
						.addOption(profileParticles(builder, PROFILES_GLOBAL_PARTICLES,
								"global.particles", global.particleMode(),
								PROFILES_CONFIG::setGlobalParticleMode,
								() -> PROFILES_CONFIG.getGlobalDefaults().particleMode(),
								PROFILES_ENABLED)))
				.addOptionGroup(builder.createOptionGroup()
						.setName(Component.translatable(
								"sodium-volt.options.profiles.group.single_player"
						))
						.addOption(profileToggle(builder, PROFILES_SINGLE_PLAYER_ENABLED,
								"single_player.enabled", true,
								PROFILES_CONFIG::setSinglePlayerProfilesEnabled,
								PROFILES_CONFIG::isSinglePlayerProfilesEnabled,
								PROFILES_ENABLED))
						.addOption(profileInteger(builder, PROFILES_SINGLE_PLAYER_RENDER_DISTANCE,
								"single_player.render_distance", ProfileSettings.RENDER_DISTANCE_MIN,
								ProfileSettings.RENDER_DISTANCE_MAX, 1, singlePlayer.renderDistance(),
								"chunks", OptionImpact.HIGH,
								PROFILES_CONFIG::setSinglePlayerRenderDistance,
								() -> PROFILES_CONFIG.getSinglePlayerTemplate().renderDistance(),
								PROFILES_ENABLED, PROFILES_SINGLE_PLAYER_ENABLED))
						.addOption(profileInteger(builder,
								PROFILES_SINGLE_PLAYER_SIMULATION_DISTANCE,
								"single_player.simulation_distance",
								ProfileSettings.SIMULATION_DISTANCE_MIN,
								ProfileSettings.SIMULATION_DISTANCE_MAX, 1,
								singlePlayer.simulationDistance(), "chunks", OptionImpact.HIGH,
								PROFILES_CONFIG::setSinglePlayerSimulationDistance,
								() -> PROFILES_CONFIG.getSinglePlayerTemplate().simulationDistance(),
								PROFILES_ENABLED, PROFILES_SINGLE_PLAYER_ENABLED))
						.addOption(profileInteger(builder, PROFILES_SINGLE_PLAYER_ENTITY_DISTANCE,
								"single_player.entity_distance", ProfileSettings.ENTITY_DISTANCE_MIN,
								ProfileSettings.ENTITY_DISTANCE_MAX, ProfileSettings.ENTITY_DISTANCE_STEP,
								singlePlayer.entityDistancePercent(), "percent", OptionImpact.MEDIUM,
								PROFILES_CONFIG::setSinglePlayerEntityDistancePercent,
								() -> PROFILES_CONFIG.getSinglePlayerTemplate().entityDistancePercent(),
								PROFILES_ENABLED, PROFILES_SINGLE_PLAYER_ENABLED))
						.addOption(profileInteger(builder, PROFILES_SINGLE_PLAYER_FRAMERATE_LIMIT,
								"single_player.framerate_limit", ProfileSettings.FRAMERATE_LIMIT_MIN,
								ProfileSettings.FRAMERATE_LIMIT_MAX, ProfileSettings.FRAMERATE_LIMIT_STEP,
								singlePlayer.framerateLimit(), "fps", OptionImpact.HIGH,
								PROFILES_CONFIG::setSinglePlayerFramerateLimit,
								() -> PROFILES_CONFIG.getSinglePlayerTemplate().framerateLimit(),
								PROFILES_ENABLED, PROFILES_SINGLE_PLAYER_ENABLED))
						.addOption(profileParticles(builder, PROFILES_SINGLE_PLAYER_PARTICLES,
								"single_player.particles", singlePlayer.particleMode(),
								PROFILES_CONFIG::setSinglePlayerParticleMode,
								() -> PROFILES_CONFIG.getSinglePlayerTemplate().particleMode(),
								PROFILES_ENABLED, PROFILES_SINGLE_PLAYER_ENABLED))
						.addOption(profileButton(builder, PROFILES_SINGLE_PLAYER_CAPTURE,
								"single_player.capture", screen ->
										PerformanceProfileEngine.captureCurrentSinglePlayerProfile(
												Minecraft.getInstance()
										), PROFILES_ENABLED, PROFILES_SINGLE_PLAYER_ENABLED))
						.addOption(profileButton(builder, PROFILES_SINGLE_PLAYER_FORGET,
								"single_player.forget", screen ->
										PerformanceProfileEngine.forgetCurrentSinglePlayerProfile(
												Minecraft.getInstance()
										), PROFILES_ENABLED, PROFILES_SINGLE_PLAYER_ENABLED)))
				.addOptionGroup(builder.createOptionGroup()
						.setName(Component.translatable(
								"sodium-volt.options.profiles.group.server"
						))
						.addOption(profileToggle(builder, PROFILES_SERVER_ENABLED,
								"server.enabled", true,
								PROFILES_CONFIG::setSpecificServerProfilesEnabled,
								PROFILES_CONFIG::isSpecificServerProfilesEnabled,
								PROFILES_ENABLED))
						.addOption(profileInteger(builder, PROFILES_SERVER_RENDER_DISTANCE,
								"server.render_distance", ProfileSettings.RENDER_DISTANCE_MIN,
								ProfileSettings.RENDER_DISTANCE_MAX, 1, server.renderDistance(),
								"chunks", OptionImpact.HIGH,
								PROFILES_CONFIG::setServerRenderDistance,
								() -> PROFILES_CONFIG.getServerTemplate().renderDistance(),
								PROFILES_ENABLED, PROFILES_SERVER_ENABLED))
						.addOption(profileInteger(builder, PROFILES_SERVER_SIMULATION_DISTANCE,
								"server.simulation_distance", ProfileSettings.SIMULATION_DISTANCE_MIN,
								ProfileSettings.SIMULATION_DISTANCE_MAX, 1,
								server.simulationDistance(), "chunks", OptionImpact.HIGH,
								PROFILES_CONFIG::setServerSimulationDistance,
								() -> PROFILES_CONFIG.getServerTemplate().simulationDistance(),
								PROFILES_ENABLED, PROFILES_SERVER_ENABLED))
						.addOption(profileInteger(builder, PROFILES_SERVER_ENTITY_DISTANCE,
								"server.entity_distance", ProfileSettings.ENTITY_DISTANCE_MIN,
								ProfileSettings.ENTITY_DISTANCE_MAX, ProfileSettings.ENTITY_DISTANCE_STEP,
								server.entityDistancePercent(), "percent", OptionImpact.MEDIUM,
								PROFILES_CONFIG::setServerEntityDistancePercent,
								() -> PROFILES_CONFIG.getServerTemplate().entityDistancePercent(),
								PROFILES_ENABLED, PROFILES_SERVER_ENABLED))
						.addOption(profileInteger(builder, PROFILES_SERVER_FRAMERATE_LIMIT,
								"server.framerate_limit", ProfileSettings.FRAMERATE_LIMIT_MIN,
								ProfileSettings.FRAMERATE_LIMIT_MAX, ProfileSettings.FRAMERATE_LIMIT_STEP,
								server.framerateLimit(), "fps", OptionImpact.HIGH,
								PROFILES_CONFIG::setServerFramerateLimit,
								() -> PROFILES_CONFIG.getServerTemplate().framerateLimit(),
								PROFILES_ENABLED, PROFILES_SERVER_ENABLED))
						.addOption(profileParticles(builder, PROFILES_SERVER_PARTICLES,
								"server.particles", server.particleMode(),
								PROFILES_CONFIG::setServerParticleMode,
								() -> PROFILES_CONFIG.getServerTemplate().particleMode(),
								PROFILES_ENABLED, PROFILES_SERVER_ENABLED))
						.addOption(profileButton(builder, PROFILES_SERVER_CAPTURE,
								"server.capture", screen ->
										PerformanceProfileEngine.captureCurrentServerProfile(
												Minecraft.getInstance()
										), PROFILES_ENABLED, PROFILES_SERVER_ENABLED))
						.addOption(profileButton(builder, PROFILES_SERVER_FORGET,
								"server.forget", screen ->
										PerformanceProfileEngine.forgetCurrentServerProfile(
												Minecraft.getInstance()
										), PROFILES_ENABLED, PROFILES_SERVER_ENABLED)))
				.addOptionGroup(builder.createOptionGroup()
						.setName(Component.translatable(
								"sodium-volt.options.profiles.group.factory_reset"
						))
						.addOption(builder.createExternalButtonOption(PROFILES_FACTORY_RESET)
								.setName(Component.translatable(
										"sodium-volt.options.profiles.factory_reset"
								))
								.setTooltip(Component.translatable(
										"sodium-volt.options.profiles.factory_reset.tooltip"
								))
								.setScreenConsumer(
										SodiumVoltConfigEntryPoint::openFactoryResetConfirmation
								)));
	}

	private static BooleanOptionBuilder profileToggle(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			boolean defaultValue,
			Consumer<Boolean> setter,
			Supplier<Boolean> getter,
			Identifier... dependencies
	) {
		String key = "sodium-volt.options.profiles." + translationPath;
		return builder.createBooleanOption(id)
				.setName(Component.translatable(key))
				.setTooltip(Component.translatable(key + ".tooltip"))
				.setImpact(OptionImpact.VARIES)
				.setDefaultValue(defaultValue)
				.setStorageHandler(PROFILES_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setEnabledProvider(
						state -> allProfileDependenciesEnabled(state, dependencies),
						dependencies
				);
	}

	private static IntegerOptionBuilder profileInteger(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			int minimum,
			int maximum,
			int step,
			int defaultValue,
			String valueTranslation,
			OptionImpact impact,
			Consumer<Integer> setter,
			Supplier<Integer> getter,
			Identifier... dependencies
	) {
		String key = "sodium-volt.options.profiles." + translationPath;
		return builder.createIntegerOption(id)
				.setName(Component.translatable(key))
				.setTooltip(Component.translatable(key + ".tooltip"))
				.setImpact(impact)
				.setRange(minimum, maximum, step)
				.setValueFormatter(value -> Component.translatable(
						"sodium-volt.options.profiles.value." + valueTranslation, value
				))
				.setDefaultValue(defaultValue)
				.setStorageHandler(PROFILES_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setEnabledProvider(
						state -> allProfileDependenciesEnabled(state, dependencies),
						dependencies
				);
	}

	private static EnumOptionBuilder<ProfileParticleMode> profileParticles(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			ProfileParticleMode defaultValue,
			Consumer<ProfileParticleMode> setter,
			Supplier<ProfileParticleMode> getter,
			Identifier... dependencies
	) {
		String key = "sodium-volt.options.profiles." + translationPath;
		return builder.createEnumOption(id, ProfileParticleMode.class)
				.setName(Component.translatable(key))
				.setTooltip(Component.translatable(key + ".tooltip"))
				.setImpact(OptionImpact.MEDIUM)
				.setDefaultValue(defaultValue)
				.setStorageHandler(PROFILES_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setElementNameProvider(value -> Component.translatable(
						"sodium-volt.options.profiles.particles." + value.serializedName()
				))
				.setEnabledProvider(
						state -> allProfileDependenciesEnabled(state, dependencies),
						dependencies
				);
	}

	private static ExternalButtonOptionBuilder profileButton(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			Consumer<Screen> action,
			Identifier... dependencies
	) {
		String key = "sodium-volt.options.profiles." + translationPath;
		return builder.createExternalButtonOption(id)
				.setName(Component.translatable(key))
				.setTooltip(Component.translatable(key + ".tooltip"))
				.setScreenConsumer(action)
				.setEnabledProvider(
						state -> allProfileDependenciesEnabled(state, dependencies),
						dependencies
				);
	}

	private static boolean allProfileDependenciesEnabled(
			ConfigState state,
			Identifier[] dependencies
	) {
		for (Identifier dependency : dependencies) {
			if (!state.readBooleanOption(dependency)) {
				return false;
			}
		}
		return true;
	}

	private static void openFactoryResetConfirmation(Screen parent) {
		Minecraft minecraft = Minecraft.getInstance();
		ConfirmScreen confirmation = new ConfirmScreen(
				confirmed -> FactoryResetDecision.handle(
						confirmed,
						() -> VoltFactoryReset.reset(minecraft),
						parent::onClose,
						() -> minecraft.setScreenAndShow(parent)
				),
				Component.translatable("sodium-volt.options.profiles.factory_reset.title"),
				Component.translatable("sodium-volt.options.profiles.factory_reset.message"),
				CommonComponents.GUI_YES,
				CommonComponents.GUI_NO
		);
		minecraft.setScreenAndShow(confirmation);
	}

	private static BooleanOptionBuilder privacyToggle(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			OptionImpact impact,
			boolean defaultValue,
			String privacyImpact,
			Consumer<Boolean> setter,
			Supplier<Boolean> getter
	) {
		String key = "sodium-volt.options.privacy." + translationPath;
		return builder.createBooleanOption(id)
				.setName(Component.translatable(key))
				.setTooltip(privacyTooltip(translationPath, privacyImpact))
				.setImpact(impact)
				.setDefaultValue(defaultValue)
				.setStorageHandler(PRIVACY_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setEnabledProvider(
						state -> state.readBooleanOption(PRIVACY_SCREENSHOT_ENABLED),
						PRIVACY_SCREENSHOT_ENABLED
				);
	}

	private static Component privacyTooltip(String translationPath, String impact) {
		return Component.translatable(
						"sodium-volt.options.privacy." + translationPath + ".tooltip"
				)
				.append(Component.literal("\n"))
				.append(Component.translatable(
						"sodium-volt.options.privacy.impact." + impact
				));
	}

	private static BooleanOptionBuilder shieldToggle(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			OptionImpact impact,
			String securityImpact,
			Consumer<Boolean> setter,
			Supplier<Boolean> getter
	) {
		String key = "sodium-volt.options.security." + translationPath;
		return builder.createBooleanOption(id)
				.setName(Component.translatable(key))
				.setTooltip(securityTooltip(translationPath, securityImpact))
				.setImpact(impact)
				.setDefaultValue(true)
				.setStorageHandler(SHIELD_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setEnabledProvider(
						SodiumVoltConfigEntryPoint::shieldFeatureEnabled,
						SHIELD_ENABLED,
						SHIELD_LOCAL_PACKS,
						SHIELD_SERVER_PACKS
				);
	}

	private static IntegerOptionBuilder shieldIntegerOption(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			int minimum,
			int maximum,
			int step,
			int defaultValue,
			String valueTranslation,
			OptionImpact impact,
			String securityImpact,
			Consumer<Integer> setter,
			Supplier<Integer> getter
	) {
		String key = "sodium-volt.options.security." + translationPath;
		return builder.createIntegerOption(id)
				.setName(Component.translatable(key))
				.setTooltip(securityTooltip(translationPath, securityImpact))
				.setImpact(impact)
				.setRange(minimum, maximum, step)
				.setValueFormatter(value -> Component.translatable(
						"sodium-volt.options.security." + valueTranslation, value
				))
				.setDefaultValue(defaultValue)
				.setStorageHandler(SHIELD_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setEnabledProvider(
						SodiumVoltConfigEntryPoint::shieldFeatureEnabled,
						SHIELD_ENABLED,
						SHIELD_LOCAL_PACKS,
						SHIELD_SERVER_PACKS
				);
	}

	private static boolean shieldFeatureEnabled(ConfigState state) {
		return state.readBooleanOption(SHIELD_ENABLED)
				&& (state.readBooleanOption(SHIELD_LOCAL_PACKS)
						|| state.readBooleanOption(SHIELD_SERVER_PACKS));
	}

	private static Component securityTooltip(String translationPath, String impact) {
		return Component.translatable(
						"sodium-volt.options.security." + translationPath + ".tooltip"
				)
				.append(Component.literal("\n"))
				.append(Component.translatable(
						"sodium-volt.options.security.impact." + impact
				));
	}

	private static BooleanOptionBuilder inspectorToggle(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			OptionImpact impact,
			Consumer<Boolean> setter,
			Supplier<Boolean> getter
	) {
		String key = "sodium-volt.options.inspector." + translationPath;
		return builder.createBooleanOption(id)
				.setName(Component.translatable(key))
				.setTooltip(Component.translatable(key + ".tooltip"))
				.setImpact(impact)
				.setDefaultValue(true)
				.setStorageHandler(INSPECTOR_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setEnabledProvider(
						state -> state.readBooleanOption(VOLT_INSPECTOR_ENABLED),
						VOLT_INSPECTOR_ENABLED
				);
	}

	private static BooleanOptionBuilder inspectorFrameDependentToggle(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			OptionImpact impact,
			Consumer<Boolean> setter,
			Supplier<Boolean> getter
	) {
		String key = "sodium-volt.options.inspector." + translationPath;
		return builder.createBooleanOption(id)
				.setName(Component.translatable(key))
				.setTooltip(Component.translatable(key + ".tooltip"))
				.setImpact(impact)
				.setDefaultValue(true)
				.setStorageHandler(INSPECTOR_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setEnabledProvider(
						state -> state.readBooleanOption(VOLT_INSPECTOR_ENABLED)
								&& state.readBooleanOption(FRAME_TIME_STATISTICS),
						VOLT_INSPECTOR_ENABLED,
						FRAME_TIME_STATISTICS
				);
	}

	private static BooleanOptionBuilder performanceToggle(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			OptionImpact impact,
			Consumer<Boolean> setter,
			Supplier<Boolean> getter
	) {
		String key = "sodium-volt.options.performance." + translationPath;
		return builder.createBooleanOption(id)
				.setName(Component.translatable(key))
				.setTooltip(Component.translatable(key + ".tooltip"))
				.setImpact(impact)
				.setDefaultValue(true)
				.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setEnabledProvider(
						state -> state.readBooleanOption(APC_ENABLED),
						APC_ENABLED
				);
	}

	private static BooleanOptionBuilder vapsToggle(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			OptionImpact impact,
			Consumer<Boolean> setter,
			Supplier<Boolean> getter
	) {
		String key = "sodium-volt.options.performance.vaps." + translationPath;
		return builder.createBooleanOption(id)
				.setName(Component.translatable(key))
				.setTooltip(Component.translatable(key + ".tooltip"))
				.setImpact(impact)
				.setDefaultValue(true)
				.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setEnabledProvider(
						state -> state.readBooleanOption(VAPS_ENABLED),
						VAPS_ENABLED
				);
	}

	private static BooleanOptionBuilder berpToggle(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			OptionImpact impact,
			boolean defaultValue,
			Consumer<Boolean> setter,
			Supplier<Boolean> getter
	) {
		String key = "sodium-volt.options.performance.berp." + translationPath;
		return builder.createBooleanOption(id)
				.setName(Component.translatable(key))
				.setTooltip(Component.translatable(key + ".tooltip"))
				.setImpact(impact)
				.setDefaultValue(defaultValue)
				.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setEnabledProvider(
						state -> state.readBooleanOption(BERP_ENABLED),
						BERP_ENABLED
				);
	}

	private static BooleanOptionBuilder attToggle(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			OptionImpact impact,
			Consumer<Boolean> setter,
			Supplier<Boolean> getter
	) {
		String key = "sodium-volt.options.performance.att." + translationPath;
		return builder.createBooleanOption(id)
				.setName(Component.translatable(key))
				.setTooltip(Component.translatable(key + ".tooltip"))
				.setImpact(impact)
				.setDefaultValue(true)
				.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setEnabledProvider(
						state -> state.readBooleanOption(ATT_ENABLED),
						ATT_ENABLED
				);
	}

	private static BooleanOptionBuilder vramToggle(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			OptionImpact impact,
			Consumer<Boolean> setter,
			Supplier<Boolean> getter
	) {
		String key = "sodium-volt.options.performance.vram." + translationPath;
		return builder.createBooleanOption(id)
				.setName(Component.translatable(key))
				.setTooltip(Component.translatable(key + ".tooltip"))
				.setImpact(impact)
				.setDefaultValue(true)
				.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setEnabledProvider(
						state -> state.readBooleanOption(VRAM_ENABLED),
						VRAM_ENABLED
					);
	}

	private static BooleanOptionBuilder smartFpsToggle(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			OptionImpact impact,
			Consumer<Boolean> setter,
			Supplier<Boolean> getter
	) {
		String key = "sodium-volt.options.smart_fps." + translationPath;
		return builder.createBooleanOption(id)
				.setName(Component.translatable(key))
				.setTooltip(Component.translatable(key + ".tooltip"))
				.setImpact(impact)
				.setDefaultValue(true)
				.setStorageHandler(SMART_FPS_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setEnabledProvider(
						state -> state.readBooleanOption(SMART_FPS_ENABLED),
						SMART_FPS_ENABLED
				);
	}

	private static BooleanOptionBuilder recoveryToggle(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			OptionImpact impact,
			Consumer<Boolean> setter,
			Supplier<Boolean> getter
	) {
		String key = "sodium-volt.options.recovery." + translationPath;
		return builder.createBooleanOption(id)
				.setName(Component.translatable(key))
				.setTooltip(Component.translatable(key + ".tooltip"))
				.setImpact(impact)
				.setDefaultValue(true)
				.setStorageHandler(RECOVERY_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setEnabledProvider(
						state -> state.readBooleanOption(RECOVERY_ENABLED),
						RECOVERY_ENABLED
				);
	}

	private static BooleanOptionBuilder watchdogToggle(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			OptionImpact impact,
			Consumer<Boolean> setter,
			Supplier<Boolean> getter
	) {
		String key = "sodium-volt.options.watchdog." + translationPath;
		return builder.createBooleanOption(id)
				.setName(Component.translatable(key))
				.setTooltip(Component.translatable(key + ".tooltip"))
				.setImpact(impact)
				.setDefaultValue(true)
				.setStorageHandler(WATCHDOG_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setEnabledProvider(
						state -> state.readBooleanOption(WATCHDOG_ENABLED),
						WATCHDOG_ENABLED
				);
	}

	private static net.caffeinemc.mods.sodium.api.config.structure.IntegerOptionBuilder watchdogIntegerOption(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			int minimum,
			int maximum,
			int step,
			int defaultValue,
			String valueTranslation,
			Consumer<Integer> setter,
			Supplier<Integer> getter
	) {
		String key = "sodium-volt.options.watchdog." + translationPath;
		return builder.createIntegerOption(id)
				.setName(Component.translatable(key))
				.setTooltip(Component.translatable(key + ".tooltip"))
				.setImpact(OptionImpact.LOW)
				.setRange(minimum, maximum, step)
				.setValueFormatter(value -> Component.translatable(
						"sodium-volt.options.watchdog." + valueTranslation,
						value
				))
				.setDefaultValue(defaultValue)
				.setStorageHandler(WATCHDOG_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setEnabledProvider(
						state -> state.readBooleanOption(WATCHDOG_ENABLED),
						WATCHDOG_ENABLED
				);
	}

	private static net.caffeinemc.mods.sodium.api.config.structure.IntegerOptionBuilder performanceSecondsOption(
			ConfigBuilder builder,
			Identifier id,
			String translationPath,
			int minimum,
			int maximum,
			int defaultValue,
			Consumer<Integer> setter,
			Supplier<Integer> getter
	) {
		String key = "sodium-volt.options.performance." + translationPath;
		return builder.createIntegerOption(id)
				.setName(Component.translatable(key))
				.setTooltip(Component.translatable(key + ".tooltip"))
				.setImpact(OptionImpact.LOW)
				.setRange(minimum, maximum, 1)
				.setValueFormatter(value -> Component.translatable(
						"sodium-volt.options.performance.seconds.value", value
				))
				.setDefaultValue(defaultValue)
				.setStorageHandler(PERFORMANCE_STORAGE_HANDLER)
				.setBinding(setter, getter)
				.setEnabledProvider(
						state -> state.readBooleanOption(APC_ENABLED),
						APC_ENABLED
				);
	}

	private static Identifier optionId(String path) {
		return Identifier.fromNamespaceAndPath("sodium-volt", path);
	}
}
