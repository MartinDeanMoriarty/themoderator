package com.nomoneypirate.commands;

import static com.nomoneypirate.Themoderator.LOGGER;
import com.nomoneypirate.config.ConfigLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.network.chat.Component;
import static net.minecraft.commands.Commands.literal;

public class ModCommands {

    // Let's register a command to be able to reload configuration file at runtime
    // Note, we require the COMMANDS_GAMEMASTER permission tier (the old integer level 2) to make sure only operators can use it
    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->

                dispatcher.register(
                        literal("moderatorreload")
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .executes(context -> {
                                    // Load configuration file
                                    ConfigLoader.loadConfig();
                                    // Load language file
                                    ConfigLoader.loadLang();
                                    context.getSource().sendSuccess(() -> Component.literal("Configuration Files Reloaded."), false);
                                    if (ConfigLoader.config.modLogging) LOGGER.info("Configuration Files Reloaded.");
                                    return 1;
                                })
                )
        );
        // Log this!
        if (ConfigLoader.config.modLogging) LOGGER.info("Commands Initialized.");
    }
}
