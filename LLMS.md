# OpenCode Minecraft - LLM Context Document

This document provides context for LLMs working with the OpenCode Minecraft mod codebase.

## Project Summary

OpenCode Minecraft is a Fabric mod for Minecraft 1.21.4 that integrates with [OpenCode](https://github.com/anthropics/opencode), an agentic coding tool. The mod's unique feature is a **pause mechanic**: the game freezes when the AI is idle (waiting for input) and resumes when the AI is actively working. This lets players code without their Minecraft character dying.

## Tech Stack

- **Language**: Java 21
- **Build**: Gradle with Fabric Loom
- **Mod Loader**: Fabric (client-side only)
- **Minecraft**: 1.21.4
- **HTTP Client**: `java.net.http.HttpClient` (no external dependencies)
- **JSON**: Minecraft's bundled Gson

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     Minecraft Client                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │   Mixins    │  │  Commands   │  │    Game Layer       │  │
│  │ (hooks)     │  │  (/oc ...)  │  │ (PauseController,   │  │
│  │             │  │             │  │  MessageRenderer)   │  │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘  │
│         │                │                     │             │
│         └────────────────┼─────────────────────┘             │
│                          │                                   │
│                   ┌──────▼──────┐                            │
│                   │ OpenCode    │                            │
│                   │ Client      │                            │
│                   └──────┬──────┘                            │
└──────────────────────────┼──────────────────────────────────┘
                           │ HTTP + SSE
                           ▼
                    ┌──────────────┐
                    │  OpenCode    │
                    │  Server      │
                    │ (localhost:  │
                    │   4096)      │
                    └──────────────┘
```

## Key Files

| File | Purpose |
|------|---------|
| `OpenCodeMod.java` | Mod entry point, initializes all components |
| `client/OpenCodeClient.java` | Coordinates HTTP client, session manager, event handling |
| `client/http/OpenCodeHttpClient.java` | REST API client, SSE subscription |
| `client/http/SseEvent.java` | SSE event data model with helper methods |
| `client/session/SessionManager.java` | Session lifecycle, state machine |
| `client/session/SessionStatus.java` | Enum: DISCONNECTED, IDLE, BUSY, GENERATING, RETRY |
| `game/PauseController.java` | **CRITICAL**: Determines when game should pause |
| `game/MessageRenderer.java` | Renders AI responses to Minecraft chat |
| `game/PauseOverlay.java` | Dark overlay shown when paused |
| `command/OpenCodeCommand.java` | All `/oc` command handlers |
| `config/ModConfig.java` | Configuration data class |
| `config/ConfigManager.java` | Loads/saves config JSON |
| `mixin/IntegratedServerMixin.java` | Cancels server ticks when paused |
| `mixin/KeyboardInputMixin.java` | Blocks player input when paused |

## OpenCode API Integration

### REST Endpoints Used

```
GET  /global/health          → Health check
GET  /global/event           → SSE event stream
GET  /session                → List all sessions
POST /session                → Create new session
GET  /session/{id}           → Get session by ID
POST /session/{id}/message   → Send prompt (body: { parts: [{ type: "text", text: "..." }] })
POST /session/{id}/abort     → Cancel current generation
```

### SSE Event Stream

Subscribe to `/global/event` for real-time updates. Event format:
```json
{
  "directory": "/path/to/project",
  "payload": {
    "type": "event.type.here",
    "properties": { ... }
  }
}
```

Key event types:
- `session.status` - Contains `properties.status.type` = "idle" | "busy"
- `message.part.updated` - Contains `properties.delta` for token streaming
- `message.created` - New message started
- `server.heartbeat` - Keep-alive (ignore)

### Pause Logic (Critical)

The pause mechanic is controlled by `PauseController.shouldGameBePaused()`:

```java
public boolean shouldGameBePaused() {
    if (!enabled) return false;
    if (client.world == null) return false;
    if (!client.isIntegratedServerRunning()) return false;  // Singleplayer only
    if (client.player == null) return false;

    // Grace period after joining
    if (System.currentTimeMillis() - gameReadyTime < 3000) return false;

    // Pause if user is typing
    if (userTyping) return true;

    // Pause based on session status
    return currentStatus.shouldPause();  // IDLE and DISCONNECTED return true
}
```

Status transitions:
- `DISCONNECTED` → `IDLE` (on connect)
- `IDLE` → `BUSY` (on prompt sent)
- `BUSY` → `GENERATING` (on first delta received)
- `GENERATING` → `IDLE` (on session.status = idle event)
- Any → `DISCONNECTED` (on connection lost)

## Commands

| Command | Handler Method | Description |
|---------|----------------|-------------|
| `/oc <prompt>` | `executePrompt` | Send prompt to current session |
| `/oc status` | `executeStatus` | Show connection/session info |
| `/oc help` | `executeHelp` | Show help text |
| `/oc session new` | `executeSessionNew` | Create new session |
| `/oc session list` | `executeSessionList` | List sessions (cached by number) |
| `/oc session use <id>` | `executeSessionUse` | Switch session by number or ID |
| `/oc cancel` | `executeCancel` | Abort current generation |
| `/oc pause` | `executePause` | Toggle pause control |
| `/oc config url <url>` | `executeConfigUrl` | Set server URL |
| `/oc config dir <path>` | `executeConfigDir` | Set working directory |

## Configuration

Stored in `.minecraft/config/opencode.json`:

```java
public class ModConfig {
    public String serverUrl = "http://localhost:4096";
    public String workingDirectory = System.getProperty("user.home");
    public String lastSessionId = null;
    public boolean autoReconnect = true;
    public int reconnectIntervalMs = 5000;
    public boolean showStatusBar = true;
}
```

## Mixins

| Mixin | Target | Injection Point | Purpose |
|-------|--------|-----------------|---------|
| `IntegratedServerMixin` | `IntegratedServer.tick()` | HEAD, cancellable | Stops server ticks when paused |
| `KeyboardInputMixin` | `KeyboardInput.tick()` | TAIL | Zeros movement input when paused |
| `InGameHudMixin` | `InGameHud.render()` | TAIL | Renders pause overlay |
| `MinecraftClientMixin` | `MinecraftClient` | Various | Client lifecycle hooks |

All injected methods/fields use `opencode$` prefix for namespacing.

## Threading Model

- **Main Thread**: All Minecraft state access, rendering, command execution
- **Background Thread**: HTTP requests, SSE stream reading
- **Bridge**: Use `MinecraftClient.getInstance().execute(() -> { ... })` to dispatch to main thread

## Common Modifications

### Adding a New Command
1. Add literal/argument in `OpenCodeCommand.register()`
2. Create `executeXxx()` handler method
3. Update help text in `executeHelp()`

### Changing Pause Behavior
1. Modify `SessionStatus.shouldPause()` for status-based changes
2. Modify `PauseController.shouldGameBePaused()` for other conditions

### Adding New SSE Event Handling
1. Add helper methods to `SseEvent.java` if needed
2. Add case to `OpenCodeClient.handleEvent()` switch statement

### Adding New HTTP Endpoint
1. Add method to `OpenCodeHttpClient.java`
2. Call from `SessionManager` or `OpenCodeClient` as appropriate

## Build Commands

```bash
./gradlew build          # Build JAR
./gradlew runClient      # Run Minecraft with mod
./gradlew genSources     # Generate Minecraft sources for IDE
./gradlew clean build    # Clean rebuild (fixes stale class issues)
```

Output JAR: `build/libs/opencode-minecraft-1.0.0.jar`
