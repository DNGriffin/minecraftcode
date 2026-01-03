package com.opencode.minecraft.game;

import com.opencode.minecraft.OpenCodeMod;
import com.opencode.minecraft.client.session.SessionStatus;
import net.minecraft.client.MinecraftClient;

/**
 * Controls game pause state based on OpenCode session status.
 *
 * Pause Logic:
 * - PAUSE when session status is IDLE (waiting for user input)
 * - PAUSE when session status is DISCONNECTED
 * - RESUME when session status is BUSY (processing, tool calls, planning)
 * - RESUME when session status is GENERATING (outputting tokens)
 *
 * Status is driven by session.status SSE events from OpenCode server.
 */
public class PauseController {
    private volatile SessionStatus currentStatus = SessionStatus.DISCONNECTED;
    private volatile boolean userTyping = false;
    private volatile boolean enabled = true;
    private volatile boolean gameReady = false;
    private volatile long gameReadyTime = 0;

    // Wait 3 seconds after joining before enabling pause
    private static final long STARTUP_GRACE_PERIOD_MS = 3000;

    /**
     * Called every client tick to update pause state
     */
    public void tick() {
        // No longer using delta timeout - we rely on session.status events from OpenCode
        // OpenCode will tell us when it's idle vs busy
    }

    /**
     * Determines if the game should currently be paused.
     * Called from the IntegratedServerMixin.
     */
    public boolean shouldGameBePaused() {
        if (!enabled) return false;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return false;

        // Only pause in singleplayer
        if (!client.isIntegratedServerRunning()) return false;

        // Don't pause until player is fully in the game
        if (client.player == null) return false;

        // Track when game becomes ready
        if (!gameReady) {
            gameReady = true;
            gameReadyTime = System.currentTimeMillis();
            OpenCodeMod.LOGGER.info("Game ready, pause will activate in {}ms", STARTUP_GRACE_PERIOD_MS);
        }

        // Grace period after joining - don't pause yet
        if (System.currentTimeMillis() - gameReadyTime < STARTUP_GRACE_PERIOD_MS) {
            return false;
        }

        // Pause if user is typing a prompt
        if (userTyping) return true;

        // Pause based on session status
        return currentStatus.shouldPause();
    }

    /**
     * Called when leaving a world to reset the ready state
     */
    public void onWorldUnload() {
        gameReady = false;
        gameReadyTime = 0;
    }

    /**
     * Called when a delta (token) is received from the LLM.
     * Transitions to GENERATING state to indicate active output.
     */
    public void onDeltaReceived() {
        if (currentStatus != SessionStatus.GENERATING) {
            setStatus(SessionStatus.GENERATING);
        }
    }

    /**
     * Sets the current session status
     */
    public void setStatus(SessionStatus status) {
        if (this.currentStatus != status) {
            SessionStatus oldStatus = this.currentStatus;
            this.currentStatus = status;
            OpenCodeMod.LOGGER.debug("Pause controller status: {} -> {}", oldStatus, status);
        }
    }

    /**
     * Sets whether the user is currently typing a prompt
     */
    public void setUserTyping(boolean typing) {
        this.userTyping = typing;
    }

    /**
     * Returns true if the user is currently typing
     */
    public boolean isUserTyping() {
        return userTyping;
    }

    /**
     * Gets the current session status
     */
    public SessionStatus getStatus() {
        return currentStatus;
    }

    /**
     * Enables or disables the pause controller
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns true if the pause controller is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Gets a human-readable status string
     */
    public String getStatusText() {
        if (!enabled) return "Disabled";
        if (userTyping) return "Typing (Paused)";

        return switch (currentStatus) {
            case DISCONNECTED -> "Disconnected";
            case IDLE -> "Idle (Paused)";
            case BUSY -> "Processing...";
            case GENERATING -> "Generating...";
            case RETRY -> "Retrying...";
        };
    }
}
