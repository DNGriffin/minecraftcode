package com.opencode.minecraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConfigManager save/load functionality.
 * Note: These tests are simplified as ConfigManager depends on FabricLoader.
 * In a full test suite, we'd mock FabricLoader or refactor ConfigManager to accept a Path.
 */
class ConfigManagerTest {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Test
    void testConfigSerializationDeserialization() {
        // Test that config can be serialized and deserialized correctly
        ModConfig original = new ModConfig();
        original.pauseEnabled = false;
        original.serverUrl = "http://test:8080";
        original.lastSessionId = "test-session";

        // Serialize
        String json = gson.toJson(original);

        // Deserialize
        ModConfig deserialized = gson.fromJson(json, ModConfig.class);

        // Verify
        assertEquals(original.pauseEnabled, deserialized.pauseEnabled);
        assertEquals(original.serverUrl, deserialized.serverUrl);
        assertEquals(original.lastSessionId, deserialized.lastSessionId);
        assertEquals(original.autoReconnect, deserialized.autoReconnect);
        assertEquals(original.reconnectIntervalMs, deserialized.reconnectIntervalMs);
        assertEquals(original.showStatusBar, deserialized.showStatusBar);
    }

    @Test
    void testBackwardCompatibility(@TempDir Path tempDir) throws IOException {
        // Test that old config without pauseEnabled field works correctly
        Path configFile = tempDir.resolve("opencode.json");

        // Create old config without pauseEnabled field
        String oldConfig = """
                {
                  "serverUrl": "http://localhost:4096",
                  "workingDirectory": "/home/user",
                  "lastSessionId": null,
                  "autoReconnect": true,
                  "reconnectIntervalMs": 5000,
                  "showStatusBar": true
                }
                """;

        Files.writeString(configFile, oldConfig);

        // Load config
        String json = Files.readString(configFile);
        ModConfig config = gson.fromJson(json, ModConfig.class);

        // Verify pauseEnabled defaults to true (field default)
        assertTrue(config.pauseEnabled, "Missing pauseEnabled should default to true");
    }

    @Test
    void testPauseEnabledPersistence(@TempDir Path tempDir) throws IOException {
        // Test that pauseEnabled is properly saved and loaded
        Path configFile = tempDir.resolve("opencode.json");

        // Create config with pauseEnabled = false
        ModConfig config = new ModConfig();
        config.pauseEnabled = false;

        // Save
        Files.writeString(configFile, gson.toJson(config));

        // Load
        String json = Files.readString(configFile);
        ModConfig loaded = gson.fromJson(json, ModConfig.class);

        // Verify
        assertFalse(loaded.pauseEnabled, "pauseEnabled should persist as false");
    }

    @Test
    void testInvalidJsonHandling() {
        // Test that invalid JSON returns default config
        String invalidJson = "{ invalid json }";

        try {
            gson.fromJson(invalidJson, ModConfig.class);
            fail("Should throw exception for invalid JSON");
        } catch (Exception e) {
            // Expected - invalid JSON should throw exception
            // ConfigManager.load() catches this and creates new ModConfig()
        }
    }

    @Test
    void testEmptyJsonHandling() {
        // Test that empty JSON creates config with defaults
        String emptyJson = "{}";
        ModConfig config = gson.fromJson(emptyJson, ModConfig.class);

        // All fields should have their default values
        assertNotNull(config);
        assertTrue(config.pauseEnabled, "pauseEnabled should default to true");
        assertTrue(config.autoReconnect);
        assertTrue(config.showStatusBar);
    }
}
