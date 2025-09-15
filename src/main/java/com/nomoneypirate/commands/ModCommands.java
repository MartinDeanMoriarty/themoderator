package com.nomoneypirate.commands;

import static com.nomoneypirate.Themoderator.LOGGER;
import com.nomoneypirate.config.ConfigLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.literal;

public class ModCommands {

    // Let's register a command to be able to reload configuration file at runtime
    // Note, we use permission level (2) to make sure only operators can use it
    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->

                dispatcher.register(
                        literal("moderatorreload")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> {
                                    // Load configuration file
                                    ConfigLoader.loadConfig();
                                    // Load language file
                                    ConfigLoader.loadLang();
                                    context.getSource().sendFeedback(() -> Text.literal("Configuration Files Reloaded."), false);
                                    if (ConfigLoader.config.modLogging) LOGGER.info("Configuration Files Reloaded.");
                                    return 1;
                                })
                )
        );
        // Log this!
        if (ConfigLoader.config.modLogging) LOGGER.info("3of6 Commands Initialized.");
    }
}