package com.ragedriver.sodiumvolt.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ragedriver.sodiumvolt.client.inspector.VoltInspectorEngine;
import com.ragedriver.sodiumvolt.client.performance.BlockEntityRenderBudgetEngine;
import com.ragedriver.sodiumvolt.client.resourcepack.ResourcePackShieldEngine;
import com.ragedriver.sodiumvolt.client.resourcepack.ShieldReloadGate;
import com.ragedriver.sodiumvolt.client.watchdog.GpuTimeoutWatchdogEngine;
import net.minecraft.client.GameLoadCookie;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public abstract class MinecraftResourceReloadMixin {
	@Unique
	private static final int SODIUM_VOLT_MAX_RELOAD_NESTING = 32;
	@Unique
	private int sodiumVolt$publicReloadDepth;
	@Unique
	private int sodiumVolt$reloadOverflowDepth;
	@Unique
	private ResourcePackShieldEngine.ReloadToken sodiumVolt$currentPublicShieldToken;
	@Unique
	private boolean sodiumVolt$publicShieldTokenAvailable;
	@Unique
	private int sodiumVolt$privateReloadDepth;
	@Unique
	private int sodiumVolt$privateReloadOverflowDepth;
	@Unique
	private ResourcePackShieldEngine.ReloadToken sodiumVolt$currentPrivateShieldToken;

	@WrapMethod(
			method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;"
	)
	private CompletableFuture<Void> sodiumVolt$wrapPublicResourceReload(
			Operation<CompletableFuture<Void>> original
	) {
		BlockEntityRenderBudgetEngine.onResourceReload();
		if (this.sodiumVolt$reloadOverflowDepth > 0
				|| this.sodiumVolt$publicReloadDepth >= SODIUM_VOLT_MAX_RELOAD_NESTING) {
			this.sodiumVolt$reloadOverflowDepth++;
			try {
				return original.call();
			} finally {
				this.sodiumVolt$reloadOverflowDepth--;
			}
		}
		this.sodiumVolt$publicReloadDepth++;
		VoltInspectorEngine.ReloadObservation observation = null;
		GpuTimeoutWatchdogEngine.ReloadToken watchdogToken = null;
		ResourcePackShieldEngine.ReloadToken shieldToken =
				ResourcePackShieldEngine.ReloadToken.DISABLED;
		ResourcePackShieldEngine.ReloadToken previousShieldToken =
				this.sodiumVolt$currentPublicShieldToken;
		boolean previousShieldTokenAvailable = this.sodiumVolt$publicShieldTokenAvailable;

		CompletableFuture<Void> future = null;
		try {
			observation = VoltInspectorEngine.beginResourceReload();
			watchdogToken = GpuTimeoutWatchdogEngine.beginResourceReload();
			shieldToken = ResourcePackShieldEngine.beginResourceReload();
			this.sodiumVolt$currentPublicShieldToken = shieldToken;
			this.sodiumVolt$publicShieldTokenAvailable = true;
			future = original.call();
			return future;
		} finally {
			try {
				VoltInspectorEngine.watchResourceReload(future, observation);
				GpuTimeoutWatchdogEngine.watchResourceReload(future, watchdogToken);
				ResourcePackShieldEngine.watchResourceReload(future, shieldToken);
			} finally {
				this.sodiumVolt$currentPublicShieldToken = previousShieldToken;
				this.sodiumVolt$publicShieldTokenAvailable =
						previousShieldTokenAvailable;
				this.sodiumVolt$publicReloadDepth--;
			}
		}
	}

	@WrapMethod(
			method = "reloadResourcePacks(ZLnet/minecraft/client/GameLoadCookie;)"
					+ "Ljava/util/concurrent/CompletableFuture;"
	)
	private CompletableFuture<Void> sodiumVolt$wrapPrivateShieldReload(
			boolean recovery,
			GameLoadCookie cookie,
			Operation<CompletableFuture<Void>> original
	) {
		if (this.sodiumVolt$privateReloadOverflowDepth > 0
				|| this.sodiumVolt$privateReloadDepth >= SODIUM_VOLT_MAX_RELOAD_NESTING) {
			this.sodiumVolt$privateReloadOverflowDepth++;
			ResourcePackShieldEngine.ReloadToken previous =
					this.sodiumVolt$currentPrivateShieldToken;
			this.sodiumVolt$currentPrivateShieldToken =
					ResourcePackShieldEngine.ReloadToken.DISABLED;
			ResourcePackShieldEngine.enterSynchronousReload(
					ResourcePackShieldEngine.ReloadToken.DISABLED
			);
			try {
				return original.call(recovery, cookie);
			} finally {
				try {
					ResourcePackShieldEngine.exitSynchronousReload();
				} finally {
					this.sodiumVolt$currentPrivateShieldToken = previous;
					this.sodiumVolt$privateReloadOverflowDepth--;
				}
			}
		}
		this.sodiumVolt$privateReloadDepth++;
		ResourcePackShieldEngine.ReloadToken previous =
				this.sodiumVolt$currentPrivateShieldToken;
		ShieldReloadGate.PrivateTokenSource tokenSource =
				ShieldReloadGate.privateTokenSource(
						this.sodiumVolt$privateReloadDepth,
						this.sodiumVolt$reloadOverflowDepth,
						this.sodiumVolt$publicShieldTokenAvailable
				);
		boolean ownsToken = tokenSource.ownsToken();
		ResourcePackShieldEngine.ReloadToken token =
				ResourcePackShieldEngine.ReloadToken.DISABLED;
		boolean contextEntered = false;
		CompletableFuture<Void> future = null;
		try {
			token = switch (tokenSource) {
				case DISABLED -> ResourcePackShieldEngine.ReloadToken.DISABLED;
				case PUBLIC -> this.sodiumVolt$currentPublicShieldToken;
				case PRIVATE -> previous;
				case OWNED -> ResourcePackShieldEngine.beginResourceReload();
			};
			this.sodiumVolt$currentPrivateShieldToken = token;
			ResourcePackShieldEngine.enterSynchronousReload(token);
			contextEntered = true;
			future = original.call(recovery, cookie);
			return future;
		} finally {
			try {
				if (contextEntered) {
					ResourcePackShieldEngine.exitSynchronousReload();
				}
			} finally {
				this.sodiumVolt$currentPrivateShieldToken = previous;
				this.sodiumVolt$privateReloadDepth--;
				if (ownsToken) {
					ResourcePackShieldEngine.watchResourceReload(future, token);
				}
			}
		}
	}

	@ModifyArg(
			method = "reloadResourcePacks(ZLnet/minecraft/client/GameLoadCookie;)"
					+ "Ljava/util/concurrent/CompletableFuture;",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/packs/resources/ReloadableResourceManager;"
							+ "createReload(Ljava/util/concurrent/Executor;"
							+ "Ljava/util/concurrent/Executor;"
							+ "Ljava/util/concurrent/CompletableFuture;"
							+ "Ljava/util/List;)"
							+ "Lnet/minecraft/server/packs/resources/ReloadInstance;"
			),
			index = 2
	)
	private CompletableFuture<Unit> sodiumVolt$failRejectedPackThroughReload(
			CompletableFuture<Unit> initialTask
	) {
		ResourcePackShieldEngine.ReloadToken token =
				this.sodiumVolt$currentPrivateShieldToken;
		return ResourcePackShieldEngine.guardInitialReloadTask(initialTask, token);
	}
}
