package com.nomoneypirate;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nomoneypirate.llm.LlmClient;
import com.nomoneypirate.llm.ModerationDecision;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WhitelistEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.server.BannedPlayerEntry;
import com.mojang.authlib.GameProfile;

public class Themoderator implements ModInitializer {
	public static final String MOD_ID = "themoderator";
    private static UUID currentMobId = null;
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

            String welcomeText = ConfigLoader.config.welcomeText;
            // Asynchronous to the LLM
            LlmClient.moderateAsync(playerName, welcomeText).thenAccept(decision -> {
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

            case WHISPER, WARN, KICK, BAN -> {
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
                        LOGGER.info("[themoderator] {}", feedback);
                    }

                    case KICK -> {
                        player.networkHandler.disconnect(
                                Text.literal("The Moderator: "
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
                        LOGGER.info("[themoderator] {}", feedback);
                    }

                    case BAN -> {
                        if (!ConfigLoader.config.allowBanCommand) {
                            String feedback = "The BAN command is not available.";
                            LlmClient.sendFeedbackAsync(feedback)
                                    .thenAccept(dec -> applyDecision(server, dec));
                            return;
                        }
                        player.networkHandler.disconnect(
                                Text.literal("The Moderator: "
                                                + decision.value2()
                                )
                        );
                        if (ConfigLoader.config.useWhitelist) {
                            // Remove Player of the whitelist
                            WhitelistEntry entry = server.getPlayerManager().getWhitelist().get(player.getGameProfile());
                            if (entry != null) {
                                server.getPlayerManager().getWhitelist().remove(player.getGameProfile());
                                server.getPlayerManager().reloadWhitelist();
                            }
                        }
                        if (ConfigLoader.config.useBanlist) {
                            GameProfile profile = player.getGameProfile();
                            Date now = new Date();
                            String reason = decision.value2();
                            String source = "[themoderator]";
                            Date expiry = null; // null = permanent

                            BannedPlayerEntry entry = new BannedPlayerEntry(
                                    profile,
                                    now,
                                    source,
                                    expiry,
                                    reason
                            );
                            server.getPlayerManager().getUserBanList().add(entry);
                            try {
                                server.getPlayerManager().getUserBanList().save();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        String feedback = "Banned "
                                + decision.value()
                                + " with reason: \""
                                + decision.value2()
                                + "\"";
                        LlmClient.sendFeedbackAsync(feedback)
                                .thenAccept(dec -> applyDecision(server, dec));
                        LOGGER.info("[themoderator] {}", feedback);
                    }
                }
            }

            case IGNORE -> {
                // Do nothing
            }
        }
    }

    // Let's register a command to be able to reload configuration file at runtime
    // Note, we use permission level (2) to make sure only operators can use it
    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->

                dispatcher.register(
                literal("moderatorreload")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> {
                            ConfigLoader.load();
                            context.getSource().sendFeedback(() -> Text.literal("[themoderator] Configuration File Reloaded."), false);
                            return 1;
                        })
                )


        );

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // === Spawn Command ===
            dispatcher.register(
                    literal("spawnavatar")
                            .then(argument("type", StringArgumentType.word())
                                    .then(argument("x", DoubleArgumentType.doubleArg())
                                            .then(argument("y", DoubleArgumentType.doubleArg())
                                                    .then(argument("z", DoubleArgumentType.doubleArg())
                                                            .executes(ctx -> {
                                                                String type = StringArgumentType.getString(ctx, "type")
                                                                        .toUpperCase(Locale.ROOT);
                                                                double x = DoubleArgumentType.getDouble(ctx, "x");
                                                                double y = DoubleArgumentType.getDouble(ctx, "y");
                                                                double z = DoubleArgumentType.getDouble(ctx, "z");

                                                                ServerWorld world = ctx.getSource().getWorld();

                                                                // Remove old avatar
                                                                if (currentMobId != null) {
                                                                    Entity old = world.getEntity(currentMobId);
                                                                    if (old != null) old.discard();
                                                                    currentMobId = null;
                                                                }

                                                                // Mob-Type
                                                                EntityType<?> entityType = switch (type) {
                                                                    case "CHICKEN" -> EntityType.CHICKEN;
                                                                    case "COW" -> EntityType.COW;
                                                                    case "PIG" -> EntityType.PIG;
                                                                    case "HORSE" -> EntityType.HORSE;
                                                                    case "SHEEP" -> EntityType.SHEEP;
                                                                    case "GOAT" -> EntityType.GOAT;
                                                                    case "FROG" -> EntityType.FROG;
                                                                    default -> null;
                                                                };

                                                                if (entityType == null) {
                                                                    ctx.getSource().sendError(Text.literal("Ungültiger Avatar-Typ: " + type));
                                                                    return 0;
                                                                }

                                                                BlockPos pos = BlockPos.ofFloored(x, y, z);

                                                                Entity entity = entityType.create(
                                                                        world,
                                                                        null
                                                                );

                                                                if (entity != null) {
                                                                    entity.setInvulnerable(true);

                                                                    if (entity instanceof LivingEntity living) {
                                                                        //entity.getDataTracker().set(LivingEntity, true);
                                                                    }

                                                                    world.spawnEntity(entity);
                                                                    entity.refreshPositionAndAngles(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
                                                                    currentMobId = entity.getUuid();
                                                                    ctx.getSource().sendFeedback(
                                                                            () -> Text.literal("Moderator-Avatar gespawnt: " + type),
                                                                            false
                                                                    );
                                                                } else {
                                                                    ctx.getSource().sendError(Text.literal("Fehler beim Erstellen des Avatars."));
                                                                }
                                                                return 1;
                                                            })
                                                    )
                                            )
                                    )
                            )
            );

            // === Despawn Command ===
            dispatcher.register(
                    literal("despawnavatar")
                            .executes(ctx -> {
                                ServerWorld world = ctx.getSource().getWorld();
                                if (currentMobId != null) {
                                    Entity e = world.getEntity(currentMobId);
                                    if (e != null) e.discard();
                                    currentMobId = null;
                                    ctx.getSource().sendFeedback(() -> Text.literal("Avatar entfernt."), false);
                                } else {
                                    ctx.getSource().sendError(Text.literal("Kein Avatar vorhanden."));
                                }
                                return 1;
                            })
            );

        });

    }

}