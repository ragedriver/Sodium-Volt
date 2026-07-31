package com.ragedriver.sodiumvolt.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ragedriver.sodiumvolt.client.inspector.VoltInspectorEngine;
import com.ragedriver.sodiumvolt.client.performance.BlockEntityRenderBudgetEngine;
import com.ragedriver.sodiumvolt.client.resourcepack.ResourcePackShieldEngine;
import com.ragedriver.sodiumvolt.client.resourcepack.ShieldReloadContextStack;
import com.ragedriver.sodiumvolt.client.watchdog.GpuTimeoutWatchdogEngine;
import net.minecraft.client.GameLoadCookie;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public abstract class MinecraftResourceReloadMixin {
	@Unique
	private static final int SODIUM_VOLT_MAX_RELOAD_NESTING = 32;
	@Unique
	private Deque<VoltInspectorEngine.ReloadObservation> sodiumVolt$reloadObservations;
	@Unique
	private Deque<GpuTimeoutWatchdogEngine.ReloadToken> sodiumVolt$watchdogReloadTokens;
	@Unique
	private Deque<ResourcePackShieldEngine.ReloadToken> sodiumVolt$shieldReloadTokens;
	@Unique
	private ShieldReloadContextStack<ResourcePackShieldEngine.ReloadToken>
			sodiumVolt$privateShieldContexts;
	@Unique
	private int sodiumVolt$reloadOverflowDepth;

	@WrapMethod(
			method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;"
	)
	private CompletableFuture<Void> sodiumVolt$wrapPublicResourceReload(
			Operation<CompletableFuture<Void>> original
	) {
		BlockEntityRenderBudgetEngine.onResourceReload();
		sodiumVolt$ensureReloadState();
		if (this.sodiumVolt$reloadObservations.size() >= SODIUM_VOLT_MAX_RELOAD_NESTING) {
			this.sodiumVolt$reloadOverflowDepth++;
			try {
				return original.call();
			} finally {
				this.sodiumVolt$reloadOverflowDepth--;
			}
		}
		VoltInspectorEngine.ReloadObservation observation =
				VoltInspectorEngine.beginResourceReload();
		GpuTimeoutWatchdogEngine.ReloadToken watchdogToken =
				GpuTimeoutWatchdogEngine.beginResourceReload();
		ResourcePackShieldEngine.ReloadToken shieldToken =
				ResourcePackShieldEngine.beginResourceReload();
		this.sodiumVolt$reloadObservations.push(observation);
		this.sodiumVolt$watchdogReloadTokens.push(watchdogToken);
		this.sodiumVolt$shieldReloadTokens.push(shieldToken);

		CompletableFuture<Void> future = null;
		try {
			future = original.call();
			return future;
		} finally {
			if (!this.sodiumVolt$reloadObservations.isEmpty()
					&& this.sodiumVolt$reloadObservations.peek() == observation) {
				this.sodiumVolt$reloadObservations.pop();
			}
			VoltInspectorEngine.watchResourceReload(future, observation);
			if (!this.sodiumVolt$watchdogReloadTokens.isEmpty()
					&& this.sodiumVolt$watchdogReloadTokens.peek() == watchdogToken) {
				this.sodiumVolt$watchdogReloadTokens.pop();
			}
			GpuTimeoutWatchdogEngine.watchResourceReload(future, watchdogToken);
			if (!this.sodiumVolt$shieldReloadTokens.isEmpty()
					&& this.sodiumVolt$shieldReloadTokens.peek() == shieldToken) {
				this.sodiumVolt$shieldReloadTokens.pop();
			}
			ResourcePackShieldEngine.watchResourceReload(future, shieldToken);
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
		sodiumVolt$ensureReloadState();
		ShieldReloadContextStack.Frame<ResourcePackShieldEngine.ReloadToken> frame =
				this.sodiumVolt$privateShieldContexts.begin(
						this.sodiumVolt$shieldReloadTokens.peek(),
						!this.sodiumVolt$shieldReloadTokens.isEmpty(),
						this.sodiumVolt$reloadOverflowDepth > 0,
						ResourcePackShieldEngine::beginResourceReload,
						ResourcePackShieldEngine.ReloadToken.DISABLED
				);
		ResourcePackShieldEngine.ReloadToken token = frame == null
				? ResourcePackShieldEngine.ReloadToken.DISABLED
				: frame.token();
		if (frame != null && frame.ownsToken()) {
			this.sodiumVolt$shieldReloadTokens.push(frame.token());
		}
		ResourcePackShieldEngine.enterSynchronousReload(token);
		CompletableFuture<Void> future = null;
		try {
			future = original.call(recovery, cookie);
			return future;
		} finally {
			ResourcePackShieldEngine.exitSynchronousReload();
			ShieldReloadContextStack.Frame<ResourcePackShieldEngine.ReloadToken> completed =
					this.sodiumVolt$privateShieldContexts.finish();
			if (completed != null && completed.ownsToken()) {
				if (!this.sodiumVolt$shieldReloadTokens.isEmpty()
						&& this.sodiumVolt$shieldReloadTokens.peek() == completed.token()) {
					this.sodiumVolt$shieldReloadTokens.pop();
				}
				ResourcePackShieldEngine.watchResourceReload(future, completed.token());
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
				this.sodiumVolt$privateShieldContexts == null
								|| this.sodiumVolt$privateShieldContexts.isEmpty()
						? null
						: this.sodiumVolt$privateShieldContexts.currentOr(
								ResourcePackShieldEngine.ReloadToken.DISABLED
						);
		return ResourcePackShieldEngine.guardInitialReloadTask(initialTask, token);
	}

	@Unique
	private void sodiumVolt$ensureReloadState() {
		if (this.sodiumVolt$reloadObservations == null) {
			this.sodiumVolt$reloadObservations = new ArrayDeque<>(4);
		}
		if (this.sodiumVolt$watchdogReloadTokens == null) {
			this.sodiumVolt$watchdogReloadTokens = new ArrayDeque<>(4);
		}
		if (this.sodiumVolt$shieldReloadTokens == null) {
			this.sodiumVolt$shieldReloadTokens = new ArrayDeque<>(4);
		}
		if (this.sodiumVolt$privateShieldContexts == null) {
			this.sodiumVolt$privateShieldContexts =
					new ShieldReloadContextStack<>(SODIUM_VOLT_MAX_RELOAD_NESTING);
		}
	}
}
