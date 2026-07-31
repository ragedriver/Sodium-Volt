package com.ragedriver.sodiumvolt.client.mixin;

import com.ragedriver.sodiumvolt.client.performance.AttSpriteExtension;
import com.ragedriver.sodiumvolt.client.performance.AttVisibilityLogic;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteAttMixin implements AttSpriteExtension {
	@Unique
	private int sodiumVolt$visibilityGeneration;
	@Unique
	private float sodiumVolt$minimumDistanceSquared = Float.POSITIVE_INFINITY;
	@Unique
	private long sodiumVolt$lastVisibleTick = Long.MIN_VALUE;
	@Unique
	private boolean sodiumVolt$resumePending;

	@Override
	public void sodiumVolt$recordVisibility(
			int generation,
			int previousGeneration,
			float distanceSquared,
			long clientTick
	) {
		if (this.sodiumVolt$visibilityGeneration != generation) {
			this.sodiumVolt$resumePending =
					AttVisibilityLogic.isNewlyVisible(
							this.sodiumVolt$visibilityGeneration,
							previousGeneration
					);
			this.sodiumVolt$visibilityGeneration = generation;
			this.sodiumVolt$minimumDistanceSquared =
					AttVisibilityLogic.minimumDistance(Float.POSITIVE_INFINITY, distanceSquared);
		} else {
			this.sodiumVolt$minimumDistanceSquared = AttVisibilityLogic.minimumDistance(
					this.sodiumVolt$minimumDistanceSquared,
					distanceSquared
			);
		}
		this.sodiumVolt$lastVisibleTick = clientTick;
	}

	@Override
	public int sodiumVolt$visibilityGeneration() {
		return this.sodiumVolt$visibilityGeneration;
	}

	@Override
	public float sodiumVolt$minimumDistanceSquared() {
		return this.sodiumVolt$minimumDistanceSquared;
	}

	@Override
	public long sodiumVolt$lastVisibleTick() {
		return this.sodiumVolt$lastVisibleTick;
	}

	@Override
	public boolean sodiumVolt$resumePending() {
		return this.sodiumVolt$resumePending;
	}

	@Override
	public void sodiumVolt$consumeResume() {
		this.sodiumVolt$resumePending = false;
	}

	@Override
	public void sodiumVolt$clearVisibility() {
		this.sodiumVolt$visibilityGeneration = 0;
		this.sodiumVolt$minimumDistanceSquared = Float.POSITIVE_INFINITY;
		this.sodiumVolt$lastVisibleTick = Long.MIN_VALUE;
		this.sodiumVolt$resumePending = false;
	}
}
