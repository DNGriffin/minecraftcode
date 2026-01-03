package com.opencode.minecraft.client.http;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.opencode.minecraft.OpenCodeMod;
import com.opencode.minecraft.client.session.SessionInfo;
import com.opencode.minecraft.config.ModConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

/**
 * HTTP client for communicating with the OpenCode server.
 * Uses Java's built-in HttpClient for REST and SSE.
 */
public class OpenCodeHttpClient {
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String directory;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private volatile boolean connected = false;
    private volatile boolean sseRunning = false;
    private Consumer<SseEvent> eventHandler;
    private Consumer<String> responseHandler;

    public OpenCodeHttpClient(ModConfig config) {
        this.baseUrl = config.serverUrl;
        this.directory = config.workingDirectory;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(executor)
                .build();
    }

    /**
     * Checks if the server is healthy
     */
    public CompletableFuture<Boolean> checkHealth() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/global/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    connected = response.statusCode() == 200;
                    return connected;
                })
                .exceptionally(e -> {
                    connected = false;
                    OpenCodeMod.LOGGER.warn("Health check failed: {}", e.getMessage());
                    return false;
                });
    }

    /**
     * Creates a new session
     */
    public CompletableFuture<SessionInfo> createSession() {
        JsonObject body = new JsonObject();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/session"))
                .header("Content-Type", "application/json")
                .header("x-opencode-directory", directory)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200 && response.statusCode() != 201) {
                        throw new RuntimeException("Failed to create session: " + response.statusCode());
                    }
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    return SessionInfo.fromJson(json);
                });
    }

    /**
     * Lists all sessions
     */
    public CompletableFuture<List<SessionInfo>> listSessions() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/session"))
                .header("x-opencode-directory", directory)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Failed to list sessions: " + response.statusCode());
                    }
                    JsonArray array = JsonParser.parseString(response.body()).getAsJsonArray();
                    List<SessionInfo> sessions = new ArrayList<>();
                    for (int i = 0; i < array.size(); i++) {
                        sessions.add(SessionInfo.fromJson(array.get(i).getAsJsonObject()));
                    }
                    return sessions;
                });
    }

    /**
     * Gets a specific session
     */
    public CompletableFuture<SessionInfo> getSession(String sessionId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/session/" + sessionId))
                .header("x-opencode-directory", directory)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Failed to get session: " + response.statusCode());
                    }
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    return SessionInfo.fromJson(json);
                });
    }

    /**
     * Sends a prompt to a session via the /session/{id}/message endpoint.
     * This is the correct API endpoint for sending messages programmatically.
     */
    public CompletableFuture<String> sendPrompt(String sessionId, String text) {
        // Build the message body with parts array structure
        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", text);

        JsonArray parts = new JsonArray();
        parts.add(textPart);

        JsonObject body = new JsonObject();
        body.add("parts", parts);

        String url = baseUrl + "/session/" + sessionId + "/message";

        OpenCodeMod.LOGGER.info("Sending message to session {}: {}", sessionId, text);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(10))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        OpenCodeMod.LOGGER.error("Failed to send message: {} - {}", response.statusCode(), response.body());
                        throw new RuntimeException("Failed to send message: " + response.statusCode());
                    }
                    OpenCodeMod.LOGGER.info("Message sent successfully to session {}", sessionId);
                    // Response is streamed, SSE events will deliver the actual content
                    return "Message sent";
                })
                .exceptionally(e -> {
                    OpenCodeMod.LOGGER.error("Failed to send message: {}", e.getMessage(), e);
                    return "Error: " + e.getMessage();
                });
    }

    /**
     * Aborts the current session operation
     */
    public CompletableFuture<Void> abortSession(String sessionId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/session/" + sessionId + "/abort"))
                .header("x-opencode-directory", directory)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() != 200 && response.statusCode() != 204) {
                        OpenCodeMod.LOGGER.warn("Abort returned status: {}", response.statusCode());
                    }
                });
    }

    /**
     * Subscribes to the global event stream (SSE)
     */
    public void subscribeToEvents(Consumer<SseEvent> handler) {
        this.eventHandler = handler;

        if (sseRunning) {
            OpenCodeMod.LOGGER.debug("SSE already running");
            return;
        }

        sseRunning = true;
        executor.submit(this::runSseLoop);
    }

    private void runSseLoop() {
        while (sseRunning) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/global/event"))
                        .header("Accept", "text/event-stream")
                        .header("x-opencode-directory", directory)
                        .GET()
                        .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.fromLineSubscriber(
                        new SseLineSubscriber(this::handleSseLine)))
                        .join();

            } catch (Exception e) {
                if (sseRunning) {
                    OpenCodeMod.LOGGER.warn("SSE connection error, reconnecting in 5s: {}", e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private void handleSseLine(String line) {
        if (line == null || line.isEmpty()) return;

        if (line.startsWith("data: ")) {
            String data = line.substring(6);
            try {
                JsonObject json = JsonParser.parseString(data).getAsJsonObject();

                // SSE events have structure: { directory, payload: { type, properties } }
                String dir = json.has("directory") ? json.get("directory").getAsString() : "";

                String type = "unknown";
                JsonObject properties = null;

                if (json.has("payload")) {
                    JsonObject payload = json.getAsJsonObject("payload");
                    type = payload.has("type") ? payload.get("type").getAsString() : "unknown";
                    if (payload.has("properties")) {
                        properties = payload.getAsJsonObject("properties");
                    }
                }

                OpenCodeMod.LOGGER.debug("SSE event received: type={}, hasProps={}", type, properties != null);

                SseEvent event = new SseEvent(type, properties, dir);

                if (eventHandler != null) {
                    eventHandler.accept(event);
                }
            } catch (Exception e) {
                OpenCodeMod.LOGGER.warn("Failed to parse SSE data: {} - raw: {}", e.getMessage(), data);
            }
        }
    }

    public void setResponseHandler(Consumer<String> handler) {
        this.responseHandler = handler;
    }

    public boolean isConnected() {
        return connected;
    }

    public void disconnect() {
        sseRunning = false;
        connected = false;
    }

    public void shutdown() {
        disconnect();
        executor.shutdown();
    }

    /**
     * Simple line subscriber for SSE
     */
    private static class SseLineSubscriber implements Flow.Subscriber<String> {
        private final Consumer<String> lineHandler;
        private Flow.Subscription subscription;

        SseLineSubscriber(Consumer<String> lineHandler) {
            this.lineHandler = lineHandler;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(String item) {
            lineHandler.accept(item);
        }

        @Override
        public void onError(Throwable throwable) {
            OpenCodeMod.LOGGER.debug("SSE stream error: {}", throwable.getMessage());
        }

        @Override
        public void onComplete() {
            OpenCodeMod.LOGGER.debug("SSE stream completed");
        }
    }
}
