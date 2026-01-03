package com.opencode.minecraft.mixin;

import com.opencode.minecraft.OpenCodeMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for MinecraftClient to hook into rendering and screen changes.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    /**
     * Hook into the render loop to update status display
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void opencode$onRender(boolean tick, CallbackInfo ci) {
        // Update pause controller status display if needed
        if (OpenCodeMod.getPauseController() != null) {
            OpenCodeMod.getPauseController().tick();
        }
    }

    /**
     * Detect when chat screen is opened/closed
     */
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void opencode$onSetScreen(Screen screen, CallbackInfo ci) {
        if (OpenCodeMod.getPauseController() != null) {
            // Check if entering chat screen
            boolean isChatScreen = screen != null &&
                screen.getClass().getSimpleName().contains("Chat");

            // Note: We set userTyping via command handling, not screen detection
            // This is just for potential future UI integration
        }
    }
}
