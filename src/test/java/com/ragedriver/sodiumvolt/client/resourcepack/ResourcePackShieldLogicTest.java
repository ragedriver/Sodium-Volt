package com.ragedriver.sodiumvolt.client.resourcepack;

import com.ragedriver.sodiumvolt.client.config.ResourcePackShieldConfigTestSupport;
import net.minecraft.server.packs.repository.PackSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ResourcePackShieldLogicTest {
	private ResourcePackShieldLogicTest() {
	}

	public static void main(String[] arguments) throws Exception {
		testPathPolicy();
		testValidZipAndDirectory();
		testZipLimitsAndMetadata();
		testDirectoryLinksAndCount();
		testPngAndJsonValidation();
		testShaderPolicy();
		testOverlaySemanticsAndBudgets();
		testHardDirectoryContainment();
		testEnforcementAndReloadGate();
		testReloadRejectionIsolation();
		testReloadContextPairing();
		testAggregateScanBudget();
		testLiveStreamAccountingAndClose();
		testArchiveOpenGateConcurrency();
		testSanitizedReport();
		testSourceScope();
		ResourcePackShieldConfigTestSupport.run();
		System.out.println("Resource-Pack Shield logic tests passed");
	}

	private static void testPathPolicy() {
		check(ShieldPathPolicy.isSafe("assets/example/textures/a.png", 512, 32),
				"valid path");
		for (String invalid : new String[]{
				"../a", "a/../b", "./a", "/absolute", "C:/absolute",
				"a\\b", "a\0b", "a//b"
		}) {
			check(!ShieldPathPolicy.isSafe(invalid, 512, 32),
					"unsafe path must fail");
		}
		check(!ShieldPathPolicy.isSafe("a/".repeat(33) + "x", 512, 32),
				"path depth");
		check(!ShieldPathPolicy.isSafe("x".repeat(513), 512, 32),
				"path length");
	}

	private static void testValidZipAndDirectory() throws Exception {
		Path directory = Files.createTempDirectory("sodium-volt-shield-valid-");
		Path archive = directory.resolve("valid.zip");
		Path pack = directory.resolve("pack");
		try {
			LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
			entries.put("pack.mcmeta", json("{\"pack\":{\"description\":\"ok\"}}"));
			entries.put("assets/example/textures/valid.png", png(16, 16));
			writeZip(archive, entries);
			check(ZipPackScanner.scan(archive, policy(true)).accepted(), "valid ZIP");

			Files.createDirectories(pack.resolve("assets/example/textures"));
			Files.write(pack.resolve("pack.mcmeta"), entries.get("pack.mcmeta"));
			Files.write(
					pack.resolve("assets/example/textures/valid.png"),
					entries.get("assets/example/textures/valid.png")
			);
			check(DirectoryPackScanner.scan(pack, policy(true)).accepted(),
					"valid directory");
		} finally {
			Files.deleteIfExists(pack.resolve("assets/example/textures/valid.png"));
			Files.deleteIfExists(pack.resolve("assets/example/textures"));
			Files.deleteIfExists(pack.resolve("assets/example"));
			Files.deleteIfExists(pack.resolve("assets"));
			Files.deleteIfExists(pack.resolve("pack.mcmeta"));
			Files.deleteIfExists(pack);
			Files.deleteIfExists(archive);
			Files.deleteIfExists(directory);
		}
	}

	private static void testZipLimitsAndMetadata() throws Exception {
		Path directory = Files.createTempDirectory("sodium-volt-shield-zip-limits-");
		Path archive = directory.resolve("test.zip");
		try {
			writeZip(archive, Map.of("a.txt", new byte[15], "b.txt", new byte[15]));
			ResourcePackShieldPolicy oneEntry = customPolicy(
					true, 1, 1_000_000, 100, 1_000, 200
			);
			check(ZipPackScanner.scan(archive, oneEntry).reason()
							== ShieldReason.ENTRY_LIMIT,
					"ZIP entry limit");
			ResourcePackShieldPolicy tinyArchive = customPolicy(
					true, 100, 1, 100, 1_000, 200
			);
			check(ZipPackScanner.scan(archive, tinyArchive).reason()
							== ShieldReason.ARCHIVE_SIZE,
					"archive size");
			ResourcePackShieldPolicy tinySingle = customPolicy(
					true, 100, 1_000_000, 10, 1_000, 200
			);
			check(ZipPackScanner.scan(archive, tinySingle).reason()
							== ShieldReason.SINGLE_RESOURCE_SIZE,
					"single expanded size");
			ResourcePackShieldPolicy tinyTotal = customPolicy(
					true, 100, 1_000_000, 20, 25, 200
			);
			check(ZipPackScanner.scan(archive, tinyTotal).reason()
							== ShieldReason.TOTAL_RESOURCE_SIZE,
					"total expanded size");
			check(ZipPackScanner.evaluateMetadata(-1, 1, 0, policy(true))
							== ShieldReason.UNKNOWN_METADATA,
					"unknown size metadata");
			check(ZipPackScanner.evaluateMetadata(1000, 1, 0,
							customPolicy(true, 100, 1_000_000, 2_000, 3_000, 10))
							== ShieldReason.COMPRESSION_RATIO,
					"compression ratio");
			ResourcePackShieldPolicy overflow = customPolicy(
					true, 100, 1_000_000, 100, 100, 200
			);
			check(ZipPackScanner.evaluateMetadata(2, 2, 99, overflow)
							== ShieldReason.TOTAL_RESOURCE_SIZE,
					"overflow-safe total arithmetic");

			writeZip(archive, Map.of("../escape.txt", new byte[]{1}));
			check(ZipPackScanner.scan(archive, policy(true)).reason()
							== ShieldReason.UNSAFE_PATH,
					"ZIP traversal");
			check(ZipPackScanner.scan(
					archive,
					boundedPolicy(false, true, 100, 100, 1_000, 2_000_000_000L)
			).reason() == ShieldReason.UNSAFE_PATH,
					"ZIP structural traversal remains blocked with strict limits off");
			writeZip(archive, Map.of("assets\\escape.txt", new byte[]{1}));
			check(ZipPackScanner.scan(archive, policy(true)).reason()
							== ShieldReason.UNSAFE_PATH,
					"ZIP backslash");
			writeZip(archive, Map.of("/absolute.txt", new byte[]{1}));
			check(ZipPackScanner.scan(archive, policy(true)).reason()
							== ShieldReason.UNSAFE_PATH,
					"ZIP absolute path");
		} finally {
			Files.deleteIfExists(archive);
			Files.deleteIfExists(directory);
		}
	}

	private static void testDirectoryLinksAndCount() throws Exception {
		Path directory = Files.createTempDirectory("sodium-volt-shield-directory-");
		Path root = directory.resolve("pack");
		Path outside = directory.resolve("outside.txt");
		Path symlink = root.resolve("linked.txt");
		Path dangling = root.resolve("dangling.txt");
		try {
			Files.createDirectory(root);
			Files.writeString(root.resolve("a.txt"), "a");
			Files.writeString(root.resolve("b.txt"), "b");
			check(DirectoryPackScanner.scan(
					root, customPolicy(true, 1, 1_000_000, 100, 1_000, 200)
			).reason() == ShieldReason.ENTRY_LIMIT, "directory entry count");
			Files.writeString(outside, "outside");
			if (createSymlink(symlink, outside)) {
				check(DirectoryPackScanner.scan(root, policy(true)).reason()
								== ShieldReason.SYMLINK,
						"directory symlink");
				Files.delete(symlink);
			}
			if (createSymlink(dangling, Path.of("missing-target"))) {
				check(Files.isSymbolicLink(dangling) && !Files.exists(dangling),
						"dangling test link");
				check(DirectoryPackScanner.scan(root, policy(true)).reason()
								== ShieldReason.SYMLINK,
						"dangling directory symlink");
			}
		} finally {
			Files.deleteIfExists(dangling);
			Files.deleteIfExists(symlink);
			Files.deleteIfExists(root.resolve("a.txt"));
			Files.deleteIfExists(root.resolve("b.txt"));
			Files.deleteIfExists(root);
			Files.deleteIfExists(outside);
			Files.deleteIfExists(directory);
		}
	}

	private static void testPngAndJsonValidation() throws Exception {
		ResourcePackShieldPolicy policy = customPolicy(
				true, 100, 1_000_000, 1_000_000, 2_000_000, 200
		);
		check(ShieldContentValidators.validatePngPrefix(png(64, 64), 24, policy)
						== ShieldReason.NONE,
				"valid PNG");
		check(ShieldContentValidators.validatePngPrefix(new byte[24], 24, policy)
						== ShieldReason.PNG_HEADER,
				"PNG signature");
		ResourcePackShieldPolicy smallPixels = new ResourcePackShieldPolicy(
				true, true, true, 100, 1_000_000, 1_000_000, 2_000_000,
				200, 16_384, 1_000_000, 8, 512, 32, 1_000_000_000L
		);
		check(ShieldContentValidators.validatePngPrefix(
				png(4096, 4096), 24, smallPixels
		) == ShieldReason.PNG_DIMENSIONS, "PNG pixel area");
		check(ShieldContentValidators.validatePngPrefix(
				pngUnsigned(0xFFFF_FFFFL, 2), 24, smallPixels
		) == ShieldReason.PNG_DIMENSIONS, "PNG unsigned dimension overflow");

		check(validateJson("{\"text\":\"[{\\\\\\\"}]\"}", 8) == ShieldReason.NONE,
				"JSON quotes and escapes");
		check(validateJson("[[[]]]", 2) == ShieldReason.JSON_NESTING,
				"JSON depth");
		check(validateJson("{\"x\":[1,2}", 8) == ShieldReason.JSON_NESTING,
				"JSON mismatched close");
		check(validateJson("{\"x\":\"unterminated}", 8) == ShieldReason.JSON_NESTING,
				"JSON unterminated quote");
		Path directory = Files.createTempDirectory("sodium-volt-shield-mcmeta-");
		Path archive = directory.resolve("metadata.zip");
		try {
			writeZip(archive, Map.of(
					"assets/example/textures/a.png.mcmeta",
					"[[[[[[[[[0]]]]]]]]]".getBytes(StandardCharsets.UTF_8)
			));
			ResourcePackShieldPolicy shallow = new ResourcePackShieldPolicy(
					true, true, true, 100, 1_000_000, 1_000_000, 2_000_000,
					200, 16_384, 128_000_000L, 3, 512, 32, 1_000_000_000L
			);
			check(ZipPackScanner.scan(archive, shallow).reason()
							== ShieldReason.JSON_NESTING,
					"mcmeta preflight uses JSON depth validation");
		} finally {
			Files.deleteIfExists(archive);
			Files.deleteIfExists(directory);
		}
	}

	private static void testShaderPolicy() throws Exception {
		check(ShieldPathPolicy.isCoreShader(
				"assets/minecraft/shaders/core/terrain.vsh"
		), "core shader path");
		check(!ShieldPathPolicy.isCoreShader(
				"assets/example/shaders/core/terrain.vsh"
		), "non-core namespace");
		Path directory = Files.createTempDirectory("sodium-volt-shield-shader-");
		Path archive = directory.resolve("shader.zip");
		try {
			writeZip(archive, Map.of(
					"assets/minecraft/shaders/core/terrain.vsh",
					"void main(){}".getBytes(StandardCharsets.UTF_8)
			));
			check(ZipPackScanner.scan(archive, policy(true)).reason()
							== ShieldReason.CORE_SHADER_OVERRIDE,
					"shader override scan");
		} finally {
			Files.deleteIfExists(archive);
			Files.deleteIfExists(directory);
		}
	}

	private static void testOverlaySemanticsAndBudgets() throws Exception {
		ResourcePackShieldPolicy defaults = policy(true);
		ShieldOverlayPlan.Validation highValidation = ShieldOverlayPlan.validate(
				List.of("high"), defaults
		);
		check(highValidation.accepted(), "valid overlay declaration");
		ShieldOverlayPlan high = highValidation.plan();
		check(high.effectivePath(
				"high/assets/minecraft/shaders/core/terrain.vsh"
		).equals("assets/minecraft/shaders/core/terrain.vsh"),
				"overlay physical path maps to effective logical path");
		ShieldOverlayPlan nested = ShieldOverlayPlan.validate(
				List.of("base", "base/nested"), defaults
		).plan();
		check(nested.effectivePath("base/nested/assets/example/a.json")
						.equals("assets/example/a.json"),
				"longest declared overlay prefix wins deterministically");
		check(ShieldOverlayPlan.validate(List.of("high/"), defaults).reason()
						== ShieldReason.UNSAFE_PATH,
				"trailing-slash overlays are rejected before matching");

		Path temporary = Files.createTempDirectory("sodium-volt-shield-overlays-");
		Path directoryShader = temporary.resolve("directory-shader");
		Path combined = temporary.resolve("combined");
		Path absent = temporary.resolve("absent");
		Path special = temporary.resolve("special");
		Path linked = temporary.resolve("linked");
		Path outside = temporary.resolve("outside");
		Path archiveShader = temporary.resolve("shader.zip");
		Path archiveCombined = temporary.resolve("combined.zip");
		Path archiveAbsent = temporary.resolve("absent.zip");
		Path archiveLarge = temporary.resolve("large.zip");
		Path archiveMany = temporary.resolve("many.zip");
		try {
			Files.createDirectories(directoryShader.resolve(
					"high/assets/minecraft/shaders/core"
			));
			Files.writeString(
					directoryShader.resolve(
							"high/assets/minecraft/shaders/core/terrain.vsh"
					),
					"shader"
			);
			check(DirectoryPackScanner.scan(
					directoryShader, defaults, high
			).reason() == ShieldReason.CORE_SHADER_OVERRIDE,
					"directory overlay core shader uses effective logical path");

			writeZip(archiveShader, Map.of(
					"high/assets/minecraft/shaders/core/terrain.vsh",
					"shader".getBytes(StandardCharsets.UTF_8)
			));
			check(ZipPackScanner.scan(
					archiveShader, defaults, high
			).reason() == ShieldReason.CORE_SHADER_OVERRIDE,
					"ZIP overlay core shader uses effective logical path");

			Files.createDirectories(combined.resolve("high"));
			Files.write(combined.resolve("root.bin"), new byte[4]);
			Files.write(combined.resolve("high/overlay.bin"), new byte[5]);
			ResourcePackShieldPolicy exactCombined = boundedPolicy(
					true, true, 3, 5, 9, 2_000_000_000L
			);
			ShieldScanResult directoryCombined = DirectoryPackScanner.scan(
					combined, exactCombined, high
			);
			check(directoryCombined.accepted()
							&& directoryCombined.entries() == 3
							&& directoryCombined.declaredBytes() == 9,
					"root and directory overlay share one single-counted budget");
			check(DirectoryPackScanner.scan(
					combined,
					boundedPolicy(true, true, 2, 5, 9, 2_000_000_000L),
					high
			).reason() == ShieldReason.ENTRY_LIMIT,
					"combined root and overlay entry limit");
			check(DirectoryPackScanner.scan(
					combined,
					boundedPolicy(true, true, 3, 5, 8, 2_000_000_000L),
					high
			).reason() == ShieldReason.TOTAL_RESOURCE_SIZE,
					"combined root and overlay declared-byte limit");
			check(DirectoryPackScanner.scan(
					combined,
					boundedPolicy(true, true, 3, 5, 9, 1L),
					high
			).reason() == ShieldReason.SCAN_TIME,
					"root and overlays share one scan deadline");

			writeZip(archiveCombined, Map.of(
					"root.bin", new byte[4],
					"high/overlay.bin", new byte[5]
			));
			ShieldScanResult zipCombined = ZipPackScanner.scan(
					archiveCombined,
					boundedPolicy(true, true, 2, 5, 9, 2_000_000_000L),
					high
			);
			check(zipCombined.accepted()
							&& zipCombined.entries() == 2
							&& zipCombined.declaredBytes() == 9,
					"root and ZIP overlay share one single-counted budget");

			Files.createDirectory(absent);
			check(DirectoryPackScanner.scan(absent, defaults, high).accepted(),
					"absent declared directory overlay preserves vanilla compatibility");
			writeZip(archiveAbsent, Map.of("root.bin", new byte[1]));
			check(ZipPackScanner.scan(archiveAbsent, defaults, high).accepted(),
					"absent declared ZIP overlay preserves vanilla compatibility");

			Files.createDirectory(special);
			Files.write(special.resolve("high"), new byte[1]);
			check(DirectoryPackScanner.scan(
					special, defaults, high
			).reason() == ShieldReason.SPECIAL_FILE,
					"non-directory overlay root");

			Files.createDirectory(linked);
			Files.createDirectory(outside);
			if (createSymlink(linked.resolve("high"), outside)) {
				check(DirectoryPackScanner.scan(
						linked,
						boundedPolicy(false, true, 100, 100, 1_000, 2_000_000_000L),
						high
				).reason() == ShieldReason.SYMLINK,
						"overlay symlink remains a hard defense when name checks are off");
			}

			writeZip(archiveLarge, Map.of("high/large.bin", new byte[6]));
			check(ZipPackScanner.scan(
					archiveLarge,
					boundedPolicy(true, true, 10, 5, 10, 2_000_000_000L),
					high
			).reason() == ShieldReason.SINGLE_RESOURCE_SIZE,
					"large ZIP overlay resource");
			writeZip(archiveMany, Map.of(
					"high/a.bin", new byte[1],
					"high/b.bin", new byte[1],
					"high/c.bin", new byte[1]
			));
			check(ZipPackScanner.scan(
					archiveMany,
					boundedPolicy(true, true, 2, 5, 10, 2_000_000_000L),
					high
			).reason() == ShieldReason.ENTRY_LIMIT,
					"over-entry ZIP overlay");
		} finally {
			deleteTree(temporary);
		}
	}

	private static void testHardDirectoryContainment() throws Exception {
		Path temporary = Files.createTempDirectory("sodium-volt-shield-hard-path-");
		Path root = temporary.resolve("pack");
		Path outside = temporary.resolve("outside.txt");
		Path outsideDirectory = temporary.resolve("outside-directory");
		Path link = root.resolve("assets/example/linked.txt");
		Path namespaceLink = root.resolve("assets/linkedns");
		try {
			Files.createDirectories(link.getParent());
			Files.createDirectory(root.resolve("assets/x"));
			Files.createDirectory(outsideDirectory);
			Files.writeString(outside, "outside");
			check(DirectoryCandidateValidator.validateDirectoryPrefix(
					List.of(root), "assets"
			) == ShieldReason.NONE,
					"namespace discovery validates its real directory prefix");
			check(DirectoryCandidateValidator.validateDirectoryPrefix(
					List.of(root), "assets/x/"
			) == ShieldReason.NONE,
					"namespace x and empty list prefix do not use a fake file probe");
			check(DirectoryCandidateValidator.validate(
					List.of(root), "../outside.txt"
			) == ShieldReason.UNSAFE_PATH,
					"live root containment is independent of optional name checks");
			if (createSymlink(link, outside)) {
				check(DirectoryCandidateValidator.validate(
						List.of(root), "assets/example/linked.txt"
				) == ShieldReason.SYMLINK,
						"live direct-resource symlink is always detected");
				check(DirectoryPackScanner.scan(
						root,
						boundedPolicy(false, true, 100, 100, 1_000, 2_000_000_000L)
				).reason() == ShieldReason.SYMLINK,
						"preflight symlink defense remains when lexical checks are off");
			}
			if (createSymlink(namespaceLink, outsideDirectory)) {
				check(DirectoryCandidateValidator.validateDirectoryPrefix(
						List.of(root), "assets/linkedns"
				) == ShieldReason.SYMLINK,
						"namespace directory symlink is detected after discovery");
			}
		} finally {
			deleteTree(temporary);
		}
	}

	private static void testEnforcementAndReloadGate() {
		check(ShieldEnforcement.shouldReject(policy(true), ShieldReason.UNSAFE_PATH),
				"reject action");
		check(!ShieldEnforcement.shouldReject(policy(false), ShieldReason.UNSAFE_PATH),
				"monitor-only action");
		check(!ShieldEnforcement.shouldReject(policy(true), ShieldReason.MONITOR_FAILURE),
				"internal monitor failure never rejects");

		CompletableFuture<String> prerequisite = new CompletableFuture<>();
		CompletableFuture<String> guarded = ShieldReloadGate.guardInitialTask(
				prerequisite, true
		);
		check(!guarded.isDone(), "reload gate must not throw synchronously");
		prerequisite.complete("ready");
		try {
			guarded.join();
			throw new AssertionError("rejected reload gate must fail");
		} catch (CompletionException exception) {
			check(exception.getCause() instanceof ResourcePackShieldRejectedException,
					"fixed controlled reload failure");
		}
		CompletableFuture<String> vanilla = CompletableFuture.completedFuture("ok");
		check(ShieldReloadGate.guardInitialTask(vanilla, false) == vanilla,
				"monitor mode preserves vanilla initial task");
	}

	private static void testReloadRejectionIsolation() {
		long generation = 41L;
		ShieldReloadRejection cleanA = new ShieldReloadRejection(generation, true);
		ShieldReloadRejection rejectedB = new ShieldReloadRejection(generation, true);
		rejectedB.reject(generation);
		CompletableFuture<String> initialA = CompletableFuture.completedFuture("A");
		CompletableFuture<String> initialB = CompletableFuture.completedFuture("B");
		check(cleanA.guardInitialTask(initialA, generation) == initialA,
				"overlapping clean reload A remains unchanged when B rejects");
		CompletableFuture<String> guardedB =
				rejectedB.guardInitialTask(initialB, generation);
		check(guardedB != initialB, "only rejected overlapping reload B is gated");
		try {
			guardedB.join();
			throw new AssertionError("rejected overlapping reload B must fail");
		} catch (CompletionException exception) {
			check(exception.getCause() instanceof ResourcePackShieldRejectedException,
					"per-token B rejection uses controlled future failure");
		}
		check(cleanA.guardInitialTask(initialA, generation) == initialA,
				"B rejection does not become stale global state for A");

		ShieldReloadRejection staleRejected =
				new ShieldReloadRejection(generation - 1L, true);
		staleRejected.reject(generation - 1L);
		check(staleRejected.guardInitialTask(initialA, generation) == initialA,
				"stale-generation rejection is ignored");
		ShieldReloadRejection nextReload =
				new ShieldReloadRejection(generation, true);
		check(nextReload.guardInitialTask(initialA, generation) == initialA,
				"next reload starts with fresh rejection state");
	}

	private static void testReloadContextPairing() {
		AtomicInteger created = new AtomicInteger();
		ShieldReloadContextStack<String> contexts =
				new ShieldReloadContextStack<>(2);
		ShieldReloadContextStack.Frame<String> direct = contexts.begin(
				null,
				false,
				false,
				() -> "private-" + created.incrementAndGet(),
				"disabled"
		);
		check(direct != null
						&& direct.selection() == ShieldReloadContextStack.Selection.OWNED
						&& direct.token().equals("private-1"),
				"direct private reload owns a token");
		ShieldReloadContextStack.Frame<String> borrowed = contexts.begin(
				direct.token(),
				true,
				false,
				() -> "unexpected-" + created.incrementAndGet(),
				"disabled"
		);
		check(borrowed != null
						&& borrowed.selection() == ShieldReloadContextStack.Selection.BORROWED
						&& borrowed.token() == direct.token()
						&& created.get() == 1,
				"public to private nesting borrows without creating a token");
		check(contexts.finish() == borrowed && contexts.finish() == direct
						&& contexts.isEmpty(),
				"normal nested reload contexts pair in LIFO order");

		ShieldReloadContextStack.Frame<String> disabled = contexts.begin(
				null,
				false,
				true,
				() -> "unexpected-" + created.incrementAndGet(),
				"disabled"
		);
		check(disabled != null
						&& disabled.selection() == ShieldReloadContextStack.Selection.DISABLED
						&& contexts.currentOr("fallback").equals("disabled")
						&& contexts.finish() == disabled,
				"external public overflow selects a bounded disabled context");

		ShieldReloadContextStack.Frame<String> outer = contexts.begin(
				null, false, false, () -> "outer", "disabled"
		);
		contexts.begin(outer.token(), true, false, () -> "unexpected", "disabled");
		check(contexts.begin(
				outer.token(), true, false, () -> "unexpected", "disabled"
		) == null
						&& contexts.overflowDepth() == 1
						&& contexts.currentOr("disabled").equals("disabled"),
				"internal nesting overflow does not grow the context stack");
		check(contexts.finish() == null && contexts.overflowDepth() == 0,
				"overflow return consumes only its counter");
		contexts.finish();
		contexts.finish();
		check(contexts.isEmpty(), "overflow restores the enclosing context");

		try {
			contexts.begin(
					null, false, false, () -> "throwing-private", "disabled"
			);
			throw new SyntheticReloadFailure();
		} catch (SyntheticReloadFailure expected) {
			// The production WrapMethod performs this operation in its finally block.
		} finally {
			contexts.finish();
		}
		check(contexts.isEmpty()
						&& contexts.currentOr("disabled").equals("disabled"),
				"synchronous private failure leaves no stale context");
		ShieldReloadContextStack.Frame<String> afterFailure = contexts.begin(
				null, false, false, () -> "after-failure", "disabled"
		);
		check(afterFailure != null && afterFailure.ownsToken()
						&& afterFailure.token().equals("after-failure"),
				"reload after synchronous failure receives fresh ownership");
		contexts.finish();
	}

	private static void testLiveStreamAccountingAndClose() throws Exception {
		TrackingInputStream raw = new TrackingInputStream(new byte[]{1, 2, 3, 4, 5, 6});
		ShieldReadBudget budget = new ShieldReadBudget(32);
		AtomicInteger violations = new AtomicInteger();
		ShieldedInputStream stream = new ShieldedInputStream(
				raw, 16, budget, ShieldedInputStream.ContentKind.OTHER,
				policy(true), reason -> {
					violations.incrementAndGet();
					return true;
				}
		);
		check(stream.read() == 1, "read()");
		byte[] pair = new byte[2];
		check(stream.read(pair, 0, pair.length) == 2 && pair[0] == 2 && pair[1] == 3,
				"read(byte[],off,len)");
		check(stream.skip(2) == 2 && stream.read() == 6 && stream.read() == -1,
				"skip must read, validate, and reach EOF");
		check(stream.resourceBytes() == 6 && budget.consumedBytes() == 6,
				"per-resource and aggregate accounting");
		stream.close();
		stream.close();
		check(raw.closeCount == 1 && violations.get() == 0, "idempotent close");

		AtomicInteger monitored = new AtomicInteger();
		ShieldedInputStream monitor = new ShieldedInputStream(
				new ByteArrayInputStream(new byte[20]),
				4,
				new ShieldReadBudget(4),
				ShieldedInputStream.ContentKind.OTHER,
				policy(false),
				reason -> {
					monitored.incrementAndGet();
					return false;
				}
		);
		check(monitor.readAllBytes().length == 20 && monitored.get() == 1,
				"monitor-only limit records once and preserves full reads");
		monitor.close();

		ShieldedInputStream reject = new ShieldedInputStream(
				new ByteArrayInputStream(new byte[8]),
				4,
				new ShieldReadBudget(4),
				ShieldedInputStream.ContentKind.OTHER,
				policy(true),
				reason -> true
		);
		try {
			reject.readAllBytes();
			throw new AssertionError("reject stream must fail at its ceiling");
		} catch (IOException expected) {
			check(reject.resourceBytes() == 4, "reject accounting saturates at ceiling");
		}
		reject.close();

		AtomicInteger jsonFailures = new AtomicInteger();
		ShieldedInputStream badJson = new ShieldedInputStream(
				new ByteArrayInputStream("]]]]".getBytes(StandardCharsets.UTF_8)),
				100,
				new ShieldReadBudget(100),
				ShieldedInputStream.ContentKind.JSON,
				policy(false),
				reason -> {
					jsonFailures.incrementAndGet();
					return false;
				}
		);
		badJson.readAllBytes();
		check(jsonFailures.get() == 1, "live JSON failure deduplicated");
		badJson.close();
	}

	private static void testAggregateScanBudget() {
		ResourcePackShieldPolicy policy = policy(true);
		ShieldScanBudget.Allowance remaining = ShieldScanBudget.allowance(
				policy, 10_000L, 9_000L, true
		);
		check(!remaining.expired() && remaining.policy().maximumScanNanos() == 1_000L,
				"per-pack scan cap uses only aggregate time remaining");
		for (int attempt = 0; attempt < 10_000; attempt++) {
			ShieldScanBudget.Allowance expired = ShieldScanBudget.allowance(
					policy, 10_000L, 10_001L + attempt, true
			);
			check(expired.expired() && expired.policy().maximumScanNanos() == 1L,
					"post-expiry attempts are immediate and do not receive a minimum slice");
		}
		check(!ShieldScanBudget.allowance(policy, 0L, Long.MAX_VALUE, false).expired(),
				"standalone open retains its configured bound");
	}

	private static void testSanitizedReport() throws Exception {
		ResourcePackShieldReport report = new ResourcePackShieldReport(
				ShieldReason.UNSAFE_PATH,
				ShieldSourceKind.SERVER,
				true,
				Integer.MAX_VALUE,
				5,
				10,
				Long.MAX_VALUE,
				42,
				1
		);
		String json = report.toJson().toString();
		check(report.toJson().size() == 11 && json.length() < 1_024,
				"bounded report schema");
		for (String forbidden : new String[]{
				"resource_name", "pack_name", "path_value", "account", "world_name",
				"device", "stack", "hash", "address"
		}) {
			check(!json.contains(forbidden), "report excludes private string fields");
		}
		Path directory = Files.createTempDirectory("sodium-volt-shield-report-");
		Path path = directory.resolve("report.json");
		Path dangling = directory.resolve("dangling.json");
		try {
			check(ResourcePackShieldReportStore.write(path, report)
							&& ResourcePackShieldReportStore.read(path) != null,
					"strict report round-trip");
			String valid = Files.readString(path);
			Files.writeString(path, valid.replaceFirst(
					"\"version\": 1", "\"version\": 1, \"extra\": 1"
			));
			check(ResourcePackShieldReportStore.read(path) == null,
					"extra report field");
			Files.writeString(path, valid.replaceFirst(
					"\"monitor_failures\": 1", "\"monitor_failures\": -1"
			));
			check(ResourcePackShieldReportStore.read(path) == null,
					"out-of-range report");
			if (createSymlink(dangling, Path.of("missing-report.json"))) {
				check(!ResourcePackShieldReportStore.write(dangling, report)
								&& Files.isSymbolicLink(dangling),
						"report dangling link preserved");
			}
		} finally {
			Files.deleteIfExists(dangling);
			Files.deleteIfExists(path);
			Files.deleteIfExists(directory);
		}
	}

	private static void testArchiveOpenGateConcurrency() throws Exception {
		ShieldArchiveOpenGate gate = new ShieldArchiveOpenGate();
		AtomicInteger validations = new AtomicInteger();
		CountDownLatch start = new CountDownLatch(1);
		Thread[] threads = new Thread[16];
		for (int index = 0; index < threads.length; index++) {
			threads[index] = new Thread(() -> {
				try {
					start.await();
					try (ShieldArchiveOpenGate.Lease lease = gate.acquire()) {
						if (lease.validationRequired()) {
							validations.incrementAndGet();
							lease.markOpened();
						}
					}
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new AssertionError(exception);
				}
			});
			threads[index].start();
		}
		start.countDown();
		for (Thread thread : threads) {
			thread.join();
		}
		check(gate.isOpened() && validations.get() == 1,
				"archive source validation/open transition occurs once under concurrency");
		for (int index = 0; index < 1_000; index++) {
			try (ShieldArchiveOpenGate.Lease lease = gate.acquire()) {
				check(!lease.validationRequired(), "opened archive uses constant-time gate");
			}
		}
	}

	private static void testSourceScope() {
		check(ShieldSourceScope.classify(PackSource.DEFAULT) == ShieldSourceKind.LOCAL,
				"default source local");
		check(ShieldSourceScope.classify(PackSource.WORLD) == ShieldSourceKind.LOCAL,
				"world source local");
		check(ShieldSourceScope.classify(PackSource.SERVER) == ShieldSourceKind.SERVER,
				"server source");
		check(ShieldSourceScope.classify(PackSource.BUILT_IN) == ShieldSourceKind.IGNORED,
				"built-in ignored");
		check(ShieldSourceScope.classify(PackSource.FEATURE) == ShieldSourceKind.IGNORED,
				"feature ignored");
		PackSource custom = PackSource.create(UnaryOperator.identity(), true);
		check(ShieldSourceScope.classify(custom) == ShieldSourceKind.IGNORED,
				"custom mod source ignored");
	}

	private static ResourcePackShieldPolicy policy(boolean reject) {
		ResourcePackShieldPolicy defaults = ResourcePackShieldPolicy.defaults();
		return new ResourcePackShieldPolicy(
				defaults.detectUnsafePaths(),
				defaults.blockCoreShaderOverrides(),
				reject,
				defaults.maximumEntries(),
				defaults.maximumArchiveBytes(),
				defaults.maximumSingleResourceBytes(),
				defaults.maximumTotalResourceBytes(),
				defaults.maximumCompressionRatio(),
				defaults.maximumPngDimension(),
				defaults.maximumPngPixels(),
				defaults.maximumJsonDepth(),
				defaults.maximumPathLength(),
				defaults.maximumPathDepth(),
				defaults.maximumScanNanos()
		);
	}

	private static ResourcePackShieldPolicy customPolicy(
			boolean reject,
			int entries,
			long archive,
			long single,
			long total,
			int ratio
	) {
		return new ResourcePackShieldPolicy(
				true, true, reject, entries, archive, single, total, ratio,
				16_384, 128_000_000L, 128, 512, 32, 2_000_000_000L
		);
	}

	private static ResourcePackShieldPolicy boundedPolicy(
			boolean detectUnsafePaths,
			boolean reject,
			int entries,
			long single,
			long total,
			long scanNanos
	) {
		ResourcePackShieldPolicy defaults = ResourcePackShieldPolicy.defaults();
		return new ResourcePackShieldPolicy(
				detectUnsafePaths,
				true,
				reject,
				entries,
				1_000_000L,
				single,
				total,
				defaults.maximumCompressionRatio(),
				defaults.maximumPngDimension(),
				defaults.maximumPngPixels(),
				defaults.maximumJsonDepth(),
				defaults.maximumPathLength(),
				defaults.maximumPathDepth(),
				scanNanos
		);
	}

	private static ShieldReason validateJson(String json, int depth) throws IOException {
		return ShieldContentValidators.validateJson(
				new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
				1_024,
				depth,
				Long.MAX_VALUE
		);
	}

	private static byte[] json(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] png(long width, long height) {
		return pngUnsigned(width, height);
	}

	private static byte[] pngUnsigned(long width, long height) {
		byte[] prefix = new byte[24];
		byte[] signature = new byte[]{
				(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
		};
		System.arraycopy(signature, 0, prefix, 0, signature.length);
		ByteBuffer buffer = ByteBuffer.wrap(prefix).order(ByteOrder.BIG_ENDIAN);
		buffer.putInt(8, 13);
		prefix[12] = 'I';
		prefix[13] = 'H';
		prefix[14] = 'D';
		prefix[15] = 'R';
		buffer.putInt(16, (int) width);
		buffer.putInt(20, (int) height);
		return prefix;
	}

	private static void writeZip(Path path, Map<String, byte[]> entries) throws IOException {
		try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
			for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
				output.putNextEntry(new ZipEntry(entry.getKey()));
				output.write(entry.getValue());
				output.closeEntry();
			}
		}
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root) && !Files.isSymbolicLink(root)) {
			return;
		}
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

	private static boolean createSymlink(Path link, Path target) throws Exception {
		try {
			Files.createSymbolicLink(link, target);
			return true;
		} catch (UnsupportedOperationException exception) {
			System.out.println("Skipping symlink assertions: unsupported");
			return false;
		} catch (FileSystemException exception) {
			String reason = String.valueOf(exception.getReason()).toLowerCase(Locale.ROOT);
			if (reason.contains("not supported") || reason.contains("privilege")) {
				System.out.println("Skipping symlink assertions: unavailable");
				return false;
			}
			throw exception;
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("Resource-Pack Shield: " + message);
		}
	}

	private static final class TrackingInputStream extends ByteArrayInputStream {
		private int closeCount;

		private TrackingInputStream(byte[] bytes) {
			super(bytes);
		}

		@Override
		public void close() {
			this.closeCount++;
		}
	}

	private static final class SyntheticReloadFailure extends RuntimeException {
	}
}
