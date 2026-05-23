package com.champutils.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import static net.minecraft.commands.Commands.literal;

public final class BetaDoctorCommand {

    private static final File CONFIG_DIR = new File("config/champutils");

    private BetaDoctorCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("champutils")
                        .then(literal("doctor")
                                .requires(source -> source.hasPermission(4))
                                .executes(context -> run(context.getSource())))
        ));
    }

    public static int run(CommandSourceStack source) {
        List<CheckResult> results = new ArrayList<>();

        checkCoreFolders(results);
        checkJsonConfigs(results);
        checkProgressionConfigs(results);
        checkBattleAndArenaConfigs(results);
        checkDungeonConfigs(results);
        checkWorldEventConfigs(results);
        checkNpcBindings(results);
        checkExternalHooks(results);
        checkDataFolders(results);

        int ok = 0;
        int warn = 0;
        int fail = 0;

        for (CheckResult result : results) {
            switch (result.level) {
                case OK -> ok++;
                case WARN -> warn++;
                case FAIL -> fail++;
            }
        }

        final int okCount = ok;
        final int warnCount = warn;
        final int failCount = fail;

        header(source);

        for (CheckResult result : results) {
            sendResult(source, result);
        }

        divider(source);

        source.sendSuccess(
                () -> Component.literal("Summary: ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(okCount + " OK").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal(warnCount + " Warnings").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal(failCount + " Fails").withStyle(ChatFormatting.RED)),
                false
        );

        if (failCount > 0) {
            source.sendFailure(Component.literal("Fix failed checks before opening beta."));
            return 0;
        }

        if (warnCount > 0) {
            source.sendSuccess(
                    () -> Component.literal("Beta can run, but review warnings first.")
                            .withStyle(ChatFormatting.YELLOW),
                    false
            );
            return 1;
        }

        source.sendSuccess(
                () -> Component.literal("No obvious beta blockers found.")
                        .withStyle(ChatFormatting.GREEN),
                false
        );

        return 1;
    }

    private static void header(CommandSourceStack source) {
        divider(source);
        source.sendSuccess(
                () -> Component.literal("ChampUtils Beta Doctor")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                false
        );
        source.sendSuccess(
                () -> Component.literal("Checks configs, bindings, hooks, and beta blockers.")
                        .withStyle(ChatFormatting.GRAY),
                false
        );
        divider(source);
    }

    private static void divider(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        .withStyle(ChatFormatting.DARK_GRAY),
                false
        );
    }

    private static void sendResult(CommandSourceStack source, CheckResult result) {
        String icon;
        ChatFormatting iconColor;

        switch (result.level) {
            case OK -> {
                icon = "✔";
                iconColor = ChatFormatting.GREEN;
            }
            case WARN -> {
                icon = "⚠";
                iconColor = ChatFormatting.YELLOW;
            }
            case FAIL -> {
                icon = "✖";
                iconColor = ChatFormatting.RED;
            }
            default -> {
                icon = "?";
                iconColor = ChatFormatting.GRAY;
            }
        }

        final String finalIcon = icon;
        final ChatFormatting finalIconColor = iconColor;

        source.sendSuccess(
                () -> Component.literal(finalIcon + " ")
                        .withStyle(finalIconColor)
                        .append(Component.literal(result.title).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal(result.message).withStyle(ChatFormatting.GRAY)),
                false
        );
    }

    private static void checkCoreFolders(List<CheckResult> results) {
        if (!CONFIG_DIR.exists()) {
            results.add(CheckResult.fail("Config folder", "config/champutils does not exist."));
            return;
        }

        if (!CONFIG_DIR.isDirectory()) {
            results.add(CheckResult.fail("Config folder", "config/champutils exists but is not a folder."));
            return;
        }

        results.add(CheckResult.ok("Config folder", "config/champutils exists."));
    }

    private static void checkJsonConfigs(List<CheckResult> results) {
        String[] importantFiles = {
                "rules.json",
                "gyms.json",
                "gymleaders.json",
                "gym_settings.json",
                "arena_locations.json",
                "champ_dungeons.json",
                "dungeon_keys.json",
                "dungeon_key_drops.json",
                "dungeon_rewards.json",
                "dungeon_trainers.json",
                "world_events.json",
                "profession_tools.json",
                "profession_fragments.json",
                "profession_reward_passives.json",
                "battle_profession_loot.json",
                "auction_npc_binding.json",
                "menu_npc_bindings.json",
                "dex_rewards.json"
        };

        int valid = 0;
        int missing = 0;
        int invalid = 0;

        for (String fileName : importantFiles) {
            File file = new File(CONFIG_DIR, fileName);

            if (!file.exists()) {
                missing++;
                continue;
            }

            if (!isValidJson(file)) {
                invalid++;
                results.add(CheckResult.fail("Invalid JSON", fileName + " could not be parsed."));
                continue;
            }

            valid++;
        }

        if (invalid == 0) {
            results.add(CheckResult.ok("JSON syntax", valid + " important JSON config files parsed successfully."));
        }

        if (missing > 0) {
            results.add(CheckResult.warn("Missing optional configs", missing + " expected config file(s) were not found. Some may auto-generate after reload/startup."));
        }
    }

    private static void checkProgressionConfigs(List<CheckResult> results) {
        checkJsonHasArrayOrObject(results, "profession_tools.json", "Profession tools", "tools");
        checkJsonHasArrayOrObject(results, "profession_fragments.json", "Profession fragments", "fragments");
        checkJsonHasArrayOrObject(results, "battle_profession_loot.json", "Battle profession loot", "rewards");
        checkDexRewards(results);
    }

    private static void checkBattleAndArenaConfigs(List<CheckResult> results) {
        File rules = new File(CONFIG_DIR, "rules.json");

        if (!rules.exists()) {
            results.add(CheckResult.fail("Battle rules", "rules.json is missing."));
        } else {
            JsonObject root = readObject(rules);
            if (root == null) {
                results.add(CheckResult.fail("Battle rules", "rules.json is invalid."));
            } else if (!root.has("formats")) {
                results.add(CheckResult.warn("Battle rules", "rules.json does not contain a formats section."));
            } else {
                results.add(CheckResult.ok("Battle rules", "rules.json contains battle formats."));
            }
        }

        File arenas = new File(CONFIG_DIR, "arena_locations.json");

        if (!arenas.exists()) {
            results.add(CheckResult.warn("Arenas", "arena_locations.json is missing. Use /arena commands to configure arenas."));
            return;
        }

        JsonObject root = readObject(arenas);
        if (root == null) {
            results.add(CheckResult.fail("Arenas", "arena_locations.json is invalid."));
            return;
        }

        int count = countObjectMembers(root, "arenas");

        if (count <= 0) {
            results.add(CheckResult.warn("Arenas", "No arenas are configured yet."));
        } else if (count < 10) {
            results.add(CheckResult.warn("Arenas", count + " arena(s) configured. You wanted 10 default arenas for beta."));
        } else {
            results.add(CheckResult.ok("Arenas", count + " arena(s) configured."));
        }
    }

    private static void checkDungeonConfigs(List<CheckResult> results) {
        requireConfig(results, "champ_dungeons.json", "Expeditions");
        requireConfig(results, "dungeon_rewards.json", "Expedition rewards");
        requireConfig(results, "dungeon_keys.json", "Expedition keys");
        requireConfig(results, "dungeon_trainers.json", "Expedition trainers");

        File crates = new File(CONFIG_DIR, "dungeon_native_crates.json");
        if (!crates.exists()) {
            results.add(CheckResult.warn("Crates", "No native crate bindings file found yet."));
            return;
        }

        JsonObject root = readObject(crates);
        if (root == null) {
            results.add(CheckResult.fail("Crates", "dungeon_native_crates.json is invalid."));
            return;
        }

        int total = root.entrySet().size();
        if (total <= 0) {
            results.add(CheckResult.warn("Crates", "No native crates are bound."));
        } else {
            results.add(CheckResult.ok("Crates", total + " crate binding section(s) found."));
        }
    }

    private static void checkWorldEventConfigs(List<CheckResult> results) {
        requireConfig(results, "world_events.json", "World events");

        File bindings = new File(CONFIG_DIR, "world_event_bindings.json");
        if (!bindings.exists()) {
            results.add(CheckResult.warn("World event NPCs", "No world event NPC bindings found."));
            return;
        }

        JsonObject root = readObject(bindings);
        if (root == null) {
            results.add(CheckResult.fail("World event NPCs", "world_event_bindings.json is invalid."));
            return;
        }

        if (root.entrySet().isEmpty()) {
            results.add(CheckResult.warn("World event NPCs", "world_event_bindings.json is empty."));
        } else {
            results.add(CheckResult.ok("World event NPCs", root.entrySet().size() + " binding entry/entries found."));
        }
    }

    private static void checkNpcBindings(List<CheckResult> results) {
        checkBindingFile(results, "auction_npc_binding.json", "Auction NPC");
        checkBindingFile(results, "menu_npc_bindings.json", "Menu NPCs");
        checkBindingFile(results, "dungeon_bindings.json", "Expedition NPCs");
    }

    private static void checkExternalHooks(List<CheckResult> results) {
        boolean cobblemonLoaded = FabricLoader.getInstance().isModLoaded("cobblemon");
        boolean polymerLoaded = FabricLoader.getInstance().isModLoaded("polymer-bundled")
                || FabricLoader.getInstance().isModLoaded("polymer")
                || FabricLoader.getInstance().isModLoaded("polymer-core");
        boolean luckPermsLoaded = FabricLoader.getInstance().isModLoaded("luckperms");
        boolean economyCraftLoaded = FabricLoader.getInstance().isModLoaded("economycraft")
                || FabricLoader.getInstance().isModLoaded("economy-craft");

        if (cobblemonLoaded) {
            results.add(CheckResult.ok("Cobblemon", "Cobblemon mod detected."));
        } else {
            results.add(CheckResult.fail("Cobblemon", "Cobblemon was not detected. ChampUtils needs Cobblemon."));
        }

        if (polymerLoaded) {
            results.add(CheckResult.ok("Polymer", "Polymer detected."));
        } else {
            results.add(CheckResult.warn("Polymer", "Polymer was not detected by common mod ids. Verify your bundled Polymer jar is loaded."));
        }

        if (luckPermsLoaded) {
            results.add(CheckResult.ok("LuckPerms", "LuckPerms detected."));
        } else {
            results.add(CheckResult.warn("LuckPerms", "LuckPerms was not detected. Permission-gated commands may rely on vanilla OP only."));
        }

        if (economyCraftLoaded) {
            results.add(CheckResult.ok("Economy", "EconomyCraft detected."));
        } else {
            results.add(CheckResult.warn("Economy", "EconomyCraft was not detected by common mod ids. Verify credits/economy features in-game."));
        }
    }

    private static void checkDataFolders(List<CheckResult> results) {
        checkDirectory(results, new File(CONFIG_DIR, "players"), "Player data");
        checkDirectory(results, new File(CONFIG_DIR, "professions"), "Profession data");
        checkDirectory(results, new File(CONFIG_DIR, "seasons"), "Season archives");
        checkDirectory(results, new File(CONFIG_DIR, "player_options"), "Player options");
    }

    private static void checkDexRewards(List<CheckResult> results) {
        File file = new File(CONFIG_DIR, "dex_rewards.json");

        if (!file.exists()) {
            results.add(CheckResult.warn("Dex rewards", "dex_rewards.json is missing. Dex rewards will need config before beta."));
            return;
        }

        JsonObject root = readObject(file);
        if (root == null) {
            results.add(CheckResult.fail("Dex rewards", "dex_rewards.json is invalid."));
            return;
        }

        int totalPokemon = getInt(root, "totalPokemon", -1);
        int tierCount = countArrayMembers(root, "tiers");

        if (totalPokemon != 1009) {
            results.add(CheckResult.warn("Dex rewards", "totalPokemon is " + totalPokemon + ". You said current target is 1009."));
        } else if (tierCount < 20) {
            results.add(CheckResult.warn("Dex rewards", tierCount + " tier(s) found. Every 5% from 5% to 100% should be 20 tiers."));
        } else {
            results.add(CheckResult.ok("Dex rewards", "1009 total Pokémon and " + tierCount + " reward tier(s) found."));
        }
    }

    private static void requireConfig(List<CheckResult> results, String fileName, String title) {
        File file = new File(CONFIG_DIR, fileName);

        if (!file.exists()) {
            results.add(CheckResult.warn(title, fileName + " is missing."));
            return;
        }

        if (!isValidJson(file)) {
            results.add(CheckResult.fail(title, fileName + " is invalid JSON."));
            return;
        }

        results.add(CheckResult.ok(title, fileName + " exists and parses."));
    }

    private static void checkBindingFile(List<CheckResult> results, String fileName, String title) {
        File file = new File(CONFIG_DIR, fileName);

        if (!file.exists()) {
            results.add(CheckResult.warn(title, fileName + " is missing. Bind NPCs before beta if this feature should be public."));
            return;
        }

        JsonObject root = readObject(file);

        if (root == null) {
            results.add(CheckResult.fail(title, fileName + " is invalid JSON."));
            return;
        }

        if (root.entrySet().isEmpty()) {
            results.add(CheckResult.warn(title, fileName + " exists but is empty."));
        } else {
            results.add(CheckResult.ok(title, fileName + " has binding data."));
        }
    }

    private static void checkDirectory(List<CheckResult> results, File dir, String title) {
        if (!dir.exists()) {
            results.add(CheckResult.warn(title, dir.getPath() + " does not exist yet. It may generate when players use the system."));
            return;
        }

        if (!dir.isDirectory()) {
            results.add(CheckResult.fail(title, dir.getPath() + " exists but is not a folder."));
            return;
        }

        results.add(CheckResult.ok(title, dir.getPath() + " exists."));
    }

    private static void checkJsonHasArrayOrObject(List<CheckResult> results, String fileName, String title, String preferredKey) {
        File file = new File(CONFIG_DIR, fileName);

        if (!file.exists()) {
            results.add(CheckResult.warn(title, fileName + " is missing."));
            return;
        }

        JsonObject root = readObject(file);

        if (root == null) {
            results.add(CheckResult.fail(title, fileName + " is invalid JSON."));
            return;
        }

        if (root.entrySet().isEmpty()) {
            results.add(CheckResult.warn(title, fileName + " exists but appears empty."));
            return;
        }

        if (root.has(preferredKey)) {
            results.add(CheckResult.ok(title, fileName + " contains " + preferredKey + "."));
        } else {
            results.add(CheckResult.ok(title, fileName + " exists and has data."));
        }
    }

    private static boolean isValidJson(File file) {
        try (FileReader reader = new FileReader(file)) {
            JsonParser.parseReader(reader);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static JsonObject readObject(File file) {
        try (FileReader reader = new FileReader(file)) {
            JsonElement element = JsonParser.parseReader(reader);

            if (element == null || !element.isJsonObject()) {
                return null;
            }

            return element.getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int countObjectMembers(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return 0;
        }

        return root.getAsJsonObject(key).entrySet().size();
    }

    private static int countArrayMembers(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonArray()) {
            return 0;
        }

        return root.getAsJsonArray(key).size();
    }

    private static int getInt(JsonObject root, String key, int fallback) {
        try {
            if (!root.has(key)) {
                return fallback;
            }

            return root.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private enum Level {
        OK,
        WARN,
        FAIL
    }

    private static final class CheckResult {
        private final Level level;
        private final String title;
        private final String message;

        private CheckResult(Level level, String title, String message) {
            this.level = level;
            this.title = title;
            this.message = message;
        }

        private static CheckResult ok(String title, String message) {
            return new CheckResult(Level.OK, title, message);
        }

        private static CheckResult warn(String title, String message) {
            return new CheckResult(Level.WARN, title, message);
        }

        private static CheckResult fail(String title, String message) {
            return new CheckResult(Level.FAIL, title, message);
        }
    }
}
