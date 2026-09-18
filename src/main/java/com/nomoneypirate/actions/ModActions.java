package com.nomoneypirate.actions;

import com.nomoneypirate.config.ConfigLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.Collections;

public class ModActions {

    public static String whereIs(MinecraftServer server, String name) {
        if (!name.isEmpty()) {
            ServerPlayer player = server.getPlayerList().getPlayer(name);
            if (player != null) {
                BlockPos pos = player.blockPosition();
                ResourceKey<Level> dimensionKey = player.level().dimension();
                String dimensionName = dimensionKey.identifier().getPath();
                return String.format(ConfigLoader.lang.feedback_13, name, dimensionName, pos.getX(), pos.getY(), pos.getZ());
            }
        }
        return ConfigLoader.lang.feedback_07;
    }

    public static String clearInventory(ServerLevel world, String playerName) {
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerName);
        if (player == null) return ConfigLoader.lang.feedback_07.formatted(playerName);
        // Clear inventory but not Armor and off-hand
        Collections.fill(player.getInventory().getNonEquipmentItems(), ItemStack.EMPTY);
        return ConfigLoader.lang.feedback_39.formatted(playerName);
    }

    public static String damagePlayer(ServerLevel world, String playerName, int amount) {
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerName);
        if (player == null) return ConfigLoader.lang.feedback_07.formatted(playerName);
        player.hurtServer(world, world.damageSources().generic(), amount);
        return ConfigLoader.lang.feedback_47.formatted(playerName, amount);
    }

    public static String killPlayer(ServerLevel world, String playerName) {
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerName);
        if (player == null) return ConfigLoader.lang.feedback_07.formatted(playerName);
        player.kill(world);
        return ConfigLoader.lang.feedback_40.formatted(playerName);
    }

    public static String givePlayer(ServerLevel world, String playerName, String itemString, int amount) {
        // Try to make Item identifier
        Identifier itemId = Identifier.tryParse(itemString.contains(":") ? itemString : "minecraft:" + itemString);
        if (itemId == null) {
            return "Wrong Item-Identifier: " + itemString;
        }
        // Get item from Registry
        Item item = BuiltInRegistries.ITEM.getValue(itemId);
        // Get player
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerName);
        if (player == null) return ConfigLoader.lang.feedback_07.formatted(playerName);
        // Build ItemStack
        ItemStack stack = new ItemStack(item, amount);
        // Put item into players inventory
        boolean success = player.getInventory().add(stack);
        // Drop item if player inventory is full
        if (!success) {
            player.spawnAtLocation(world, stack);
        }
        return ConfigLoader.lang.feedback_41.formatted(playerName, item.getName(stack).getString());
    }

    public static String changeWeather(ServerLevel world, String weather) {
        WeatherData weatherData = world.getWeatherData();
        switch (weather.toLowerCase()) {
            case "clear":
                weatherData.setClearWeatherTime(12000); // 10 minutes sun
                weatherData.setRaining(false);
                weatherData.setRainTime(0);
                weatherData.setThundering(false);
                weatherData.setThunderTime(0);
                break;
            case "rain":
                weatherData.setClearWeatherTime(0);
                weatherData.setRaining(true);
                weatherData.setRainTime(12000); // 10 minutes rain
                weatherData.setThundering(false);
                weatherData.setThunderTime(0);
                break;
            case "thunder":
                weatherData.setClearWeatherTime(0);
                weatherData.setRaining(true);
                weatherData.setRainTime(12000); // 10 minutes
                weatherData.setThundering(true);
                weatherData.setThunderTime(12000);
                break;
            default:
            return ConfigLoader.lang.feedback_02;
        }
        return ConfigLoader.lang.feedback_42.formatted(weather);
    }

    public static String changeTime(ServerLevel world, String time) {
        ServerLevelData levelData = (ServerLevelData) world.getLevelData();
        switch (time.toLowerCase()) {
            case "day":
                levelData.setGameTime(1000); // Morning
                break;
            case "noon":
                levelData.setGameTime(6000); // Noon
                break;
            case "evening":
                levelData.setGameTime(12000); // Evening
                break;
            case "night":
                levelData.setGameTime(13000); // Night
                break;
            case "midnight":
                levelData.setGameTime(18000); // Midnight
                break;
            default:
                return ConfigLoader.lang.feedback_02;
        }
        return ConfigLoader.lang.feedback_43.formatted(time);
    }

    public static String teleportPositionPlayer(ServerLevel world, String playerName, double posX, double posZ) {
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerName);
        if (player == null) return ConfigLoader.lang.feedback_07.formatted(playerName);
        // Check for surface
        int surfaceY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) posX, (int) posZ);
        double x = posX + 0.5;
        double y = surfaceY + 1.0;
        double z = posZ + 0.5;
        // Teleport player
        player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
        return ConfigLoader.lang.feedback_35.formatted(playerName);
    }

}
