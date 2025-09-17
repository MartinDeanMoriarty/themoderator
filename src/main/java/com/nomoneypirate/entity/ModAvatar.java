package com.nomoneypirate.entity;

import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.events.ModEvents;
import com.nomoneypirate.mixin.MobEntityAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import java.awt.*;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class ModAvatar {

    public static String currentAvatarType = null;
    public static UUID currentAvatarId = null;
    public static Integer currentAvatarPosX = null;
    public static Integer currentAvatarPosZ = null;
    public static ChunkPos lastChunkPos = null;
    public static World currentAvatarWorld = null;

    // Search for a "lost" Avatar (used at server startup).
    public static boolean searchModeratorAvatar(ServerWorld world) {
        boolean found = false;
        // Search Mob named "The Moderator"
        Entity entity = findModeratorEntity(world);
            if (entity != null) {
                currentAvatarId = entity.getUuid();
                currentAvatarType = entity.getType().toString();
                currentAvatarPosX = entity.getBlockX();
                currentAvatarPosZ = entity.getBlockZ();
                currentAvatarWorld = entity.getWorld();
                // Set Invulnerable
                entity.setInvulnerable(true);
                // Clear goals
                GoalSelector goals = ((MobEntityAccessor) entity).getGoalSelector();
                goals.getGoals().clear();
                // Set chunk loader
                world.setChunkForced(currentAvatarPosX, currentAvatarPosZ, true);
                found = true;
            }

        return found;
    }

    // Get the current Avatar and return type and coordinates
    public static String currentModeratorAvatar() {
        String feedback;
        if (currentAvatarId != null) {

            Identifier dimensionId = currentAvatarWorld.getRegistryKey().getValue();
            String dimensionType = dimensionId.getPath();
            feedback = ConfigLoader.lang.feedback_16.formatted(currentAvatarType, dimensionType, currentAvatarPosX, currentAvatarPosZ);
        } else {
            feedback = ConfigLoader.lang.feedback_15;
        }
        return feedback;
    }

    // Spawn an Avatar with given type and coordinates
    public static String spawnModeratorAvatar(MinecraftServer server, String dim, String type, double x, double z) {
        ServerWorld world = getWorldFromString(server, dim);
        if (world == null) return ConfigLoader.lang.feedback_04;
        // In case there is already an Avatar
        if (currentAvatarId != null) return ConfigLoader.lang.feedback_46;
        // Get ground position
        BlockPos groundPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos((int)x, 0, (int)z));
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
        // Make sure entityType is set
        if (entityType == null) return ConfigLoader.lang.feedback_02;
        // Create entity
        Entity entity = entityType.create(world, null);
        // Make sure entity is set
        if (entity == null) return ConfigLoader.lang.feedback_02;
        // Chunk loading
        world.setChunkForced(currentAvatarPosX, currentAvatarPosZ, true);
        // Spawn the Avatar
        world.spawnEntity(entity);
        currentAvatarId = entity.getUuid();
        currentAvatarType = entity.getType().toString();
        currentAvatarWorld = entity.getWorld();
        // Set Invulnerable
        entity.setInvulnerable(true);
        // Set to ground
        entity.refreshPositionAndAngles(groundPos.getX(), groundPos.getY(), groundPos.getZ(), 0, 0);
        // Clear goals
        GoalSelector goals = ((MobEntityAccessor) entity).getGoalSelector();
        goals.getGoals().clear();
        // NoAI
        // entity.getCommandSource(world).getServer().getCommandManager().executeWithPrefix(
        //         entity.getCommandSource(world), "data merge entity " + entity.getUuidAsString() + " {NoAI:1b}"
        // );
        // CustomName
        entity.setCustomName(Text.literal("The Moderator"));
        entity.setCustomNameVisible(true);
        // PLay a sound
        entity.playSound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 2.0f, 0.7f);
        // Chat Output: Moderator has an Avatar
        if (ModEvents.SERVER != null) ModEvents.SERVER.getPlayerManager().broadcast(Text.literal(ConfigLoader.lang.feedback_67),false);
        return ConfigLoader.lang.feedback_03.formatted(currentAvatarWorld, currentAvatarType, currentAvatarPosX, currentAvatarPosZ);
    }

    // This will remove a registered Avatar and a lingering mob with the name "The Moderator" as well
    public static String despawnModeratorAvatar(ServerWorld world) {
        boolean found = false;
        // Remove registered Mob
        if (currentAvatarId != null) {
            found = true;
            Entity e = world.getEntity(currentAvatarId);
            if (e != null) e.discard();
            world.setChunkForced(currentAvatarPosX, currentAvatarPosZ, false);
            currentAvatarId = null;
            currentAvatarType = null;
            currentAvatarPosX = null;
            currentAvatarPosZ = null;
            currentAvatarWorld = null;
        } else {
            // Remove every Mob named "The Moderator"
            for (Entity entity : world.iterateEntities()) {
                if (entity.hasCustomName() &&
                        "The Moderator".equals(Objects.requireNonNull(entity.getCustomName()).getString()) &&
                        !(entity instanceof PlayerEntity)) {
                    // PLay a sound
                    entity.playSound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 2.0f, 0.7f);
                    entity.discard(); // Remove Entity
                    found = true;
                }
            }
        }
        // Chat Output: Moderator despawned Avatar
        if (found && ModEvents.SERVER != null) ModEvents.SERVER.getPlayerManager().broadcast(Text.literal(ConfigLoader.lang.feedback_68),false);
        if (!found) return ConfigLoader.lang.feedback_06; else return ConfigLoader.lang.feedback_05;
    }

    public static void updateChunkAnchor(ServerWorld world, Entity entity) {
        ChunkPos currentChunk = new ChunkPos(entity.getBlockPos());

        if (!currentChunk.equals(lastChunkPos)) {
            if (lastChunkPos != null) {
                world.setChunkForced(lastChunkPos.x, lastChunkPos.z, false);
            }
            world.setChunkForced(currentChunk.x, currentChunk.z, true);
            lastChunkPos = currentChunk;
        }
    }
    public static Entity findModeratorEntity(ServerWorld world) {
        for (Entity entity : world.iterateEntities()) {
            if (entity.hasCustomName() &&
                    "The Moderator".equals(Objects.requireNonNull(entity.getCustomName()).getString()) &&
                    !(entity instanceof PlayerEntity)) {
                return entity;
            }
        }
        return null;
    }
    public static ServerWorld findModeratorWorld(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            if (findModeratorEntity(world) != null) {
                return world;
            }
        }
        return null;
    }

    public static ServerWorld getWorldFromString(MinecraftServer server, String dimensionName) {
        RegistryKey<World> dimensionKey = switch (dimensionName.toLowerCase()) {
            case "overworld" -> World.OVERWORLD;
            case "nether" -> World.NETHER;
            case "end" -> World.END;
            default -> null;
        };
        return dimensionKey != null ? server.getWorld(dimensionKey) : null;
    }

}