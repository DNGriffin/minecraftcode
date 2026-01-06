package com.opencode.minecraft.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ModConfig default values and behavior.
 */
class ModConfigTest {

    @Test
    void testDefaultValues() {
        ModConfig config = new ModConfig();

        // Test default values
        assertEquals("http://localhost:4096", config.serverUrl);
        assertEquals(System.getProperty("user.home"), config.workingDirectory);
        assertNull(config.lastSessionId);
        assertTrue(config.autoReconnect);
        assertEquals(5000, config.reconnectIntervalMs);
        assertTrue(config.showStatusBar);
        assertTrue(config.pauseEnabled, "pauseEnabled should default to true");
    }

    @Test
    void testPauseEnabledCanBeModified() {
        ModConfig config = new ModConfig();

        // Test that pauseEnabled can be changed
        assertTrue(config.pauseEnabled);

        config.pauseEnabled = false;
        assertFalse(config.pauseEnabled);

        config.pauseEnabled = true;
        assertTrue(config.pauseEnabled);
    }

    @Test
    void testServerUrlCanBeModified() {
        ModConfig config = new ModConfig();
        config.serverUrl = "http://example.com:8080";
        assertEquals("http://example.com:8080", config.serverUrl);
    }

    @Test
    void testWorkingDirectoryCanBeModified() {
        ModConfig config = new ModConfig();
        config.workingDirectory = "/custom/path";
        assertEquals("/custom/path", config.workingDirectory);
    }
}
