package com.ragedriver.sodiumvolt.client.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticlePositionAccessor {
	@Accessor("x")
	double sodiumVolt$getX();

	@Accessor("y")
	double sodiumVolt$getY();

	@Accessor("z")
	double sodiumVolt$getZ();

	@Accessor("xo")
	void sodiumVolt$setPreviousX(double value);

	@Accessor("yo")
	void sodiumVolt$setPreviousY(double value);

	@Accessor("zo")
	void sodiumVolt$setPreviousZ(double value);

	@Accessor("age")
	int sodiumVolt$getAge();

	@Accessor("age")
	void sodiumVolt$setAge(int value);

	@Accessor("lifetime")
	int sodiumVolt$getLifetime();
}
