package com.ragedriver.sodiumvolt.client.performance;

public interface VramTrackedResource {
	void sodiumVolt$registerVramEstimate(long bytes, boolean texture, boolean renderAttachment);

	void sodiumVolt$releaseVramEstimate();
}
