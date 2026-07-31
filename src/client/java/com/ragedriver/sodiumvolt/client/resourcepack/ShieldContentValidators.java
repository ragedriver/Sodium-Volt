package com.ragedriver.sodiumvolt.client.resourcepack;

import java.io.IOException;
import java.io.InputStream;

public final class ShieldContentValidators {
	private static final byte[] PNG_SIGNATURE = new byte[]{
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
	};

	private ShieldContentValidators() {
	}

	public static ShieldReason validatePngPrefix(
			byte[] prefix,
			int length,
			ResourcePackShieldPolicy policy
	) {
		if (prefix == null || length < 24) {
			return ShieldReason.PNG_HEADER;
		}
		for (int index = 0; index < PNG_SIGNATURE.length; index++) {
			if (prefix[index] != PNG_SIGNATURE[index]) {
				return ShieldReason.PNG_HEADER;
			}
		}
		if (readUnsignedInt(prefix, 8) != 13L
				|| prefix[12] != 'I' || prefix[13] != 'H'
				|| prefix[14] != 'D' || prefix[15] != 'R') {
			return ShieldReason.PNG_HEADER;
		}
		long width = readUnsignedInt(prefix, 16);
		long height = readUnsignedInt(prefix, 20);
		if (width == 0L || height == 0L
				|| width > policy.maximumPngDimension()
				|| height > policy.maximumPngDimension()
				|| width > policy.maximumPngPixels() / height) {
			return ShieldReason.PNG_DIMENSIONS;
		}
		return ShieldReason.NONE;
	}

	public static ShieldReason validatePng(
			InputStream input,
			ResourcePackShieldPolicy policy
	) throws IOException {
		byte[] prefix = new byte[24];
		int offset = 0;
		while (offset < prefix.length) {
			int read = input.read(prefix, offset, prefix.length - offset);
			if (read < 0) {
				break;
			}
			if (read == 0) {
				int one = input.read();
				if (one < 0) {
					break;
				}
				prefix[offset++] = (byte) one;
			} else {
				offset += read;
			}
		}
		return validatePngPrefix(prefix, offset, policy);
	}

	public static ShieldReason validateJson(
			InputStream input,
			long maximumBytes,
			int maximumDepth,
			long deadlineNanos
	) throws IOException {
		JsonLexicalValidator validator = new JsonLexicalValidator(maximumDepth);
		byte[] buffer = new byte[8_192];
		long total = 0L;
		while (true) {
			if (deadlineNanos != Long.MAX_VALUE && System.nanoTime() - deadlineNanos >= 0L) {
				return ShieldReason.SCAN_TIME;
			}
			int remaining = (int) Math.min(buffer.length, maximumBytes - total + 1L);
			if (remaining <= 0) {
				return ShieldReason.SINGLE_RESOURCE_SIZE;
			}
			int read = input.read(buffer, 0, remaining);
			if (read < 0) {
				return validator.finish();
			}
			if (read == 0) {
				continue;
			}
			total += read;
			if (total > maximumBytes) {
				return ShieldReason.SINGLE_RESOURCE_SIZE;
			}
			for (int index = 0; index < read; index++) {
				ShieldReason reason = validator.accept(buffer[index] & 0xFF);
				if (reason != ShieldReason.NONE) {
					return reason;
				}
			}
		}
	}

	static long readUnsignedInt(byte[] value, int offset) {
		return (long) (value[offset] & 0xFF) << 24
				| (long) (value[offset + 1] & 0xFF) << 16
				| (long) (value[offset + 2] & 0xFF) << 8
				| value[offset + 3] & 0xFFL;
	}

	public static final class JsonLexicalValidator {
		private final byte[] containers;
		private int depth;
		private boolean inString;
		private boolean escaped;
		private boolean failed;

		public JsonLexicalValidator(int maximumDepth) {
			this.containers = new byte[Math.max(1, maximumDepth)];
		}

		public ShieldReason accept(int value) {
			if (this.failed) {
				return ShieldReason.JSON_NESTING;
			}
			if (this.inString) {
				if (this.escaped) {
					this.escaped = false;
				} else if (value == '\\') {
					this.escaped = true;
				} else if (value == '"') {
					this.inString = false;
				} else if (value >= 0 && value < 0x20) {
					this.failed = true;
				}
				return this.failed ? ShieldReason.JSON_NESTING : ShieldReason.NONE;
			}
			if (value == '"') {
				this.inString = true;
			} else if (value == '{' || value == '[') {
				if (this.depth >= this.containers.length) {
					this.failed = true;
				} else {
					this.containers[this.depth++] = (byte) value;
				}
			} else if (value == '}' || value == ']') {
				byte expected = (byte) (value == '}' ? '{' : '[');
				if (this.depth == 0 || this.containers[--this.depth] != expected) {
					this.failed = true;
				}
			}
			return this.failed ? ShieldReason.JSON_NESTING : ShieldReason.NONE;
		}

		public ShieldReason finish() {
			return this.failed || this.inString || this.escaped || this.depth != 0
					? ShieldReason.JSON_NESTING
					: ShieldReason.NONE;
		}
	}
}
