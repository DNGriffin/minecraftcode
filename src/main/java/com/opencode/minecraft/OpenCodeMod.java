package com.opencode.minecraft;

import com.opencode.minecraft.client.AgentClient;
import com.opencode.minecraft.client.CodexClient;
import com.opencode.minecraft.client.OpenCodeClient;
import com.opencode.minecraft.command.OpenCodeCommand;
import com.opencode.minecraft.client.session.SessionStatus;
import com.opencode.minecraft.config.ConfigManager;
import com.opencode.minecraft.config.ModConfig;
import com.opencode.minecraft.game.PauseController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenCodeMod implements ClientModInitializer {
    public static final String MOD_ID = "opencode";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static AgentClient client;
    private static PauseController pauseController;
    private static ConfigManager configManager;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing OpenCode Minecraft client");

        // Initialize configuration
        configManager = new ConfigManager();
        configManager.load();
        ModConfig config = configManager.getConfig();
        LOGGER.info("Using backend: {}", config.backend);

        // Initialize pause controller
        pauseController = new PauseController();

        // Initialize backend client
        if ("codex".equalsIgnoreCase(config.backend)) {
            client = new CodexClient(config, pauseController);
        } else {
            client = new OpenCodeClient(config, pauseController);
        }

        // Register commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            OpenCodeCommand.register(dispatcher);
        });

        // Register tick event for status updates
        ClientTickEvents.END_CLIENT_TICK.register(minecraftClient -> {
            pauseController.tick();
        });

        LOGGER.info("OpenCode Minecraft client initialized");
    }

    public static AgentClient getClient() {
        return client;
    }

    public static PauseController getPauseController() {
        return pauseController;
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }

    public static synchronized boolean ensureBackend(String backend) {
        if (configManager == null) {
            return false;
        }
        ModConfig config = configManager.getConfig();
        if (config == null) {
            return false;
        }
        String normalized = backend.toLowerCase();
        if (config.backend != null && config.backend.equalsIgnoreCase(normalized) && client != null) {
            return false;
        }

        if (client != null) {
            client.shutdown();
        }

        configManager.setBackend(normalized);
        if (pauseController != null) {
            pauseController.setStatus(SessionStatus.DISCONNECTED);
        }

        if ("codex".equalsIgnoreCase(normalized)) {
            client = new CodexClient(config, pauseController);
        } else {
            client = new OpenCodeClient(config, pauseController);
        }

        LOGGER.info("Switched backend to {}", normalized);
        return true;
    }

    public static String getAgentName() {
        ModConfig config = configManager != null ? configManager.getConfig() : new ModConfig();
        if (config != null && "codex".equalsIgnoreCase(config.backend)) {
            return "Codex";
        }
        return "OpenCode";
    }

    public static boolean isCodexBackend() {
        ModConfig config = configManager != null ? configManager.getConfig() : new ModConfig();
        return config != null && "codex".equalsIgnoreCase(config.backend);
    }

    public static String getVersion() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }
}
