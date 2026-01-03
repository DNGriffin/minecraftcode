package com.opencode.minecraft.client;

import com.opencode.minecraft.OpenCodeMod;
import com.opencode.minecraft.client.http.OpenCodeHttpClient;
import com.opencode.minecraft.client.http.SseEvent;
import com.opencode.minecraft.client.session.SessionInfo;
import com.opencode.minecraft.client.session.SessionManager;
import com.opencode.minecraft.client.session.SessionStatus;
import com.opencode.minecraft.config.ModConfig;
import com.opencode.minecraft.game.MessageRenderer;
import com.opencode.minecraft.game.PauseController;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main coordinator for OpenCode client functionality.
 * Manages HTTP client, session, and event handling.
 */
public class OpenCodeClient {
    private final OpenCodeHttpClient httpClient;
    private final SessionManager sessionManager;
    private final PauseController pauseController;
    private final MessageRenderer messageRenderer;
    private final ModConfig config;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean initialized = false;

    public OpenCodeClient(ModConfig config, PauseController pauseController) {
        this.config = config;
        this.pauseController = pauseController;
        this.httpClient = new OpenCodeHttpClient(config);
        this.sessionManager = new SessionManager(httpClient);
        this.messageRenderer = new MessageRenderer();

        // Listen for session status changes
        sessionManager.addStatusListener(this::onStatusChange);

        // Set up response handler for streaming responses
        httpClient.setResponseHandler(this::handleResponse);

        // Start initialization
        initialize();
    }

    private void initialize() {
        // Check health and connect
        scheduler.schedule(this::connect, 1, TimeUnit.SECONDS);
    }

    private void connect() {
        httpClient.checkHealth()
                .thenAccept(healthy -> {
                    if (healthy) {
                        OpenCodeMod.LOGGER.info("Connected to OpenCode server");
                        sessionManager.onConnected();

                        // Subscribe to events
                        httpClient.subscribeToEvents(this::handleEvent);

                        // Resume last session if available
                        if (config.lastSessionId != null) {
                            sessionManager.useSession(config.lastSessionId)
                                    .exceptionally(e -> {
                                        OpenCodeMod.LOGGER.debug("Could not resume session: {}", e.getMessage());
                                        return null;
                                    });
                        }

                        initialized = true;
                    } else {
                        scheduleReconnect();
                    }
                })
                .exceptionally(e -> {
                    OpenCodeMod.LOGGER.debug("Connection failed: {}", e.getMessage());
                    scheduleReconnect();
                    return null;
                });
    }

    private void scheduleReconnect() {
        if (config.autoReconnect) {
            scheduler.schedule(this::connect, config.reconnectIntervalMs, TimeUnit.MILLISECONDS);
        }
    }

    private void handleEvent(SseEvent event) {
        // Dispatch to main thread
        MinecraftClient.getInstance().execute(() -> {
            switch (event.getType()) {
                case "session.status" -> {
                    String statusType = event.getStatusType();
                    if ("idle".equals(statusType)) {
                        sessionManager.onSessionIdle();
                        messageRenderer.sendSystemMessage("Ready for input");
                    } else if ("busy".equals(statusType)) {
                        sessionManager.onSessionBusy();
                        messageRenderer.sendSystemMessage("Processing...");
                    }
                }
                case "message.part.updated" -> {
                    handlePartUpdated(event);
                }
                case "message.created" -> {
                    messageRenderer.startNewMessage();
                }
                case "session.error" -> {
                    messageRenderer.sendErrorMessage("Session error occurred");
                }
                case "server.connected" -> {
                    messageRenderer.sendSystemMessage("Connected to OpenCode");
                }
                case "server.heartbeat" -> {
                    // Ignore heartbeats
                }
                default -> {
                    // Silently ignore other events
                }
            }
        });
    }

    private void handlePartUpdated(SseEvent event) {
        String partType = event.getPartType();
        if (partType == null) return;

        switch (partType) {
            case "text" -> {
                // Text output with delta
                if (event.hasDelta()) {
                    sessionManager.onDeltaReceived();
                    pauseController.onDeltaReceived();
                    String delta = event.getDelta();
                    if (delta != null && !delta.isEmpty()) {
                        messageRenderer.appendDelta(delta);
                    }
                }
            }
            case "tool" -> {
                // Tool invocation
                String toolName = event.getToolName();
                String toolState = event.getToolState();
                if (toolName != null && toolState != null) {
                    messageRenderer.sendToolMessage(toolName, toolState);
                }
            }
            case "step-start" -> {
                // Step started
                String title = event.getStepTitle();
                if (title != null) {
                    messageRenderer.sendSystemMessage("Step: " + title);
                }
            }
            case "file" -> {
                // File operation
                String filePath = event.getFilePath();
                if (filePath != null) {
                    // Just show filename, not full path
                    String fileName = filePath.contains("/")
                        ? filePath.substring(filePath.lastIndexOf('/') + 1)
                        : filePath;
                    messageRenderer.sendSystemMessage("File: " + fileName);
                }
            }
            case "reasoning" -> {
                // LLM is thinking - show indicator but not content
                if (event.hasDelta()) {
                    sessionManager.onDeltaReceived();
                    pauseController.onDeltaReceived();
                    // Don't show reasoning content, just indicate thinking
                }
            }
            default -> {
                // Other part types - just ensure we track activity
                if (event.hasDelta()) {
                    sessionManager.onDeltaReceived();
                    pauseController.onDeltaReceived();
                }
            }
        }
    }

    private void handleResponse(String line) {
        // Handle streaming JSON response from prompt
        // The SSE events will handle the actual content
    }

    private void onStatusChange(SessionStatus status) {
        pauseController.setStatus(status);
    }

    /**
     * Creates a new session
     */
    public CompletableFuture<SessionInfo> createSession() {
        return sessionManager.createSession()
                .thenApply(session -> {
                    OpenCodeMod.getConfigManager().setLastSessionId(session.getId());
                    return session;
                });
    }

    /**
     * Lists all sessions
     */
    public CompletableFuture<List<SessionInfo>> listSessions() {
        return sessionManager.listSessions();
    }

    /**
     * Switches to an existing session
     */
    public CompletableFuture<SessionInfo> useSession(String sessionId) {
        return sessionManager.useSession(sessionId)
                .thenApply(session -> {
                    OpenCodeMod.getConfigManager().setLastSessionId(session.getId());
                    return session;
                });
    }

    /**
     * Sends a prompt to the current session via the TUI.
     * Response will come through SSE events.
     */
    public CompletableFuture<Void> sendPrompt(String text) {
        pauseController.setUserTyping(false);
        pauseController.setStatus(SessionStatus.BUSY);
        messageRenderer.addUserMessage(text);

        return sessionManager.sendPrompt(text)
                .thenAccept(response -> {
                    // Prompt was sent to TUI, response will come via SSE
                    MinecraftClient.getInstance().execute(() -> {
                        if (response != null && response.startsWith("Error:")) {
                            messageRenderer.sendErrorMessage(response);
                            pauseController.setStatus(SessionStatus.IDLE);
                        }
                        // Otherwise, wait for SSE events to deliver the response
                    });
                });
    }

    /**
     * Cancels the current generation
     */
    public CompletableFuture<Void> cancel() {
        return sessionManager.cancel();
    }

    /**
     * Gets the current session
     */
    public SessionInfo getCurrentSession() {
        return sessionManager.getCurrentSession();
    }

    /**
     * Gets the current status
     */
    public SessionStatus getStatus() {
        return sessionManager.getStatus();
    }

    /**
     * Returns true if connected and initialized
     */
    public boolean isReady() {
        return initialized && httpClient.isConnected();
    }

    /**
     * Ticks the session manager for status timeout checks
     */
    public void tick() {
        sessionManager.tick();
    }

    /**
     * Shuts down the client
     */
    public void shutdown() {
        scheduler.shutdown();
        httpClient.shutdown();
    }
}
