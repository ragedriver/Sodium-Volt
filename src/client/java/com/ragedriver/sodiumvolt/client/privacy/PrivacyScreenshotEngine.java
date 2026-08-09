package com.ragedriver.sodiumvolt.client.privacy;

import com.ragedriver.sodiumvolt.client.config.PrivacyScreenshotConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.nio.file.Path;
import java.util.Optional;

public final class PrivacyScreenshotEngine {
	private static final PrivacyScreenshotConfig CONFIG =
			PrivacyScreenshotConfig.getInstance();
	private static final PrivacyCaptureStateMachine STATE =
			new PrivacyCaptureStateMachine();
	private static final PrivacyScreenshotPolicy INACTIVE_POLICY =
			new PrivacyScreenshotPolicy(
					false, false, false, false, false,
					false, false, false, false, false,
					false, false, false, true, false
			);

	private static volatile PrivacyScreenshotPolicy activePolicy = INACTIVE_POLICY;
	private static boolean captureIssued;
	private static boolean coalescedNotice;

	private PrivacyScreenshotEngine() {
	}

	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register(
				(handler, minecraft) -> reset()
		);
		ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> reset());
	}

	/**
	 * @return true when the vanilla F2 invocation has been handled or denied.
	 */
	public static boolean interceptVanillaCapture(Minecraft minecraft, boolean debugKey) {
		if (debugKey && SharedConstants.DEBUG_PANORAMA_SCREENSHOT) {
			return false;
		}
		if (!CONFIG.isEnabled()) {
			return false;
		}
		PrivacyScreenshotConfig.RuntimeSnapshot snapshot = CONFIG.runtimeSnapshot();
		if (!snapshot.enabled()) {
			return false;
		}
		PrivacyScreenshotPolicy policy = snapshot.policy();
		if (!minecraft.isSameThread()) {
			if (policy.failClosed()) {
				notifyFixed(minecraft, policy, "sodium-volt.notification.privacy.failed");
				return true;
			}
			return false;
		}
		if (hasOpenUi(minecraft) && policy.blockOpenScreens()) {
			notifyFixed(minecraft, policy, "sodium-volt.notification.privacy.screen_blocked");
			return true;
		}
		if (minecraft.level == null) {
			if (policy.failClosed()) {
				notifyFixed(minecraft, policy, "sodium-volt.notification.privacy.unavailable");
				return true;
			}
			return false;
		}
		PrivacyCaptureStateMachine.RequestResult result = STATE.request();
		if (result == PrivacyCaptureStateMachine.RequestResult.ACCEPTED) {
			PrivacyCaptureFastGate.markPending();
		} else {
			coalescedNotice = true;
		}
		return true;
	}

	public static FrameScope beginRenderFrame(Minecraft minecraft) {
		if (!PrivacyCaptureFastGate.hasPendingCapture()) {
			return FrameScope.INACTIVE;
		}
		if (!CONFIG.isEnabled()) {
			reset();
			return FrameScope.INACTIVE;
		}
		PrivacyScreenshotConfig.RuntimeSnapshot snapshot = CONFIG.runtimeSnapshot();
		if (!snapshot.enabled()) {
			reset();
			return FrameScope.INACTIVE;
		}
		PrivacyScreenshotPolicy policy = snapshot.policy();
		if (policy.blockOpenScreens() && hasOpenUi(minecraft)) {
			reset();
			notifyFixed(minecraft, policy, "sodium-volt.notification.privacy.screen_blocked");
			return FrameScope.INACTIVE;
		}
		PrivacyCaptureStateMachine.CaptureScope stateScope = STATE.beginFrame();
		if (!stateScope.active()) {
			PrivacyCaptureFastGate.clear();
			return FrameScope.INACTIVE;
		}
		activePolicy = policy;
		PrivacyCaptureFastGate.beginCapture();
		captureIssued = false;
		return new FrameScope(stateScope, minecraft, policy, true);
	}

	public static void captureRenderedFrame(Minecraft minecraft) {
		if (!PrivacyCaptureFastGate.isCaptureActive() || captureIssued) {
			return;
		}
		if (!CONFIG.isEnabled()) {
			reset();
			return;
		}
		captureIssued = true;
		PrivacyScreenshotPolicy policy = activePolicy;
		if (policy.blockOpenScreens() && hasOpenUi(minecraft)) {
			notifyFixed(
					minecraft,
					policy,
					"sodium-volt.notification.privacy.screen_blocked"
			);
			flushCoalescedNotice(minecraft, policy);
			return;
		}
		String filename = null;
		if (policy.randomizeFilename()) {
			Path screenshots = minecraft.gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR);
			Optional<String> selected = PrivacyFilenameGenerator.choose(screenshots);
			if (selected.isEmpty()) {
				if (policy.failClosed()) {
					notifyFixed(minecraft, policy, "sodium-volt.notification.privacy.failed");
					flushCoalescedNotice(minecraft, policy);
					return;
				}
			} else {
				filename = selected.get();
			}
		}
		try {
			Screenshot.grab(
					minecraft.gameDirectory,
					filename,
					minecraft.gameRenderer.mainRenderTarget(),
					1,
					result -> onScreenshotResult(minecraft, policy, result)
			);
		} catch (RuntimeException | LinkageError exception) {
			notifyFixed(minecraft, policy, "sodium-volt.notification.privacy.failed");
		}
		flushCoalescedNotice(minecraft, policy);
	}

	public static boolean hidesChat() {
		return activePolicy.hideChat();
	}

	public static boolean hidesDebugOverlay() {
		return activePolicy.hideDebugOverlay();
	}

	public static boolean hidesPlayerList() {
		return activePolicy.hidePlayerList();
	}

	public static boolean hidesScoreboard() {
		return activePolicy.hideScoreboard();
	}

	public static boolean hidesBossBars() {
		return activePolicy.hideBossBars();
	}

	public static boolean hidesTitlesAndActionBar() {
		return activePolicy.hideTitlesAndActionBar();
	}

	public static boolean hidesSubtitles() {
		return activePolicy.hideSubtitles();
	}

	public static boolean hidesToastsAndSavingIndicator() {
		return activePolicy.hideToastsAndSavingIndicator();
	}

	public static boolean hidesNameTags() {
		return activePolicy.hideNameTags();
	}

	public static boolean hidesGameplayHud() {
		return activePolicy.hideGameplayHud();
	}

	public static boolean hidesHeldItem() {
		return activePolicy.hideHeldItem();
	}

	public static boolean isCaptureActive() {
		return PrivacyCaptureFastGate.isCaptureActive();
	}

	public static void resetForFactoryDefaults() {
		reset();
	}

	private static void onScreenshotResult(
			Minecraft minecraft,
			PrivacyScreenshotPolicy policy,
			Component result
	) {
		boolean success = result != null
				&& result.getContents() instanceof TranslatableContents contents
				&& "screenshot.success".equals(contents.getKey());
		notifyFixed(
				minecraft,
				policy,
				success
						? "sodium-volt.notification.privacy.saved"
						: "sodium-volt.notification.privacy.failed"
		);
	}

	private static void flushCoalescedNotice(
			Minecraft minecraft,
			PrivacyScreenshotPolicy policy
	) {
		if (!coalescedNotice) {
			return;
		}
		coalescedNotice = false;
		notifyFixed(minecraft, policy, "sodium-volt.notification.privacy.coalesced");
	}

	private static void notifyFixed(
			Minecraft minecraft,
			PrivacyScreenshotPolicy policy,
			String translationKey
	) {
		if (!policy.showNotifications()) {
			return;
		}
		try {
			minecraft.execute(() -> minecraft.showDebugChat(
					Component.translatable(translationKey)
			));
		} catch (RuntimeException ignored) {
			// The client may already be stopping; no private failure detail is retained.
		}
	}

	private static boolean hasOpenUi(Minecraft minecraft) {
		return minecraft.gui.screen() != null || minecraft.gui.overlay() != null;
	}

	private static synchronized void reset() {
		STATE.reset();
		PrivacyCaptureFastGate.clear();
		activePolicy = INACTIVE_POLICY;
		captureIssued = false;
		coalescedNotice = false;
	}

	public static final class FrameScope implements AutoCloseable {
		private static final FrameScope INACTIVE = new FrameScope(
				null, null, INACTIVE_POLICY, false
		);
		private final PrivacyCaptureStateMachine.CaptureScope stateScope;
		private final Minecraft minecraft;
		private final PrivacyScreenshotPolicy policy;
		private final boolean active;
		private boolean closed;

		private FrameScope(
				PrivacyCaptureStateMachine.CaptureScope stateScope,
				Minecraft minecraft,
				PrivacyScreenshotPolicy policy,
				boolean active
		) {
			this.stateScope = stateScope;
			this.minecraft = minecraft;
			this.policy = policy;
			this.active = active;
		}

		@Override
		public void close() {
			if (!this.active || this.closed) {
				return;
			}
			this.closed = true;
			if (!PrivacyCaptureFastGate.isCaptureActive()) {
				this.stateScope.close();
				return;
			}
			if (!captureIssued) {
				notifyFixed(
						this.minecraft,
						this.policy,
						"sodium-volt.notification.privacy.failed"
				);
			}
			PrivacyCaptureFastGate.clear();
			activePolicy = INACTIVE_POLICY;
			captureIssued = false;
			this.stateScope.close();
		}
	}
}
