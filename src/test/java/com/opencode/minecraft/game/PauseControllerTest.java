package com.opencode.minecraft.game;

import com.opencode.minecraft.client.session.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PauseController pause logic.
 */
class PauseControllerTest {

    private PauseController controller;

    @BeforeEach
    void setUp() {
        controller = new PauseController();
    }

    @Test
    void testDefaultEnabledState() {
        // PauseController should be enabled by default
        assertTrue(controller.isEnabled(), "PauseController should be enabled by default");
    }

    @Test
    void testSetEnabled() {
        // Test enabling/disabling
        assertTrue(controller.isEnabled());

        controller.setEnabled(false);
        assertFalse(controller.isEnabled());

        controller.setEnabled(true);
        assertTrue(controller.isEnabled());
    }

    @Test
    void testDefaultStatus() {
        // Default status should be DISCONNECTED
        assertEquals(SessionStatus.DISCONNECTED, controller.getStatus());
    }

    @Test
    void testSetStatus() {
        // Test status changes
        controller.setStatus(SessionStatus.IDLE);
        assertEquals(SessionStatus.IDLE, controller.getStatus());

        controller.setStatus(SessionStatus.BUSY);
        assertEquals(SessionStatus.BUSY, controller.getStatus());

        controller.setStatus(SessionStatus.GENERATING);
        assertEquals(SessionStatus.GENERATING, controller.getStatus());
    }

    @Test
    void testStatusText() {
        // Test status text generation
        controller.setStatus(SessionStatus.DISCONNECTED);
        assertEquals("Disconnected", controller.getStatusText());

        controller.setStatus(SessionStatus.IDLE);
        assertTrue(controller.getStatusText().contains("Idle"));

        controller.setStatus(SessionStatus.BUSY);
        assertEquals("Processing...", controller.getStatusText());

        controller.setStatus(SessionStatus.GENERATING);
        assertEquals("Generating...", controller.getStatusText());
    }

    @Test
    void testStatusTextWhenDisabled() {
        controller.setEnabled(false);
        assertEquals("Disabled", controller.getStatusText());
    }

    @Test
    void testUserTyping() {
        // Test user typing state
        assertFalse(controller.isUserTyping());

        controller.setUserTyping(true);
        assertTrue(controller.isUserTyping());

        controller.setUserTyping(false);
        assertFalse(controller.isUserTyping());
    }

    @Test
    void testOnDeltaReceived() {
        // Test that receiving a delta transitions to GENERATING
        controller.setStatus(SessionStatus.BUSY);
        assertEquals(SessionStatus.BUSY, controller.getStatus());

        controller.onDeltaReceived();
        assertEquals(SessionStatus.GENERATING, controller.getStatus());
    }

    @Test
    void testOnDeltaReceivedAlreadyGenerating() {
        // Test that receiving a delta when already GENERATING doesn't change state
        controller.setStatus(SessionStatus.GENERATING);
        controller.onDeltaReceived();
        assertEquals(SessionStatus.GENERATING, controller.getStatus());
    }

    @Test
    void testMultipleEnableDisableToggles() {
        // Test rapid enable/disable toggles
        for (int i = 0; i < 10; i++) {
            controller.setEnabled(i % 2 == 0);
            assertEquals(i % 2 == 0, controller.isEnabled());
        }
    }

    @Test
    void testStatusTransitions() {
        // Test typical status transition flow
        controller.setStatus(SessionStatus.DISCONNECTED);
        assertEquals(SessionStatus.DISCONNECTED, controller.getStatus());

        // Connect -> IDLE
        controller.setStatus(SessionStatus.IDLE);
        assertEquals(SessionStatus.IDLE, controller.getStatus());

        // Send prompt -> BUSY
        controller.setStatus(SessionStatus.BUSY);
        assertEquals(SessionStatus.BUSY, controller.getStatus());

        // Receive delta -> GENERATING
        controller.onDeltaReceived();
        assertEquals(SessionStatus.GENERATING, controller.getStatus());

        // Complete -> IDLE
        controller.setStatus(SessionStatus.IDLE);
        assertEquals(SessionStatus.IDLE, controller.getStatus());
    }
}
