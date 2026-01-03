package com.opencode.minecraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.opencode.minecraft.OpenCodeMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages loading and saving of mod configuration.
 */
public class ConfigManager {
    private static final String CONFIG_FILE = "opencode.json";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;
    private ModConfig config;

    public ConfigManager() {
        this.configPath = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(CONFIG_FILE);
        this.config = new ModConfig();
    }

    /**
     * Loads configuration from file
     */
    public void load() {
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                config = gson.fromJson(json, ModConfig.class);
                OpenCodeMod.LOGGER.info("Loaded config from {}", configPath);
            } catch (IOException e) {
                OpenCodeMod.LOGGER.error("Failed to load config", e);
                config = new ModConfig();
            }
        } else {
            // Save default config
            save();
        }
    }

    /**
     * Saves configuration to file
     */
    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, gson.toJson(config));
            OpenCodeMod.LOGGER.debug("Saved config to {}", configPath);
        } catch (IOException e) {
            OpenCodeMod.LOGGER.error("Failed to save config", e);
        }
    }

    public ModConfig getConfig() {
        return config;
    }

    public void setServerUrl(String url) {
        config.serverUrl = url;
        save();
    }

    public void setWorkingDirectory(String directory) {
        config.workingDirectory = directory;
        save();
    }

    public void setLastSessionId(String sessionId) {
        config.lastSessionId = sessionId;
        save();
    }
}
