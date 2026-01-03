package com.opencode.minecraft;

import com.opencode.minecraft.client.OpenCodeClient;
import com.opencode.minecraft.command.OpenCodeCommand;
import com.opencode.minecraft.config.ConfigManager;
import com.opencode.minecraft.game.PauseController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenCodeMod implements ClientModInitializer {
    public static final String MOD_ID = "opencode";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static OpenCodeClient client;
    private static PauseController pauseController;
    private static ConfigManager configManager;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing OpenCode Minecraft client");

        // Initialize configuration
        configManager = new ConfigManager();
        configManager.load();

        // Initialize pause controller
        pauseController = new PauseController();

        // Initialize OpenCode client
        client = new OpenCodeClient(configManager.getConfig(), pauseController);

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

    public static OpenCodeClient getClient() {
        return client;
    }

    public static PauseController getPauseController() {
        return pauseController;
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }
}
