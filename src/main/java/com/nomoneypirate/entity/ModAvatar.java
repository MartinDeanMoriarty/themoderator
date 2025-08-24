package com.nomoneypirate.entity;

import com.nomoneypirate.mixin.MobEntityAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import static com.nomoneypirate.Themoderator.LOGGER;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class ModAvatar {

    public static UUID currentMobId = null;
    public static Integer currentMobPosX = null;
    public static Integer currentMobPosZ = null;

    public static boolean spawnModeratorAvatar(ServerWorld world, String type, int x, int z) {
        // Check for ground position
        BlockPos groundPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(x, 0, z));

        // Remove every Mob named "The Moderator"
        for (Entity entity : world.iterateEntities()) {
            if (entity.hasCustomName() &&
                    "The Moderator".equals(Objects.requireNonNull(entity.getCustomName()).getString()) &&
                    !(entity instanceof PlayerEntity)) {

                entity.discard(); // Remove Entity
                LOGGER.info("Removed lingering entity: {}", entity.getType().toString());
            }
        }
        // Remove old Avatar
        if (currentMobId != null) {
            Entity old = world.getEntity(currentMobId);
            if (old != null) old.discard();
            currentMobId = null;
            world.setChunkForced(currentMobPosX, currentMobPosZ, false);
            currentMobPosX = null;
            currentMobPosZ = null;
        }

        // Mob-Typ
        EntityType<?> entityType = switch (type.toUpperCase(Locale.ROOT)) {
            case "CHICKEN" -> EntityType.CHICKEN;
            case "COW" -> EntityType.COW;
            case "PIG" -> EntityType.PIG;
            case "HORSE" -> EntityType.HORSE;
            case "SHEEP" -> EntityType.SHEEP;
            case "GOAT" -> EntityType.GOAT;
            case "FROG" -> EntityType.FROG;
            default -> null;
        };

        if (entityType == null) return false;
        Entity entity = entityType.create(world, null);
        if (entity == null) return false;

        //Spawn
        world.spawnEntity(entity);
        currentMobId = entity.getUuid();
        //Set Invulnerable
        entity.setInvulnerable(true);
        //Set to ground
        entity.refreshPositionAndAngles(groundPos.getX(), groundPos.getY(), groundPos.getZ(), 0, 0);

        //Clear goals
        GoalSelector goals = ((MobEntityAccessor) entity).getGoalSelector();
        goals.getGoals().clear();

        // NoAI
        // entity.getCommandSource(world).getServer().getCommandManager().executeWithPrefix(
        //         entity.getCommandSource(world), "data merge entity " + entity.getUuidAsString() + " {NoAI:1b}"
        // );

        // CustomName
        entity.setCustomName(Text.literal("The Moderator"));
        entity.setCustomNameVisible(true);
        // Chunk loading
        world.setChunkForced(currentMobPosX, currentMobPosZ, true);
        return true;
    }

    public static boolean despawnModeratorAvatar(ServerWorld world) {
        boolean found = false;

        // Remove every Mob named "The Moderator"
        for (Entity entity : world.iterateEntities()) {
            if (entity.hasCustomName() &&
                    "The Moderator".equals(Objects.requireNonNull(entity.getCustomName()).getString()) &&
                    !(entity instanceof PlayerEntity)) {

                entity.discard(); // Remove Entity
                found = true;
                LOGGER.info("Removed lingering entity: {}", entity.getType().toString());
            }
        }

        // Remove registered Mob
        if (currentMobId != null) {
            Entity e = world.getEntity(currentMobId);
            if (e != null) e.discard();
            currentMobId = null;
            world.setChunkForced(currentMobPosX, currentMobPosZ, false);
            currentMobPosX = null;
            currentMobPosZ = null;
            LOGGER.info("Avatar despawned.");
            found = true;
        } else {
            LOGGER.info("No Avatar to despawn.");
        }

        return found;
    }

    public static void makeMobFollowPlayer(ServerWorld world, UUID mobId, String playerName) {
        Entity entity = world.getEntity(mobId);
        if (!(entity instanceof MobEntity mob)) return;

        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerName);
        if (player == null) return;

        // Access GoalSelector via Mixin
        GoalSelector goalSelector = ((MobEntityAccessor) mob).getGoalSelector();
        goalSelector.getGoals().clear(); // Clear all goals

        // Follow-Goal
        goalSelector.add(1, new FollowPlayerGoal(mob, player, 1.0));
    }

}
