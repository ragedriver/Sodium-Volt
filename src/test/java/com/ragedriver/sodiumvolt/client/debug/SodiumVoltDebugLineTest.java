package com.ragedriver.sodiumvolt.client.debug;

import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;

public final class SodiumVoltDebugLineTest {
	private SodiumVoltDebugLineTest() {
	}

	public static void main(String[] arguments) {
		testExactTextAndFormatting();
		testFallbackVersion();
		testImmediatePlacementAndIdempotency();
		testWrongPositionAndDuplicateCleanup();
		testOldFormattedVersionCleanup();
		testUnrelatedSimilarTextPreserved();
		testAnchorAbsentNoOp();
		testHiddenDebugOverlayNoOp();
		testUnmodifiableInputFailsClosed();
		System.out.println("Sodium Volt debug overlay logic tests passed");
	}

	private static void testExactTextAndFormatting() {
		String line = SodiumVoltDebugLine.format("1.0.2+mc26.2");
		check(line.equals(ChatFormatting.YELLOW
					+ "Sodium Volt Loaded (1.0.2+mc26.2)"),
				"the exact requested text must use one yellow prefix");
		check(ChatFormatting.stripFormatting(line)
					.equals("Sodium Volt Loaded (1.0.2+mc26.2)"),
				"formatting must not alter the visible text or version");
		String hostile = SodiumVoltDebugLine.format("1.0" + ChatFormatting.RED + "spoof");
		check(hostile.equals(ChatFormatting.YELLOW + "Sodium Volt Loaded (1.0spoof)"),
				"metadata formatting must not escape the line's yellow style");
	}

	private static void testFallbackVersion() {
		check(SodiumVoltDebugLine.format(null).equals(
				ChatFormatting.YELLOW + "Sodium Volt Loaded (unknown)"
		), "missing metadata must use the safe fallback");
		check(SodiumVoltDebugLine.format("   ").equals(
				ChatFormatting.YELLOW + "Sodium Volt Loaded (unknown)"
		), "blank metadata must use the safe fallback");
	}

	private static void testImmediatePlacementAndIdempotency() {
		String volt = SodiumVoltDebugLine.format("test-version");
		ArrayList<String> lines = new ArrayList<>(List.of(
				"Minecraft 26.2",
				ChatFormatting.GREEN + "Sodium Renderer (0.9.1+mc26.2)",
				"Chunk renderer: ready"
		));
		check(SodiumVoltDebugLine.insertAfterSodiumRenderer(lines, volt),
				"the Sodium Renderer anchor must accept one Volt line");
		check(lines.get(2).equals(volt)
					&& lines.get(3).equals("Chunk renderer: ready"),
				"Volt must be immediately below Sodium without moving Sodium details");
		check(!SodiumVoltDebugLine.insertAfterSodiumRenderer(lines, volt)
					&& count(lines, volt) == 1,
				"repeated modifiers and renders must never duplicate the line");
	}

	private static void testAnchorAbsentNoOp() {
		String volt = SodiumVoltDebugLine.format("test-version");
		String old = SodiumVoltDebugLine.format("old-version");
		ArrayList<String> lines = new ArrayList<>(List.of(
				"Minecraft 26.2", old, "Renderer unknown"
		));
		check(!SodiumVoltDebugLine.insertAfterSodiumRenderer(lines, volt)
					&& lines.equals(List.of("Minecraft 26.2", old, "Renderer unknown")),
				"an absent Sodium line must leave the overlay untouched");
	}

	private static void testWrongPositionAndDuplicateCleanup() {
		String sodium = ChatFormatting.GREEN + "Sodium Renderer (0.9.1+mc26.2)";
		String volt = SodiumVoltDebugLine.format("current");
		ArrayList<String> lines = new ArrayList<>(List.of(
				volt, "before", sodium, "detail", volt, "after"
		));
		check(SodiumVoltDebugLine.insertAfterSodiumRenderer(lines, volt),
				"wrong-position and duplicate exact lines must be normalized");
		check(lines.equals(List.of("before", sodium, volt, "detail", "after")),
				"normalization must preserve every unrelated line and its order");
		check(count(lines, volt) == 1,
				"normalization must leave exactly one current-version line");
	}

	private static void testOldFormattedVersionCleanup() {
		String sodium = ChatFormatting.GREEN + "Sodium Renderer (0.9.1+mc26.2)";
		String volt = SodiumVoltDebugLine.format("current");
		String oldFormatted = ChatFormatting.RED + "Sodium " + ChatFormatting.GOLD
				+ "Volt Loaded (old-version)" + ChatFormatting.RESET;
		ArrayList<String> lines = new ArrayList<>(List.of(
				oldFormatted, "before", sodium, oldFormatted, "detail"
		));
		check(SodiumVoltDebugLine.insertAfterSodiumRenderer(lines, volt),
				"old-version and differently formatted owned lines must be replaced");
		check(lines.equals(List.of("before", sodium, volt, "detail")),
				"all owned variants around the anchor must collapse to one yellow current line");
	}

	private static void testUnrelatedSimilarTextPreserved() {
		String sodium = ChatFormatting.GREEN + "Sodium Renderer (0.9.1+mc26.2)";
		String volt = SodiumVoltDebugLine.format("current");
		String bracketed = "Sodium Volt Loaded [third-party]";
		String prefixed = "Other Sodium Volt Loaded (third-party)";
		String trailing = "Sodium Volt Loaded (third-party) details";
		ArrayList<String> lines = new ArrayList<>(List.of(
				bracketed, sodium, prefixed, trailing
		));
		check(SodiumVoltDebugLine.insertAfterSodiumRenderer(lines, volt),
				"the current line must still be inserted around similar unrelated text");
		check(lines.equals(List.of(bracketed, sodium, volt, prefixed, trailing)),
				"only the exact Volt-owned status grammar may be removed");
	}

	private static void testHiddenDebugOverlayNoOp() {
		String sodium = ChatFormatting.GREEN + "Sodium Renderer (0.9.1+mc26.2)";
		ArrayList<String> lines = new ArrayList<>(List.of(sodium));
		check(!SodiumVoltDebugLine.insertIfDebugVisible(false, lines)
					&& lines.equals(List.of(sodium)),
				"the status line must not appear while F3 is hidden");
	}

	private static void testUnmodifiableInputFailsClosed() {
		String sodium = ChatFormatting.GREEN + "Sodium Renderer (0.9.1+mc26.2)";
		List<String> immutable = List.of(sodium);
		check(!SodiumVoltDebugLine.insertAfterSodiumRenderer(
				immutable, SodiumVoltDebugLine.format("test-version")
		), "an immutable list must remain unchanged rather than crash the overlay");
		check(immutable.equals(List.of(sodium)),
				"failed immutable insertion must not replace the caller's list");
		String old = SodiumVoltDebugLine.format("old-version");
		List<String> immutableStale = List.of(old, sodium);
		check(!SodiumVoltDebugLine.insertAfterSodiumRenderer(
				immutableStale, SodiumVoltDebugLine.format("test-version")
		), "immutable stale-line cleanup must fail closed");
		check(immutableStale.equals(List.of(old, sodium)),
				"failed immutable cleanup must not partially remove an old line");
	}

	private static int count(List<String> lines, String expected) {
		int count = 0;
		for (String line : lines) {
			if (expected.equals(line)) {
				count++;
			}
		}
		return count;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("Debug overlay: " + message);
		}
	}
}
