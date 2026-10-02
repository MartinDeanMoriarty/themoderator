package com.nomoneypirate.actions;

import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.locations.Location;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * The things the moderator can actually do to the world and its players. Every method returns the
 * feedback text the LLM gets to see afterwards, so each branch has to produce a message that makes
 * sense on its own - a missing or malformed one would end the action chain.
 */
public class ModActions {

    // Limits that hold no matter what the LLM asks for
    public static final int MAX_GIVE_AMOUNT = 64;
    public static final int MIN_DAMAGE = 1;
    public static final int MAX_DAMAGE = 10;
    private static final int WEATHER_TICKS = 12000; // 10 minutes

    // Items the LLM must never hand out - op-only/creative-only blocks and items that can wreck a world.
    // ("test_*" blocks are matched by prefix below.)
    private static final Set<String> GIVE_DENYLIST = Set.of(
            "command_block", "chain_command_block", "repeating_command_block", "command_block_minecart",
            "structure_block", "structure_void", "jigsaw", "barrier", "light", "debug_stick", "bedrock",
            "spawner", "trial_spawner", "vault", "end_portal_frame", "reinforced_deepslate", "knowledge_book"
    );

    public static String whereIs(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        ResourceKey<Level> dimensionKey = player.level().dimension();
        String dimensionName = dimensionKey.identifier().getPath();
        return String.format(ConfigLoader.lang.feedback_13, player.getName().getString(), dimensionName, pos.getX(), pos.getY(), pos.getZ());
    }

    public static String clearInventory(ServerPlayer player) {
        // Clear inventory but not Armor and off-hand
        Collections.fill(player.getInventory().getNonEquipmentItems(), ItemStack.EMPTY);
        return ConfigLoader.lang.feedback_39.formatted(player.getName().getString());
    }

    public static String damagePlayer(ServerPlayer player, int amount) {
        ServerLevel world = player.level();
        int damage = Math.max(MIN_DAMAGE, Math.min(MAX_DAMAGE, amount));
        player.hurtServer(world, world.damageSources().generic(), damage);
        return ConfigLoader.lang.feedback_47.formatted(player.getName().getString(), damage);
    }

    public static String killPlayer(ServerPlayer player) {
        player.kill(player.level());
        return ConfigLoader.lang.feedback_40.formatted(player.getName().getString());
    }

    public static String givePlayer(ServerPlayer player, String itemString, int amount) {
        ServerLevel world = player.level();
        // Try to make Item identifier - and make sure it is a real item, the registry would otherwise quietly return air
        String itemName = itemString == null ? "" : itemString.trim().toLowerCase();
        Identifier itemId = Identifier.tryParse(itemName.contains(":") ? itemName : "minecraft:" + itemName);
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            return ConfigLoader.lang.feedbackInvalidInput.formatted(itemString);
        }
        String path = itemId.getPath();
        if (path.equals("air") || path.startsWith("test_") || GIVE_DENYLIST.contains(path)) {
            return ConfigLoader.lang.feedbackItemDenied.formatted(itemName);
        }
        Item item = BuiltInRegistries.ITEM.getValue(itemId);

        int remaining = Math.max(1, Math.min(MAX_GIVE_AMOUNT, amount));
        int given = remaining;
        int maxStack = Math.max(1, item.getDefaultMaxStackSize());
        while (remaining > 0) {
            // Build ItemStack - never bigger than the item's own stack size
            ItemStack stack = new ItemStack(item, Math.min(remaining, maxStack));
            remaining -= stack.getCount();
            // Put item into players inventory; whatever didn't fit is dropped in front of them
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.spawnAtLocation(world, stack);
            }
        }
        String shown = given + "x " + item.getName(new ItemStack(item)).getString();
        return ConfigLoader.lang.feedback_41.formatted(shown, player.getName().getString());
    }

    public static String changeWeather(MinecraftServer server, String weather) {
        // Same calls as vanilla's /weather command: (clearTime, weatherTime, raining, thundering)
        switch (weather.trim().toLowerCase()) {
            case "clear":
                server.setWeatherParameters(WEATHER_TICKS, 0, false, false);
                break;
            case "rain":
                server.setWeatherParameters(0, WEATHER_TICKS, true, false);
                break;
            case "thunder":
                server.setWeatherParameters(0, WEATHER_TICKS, true, true);
                break;
            default:
                return ConfigLoader.lang.feedback_02;
        }
        return ConfigLoader.lang.feedback_42.formatted(weather);
    }

    public static String changeTime(MinecraftServer server, String time) {
        // Minecraft's day/night cycle is a world clock with named time markers - same as vanilla's /time set
        Optional<Holder<WorldClock>> clock = server.overworld().dimensionTypeRegistration().value().defaultClock();
        if (clock.isEmpty()) return ConfigLoader.lang.exceptionFeedback;
        ServerClockManager clocks = server.clockManager();
        switch (time.trim().toLowerCase()) {
            case "day":
                clocks.moveToTimeMarker(clock.get(), ClockTimeMarkers.DAY);
                break;
            case "noon":
                clocks.moveToTimeMarker(clock.get(), ClockTimeMarkers.NOON);
                break;
            case "night":
                clocks.moveToTimeMarker(clock.get(), ClockTimeMarkers.NIGHT);
                break;
            case "midnight":
                clocks.moveToTimeMarker(clock.get(), ClockTimeMarkers.MIDNIGHT);
                break;
            case "evening": {
                // There is no marker for it: jump to the next 12000 of the 24000-tick day
                long total = clocks.getInstance(clock.get()).totalTicks();
                long next = total - Math.floorMod(total, 24000L) + 12000L;
                if (next <= total) next += 24000L;
                clocks.setTotalTicks(clock.get(), next);
                break;
            }
            default:
                return ConfigLoader.lang.feedback_02;
        }
        return ConfigLoader.lang.feedback_43.formatted(time);
    }

    /** Teleports to X/Z in the world the player is already in. */
    public static String teleportPositionPlayer(ServerPlayer player, double posX, double posZ) {
        return teleport(player, player.level(), (int) Math.floor(posX), (int) Math.floor(posZ));
    }

    /** Teleports to a saved location - including its dimension. */
    public static String teleportToLocation(MinecraftServer server, ServerPlayer player, Location location) {
        String dim = location.dim == null ? "" : location.dim.trim().toUpperCase();
        ResourceKey<Level> key = switch (dim) {
            case "NETHER", "THE_NETHER" -> Level.NETHER;
            case "END", "THE_END" -> Level.END;
            default -> Level.OVERWORLD;
        };
        ServerLevel target = server.getLevel(key);
        if (target == null) return ConfigLoader.lang.feedbackInvalidInput.formatted(location.dim);
        return teleport(player, target, location.x, location.z);
    }

    private static String teleport(ServerPlayer player, ServerLevel target, int x, int z) {
        if (!target.getWorldBorder().isWithinBounds(x + 0.5, z + 0.5)) {
            return ConfigLoader.lang.feedbackOutsideBorder.formatted(x, z);
        }
        Integer y = findSafeY(target, x, z);
        if (y == null) return ConfigLoader.lang.feedbackTeleportUnsafe.formatted(x, z);
        // Teleport player (also works across dimensions)
        player.teleportTo(target, x + 0.5, y, z + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
        return ConfigLoader.lang.feedback_35.formatted(player.getName().getString(), x, z);
    }

    /**
     * The Y to put a player's feet at for X/Z, or null if there is no safe spot: void, lava, or - in
     * the Nether - no free pocket below the ceiling (the heightmap there would point onto the bedrock roof).
     */
    private static Integer findSafeY(ServerLevel level, int x, int z) {
        // The heightmap of a chunk that isn't loaded reads as "nothing here" - load (or generate) it first,
        // otherwise every teleport to unexplored land would look like a void
        level.getChunk(x >> 4, z >> 4);
        int minY = level.getMinY();
        DimensionType dimension = level.dimensionType();
        if (dimension.hasCeiling()) {
            int top = minY + dimension.logicalHeight() - 3;
            for (int y = top; y > minY + 1; y--) {
                if (canStandAt(level, x, y, z, true)) return y;
            }
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (y <= minY) return null;
        return canStandAt(level, x, y, z, false) ? y : null;
    }

    private static boolean canStandAt(ServerLevel level, int x, int y, int z, boolean needFreeSpace) {
        BlockPos feet = new BlockPos(x, y, z);
        BlockState below = level.getBlockState(feet.below());
        BlockState atFeet = level.getBlockState(feet);
        if (below.getFluidState().is(FluidTags.LAVA) || atFeet.getFluidState().is(FluidTags.LAVA)) return false;
        boolean ground = !below.getCollisionShape(level, feet.below()).isEmpty() || below.getFluidState().is(FluidTags.WATER);
        if (!ground) return false;
        return !needFreeSpace || (atFeet.isAir() && level.getBlockState(feet.above()).isAir());
    }

}
