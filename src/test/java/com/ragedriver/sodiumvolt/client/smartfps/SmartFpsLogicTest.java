package com.ragedriver.sodiumvolt.client.smartfps;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class SmartFpsLogicTest {
	private static final long SECOND = 1_000_000_000L;

	private SmartFpsLogicTest() {
	}

	public static void main(String[] arguments) {
		testMinimizedIsImmediateAndExclusive();
		testUnfocusedDelayAndImmediateRestore();
		testCapPrecedenceAndVanillaCeiling();
		testPowerModes();
		testPowerSourceAggregation();
		testPowerProbeLazyReuse();
		testPowerProbeConcurrentReuse();
		testPowerProbeInitializationRetry();
		testPowerProbeErrorClassification();
		testDisabledAndUnknownPowerFailOpen();
		testClockRollbackRestartsDelay();
		testApcSuspensionTransitions();
		testNormalization();
		System.out.println("Smart FPS logic tests passed");
	}

	private static void testMinimizedIsImmediateAndExclusive() {
		SmartFpsPolicy policy = new SmartFpsPolicy();
		int capped = evaluate(
				policy, 260, 100L, true, true, false,
				true, 15, true, 30, 10L * SECOND,
				false, 45, true, true, 25, 20,
				SmartFpsPowerSnapshot.UNKNOWN
		);
		check(capped == 15, "minimized cap must apply immediately");
		check(policy.reasons() == SmartFpsPolicy.REASON_MINIMIZED,
				"minimized state must not also activate the unfocused reason");

		int vanillaWins = evaluate(
				policy, 10, 101L, true, true, false,
				true, 15, true, 30, 0L,
				false, 45, true, true, 25, 20,
				SmartFpsPowerSnapshot.UNKNOWN
		);
		check(vanillaWins == 10, "Smart FPS must never raise a stricter vanilla limit");

		int minimizedDisabled = evaluate(
				policy, 260, 102L, true, true, false,
				false, 15, true, 30, 0L,
				false, 45, true, true, 25, 20,
				SmartFpsPowerSnapshot.UNKNOWN
		);
		check(minimizedDisabled == 260 && policy.reasons() == 0,
				"a minimized window must remain exclusive from ordinary unfocused throttling");
	}

	private static void testUnfocusedDelayAndImmediateRestore() {
		SmartFpsPolicy policy = new SmartFpsPolicy();
		long start = 20L * SECOND;
		check(background(policy, start, false, 2L * SECOND) == 260,
				"unfocused delay must begin without an early cap");
		check(background(policy, start + 2L * SECOND - 1L, false, 2L * SECOND) == 260,
				"unfocused cap must wait for the complete delay");
		check(background(policy, start + 2L * SECOND, false, 2L * SECOND) == 30,
				"unfocused cap must activate on the delay boundary");
		check(background(policy, start + 3L * SECOND, true, 2L * SECOND) == 260,
				"focus regain must restore the effective limit immediately");
		check(background(policy, start + 4L * SECOND, false, 2L * SECOND) == 260,
				"a new unfocused period must receive a fresh delay");

		policy.reset();
		check(background(policy, start, false, 0L) == 30,
				"a zero-second unfocused delay must cap immediately");
	}

	private static void testCapPrecedenceAndVanillaCeiling() {
		SmartFpsPolicy policy = new SmartFpsPolicy();
		int effective = evaluate(
				policy, 260, 5L, true, false, false,
				true, 15, true, 30, 0L,
				true, 45, false, true, 25, 20,
				SmartFpsPowerSnapshot.discharging(25)
		);
		check(effective == 20, "the strictest active Smart FPS cap must win");
		check(policy.smartCap() == 20, "Smart cap reporting must expose the strictest Smart cap");
		check((policy.reasons() & SmartFpsPolicy.REASON_UNFOCUSED) != 0,
				"unfocused reason must remain visible when another cap wins");
		check((policy.reasons() & SmartFpsPolicy.REASON_BATTERY) != 0,
				"battery reason must remain visible when another cap wins");
		check((policy.reasons() & SmartFpsPolicy.REASON_LOW_BATTERY) != 0,
				"low-battery reason must remain visible");

		effective = evaluate(
				policy, 12, 6L, true, false, false,
				true, 15, true, 30, 0L,
				true, 45, false, true, 25, 20,
				SmartFpsPowerSnapshot.discharging(25)
		);
		check(effective == 12, "the existing user/vanilla limit must remain authoritative");
	}

	private static void testPowerModes() {
		SmartFpsPolicy policy = new SmartFpsPolicy();
		check(power(policy, SmartFpsPowerSnapshot.discharging(80), true) == 45,
				"battery mode must cap a discharging system");
		check(power(policy, SmartFpsPowerSnapshot.discharging(25), true) == 20,
				"low-battery threshold must be inclusive");
		check(power(policy, SmartFpsPowerSnapshot.charging(10), true) == 260,
				"charging bypass must suppress both battery caps");
		check(power(policy, SmartFpsPowerSnapshot.charging(10), false) == 20,
				"disabled charging bypass must allow battery and low-battery caps");
	}

	private static void testPowerSourceAggregation() {
		SmartFpsPowerAggregation mixed = new SmartFpsPowerAggregation();
		mixed.accept(0.70D, true, true, false);
		mixed.accept(0.40D, false, false, true);
		check(
				mixed.snapshot().equals(SmartFpsPowerSnapshot.discharging(40)),
				"any valid discharging source must take precedence over charging sources"
		);

		SmartFpsPowerAggregation charging = new SmartFpsPowerAggregation();
		charging.accept(0.80D, true, false, false);
		charging.accept(0.55D, false, true, false);
		check(
				charging.snapshot().equals(SmartFpsPowerSnapshot.charging(55)),
				"all valid charging or online sources must aggregate as charging"
		);

		SmartFpsPowerAggregation invalid = new SmartFpsPowerAggregation();
		invalid.accept(Double.NaN, false, false, true);
		invalid.accept(1.5D, true, true, false);
		check(
				invalid.snapshot().equals(SmartFpsPowerSnapshot.UNKNOWN),
				"state flags without a valid percentage must remain unknown"
		);

		SmartFpsPowerAggregation single = new SmartFpsPowerAggregation();
		single.accept(0.67D, false, false, true);
		check(
				single.snapshot().equals(SmartFpsPowerSnapshot.discharging(67)),
				"a normal single source must preserve its state and percentage"
		);
	}

	private static void testPowerProbeLazyReuse() {
		AtomicInteger constructions = new AtomicInteger();
		Object expected = new Object();
		SmartFpsPowerProbe.RetryableLazy<Object> lazy = new SmartFpsPowerProbe.RetryableLazy<>(
				() -> {
					constructions.incrementAndGet();
					return expected;
				}
		);

		check(constructions.get() == 0, "the OSHI state cache must initialize lazily");
		check(lazy.get() == expected, "the first access must return the factory identity");
		check(lazy.get() == expected, "sequential accesses must reuse one identity");
		check(constructions.get() == 1, "sequential accesses must construct state once");
	}

	private static void testPowerProbeConcurrentReuse() {
		int workerCount = 16;
		AtomicInteger constructions = new AtomicInteger();
		Object expected = new Object();
		SmartFpsPowerProbe.RetryableLazy<Object> lazy = new SmartFpsPowerProbe.RetryableLazy<>(
				() -> {
					constructions.incrementAndGet();
					return expected;
				}
		);
		CountDownLatch ready = new CountDownLatch(workerCount);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(workerCount);
		List<Future<Object>> results = new ArrayList<>(workerCount);
		try {
			for (int worker = 0; worker < workerCount; worker++) {
				results.add(executor.submit(() -> {
					ready.countDown();
					if (!start.await(5L, TimeUnit.SECONDS)) {
						throw new AssertionError("concurrent cache test did not start in time");
					}
					return lazy.get();
				}));
			}
			check(ready.await(5L, TimeUnit.SECONDS),
					"concurrent cache workers must become ready");
			start.countDown();
			for (Future<Object> result : results) {
				check(result.get(5L, TimeUnit.SECONDS) == expected,
						"concurrent accesses must reuse one identity");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError("concurrent cache test was interrupted", exception);
		} catch (ExecutionException | TimeoutException exception) {
			throw new AssertionError("concurrent cache access failed", exception);
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
		check(constructions.get() == 1, "concurrent accesses must construct state once");
	}

	private static void testPowerProbeInitializationRetry() {
		AtomicInteger attempts = new AtomicInteger();
		Object expected = new Object();
		SmartFpsPowerProbe.RetryableLazy<Object> lazy = new SmartFpsPowerProbe.RetryableLazy<>(
				() -> {
					if (attempts.incrementAndGet() == 1) {
						throw new IllegalStateException("expected test failure");
					}
					return expected;
				}
		);

		boolean failed = false;
		try {
			lazy.get();
		} catch (IllegalStateException expectedFailure) {
			failed = true;
		}
		check(failed, "a failed initial construction must reach the fail-open caller");
		check(lazy.get() == expected, "a failed initial construction must remain retryable");
		check(lazy.get() == expected, "a successful retry must become the reusable identity");
		check(attempts.get() == 2, "a successful retry must stop further construction attempts");
	}

	@SuppressWarnings("removal")
	private static void testPowerProbeErrorClassification() {
		check(
				SmartFpsPowerProbe.mustRethrow(new OutOfMemoryError("test")),
				"virtual-machine errors must never be swallowed"
		);
		check(
				SmartFpsPowerProbe.mustRethrow(new ThreadDeath()),
				"thread termination must never be swallowed"
		);
		check(
				!SmartFpsPowerProbe.mustRethrow(new AssertionError("test")),
				"ordinary errors must use the battery-query fail-open path"
		);
	}

	private static void testDisabledAndUnknownPowerFailOpen() {
		SmartFpsPolicy policy = new SmartFpsPolicy();
		int disabled = evaluate(
				policy, 144, 10L, false, true, false,
				true, 15, true, 30, 0L,
				true, 45, false, true, 25, 20,
				SmartFpsPowerSnapshot.discharging(5)
		);
		check(disabled == 144 && policy.reasons() == 0 && !policy.shouldSuspendApcSampling(),
				"master off must pass through and clear all policy state");

		check(power(policy, SmartFpsPowerSnapshot.UNKNOWN, true) == 260,
				"unknown power state must fail open");
		check(power(policy, new SmartFpsPowerSnapshot(null, 50), true) == 260,
				"malformed power snapshots must normalize to unknown and fail open");
	}

	private static void testClockRollbackRestartsDelay() {
		SmartFpsPolicy policy = new SmartFpsPolicy();
		check(background(policy, 100L * SECOND, false, 2L * SECOND) == 260,
				"initial unfocused sample must wait");
		check(background(policy, 101L * SECOND, false, 2L * SECOND) == 260,
				"partial delay must wait");
		check(background(policy, 50L * SECOND, false, 2L * SECOND) == 260,
				"clock rollback must not activate a stale delay");
		check(background(policy, 51L * SECOND, false, 2L * SECOND) == 260,
				"rollback must restart the delay from the new clock");
		check(background(policy, 52L * SECOND, false, 2L * SECOND) == 30,
				"restarted delay must activate at its new boundary");
	}

	private static void testApcSuspensionTransitions() {
		SmartFpsPolicy policy = new SmartFpsPolicy();
		check(!policy.shouldSuspendApcSampling(), "new policy must not suspend APC");
		background(policy, 1L, false, 0L);
		check(policy.shouldSuspendApcSampling(),
				"an active Smart FPS cap must suspend APC sampling");
		background(policy, 2L, true, 0L);
		check(!policy.shouldSuspendApcSampling(),
				"restoring focus must release APC sampling immediately");
		power(policy, SmartFpsPowerSnapshot.discharging(80), true);
		check(policy.shouldSuspendApcSampling(),
				"a battery cap must also suspend APC sampling");
		policy.reset();
		check(!policy.shouldSuspendApcSampling(),
				"policy reset must release APC sampling");
	}

	private static void testNormalization() {
		check(SmartFpsConfigNormalization.clamp(-10, 5, 60) == 5,
				"normalization must clamp the lower bound");
		check(SmartFpsConfigNormalization.clamp(100, 5, 60) == 60,
				"normalization must clamp the upper bound");
		check(SmartFpsConfigNormalization.clampStep(43, 15, 120, 5) == 45,
				"normalization must round to the nearest valid step");
		check(SmartFpsConfigNormalization.clampStep(42, 15, 120, 5) == 40,
				"normalization must round down below the midpoint");
		boolean rejected = false;
		try {
			SmartFpsConfigNormalization.clampStep(20, 10, 50, 0);
		} catch (IllegalArgumentException expected) {
			rejected = true;
		}
		check(rejected, "normalization must reject an invalid step");
	}

	private static int background(
			SmartFpsPolicy policy,
			long nowNanos,
			boolean focused,
			long delayNanos
	) {
		return evaluate(
				policy, 260, nowNanos, true, false, focused,
				true, 15, true, 30, delayNanos,
				false, 45, true, true, 25, 20,
				SmartFpsPowerSnapshot.UNKNOWN
		);
	}

	private static int power(
			SmartFpsPolicy policy,
			SmartFpsPowerSnapshot power,
			boolean bypassWhileCharging
	) {
		return evaluate(
				policy, 260, 100L, true, false, true,
				true, 15, true, 30, 2L * SECOND,
				true, 45, bypassWhileCharging, true, 25, 20,
				power
		);
	}

	private static int evaluate(
			SmartFpsPolicy policy,
			int vanillaLimit,
			long nowNanos,
			boolean masterEnabled,
			boolean minimized,
			boolean focused,
			boolean throttleMinimized,
			int minimizedTarget,
			boolean throttleUnfocused,
			int unfocusedTarget,
			long unfocusedDelayNanos,
			boolean batteryMode,
			int batteryTarget,
			boolean bypassWhileCharging,
			boolean lowBatteryProtection,
			int lowBatteryThreshold,
			int lowBatteryTarget,
			SmartFpsPowerSnapshot power
	) {
		return policy.evaluate(
				vanillaLimit,
				nowNanos,
				masterEnabled,
				minimized,
				focused,
				throttleMinimized,
				minimizedTarget,
				throttleUnfocused,
				unfocusedTarget,
				unfocusedDelayNanos,
				batteryMode,
				batteryTarget,
				bypassWhileCharging,
				lowBatteryProtection,
				lowBatteryThreshold,
				lowBatteryTarget,
				power
		);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
