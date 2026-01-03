# Contributing to OpenCode Minecraft

Thank you for your interest in contributing! This guide will help you get started.

## Development Setup

### Prerequisites

- **Java 21+** - [Eclipse Temurin](https://adoptium.net/) recommended
- **Git** - For version control
- **IDE** - IntelliJ IDEA recommended (has excellent Minecraft modding support)

### Clone and Build

```bash
git clone https://github.com/anthropics/opencode-minecraft.git
cd opencode-minecraft
./gradlew build
```

### IDE Setup

#### IntelliJ IDEA (Recommended)

1. Open the project folder in IntelliJ
2. Import as Gradle project when prompted
3. Wait for indexing to complete
4. Run `./gradlew genSources` to generate Minecraft sources for navigation

#### VS Code

1. Install the "Extension Pack for Java" extension
2. Open the project folder
3. Run `./gradlew genSources` for source navigation

### Running in Development

```bash
# Launch Minecraft with the mod loaded
./gradlew runClient
```

This launches Minecraft with hot-reload support. The game will connect to `http://localhost:4096` by default.

## Project Structure

```
src/main/java/com/opencode/minecraft/
├── OpenCodeMod.java              # Mod entry point, initialization
├── client/
│   ├── OpenCodeClient.java       # Main coordinator for HTTP and events
│   ├── http/
│   │   ├── OpenCodeHttpClient.java   # REST API client
│   │   └── SseEvent.java             # SSE event data model
│   └── session/
│       ├── SessionManager.java       # Session lifecycle management
│       ├── SessionInfo.java          # Session data model
│       └── SessionStatus.java        # Status enum (IDLE, BUSY, etc.)
├── game/
│   ├── PauseController.java      # Game pause logic (CRITICAL)
│   ├── MessageRenderer.java      # Chat message rendering
│   └── PauseOverlay.java         # Visual overlay when paused
├── command/
│   └── OpenCodeCommand.java      # /oc command handlers
├── config/
│   ├── ModConfig.java            # Configuration data model
│   └── ConfigManager.java        # Config load/save
├── mixin/
│   ├── IntegratedServerMixin.java    # Server tick control
│   ├── KeyboardInputMixin.java       # Input blocking when paused
│   ├── InGameHudMixin.java           # HUD overlay hook
│   └── MinecraftClientMixin.java     # Client lifecycle hooks
└── util/
    └── MarkdownToMinecraft.java  # Markdown to Minecraft formatting
```

## Code Style

### General Guidelines

- Use Java 21 features where appropriate
- Follow existing code patterns in the project
- Keep methods focused and reasonably sized
- Use meaningful variable and method names

### Formatting

- 4-space indentation (no tabs)
- Opening braces on same line
- Max line length: 120 characters
- Use `this.` prefix for instance fields

### Comments

- Use Javadoc for public methods
- Add inline comments for complex logic
- Keep comments up-to-date with code changes

## Working with Mixins

Mixins are how we hook into Minecraft's code. They're powerful but require care.

### Key Principles

1. **Minimize scope** - Only inject what you need
2. **Use unique prefixes** - All injected fields/methods use `opencode$` prefix
3. **Handle failures gracefully** - Minecraft updates can break mixins
4. **Test thoroughly** - Mixins can cause subtle bugs

### Current Mixins

| Mixin | Target | Purpose |
|-------|--------|---------|
| `IntegratedServerMixin` | `IntegratedServer` | Cancels server ticks when paused |
| `KeyboardInputMixin` | `KeyboardInput` | Blocks player input when paused |
| `InGameHudMixin` | `InGameHud` | Renders pause overlay |
| `MinecraftClientMixin` | `MinecraftClient` | Client lifecycle hooks |

### Adding a New Mixin

1. Create the mixin class in `com.opencode.minecraft.mixin`
2. Add it to `opencode.mixins.json`
3. Use `@Inject` with appropriate timing (`HEAD`, `TAIL`, `RETURN`)
4. Prefix all injected names with `opencode$`

Example:
```java
@Mixin(SomeMinecraftClass.class)
public class SomeMinecraftClassMixin {
    @Inject(method = "someMethod", at = @At("HEAD"), cancellable = true)
    private void opencode$onSomeMethod(CallbackInfo ci) {
        if (shouldCancel()) {
            ci.cancel();
        }
    }
}
```

## OpenCode API Integration

The mod communicates with OpenCode via:

### REST Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/global/health` | GET | Health check |
| `/global/event` | GET | SSE event stream |
| `/session` | GET | List sessions |
| `/session` | POST | Create session |
| `/session/{id}` | GET | Get session details |
| `/session/{id}/message` | POST | Send prompt |
| `/session/{id}/abort` | POST | Cancel generation |

### SSE Events

Events arrive at `/global/event`. Key event types:

- `session.status` - Session state changes (idle/busy)
- `message.part.updated` - Content updates (with `delta` for tokens)
- `message.created` - New message started
- `server.heartbeat` - Keep-alive

### Threading

- **Network operations**: Background thread (HttpClient executor)
- **Game state changes**: Main thread via `MinecraftClient.execute()`
- **Always dispatch to main thread** before touching Minecraft state

## Testing

### Manual Testing

1. Run `./gradlew runClient`
2. Create a single player world
3. Start OpenCode: `opencode serve`
4. Test commands: `/oc status`, `/oc session new`, `/oc hello`
5. Verify pause/resume behavior

### What to Test

- [ ] Connection and reconnection
- [ ] Session create/list/switch
- [ ] Prompt sending and response display
- [ ] Pause when idle, resume when generating
- [ ] Cancel with `/oc cancel`
- [ ] Config changes persist

## Submitting Changes

### Before Submitting

1. Run `./gradlew build` - ensure it compiles
2. Test your changes manually in-game
3. Update documentation if needed
4. Keep commits focused and well-described

### Pull Request Process

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes
4. Push to your fork
5. Open a Pull Request with:
   - Clear description of changes
   - Any related issues
   - Testing performed

### PR Guidelines

- Keep PRs focused on one feature/fix
- Include screenshots/GIFs for UI changes
- Respond to review feedback promptly
- Squash commits if requested

## Reporting Issues

When opening an issue, please include:

- Minecraft version
- Mod version
- Steps to reproduce
- Expected vs actual behavior
- Relevant logs (from `.minecraft/logs/latest.log`)

## Questions?

- Open a GitHub issue for bugs or feature requests
- Check existing issues before creating new ones

Thank you for contributing!
