package com.nomoneypirate.commands;

import com.nomoneypirate.config.ConfigLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.literal;
import static com.nomoneypirate.Themoderator.LOGGER;

public class ModCommands {

    // Let's register a command to be able to reload configuration file at runtime
    // Note, we use permission level (2) to make sure only operators can use it
    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->

                dispatcher.register(
                        literal("moderatorreload")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> {
                                    ConfigLoader.load();
                                    context.getSource().sendFeedback(() -> Text.literal("Configuration File Reloaded."), false);
                                    LOGGER.info("Configuration File Reloaded.");
                                    return 1;
                                })
                )
        );

    }
}
