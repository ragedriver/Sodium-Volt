package com.ragedriver.sodiumvolt.client.debug;

import com.ragedriver.sodiumvolt.SodiumVolt;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;

import java.util.List;

/** Cached F3 status text and allocation-free anchor matching. */
public final class SodiumVoltDebugLine {
	static final String FALLBACK_VERSION = "unknown";
	private static final String SODIUM_RENDERER_ANCHOR = "Sodium Renderer (";
	private static final String VOLT_STATUS_PREFIX = "Sodium Volt Loaded (";
	private static final String CACHED_LINE = format(resolveRuntimeVersion());

	private SodiumVoltDebugLine() {
	}

	public static boolean insertAfterSodiumRenderer(List<String> lines) {
		return insertAfterSodiumRenderer(lines, CACHED_LINE);
	}

	public static boolean insertIfDebugVisible(boolean debugVisible, List<String> lines) {
		return debugVisible && insertAfterSodiumRenderer(lines);
	}

	static boolean insertAfterSodiumRenderer(List<String> lines, String voltLine) {
		if (lines == null || voltLine == null || voltLine.isEmpty()) {
			return false;
		}
		int anchorIndex = -1;
		int ownedLineCount = 0;
		for (int index = 0; index < lines.size(); index++) {
			String line = lines.get(index);
			if (isVoltStatusLine(line)) {
				ownedLineCount++;
			}
			if (anchorIndex < 0 && startsWithIgnoringFormatting(line, SODIUM_RENDERER_ANCHOR)) {
				anchorIndex = index;
			}
		}
		if (anchorIndex < 0) {
			return false;
		}
		if (ownedLineCount == 1 && anchorIndex + 1 < lines.size()
				&& voltLine.equals(lines.get(anchorIndex + 1))) {
			return false;
		}
		if (ownedLineCount == 0) {
			try {
				lines.add(anchorIndex + 1, voltLine);
				return true;
			} catch (UnsupportedOperationException exception) {
				return false;
			}
		}

		try {
			int updatedAnchorIndex = anchorIndex;
			for (int index = lines.size() - 1; index >= 0; index--) {
				if (isVoltStatusLine(lines.get(index))) {
					lines.remove(index);
					if (index < updatedAnchorIndex) {
						updatedAnchorIndex--;
					}
				}
			}
			lines.add(updatedAnchorIndex + 1, voltLine);
			return true;
		} catch (UnsupportedOperationException exception) {
			return false;
		}
	}

	static String format(String version) {
		String resolved = sanitizeVersion(version);
		return ChatFormatting.YELLOW + "Sodium Volt Loaded (" + resolved + ")";
	}

	private static String sanitizeVersion(String version) {
		if (version == null || version.isBlank()) {
			return FALLBACK_VERSION;
		}
		String trimmed = version.trim();
		StringBuilder safe = null;
		for (int index = 0; index < trimmed.length(); index++) {
			char character = trimmed.charAt(index);
			boolean formatting = character == ChatFormatting.PREFIX_CODE
					&& index + 1 < trimmed.length();
			if (formatting || Character.isISOControl(character)) {
				if (safe == null) {
					safe = new StringBuilder(trimmed.length());
					safe.append(trimmed, 0, index);
				}
				if (formatting) {
					index++;
				}
			} else if (safe != null) {
				safe.append(character);
			}
		}
		String result = safe == null ? trimmed : safe.toString();
		return result.isBlank() ? FALLBACK_VERSION : result;
	}

	private static String resolveRuntimeVersion() {
		try {
			return FabricLoader.getInstance()
					.getModContainer(SodiumVolt.MOD_ID)
					.map(container -> container.getMetadata().getVersion().getFriendlyString())
					.orElse(FALLBACK_VERSION);
		} catch (RuntimeException | LinkageError exception) {
			return FALLBACK_VERSION;
		}
	}

	private static boolean startsWithIgnoringFormatting(String value, String expected) {
		if (value == null) {
			return false;
		}
		int valueIndex = 0;
		int expectedIndex = 0;
		while (valueIndex < value.length() && expectedIndex < expected.length()) {
			char character = value.charAt(valueIndex);
			if (character == ChatFormatting.PREFIX_CODE && valueIndex + 1 < value.length()) {
				valueIndex += 2;
				continue;
			}
			if (character != expected.charAt(expectedIndex)) {
				return false;
			}
			valueIndex++;
			expectedIndex++;
		}
		return expectedIndex == expected.length();
	}

	private static boolean isVoltStatusLine(String value) {
		if (!startsWithIgnoringFormatting(value, VOLT_STATUS_PREFIX)) {
			return false;
		}
		int valueIndex = 0;
		int prefixIndex = 0;
		while (valueIndex < value.length() && prefixIndex < VOLT_STATUS_PREFIX.length()) {
			char character = value.charAt(valueIndex++);
			if (character == ChatFormatting.PREFIX_CODE && valueIndex < value.length()) {
				valueIndex++;
			} else {
				prefixIndex++;
			}
		}
		boolean hasVersionCharacter = false;
		while (valueIndex < value.length()) {
			char character = value.charAt(valueIndex++);
			if (character == ChatFormatting.PREFIX_CODE && valueIndex < value.length()) {
				valueIndex++;
				continue;
			}
			if (character != ')') {
				hasVersionCharacter = true;
				continue;
			}
			while (valueIndex < value.length()) {
				if (value.charAt(valueIndex) != ChatFormatting.PREFIX_CODE
						|| valueIndex + 1 >= value.length()) {
					return false;
				}
				valueIndex += 2;
			}
			return hasVersionCharacter;
		}
		return false;
	}
}
