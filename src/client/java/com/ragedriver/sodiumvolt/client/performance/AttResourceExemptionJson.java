package com.ragedriver.sodiumvolt.client.performance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class AttResourceExemptionJson {
	private static final int MAXIMUM_RAW_ARRAY_VISITS =
			AttExemptionParsing.MAX_RESOURCE_ENTRIES * 4;

	private AttResourceExemptionJson() {
	}

	public static ParseResult parse(String json) {
		if (json == null || json.length() > AttExemptionParsing.MAX_FILE_BYTES) {
			return new ParseResult(new String[0], true);
		}
		JsonElement root = JsonParser.parseString(json);
		if (!root.isJsonObject()) {
			throw new IllegalArgumentException("exemption root must be an object");
		}
		JsonObject object = root.getAsJsonObject();
		JsonElement textures = object.get("textures");
		if (textures == null || !textures.isJsonArray()) {
			throw new IllegalArgumentException("textures must be an array");
		}
		String[] raw = new String[Math.min(
				textures.getAsJsonArray().size(),
				MAXIMUM_RAW_ARRAY_VISITS
		)];
		int count = 0;
		boolean truncated = textures.getAsJsonArray().size() > MAXIMUM_RAW_ARRAY_VISITS;
		for (JsonElement entry : textures.getAsJsonArray()) {
			if (count >= raw.length) {
				break;
			}
			if (entry != null && entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
				raw[count++] = entry.getAsString();
			}
		}
		String[] compact = new String[count];
		System.arraycopy(raw, 0, compact, 0, count);
		String[] normalized = AttExemptionParsing.normalize(
				compact,
				AttExemptionParsing.MAX_RESOURCE_ENTRIES
		);
		return new ParseResult(
				normalized,
				truncated || normalized.length < count
		);
	}

	public record ParseResult(String[] identifiers, boolean truncated) {
	}
}
