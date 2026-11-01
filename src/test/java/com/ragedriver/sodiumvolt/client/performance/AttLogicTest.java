package com.ragedriver.sodiumvolt.client.performance;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class AttLogicTest {
	private AttLogicTest() {
	}

	public static void main(String[] arguments) {
		testAtlasTextureEligibility();
		testIdentifiersAndJson();
		testCollidingIdentifiers();
		testDisabledExemptionLoadingAndToggle();
		testInvisibleAndKeepalive();
		testElapsedBoundaries();
		testDistanceApcAndResumeCadence();
		testProtectedAndExemptDecisions();
		testBudgetsAndExemptionReserve();
		testStateSpriteMappingAndFailOpenBounds();
		testAnimationCycleContextComposition();
		testDisabledTickTransitionCleanup();
		testDisabledReloadDeferral();
		testReloadGenerationRejectsStalePublication();
		testReloadPublicationAcrossThreads();
		testStatisticsOffHasNoCounterChurn();
		testPersistentMappingFailOpenLatch();
		testVisibilityGenerationRules();
		testFailOpenRetryAndSaturation();
		testExactCriticalTextures();
		System.out.println("ATT logic tests passed");
	}

	private static void testAtlasTextureEligibility() {
		check(AttAtlasPolicy.isWorldAtlasTexture(Identifier.parse("minecraft:textures/atlas/blocks.png")),
				"block atlas textures remain eligible for world animation throttling");
		check(AttAtlasPolicy.isWorldAtlasTexture(Identifier.parse("minecraft:textures/atlas/particles.png")),
				"particle atlas textures remain eligible for world animation throttling");
		check(!AttAtlasPolicy.isWorldAtlasTexture(Identifier.parse("minecraft:blocks")),
				"atlas definition IDs must not be confused with texture IDs");
		check(!AttAtlasPolicy.isWorldAtlasTexture(Identifier.parse("minecraft:textures/atlas/gui.png"))
				&& !AttAtlasPolicy.isWorldAtlasTexture(Identifier.parse("minecraft:textures/atlas/items.png"))
				&& !AttAtlasPolicy.isWorldAtlasTexture(Identifier.parse("custom:textures/atlas/blocks.png"))
				&& !AttAtlasPolicy.isWorldAtlasTexture(null),
				"interface, item, custom, and unknown atlases stay protected");
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

	private static void testCollidingIdentifiers() {
		// Reproduce the old 32-probe failure using actual Minecraft Identifier hashes.
		List<String> colliding = new ArrayList<>();
		for (int candidate = 0; colliding.size() < 128 && candidate < 1_000_000; candidate++) {
			String value = "attack:texture_" + candidate;
			int hash = Identifier.parse(value).hashCode();
			hash ^= hash >>> 16;
			hash *= 0x7FEB352D;
			hash ^= hash >>> 15;
			hash *= 0x846CA68B;
			hash ^= hash >>> 16;
			if ((hash & 255) == 0) {
				colliding.add(value);
			}
		}
		check(colliding.size() == 128, "collision fixture must fill both accepted lists");
		String json = "{\"textures\":[\"" + String.join("\",\"", colliding.subList(0, 33)) + "\"]}";
		AttResourceExemptionJson.ParseResult parsed = AttResourceExemptionJson.parse(json);
		check(parsed.identifiers().length == 33 && !parsed.truncated(),
				"the original attack must remain valid bounded resource JSON");
		AttIdentifierLookup lookup = new AttIdentifierLookup();
		for (String value : parsed.identifiers()) {
			check(lookup.add(value), "all 33 colliding resource identifiers must be accepted");
		}
		for (String value : colliding.subList(33, 128)) {
			check(lookup.add(value), "all accepted user plus resource entries must fit");
		}
		check(lookup.size() == 128 && !lookup.add("attack:overflow"),
				"storage must stay bounded even if a caller exceeds parser limits");
		for (String value : colliding) {
			check(lookup.contains(Identifier.parse(value)) && !lookup.add(value),
					"collision and capacity pressure must preserve exact membership and deduplication");
		}
		check(!lookup.contains(null) && !lookup.contains(Identifier.parse("other:texture_0"))
				&& !lookup.contains(Identifier.parse("attack:overflow")),
				"absent IDs must not gain exemptions");
	}

	private static void testDisabledExemptionLoadingAndToggle() {
		AttResourceExemptionLoader.LoadResult disabled = AttResourceExemptionLoader.load(null, false);
		check(disabled.identifiers().length == 0 && disabled.filesRead() == 0
				&& disabled.bytesRead() == 0 && !disabled.truncated(),
				"disabled exemption lists must never access even the resource manager");
		AttExemptionReloadGate gate = new AttExemptionReloadGate();
		long disabledGeneration = gate.onEnabledTick(false);
		gate.complete(disabledGeneration);
		check(gate.ready() && gate.onEnabledTick(false) == 0L,
				"disabled lists must allow scheduling without repeating discovery");
		long enabledGeneration = gate.onEnabledTick(true);
		check(enabledGeneration != 0L && !gate.ready(),
				"enabling lists must request their current resources without a resource reload");
		check(gate.onEnabledTick(false) == 0L,
				"a setting change during discovery must coalesce with the active scan");
		AttExemptionReloadGate.Completion stale = gate.complete(enabledGeneration);
		check(!stale.publish() && stale.followUpGeneration() != 0L,
				"a stale enabled-list scan must not overwrite the disabled-list generation");
		gate.complete(stale.followUpGeneration());
		check(gate.ready(), "the latest disabled setting must become schedulable");
		long reload = gate.onResourceReload(true, false);
		gate.complete(reload);
		check(gate.onEnabledTick(false) == 0L && gate.onEnabledTick(true) != 0L,
				"a disabled-list reload must still permit later activation");
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
		for (int sprite = 0; sprite < 10_000; sprite++) {
			check(!AttAnimationCycleContext.hasActiveCycle(),
					"disabled animation-state hooks use one primitive cycle gate");
		}
		check(!AttAnimationCycleContext.initializedForTesting(),
				"disabled animation-state hooks never initialize their ThreadLocal");
		Identifier outer = Identifier.parse("minecraft:textures/atlas/blocks.png");
		Identifier inner = Identifier.parse("minecraft:textures/atlas/particles.png");
		boolean outerInstalled = AttAnimationCycleContext.push(outer, true);
		AttRuntimeSettings.AtlasCycle outerCycle = AttAnimationCycleContext.current();
		check(outerInstalled && AttAnimationCycleContext.hasActiveCycle()
				&& AttAnimationCycleContext.initializedForTesting()
				&& AttAnimationCycleContext.atlasLocation().equals(outer)
				&& AttAnimationCycleContext.isWarmup(),
				"an atlas cycle must expose its location and warmup state to state ticks");
		boolean innerInstalled = AttAnimationCycleContext.push(inner, false);
		AttRuntimeSettings.AtlasCycle innerCycle = AttAnimationCycleContext.current();
		check(innerInstalled && AttAnimationCycleContext.atlasLocation().equals(inner)
				&& !AttAnimationCycleContext.isWarmup() && innerCycle != outerCycle,
				"a nested atlas cycle must temporarily replace the outer context");
		AttAnimationCycleContext.pop(innerInstalled);
		check(AttAnimationCycleContext.atlasLocation().equals(outer)
				&& AttAnimationCycleContext.isWarmup(),
				"unwinding must restore the prior atlas context");
		AttAnimationCycleContext.pop(outerInstalled);
		check(AttAnimationCycleContext.atlasLocation() == null
				&& AttAnimationCycleContext.depthForTesting() == 0,
				"the final unwind must clear all atlas references");
		boolean reusedInstalled = AttAnimationCycleContext.push(inner, false);
		check(reusedInstalled && AttAnimationCycleContext.current() == outerCycle
					&& AttAnimationCycleContext.atlasLocation().equals(inner)
					&& !AttAnimationCycleContext.isWarmup(),
				"stable atlas cycles must reuse their depth slot and publish newly captured settings");
		AttAnimationCycleContext.pop(reusedInstalled);

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

	private static void testDisabledTickTransitionCleanup() {
		AttRuntimeLifecycle lifecycle = new AttRuntimeLifecycle();
		check(!lifecycle.observeAndShouldCleanup(false)
					&& !lifecycle.observeAndShouldCleanup(false),
				"stable disabled ATT ticks must skip repeated counter and visibility cleanup");
		check(!lifecycle.observeAndShouldCleanup(true)
					&& lifecycle.observeAndShouldCleanup(false)
					&& !lifecycle.observeAndShouldCleanup(false),
				"ATT disable must clean up exactly once and re-enable must establish a new transition");
	}

	private static void testDisabledReloadDeferral() {
		AttExemptionReloadGate gate = new AttExemptionReloadGate();
		check(gate.onResourceReload(false, true) == 0L,
				"disabled ATT reloads must skip exemption resource discovery");
		long activation = gate.onEnabledTick(true);
		check(activation != 0L,
				"enabling after a skipped reload must discover current exemptions once");
		check(!gate.ready(),
				"ATT must fail open while deferred exemptions are still stale");
		check(gate.complete(activation).publish(),
				"the current activation scan must publish");
		check(gate.ready() && gate.onEnabledTick(true) == 0L,
				"stable enabled ticks must not repeat exemption discovery");
		long reload = gate.onResourceReload(true, true);
		check(reload != 0L,
				"enabled ATT reloads must discover updated exemptions immediately");
		gate.complete(reload);
		check(gate.onEnabledTick(true) == 0L,
				"a completed enabled reload must not be repeated on the next tick");
	}

	private static void testReloadGenerationRejectsStalePublication() {
		AttExemptionReloadGate gate = new AttExemptionReloadGate();
		long first = gate.onEnabledTick(true);
		check(first != 0L && gate.onResourceReload(true, true) == 0L,
				"an in-flight scan owns the generation until completion");
		AtomicInteger published = new AtomicInteger();
		AttExemptionReloadGate.Completion stale = gate.complete(
				first, () -> published.set(1)
		);
		check(!stale.publish() && published.get() == 0
					&& stale.followUpGeneration() != 0L,
				"a stale scan must never overwrite newer exemption state");
		AttExemptionReloadGate.Completion latest = gate.complete(
				stale.followUpGeneration(), () -> published.set(2)
		);
		check(latest.publish() && published.get() == 2 && gate.ready(),
				"exactly one latest-generation follow-up must publish atomically");
	}

	private static void testReloadPublicationAcrossThreads() {
		AttExemptionReloadGate gate = new AttExemptionReloadGate();
		long initial = gate.onEnabledTick(true);
		gate.complete(initial);
		CountDownLatch dirtyPublished = new CountDownLatch(1);
		CountDownLatch allowCompletion = new CountDownLatch(1);
		AtomicBoolean reloadAccepted = new AtomicBoolean();
		Thread reloadThread = new Thread(() -> {
			long generation = gate.onResourceReload(true, true);
			reloadAccepted.set(generation != 0L);
			dirtyPublished.countDown();
			try {
				if (!allowCompletion.await(5L, TimeUnit.SECONDS)) {
					return;
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return;
			}
			AttExemptionReloadGate.Completion stale = gate.complete(generation);
			if (stale.followUpGeneration() != 0L) {
				gate.complete(stale.followUpGeneration());
			}
		}, "ATT reload publication test");
		reloadThread.start();
		try {
			check(dirtyPublished.await(5L, TimeUnit.SECONDS),
					"the reload thread must publish its dirty transition");
			check(reloadAccepted.get() && !gate.ready() && gate.onEnabledTick(true) == 0L,
					"the client thread must fail open while an enabled reload is pending");
			check(gate.onResourceReload(true, true) == 0L,
					"a reload arriving in flight must coalesce instead of starting a duplicate scan");
			allowCompletion.countDown();
			reloadThread.join(5_000L);
			check(!reloadThread.isAlive() && gate.ready(),
					"the completed reload must publish readiness back to the client thread");
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError("ATT reload publication test was interrupted", exception);
		} finally {
			allowCompletion.countDown();
			if (reloadThread.isAlive()) {
				reloadThread.interrupt();
			}
		}
	}

	private static void testStatisticsOffHasNoCounterChurn() {
		AttDiagnosticCounters counters = new AttDiagnosticCounters();
		for (int sprite = 0; sprite < 10_000; sprite++) {
			counters.recordTicked(false);
			counters.recordSkippedInvisible(false);
			counters.recordSkippedCadence(false);
			counters.recordSkippedBudget(false);
			counters.recordProtected(false);
			counters.recordExempt(false);
		}
		check(counters.mutationRevision() == 0L
					&& counters.ticked() == 0L
					&& counters.skippedBudget() == 0L,
				"statistics-off atlas cycles do not mutate diagnostic counters");
		counters.recordTicked(true);
		check(counters.mutationRevision() == 1L && counters.ticked() == 1L,
				"statistics-on cycles retain diagnostic accounting");
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
