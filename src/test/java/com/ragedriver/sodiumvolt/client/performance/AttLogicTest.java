package com.ragedriver.sodiumvolt.client.performance;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class AttLogicTest {
	private AttLogicTest() {
	}

	public static void main(String[] arguments) {
		testIdentifiersAndJson();
		testInvisibleAndKeepalive();
		testElapsedBoundaries();
		testDistanceApcAndResumeCadence();
		testProtectedAndExemptDecisions();
		testBudgetsAndExemptionReserve();
		testStateSpriteMappingAndFailOpenBounds();
		testAnimationCycleContextComposition();
		testPersistentMappingFailOpenLatch();
		testVisibilityGenerationRules();
		testFailOpenRetryAndSaturation();
		testExactCriticalTextures();
		System.out.println("ATT logic tests passed");
	}

	private static void testIdentifiersAndJson() {
		check(AttExemptionParsing.isValidIdentifier("minecraft:block/water_still"),
				"valid exact texture IDs must be accepted");
		check(!AttExemptionParsing.isValidIdentifier("minecraft:block/*"),
				"wildcards must not become implicit substring exemptions");
		check(!AttExemptionParsing.isValidIdentifier("Minecraft:block/fire_0"),
				"IDs must use canonical lowercase syntax");
		String[] normalized = AttExemptionParsing.normalizeUserEntries(new String[]{
				"minecraft:block/fire_0", "minecraft:block/fire_0", "bad id"
		});
		check(normalized.length == 1, "user IDs must be validated and deduplicated");
		AttResourceExemptionJson.ParseResult parsed = AttResourceExemptionJson.parse(
				"{\"textures\":[\"minecraft:block/fire_0\",\"bad id\"]}"
		);
		check(parsed.identifiers().length == 1 && parsed.truncated(),
				"resource JSON must reject malformed IDs without widening exemptions");
		expectFailure(() -> AttResourceExemptionJson.parse("{\"textures\":{}}"),
				"malformed exemption JSON must fail as one bounded resource");
	}

	private static void testInvisibleAndKeepalive() {
		check(decide(false, false, 0, 50, Long.MIN_VALUE, 1.0D, false, false, false)
						== AttPolicy.Decision.SKIP_INVISIBLE,
				"zero keepalive must fully pause unseen animations");
		check(decide(false, false, 5, 50, 47, 1.0D, false, false, false)
						== AttPolicy.Decision.TICK_NORMAL,
				"nonzero keepalive must offer a bounded escape hatch");
		check(decide(false, false, 5, 60, 47, 1.0D, false, false, false)
						== AttPolicy.Decision.SKIP_INVISIBLE,
				"expired keepalive must pause again");
		check(decide(false, false, 5, 50, Long.MIN_VALUE, 1.0D, false, false, false)
						== AttPolicy.Decision.SKIP_INVISIBLE,
				"a never-visible sprite must not become recent through sentinel overflow");
	}

	private static void testElapsedBoundaries() {
		check(AttPolicy.elapsed(5L, 3L) == 2L,
				"normal elapsed time must remain exact");
		check(AttPolicy.elapsed(Long.MAX_VALUE, Long.MAX_VALUE - 1L) == 1L,
				"elapsed time near the upper boundary must remain exact");
		check(AttPolicy.elapsed(0L, Long.MIN_VALUE) == Long.MAX_VALUE,
				"the never-visible sentinel must produce an expired duration");
		check(AttPolicy.elapsed(3L, 4L) == Long.MAX_VALUE,
				"a clock moving backwards must fail safely as expired");
		check(AttPolicy.elapsed(Long.MAX_VALUE, -1L) == Long.MAX_VALUE,
				"subtraction overflow must saturate as expired");
	}

	private static void testDistanceApcAndResumeCadence() {
		check(decide(true, false, 0, 8, 8, 2_000.0D, false, false, false)
						== AttPolicy.Decision.TICK_NORMAL,
				"distant animations must tick on their cadence boundary");
		check(decide(true, false, 0, 9, 9, 2_000.0D, false, false, false)
						== AttPolicy.Decision.SKIP_CADENCE,
				"distant animations must skip between cadence boundaries");
		check(decide(true, false, 0, 3, 3, 1.0D, false, true, false)
						== AttPolicy.Decision.SKIP_CADENCE,
				"APC severe pressure must compose as an every-other cadence");
		check(decide(true, false, 0, 3, 3, 2_000.0D, true, true, false)
						== AttPolicy.Decision.TICK_NORMAL,
				"new visibility must resume immediately and never catch up multiple ticks");
	}

	private static void testProtectedAndExemptDecisions() {
		check(policy(true, false, false, false, true, false, false, false, 0,
				1, Long.MIN_VALUE, false, 0, 1, 1, false, false, false, true)
						== AttPolicy.Decision.TICK_PROTECTED,
				"unknown or incomplete visibility must fail open");
		check(policy(true, false, true, false, false, false, false, false, 0,
				1, Long.MIN_VALUE, false, 0, 1, 1, false, false, false, true)
						== AttPolicy.Decision.TICK_PROTECTED,
				"interface atlases must remain full speed");
		check(policy(true, false, false, false, false, true, true, false, 0,
				1, Long.MIN_VALUE, false, 0, 1, 1, false, false, false, false)
						== AttPolicy.Decision.TICK_EXEMPT,
				"the dedicated exemption reserve must bypass the normal budget");
		check(policy(true, false, false, false, false, true, false, false, 0,
				1, Long.MIN_VALUE, false, 0, 1, 1, false, false, false, true)
						== AttPolicy.Decision.TICK_NORMAL,
				"exempt overflow must fall back to the normal budget");
	}

	private static void testBudgetsAndExemptionReserve() {
		AttTickBudget budget = new AttTickBudget();
		budget.beginClientTick(1L, 2);
		check(budget.claimNormal() && budget.claimNormal() && !budget.claimNormal(),
				"per-atlas normal budget must be a hard cap");
		int exemptAccepted = 0;
		while (budget.claimExemption()) {
			exemptAccepted++;
		}
		check(exemptAccepted == AttTickBudget.EXEMPTION_RESERVE,
				"exemption reserve must have the exact hard cap");
		budget.beginClientTick(1L, 2);
		check(budget.claimNormal() && budget.claimNormal() && !budget.claimNormal(),
				"a second atlas has its own normal cap but shares the global epoch cap");
		budget.beginClientTick(1L, 2);
		check(!budget.claimNormal(),
				"global budget must remain a genuine hard cap across atlases");
		budget.beginClientTick(2L, 2);
		check(budget.claimNormal(), "a new client tick must restore global capacity");
	}

	private static void testStateSpriteMappingAndFailOpenBounds() {
		Object staticSprite = new Object();
		Object animatedA = new Object();
		Object animatedB = new Object();
		Object stateA = new Object();
		Object stateB = new Object();
		AttStateSpriteMapping<Object, Object> mapping = AttStateSpriteMapping.build(
				List.of(staticSprite, animatedA, animatedB),
				List.of(stateA, stateB),
				sprite -> sprite != staticSprite,
				8,
				4
		);
		check(mapping.isValid() && mapping.stateAt(0) == stateA
						&& mapping.spriteAt(0) == animatedA && mapping.spriteAt(1) == animatedB,
				"mapping must preserve TextureAtlas animated state order");
		mapping.release();
		check(!mapping.isValid() && !mapping.retainsForTesting(stateA)
						&& !mapping.retainsForTesting(animatedA),
				"atlas clear/disable must release all strong references");

		AttStateSpriteMapping<Object, Object> mismatch = AttStateSpriteMapping.build(
				List.of(animatedA),
				List.of(stateA, stateB),
				sprite -> true,
				8,
				4
		);
		check(!mismatch.isValid(),
				"animated sprite/state mismatch must be per-atlas vanilla fail-open");
		ArrayList<Object> tooMany = new ArrayList<>();
		for (int index = 0; index < 5; index++) {
			tooMany.add(new Object());
		}
		AttStateSpriteMapping<Object, Object> overflow = AttStateSpriteMapping.build(
				tooMany,
				List.of(stateA),
				sprite -> true,
				4,
				4
		);
		check(!overflow.isValid() && overflow.hadRawOverflow(),
				"raw atlas overflow must be bounded and fail open");
	}

	private static void testAnimationCycleContextComposition() {
		AttAnimationCycleContext.clearForTesting();
		Identifier outer = Identifier.parse("minecraft:textures/atlas/blocks.png");
		Identifier inner = Identifier.parse("minecraft:textures/atlas/particles.png");
		boolean outerInstalled = AttAnimationCycleContext.push(outer, true);
		check(outerInstalled && AttAnimationCycleContext.atlasLocation().equals(outer)
				&& AttAnimationCycleContext.isWarmup(),
				"an atlas cycle must expose its location and warmup state to state ticks");
		boolean innerInstalled = AttAnimationCycleContext.push(inner, false);
		check(innerInstalled && AttAnimationCycleContext.atlasLocation().equals(inner)
				&& !AttAnimationCycleContext.isWarmup(),
				"a nested atlas cycle must temporarily replace the outer context");
		AttAnimationCycleContext.pop(innerInstalled);
		check(AttAnimationCycleContext.atlasLocation().equals(outer)
				&& AttAnimationCycleContext.isWarmup(),
				"unwinding must restore the prior atlas context");
		AttAnimationCycleContext.pop(outerInstalled);
		check(AttAnimationCycleContext.atlasLocation() == null
				&& AttAnimationCycleContext.depthForTesting() == 0,
				"the final unwind must clear all atlas references");

		boolean[] installed = new boolean[9];
		for (int index = 0; index < installed.length; index++) {
			installed[index] = AttAnimationCycleContext.push(outer, false);
		}
		check(!installed[8] && AttAnimationCycleContext.atlasLocation() == null,
				"excessive third-party recursion must fail open instead of using a wrong atlas");
		for (int index = installed.length - 1; index >= 0; index--) {
			AttAnimationCycleContext.pop(installed[index]);
		}
		check(AttAnimationCycleContext.atlasLocation() == null
				&& AttAnimationCycleContext.depthForTesting() == 0,
				"overflow unwind must leave no stale render-thread context");
		AttAnimationCycleContext.clearForTesting();
	}

	private static void testVisibilityGenerationRules() {
		check(AttVisibilityLogic.nextGeneration(Integer.MAX_VALUE) == 1,
				"visibility generation must wrap without using the unknown sentinel");
		check(AttVisibilityLogic.minimumDistance(40.0F, 12.0F) == 12.0F,
				"multiple visible sections must retain the nearest real distance");
		check(AttVisibilityLogic.isNewlyVisible(2, 3),
				"a sprite absent from the prior complete generation must resume immediately");
		check(!AttVisibilityLogic.canPublishGeneration(true, false, true),
				"a scan truncated by section/sprite hard caps must never publish partial visibility");
		check(AttVisibilityLogic.canPublishGeneration(true, false, false),
				"a complete successful scan may publish its generation");
	}

	private static void testPersistentMappingFailOpenLatch() {
		AttMappingFailOpenLatch latch = new AttMappingFailOpenLatch();
		check(latch.canBuild(), "a new atlas may build its mapping once");
		check(latch.failOpen(), "the first invalid mapping must publish one fallback event");
		check(!latch.canBuild() && !latch.failOpen(),
				"a stable invalid mapping must stay vanilla without repeated rebuilds or stats");
		latch.resetForUpload();
		check(latch.canBuild(), "atlas upload/resource rebuild must permit one deliberate retry");
		check(latch.failOpen(), "an invalid post-upload rebuild must latch again");
		latch.observeMasterDisabled();
		check(latch.canBuild(), "master OFF observation must permit a later OFF to ON retry");
		latch.blockUntilUpload();
		check(!latch.canBuild(), "atlas clear must block lazy mapping until its matching upload");
		latch.resetForUpload();
		check(latch.canBuild(), "the matching upload must release the clear lifecycle block");
	}

	private static void testFailOpenRetryAndSaturation() {
		AttFailOpenLatch latch = new AttFailOpenLatch();
		latch.fail();
		check(!latch.canRun(), "runtime faults must latch ATT fail-open");
		latch.observeDisabled();
		check(latch.canRun(), "OFF to ON must permit a retry");
		latch.fail();
		latch.resetForReload();
		check(latch.canRun(), "resource reload must permit a retry");
		check(AttPolicy.saturatingAdd(Long.MAX_VALUE - 1L, 10L) == Long.MAX_VALUE,
				"statistics must saturate rather than wrap");
	}

	private static void testExactCriticalTextures() {
		String[] expected = {
				"minecraft:block/water_still", "minecraft:block/water_flow",
				"minecraft:block/lava_still", "minecraft:block/lava_flow",
				"minecraft:block/fire_0", "minecraft:block/fire_1",
				"minecraft:block/soul_fire_0", "minecraft:block/soul_fire_1",
				"minecraft:block/nether_portal", "minecraft:item/clock",
				"minecraft:item/compass", "minecraft:item/recovery_compass"
		};
		check(List.of(AttCriticalTextures.EXACT_IDS).equals(List.of(expected)),
				"critical exemptions must remain the documented exact IDs");
	}

	private static AttPolicy.Decision decide(
			boolean visible,
			boolean unknown,
			int keepalive,
			long tick,
			long lastVisible,
			double distanceSquared,
			boolean resume,
			boolean apc,
			boolean noBudget
	) {
		return policy(true, false, false, false, unknown, false, false, visible,
				keepalive, tick, lastVisible, true, distanceSquared, 32.0D * 32.0D,
				4, true, resume, apc, !noBudget);
	}

	private static AttPolicy.Decision policy(
			boolean enabled, boolean warmup, boolean interfaceProtected,
			boolean screenProtected, boolean unknownActive, boolean exempt,
			boolean exemptionReserve, boolean visible, int keepalive, long tick,
			long lastVisible, boolean distanceAware, double distanceSquared,
			double fullSpeedDistanceSquared, int interval, boolean immediateResume,
			boolean resumePending, boolean apcPressure, boolean normalBudget
	) {
		return AttPolicy.decide(
				enabled, warmup, interfaceProtected, screenProtected, unknownActive,
				exempt, exemptionReserve, visible, true, keepalive, tick, lastVisible,
				distanceAware, distanceSquared, fullSpeedDistanceSquared, interval,
				immediateResume, resumePending, apcPressure, normalBudget
		);
	}

	private static void expectFailure(Runnable runnable, String message) {
		try {
			runnable.run();
		} catch (RuntimeException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
