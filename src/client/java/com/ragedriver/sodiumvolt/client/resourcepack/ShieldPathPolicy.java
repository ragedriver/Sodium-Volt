package com.ragedriver.sodiumvolt.client.resourcepack;

import java.nio.file.Path;

public final class ShieldPathPolicy {
	private ShieldPathPolicy() {
	}

	public static boolean isSafe(String path, int maximumLength, int maximumDepth) {
		if (!isStructurallySafeRelative(path) || path.length() > maximumLength) {
			return false;
		}
		int depth = 0;
		int start = 0;
		for (int index = 0; index <= path.length(); index++) {
			if (index != path.length() && path.charAt(index) != '/') {
				continue;
			}
			int length = index - start;
			boolean trailingDirectorySlash = index == path.length() && length == 0 && depth > 0;
			if (!trailingDirectorySlash) {
				if (length == 0
						|| length == 1 && path.charAt(start) == '.'
						|| length == 2 && path.charAt(start) == '.'
								&& path.charAt(start + 1) == '.') {
					return false;
				}
				if (++depth > maximumDepth) {
					return false;
				}
			}
			start = index + 1;
		}
		return depth > 0;
	}

	public static boolean isStructurallySafeRelative(String path) {
		if (path == null || path.isEmpty()
				|| path.indexOf('\0') >= 0 || path.indexOf('\\') >= 0
				|| path.charAt(0) == '/' || path.startsWith("//")
				|| isWindowsAbsolute(path)) {
			return false;
		}
		int start = 0;
		for (int index = 0; index <= path.length(); index++) {
			if (index != path.length() && path.charAt(index) != '/') {
				continue;
			}
			int length = index - start;
			boolean trailingDirectorySlash = index == path.length()
					&& length == 0 && start > 0;
			if (!trailingDirectorySlash
					&& (length == 0
							|| length == 1 && path.charAt(start) == '.'
							|| length == 2 && path.charAt(start) == '.'
									&& path.charAt(start + 1) == '.')) {
				return false;
			}
			start = index + 1;
		}
		return true;
	}

	public static boolean isSafeRelativePath(
			Path relative,
			int maximumLength,
			int maximumDepth
	) {
		if (relative == null || relative.isAbsolute()
				|| relative.getNameCount() == 0
				|| relative.getNameCount() > maximumDepth) {
			return false;
		}
		int characters = Math.max(0, relative.getNameCount() - 1);
		for (Path component : relative) {
			String value = component.toString();
			if (value.isEmpty() || value.equals(".") || value.equals("..")
					|| value.indexOf('\0') >= 0 || value.indexOf('\\') >= 0) {
				return false;
			}
			characters += value.length();
			if (characters > maximumLength) {
				return false;
			}
		}
		return true;
	}

	public static boolean isCoreShader(String normalizedPath) {
		return normalizedPath != null
				&& normalizedPath.startsWith("assets/minecraft/shaders/core/")
				&& !normalizedPath.endsWith("/");
	}

	private static boolean isWindowsAbsolute(String path) {
		return path.length() >= 2
				&& ((path.charAt(0) >= 'A' && path.charAt(0) <= 'Z')
						|| (path.charAt(0) >= 'a' && path.charAt(0) <= 'z'))
				&& path.charAt(1) == ':';
	}
}
