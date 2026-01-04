package com.opencode.minecraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.opencode.minecraft.OpenCodeMod;
import com.opencode.minecraft.client.AgentClient;
import com.opencode.minecraft.client.session.SessionInfo;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles all /oc (alias /codex) commands for interacting with OpenCode/Codex.
 *
 * Commands:
 * - /oc                 - Show help
 * - /oc help            - Show help
 * - /oc <prompt>        - Send a prompt to OpenCode
 * - /oc status          - Show connection and session status
 * - /oc session new     - Create a new session
 * - /oc session list    - List available sessions
 * - /oc session use <id> - Switch to an existing session
 * - /oc cancel          - Cancel current generation
 * - /oc config url <url> - Set server URL
 * - /oc config dir <path> - Set working directory
 * - /oc pause           - Toggle pause controller
 */
public class OpenCodeCommand {

    // Cache of sessions from last list command, indexed by number (1-based)
    private static List<SessionInfo> cachedSessions = new ArrayList<>();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(buildRootCommand("oc", false));
        dispatcher.register(buildRootCommand("codex", true));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> buildRootCommand(
            String name, boolean preferCodex) {
        return ClientCommandManager.literal(name)
            // /<cmd> help
            .then(ClientCommandManager.literal("help")
                .executes(context -> executeHelp(context, preferCodex)))

            // /<cmd> status
            .then(ClientCommandManager.literal("status")
                .executes(context -> executeStatus(context, preferCodex)))

            // /<cmd> cancel
            .then(ClientCommandManager.literal("cancel")
                .executes(context -> executeCancel(context, preferCodex)))

            // /<cmd> pause
            .then(ClientCommandManager.literal("pause")
                .executes(context -> executePause(context, preferCodex)))

            // /<cmd> session ...
            .then(ClientCommandManager.literal("session")
                // /<cmd> session new
                .then(ClientCommandManager.literal("new")
                    .executes(context -> executeSessionNew(context, preferCodex)))
                // /<cmd> session list
                .then(ClientCommandManager.literal("list")
                    .executes(context -> executeSessionList(context, preferCodex)))
                // /<cmd> session use <id>
                .then(ClientCommandManager.literal("use")
                    .then(ClientCommandManager.argument("sessionId", StringArgumentType.string())
                        .executes(context -> executeSessionUse(context, preferCodex)))))

            // /<cmd> config ...
            .then(ClientCommandManager.literal("config")
                // /<cmd> config url <url>
                .then(ClientCommandManager.literal("url")
                    .then(ClientCommandManager.argument("url", StringArgumentType.string())
                        .executes(context -> executeConfigUrl(context, preferCodex))))
                // /<cmd> config backend <opencode|codex>
                .then(ClientCommandManager.literal("backend")
                    .then(ClientCommandManager.argument("backend", StringArgumentType.word())
                        .executes(context -> executeConfigBackend(context, preferCodex))))
                // /<cmd> config codex <path>
                .then(ClientCommandManager.literal("codex")
                    .then(ClientCommandManager.argument("path", StringArgumentType.greedyString())
                        .executes(context -> executeConfigCodexPath(context, preferCodex))))
                // /<cmd> config approve <true|false>
                .then(ClientCommandManager.literal("approve")
                    .then(ClientCommandManager.argument("autoApprove", StringArgumentType.word())
                        .executes(context -> executeConfigAutoApprove(context, preferCodex))))
                // /<cmd> config dir <path>
                .then(ClientCommandManager.literal("dir")
                    .then(ClientCommandManager.argument("path", StringArgumentType.greedyString())
                        .executes(context -> executeConfigDir(context, preferCodex)))))

            // /<cmd> <prompt> - default: send prompt
            .then(ClientCommandManager.argument("prompt", StringArgumentType.greedyString())
                .executes(context -> executePrompt(context, preferCodex)))

            // /<cmd> - show help
            .executes(context -> executeHelp(context, preferCodex));
    }

    private static void ensurePreferredBackend(CommandContext<FabricClientCommandSource> context, boolean preferCodex) {
        if (!preferCodex) {
            return;
        }
        boolean switched = OpenCodeMod.ensureBackend("codex");
        if (switched) {
            context.getSource().sendFeedback(Text.literal("Switched backend to Codex")
                    .formatted(Formatting.YELLOW));
        }
    }

    private static int executeHelp(CommandContext<FabricClientCommandSource> context, boolean preferCodex) {
        ensurePreferredBackend(context, preferCodex);
        FabricClientCommandSource source = context.getSource();
        String agentName = OpenCodeMod.getAgentName();

        source.sendFeedback(Text.literal("=== " + agentName + " Commands ===").formatted(Formatting.AQUA, Formatting.BOLD));
        source.sendFeedback(Text.literal("/oc (/codex) <prompt>").formatted(Formatting.GREEN)
                .append(Text.literal(" - Send a prompt").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/oc (/codex) status").formatted(Formatting.GREEN)
                .append(Text.literal(" - Show status").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/oc (/codex) session new").formatted(Formatting.GREEN)
                .append(Text.literal(" - Create new session").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/oc (/codex) session list").formatted(Formatting.GREEN)
                .append(Text.literal(" - List sessions").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/oc (/codex) session use <#>").formatted(Formatting.GREEN)
                .append(Text.literal(" - Switch session by number").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/oc (/codex) cancel").formatted(Formatting.GREEN)
                .append(Text.literal(" - Cancel generation").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/oc (/codex) pause").formatted(Formatting.GREEN)
                .append(Text.literal(" - Toggle pause control").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/oc (/codex) config backend <opencode|codex>").formatted(Formatting.GREEN)
                .append(Text.literal(" - Set backend").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/oc (/codex) config codex <path>").formatted(Formatting.GREEN)
                .append(Text.literal(" - Set Codex binary path").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/oc (/codex) config approve <true|false>").formatted(Formatting.GREEN)
                .append(Text.literal(" - Auto-approve Codex requests").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/oc (/codex) help").formatted(Formatting.GREEN)
                .append(Text.literal(" - Show this help").formatted(Formatting.GRAY)));

        return 1;
    }

    private static int executeStatus(CommandContext<FabricClientCommandSource> context, boolean preferCodex) {
        ensurePreferredBackend(context, preferCodex);
        FabricClientCommandSource source = context.getSource();
        AgentClient client = OpenCodeMod.getClient();
        String agentName = OpenCodeMod.getAgentName();

        source.sendFeedback(Text.literal("=== " + agentName + " Status ===").formatted(Formatting.AQUA, Formatting.BOLD));

        // Connection status
        boolean connected = client.isReady();
        source.sendFeedback(Text.literal("Connection: ").formatted(Formatting.GRAY)
                .append(Text.literal(connected ? "Connected" : "Disconnected")
                        .formatted(connected ? Formatting.GREEN : Formatting.RED)));

        // Session status
        SessionInfo session = client.getCurrentSession();
        if (session != null) {
            source.sendFeedback(Text.literal("Session: ").formatted(Formatting.GRAY)
                    .append(Text.literal(session.getId()).formatted(Formatting.YELLOW)));
            source.sendFeedback(Text.literal("Title: ").formatted(Formatting.GRAY)
                    .append(Text.literal(session.getTitle()).formatted(Formatting.WHITE)));
        } else {
            source.sendFeedback(Text.literal("Session: ").formatted(Formatting.GRAY)
                    .append(Text.literal("None (use /oc or /codex session new)").formatted(Formatting.YELLOW)));
        }

        // Pause status
        String pauseStatus = OpenCodeMod.getPauseController().getStatusText();
        source.sendFeedback(Text.literal("Status: ").formatted(Formatting.GRAY)
                .append(Text.literal(pauseStatus).formatted(Formatting.GOLD)));

        return 1;
    }

    private static int executePrompt(CommandContext<FabricClientCommandSource> context, boolean preferCodex) {
        ensurePreferredBackend(context, preferCodex);
        FabricClientCommandSource source = context.getSource();
        AgentClient client = OpenCodeMod.getClient();
        String prompt = StringArgumentType.getString(context, "prompt");
        String agentName = OpenCodeMod.getAgentName();

        if (!client.isReady()) {
            source.sendError(Text.literal("Not connected to " + agentName));
            return 0;
        }

        if (client.getCurrentSession() == null) {
            source.sendError(Text.literal("No active session. Use /oc or /codex session new"));
            return 0;
        }

        // Mark as typing to pause the game while submitting
        OpenCodeMod.getPauseController().setUserTyping(true);

        client.sendPrompt(prompt)
                .exceptionally(e -> {
                    OpenCodeMod.LOGGER.error("Failed to send prompt", e);
                    source.sendError(Text.literal("Failed: " + e.getMessage()));
                    OpenCodeMod.getPauseController().setUserTyping(false);
                    return null;
                });

        return 1;
    }

    private static int executeCancel(CommandContext<FabricClientCommandSource> context, boolean preferCodex) {
        ensurePreferredBackend(context, preferCodex);
        FabricClientCommandSource source = context.getSource();
        AgentClient client = OpenCodeMod.getClient();

        client.cancel()
                .thenRun(() -> {
                    source.sendFeedback(Text.literal("Cancelled").formatted(Formatting.YELLOW));
                })
                .exceptionally(e -> {
                    source.sendError(Text.literal("Failed to cancel: " + e.getMessage()));
                    return null;
                });

        return 1;
    }

    private static int executePause(CommandContext<FabricClientCommandSource> context, boolean preferCodex) {
        ensurePreferredBackend(context, preferCodex);
        FabricClientCommandSource source = context.getSource();
        var pauseController = OpenCodeMod.getPauseController();

        boolean newState = !pauseController.isEnabled();
        pauseController.setEnabled(newState);

        source.sendFeedback(Text.literal("Pause control: ")
                .append(Text.literal(newState ? "Enabled" : "Disabled")
                        .formatted(newState ? Formatting.GREEN : Formatting.RED)));

        return 1;
    }

    private static int executeSessionNew(CommandContext<FabricClientCommandSource> context, boolean preferCodex) {
        ensurePreferredBackend(context, preferCodex);
        FabricClientCommandSource source = context.getSource();
        AgentClient client = OpenCodeMod.getClient();
        String agentName = OpenCodeMod.getAgentName();

        if (!client.isReady()) {
            source.sendError(Text.literal("Not connected to " + agentName));
            return 0;
        }

        source.sendFeedback(Text.literal("Creating new session...").formatted(Formatting.GRAY));

        client.createSession()
                .thenAccept(session -> {
                    source.sendFeedback(Text.literal("Created session: ")
                            .append(Text.literal(session.getId()).formatted(Formatting.GREEN)));
                })
                .exceptionally(e -> {
                    source.sendError(Text.literal("Failed: " + e.getMessage()));
                    return null;
                });

        return 1;
    }

    private static int executeSessionList(CommandContext<FabricClientCommandSource> context, boolean preferCodex) {
        ensurePreferredBackend(context, preferCodex);
        FabricClientCommandSource source = context.getSource();
        AgentClient client = OpenCodeMod.getClient();
        String agentName = OpenCodeMod.getAgentName();

        if (!client.isReady()) {
            source.sendError(Text.literal("Not connected to " + agentName));
            return 0;
        }

        client.listSessions()
                .thenAccept(sessions -> {
                    if (sessions.isEmpty()) {
                        cachedSessions.clear();
                        source.sendFeedback(Text.literal("No sessions found").formatted(Formatting.YELLOW));
                        return;
                    }

                    // Cache sessions for use with /oc or /codex session use <number>
                    cachedSessions = new ArrayList<>(sessions);

                    source.sendFeedback(Text.literal("=== Sessions ===").formatted(Formatting.AQUA, Formatting.BOLD));
                    source.sendFeedback(Text.literal("Use /oc or /codex session use <number> to switch")
                            .formatted(Formatting.GRAY));

                    for (int i = 0; i < sessions.size(); i++) {
                        SessionInfo session = sessions.get(i);
                        String current = client.getCurrentSession() != null &&
                                client.getCurrentSession().getId().equals(session.getId()) ? " (current)" : "";
                        source.sendFeedback(Text.literal((i + 1) + ". ").formatted(Formatting.GREEN)
                                .append(Text.literal(session.getTitle() + current).formatted(Formatting.WHITE)));
                    }
                })
                .exceptionally(e -> {
                    source.sendError(Text.literal("Failed: " + e.getMessage()));
                    return null;
                });

        return 1;
    }

    private static int executeSessionUse(CommandContext<FabricClientCommandSource> context, boolean preferCodex) {
        ensurePreferredBackend(context, preferCodex);
        FabricClientCommandSource source = context.getSource();
        AgentClient client = OpenCodeMod.getClient();
        String agentName = OpenCodeMod.getAgentName();
        String sessionIdOrNumber = StringArgumentType.getString(context, "sessionId");

        if (!client.isReady()) {
            source.sendError(Text.literal("Not connected to " + agentName));
            return 0;
        }

        // Check if input is a number (reference to cached session list)
        // If it's a valid number in range, use cached session; otherwise treat as session ID
        String sessionId = sessionIdOrNumber;
        try {
            int index = Integer.parseInt(sessionIdOrNumber);
            if (!cachedSessions.isEmpty() && index >= 1 && index <= cachedSessions.size()) {
                sessionId = cachedSessions.get(index - 1).getId();
            }
            // If cache is empty or out of range, fall through and use as-is (let server validate)
        } catch (NumberFormatException e) {
            // Not a number, treat as session ID
        }

        final String finalSessionId = sessionId;
        client.useSession(finalSessionId)
                .thenAccept(session -> {
                    source.sendFeedback(Text.literal("Switched to session: ")
                            .append(Text.literal(session.getTitle()).formatted(Formatting.GREEN)));
                })
                .exceptionally(e -> {
                    source.sendError(Text.literal("Failed: " + e.getMessage()));
                    return null;
                });

        return 1;
    }

    private static int executeConfigUrl(CommandContext<FabricClientCommandSource> context, boolean preferCodex) {
        FabricClientCommandSource source = context.getSource();
        String url = StringArgumentType.getString(context, "url");

        OpenCodeMod.getConfigManager().setServerUrl(url);
        source.sendFeedback(Text.literal("Server URL set to: ")
                .append(Text.literal(url).formatted(Formatting.GREEN)));
        source.sendFeedback(Text.literal("Restart the game to apply changes").formatted(Formatting.YELLOW));

        return 1;
    }

    private static int executeConfigBackend(CommandContext<FabricClientCommandSource> context, boolean preferCodex) {
        FabricClientCommandSource source = context.getSource();
        String backend = StringArgumentType.getString(context, "backend");
        String normalized = backend.toLowerCase();

        if (!"opencode".equals(normalized) && !"codex".equals(normalized)) {
            source.sendError(Text.literal("Backend must be opencode or codex"));
            return 0;
        }

        boolean switched = OpenCodeMod.ensureBackend(normalized);
        source.sendFeedback(Text.literal("Backend set to: ")
                .append(Text.literal(normalized).formatted(Formatting.GREEN)));
        if (switched) {
            source.sendFeedback(Text.literal("Backend switched immediately").formatted(Formatting.YELLOW));
        } else {
            source.sendFeedback(Text.literal("Backend already active").formatted(Formatting.GRAY));
        }

        return 1;
    }

    private static int executeConfigCodexPath(CommandContext<FabricClientCommandSource> context, boolean preferCodex) {
        FabricClientCommandSource source = context.getSource();
        String path = StringArgumentType.getString(context, "path");

        OpenCodeMod.getConfigManager().setCodexPath(path);
        source.sendFeedback(Text.literal("Codex path set to: ")
                .append(Text.literal(path).formatted(Formatting.GREEN)));
        source.sendFeedback(Text.literal("Restart the game to apply changes").formatted(Formatting.YELLOW));

        return 1;
    }

    private static int executeConfigAutoApprove(CommandContext<FabricClientCommandSource> context, boolean preferCodex) {
        FabricClientCommandSource source = context.getSource();
        String value = StringArgumentType.getString(context, "autoApprove");
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            source.sendError(Text.literal("Value must be true or false"));
            return 0;
        }
        boolean enabled = Boolean.parseBoolean(value);

        OpenCodeMod.getConfigManager().setCodexAutoApprove(enabled);
        source.sendFeedback(Text.literal("Codex auto-approve: ")
                .append(Text.literal(enabled ? "Enabled" : "Disabled")
                        .formatted(enabled ? Formatting.GREEN : Formatting.RED)));
        source.sendFeedback(Text.literal("Restart the game to apply changes").formatted(Formatting.YELLOW));

        return 1;
    }

    private static int executeConfigDir(CommandContext<FabricClientCommandSource> context, boolean preferCodex) {
        FabricClientCommandSource source = context.getSource();
        String path = StringArgumentType.getString(context, "path");

        OpenCodeMod.getConfigManager().setWorkingDirectory(path);
        source.sendFeedback(Text.literal("Working directory set to: ")
                .append(Text.literal(path).formatted(Formatting.GREEN)));
        source.sendFeedback(Text.literal("Restart the game to apply changes").formatted(Formatting.YELLOW));

        return 1;
    }
}
