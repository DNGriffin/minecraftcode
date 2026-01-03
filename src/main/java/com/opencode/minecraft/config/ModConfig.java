package com.opencode.minecraft.config;

/**
 * Configuration for the OpenCode mod.
 */
public class ModConfig {
    /**
     * OpenCode server URL
     */
    public String serverUrl = "http://localhost:4096";

    /**
     * Working directory for OpenCode operations
     */
    public String workingDirectory = System.getProperty("user.home");

    /**
     * Last used session ID (for resuming)
     */
    public String lastSessionId = null;

    /**
     * Whether to automatically reconnect on connection loss
     */
    public boolean autoReconnect = true;

    /**
     * Reconnection interval in milliseconds
     */
    public int reconnectIntervalMs = 5000;

    /**
     * Whether to show status in action bar
     */
    public boolean showStatusBar = true;
}
