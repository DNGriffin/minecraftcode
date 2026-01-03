package com.opencode.minecraft.mixin;

import com.opencode.minecraft.OpenCodeMod;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to block player movement input when OpenCode is paused.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    /**
     * After tick processes input, zero out all movement if paused.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void opencode$onTick(CallbackInfo ci) {
        if (OpenCodeMod.getPauseController() != null &&
            OpenCodeMod.getPauseController().shouldGameBePaused()) {

            // Zero out all movement by setting a neutral PlayerInput
            this.playerInput = PlayerInput.DEFAULT;
            this.movementForward = 0.0f;
            this.movementSideways = 0.0f;
        }
    }
}
