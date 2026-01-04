package com.opencode.minecraft.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.opencode.minecraft.OpenCodeMod;
import com.opencode.minecraft.client.session.SessionInfo;
import com.opencode.minecraft.client.session.SessionStatus;
import com.opencode.minecraft.config.ModConfig;
import com.opencode.minecraft.game.MessageRenderer;
import com.opencode.minecraft.game.PauseController;
import net.minecraft.client.MinecraftClient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Codex backend implementation using the Codex app-server JSON-RPC protocol.
 * Spawns `codex app-server` and streams notifications over stdout.
 */
public class CodexClient implements AgentClient {
    private final ModConfig config;
    private final PauseController pauseController;
    private final MessageRenderer messageRenderer;
    private final Gson gson = new Gson();
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, CompletableFuture<JsonObject>> pendingRequests = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();
    private final AtomicLong processToken = new AtomicLong();

    private volatile Process process;
    private volatile BufferedWriter stdinWriter;
    private volatile boolean running = false;
    private volatile boolean initialized = false;
    private volatile boolean shuttingDown = false;

    private volatile SessionInfo currentSession;
    private volatile String currentThreadId;
    private volatile String currentTurnId;
    private volatile SessionStatus status = SessionStatus.DISCONNECTED;

    public CodexClient(ModConfig config, PauseController pauseController) {
        this.config = config;
        this.pauseController = pauseController;
        this.messageRenderer = new MessageRenderer();

        startProcess();
    }

    private void startProcess() {
        try {
            ProcessBuilder builder = new ProcessBuilder(config.codexPath, "app-server");
            builder.redirectErrorStream(false);
            process = builder.start();
            stdinWriter = new BufferedWriter(new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8));
            running = true;

            long token = processToken.incrementAndGet();
            ioExecutor.submit(() -> readStdout(process, token));
            ioExecutor.submit(() -> readStderr(process, token));

            sendInitialize()
                    .thenAccept(response -> {
                        initialized = true;
                        setStatus(SessionStatus.IDLE);
                        MinecraftClient.getInstance().execute(() ->
                                messageRenderer.sendSystemMessage("Connected to " + OpenCodeMod.getAgentName()));

                        if (config.lastSessionId != null) {
                            useSession(config.lastSessionId)
                                    .exceptionally(e -> {
                                        OpenCodeMod.LOGGER.debug("Could not resume Codex thread: {}", e.getMessage());
                                        return null;
                                    });
                        }
                    })
                    .exceptionally(e -> {
                        OpenCodeMod.LOGGER.warn("Codex initialization failed: {}", e.getMessage());
                        scheduleReconnect();
                        return null;
                    });
        } catch (IOException e) {
            OpenCodeMod.LOGGER.warn("Failed to start Codex app-server: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (config.autoReconnect) {
            scheduler.schedule(this::restartProcess, config.reconnectIntervalMs, TimeUnit.MILLISECONDS);
        }
    }

    private void restartProcess() {
        stopProcess();
        startProcess();
    }

    private void readStdout(Process process, long token) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                handleLine(line);
            }
        } catch (IOException e) {
            if (running) {
                OpenCodeMod.LOGGER.warn("Codex stdout error: {}", e.getMessage());
            }
        } finally {
            handleDisconnect(token);
        }
    }

    private void readStderr(Process process, long token) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                OpenCodeMod.LOGGER.debug("Codex stderr: {}", line);
            }
        } catch (IOException e) {
            if (running) {
                OpenCodeMod.LOGGER.debug("Codex stderr reader error: {}", e.getMessage());
            }
        }
    }

    private void handleLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return;

        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(trimmed);
        } catch (Exception e) {
            OpenCodeMod.LOGGER.debug("Codex JSON parse failed: {}", e.getMessage());
            return;
        }

        if (!parsed.isJsonObject()) return;
        JsonObject message = parsed.getAsJsonObject();

        if (message.has("id")) {
            if (message.has("method")) {
                handleServerRequest(message);
            } else {
                handleResponse(message);
            }
        } else if (message.has("method")) {
            handleNotification(message);
        }
    }

    private void handleResponse(JsonObject message) {
        String id = asString(message.get("id"));
        if (id == null) return;

        CompletableFuture<JsonObject> future = pendingRequests.remove(id);
        if (future == null) return;

        if (message.has("error")) {
            JsonObject error = message.getAsJsonObject("error");
            String errorMessage = error != null && error.has("message")
                    ? error.get("message").getAsString()
                    : "Unknown error";
            future.completeExceptionally(new RuntimeException(errorMessage));
            return;
        }

        JsonObject result = message.has("result") && message.get("result").isJsonObject()
                ? message.getAsJsonObject("result")
                : new JsonObject();
        future.complete(result);
    }

    private void handleServerRequest(JsonObject message) {
        String method = asString(message.get("method"));
        if (method == null) return;

        switch (method) {
            case "item/commandExecution/requestApproval",
                 "item/fileChange/requestApproval" -> {
                if (!config.codexAutoApprove) {
                    MinecraftClient.getInstance().execute(() ->
                            messageRenderer.sendErrorMessage("Codex approval requested; auto-approve is disabled"));
                }
                JsonObject response = new JsonObject();
                response.add("id", message.get("id"));
                JsonObject result = new JsonObject();
                result.addProperty("decision", config.codexAutoApprove ? "accept" : "decline");
                response.add("result", result);
                sendRawMessage(response);
            }
            default -> OpenCodeMod.LOGGER.debug("Unhandled Codex request: {}", method);
        }
    }

    private void handleNotification(JsonObject message) {
        String method = asString(message.get("method"));
        if (method == null) return;

        JsonObject params = message.has("params") && message.get("params").isJsonObject()
                ? message.getAsJsonObject("params")
                : new JsonObject();

        MinecraftClient.getInstance().execute(() -> handleNotificationOnMainThread(method, params));
    }

    private void handleNotificationOnMainThread(String method, JsonObject params) {
        switch (method) {
            case "thread/started" -> {
                String threadId = getString(params, "threadId");
                if (threadId != null && threadId.equals(currentThreadId)) {
                    setStatus(SessionStatus.IDLE);
                }
            }
            case "turn/started" -> {
                String threadId = getString(params, "threadId");
                if (threadId != null && threadId.equals(currentThreadId)) {
                    JsonObject turn = params.getAsJsonObject("turn");
                    currentTurnId = turn != null ? getString(turn, "id") : null;
                    setStatus(SessionStatus.BUSY);
                }
            }
            case "turn/completed" -> {
                String threadId = getString(params, "threadId");
                if (threadId != null && threadId.equals(currentThreadId)) {
                    messageRenderer.flushCurrentMessage();
                    setStatus(SessionStatus.IDLE);
                    currentTurnId = null;
                    messageRenderer.sendSystemMessage("Ready for input");
                }
            }
            case "item/agentMessage/delta" -> {
                String threadId = getString(params, "threadId");
                if (threadId != null && threadId.equals(currentThreadId)) {
                    String delta = getString(params, "delta");
                    if (delta != null && !delta.isEmpty()) {
                        setStatus(SessionStatus.GENERATING);
                        pauseController.onDeltaReceived();
                        messageRenderer.appendDelta(delta);
                    }
                }
            }
            case "item/started" -> {
                if (isCurrentThread(params)) {
                    JsonObject item = params.getAsJsonObject("item");
                    handleItemStarted(item);
                }
            }
            case "item/completed" -> {
                if (isCurrentThread(params)) {
                    JsonObject item = params.getAsJsonObject("item");
                    handleItemCompleted(item);
                }
            }
            case "item/commandExecution/outputDelta" -> {
                if (isCurrentThread(params)) {
                    String delta = getString(params, "delta");
                    if (delta != null && !delta.isEmpty()) {
                        messageRenderer.sendSystemMessage(delta.trim());
                    }
                }
            }
            case "item/mcpToolCall/progress" -> {
                if (isCurrentThread(params)) {
                    String message = getString(params, "message");
                    if (message != null && !message.isEmpty()) {
                        messageRenderer.sendSystemMessage(message);
                    }
                }
            }
            case "error" -> {
                String error = getString(params, "message");
                if (error != null) {
                    messageRenderer.sendErrorMessage(error);
                }
            }
            default -> {
                // Ignore other notifications.
            }
        }
    }

    private boolean isCurrentThread(JsonObject params) {
        String threadId = getString(params, "threadId");
        return threadId != null && threadId.equals(currentThreadId);
    }

    private void handleItemStarted(JsonObject item) {
        if (item == null) return;
        String type = getString(item, "type");
        if (type == null) return;

        switch (type) {
            case "agentMessage" -> messageRenderer.startNewMessage();
            case "commandExecution" -> {
                String command = getString(item, "command");
                if (command != null && !command.isEmpty()) {
                    messageRenderer.sendSystemMessage("Command: " + command);
                }
                messageRenderer.sendToolMessage("commandExecution", "running");
            }
            case "fileChange" -> messageRenderer.sendToolMessage("fileChange", "running");
            case "mcpToolCall" -> {
                String tool = getString(item, "tool");
                messageRenderer.sendToolMessage(tool != null ? tool : "mcpToolCall", "running");
            }
            case "webSearch" -> {
                String query = getString(item, "query");
                if (query != null && !query.isEmpty()) {
                    messageRenderer.sendSystemMessage("Search: " + query);
                }
            }
            default -> {
                // Unhandled item types are ignored.
            }
        }
    }

    private void handleItemCompleted(JsonObject item) {
        if (item == null) return;
        String type = getString(item, "type");
        if (type == null) return;

        switch (type) {
            case "commandExecution" -> {
                String statusValue = getString(item, "status");
                messageRenderer.sendToolMessage("commandExecution", normalizeStatus(statusValue));
            }
            case "fileChange" -> messageRenderer.sendToolMessage("fileChange", "completed");
            case "mcpToolCall" -> {
                String statusValue = getString(item, "status");
                String tool = getString(item, "tool");
                messageRenderer.sendToolMessage(tool != null ? tool : "mcpToolCall",
                        normalizeStatus(statusValue));
            }
            default -> {
                // No special handling.
            }
        }
    }

    private String normalizeStatus(String status) {
        if (status == null) return "running";
        return switch (status) {
            case "inProgress" -> "running";
            case "completed" -> "completed";
            case "failed", "declined" -> "failed";
            default -> "running";
        };
    }

    private CompletableFuture<JsonObject> sendInitialize() {
        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("name", "opencode-minecraft");
        clientInfo.addProperty("title", "OpenCode Minecraft");
        clientInfo.addProperty("version", OpenCodeMod.getVersion());

        JsonObject params = new JsonObject();
        params.add("clientInfo", clientInfo);
        return sendRequest("initialize", params);
    }

    private CompletableFuture<JsonObject> sendRequest(String method, JsonObject params) {
        String id = UUID.randomUUID().toString();

        JsonObject request = new JsonObject();
        request.addProperty("id", id);
        request.addProperty("method", method);
        if (params != null) {
            request.add("params", params);
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pendingRequests.put(id, future);
        sendRawMessage(request);
        return future;
    }

    private void sendRawMessage(JsonObject message) {
        String payload = gson.toJson(message);
        synchronized (writeLock) {
            if (stdinWriter == null) return;
            try {
                stdinWriter.write(payload);
                stdinWriter.newLine();
                stdinWriter.flush();
            } catch (IOException e) {
                OpenCodeMod.LOGGER.warn("Failed to send Codex message: {}", e.getMessage());
            }
        }
    }

    private void handleDisconnect(long token) {
        if (shuttingDown || token != processToken.get()) return;
        running = false;
        initialized = false;
        setStatus(SessionStatus.DISCONNECTED);
        scheduleReconnect();
    }

    private void setStatus(SessionStatus newStatus) {
        if (status != newStatus) {
            status = newStatus;
            pauseController.setStatus(newStatus);
        }
    }

    private SessionInfo sessionFromThread(JsonObject thread) {
        if (thread == null) {
            return new SessionInfo("unknown", "Untitled", "", 0, 0);
        }
        String id = getString(thread, "id");
        String preview = getString(thread, "preview");
        String cwd = getString(thread, "cwd");
        long createdAt = thread.has("createdAt") ? thread.get("createdAt").getAsLong() : 0L;
        String title = (preview == null || preview.isBlank()) ? "Untitled" : preview;
        return new SessionInfo(id, title, cwd != null ? cwd : "", createdAt, createdAt);
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        return asString(obj.get(key));
    }

    private static String asString(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        return element.getAsString();
    }

    @Override
    public CompletableFuture<SessionInfo> createSession() {
        JsonObject params = new JsonObject();
        if (config.workingDirectory != null && !config.workingDirectory.isBlank()) {
            params.addProperty("cwd", config.workingDirectory);
        }
        return sendRequest("thread/start", params)
                .thenApply(result -> {
                    JsonObject thread = result.getAsJsonObject("thread");
                    SessionInfo session = sessionFromThread(thread);
                    currentSession = session;
                    currentThreadId = session.getId();
                    OpenCodeMod.getConfigManager().setLastSessionId(session.getId());
                    setStatus(SessionStatus.IDLE);
                    return session;
                });
    }

    @Override
    public CompletableFuture<List<SessionInfo>> listSessions() {
        JsonObject params = new JsonObject();
        params.addProperty("limit", 50);
        return sendRequest("thread/list", params)
                .thenApply(result -> {
                    List<SessionInfo> sessions = new ArrayList<>();
                    JsonArray data = result.getAsJsonArray("data");
                    if (data != null) {
                        for (JsonElement element : data) {
                            if (element.isJsonObject()) {
                                sessions.add(sessionFromThread(element.getAsJsonObject()));
                            }
                        }
                    }
                    return sessions;
                });
    }

    @Override
    public CompletableFuture<SessionInfo> useSession(String sessionId) {
        JsonObject params = new JsonObject();
        params.addProperty("threadId", sessionId);
        return sendRequest("thread/resume", params)
                .thenApply(result -> {
                    JsonObject thread = result.getAsJsonObject("thread");
                    SessionInfo session = sessionFromThread(thread);
                    currentSession = session;
                    currentThreadId = session.getId();
                    OpenCodeMod.getConfigManager().setLastSessionId(session.getId());
                    setStatus(SessionStatus.IDLE);
                    return session;
                });
    }

    @Override
    public CompletableFuture<Void> sendPrompt(String text) {
        pauseController.setUserTyping(false);
        messageRenderer.addUserMessage(text);
        setStatus(SessionStatus.BUSY);

        if (currentThreadId == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("No active Codex thread"));
        }

        JsonObject params = new JsonObject();
        params.addProperty("threadId", currentThreadId);
        if (config.workingDirectory != null && !config.workingDirectory.isBlank()) {
            params.addProperty("cwd", config.workingDirectory);
        }
        JsonArray input = new JsonArray();
        JsonObject textInput = new JsonObject();
        textInput.addProperty("type", "text");
        textInput.addProperty("text", text);
        input.add(textInput);
        params.add("input", input);

        return sendRequest("turn/start", params)
                .thenAccept(result -> {
                    JsonObject turn = result.getAsJsonObject("turn");
                    currentTurnId = turn != null ? getString(turn, "id") : null;
                })
                .exceptionally(e -> {
                    messageRenderer.sendErrorMessage("Failed: " + e.getMessage());
                    setStatus(SessionStatus.IDLE);
                    return null;
                });
    }

    @Override
    public CompletableFuture<Void> cancel() {
        if (currentThreadId == null || currentTurnId == null) {
            return CompletableFuture.completedFuture(null);
        }
        JsonObject params = new JsonObject();
        params.addProperty("threadId", currentThreadId);
        params.addProperty("turnId", currentTurnId);
        return sendRequest("turn/interrupt", params)
                .thenAccept(result -> setStatus(SessionStatus.IDLE));
    }

    @Override
    public SessionInfo getCurrentSession() {
        return currentSession;
    }

    @Override
    public SessionStatus getStatus() {
        return status;
    }

    @Override
    public boolean isReady() {
        return running && initialized && process != null && process.isAlive();
    }

    @Override
    public void tick() {
        // Codex status is driven by notifications.
    }

    @Override
    public void shutdown() {
        shuttingDown = true;
        stopProcess();
        ioExecutor.shutdownNow();
        scheduler.shutdownNow();
    }

    private void stopProcess() {
        running = false;
        initialized = false;
        pendingRequests.clear();

        if (stdinWriter != null) {
            try {
                stdinWriter.close();
            } catch (IOException e) {
                OpenCodeMod.LOGGER.debug("Failed to close Codex stdin: {}", e.getMessage());
            }
        }
        stdinWriter = null;

        if (process != null && process.isAlive()) {
            process.destroy();
        }
        process = null;
    }
}
