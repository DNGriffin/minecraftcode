package com.opencode.minecraft.game;

import com.opencode.minecraft.util.MarkdownToMinecraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Renders OpenCode messages in Minecraft chat.
 */
public class MessageRenderer {
    private StringBuilder currentMessage = new StringBuilder();
    private boolean messageInProgress = false;
    private long lastUpdateTime = 0;

    // Flush accumulated text every 100ms for smoother output
    private static final long FLUSH_INTERVAL_MS = 100;

    /**
     * Starts a new assistant message
     */
    public void startNewMessage() {
        flushCurrentMessage();
        currentMessage = new StringBuilder();
        messageInProgress = true;
    }

    /**
     * Appends a delta (token) to the current message
     */
    public void appendDelta(String delta) {
        currentMessage.append(delta);

        long now = System.currentTimeMillis();
        if (now - lastUpdateTime > FLUSH_INTERVAL_MS) {
            flushCurrentMessage();
            lastUpdateTime = now;
        }
    }

    /**
     * Flushes the current message buffer to chat
     */
    public void flushCurrentMessage() {
        if (currentMessage.length() > 0) {
            String text = currentMessage.toString();
            currentMessage = new StringBuilder();

            // Split into lines and render each
            String[] lines = text.split("\n");
            for (String line : lines) {
                if (!line.isEmpty()) {
                    sendAssistantMessage(line);
                }
            }
        }
        messageInProgress = false;
    }

    /**
     * Adds a user message to chat
     */
    public void addUserMessage(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        MutableText message = Text.literal("")
                .append(Text.literal("[You] ").formatted(Formatting.GREEN))
                .append(Text.literal(text).formatted(Formatting.WHITE));

        client.inGameHud.getChatHud().addMessage(message);
    }

    /**
     * Sends an assistant message line to chat (public)
     */
    public void sendAssistantLine(String text) {
        sendAssistantMessage(text);
    }

    /**
     * Sends an assistant message to chat
     */
    private void sendAssistantMessage(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // Convert markdown to Minecraft formatting
        Text formatted = MarkdownToMinecraft.convert(text);

        MutableText message = Text.literal("")
                .append(Text.literal("[OpenCode] ").formatted(Formatting.AQUA))
                .append(formatted);

        client.inGameHud.getChatHud().addMessage(message);
    }

    /**
     * Sends a system message (status, errors, etc.)
     */
    public void sendSystemMessage(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        MutableText message = Text.literal("")
                .append(Text.literal("[OpenCode] ").formatted(Formatting.GOLD))
                .append(Text.literal(text).formatted(Formatting.YELLOW));

        client.inGameHud.getChatHud().addMessage(message);
    }

    /**
     * Sends an error message
     */
    public void sendErrorMessage(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        MutableText message = Text.literal("")
                .append(Text.literal("[OpenCode Error] ").formatted(Formatting.RED))
                .append(Text.literal(text).formatted(Formatting.RED));

        client.inGameHud.getChatHud().addMessage(message);
    }

    /**
     * Sends a tool execution message
     */
    public void sendToolMessage(String toolName, String status) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Formatting statusColor = switch (status) {
            case "running" -> Formatting.YELLOW;
            case "completed" -> Formatting.GREEN;
            case "failed" -> Formatting.RED;
            default -> Formatting.GRAY;
        };

        MutableText message = Text.literal("")
                .append(Text.literal("[Tool] ").formatted(Formatting.DARK_PURPLE))
                .append(Text.literal(toolName + ": ").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal(status).formatted(statusColor));

        client.inGameHud.getChatHud().addMessage(message);
    }
}
