package com.opencode.minecraft.config;

import com.google.gson.JsonObject;

/**
 * Configuration for the OpenCode mod.
 */
public class ModConfig {
    /**
     * OpenCode server URL
     */
    public String serverUrl = "http://localhost:4096";

    /**
     * Backend to use: "opencode" or "codex"
     */
    public String backend = "opencode";

    /**
     * Path to the Codex CLI binary
     */
    public String codexPath = "codex";

    /**
     * Auto-approve Codex tool requests
     */
    public boolean codexAutoApprove = true;

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

    public void applyDefaults(JsonObject raw) {
        if (raw == null || !raw.has("serverUrl") || serverUrl == null || serverUrl.isBlank()) {
            serverUrl = "http://localhost:4096";
        }
        if (raw == null || !raw.has("backend") || backend == null || backend.isBlank()) {
            backend = "opencode";
        }
        if (raw == null || !raw.has("codexPath") || codexPath == null || codexPath.isBlank()) {
            codexPath = "codex";
        }
        if (raw == null || !raw.has("codexAutoApprove")) {
            codexAutoApprove = true;
        }
        if (raw == null || !raw.has("workingDirectory") || workingDirectory == null || workingDirectory.isBlank()) {
            workingDirectory = System.getProperty("user.home");
        }
        if (raw == null || !raw.has("autoReconnect")) {
            autoReconnect = true;
        }
        if (raw == null || !raw.has("reconnectIntervalMs") || reconnectIntervalMs <= 0) {
            reconnectIntervalMs = 5000;
        }
        if (raw == null || !raw.has("showStatusBar")) {
            showStatusBar = true;
        }
    }
}
