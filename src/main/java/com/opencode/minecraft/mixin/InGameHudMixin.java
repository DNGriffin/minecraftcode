package com.opencode.minecraft.mixin;

import com.opencode.minecraft.game.PauseOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to render the pause overlay on the HUD.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    /**
     * Render the pause overlay after the normal HUD renders.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void opencode$onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        PauseOverlay.render(context, tickCounter.getTickDelta(true));
    }
}
