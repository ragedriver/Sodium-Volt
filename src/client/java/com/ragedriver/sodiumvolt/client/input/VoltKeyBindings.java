package com.ragedriver.sodiumvolt.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.ragedriver.sodiumvolt.client.config.VoltInspectorConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class VoltKeyBindings {
	private static final VoltInspectorConfig INSPECTOR_CONFIG = VoltInspectorConfig.getInstance();
	private static final KeyMapping.Category VOLT_BINDS_CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath("sodium-volt", "volt_binds")
	);
	private static final KeyMapping TOGGLE_INSPECTOR_HUD = KeyMappingHelper.registerKeyMapping(
			new KeyMapping(
					"key.sodium-volt.toggle_volt_inspector_hud",
					InputConstants.Type.KEYSYM,
					GLFW.GLFW_KEY_LEFT_BRACKET,
					VOLT_BINDS_CATEGORY
			)
	);

	private VoltKeyBindings() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TOGGLE_INSPECTOR_HUD.consumeClick()) {
				// Consume, but ignore, presses made while typing or navigating a screen.
				if (client.level == null || client.gui.screen() != null || client.gui.overlay() != null) {
					continue;
				}
				toggleInspectorHud();
			}
		});
	}

	private static void toggleInspectorHud() {
		boolean showHud = !INSPECTOR_CONFIG.isVoltInspectorEnabled()
				|| !INSPECTOR_CONFIG.isShowInspectorOverlay();
		if (showHud) {
			INSPECTOR_CONFIG.setVoltInspectorEnabled(true);
		}
		INSPECTOR_CONFIG.setShowInspectorOverlay(showHud);
		INSPECTOR_CONFIG.save();
	}
}
