package com.ragedriver.sodiumvolt.client.config;

import com.google.gson.JsonObject;
import com.ragedriver.sodiumvolt.SodiumVolt;
import com.ragedriver.sodiumvolt.client.privacy.PrivacyScreenshotPolicy;
import com.ragedriver.sodiumvolt.client.resourcepack.ShieldJsonFile;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PrivacyScreenshotConfig {
	static final int CONFIG_VERSION = 1;
	static final int MAXIMUM_CONFIG_BYTES = 8 * 1024;
	static final Set<String> CONFIG_KEYS = Set.of(
			"version",
			"privacy_screenshot_mode_enabled",
			"hide_chat",
			"hide_debug_overlay",
			"hide_player_list",
			"hide_scoreboard",
			"hide_boss_bars",
			"hide_titles_and_action_bar",
			"hide_subtitles",
			"hide_toasts_and_saving_indicator",
			"hide_name_tags",
			"hide_gameplay_hud",
			"hide_held_item",
			"block_open_screens",
			"randomize_filename",
			"fail_closed",
			"show_notifications"
	);
	private static final AtomicBoolean LOAD_FAILURE_LOGGED = new AtomicBoolean();
	private static final AtomicBoolean SAVE_FAILURE_LOGGED = new AtomicBoolean();

	private boolean enabled;
	private boolean hideChat = true;
	private boolean hideDebugOverlay = true;
	private boolean hidePlayerList = true;
	private boolean hideScoreboard = true;
	private boolean hideBossBars = true;
	private boolean hideTitlesAndActionBar = true;
	private boolean hideSubtitles = true;
	private boolean hideToastsAndSavingIndicator = true;
	private boolean hideNameTags = true;
	private boolean hideGameplayHud;
	private boolean hideHeldItem;
	private boolean blockOpenScreens = true;
	private boolean randomizeFilename = true;
	private boolean failClosed = true;
	private boolean showNotifications = true;
	private long revision;

	private PrivacyScreenshotConfig() {
	}

	public static PrivacyScreenshotConfig getInstance() {
		return Holder.INSTANCE;
	}

	static PrivacyScreenshotConfig createForTest() {
		return new PrivacyScreenshotConfig();
	}

	public synchronized boolean isEnabled() {
		return this.enabled;
	}

	public synchronized void setEnabled(boolean value) {
		this.enabled = value;
		this.revision++;
	}

	public synchronized boolean isHideChat() {
		return this.hideChat;
	}

	public synchronized void setHideChat(boolean value) {
		this.hideChat = value;
		this.revision++;
	}

	public synchronized boolean isHideDebugOverlay() {
		return this.hideDebugOverlay;
	}

	public synchronized void setHideDebugOverlay(boolean value) {
		this.hideDebugOverlay = value;
		this.revision++;
	}

	public synchronized boolean isHidePlayerList() {
		return this.hidePlayerList;
	}

	public synchronized void setHidePlayerList(boolean value) {
		this.hidePlayerList = value;
		this.revision++;
	}

	public synchronized boolean isHideScoreboard() {
		return this.hideScoreboard;
	}

	public synchronized void setHideScoreboard(boolean value) {
		this.hideScoreboard = value;
		this.revision++;
	}

	public synchronized boolean isHideBossBars() {
		return this.hideBossBars;
	}

	public synchronized void setHideBossBars(boolean value) {
		this.hideBossBars = value;
		this.revision++;
	}

	public synchronized boolean isHideTitlesAndActionBar() {
		return this.hideTitlesAndActionBar;
	}

	public synchronized void setHideTitlesAndActionBar(boolean value) {
		this.hideTitlesAndActionBar = value;
		this.revision++;
	}

	public synchronized boolean isHideSubtitles() {
		return this.hideSubtitles;
	}

	public synchronized void setHideSubtitles(boolean value) {
		this.hideSubtitles = value;
		this.revision++;
	}

	public synchronized boolean isHideToastsAndSavingIndicator() {
		return this.hideToastsAndSavingIndicator;
	}

	public synchronized void setHideToastsAndSavingIndicator(boolean value) {
		this.hideToastsAndSavingIndicator = value;
		this.revision++;
	}

	public synchronized boolean isHideNameTags() {
		return this.hideNameTags;
	}

	public synchronized void setHideNameTags(boolean value) {
		this.hideNameTags = value;
		this.revision++;
	}

	public synchronized boolean isHideGameplayHud() {
		return this.hideGameplayHud;
	}

	public synchronized void setHideGameplayHud(boolean value) {
		this.hideGameplayHud = value;
		this.revision++;
	}

	public synchronized boolean isHideHeldItem() {
		return this.hideHeldItem;
	}

	public synchronized void setHideHeldItem(boolean value) {
		this.hideHeldItem = value;
		this.revision++;
	}

	public synchronized boolean isBlockOpenScreens() {
		return this.blockOpenScreens;
	}

	public synchronized void setBlockOpenScreens(boolean value) {
		this.blockOpenScreens = value;
		this.revision++;
	}

	public synchronized boolean isRandomizeFilename() {
		return this.randomizeFilename;
	}

	public synchronized void setRandomizeFilename(boolean value) {
		this.randomizeFilename = value;
		this.revision++;
	}

	public synchronized boolean isFailClosed() {
		return this.failClosed;
	}

	public synchronized void setFailClosed(boolean value) {
		this.failClosed = value;
		this.revision++;
	}

	public synchronized boolean isShowNotifications() {
		return this.showNotifications;
	}

	public synchronized void setShowNotifications(boolean value) {
		this.showNotifications = value;
		this.revision++;
	}

	public synchronized RuntimeSnapshot runtimeSnapshot() {
		return new RuntimeSnapshot(
				this.revision,
				this.enabled,
				new PrivacyScreenshotPolicy(
						this.hideChat,
						this.hideDebugOverlay,
						this.hidePlayerList,
						this.hideScoreboard,
						this.hideBossBars,
						this.hideTitlesAndActionBar,
						this.hideSubtitles,
						this.hideToastsAndSavingIndicator,
						this.hideNameTags,
						this.hideGameplayHud,
						this.hideHeldItem,
						this.blockOpenScreens,
						this.randomizeFilename,
						this.failClosed,
						this.showNotifications
				)
		);
	}

	public record RuntimeSnapshot(
			long revision,
			boolean enabled,
			PrivacyScreenshotPolicy policy
	) {
	}

	public synchronized void resetToFactoryDefaults() {
		long nextRevision = ConfigFactoryDefaults.nextRevision(this.revision);
		ConfigFactoryDefaults.copyMutableFields(this, new PrivacyScreenshotConfig());
		this.revision = nextRevision;
	}

	public synchronized void save() {
		saveChecked();
	}

	public synchronized boolean saveChecked() {
		return save(configPath());
	}

	synchronized boolean save(Path path) {
		try {
			ShieldJsonFile.writeObject(
					path,
					toJson(),
					MAXIMUM_CONFIG_BYTES,
					"sodium-volt-privacy-screenshot-"
			);
			return true;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			if (SAVE_FAILURE_LOGGED.compareAndSet(false, true)) {
				SodiumVolt.LOGGER.warn("Could not save Privacy Screenshot Mode configuration");
			}
			return false;
		}
	}

	static PrivacyScreenshotConfig load(Path path) {
		PrivacyScreenshotConfig config = new PrivacyScreenshotConfig();
		try {
			JsonObject root = ShieldJsonFile.readObject(path, MAXIMUM_CONFIG_BYTES);
			if (root == null) {
				return config;
			}
			ShieldJsonFile.requireExactKeys(root, CONFIG_KEYS);
			if (ShieldJsonFile.requiredInteger(root, "version") != CONFIG_VERSION) {
				throw new IllegalArgumentException("Unsupported privacy config version");
			}
			config.enabled = value(root, "privacy_screenshot_mode_enabled");
			config.hideChat = value(root, "hide_chat");
			config.hideDebugOverlay = value(root, "hide_debug_overlay");
			config.hidePlayerList = value(root, "hide_player_list");
			config.hideScoreboard = value(root, "hide_scoreboard");
			config.hideBossBars = value(root, "hide_boss_bars");
			config.hideTitlesAndActionBar = value(root, "hide_titles_and_action_bar");
			config.hideSubtitles = value(root, "hide_subtitles");
			config.hideToastsAndSavingIndicator = value(
					root, "hide_toasts_and_saving_indicator"
			);
			config.hideNameTags = value(root, "hide_name_tags");
			config.hideGameplayHud = value(root, "hide_gameplay_hud");
			config.hideHeldItem = value(root, "hide_held_item");
			config.blockOpenScreens = value(root, "block_open_screens");
			config.randomizeFilename = value(root, "randomize_filename");
			config.failClosed = value(root, "fail_closed");
			config.showNotifications = value(root, "show_notifications");
			return config;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			if (LOAD_FAILURE_LOGGED.compareAndSet(false, true)) {
				SodiumVolt.LOGGER.warn("Ignoring invalid Privacy Screenshot Mode configuration");
			}
			return new PrivacyScreenshotConfig();
		}
	}

	JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("version", CONFIG_VERSION);
		root.addProperty("privacy_screenshot_mode_enabled", this.enabled);
		root.addProperty("hide_chat", this.hideChat);
		root.addProperty("hide_debug_overlay", this.hideDebugOverlay);
		root.addProperty("hide_player_list", this.hidePlayerList);
		root.addProperty("hide_scoreboard", this.hideScoreboard);
		root.addProperty("hide_boss_bars", this.hideBossBars);
		root.addProperty("hide_titles_and_action_bar", this.hideTitlesAndActionBar);
		root.addProperty("hide_subtitles", this.hideSubtitles);
		root.addProperty(
				"hide_toasts_and_saving_indicator", this.hideToastsAndSavingIndicator
		);
		root.addProperty("hide_name_tags", this.hideNameTags);
		root.addProperty("hide_gameplay_hud", this.hideGameplayHud);
		root.addProperty("hide_held_item", this.hideHeldItem);
		root.addProperty("block_open_screens", this.blockOpenScreens);
		root.addProperty("randomize_filename", this.randomizeFilename);
		root.addProperty("fail_closed", this.failClosed);
		root.addProperty("show_notifications", this.showNotifications);
		return root;
	}

	private static boolean value(JsonObject object, String key) {
		return ShieldJsonFile.requiredBoolean(object, key);
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir()
				.resolve("sodium-volt-privacy-screenshot.json");
	}

	private static final class Holder {
		private static final PrivacyScreenshotConfig INSTANCE =
				PrivacyScreenshotConfig.load(configPath());
	}
}
