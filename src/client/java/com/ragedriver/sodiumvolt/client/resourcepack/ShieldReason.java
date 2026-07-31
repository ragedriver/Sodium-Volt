package com.ragedriver.sodiumvolt.client.resourcepack;

/**
 * Fixed, non-user-controlled classifications used by reports, notifications and Inspector.
 */
public enum ShieldReason {
	NONE,
	UNSAFE_PATH,
	SYMLINK,
	SPECIAL_FILE,
	ENTRY_LIMIT,
	ARCHIVE_SIZE,
	SINGLE_RESOURCE_SIZE,
	TOTAL_RESOURCE_SIZE,
	UNKNOWN_METADATA,
	COMPRESSION_RATIO,
	PNG_HEADER,
	PNG_DIMENSIONS,
	JSON_NESTING,
	CORE_SHADER_OVERRIDE,
	SCAN_TIME,
	LIVE_READ_LIMIT,
	RESOURCE_OUTPUT_LIMIT,
	MONITOR_FAILURE
}
