package com.nomoneypirate.entity;

import static com.nomoneypirate.Themoderator.LOGGER;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.mixin.MobEntityAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
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
    private static ChunkPos lastChunkPos = null;
    public static World currentAvatarWorld = null;

    // Search for a "lost" Avatar (used at server startup).
    // This may happen when the server shuts down for some reason
    // and a mob with the name "The Moderator" may still exist.
    // In this case we simply "register" the mob as the new Avatar
    // Only possible if the mob had a chunk loader
    public static boolean searchModeratorAvatar(ServerWorld world) {
        boolean found = false;
        // Search Mob named "The Moderator"
        for (Entity entity : world.iterateEntities()) {
            if (entity.hasCustomName() &&
                    "The Moderator".equals(Objects.requireNonNull(entity.getCustomName()).getString()) &&
                    !(entity instanceof PlayerEntity)) {
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
        }
        return found;
    }

    // Get the current Avatar and return type and coordinates
    public static String currentModeratorAvatar() {
        String feedback;
        if (currentAvatarId != null) {
            feedback = ConfigLoader.lang.feedback_16.formatted(currentAvatarType, currentAvatarWorld, currentAvatarPosX, currentAvatarPosZ);
        } else {
            feedback = ConfigLoader.lang.feedback_15;
        }
        return feedback;
    }

    // Spawn an Avatar with given type and coordinates
    public static boolean spawnModeratorAvatar(MinecraftServer server, String dim, String type, int x, int z) {

        System.out.println("[themoderator] Try spawnModeratorAvatar");
        ServerWorld world = getWorldFromString(server, dim);
        if (world == null) return false;

        // Get ground position
        BlockPos groundPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(x, 0, z));
        // In case there is already an Avatar, we just remove it

        if (despawnModeratorAvatar(world)) System.out.println("[themoderator] spawnModeratorAvatar old removed");
//        if (currentAvatarId != null) {
//            System.out.println("[themoderator] spawnModeratorAvatar old removed");
//            Entity old = world.getEntity(currentAvatarId);
//            if (old != null) old.discard();
//            currentAvatarId = null;
//            currentAvatarType = null;
//            world.setChunkForced(currentAvatarPosX, currentAvatarPosZ, false);
//            currentAvatarPosX = null;
//            currentAvatarPosZ = null;
//            currentAvatarWorld = null;
//        }
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
        if (entityType == null) return false;
        // Create entity
        Entity entity = entityType.create(world, null);
        // Make sure entity is set
        if (entity == null) return false;
        System.out.println("[themoderator] Try spawnModeratorAvatar 2");
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
        System.out.println("[themoderator] Try spawnModeratorAvatar return true");
        return true;
    }

    // This will remove a registered Avatar and a lingering mob with the name "The Moderator" as well
    public static boolean despawnModeratorAvatar(ServerWorld world) {
        boolean found = false;
        // Remove registered Mob
        if (currentAvatarId != null) {
            Entity e = world.getEntity(currentAvatarId);
            if (e != null) e.discard();
            currentAvatarId = null;
            currentAvatarType = null;
            world.setChunkForced(currentAvatarPosX, currentAvatarPosZ, false);
            currentAvatarPosX = null;
            currentAvatarPosZ = null;
            currentAvatarWorld = null;
            LOGGER.info("Avatar despawned.");
            found = true;
        } else {
            LOGGER.info("No Avatar to despawn.");
        }
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
        return found;
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
        System.out.println("[themoderator] Try getWorldFromString");
        RegistryKey<World> dimensionKey = switch (dimensionName.toLowerCase()) {
            case "overworld" -> World.OVERWORLD;
            case "nether" -> World.NETHER;
            case "end" -> World.END;
            default -> null;
        };
        System.out.println("[themoderator] getWorldFromString dimensionKey");
        return dimensionKey != null ? server.getWorld(dimensionKey) : null;
    }

}