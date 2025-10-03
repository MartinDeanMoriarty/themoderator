package com.nomoneypirate.actions;

import com.nomoneypirate.config.ConfigLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.registry.Registries;
import java.util.Collections;

public class ModActions {

    public static String whereIs(MinecraftServer server, String name) {
        if (!name.isEmpty()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(name);
            if (player != null) {
                BlockPos pos = player.getBlockPos();
                RegistryKey<World> dimensionKey = player.getWorld().getRegistryKey();
                String dimensionName = dimensionKey.getValue().getPath();
                return String.format(ConfigLoader.lang.feedback_13, name, dimensionName, pos.getX(), pos.getY(), pos.getZ());
            }
        }
        return ConfigLoader.lang.feedback_07;
    }

    public static String clearInventory(ServerWorld world, String playerName) {
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerName);
        if (player == null) return ConfigLoader.lang.feedback_07.formatted(playerName);
        // Clear inventory but not Armor and off-hand
        Collections.fill(player.getInventory().getMainStacks(), ItemStack.EMPTY);
        return ConfigLoader.lang.feedback_39.formatted(playerName);
    }

    public static String damagePlayer(ServerWorld world, String playerName, int amount) {
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerName);
        if (player == null) return ConfigLoader.lang.feedback_07.formatted(playerName);
        player.damage(world, world.getDamageSources().generic(), amount);
        return ConfigLoader.lang.feedback_47.formatted(playerName, amount);
    }

    public static String killPlayer(ServerWorld world, String playerName) {
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerName);
        if (player == null) return ConfigLoader.lang.feedback_07.formatted(playerName);
        player.kill(world);
        return ConfigLoader.lang.feedback_40.formatted(playerName);
    }

    public static String givePlayer(ServerWorld world, String playerName, String itemString, int amount) {
        // Try to make Item identifier
        Identifier itemId = Identifier.tryParse(itemString.contains(":") ? itemString : "minecraft:" + itemString);
        if (itemId == null) {
            return "Wrong Item-Identifier: " + itemString;
        }
        // Get item from Registry
        Item item = Registries.ITEM.get(itemId);
        // Get player
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerName);
        if (player == null) return ConfigLoader.lang.feedback_07.formatted(playerName);
        // Build ItemStack
        ItemStack stack = new ItemStack(item, amount);
        // Put item into players inventory
        boolean success = player.getInventory().insertStack(stack);
        // Drop item if player inventory is full
        if (!success) {
            player.dropItem(stack, false);
        }
        return ConfigLoader.lang.feedback_41.formatted(playerName, item.getName().getString());
    }

    public static String changeWeather(ServerWorld world, String weather) {
        switch (weather.toLowerCase()) {
            case "clear":
                world.setWeather(12000, 0, false, false); // 10 minutes sun
                break;
            case "rain":
                world.setWeather(0, 12000, false,false); // 10 minutes rain
                break;
            case "thunder":
                world.setWeather(0, 12000, true, true); // 10 minutes
                break;
            default:
            return ConfigLoader.lang.feedback_02;
        }
        return ConfigLoader.lang.feedback_42.formatted(weather);
    }

    public static String changeTime(ServerWorld world, String time) {
        switch (time.toLowerCase()) {
            case "day":
                world.setTimeOfDay(1000); // Morning
                break;
            case "noon":
                world.setTimeOfDay(6000); // Noon
                break;
            case "evening":
                world.setTimeOfDay(12000); // Evening
                break;
            case "night":
                world.setTimeOfDay(13000); // Night
                break;
            case "midnight":
                world.setTimeOfDay(18000); // Midnight
                break;
            default:
                return ConfigLoader.lang.feedback_02;
        }
        return ConfigLoader.lang.feedback_43.formatted(time);
    }

    public static String teleportPositionPlayer(ServerWorld world, String playerName, double posX, double posZ) {
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerName);
        if (player == null) return ConfigLoader.lang.feedback_07.formatted(playerName);
        // Check for surface
        BlockPos surface = world.getTopPosition(
                Heightmap.Type.MOTION_BLOCKING,
                new BlockPos((int) posX, 0, (int) posZ)
        );
        double x = surface.getX() + 0.5;
        double y = surface.getY() + 1.0;
        double z = surface.getZ() + 0.5;
        // Teleport player
        player.networkHandler.requestTeleport(x, y, z, player.getYaw(), player.getPitch());
        return ConfigLoader.lang.feedback_35.formatted(playerName);
    }

}