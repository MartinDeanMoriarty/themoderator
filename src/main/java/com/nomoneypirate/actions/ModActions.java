package com.nomoneypirate.actions;

import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.entity.FollowPlayerGoal;
import com.nomoneypirate.mixin.MobEntityAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
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

    // Set a new Goal
    public static boolean startFollowPlayer(ServerWorld world, UUID mobId, String playerName) {
        Entity entity = world.getEntity(mobId);
        if (!(entity instanceof MobEntity mob)) return false;
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerName);
        if (player == null) return false;
        // Access GoalSelector via Mixin
        GoalSelector goalSelector = ((MobEntityAccessor) mob).getGoalSelector();
        goalSelector.getGoals().clear(); // Clear all goals
        // Follow-Goal
        goalSelector.add(1, new FollowPlayerGoal(mob, player, 1.0));
        return  true;
    }
    // Stop All Goals
    public static boolean stopAllGoals(ServerWorld world, UUID mobId) {
        Entity entity = world.getEntity(mobId);
        if (!(entity instanceof MobEntity mob)) return false;
        // Access GoalSelector via Mixin
        GoalSelector goalSelector = ((MobEntityAccessor) mob).getGoalSelector();
        // Clear all goals
        goalSelector.getGoals().clear();
        return true;
    }
}
