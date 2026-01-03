package com.opencode.minecraft.client.http;

import com.google.gson.JsonObject;

/**
 * Represents a Server-Sent Event from OpenCode.
 */
public class SseEvent {
    private final String type;
    private final JsonObject properties;
    private final String directory;

    public SseEvent(String type, JsonObject properties, String directory) {
        this.type = type;
        this.properties = properties;
        this.directory = directory;
    }

    public String getType() {
        return type;
    }

    public JsonObject getProperties() {
        return properties;
    }

    public String getDirectory() {
        return directory;
    }

    /**
     * Checks if this is a message part update with a delta (token)
     */
    public boolean hasDelta() {
        if (properties == null) return false;
        return properties.has("delta") && !properties.get("delta").isJsonNull();
    }

    /**
     * Gets the delta text if present
     */
    public String getDelta() {
        if (!hasDelta()) return null;
        return properties.get("delta").getAsString();
    }

    /**
     * Gets the session status type if this is a status event
     */
    public String getStatusType() {
        if (properties == null || !properties.has("status")) return null;
        JsonObject status = properties.getAsJsonObject("status");
        if (status.has("type")) {
            return status.get("type").getAsString();
        }
        return null;
    }

    /**
     * Gets the part object from message.part.updated events
     */
    public JsonObject getPart() {
        if (properties == null || !properties.has("part")) return null;
        return properties.getAsJsonObject("part");
    }

    /**
     * Gets the part type (text, tool, reasoning, file, step-start, etc.)
     */
    public String getPartType() {
        JsonObject part = getPart();
        if (part == null || !part.has("type")) return null;
        return part.get("type").getAsString();
    }

    /**
     * Gets the tool name if this is a tool part
     */
    public String getToolName() {
        JsonObject part = getPart();
        if (part == null || !part.has("tool")) return null;
        return part.get("tool").getAsString();
    }

    /**
     * Gets the tool state (pending, running, completed, error)
     */
    public String getToolState() {
        JsonObject part = getPart();
        if (part == null || !part.has("state")) return null;
        JsonObject state = part.getAsJsonObject("state");
        if (state.has("status")) {
            return state.get("status").getAsString();
        }
        return null;
    }

    /**
     * Gets file path from file parts
     */
    public String getFilePath() {
        JsonObject part = getPart();
        if (part == null || !part.has("file")) return null;
        return part.get("file").getAsString();
    }

    /**
     * Gets text content from text parts
     */
    public String getTextContent() {
        JsonObject part = getPart();
        if (part == null || !part.has("text")) return null;
        return part.get("text").getAsString();
    }

    /**
     * Gets the step title from step-start parts
     */
    public String getStepTitle() {
        JsonObject part = getPart();
        if (part == null || !part.has("title")) return null;
        return part.get("title").getAsString();
    }

    @Override
    public String toString() {
        return String.format("SseEvent[type=%s]", type);
    }
}
