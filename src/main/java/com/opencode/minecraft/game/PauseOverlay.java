package com.opencode.minecraft.game;

import com.opencode.minecraft.OpenCodeMod;
import com.opencode.minecraft.client.session.SessionStatus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Renders an overlay when the game is paused by OpenCode.
 */
public class PauseOverlay {

    /**
     * Renders the pause overlay if the game is paused.
     * Called from HUD rendering.
     */
    public static void render(DrawContext context, float tickDelta) {
        PauseController pauseController = OpenCodeMod.getPauseController();
        if (pauseController == null || !pauseController.shouldGameBePaused()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        TextRenderer textRenderer = client.textRenderer;

        // Semi-transparent dark overlay
        int overlayColor = 0x88000000;
        context.fill(0, 0, screenWidth, screenHeight, overlayColor);

        // Main message
        String mainMessage = getMainMessage(pauseController);
        String subMessage = getSubMessage(pauseController);

        // Center the text
        int mainWidth = textRenderer.getWidth(mainMessage);
        int subWidth = textRenderer.getWidth(subMessage);

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // Draw main message with shadow
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(mainMessage).formatted(Formatting.GOLD, Formatting.BOLD),
            centerX - mainWidth / 2,
            centerY - 20,
            0xFFFFAA00
        );

        // Draw sub message
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(subMessage).formatted(Formatting.GRAY),
            centerX - subWidth / 2,
            centerY + 5,
            0xFFAAAAAA
        );

        // Draw hint at bottom
        String hint = "Use /oc <prompt> to give " + OpenCodeMod.getAgentName() + " a task";
        int hintWidth = textRenderer.getWidth(hint);
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(hint).formatted(Formatting.DARK_GRAY, Formatting.ITALIC),
            centerX - hintWidth / 2,
            centerY + 30,
            0xFF666666
        );
    }

    private static String getMainMessage(PauseController controller) {
        SessionStatus status = controller.getStatus();
        String agentName = OpenCodeMod.getAgentName();

        if (controller.isUserTyping()) {
            return "GAME PAUSED - Typing...";
        }

        return switch (status) {
            case DISCONNECTED -> "GAME PAUSED - Not Connected";
            case IDLE -> "GAME PAUSED - Waiting for Task";
            case BUSY -> "Processing...";
            case GENERATING -> agentName + " is working...";
            case RETRY -> "Retrying...";
        };
    }

    private static String getSubMessage(PauseController controller) {
        SessionStatus status = controller.getStatus();
        boolean isCodex = OpenCodeMod.isCodexBackend();

        if (controller.isUserTyping()) {
            return "Game will resume when output starts";
        }

        return switch (status) {
            case DISCONNECTED -> isCodex
                    ? "Ensure Codex is installed and logged in"
                    : "Start OpenCode with: opencode serve";
            case IDLE -> "Waiting for your next task";
            case BUSY -> "Waiting for response...";
            case GENERATING -> "Game resumed - LLM is generating tokens";
            case RETRY -> "Connection issue, retrying...";
        };
    }
}
