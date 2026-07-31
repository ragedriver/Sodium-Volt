package com.ragedriver.sodiumvolt.client.privacy;

public record PrivacyScreenshotPolicy(
		boolean hideChat,
		boolean hideDebugOverlay,
		boolean hidePlayerList,
		boolean hideScoreboard,
		boolean hideBossBars,
		boolean hideTitlesAndActionBar,
		boolean hideSubtitles,
		boolean hideToastsAndSavingIndicator,
		boolean hideNameTags,
		boolean hideGameplayHud,
		boolean hideHeldItem,
		boolean blockOpenScreens,
		boolean randomizeFilename,
		boolean failClosed,
		boolean showNotifications
) {
}
