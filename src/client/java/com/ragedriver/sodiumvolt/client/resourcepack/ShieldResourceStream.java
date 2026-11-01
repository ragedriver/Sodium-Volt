package com.ragedriver.sodiumvolt.client.resourcepack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/** Applies protection while the supplier's synchronous reload context is still active. */
public final class ShieldResourceStream {
	private ShieldResourceStream() {
	}

	public static <T extends AutoCloseable> Stream<T> guard(Stream<T> source, UnaryOperator<T> guard) {
		List<T> opened = new ArrayList<>();
		List<T> protectedResources = new ArrayList<>();
		Map<T, T> guardedByOriginal = new IdentityHashMap<>();
		try (source) {
			source.forEachOrdered(opened::add);
			for (T resource : opened) {
				T protectedResource = guardedByOriginal.get(resource);
				if (protectedResource == null) {
					protectedResource = Objects.requireNonNull(guard.apply(resource));
					guardedByOriginal.put(resource, protectedResource);
				}
				protectedResources.add(protectedResource);
			}
		} catch (RuntimeException | Error failure) {
			Throwable originalFailure = failure;
			Set<T> closed = Collections.newSetFromMap(new IdentityHashMap<>());
			for (int index = 0; index < opened.size(); index++) {
				if (!closed.add(opened.get(index))) {
					continue;
				}
				T resource = index < protectedResources.size() ? protectedResources.get(index) : opened.get(index);
				try {
					resource.close();
				} catch (Exception | Error closeFailure) {
					Throwable cleanupFailure = closeFailure;
					if (cleanupFailure != originalFailure) {
						originalFailure.addSuppressed(cleanupFailure);
					}
				}
			}
			throw failure;
		}
		// Resource ownership transfers to Minecraft's resource manager, as before 26.3.
		return protectedResources.stream();
	}
}
