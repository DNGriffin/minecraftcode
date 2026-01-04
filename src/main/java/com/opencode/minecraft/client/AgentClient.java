package com.opencode.minecraft.client;

import com.opencode.minecraft.client.session.SessionInfo;
import com.opencode.minecraft.client.session.SessionStatus;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Common interface for AI backends (OpenCode, Codex, etc.).
 */
public interface AgentClient {
    CompletableFuture<SessionInfo> createSession();

    CompletableFuture<List<SessionInfo>> listSessions();

    CompletableFuture<SessionInfo> useSession(String sessionId);

    CompletableFuture<Void> sendPrompt(String text);

    CompletableFuture<Void> cancel();

    SessionInfo getCurrentSession();

    SessionStatus getStatus();

    boolean isReady();

    void tick();

    void shutdown();
}
