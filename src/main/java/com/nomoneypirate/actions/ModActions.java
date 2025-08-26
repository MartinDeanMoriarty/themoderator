package com.nomoneypirate.actions;

import com.nomoneypirate.config.ConfigLoader;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;
import java.util.UUID;

public class ModActions {

    public static String whereIs(ServerWorld world, String name, UUID currentMobUuid) {
        // Player
        if (name.isEmpty() && currentMobUuid == null) {
            return ConfigLoader.lang.feedback_02;
        }
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(name);
        if (player != null) {
            BlockPos pos = player.getBlockPos();
            return String.format(ConfigLoader.lang.feedback_13, name, pos.getX(), pos.getY(), pos.getZ());
        }

        // Moderator mob
        Entity entity = Objects.requireNonNull(world.getServer().getWorld(world.getRegistryKey())).getEntity(currentMobUuid);
        if (entity != null) {
            BlockPos pos = entity.getBlockPos();
            return String.format(ConfigLoader.lang.feedback_14, pos.getX(), pos.getY(), pos.getZ());
        }

        return String.format(ConfigLoader.lang.feedback_07.formatted(name));
    }
}
