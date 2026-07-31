package com.ragedriver.sodiumvolt.client.recovery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ragedriver.sodiumvolt.SodiumVolt;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

final class RecoveryReportWriter {
	private static final Path REPORT_PATH = FabricLoader.getInstance().getConfigDir()
			.resolve("sodium-volt-recovery-report.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private RecoveryReportWriter() {
	}

	static boolean write(RecoveryReport report) {
		Path temporaryPath = null;
		try {
			Path directory = REPORT_PATH.getParent();
			Files.createDirectories(directory);
			if (Files.exists(REPORT_PATH)
					&& (Files.isSymbolicLink(REPORT_PATH) || !Files.isRegularFile(REPORT_PATH))) {
				throw new IOException("Unsafe recovery report target");
			}
			temporaryPath = Files.createTempFile(directory, "sodium-volt-recovery-report-", ".tmp");
			String document = GSON.toJson(report.toJson());
			if (document.length() > 4096) {
				throw new IOException("Recovery report exceeded its fixed bound");
			}
			Files.writeString(
					temporaryPath,
					document,
					StandardCharsets.UTF_8,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE
			);
			moveIntoPlace(temporaryPath, REPORT_PATH);
			temporaryPath = null;
			return true;
		} catch (IOException | RuntimeException | StackOverflowError exception) {
			SodiumVolt.LOGGER.warn("Could not write the sanitized local Volt Recovery report");
			return false;
		} finally {
			if (temporaryPath != null) {
				try {
					Files.deleteIfExists(temporaryPath);
				} catch (IOException | SecurityException exception) {
					SodiumVolt.LOGGER.warn("Could not remove a temporary Volt Recovery report");
				}
			}
		}
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
}
