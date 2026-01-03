package com.opencode.minecraft.client.session;

import com.google.gson.JsonObject;

/**
 * Represents information about an OpenCode session.
 */
public class SessionInfo {
    private final String id;
    private final String title;
    private final String directory;
    private final long createdAt;
    private final long updatedAt;

    public SessionInfo(String id, String title, String directory, long createdAt, long updatedAt) {
        this.id = id;
        this.title = title;
        this.directory = directory;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static SessionInfo fromJson(JsonObject json) {
        String id = json.get("id").getAsString();
        String title = json.has("title") ? json.get("title").getAsString() : "Untitled";
        String directory = json.has("directory") ? json.get("directory").getAsString() : "";

        long createdAt = 0;
        long updatedAt = 0;
        if (json.has("time")) {
            JsonObject time = json.getAsJsonObject("time");
            createdAt = time.has("created") ? time.get("created").getAsLong() : 0;
            updatedAt = time.has("updated") ? time.get("updated").getAsLong() : 0;
        }

        return new SessionInfo(id, title, directory, createdAt, updatedAt);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDirectory() {
        return directory;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return String.format("Session[%s: %s]", id, title);
    }
}
