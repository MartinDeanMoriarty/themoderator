package com.nomoneypirate.actions;

import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.entity.*;
import com.nomoneypirate.mixin.MobEntityAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;

import static com.nomoneypirate.entity.ModAvatar.findModeratorWorld;

public class ModActions {

    public static String whereIs(MinecraftServer server, String name, UUID currentMobUuid) {
        // Player
        if (name.isEmpty() && currentMobUuid == null) {
            return ConfigLoader.lang.feedback_02;
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(name);
        if (player != null) {
            BlockPos pos = player.getBlockPos();
            RegistryKey<World> dimensionKey = player.getWorld().getRegistryKey();
            String dimensionName = dimensionKey.getValue().getPath();
            return String.format(ConfigLoader.lang.feedback_13, name, dimensionName, pos.getX(), pos.getY(), pos.getZ());
        }

        // Moderator mob
        ServerWorld world = findModeratorWorld(server);
        if (world == null) {
            return String.format(ConfigLoader.lang.feedback_07.formatted(name));
        }
        Entity entity = Objects.requireNonNull(server.getWorld(world.getRegistryKey())).getEntity(currentMobUuid);
        if (entity != null) {
            BlockPos pos = entity.getBlockPos();
            return String.format(ConfigLoader.lang.feedback_14, world, pos.getX(), pos.getY(), pos.getZ());
        }

        return String.format(ConfigLoader.lang.feedback_07.formatted(name));
    }

    public static String teleportPlayer(ServerWorld world, UUID mobId, String playerName) {
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerName);
        if (player == null) return ConfigLoader.lang.feedback_07.formatted(playerName);

        Entity entity = world.getEntity(mobId);
        if (!(entity instanceof MobEntity mob)) return ConfigLoader.lang.feedback_15.formatted(playerName);

        // Teleport player to mob's position
        BlockPos surface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, mob.getBlockPos());
        double x = surface.getX() + 0.5;
        double y = surface.getY() + 1.0;
        double z = surface.getZ() + 0.5;

        player.networkHandler.requestTeleport(x, y, z, mob.getYaw(), mob.getPitch());

        return ConfigLoader.lang.feedback_33.formatted(playerName);
    }


    public static String teleportAvatar(ServerWorld world, UUID mobId, String playerName) {
        Entity entity = world.getEntity(mobId);
        if (!(entity instanceof MobEntity mob)) return ConfigLoader.lang.feedback_15.formatted(playerName);

        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerName);
        if (player == null) return ConfigLoader.lang.feedback_07.formatted(playerName);

        // Teleport mob to player's position
        mob.refreshPositionAndAngles(
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYaw(),
                player.getPitch()
        );

        return ConfigLoader.lang.feedback_34.formatted(playerName);
    }

    // Set a new Goal
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


    // Set a new Goal
    public static String teleportPositionAvatar(ServerWorld world, UUID mobId, double posX, double posZ) {

        Entity entity = world.getEntity(mobId);
        if (!(entity instanceof MobEntity mob)) return ConfigLoader.lang.feedback_37.formatted(posX, posZ);

        // Check for surface
        BlockPos surface = world.getTopPosition(
                Heightmap.Type.MOTION_BLOCKING,
                new BlockPos((int) posX, 0, (int) posZ)
        );

        double x = surface.getX() + 0.5;
        double y = surface.getY() + 1.0;
        double z = surface.getZ() + 0.5;

        // Teleport mob to position
        mob.refreshPositionAndAngles(
                x,
                y,
                z,
                mob.getYaw(),
                mob.getPitch()
        );

        return ConfigLoader.lang.feedback_36.formatted(posX, posZ);
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

    // Set a new Goal
    public static boolean startLookingAtPlayer(ServerWorld world, UUID mobId, String playerName) {
        Entity entity = world.getEntity(mobId);
        if (!(entity instanceof MobEntity mob)) return false;
        // Access GoalSelector via Mixin
        GoalSelector goalSelector = ((MobEntityAccessor) mob).getGoalSelector();
        // Get Player
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerName);
        if (player != null) {
            // LookAtPlayerGoal
            goalSelector.add(1, new LookAtPlayerGoal(mob, player));

        }
        return  true;
    }

    // Set a new Goal
    public static boolean startLookingAtPos(ServerWorld world, UUID mobId, double posX, double posY, double posZ) {
        Entity entity = world.getEntity(mobId);
        if (!(entity instanceof MobEntity mob)) return false;
        // Access GoalSelector via Mixin
        GoalSelector goalSelector = ((MobEntityAccessor) mob).getGoalSelector();

        // LookAtGoal
        goalSelector.add(1, new LookAtPositionGoal(mob, posX, posY, posZ));

        return  true;
    }

    // Set a new Goal
    public static String startGotoPlayer(ServerWorld world, UUID mobId, String playerName) {
        Entity entity = world.getEntity(mobId);
        // Avatar not found
        if (!(entity instanceof MobEntity mob)) return ConfigLoader.lang.feedback_15;
        // Access GoalSelector via Mixin
        GoalSelector goalSelector = ((MobEntityAccessor) mob).getGoalSelector();
        goalSelector.getGoals().clear(); // Clear all goals
        // Get Player
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerName);
        // Player not found
        if (player == null) return ConfigLoader.lang.feedback_07.formatted(playerName);

        Path path = mob.getNavigation().findPathTo(player, 0);
        if (path != null && path.reachesTarget()) {
            // Goto goal
            goalSelector.add(1, new GotoPlayerGoal(mob, player, 1.0));
            return  ConfigLoader.lang.feedback_24.formatted(playerName);
        } else {
            // No path, unreachable.
            return  ConfigLoader.lang.feedback_27.formatted(playerName);
        }
    }

    // Set a new Goal
    public static String startGotoPosition(ServerWorld world, UUID mobId, double posX, double posZ) {
        Entity entity = world.getEntity(mobId);
        // Avatar not found
        if (!(entity instanceof MobEntity mob)) return ConfigLoader.lang.feedback_15;
        // Access GoalSelector via Mixin
        GoalSelector goalSelector = ((MobEntityAccessor) mob).getGoalSelector();
        goalSelector.getGoals().clear(); // Clear all goals
        // Check path
        BlockPos surfacePos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, new BlockPos((int) posX, 0, (int) posZ));
        Path path = mob.getNavigation().findPathTo(surfacePos, 0);
        if (path != null && path.reachesTarget()) {
            // Goto goal
            goalSelector.add(1, new GotoPositionGoal(mob, posX, posZ, 1.0));
            return  ConfigLoader.lang.feedback_30.formatted(posX, posZ);
        } else {
            // No path, unreachable.
            return  ConfigLoader.lang.feedback_32.formatted(posX, posZ);
        }
    }

    // Set a new Goal
    public static boolean startMoveAround(ServerWorld world, UUID mobId, double blockRadius) {
        Entity entity = world.getEntity(mobId);
        if (!(entity instanceof MobEntity mob)) return false;
        // Access GoalSelector via Mixin
        GoalSelector goalSelector = ((MobEntityAccessor) mob).getGoalSelector();
        goalSelector.getGoals().clear(); // Clear all goals
        // MoveAroundGoal
        goalSelector.add(1, new MoveAroundGoal(mob, blockRadius, 1.0));
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

    public static boolean stopSpecificGoal(ServerWorld world, UUID mobId, String string) {
        Entity entity = world.getEntity(mobId);
        if (!(entity instanceof MobEntity mob)) return false;

        GoalSelector goalSelector = ((MobEntityAccessor) mob).getGoalSelector();

        Iterator<PrioritizedGoal> iterator = goalSelector.getGoals().iterator();
        while (iterator.hasNext()) {
            PrioritizedGoal prioritizedGoal = iterator.next();
            Goal goal = prioritizedGoal.getGoal();

            switch (string) {
                case "FOLLOWING" -> {
                    if (goal instanceof FollowPlayerGoal) {
                        iterator.remove();
                        return true;
                    }

                }
                case "LOOKINGAT" -> {
                    if (goal instanceof LookAtPlayerGoal) {
                        iterator.remove();
                        return true;
                    }

                }
                case "MOVINGAROUND" -> {
                    if (goal instanceof MoveAroundGoal) {
                        iterator.remove();
                        return true;
                    }

                }
            }
        }

        return false; // Goal not found
    }
}