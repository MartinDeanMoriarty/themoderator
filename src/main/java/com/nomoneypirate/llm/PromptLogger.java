package com.nomoneypirate.llm;

import static com.nomoneypirate.Themoderator.LOGGER;
import com.nomoneypirate.config.ConfigLoader;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PromptLogger {
    public static void logPrompt(LlmClient.ModerationType type, String fullPrompt, String model) {
        if (!type.loggingEnabled) return;

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String filename = type.logFilenamePrefix + (type == LlmClient.ModerationType.SUMMARY ? timestamp.replace(":", "-") + ".log" : ".log");
        int estimatedTokens = (fullPrompt.length()) / 4;

        String sb = "[" + timestamp + "] Context:\n" +
                "Model: " + model + "\n" +
                "Estimated Tokens: " + estimatedTokens + " / " + ConfigLoader.config.tokenLimit + "\n" +
                "Full Prompt:\n---\n" + fullPrompt + "\n---\n";
        logToFile(filename, sb);
    }

    public static void logToFile(String filename, String content) {
        Path logDir = FabricLoader.getInstance().getGameDir().resolve("logs");
        Path logFile = logDir.resolve(filename);
        try {
            Files.createDirectories(logDir);
            Files.writeString(logFile, content + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            if (ConfigLoader.config.modLogging) LOGGER.warn("Logfile save error!");
        }
    }
}

