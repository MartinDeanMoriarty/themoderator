package com.nomoneypirate;

import com.nomoneypirate.llm.LlmClient;
import com.nomoneypirate.llm.ModerationDecision;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.stream.Collectors;

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
                LOGGER.error("[themoderator] LLM error: {}", ex.getMessage());
                return null;
            });

        });
        //System.out.println("[themoderator] Initialized.");
        LOGGER.info("[themoderator] Initialized.");
    }


    private void applyDecision(MinecraftServer server, ModerationDecision decision) {
        switch (decision.action()) {
            case CHAT -> server.getPlayerManager().broadcast(
                    Text.literal("The Moderator: " + decision.value()),
                    false
            );

            case PLAYERLIST -> {
                Collection<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
                String list = players.stream()
                        .map(p -> p.getName().getString())
                        .collect(Collectors.joining(", "));
                // Feedback ans LLM
                String feedback = "Current players: " + list;
                LlmClient.sendFeedbackAsync(feedback)
                        .thenAccept(dec -> applyDecision(server, dec));
            }

            case WHISPER, WARN, KICK -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(decision.value());
                if (player == null) {
                    String info = "Player '" + decision.value() + "' not found.";
                    LOGGER.info("[themoderator] {}", info);
                    LlmClient.sendFeedbackAsync(info)
                            .thenAccept(dec -> applyDecision(server, dec));
                    return;
                }

                switch (decision.action()) {
                    case WHISPER -> player.sendMessage(
                            Text.literal("The Moderator: " + decision.value2()),
                            false
                    );

                    case WARN -> {
                        player.sendMessage(
                                Text.literal(
                                        "The Moderator: "
                                                + ConfigLoader.config.warnMessage
                                                + " : "
                                                + decision.value2()
                                ),
                                false
                        );
                        String feedback = "Warned "
                                + decision.value()
                                + " with message: \""
                                + decision.value2()
                                + "\"";
                        LlmClient.sendFeedbackAsync(feedback)
                                .thenAccept(dec -> applyDecision(server, dec));
                        LOGGER.info("[themoderator] {} : {}", ConfigLoader.config.warnMessage, decision.value2());
                    }

                    case KICK -> {
                        player.networkHandler.disconnect(
                                Text.literal(
                                        ConfigLoader.config.kickMessage
                                                + " : "
                                                + decision.value2()
                                )
                        );
                        String feedback = "Kicked "
                                + decision.value()
                                + " with reason: \""
                                + decision.value2()
                                + "\"";
                        LlmClient.sendFeedbackAsync(feedback)
                                .thenAccept(dec -> applyDecision(server, dec));
                        LOGGER.info("[themoderator] {} : {}", ConfigLoader.config.kickMessage, decision.value2());
                    }
                }
            }

            case IGNORE -> {
                // nichts weiter tun
            }
        }
    }


    // Let's register a command to be able to reload configuration file at runtime
    // Note, we use permission level (2) to make sure only operators can use it
    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("moderatorreload")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> {
                            ConfigLoader.load();
                            context.getSource().sendFeedback(() -> Text.literal("Moderator-Konfiguration neu geladen."), false);
                            return 1;
                        })
        ));

    }
}