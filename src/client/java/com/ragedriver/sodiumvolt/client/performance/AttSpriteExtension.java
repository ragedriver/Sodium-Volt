package com.ragedriver.sodiumvolt.client.performance;

public interface AttSpriteExtension {
	void sodiumVolt$recordVisibility(
			int generation,
			int previousGeneration,
			float distanceSquared,
			long clientTick
	);

	int sodiumVolt$visibilityGeneration();

	float sodiumVolt$minimumDistanceSquared();

	long sodiumVolt$lastVisibleTick();

	boolean sodiumVolt$resumePending();

	void sodiumVolt$consumeResume();

	void sodiumVolt$clearVisibility();
}
