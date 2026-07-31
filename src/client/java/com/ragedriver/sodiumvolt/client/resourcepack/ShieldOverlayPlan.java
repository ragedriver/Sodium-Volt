package com.ragedriver.sodiumvolt.client.resourcepack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Validated overlay prefixes used to translate physical pack paths to their effective
 * logical paths. Longest component depth wins for nested declarations.
 */
public final class ShieldOverlayPlan {
	public static final ShieldOverlayPlan EMPTY = new ShieldOverlayPlan(List.of());
	private final List<String> prefixes;

	private ShieldOverlayPlan(List<String> prefixes) {
		this.prefixes = prefixes;
	}

	public static Validation validate(
			List<String> declaredPrefixes,
			ResourcePackShieldPolicy policy
	) {
		if (policy == null) {
			return new Validation(ShieldReason.UNKNOWN_METADATA, EMPTY);
		}
		return validate(
				declaredPrefixes,
				policy,
				ZipPackScanner.deadline(policy.maximumScanNanos())
		);
	}

	public static Validation validate(
			List<String> declaredPrefixes,
			ResourcePackShieldPolicy policy,
			long deadlineNanos
	) {
		if (declaredPrefixes == null || policy == null) {
			return new Validation(ShieldReason.UNKNOWN_METADATA, EMPTY);
		}
		if (declaredPrefixes.size() > policy.maximumEntries()) {
			return new Validation(ShieldReason.ENTRY_LIMIT, EMPTY);
		}
		LinkedHashSet<String> unique = new LinkedHashSet<>();
		for (String prefix : declaredPrefixes) {
			if (ZipPackScanner.expired(deadlineNanos)) {
				return new Validation(ShieldReason.SCAN_TIME, EMPTY);
			}
			if (prefix == null || prefix.endsWith("/")
					|| !ShieldPathPolicy.isStructurallySafeRelative(prefix)
					|| policy.detectUnsafePaths()
							&& !ShieldPathPolicy.isSafe(
									prefix,
									policy.maximumPathLength(),
									policy.maximumPathDepth()
							)) {
				return new Validation(ShieldReason.UNSAFE_PATH, EMPTY);
			}
			unique.add(prefix);
		}
		ArrayList<String> ordered = new ArrayList<>(unique);
		ordered.sort(
				Comparator.comparingInt(ShieldOverlayPlan::componentCount)
						.reversed()
						.thenComparing(Comparator.comparingInt(String::length).reversed())
						.thenComparing(Comparator.naturalOrder())
		);
		if (ZipPackScanner.expired(deadlineNanos)) {
			return new Validation(ShieldReason.SCAN_TIME, EMPTY);
		}
		return new Validation(
				ShieldReason.NONE,
				ordered.isEmpty() ? EMPTY : new ShieldOverlayPlan(List.copyOf(ordered))
		);
	}

	public List<String> prefixes() {
		return this.prefixes;
	}

	public String effectivePath(String physicalPath) {
		for (String prefix : this.prefixes) {
			if (physicalPath.equals(prefix)) {
				return "";
			}
			if (physicalPath.startsWith(prefix)
					&& physicalPath.length() > prefix.length()
					&& physicalPath.charAt(prefix.length()) == '/') {
				return physicalPath.substring(prefix.length() + 1);
			}
		}
		return physicalPath;
	}

	private static int componentCount(String path) {
		int count = 1;
		for (int index = 0; index < path.length(); index++) {
			if (path.charAt(index) == '/') {
				count++;
			}
		}
		return count;
	}

	public record Validation(
			ShieldReason reason,
			ShieldOverlayPlan plan
	) {
		public Validation {
			reason = reason == null ? ShieldReason.UNKNOWN_METADATA : reason;
			plan = plan == null ? EMPTY : plan;
		}

		public boolean accepted() {
			return this.reason == ShieldReason.NONE;
		}
	}
}
