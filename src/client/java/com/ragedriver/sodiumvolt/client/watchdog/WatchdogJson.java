package com.ragedriver.sodiumvolt.client.watchdog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

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

public final class WatchdogJson {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private WatchdogJson() {
	}

	public static JsonObject readObject(Path path, int maximumBytes) throws IOException {
		if (Files.isSymbolicLink(path)) {
			throw new IOException("Invalid watchdog JSON target");
		}
		if (!Files.exists(path)) {
			return null;
		}
		if (!Files.isRegularFile(path)
				|| Files.size(path) > maximumBytes) {
			throw new IOException("Invalid watchdog JSON target");
		}
		byte[] document;
		try (InputStream input = Files.newInputStream(path)) {
			document = input.readNBytes(maximumBytes + 1);
		}
		if (document.length > maximumBytes) {
			throw new IOException("Oversized watchdog JSON document");
		}
		rejectDuplicateKeys(document);
		try (Reader reader = new InputStreamReader(
				new ByteArrayInputStream(document),
				StandardCharsets.UTF_8
		)) {
			JsonElement element = JsonParser.parseReader(reader);
			if (!element.isJsonObject()) {
				throw new IllegalArgumentException("Watchdog JSON root must be an object");
			}
			return element.getAsJsonObject();
		}
	}

	public static void writeObject(
			Path path,
			JsonObject object,
			int maximumBytes,
			String temporaryPrefix
	) throws IOException {
		byte[] document = GSON.toJson(object).getBytes(StandardCharsets.UTF_8);
		if (document.length > maximumBytes) {
			throw new IOException("Watchdog JSON output exceeds its fixed bound");
		}
		Path directory = path.getParent();
		if (directory == null) {
			throw new IOException("Watchdog JSON path has no parent");
		}
		Files.createDirectories(directory);
		if (Files.isSymbolicLink(path)) {
			throw new IOException("Unsafe watchdog JSON target");
		}
		if (Files.exists(path) && !Files.isRegularFile(path)) {
			throw new IOException("Unsafe watchdog JSON target");
		}
		Path temporary = Files.createTempFile(directory, temporaryPrefix, ".tmp");
		try {
			Files.write(
					temporary,
					document,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE
			);
			try {
				Files.move(
						temporary,
						path,
						StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING
				);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	public static boolean deleteRegularFile(Path path) throws IOException {
		if (Files.isSymbolicLink(path)) {
			throw new IOException("Unsafe watchdog JSON delete target");
		}
		if (!Files.exists(path)) {
			return true;
		}
		if (!Files.isRegularFile(path)) {
			throw new IOException("Unsafe watchdog JSON delete target");
		}
		Files.delete(path);
		return true;
	}

	public static void requireExactKeys(JsonObject object, Set<String> expected) {
		if (!object.keySet().equals(expected)) {
			throw new IllegalArgumentException("Unexpected watchdog JSON fields");
		}
	}

	public static boolean requiredBoolean(JsonObject object, String key) {
		JsonPrimitive value = primitive(object, key);
		if (value == null || !value.isBoolean()) {
			throw new IllegalArgumentException("Missing watchdog boolean field");
		}
		return value.getAsBoolean();
	}

	public static int requiredInteger(JsonObject object, String key) {
		JsonPrimitive value = primitive(object, key);
		if (value == null || !value.isNumber()) {
			throw new IllegalArgumentException("Missing watchdog integer field");
		}
		try {
			return value.getAsBigDecimal().intValueExact();
		} catch (ArithmeticException | NumberFormatException exception) {
			throw new IllegalArgumentException("Invalid watchdog integer field", exception);
		}
	}

	public static String requiredString(JsonObject object, String key, int maximumLength) {
		JsonPrimitive value = primitive(object, key);
		if (value == null || !value.isString()) {
			throw new IllegalArgumentException("Missing watchdog string field");
		}
		String result = value.getAsString();
		if (result.length() > maximumLength) {
			throw new IllegalArgumentException("Oversized watchdog string field");
		}
		return result;
	}

	private static JsonPrimitive primitive(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive()
				? element.getAsJsonPrimitive()
				: null;
	}

	private static void rejectDuplicateKeys(byte[] document) throws IOException {
		try (JsonReader reader = new JsonReader(new InputStreamReader(
				new ByteArrayInputStream(document),
				StandardCharsets.UTF_8
		))) {
			reader.setStrictness(Strictness.STRICT);
			scanUniqueValue(reader);
			if (reader.peek() != JsonToken.END_DOCUMENT) {
				throw new IllegalArgumentException("Trailing watchdog JSON content");
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
						throw new IllegalArgumentException("Duplicate watchdog JSON field");
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
			default -> throw new IllegalArgumentException("Invalid watchdog JSON value");
		}
	}
}
