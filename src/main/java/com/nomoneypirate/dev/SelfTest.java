package com.nomoneypirate.dev;

import static com.nomoneypirate.Themoderator.LOGGER;

import com.mojang.authlib.GameProfile;
import com.nomoneypirate.actions.ModActions;
import com.nomoneypirate.actions.ModDecisions;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.events.ModEvents;
import com.nomoneypirate.llm.LlmClient;
import com.nomoneypirate.llm.ModerationDecision;
import com.nomoneypirate.llm.ModerationDecision.Action;
import com.nomoneypirate.locations.Location;
import com.nomoneypirate.locations.LocationManager;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Development-only self test: runs the moderator's real actions inside a real dedicated server and prints PASS/FAIL.
 * Only active when the environment variable THEMODERATOR_SELFTEST is set (see Themoderator.onInitialize), and the
 * class is left out of the release jar. Start it with: THEMODERATOR_SELFTEST=1 ./gradlew runServer
 */
public final class SelfTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> run(server)));
    }

    private static void check(String name, boolean ok) {
        if (ok) passed++; else failed++;
        LOGGER.info("[SelfTest] {} {}", ok ? "PASS" : "FAIL", name);
    }

    private static void run(MinecraftServer server) {
        // The LLM is not part of this test: feedback goes into a list instead
        List<String> feedback = new ArrayList<>();
        ModDecisions.testFeedbackSink = feedback::add;
        try {
            time(server);
            weather(server);
            players(server);
            decisions(server, feedback);
        } catch (Throwable t) {
            failed++;
            LOGGER.error("[SelfTest] FAIL unexpected exception", t);
        } finally {
            ModDecisions.testFeedbackSink = null;
        }
        // The chain tests wait for network answers - they must not block the server thread
        Thread chain = new Thread(() -> {
            try {
                chains(server);
            } catch (Throwable t) {
                failed++;
                LOGGER.error("[SelfTest] FAIL unexpected exception in the chain tests", t);
            } finally {
                LOGGER.info("[SelfTest] RESULT: {} passed, {} failed", passed, failed);
                server.execute(() -> server.halt(false));
            }
        }, "themoderator-selftest");
        chain.setDaemon(true);
        chain.start();
    }

    private static boolean waitUntilIdle(int seconds) throws InterruptedException {
        long end = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < end) {
            if (!ModEvents.isBusy()) return true;
            Thread.sleep(250);
        }
        return false;
    }

    /** A whole request/answer chain through the real code path: LLM down, then LLM up. */
    private static void chains(MinecraftServer server) throws Exception {
        var config = ConfigLoader.config;
        String realUri = config.ollamaURI;
        String message = ConfigLoader.lang.requestContext.formatted(ConfigLoader.lang.playerMessage.formatted("SelfTest", "Moderator, was ist ein Creeper?"));

        // 1) The LLM is not reachable: the request fails - the moderator must not stay busy forever
        config.ollamaURI = "http://127.0.0.1:9/api/chat";
        server.execute(() -> ModDecisions.moderateAndApply(server, LlmClient.ModerationType.MODERATION, message));
        Thread.sleep(500);
        check("chain with the LLM down: busy flag is released again", waitUntilIdle(20));
        config.ollamaURI = realUri;

        // 2) The real LLM answers (skipped if no Ollama is reachable)
        boolean ollama = false;
        try {
            var conn = new java.net.URI(realUri.replace("/api/chat", "/api/tags")).toURL().openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.getInputStream().close();
            ollama = true;
        } catch (Exception ignored) {
            LOGGER.info("[SelfTest] SKIP chain with the real LLM: no Ollama reachable at {}", realUri);
        }
        if (ollama) {
            config.responseTimeout = 120;
            server.execute(() -> ModDecisions.moderateAndApply(server, LlmClient.ModerationType.MODERATION, message));
            Thread.sleep(500);
            check("chain with the real LLM: finishes and releases the busy flag", waitUntilIdle(180));
        }
    }

    private static void time(MinecraftServer server) {
        Holder<WorldClock> clock = server.overworld().dimensionTypeRegistration().value().defaultClock().orElseThrow();
        String[] names = {"day", "noon", "night", "midnight"};
        var markers = List.of(ClockTimeMarkers.DAY, ClockTimeMarkers.NOON, ClockTimeMarkers.NIGHT, ClockTimeMarkers.MIDNIGHT);
        for (int i = 0; i < names.length; i++) {
            String result = ModActions.changeTime(server, names[i]);
            check("time '" + names[i] + "' sets the clock marker", server.clockManager().isAtTimeMarker(clock, markers.get(i)));
            check("time '" + names[i] + "' feedback", result.equals(ConfigLoader.lang.feedback_43.formatted(names[i])));
        }
        ModActions.changeTime(server, "evening");
        long total = server.clockManager().getInstance(clock).totalTicks();
        check("time 'evening' lands on 12000 of the day (was " + Math.floorMod(total, 24000L) + ")", Math.floorMod(total, 24000L) == 12000L);
        check("time with nonsense -> usage feedback", ModActions.changeTime(server, "teatime").equals(ConfigLoader.lang.feedback_02));
    }

    private static void weather(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        ModActions.changeWeather(server, "rain");
        // isRaining() is the visible rain level, which only ramps up over the following ticks - check the target state
        var data = overworld.getWeatherData();
        check("weather rain: raining, not thundering", data.isRaining() && !data.isThundering() && data.getRainTime() > 0);
        ModActions.changeWeather(server, "thunder");
        check("weather thunder: raining and thundering", data.isRaining() && data.isThundering() && data.getThunderTime() > 0);
        ModActions.changeWeather(server, "clear");
        check("weather clear: neither, and a clear spell is set", !data.isRaining() && !data.isThundering() && data.getClearWeatherTime() > 0);
        check("weather with nonsense -> usage feedback", ModActions.changeWeather(server, "snow").equals(ConfigLoader.lang.feedback_02));
    }

    private static int count(FakePlayer player, String itemPath) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(itemPath)) total += stack.getCount();
        }
        return total;
    }

    private static void players(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        FakePlayer bot = FakePlayer.get(overworld, new GameProfile(UUID.randomUUID(), "SelfTestBot"));
        var lang = ConfigLoader.lang;

        // --- teleport
        String tp = ModActions.teleportPositionPlayer(bot, 100, 100);
        check("teleport: success feedback with all three values (" + tp + ")", tp.equals(lang.feedback_35.formatted("SelfTestBot", 100, 100)));
        check("teleport: player is at the target (" + bot.getX() + ", " + bot.getY() + ", " + bot.getZ() + ")",
                Math.abs(bot.getX() - 100.5) < 0.01 && Math.abs(bot.getZ() - 100.5) < 0.01 && bot.getY() > overworld.getMinY());
        check("teleport: outside the world border is refused",
                ModActions.teleportPositionPlayer(bot, 40_000_000, 0).equals(lang.feedbackOutsideBorder.formatted(40_000_000, 0)));
        check("teleport: the void of the End is refused",
                ModActions.teleportToLocation(server, bot, new Location("void", "END", 400, 400)).equals(lang.feedbackTeleportUnsafe.formatted(400, 400)));

        boolean netherOk = false;
        for (int x = 40; x < 400 && !netherOk; x += 37) {
            String r = ModActions.teleportToLocation(server, bot, new Location("hell", "NETHER", x, x));
            if (r.equals(lang.feedback_35.formatted("SelfTestBot", x, x))) {
                netherOk = bot.level().dimension() == Level.NETHER && bot.getY() < 125 && bot.getY() > 0
                        && bot.level().getBlockState(bot.blockPosition()).isAir();
            }
        }
        check("teleport: into the Nether lands below the ceiling, in a free pocket (y=" + bot.getY() + ")", netherOk);
        String back = ModActions.teleportToLocation(server, bot, new Location("home", "OVERWORLD", 10, 10));
        check("teleport: back to the Overworld across dimensions (" + back + ", now in " + bot.level().dimension().identifier() + ")", back.equals(lang.feedback_35.formatted("SelfTestBot", 10, 10)) && bot.level().dimension() == Level.OVERWORLD);

        // --- whereis
        check("whereis mentions the player and the dimension", ModActions.whereIs(bot).contains("SelfTestBot") && ModActions.whereIs(bot).contains("overworld"));

        // --- give
        String give = ModActions.givePlayer(bot, "diamond", 5);
        check("give: 5 diamonds arrive (" + give + ")", count(bot, "diamond") == 5 && give.contains("5x"));
        check("give: command_block is denied", ModActions.givePlayer(bot, "command_block", 1).equals(lang.feedbackItemDenied.formatted("command_block")) && count(bot, "command_block") == 0);
        check("give: unknown item is invalid", ModActions.givePlayer(bot, "banana", 1).equals(lang.feedbackInvalidInput.formatted("banana")));
        ModActions.givePlayer(bot, "cobblestone", 500);
        check("give: amount is capped at 64 (got " + count(bot, "cobblestone") + ")", count(bot, "cobblestone") == 64);
        ModActions.givePlayer(bot, "diamond_sword", 3);
        check("give: unstackable items arrive as separate stacks (got " + count(bot, "diamond_sword") + ")", count(bot, "diamond_sword") == 3);
        check("give: namespaced ids work", ModActions.givePlayer(bot, "minecraft:apple", 2).contains("2x") && count(bot, "apple") == 2);

        // --- damage (a fake player is invulnerable, so only the clamping is checked), clear, kill
        check("damage: amount is clamped to 10", ModActions.damagePlayer(bot, 999).equals(lang.feedback_47.formatted("SelfTestBot", 10)));
        ModActions.clearInventory(bot);
        check("clear: inventory is empty afterwards", bot.getInventory().getNonEquipmentItems().stream().allMatch(ItemStack::isEmpty));
        // (a FakePlayer refuses all damage by design, so this only proves the call itself runs)
        boolean killRan;
        try { ModActions.killPlayer(bot); killRan = true; } catch (RuntimeException e) { killRan = false; }
        check("kill: call runs without error", killRan);
    }

    private static void decisions(MinecraftServer server, List<String> feedback) {
        var lang = ConfigLoader.lang;
        var config = ConfigLoader.config;

        // --- pardon of an OFFLINE banned player, by name
        UserBanList bans = server.getPlayerList().getBans();
        NameAndId offline = new NameAndId(UUID.randomUUID(), "SelfTestBanned");
        bans.add(new UserBanListEntry(offline, new Date(), "selftest", null, "test"));
        check("ban list: entry is there", bans.get(offline) != null);
        feedback.clear();
        ModDecisions.applyDecision(server, new ModerationDecision(Action.PARDON, "selftestbanned", "", ""));
        check("pardon: works by name (case-insensitive) for an offline player", bans.get(offline) == null && feedback.contains(lang.feedback_12.formatted("SelfTestBanned")));

        config.useWhitelist = true;
        bans.add(new UserBanListEntry(offline, new Date(), "selftest", null, "test"));
        ModDecisions.applyDecision(server, new ModerationDecision(Action.PARDON, "SelfTestBanned", "", ""));
        boolean whitelisted = server.getPlayerList().getWhiteList().get(offline) != null;
        check("pardon with useWhitelist: put back on the whitelist", whitelisted);
        server.getPlayerList().getWhiteList().remove(offline);
        config.useWhitelist = false;

        feedback.clear();
        ModDecisions.applyDecision(server, new ModerationDecision(Action.PARDON, "NobodyKnowsThisName", "", ""));
        check("pardon: unknown name gets an honest answer", feedback.size() == 1 && feedback.get(0).contains("NobodyKnowsThisName"));

        // --- disabled actions
        config.disabledActions.add("whereis");
        feedback.clear();
        ModDecisions.applyDecision(server, new ModerationDecision(Action.WHEREIS, "Someone", "", ""));
        check("disabledActions: refused (case-insensitive), LLM is told", feedback.equals(List.of(lang.feedbackActionDisabled.formatted("WHEREIS"))));
        config.disabledActions.remove("whereis");

        // --- locations
        LocationManager.remLocation("selftest-base");
        feedback.clear();
        ModDecisions.applyDecision(server, new ModerationDecision(Action.SETLOCATION, "SelfTest-Base", "the_nether", "10, -20"));
        check("setlocation: accepts 'the_nether' and '10, -20'", feedback.contains(lang.feedback_54.formatted("SelfTest-Base")));
        Location saved = LocationManager.getLocation("selftest-base");
        check("setlocation: stored normalized", saved != null && saved.dim.equals("NETHER") && saved.x == 10 && saved.z == -20);
        feedback.clear();
        ModDecisions.applyDecision(server, new ModerationDecision(Action.SETLOCATION, "SelfTest-Mars", "mars", "1 2"));
        check("setlocation: unknown dimension is refused", feedback.equals(List.of(lang.feedbackInvalidInput.formatted("mars"))));
        feedback.clear();
        ModDecisions.applyDecision(server, new ModerationDecision(Action.SETLOCATION, "SelfTest-Bad", "overworld", "irgendwo"));
        check("setlocation: unreadable position is refused", feedback.equals(List.of(lang.feedback_61)));
        feedback.clear();
        ModDecisions.applyDecision(server, new ModerationDecision(Action.REMLOCATION, "selftest-base", "", ""));
        check("remlocation: removes it", LocationManager.getLocation("selftest-base") == null && feedback.contains(lang.feedback_55.formatted("selftest-base")));

        // --- misc
        feedback.clear();
        ModDecisions.applyDecision(server, new ModerationDecision(Action.WHOIS, "Ghost", "", ""));
        check("whois: unknown offline name does not create a profile", feedback.equals(List.of(lang.feedback_07.formatted("Ghost"))));
    }

    private SelfTest() {
    }
}
