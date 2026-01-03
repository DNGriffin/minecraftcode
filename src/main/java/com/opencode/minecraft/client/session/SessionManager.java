package com.opencode.minecraft.client.session;

import com.opencode.minecraft.OpenCodeMod;
import com.opencode.minecraft.client.http.OpenCodeHttpClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Manages OpenCode sessions and their lifecycle.
 */
public class SessionManager {
    private final OpenCodeHttpClient httpClient;
    private final List<Consumer<SessionStatus>> statusListeners = new CopyOnWriteArrayList<>();

    private SessionInfo currentSession;
    private SessionStatus status = SessionStatus.DISCONNECTED;

    public SessionManager(OpenCodeHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Creates a new session
     */
    public CompletableFuture<SessionInfo> createSession() {
        return httpClient.createSession()
                .thenApply(session -> {
                    this.currentSession = session;
                    setStatus(SessionStatus.IDLE);
                    OpenCodeMod.LOGGER.info("Created session: {}", session.getId());
                    return session;
                });
    }

    /**
     * Lists all available sessions
     */
    public CompletableFuture<List<SessionInfo>> listSessions() {
        return httpClient.listSessions();
    }

    /**
     * Switches to an existing session
     */
    public CompletableFuture<SessionInfo> useSession(String sessionId) {
        return httpClient.getSession(sessionId)
                .thenApply(session -> {
                    this.currentSession = session;
                    setStatus(SessionStatus.IDLE);
                    OpenCodeMod.LOGGER.info("Switched to session: {}", session.getId());
                    return session;
                });
    }

    /**
     * Sends a prompt to the current session.
     * Status will be updated via SSE events, not when HTTP response completes.
     */
    public CompletableFuture<String> sendPrompt(String text) {
        if (currentSession == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("No active session"));
        }

        setStatus(SessionStatus.BUSY);
        return httpClient.sendPrompt(currentSession.getId(), text);
        // Don't set IDLE here - let SSE session.status events drive state
    }

    /**
     * Cancels the current generation
     */
    public CompletableFuture<Void> cancel() {
        if (currentSession == null) {
            return CompletableFuture.completedFuture(null);
        }

        return httpClient.abortSession(currentSession.getId())
                .thenRun(() -> setStatus(SessionStatus.IDLE));
    }

    /**
     * Called when a delta (token) is received from the LLM
     */
    public void onDeltaReceived() {
        if (status != SessionStatus.GENERATING) {
            setStatus(SessionStatus.GENERATING);
        }
    }

    /**
     * Called when the session becomes idle
     */
    public void onSessionIdle() {
        setStatus(SessionStatus.IDLE);
    }

    /**
     * Called when the session is busy but not generating
     */
    public void onSessionBusy() {
        if (status != SessionStatus.GENERATING) {
            setStatus(SessionStatus.BUSY);
        }
    }

    /**
     * Called when connection is established
     */
    public void onConnected() {
        if (status == SessionStatus.DISCONNECTED) {
            setStatus(SessionStatus.IDLE);
        }
    }

    /**
     * Called when connection is lost
     */
    public void onDisconnected() {
        setStatus(SessionStatus.DISCONNECTED);
    }

    /**
     * Called each tick - no longer using delta timeout.
     * Status is now driven entirely by SSE events from OpenCode.
     */
    public void tick() {
        // Status is driven by SSE events, not timeouts
    }

    public SessionInfo getCurrentSession() {
        return currentSession;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void addStatusListener(Consumer<SessionStatus> listener) {
        statusListeners.add(listener);
    }

    public void removeStatusListener(Consumer<SessionStatus> listener) {
        statusListeners.remove(listener);
    }

    private void setStatus(SessionStatus newStatus) {
        if (this.status != newStatus) {
            SessionStatus oldStatus = this.status;
            this.status = newStatus;
            OpenCodeMod.LOGGER.debug("Session status changed: {} -> {}", oldStatus, newStatus);

            for (Consumer<SessionStatus> listener : statusListeners) {
                try {
                    listener.accept(newStatus);
                } catch (Exception e) {
                    OpenCodeMod.LOGGER.error("Error in status listener", e);
                }
            }
        }
    }
}
