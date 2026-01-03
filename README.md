# OpenCode Minecraft

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-brightgreen.svg)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Mod%20Loader-Fabric-blue.svg)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A Fabric mod that integrates [OpenCode](https://github.com/anthropics/opencode) into Minecraft Java Edition single player, with a unique pause mechanic that freezes the game while the AI is waiting for input and resumes when it's actively working.

## Demo



https://github.com/user-attachments/assets/e3aad745-a03a-4c0f-b83c-ce15f92d66ed



## Features

- **Smart Pause Mechanic** - Game automatically pauses when the AI is idle, letting you focus on coding without your character dying. Resumes instantly when the AI starts working.
- **In-Game Chat Interface** - Send prompts and receive responses directly in Minecraft chat using `/oc` commands.
- **Session Management** - Create, list, and switch between coding sessions without leaving the game.
- **Real-Time Streaming** - See AI responses as they're generated, token by token.
- **Tool Visibility** - Get feedback about what tools the AI is using (reading files, writing code, etc.).
- **Configurable** - Set your working directory, server URL, and other options.
- **Cross-Platform** - Works on macOS, Windows, and Linux.

## Quick Start

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.4
2. Download the mod JAR from [Releases](../../releases) and place it in your `.minecraft/mods` folder
3. Start OpenCode: `opencode`
4. Launch Minecraft and create/join a single player world
5. Use `/oc session list` & `/oc session join #` 
6. Start coding with `/oc <your prompt here>`

## Requirements

- Minecraft 1.21.4
- Fabric Loader 0.16.10+
- Fabric API
- Java 21+
- [OpenCode](https://github.com/anthropics/opencode) running in server mode (`opencode serve`)

## Installation

### Option 1: Download Release (Recommended)

1. Download the latest `opencode-minecraft-x.x.x.jar` from [Releases](../../releases)
2. Place the JAR file in your `.minecraft/mods` folder
3. Ensure [Fabric API](https://modrinth.com/mod/fabric-api) is also installed

### Option 2: Build from Source

```bash
git clone https://github.com/anthropics/opencode-minecraft.git
cd opencode-minecraft
./gradlew build
```

The built JAR will be in `build/libs/opencode-minecraft-1.0.0.jar`

## Commands

| Command | Description |
|---------|-------------|
| `/oc <prompt>` | Send a prompt to OpenCode |
| `/oc status` | Show connection and session status |
| `/oc session new` | Create a new coding session |
| `/oc session list` | List available sessions (numbered) |
| `/oc session use <#>` | Switch to session by number or ID |
| `/oc cancel` | Cancel current generation |
| `/oc pause` | Toggle pause control on/off |
| `/oc config url <url>` | Set server URL |
| `/oc config dir <path>` | Set working directory |
| `/oc help` | Show help |

## How the Pause Mechanic Works

The mod uses Minecraft's integrated server to control game simulation:

| AI State | Game State | What's Happening |
|----------|------------|------------------|
| **Idle** | Paused | AI is waiting for your next prompt |
| **Busy** | Running | AI is processing (reading files, planning, etc.) |
| **Generating** | Running | AI is actively outputting text |
| **Disconnected** | Paused | Not connected to OpenCode server |

When paused:
- World simulation stops (mobs freeze, time stops)
- You cannot move or interact
- A dark overlay shows the current status
- Chat remains functional for commands

A 3-second grace period after joining a world prevents immediate pausing.

## Configuration

Configuration is stored in `.minecraft/config/opencode.json`:

```json
{
  "serverUrl": "http://localhost:4096",
  "workingDirectory": "/path/to/your/project",
  "lastSessionId": null,
  "autoReconnect": true,
  "reconnectIntervalMs": 5000,
  "showStatusBar": true
}
```

| Option | Default | Description |
|--------|---------|-------------|
| `serverUrl` | `http://localhost:4096` | OpenCode server URL |
| `workingDirectory` | User home | Project directory for file operations |
| `lastSessionId` | `null` | Auto-resume last session on connect |
| `autoReconnect` | `true` | Automatically reconnect if disconnected |
| `reconnectIntervalMs` | `5000` | Reconnection attempt interval |
| `showStatusBar` | `true` | Show status in action bar |

## Troubleshooting

### "Not connected to OpenCode server"
- Ensure OpenCode is running: `opencode serve`
- Check if the server is accessible at `http://localhost:4096`
- Verify `serverUrl` in config matches your server

### Game stays paused
- Use `/oc status` to check the current state
- If stuck, use `/oc pause` to toggle pause control off
- Ensure OpenCode server is running and responsive

### No response in chat
- Create a session first: `/oc session new`
- Check `/oc status` for connection issues
- Look at Minecraft logs for errors

### "Connection Lost" when joining world
- Ensure you're using the correct mod version for Minecraft 1.21.4
- Try `./gradlew clean build` if building from source
- Check for conflicting mods

## Development

```bash
# Build the mod
./gradlew build

# Run Minecraft with the mod (for development)
./gradlew runClient
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines.

## How It Works (Technical)

The mod consists of several key components:

- **OpenCodeClient** - Coordinates HTTP communication and event handling
- **SessionManager** - Manages session lifecycle and state
- **PauseController** - Determines when to pause/resume based on AI state
- **Mixins** - Hook into Minecraft's integrated server to control ticks

Communication with OpenCode:
- REST API for session management (`/session/*` endpoints)
- Server-Sent Events (SSE) at `/global/event` for real-time updates
- Pause state is driven by `session.status` events (`idle` vs `busy`)

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for:
- Development setup
- Code style guidelines
- How to submit pull requests
- Mixin best practices

## License

MIT License - see [LICENSE](LICENSE) for details.

## Related Projects

- [OpenCode](https://github.com/anthropics/opencode) - The agentic coding tool this mod integrates with
