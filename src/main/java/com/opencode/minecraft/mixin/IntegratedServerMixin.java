package com.opencode.minecraft.mixin;

import com.opencode.minecraft.OpenCodeMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.PlayerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Mixin to control the integrated server's tick execution.
 * This allows us to pause the game when OpenCode is idle.
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {

    @Unique
    private int opencode$ticksSincePlayerJoined = 0;

    @Unique
    private static final int OPENCODE$GRACE_TICKS = 100; // ~5 seconds at 20 TPS

    /**
     * Inject at the head of the tick method to potentially cancel it.
     * When cancelled, the world won't update (entities freeze, time stops).
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void opencode$onTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        // Cast to MinecraftServer to access getPlayerManager()
        MinecraftServer server = (MinecraftServer) (Object) this;

        // Never pause if no players are connected yet
        PlayerManager playerManager = server.getPlayerManager();
        if (playerManager == null || playerManager.getPlayerList().isEmpty()) {
            opencode$ticksSincePlayerJoined = 0;
            return;
        }

        // Grace period after player joins - let the world fully load
        if (opencode$ticksSincePlayerJoined < OPENCODE$GRACE_TICKS) {
            opencode$ticksSincePlayerJoined++;
            return;
        }

        // Now check if we should pause
        if (OpenCodeMod.getPauseController() != null &&
            OpenCodeMod.getPauseController().shouldGameBePaused()) {
            ci.cancel();
        }
    }
}
