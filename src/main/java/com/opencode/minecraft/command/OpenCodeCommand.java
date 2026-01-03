package com.opencode.minecraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.opencode.minecraft.OpenCodeMod;
import com.opencode.minecraft.client.OpenCodeClient;
import com.opencode.minecraft.client.session.SessionInfo;
import com.opencode.minecraft.game.MessageRenderer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles all /oc commands for interacting with OpenCode.
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
        dispatcher.register(
            ClientCommandManager.literal("oc")
                // /oc help
                .then(ClientCommandManager.literal("help")
                    .executes(OpenCodeCommand::executeHelp))

                // /oc status
                .then(ClientCommandManager.literal("status")
                    .executes(OpenCodeCommand::executeStatus))

                // /oc cancel
                .then(ClientCommandManager.literal("cancel")
                    .executes(OpenCodeCommand::executeCancel))

                // /oc pause
                .then(ClientCommandManager.literal("pause")
                    .executes(OpenCodeCommand::executePause))

                // /oc session ...
                .then(ClientCommandManager.literal("session")
                    // /oc session new
                    .then(ClientCommandManager.literal("new")
                        .executes(OpenCodeCommand::executeSessionNew))
                    // /oc session list
                    .then(ClientCommandManager.literal("list")
                        .executes(OpenCodeCommand::executeSessionList))
                    // /oc session use <id>
                    .then(ClientCommandManager.literal("use")
                        .then(ClientCommandManager.argument("sessionId", StringArgumentType.string())
                            .executes(OpenCodeCommand::executeSessionUse))))

                // /oc config ...
                .then(ClientCommandManager.literal("config")
                    // /oc config url <url>
                    .then(ClientCommandManager.literal("url")
                        .then(ClientCommandManager.argument("url", StringArgumentType.string())
                            .executes(OpenCodeCommand::executeConfigUrl)))
                    // /oc config dir <path>
                    .then(ClientCommandManager.literal("dir")
                        .then(ClientCommandManager.argument("path", StringArgumentType.greedyString())
                            .executes(OpenCodeCommand::executeConfigDir))))

                // /oc <prompt> - default: send prompt
                .then(ClientCommandManager.argument("prompt", StringArgumentType.greedyString())
                    .executes(OpenCodeCommand::executePrompt))

                // /oc - show help
                .executes(OpenCodeCommand::executeHelp)
        );
    }

    private static int executeHelp(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();

        source.sendFeedback(Text.literal("=== OpenCode Commands ===").formatted(Formatting.AQUA, Formatting.BOLD));
        source.sendFeedback(Text.literal("/oc <prompt>").formatted(Formatting.GREEN)
                .append(Text.literal(" - Send a prompt").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/oc status").formatted(Formatting.GREEN)
                .append(Text.literal(" - Show status").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/oc session new").formatted(Formatting.GREEN)
                .append(Text.literal(" - Create new session").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/oc session list").formatted(Formatting.GREEN)
                .append(Text.literal(" - List sessions").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/oc session use <#>").formatted(Formatting.GREEN)
                .append(Text.literal(" - Switch session by number").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/oc cancel").formatted(Formatting.GREEN)
                .append(Text.literal(" - Cancel generation").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/oc pause").formatted(Formatting.GREEN)
                .append(Text.literal(" - Toggle pause control").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/oc help").formatted(Formatting.GREEN)
                .append(Text.literal(" - Show this help").formatted(Formatting.GRAY)));

        return 1;
    }

    private static int executeStatus(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        OpenCodeClient client = OpenCodeMod.getClient();

        source.sendFeedback(Text.literal("=== OpenCode Status ===").formatted(Formatting.AQUA, Formatting.BOLD));

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
                    .append(Text.literal("None (use /oc session new)").formatted(Formatting.YELLOW)));
        }

        // Pause status
        String pauseStatus = OpenCodeMod.getPauseController().getStatusText();
        source.sendFeedback(Text.literal("Status: ").formatted(Formatting.GRAY)
                .append(Text.literal(pauseStatus).formatted(Formatting.GOLD)));

        return 1;
    }

    private static int executePrompt(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        OpenCodeClient client = OpenCodeMod.getClient();
        String prompt = StringArgumentType.getString(context, "prompt");

        if (!client.isReady()) {
            source.sendError(Text.literal("Not connected to OpenCode server"));
            return 0;
        }

        if (client.getCurrentSession() == null) {
            source.sendError(Text.literal("No active session. Use /oc session new"));
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

    private static int executeCancel(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        OpenCodeClient client = OpenCodeMod.getClient();

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

    private static int executePause(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        var pauseController = OpenCodeMod.getPauseController();

        boolean newState = !pauseController.isEnabled();
        pauseController.setEnabled(newState);

        source.sendFeedback(Text.literal("Pause control: ")
                .append(Text.literal(newState ? "Enabled" : "Disabled")
                        .formatted(newState ? Formatting.GREEN : Formatting.RED)));

        return 1;
    }

    private static int executeSessionNew(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        OpenCodeClient client = OpenCodeMod.getClient();

        if (!client.isReady()) {
            source.sendError(Text.literal("Not connected to OpenCode server"));
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

    private static int executeSessionList(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        OpenCodeClient client = OpenCodeMod.getClient();

        if (!client.isReady()) {
            source.sendError(Text.literal("Not connected to OpenCode server"));
            return 0;
        }

        client.listSessions()
                .thenAccept(sessions -> {
                    if (sessions.isEmpty()) {
                        cachedSessions.clear();
                        source.sendFeedback(Text.literal("No sessions found").formatted(Formatting.YELLOW));
                        return;
                    }

                    // Cache sessions for use with /oc session use <number>
                    cachedSessions = new ArrayList<>(sessions);

                    source.sendFeedback(Text.literal("=== Sessions ===").formatted(Formatting.AQUA, Formatting.BOLD));
                    source.sendFeedback(Text.literal("Use /oc session use <number> to switch").formatted(Formatting.GRAY));

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

    private static int executeSessionUse(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        OpenCodeClient client = OpenCodeMod.getClient();
        String sessionIdOrNumber = StringArgumentType.getString(context, "sessionId");

        if (!client.isReady()) {
            source.sendError(Text.literal("Not connected to OpenCode server"));
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

    private static int executeConfigUrl(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        String url = StringArgumentType.getString(context, "url");

        OpenCodeMod.getConfigManager().setServerUrl(url);
        source.sendFeedback(Text.literal("Server URL set to: ")
                .append(Text.literal(url).formatted(Formatting.GREEN)));
        source.sendFeedback(Text.literal("Restart the game to apply changes").formatted(Formatting.YELLOW));

        return 1;
    }

    private static int executeConfigDir(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        String path = StringArgumentType.getString(context, "path");

        OpenCodeMod.getConfigManager().setWorkingDirectory(path);
        source.sendFeedback(Text.literal("Working directory set to: ")
                .append(Text.literal(path).formatted(Formatting.GREEN)));
        source.sendFeedback(Text.literal("Restart the game to apply changes").formatted(Formatting.YELLOW));

        return 1;
    }
}
