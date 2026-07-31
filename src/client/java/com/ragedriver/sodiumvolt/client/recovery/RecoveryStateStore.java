package com.ragedriver.sodiumvolt.client.recovery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.ragedriver.sodiumvolt.SodiumVolt;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

public final class RecoveryStateStore {
	static final int STATE_VERSION = 1;
	static final int MAXIMUM_SIZE_BYTES = 1024 * 1024;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Set<String> ROOT_KEYS = Set.of(
			"version",
			"session_active",
			"crash_streak",
			"recovery_attempts",
			"recovery_active",
			"force_request_staged",
			"has_backup",
			"profile_applied"
	);
	private static final Set<String> BACKUP_ROOT_KEYS = Set.of(
			"version",
			"session_active",
			"crash_streak",
			"recovery_attempts",
			"recovery_active",
			"force_request_staged",
			"has_backup",
			"profile_applied",
			"original",
			"last_applied"
	);
	private static final Set<String> SNAPSHOT_KEYS = Set.of(
			"render_distance",
			"entity_distance_percent",
			"particle_mode",
			"cloud_mode",
			"ambient_occlusion",
			"entity_shadows",
			"biome_blend_radius",
			"graphics_preset"
	);

	private RecoveryStateStore() {
	}

	public static RecoveryPersistentState load() {
		return load(statePath());
	}

	public static boolean save(RecoveryPersistentState state) {
		return save(statePath(), state);
	}

	public static boolean resetToFactoryDefaults() {
		return save(RecoveryPersistentState.EMPTY);
	}

	static RecoveryPersistentState load(Path path) {
		try {
			if (!Files.exists(path)) {
				return RecoveryPersistentState.EMPTY;
			}
			if (Files.isSymbolicLink(path)
					|| !Files.isRegularFile(path)
					|| Files.size(path) > MAXIMUM_SIZE_BYTES) {
				throw new IOException("Invalid recovery state target");
			}
			byte[] document;
			try (InputStream input = Files.newInputStream(path)) {
				document = input.readNBytes(MAXIMUM_SIZE_BYTES + 1);
			}
			if (document.length > MAXIMUM_SIZE_BYTES) {
				throw new IOException("Oversized recovery state");
			}
			rejectDuplicateKeys(document);
			try (Reader reader = new InputStreamReader(
					new ByteArrayInputStream(document),
					StandardCharsets.UTF_8
			)) {
				JsonElement element = JsonParser.parseReader(reader);
				if (!element.isJsonObject()) {
					throw new IllegalArgumentException("State root must be an object");
				}
				JsonObject root = element.getAsJsonObject();
				boolean hasBackup = requiredBoolean(root, "has_backup");
				requireExactKeys(root, hasBackup ? BACKUP_ROOT_KEYS : ROOT_KEYS);
				if (requiredInteger(root, "version") != STATE_VERSION) {
					throw new IllegalArgumentException("Unsupported recovery state version");
				}
				RecoveryOptionSnapshot original = hasBackup
						? requiredSnapshot(root, "original")
						: null;
				RecoveryOptionSnapshot lastApplied = hasBackup
						? requiredSnapshot(root, "last_applied")
						: null;
				int crashStreak = requiredInteger(root, "crash_streak");
				int recoveryAttempts = requiredInteger(root, "recovery_attempts");
				if (crashStreak < 0 || crashStreak > RecoveryPersistentState.MAXIMUM_COUNTER
						|| recoveryAttempts < 0
						|| recoveryAttempts > RecoveryPersistentState.MAXIMUM_COUNTER) {
					throw new IllegalArgumentException("Recovery counters are out of range");
				}
				boolean profileApplied = requiredBoolean(root, "profile_applied");
				if (profileApplied && !hasBackup) {
					throw new IllegalArgumentException("Applied profile is missing ownership");
				}
				RecoveryPersistentState state = new RecoveryPersistentState(
						requiredBoolean(root, "session_active"),
						crashStreak,
						recoveryAttempts,
						requiredBoolean(root, "recovery_active"),
						requiredBoolean(root, "force_request_staged"),
						hasBackup,
						profileApplied,
						original,
						lastApplied
				);
				if (hasBackup && !state.hasBackup()) {
					throw new IllegalArgumentException("Recovery state is out of range");
				}
				return state;
			}
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			SodiumVolt.LOGGER.warn("Ignoring invalid local Volt Recovery state");
			return RecoveryPersistentState.EMPTY;
		}
	}

	private static void rejectDuplicateKeys(byte[] document) throws IOException {
		try (JsonReader reader = new JsonReader(new InputStreamReader(
				new ByteArrayInputStream(document),
				StandardCharsets.UTF_8
		))) {
			reader.setStrictness(Strictness.STRICT);
			scanUniqueValue(reader);
			if (reader.peek() != JsonToken.END_DOCUMENT) {
				throw new IllegalArgumentException("Trailing recovery state content");
			}
		}
	}

	private static void scanUniqueValue(JsonReader reader) throws IOException {
		switch (reader.peek()) {
			case BEGIN_OBJECT -> {
				reader.beginObject();
				Set<String> keys = new HashSet<>();
				while (reader.hasNext()) {
					if (!keys.add(reader.nextName())) {
						throw new IllegalArgumentException("Duplicate recovery state field");
					}
					scanUniqueValue(reader);
				}
				reader.endObject();
			}
			case BEGIN_ARRAY -> {
				reader.beginArray();
				while (reader.hasNext()) {
					scanUniqueValue(reader);
				}
				reader.endArray();
			}
			case STRING, NUMBER -> reader.nextString();
			case BOOLEAN -> reader.nextBoolean();
			case NULL -> reader.nextNull();
			default -> throw new IllegalArgumentException("Invalid recovery state value");
		}
	}

	static boolean save(Path path, RecoveryPersistentState state) {
		Path temporaryPath = null;
		try {
			Path directory = path.getParent();
			if (directory == null) {
				return false;
			}
			Files.createDirectories(directory);
			if (Files.exists(path)
					&& (Files.isSymbolicLink(path) || !Files.isRegularFile(path))) {
				throw new IOException("Unsafe recovery state target");
			}
			temporaryPath = Files.createTempFile(directory, "sodium-volt-recovery-state-", ".tmp");
			Files.writeString(
					temporaryPath,
					GSON.toJson(toJson(state)),
					StandardCharsets.UTF_8,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE
			);
			moveIntoPlace(temporaryPath, path);
			temporaryPath = null;
			return true;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			SodiumVolt.LOGGER.error("Could not persist local Volt Recovery state");
			return false;
		} finally {
			if (temporaryPath != null) {
				try {
					Files.deleteIfExists(temporaryPath);
				} catch (IOException | SecurityException exception) {
					SodiumVolt.LOGGER.warn("Could not remove a temporary Volt Recovery state file");
				}
			}
		}
	}

	static JsonObject toJson(RecoveryPersistentState state) {
		JsonObject root = new JsonObject();
		root.addProperty("version", STATE_VERSION);
		root.addProperty("session_active", state.sessionActive());
		root.addProperty("crash_streak", state.crashStreak());
		root.addProperty("recovery_attempts", state.recoveryAttempts());
		root.addProperty("recovery_active", state.recoveryActive());
		root.addProperty("force_request_staged", state.forceRequestStaged());
		root.addProperty("has_backup", state.hasBackup());
		root.addProperty("profile_applied", state.profileApplied());
		if (state.hasBackup()) {
			root.add("original", snapshotJson(state.original()));
			root.add("last_applied", snapshotJson(state.lastApplied()));
		}
		return root;
	}

	private static JsonObject snapshotJson(RecoveryOptionSnapshot snapshot) {
		JsonObject object = new JsonObject();
		object.addProperty("render_distance", snapshot.renderDistance());
		object.addProperty("entity_distance_percent", snapshot.entityDistancePercent());
		object.addProperty("particle_mode", snapshot.particleMode());
		object.addProperty("cloud_mode", snapshot.cloudMode());
		object.addProperty("ambient_occlusion", snapshot.ambientOcclusion());
		object.addProperty("entity_shadows", snapshot.entityShadows());
		object.addProperty("biome_blend_radius", snapshot.biomeBlendRadius());
		object.addProperty("graphics_preset", snapshot.graphicsPreset());
		return object;
	}

	private static RecoveryOptionSnapshot requiredSnapshot(JsonObject root, String key) {
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonObject()) {
			throw new IllegalArgumentException("Missing recovery option snapshot");
		}
		JsonObject object = element.getAsJsonObject();
		requireExactKeys(object, SNAPSHOT_KEYS);
		RecoveryOptionSnapshot snapshot = new RecoveryOptionSnapshot(
				requiredInteger(object, "render_distance"),
				requiredInteger(object, "entity_distance_percent"),
				requiredInteger(object, "particle_mode"),
				requiredInteger(object, "cloud_mode"),
				requiredBoolean(object, "ambient_occlusion"),
				requiredBoolean(object, "entity_shadows"),
				requiredInteger(object, "biome_blend_radius"),
				requiredInteger(object, "graphics_preset")
		);
		if (!snapshot.isValid()) {
			throw new IllegalArgumentException("Recovery option snapshot is out of range");
		}
		return snapshot;
	}

	private static void requireExactKeys(JsonObject object, Set<String> expected) {
		if (!object.keySet().equals(expected)) {
			throw new IllegalArgumentException("Unexpected recovery state fields");
		}
	}

	private static boolean requiredBoolean(JsonObject object, String key) {
		JsonPrimitive value = primitive(object, key);
		if (value == null || !value.isBoolean()) {
			throw new IllegalArgumentException("Missing boolean recovery field");
		}
		return value.getAsBoolean();
	}

	private static int requiredInteger(JsonObject object, String key) {
		JsonPrimitive value = primitive(object, key);
		if (value == null || !value.isNumber()) {
			throw new IllegalArgumentException("Missing integer recovery field");
		}
		try {
			return value.getAsBigDecimal().intValueExact();
		} catch (ArithmeticException | NumberFormatException exception) {
			throw new IllegalArgumentException("Invalid integer recovery field", exception);
		}
	}

	private static JsonPrimitive primitive(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsJsonPrimitive() : null;
	}

	private static void moveIntoPlace(Path source, Path destination) throws IOException {
		try {
			Files.move(
					source,
					destination,
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING
			);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static Path statePath() {
		return FabricLoader.getInstance().getConfigDir()
				.resolve("sodium-volt-recovery-state.json");
	}
}
