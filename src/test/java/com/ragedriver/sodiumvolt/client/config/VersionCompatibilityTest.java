package com.ragedriver.sodiumvolt.client.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class VersionCompatibilityTest {
	public static void main(String[] args) throws Exception {
		JsonObject dependencies;
		try (var stream = Objects.requireNonNull(
				VersionCompatibilityTest.class.getResourceAsStream("/fabric.mod.json"));
			 var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			dependencies = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("depends");
		}
		check(dependencies, "fabricloader", "0.19.4", false);
		check(dependencies, "fabricloader", "0.19.5", true);
		check(dependencies, "fabricloader", "0.20.1", true);
		check(dependencies, "fabricloader", "0.21.0", true);
		check(dependencies, "minecraft", "26.2", false);
		check(dependencies, "minecraft", "26.3", true);
		check(dependencies, "minecraft", "26.3.1", true);
		check(dependencies, "minecraft", "26.4", false);
		check(dependencies, "sodium", "0.9.1+mc26.2", false);
		check(dependencies, "sodium", "0.9.2+mc26.3", true);
		check(dependencies, "sodium", "0.9.3+mc26.3", false);
		check(dependencies, "fabric-api", "0.164.0+26.3", false);
		check(dependencies, "fabric-api", "0.165.0+26.3", true);
		System.out.println("Fabric dependency version compatibility tests passed");
	}

	private static void check(JsonObject dependencies, String id, String version, boolean expected) throws Exception {
		String predicate = dependencies.get(id).getAsString();
		boolean actual = VersionPredicate.parse(predicate).test(Version.parse(version));
		if (actual != expected) {
			throw new AssertionError(id + " " + predicate + " matched " + version + ": " + actual);
		}
	}
}
