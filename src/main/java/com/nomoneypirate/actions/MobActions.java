package com.nomoneypirate.actions;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;
import java.util.UUID;

public class MobActions {

    public static String whereIs(ServerWorld world, String name, UUID currentMobUuid) {
        // Player
        if (name.isEmpty() && currentMobUuid == null) {
            return "No entity specified.";
        }
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(name);
        if (player != null) {
            BlockPos pos = player.getBlockPos();
            return String.format("Player '%s' is at X: %d, Y: %d, Z: %d", name, pos.getX(), pos.getY(), pos.getZ());
        }

        // Moderator mob
        Entity entity = Objects.requireNonNull(world.getServer().getWorld(world.getRegistryKey())).getEntity(currentMobUuid);
        if (entity != null) {
            BlockPos pos = entity.getBlockPos();
            return String.format("Your '%s' is at X: %d, Y: %d, Z: %d", entity.getName().getString(), pos.getX(), pos.getY(), pos.getZ());
        }
        return "Entity not found.";
    }
}
