package com.nomoneypirate;

import com.nomoneypirate.llm.LlmClient;
import com.nomoneypirate.llm.ModerationDecision;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.minecraft.server.command.CommandManager.literal;

public class Themoderator implements ModInitializer {
	public static final String MOD_ID = "themoderator";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

        //Load configuration file
        ConfigLoader.load();
        //Register mod commands
        registerCommands();

        // Intercept player join messages (server-side)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {

            // Get player name
            ServerPlayerEntity player = handler.getPlayer();
            String playerName = player.getName().getString();

            String welcomePrompt = ConfigLoader.config.welcomePrompt;

            // Asynchronous to the LLM
            LlmClient.moderateAsync(playerName, welcomePrompt).thenAccept(decision -> {
                // Back to the server thread
                server.execute(() -> applyDecision(server, decision));
            }).exceptionally(ex -> {
                // In case of errors: do not block anything, at most log
                //System.err.println("[themoderator] LLM error: " + ex.getMessage());
                LOGGER.error("[themoderator] Welcoming failed: {}", ex.getMessage());
                return null;
            });
        });

        // Intercept chat messages (server-side)
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            MinecraftServer server = sender.getServer();
            if (server == null) return;

            // Get player name and chat content
            String playerName = sender.getName().getString();
            String content = message.getContent().getString();

            // Asynchronous to the LLM
            LlmClient.moderateAsync(playerName, content).thenAccept(decision -> {
                // Back to the server thread to apply the decision
                server.execute(() -> applyDecision(server, decision));
            }).exceptionally(ex -> {
                // In case of errors: do not block anything, at most log
                //System.err.println("[themoderator] LLM error: " + ex.getMessage());
                LOGGER.error("[themoderator] LLM error: {}", ex.getMessage());
                return null;
            });
        });

        //System.out.println("[themoderator] Initialized.");
        LOGGER.info("[themoderator] Initialized.");
    }

    // Let's register a command to be able to reload configuration file at runtime
    // Note, we use permission level (2) to make sure only operators can use it
    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    literal("moderatorreload")
                            .requires(source -> source.hasPermissionLevel(2))
                            .executes(context -> {
                                ConfigLoader.load();
                                context.getSource().sendFeedback(() -> Text.literal("Moderator-Konfiguration neu geladen."), false);
                                return 1;
                            })
            );
        });
    }

    private void applyDecision(MinecraftServer server, ModerationDecision decision) {

        switch (decision.action()) {
            case CHAT -> {
                // Broadcast to all players
                server.getPlayerManager().broadcast(Text.literal("The Moderator: " + decision.text()), false);
            }
            case WHISPER, WARN, KICK -> {
                // Get player via playerName
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(decision.playerName());
                if (player == null) {
                    LOGGER.info("[themoderator] Player '{}' not found. Ignored decision: '{}'.", decision.playerName(), decision.action());
                    return;
                }

                switch (decision.action()) {
                    // Chat directly with the specific player
                    case WHISPER -> player.sendMessage(Text.literal("The Moderator: " + decision.text()), false);
                    case WARN -> {
                        // Output directly to the specific player only
                        player.sendMessage(Text.literal("The Moderator: " + ConfigLoader.config.warnMessage + " : " + decision.text()), false);
                        LOGGER.info("[themoderator]{} : {}", ConfigLoader.config.warnMessage, decision.text());
                    }
                    case KICK -> {
                        // Disconnect the player
                        player.networkHandler.disconnect(Text.literal(ConfigLoader.config.kickMessage + " : " + decision.text()));
                        LOGGER.info("[themoderator]{} : {}", ConfigLoader.config.kickMessage, decision.text());
                    }
                }
            }
            case IGNORE -> {
                // Do nothing
            }
        }
    }
}