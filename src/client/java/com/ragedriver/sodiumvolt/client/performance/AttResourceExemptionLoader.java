package com.ragedriver.sodiumvolt.client.performance;

import com.ragedriver.sodiumvolt.SodiumVolt;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class AttResourceExemptionLoader {
	private static final String EXEMPTION_PATH =
			"sodium_volt/animated_texture_exemptions.json";
	private static final int MAXIMUM_NAMESPACES = 64;
	private static final int MAXIMUM_STACK_ENTRIES_PER_NAMESPACE = 8;

	private AttResourceExemptionLoader() {
	}

	static LoadResult load(ResourceManager manager) {
		String[] accepted = new String[AttExemptionParsing.MAX_RESOURCE_ENTRIES];
		int acceptedCount = 0;
		int filesRead = 0;
		int totalBytes = 0;
		boolean truncated = false;
		int namespacesVisited = 0;
		for (String namespace : manager.getNamespaces()) {
			if (namespacesVisited++ >= MAXIMUM_NAMESPACES
					|| filesRead >= AttExemptionParsing.MAX_RESOURCE_FILES) {
				truncated = true;
				break;
			}
			Identifier location;
			try {
				location = Identifier.fromNamespaceAndPath(namespace, EXEMPTION_PATH);
			} catch (RuntimeException exception) {
				continue;
			}
			List<Resource> stack;
			try {
				stack = manager.getResourceStack(location);
			} catch (RuntimeException | StackOverflowError exception) {
				SodiumVolt.LOGGER.warn("Ignoring unreadable ATT exemption resource {}", location);
				continue;
			}
			int stackEntries = 0;
			for (Resource resource : stack) {
				if (stackEntries++ >= MAXIMUM_STACK_ENTRIES_PER_NAMESPACE
						|| filesRead >= AttExemptionParsing.MAX_RESOURCE_FILES) {
					truncated = true;
					break;
				}
				filesRead++;
				try (InputStream input = resource.open()) {
					ReadResult read = readBounded(
							input,
							Math.min(
									AttExemptionParsing.MAX_FILE_BYTES,
									AttExemptionParsing.MAX_TOTAL_BYTES - totalBytes
							)
					);
					totalBytes += read.bytesRead();
					if (read.truncated()) {
						truncated = true;
						continue;
					}
					AttResourceExemptionJson.ParseResult parsed =
							AttResourceExemptionJson.parse(read.text());
					truncated |= parsed.truncated();
					for (String identifier : parsed.identifiers()) {
						if (acceptedCount >= accepted.length) {
							truncated = true;
							break;
						}
						if (!contains(accepted, acceptedCount, identifier)) {
							accepted[acceptedCount++] = identifier;
						}
					}
				} catch (IOException | RuntimeException | StackOverflowError exception) {
					SodiumVolt.LOGGER.warn(
							"Ignoring malformed ATT exemption resource {}",
							location,
							exception
					);
				}
				if (totalBytes >= AttExemptionParsing.MAX_TOTAL_BYTES) {
					truncated = true;
					break;
				}
			}
			if (totalBytes >= AttExemptionParsing.MAX_TOTAL_BYTES) {
				break;
			}
		}
		String[] compact = new String[acceptedCount];
		System.arraycopy(accepted, 0, compact, 0, acceptedCount);
		return new LoadResult(compact, filesRead, totalBytes, truncated);
	}

	private static ReadResult readBounded(InputStream input, int maximumBytes) throws IOException {
		if (maximumBytes <= 0) {
			return new ReadResult("", 0, true);
		}
		byte[] bytes = new byte[maximumBytes + 1];
		int count = 0;
		while (count < bytes.length) {
			int read = input.read(bytes, count, bytes.length - count);
			if (read < 0) {
				break;
			}
			if (read == 0) {
				int single = input.read();
				if (single < 0) {
					break;
				}
				bytes[count++] = (byte) single;
			} else {
				count += read;
			}
		}
		boolean truncated = count > maximumBytes;
		int acceptedBytes = Math.min(count, maximumBytes);
		return new ReadResult(
				new String(bytes, 0, acceptedBytes, StandardCharsets.UTF_8),
				acceptedBytes,
				truncated
		);
	}

	private static boolean contains(String[] values, int count, String candidate) {
		for (int index = 0; index < count; index++) {
			if (candidate.equals(values[index])) {
				return true;
			}
		}
		return false;
	}

	record LoadResult(String[] identifiers, int filesRead, int bytesRead, boolean truncated) {
	}

	private record ReadResult(String text, int bytesRead, boolean truncated) {
	}
}
