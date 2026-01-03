package com.opencode.minecraft.client.session;

/**
 * Represents the current status of the OpenCode session.
 * Used to determine game pause state.
 */
public enum SessionStatus {
    /**
     * Not connected to OpenCode server
     */
    DISCONNECTED,

    /**
     * Connected but no active processing - LLM is idle
     */
    IDLE,

    /**
     * Processing request but not yet generating tokens
     */
    BUSY,

    /**
     * Actively generating tokens (receiving delta events)
     */
    GENERATING,

    /**
     * Error occurred, retrying
     */
    RETRY;

    /**
     * Returns true if the game should be paused in this status
     */
    public boolean shouldPause() {
        return this == DISCONNECTED || this == IDLE;
    }

    /**
     * Returns true if the LLM is actively working
     */
    public boolean isActive() {
        return this == BUSY || this == GENERATING;
    }
}
