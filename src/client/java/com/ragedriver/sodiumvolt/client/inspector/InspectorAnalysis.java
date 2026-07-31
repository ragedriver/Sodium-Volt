package com.ragedriver.sodiumvolt.client.inspector;

import java.util.ArrayList;
import java.util.List;

public final class InspectorAnalysis {
	private InspectorAnalysis() {
	}

	public static Bottleneck estimateBottleneck(double gpuUtilization, double p95Ms, int spikeThresholdMs) {
		if (!Double.isFinite(gpuUtilization) || gpuUtilization < 0.0D || p95Ms <= 0.0D) {
			return Bottleneck.UNKNOWN;
		}
		if (p95Ms < spikeThresholdMs) {
			return Bottleneck.NO_CLEAR_LIMIT;
		}
		if (gpuUtilization >= 90.0D) {
			return Bottleneck.GPU_LIKELY;
		}
		if (gpuUtilization <= 65.0D) {
			return Bottleneck.CPU_LIKELY;
		}
		return Bottleneck.MIXED;
	}

	public static List<String> recommendations(
			double p95Ms,
			int spikeThresholdMs,
			int particleCount,
			int blockEntityCount,
			int displayEntityCount,
			long gcTimeDeltaMs,
			double gpuUtilization,
			int chunkQueueSize,
			long reloadDurationMs
	) {
		ArrayList<String> result = new ArrayList<>(3);
		if (gpuUtilization >= 90.0D && p95Ms >= spikeThresholdMs) {
			add(result, "GPU load is high during slow frames; consider lowering resolution or shader quality.");
		}
		if (chunkQueueSize >= 8) {
			add(result, "Chunk builds are queued; consider lowering render distance or moving more slowly.");
		}
		if (particleCount >= 4_096) {
			add(result, "Particle volume is high; Volt Guard's particle budget may smooth spikes.");
		}
		if (blockEntityCount + displayEntityCount >= 384) {
			add(result, "The scene is entity-dense; Volt Guard's entity budgets may reduce render pressure.");
		}
		if (gcTimeDeltaMs >= 25L) {
			add(result, "JVM collection time increased; review memory-heavy resource packs or mods.");
		}
		if (reloadDurationMs >= 10_000L) {
			add(result, "The last resource reload was long; review large or overlapping resource packs.");
		}
		if (p95Ms >= spikeThresholdMs) {
			add(result, "Frame-time spikes persist; reduce view distance or expensive visual effects.");
		}
		return List.copyOf(result);
	}

	private static void add(ArrayList<String> result, String recommendation) {
		if (result.size() < 3) {
			result.add(recommendation);
		}
	}

	public enum Bottleneck {
		CPU_LIKELY("CPU-bound estimate"),
		GPU_LIKELY("GPU-bound estimate"),
		MIXED("Mixed CPU/GPU estimate"),
		NO_CLEAR_LIMIT("No clear bottleneck"),
		UNKNOWN("Unknown");

		private final String label;

		Bottleneck(String label) {
			this.label = label;
		}

		public String label() {
			return this.label;
		}
	}
}
