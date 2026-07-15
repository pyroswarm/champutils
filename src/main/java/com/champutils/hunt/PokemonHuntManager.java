package com.champutils.hunt;

import com.champutils.adventurer.AdventurerGuildManager;
import com.champutils.database.SharedJsonStateRepository;
import com.champutils.economy.EconomyManager;
import com.champutils.crate.CrateCreditManager;
import com.champutils.network.NetworkServerConfig;
import com.champutils.profession.ProfessionFragmentManager;
import com.champutils.profession.ProfessionChunkManager;
import com.champutils.shop.NpcShopService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class PokemonHuntManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "pokemon_hunts_state.json");
    private static final Random RANDOM = new Random();

    private static PokemonHuntState STATE = new PokemonHuntState();
    private static final String STATE_KEY = "pokemon_hunts";
    private static int followerSyncCounter = 0;
    private static boolean followerSyncInFlight = false;

    private PokemonHuntManager() {}

    public static synchronized void load() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            if (!FILE.exists()) {
                STATE = new PokemonHuntState();
                save();
            }
            else {
                try (FileReader reader = new FileReader(FILE)) {
                    PokemonHuntState loaded = GSON.fromJson(reader, PokemonHuntState.class);
                    STATE = loaded == null ? new PokemonHuntState() : loaded;
                }
            }
            if (STATE.hunts == null) STATE.hunts = new ArrayList<>();
            if (STATE.pendingRewards == null) STATE.pendingRewards = new ArrayList<>();
            STATE = SharedJsonStateRepository.loadGlobal(STATE_KEY, PokemonHuntState.class, STATE);
            if (STATE.hunts == null) STATE.hunts = new ArrayList<>();
            if (STATE.pendingRewards == null) STATE.pendingRewards = new ArrayList<>();
            boolean repaired = repairLoadedHuntGenders();
            if (repaired) save();
        } catch (Exception e) {
            e.printStackTrace();
            STATE = new PokemonHuntState();
        }
    }

    private static boolean repairLoadedHuntGenders() {
        boolean changed = false;
        if (STATE == null || STATE.hunts == null) {
            return false;
        }
        for (PokemonHuntState.HuntEntry hunt : STATE.hunts) {
            if (hunt == null || hunt.species == null) {
                continue;
            }
            String repaired = forcedGenderForSpecies(hunt.species, hunt.gender == null ? java.util.List.of() : java.util.List.of(hunt.gender));
            if (repaired == null || repaired.isBlank()) {
                repaired = "any";
            }
            if (!normalizeGender(repaired).equals(normalizeGender(hunt.gender))) {
                hunt.gender = repaired;
                changed = true;
            }
        }
        return changed;
    }

    public static synchronized void save() {
        saveLocalMirror();
        SharedJsonStateRepository.saveGlobal(STATE_KEY, STATE);
    }

    private static void saveLocalMirror() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(STATE, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized PokemonHuntState snapshot() {
        return STATE;
    }

    public static synchronized long millisUntilRefresh() {
        return Math.max(0L, STATE.nextRefreshAtMillis - System.currentTimeMillis());
    }

    public static synchronized void setRefreshHours(double hours, MinecraftServer server) {
        if (hours <= 0.0) hours = 1.0;
        PokemonHuntConfig.DATA.settings.refreshHours = hours;
        PokemonHuntConfig.save();
        long now = System.currentTimeMillis();
        STATE.nextRefreshAtMillis = now + refreshMillis();
        save();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§b[Hunts] §7Hunt refresh interval set to §f" + hours + " hour(s)§7."),
                    false
            );
        }
    }

    public static synchronized void ensureStarted(MinecraftServer server) {
        if (!PokemonHuntConfig.DATA.settings.enabled) return;
        if (!NetworkServerConfig.isAuthoritativeGameplayServer()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (STATE.hunts == null || STATE.hunts.isEmpty() || STATE.nextRefreshAtMillis <= 0L) {
            refresh(server, false);
            return;
        }
        if (now >= STATE.nextRefreshAtMillis) refresh(server, true);
    }

    public static synchronized void tick(MinecraftServer server) {
        if (server == null || server.getTickCount() % 20 != 0) return;
        if (!NetworkServerConfig.isAuthoritativeGameplayServer()) {
            followerSyncCounter++;
            if (followerSyncCounter >= 60 && !followerSyncInFlight) {
                followerSyncCounter = 0;
                followerSyncInFlight = true;
                SharedJsonStateRepository.loadGlobalAsync(STATE_KEY, PokemonHuntState.class, STATE)
                        .whenComplete((loaded, error) -> {
                            synchronized (PokemonHuntManager.class) {
                                try {
                                    if (error == null && loaded != null) {
                                        STATE = loaded;
                                        if (STATE.hunts == null) STATE.hunts = new ArrayList<>();
                                        if (STATE.pendingRewards == null) STATE.pendingRewards = new ArrayList<>();
                                    } else if (error != null) {
                                        System.err.println("[ChampUtils] Failed to async sync follower Pokémon hunts: " + error.getMessage());
                                    }
                                } finally {
                                    followerSyncInFlight = false;
                                }
                            }
                        });
            }
            return;
        }
        ensureStarted(server);
    }

    public static synchronized void forceRefresh(MinecraftServer server) {
        if (!NetworkServerConfig.isAuthoritativeGameplayServer()) {
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(Component.literal("§c[Hunts] Hunt refreshes are controlled by " + NetworkServerConfig.get().survivalServerId + "."), false);
            }
            return;
        }
        refresh(server, true);
    }

    private static void refresh(MinecraftServer server, boolean announce) {
        long now = System.currentTimeMillis();
        STATE.cycleStartedAtMillis = now;
        STATE.nextRefreshAtMillis = now + refreshMillis();
        STATE.hunts = new ArrayList<>();

        int count = Math.max(1, PokemonHuntConfig.DATA.settings.huntsPerCycle);
        List<PokemonHuntConfig.HuntTarget> available = new ArrayList<>(PokemonHuntConfig.DATA.targetPool);
        Set<String> usedSpecies = new HashSet<>();

        for (int i = 0; i < count && !available.isEmpty(); i++) {
            PokemonHuntConfig.HuntTarget target = pickWeightedTarget(available, usedSpecies);
            if (target == null) break;
            usedSpecies.add(normalSpecies(target.species));
            STATE.hunts.add(createEntry(target));
        }

        save();

        if (announce && server != null && PokemonHuntConfig.DATA.settings.announceNewHunts) {
            com.champutils.profession.ProfessionNotificationSettings.sendBroadcast(
                    server,
                    Component.literal("§6[Adventurer's Guild] §fNew Pokémon hunts are available. Use §e/hunts§f or visit the Guild.")
            );
        }
    }

    private static PokemonHuntState.HuntEntry createEntry(PokemonHuntConfig.HuntTarget target) {
        PokemonHuntState.HuntEntry entry = new PokemonHuntState.HuntEntry();
        entry.id = UUID.randomUUID().toString();
        entry.species = normalSpecies(target.species);
        entry.difficulty = PokemonHuntConfig.normalizeDifficulty(target.difficulty);
        boolean requiresNature = entry.difficulty.equals("A") || entry.difficulty.equals("S");
        entry.nature = requiresNature ? pick(target.natures, "jolly").toLowerCase(Locale.ROOT) : "any";
        entry.gender = forcedGenderForSpecies(entry.species, target.genders);
        entry.ability = normalizeAbility(pick(target.abilities, "any"));
        entry.rewards = target.rewards == null ? new PokemonHuntConfig.Rewards() : target.rewards;
        return entry;
    }


    private static String forcedGenderForSpecies(String species, java.util.List<String> configured) {
        String normalized = genderKey(species);

        if (GENDERLESS_SPECIES.contains(normalized)) {
            return "genderless";
        }

        String fixedGender = SINGLE_GENDER_SPECIES.get(normalized);
        if (fixedGender != null && !fixedGender.isBlank()) {
            return fixedGender;
        }

        java.util.List<String> valid = new java.util.ArrayList<>();
        if (configured != null) {
            for (String raw : configured) {
                String gender = normalizeGender(raw);
                if (gender.equals("male") || gender.equals("female") || gender.equals("any")) {
                    valid.add(gender);
                }
            }
        }

        if (valid.isEmpty()) {
            valid.add("male");
            valid.add("female");
        }

        String picked = normalizeGender(pick(valid, "any"));
        return picked.isBlank() || picked.equals("genderless") ? "any" : picked;
    }

    private static String genderKey(String species) {
        return normalSpecies(species).replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static final java.util.Set<String> GENDERLESS_SPECIES = java.util.Set.of(
            "magnemite","magneton","voltorb","electrode","staryu","starmie","ditto","porygon","unown","porygon2","porygonz",
            "shedinja","lunatone","solrock","baltoy","claydol","beldum","metang","metagross",
            "bronzor","bronzong","magnezone","rotom","klink","klang","klinklang","cryogonal","golett","golurk",
            "carbink","minior","dhelmise","sinistea","polteageist","poltchageist","sinistcha","falinks","tandemaus","maushold","gimmighoul","gholdengo",
            "mew","celebi","jirachi","deoxys","phione","manaphy","darkrai","shaymin","arceus","victini","keldeo","meloetta","genesect","diancie","hoopa","volcanion","magearna","marshadow","zeraora","meltan","melmetal","zarude","pecharunt",
            "articuno","zapdos","moltres","mewtwo","raikou","entei","suicune","lugia","hooh","regirock","regice","registeel",
            "kyogre","groudon","rayquaza","uxie","mesprit","azelf","dialga","palkia","regigigas","giratina",
            "cobalion","terrakion","virizion","reshiram","zekrom","kyurem","xerneas","yveltal","zygarde",
            "typenull","silvally","tapukoko","tapulele","tapubulu","tapufini","cosmog","cosmoem","solgaleo","lunala","necrozma",
            "zacian","zamazenta","eternatus","regieleki","regidrago","glastrier","spectrier","calyrex",
            "wochien","chienpao","tinglu","chiyu","koraidon","miraidon",
            "greattusk","screamtail","brutebonnet","fluttermane","slitherwing","sandyshocks","roaringmoon","walkingwake",
            "irontreads","ironbundle","ironhands","ironjugulis","ironmoth","ironthorns","ironvaliant","ironleaves","ironcrown","ironboulder",
            "nihilego","buzzwole","pheromosa","xurkitree","celesteela","kartana","guzzlord","poipole","naganadel","stakataka","blacephalon"
    );

    private static final java.util.Map<String, String> SINGLE_GENDER_SPECIES = java.util.Map.ofEntries(
            java.util.Map.entry("nidoranfemale", "female"),
            java.util.Map.entry("nidoranmale", "male"),
            java.util.Map.entry("nidoranf", "female"),
            java.util.Map.entry("nidoranm", "male"),
            java.util.Map.entry("latias", "female"),
            java.util.Map.entry("latios", "male"),
            java.util.Map.entry("cresselia", "female"),
            java.util.Map.entry("tornadus", "male"),
            java.util.Map.entry("thundurus", "male"),
            java.util.Map.entry("landorus", "male"),
            java.util.Map.entry("enamorus", "female"),
            java.util.Map.entry("ogerpon", "female"),
            java.util.Map.entry("okidogi", "male"),
            java.util.Map.entry("munkidori", "male"),
            java.util.Map.entry("fezandipiti", "male"),
            java.util.Map.entry("terapagos", "male")
    );

    private static boolean isAlwaysGenderlessSpecies(String species) {
        return GENDERLESS_SPECIES.contains(genderKey(species));
    }

    private static PokemonHuntConfig.HuntTarget pickWeightedTarget(List<PokemonHuntConfig.HuntTarget> targets, Set<String> usedSpecies) {
        List<PokemonHuntConfig.HuntTarget> filtered = new ArrayList<>();
        for (PokemonHuntConfig.HuntTarget target : targets) {
            if (target == null || target.species == null || target.species.isBlank()) continue;
            if (usedSpecies.contains(normalSpecies(target.species))) continue;
            filtered.add(target);
        }
        if (filtered.isEmpty()) return null;

        int total = 0;
        for (PokemonHuntConfig.HuntTarget target : filtered) total += Math.max(1, target.weight);
        int roll = RANDOM.nextInt(Math.max(1, total));
        int cursor = 0;
        for (PokemonHuntConfig.HuntTarget target : filtered) {
            cursor += Math.max(1, target.weight);
            if (roll < cursor) return target;
        }
        return filtered.get(RANDOM.nextInt(filtered.size()));
    }

    private static String pick(List<String> values, String fallback) {
        if (values == null || values.isEmpty()) return fallback;
        List<String> clean = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) clean.add(value.trim());
        }
        if (clean.isEmpty()) return fallback;
        return clean.get(RANDOM.nextInt(clean.size()));
    }

    private static long refreshMillis() {
        double hours = Math.max(0.01, PokemonHuntConfig.DATA.settings.refreshHours);
        return Math.max(60_000L, (long) (hours * 60.0 * 60.0 * 1000.0));
    }

    public static synchronized List<PokemonHuntState.HuntEntry> sortedHunts() {
        List<PokemonHuntState.HuntEntry> list = new ArrayList<>(STATE.hunts == null ? Collections.emptyList() : STATE.hunts);
        list.sort(Comparator.comparing((PokemonHuntState.HuntEntry h) -> h.claimed).thenComparing(h -> h.species == null ? "" : h.species));
        return list;
    }

    public static void handleCatch(ServerPlayer player, Object pokemon) {
        if (player == null || pokemon == null) return;
        if (!PokemonHuntConfig.DATA.settings.enabled) return;

        String species = normalSpecies(PokemonHuntReflection.speciesId(pokemon));
        String nature = PokemonHuntReflection.natureName(pokemon).toLowerCase(Locale.ROOT);
        String gender = normalizeGender(PokemonHuntReflection.genderName(pokemon));
        String ability = normalizeAbility(PokemonHuntReflection.abilityName(pokemon));
        UUID playerId = player.getUUID();
        String playerName = player.getName().getString();

        SharedJsonStateRepository.mutateGlobalAsync(STATE_KEY, PokemonHuntState.class, snapshot(), state -> {
            ensureStateCollections(state);
            for (PokemonHuntState.HuntEntry hunt : state.hunts) {
                if (hunt == null || hunt.claimed) continue;
                if (!normalSpecies(hunt.species).equals(species)) continue;
                if (!matchesAny(normalizeNature(hunt.nature), normalizeNature(nature))) continue;
                if (!matchesAny(normalizeGender(hunt.gender), gender)) continue;
                if (!abilityMatches(hunt.ability, ability)) continue;

                hunt.claimed = true;
                hunt.winnerUuid = playerId.toString();
                hunt.winnerName = playerName;
                hunt.completedAtMillis = System.currentTimeMillis();
                hunt.rewardClaimed = false;
                addPendingReward(state, hunt);
                return new HuntCompletion(state, copyHunt(hunt));
            }
            return new HuntCompletion(state, null);
        }).whenComplete((result, error) -> player.server.execute(() -> {
            if (error != null) {
                System.err.println("[ChampUtils] Failed to atomically complete a network Pokémon hunt.");
                error.printStackTrace();
                return;
            }
            if (result == null) return;
            synchronized (PokemonHuntManager.class) {
                STATE = result.state();
                saveLocalMirror();
            }
            PokemonHuntState.HuntEntry hunt = result.hunt();
            if (hunt == null || !com.champutils.teleport.SafeTeleportManager.isLive(player)) return;
            String target = displayTarget(hunt);
            player.sendSystemMessage(Component.literal("§a[Adventurer's Guild] Hunt complete: §e" + target + "§a. Use §f/hunts claim§a or visit the Guild to claim your reward."));
            if (PokemonHuntConfig.DATA.settings.announceWinners && player.server != null) {
                com.champutils.profession.ProfessionNotificationSettings.sendBroadcast(
                        player.server,
                        Component.literal("§6[Adventurer's Guild] §f" + playerName + " caught the hunted §e" + target + "§f! Use §e/hunts claim§f to claim the reward.")
                );
            }
        }));
    }

    public static boolean claimRewards(ServerPlayer player) {
        if (player == null) return false;
        String playerUuid = player.getUUID().toString();

        SharedJsonStateRepository.mutateGlobalAsync(STATE_KEY, PokemonHuntState.class, snapshot(), state -> {
            ensureStateCollections(state);
            List<PokemonHuntState.HuntEntry> rewards = new ArrayList<>();
            List<PokemonHuntState.HuntEntry> remaining = new ArrayList<>();
            for (PokemonHuntState.HuntEntry hunt : state.pendingRewards) {
                if (hunt == null) continue;
                if (!hunt.rewardClaimed && playerUuid.equals(hunt.winnerUuid)) {
                    hunt.rewardClaimed = true;
                    markActiveHuntRewardClaimed(state, hunt.id);
                    rewards.add(copyHunt(hunt));
                } else if (!hunt.rewardClaimed) {
                    remaining.add(hunt);
                }
            }
            state.pendingRewards = remaining;
            return new HuntClaimResult(state, rewards);
        }).whenComplete((result, error) -> player.server.execute(() -> {
            if (!com.champutils.teleport.SafeTeleportManager.isLive(player)) return;
            if (error != null || result == null) {
                player.sendSystemMessage(Component.literal("§c[Hunts] Could not claim hunt rewards right now."));
                if (error != null) error.printStackTrace();
                return;
            }
            synchronized (PokemonHuntManager.class) {
                STATE = result.state();
                saveLocalMirror();
            }
            int claimedCount = result.rewards().size();
            if (claimedCount <= 0) {
                player.sendSystemMessage(Component.literal("§c[Hunts] You do not have any unclaimed hunt rewards."));
                return;
            }
            for (PokemonHuntState.HuntEntry hunt : result.rewards()) grantRewards(player, hunt);
            player.sendSystemMessage(Component.literal("§a[Hunts] Claimed reward" + (claimedCount == 1 ? "" : "s") + " for §f" + claimedCount + "§a completed hunt" + (claimedCount == 1 ? "" : "s") + "."));
        }));
        return true;
    }

    private static void ensureStateCollections(PokemonHuntState state) {
        if (state.hunts == null) state.hunts = new ArrayList<>();
        if (state.pendingRewards == null) state.pendingRewards = new ArrayList<>();
    }

    private static void addPendingReward(PokemonHuntState state, PokemonHuntState.HuntEntry hunt) {
        if (state == null || hunt == null || hunt.id == null || hunt.id.isBlank()) return;
        ensureStateCollections(state);
        for (PokemonHuntState.HuntEntry existing : state.pendingRewards) {
            if (existing != null && hunt.id.equals(existing.id)) return;
        }
        state.pendingRewards.add(copyHunt(hunt));
    }

    private static void markActiveHuntRewardClaimed(PokemonHuntState state, String huntId) {
        if (state == null || huntId == null || state.hunts == null) return;
        for (PokemonHuntState.HuntEntry hunt : state.hunts) {
            if (hunt != null && huntId.equals(hunt.id)) {
                hunt.rewardClaimed = true;
                return;
            }
        }
    }

    private static PokemonHuntState.HuntEntry copyHunt(PokemonHuntState.HuntEntry source) {
        PokemonHuntState.HuntEntry copy = new PokemonHuntState.HuntEntry();
        copy.id = source.id;
        copy.species = source.species;
        copy.nature = source.nature;
        copy.gender = source.gender;
        copy.ability = source.ability;
        copy.difficulty = source.difficulty;
        copy.claimed = source.claimed;
        copy.rewardClaimed = source.rewardClaimed;
        copy.winnerUuid = source.winnerUuid;
        copy.winnerName = source.winnerName;
        copy.completedAtMillis = source.completedAtMillis;
        copy.rewards = source.rewards;
        return copy;
    }

    private static void grantRewards(ServerPlayer player, PokemonHuntState.HuntEntry hunt) {
        com.champutils.cosmetic.TitleManager.unlock(player, "hunt_helper");
        PokemonHuntConfig.Rewards rewards = hunt.rewards == null ? new PokemonHuntConfig.Rewards() : hunt.rewards;
        long credits = PokemonHuntConfig.normalizeRewardCredits(rewards.credits, hunt.difficulty);
        rewards.credits = credits;
        if (credits > 0L) {
            EconomyManager.deposit(player, credits, "Pokémon hunt reward: " + hunt.species);
            player.sendSystemMessage(Component.literal("+" + EconomyManager.format(credits)).withStyle(ChatFormatting.GOLD));
        }

        awardCrateCredit(player, hunt.difficulty);
        awardHuntChunks(player, hunt.difficulty);
        awardFragments(player, hunt.difficulty);
        AdventurerGuildManager.awardGuildActivity(player, guildXpForDifficulty(hunt.difficulty), guildMarksForDifficulty(hunt.difficulty), "Pokémon hunt");

        if (rewards.items != null) {
            for (PokemonHuntConfig.RewardItem reward : rewards.items) {
                if (reward == null) continue;
                giveRewardItem(player, reward);
            }
        }
    }

    private static void awardHuntChunks(ServerPlayer player, String difficulty) {
        String tier = PokemonHuntConfig.normalizeDifficulty(difficulty);
        switch (tier) {
            case "E" -> { ProfessionChunkManager.addChunk(player, "COBBLESTONE", 8, false); ProfessionChunkManager.addChunk(player, "COPPER", 2, false); }
            case "D" -> { ProfessionChunkManager.addChunk(player, "COBBLESTONE", 12, false); ProfessionChunkManager.addChunk(player, "COPPER", 4, false); ProfessionChunkManager.addChunk(player, "IRON", 1, false); }
            case "C" -> { ProfessionChunkManager.addChunk(player, "COBBLESTONE", 16, false); ProfessionChunkManager.addChunk(player, "COPPER", 6, false); ProfessionChunkManager.addChunk(player, "IRON", 2, false); }
            case "B" -> { ProfessionChunkManager.addChunk(player, "COBBLESTONE", 22, false); ProfessionChunkManager.addChunk(player, "COPPER", 8, false); ProfessionChunkManager.addChunk(player, "IRON", 3, false); ProfessionChunkManager.addChunk(player, "GOLD", 1, false); }
            case "A" -> { ProfessionChunkManager.addChunk(player, "COBBLESTONE", 28, false); ProfessionChunkManager.addChunk(player, "COPPER", 10, false); ProfessionChunkManager.addChunk(player, "IRON", 5, false); ProfessionChunkManager.addChunk(player, "GOLD", 2, false); }
            case "S" -> { ProfessionChunkManager.addChunk(player, "COBBLESTONE", 36, false); ProfessionChunkManager.addChunk(player, "COPPER", 14, false); ProfessionChunkManager.addChunk(player, "IRON", 7, false); ProfessionChunkManager.addChunk(player, "GOLD", 3, false); ProfessionChunkManager.addChunk(player, "DIAMOND", 1, false); }
            default -> { ProfessionChunkManager.addChunk(player, "COBBLESTONE", 6, false); ProfessionChunkManager.addChunk(player, "COPPER", 1, false); }
        }
    }

    private static void awardCrateCredit(ServerPlayer player, String difficulty) {
        if (player == null) return;
        CrateCreditManager.addCredits(player, crateIdForDifficulty(difficulty), 1);
    }

    private static void awardFragments(ServerPlayer player, String difficulty) {
        if (player == null) return;
        String rarity = crateIdForDifficulty(difficulty).toUpperCase(Locale.ROOT);
        int amount = 1 + RANDOM.nextInt(3);
        ProfessionFragmentManager.giveFragments(player, rarity, amount);
        player.sendSystemMessage(Component.literal("+" + amount + " " + PokemonHuntConfig.displayDifficulty(difficulty) + " Essence" + (amount == 1 ? "" : "s")).withStyle(ChatFormatting.LIGHT_PURPLE));
    }


    private static int guildXpForDifficulty(String difficulty) {
        return switch (PokemonHuntConfig.normalizeDifficulty(difficulty)) {
            case "E" -> 75;
            case "D" -> 110;
            case "C" -> 170;
            case "B" -> 260;
            case "A" -> 400;
            case "S" -> 650;
            default -> 50;
        };
    }

    private static int guildMarksForDifficulty(String difficulty) {
        return switch (PokemonHuntConfig.normalizeDifficulty(difficulty)) {
            case "E" -> 2;
            case "D" -> 3;
            case "C" -> 5;
            case "B" -> 7;
            case "A" -> 11;
            case "S" -> 16;
            default -> 1;
        };
    }

    public static String crateIdForDifficulty(String difficulty) {
        return switch (PokemonHuntConfig.normalizeDifficulty(difficulty)) {
            case "E" -> "e";
            case "D" -> "d";
            case "C" -> "c";
            case "B" -> "b";
            case "A" -> "a";
            case "S" -> "s";
            default -> "f";
        };
    }

    public static String displayCrateForDifficulty(String difficulty) {
        return PokemonHuntConfig.displayDifficulty(difficulty) + " Crate Credit";
    }

    public static String prettyItemId(String itemId) {
        String value = itemId == null ? "" : itemId.trim();
        int idx = value.indexOf(':');
        if (idx >= 0 && idx + 1 < value.length()) value = value.substring(idx + 1);
        return title(value.replace('_', ' ').replace('-', ' '));
    }

    private static PokemonHuntConfig.RewardItem pickReward(List<PokemonHuntConfig.RewardItem> items) {
        if (items == null || items.isEmpty()) return null;
        int total = 0;
        for (PokemonHuntConfig.RewardItem item : items) {
            if (item != null && item.weight > 0) total += item.weight;
        }
        if (total <= 0) return null;
        int roll = RANDOM.nextInt(total);
        int cursor = 0;
        for (PokemonHuntConfig.RewardItem item : items) {
            if (item == null || item.weight <= 0) continue;
            cursor += item.weight;
            if (roll < cursor) return item;
        }
        return null;
    }

    private static void giveRewardItem(ServerPlayer player, PokemonHuntConfig.RewardItem reward) {
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(reward.item));
            if (item == null || item == Items.AIR) return;
            int amount = Math.max(1, reward.min);
            int remaining = amount;
            int stackMax = Math.max(1, item.getDefaultMaxStackSize());
            while (remaining > 0) {
                int give = Math.min(stackMax, remaining);
                NpcShopService.giveOrDrop(player, new ItemStack(item, give));
                remaining -= give;
            }
            player.sendSystemMessage(Component.literal("+" + amount + "x " + prettyItemId(reward.item)).withStyle(ChatFormatting.AQUA));
        } catch (Exception ignored) {
        }
    }

    private static boolean matchesAny(String required, String actual) {
        return required == null || required.isBlank() || required.equalsIgnoreCase("any") || required.equalsIgnoreCase(actual);
    }

    private static boolean abilityMatches(String required, String actual) {
        String req = normalizeAbility(required);
        if (req.isBlank() || req.equals("any")) return true;
        return req.equals(normalizeAbility(actual));
    }

    public static String displayTarget(PokemonHuntState.HuntEntry hunt) {
        if (hunt == null) return "Unknown Pokémon";
        return prettyNature(hunt.nature) + " " + prettyGender(hunt.gender) + " " + prettyAbility(hunt.ability) + " " + prettySpecies(hunt.species);
    }

    public static String prettySpecies(String species) {
        String value = normalSpecies(species).replace('_', ' ').replace('-', ' ');
        return title(value);
    }

    public static String prettyNature(String nature) {
        return title(normalizeNature(nature));
    }

    public static String prettyGender(String gender) {
        return title(normalizeGender(gender));
    }

    public static String prettyAbility(String ability) {
        String normalized = normalizeAbility(ability);
        if (normalized.equals("any")) return "Any Ability";
        return title(normalized);
    }

    public static String normalSpecies(String species) {
        return PokemonHuntReflection.normalizeId(species);
    }

    private static String normalizeNature(String nature) {
        return nature == null ? "" : nature.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static String normalizeGender(String gender) {
        if (gender == null) return "";
        String lower = gender.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("female")) return "female";
        if (lower.contains("male")) return "male";
        if (lower.contains("genderless") || lower.contains("none") || lower.contains("unknown")) return "genderless";
        return lower.replaceAll("[^a-z]", "");
    }

    private static String normalizeAbility(String ability) {
        return PokemonHuntReflection.normalizeTrait(ability);
    }

    private static String title(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        String[] parts = value.trim().replace('_', ' ').replace('-', ' ').split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) out.append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private record HuntCompletion(PokemonHuntState state, PokemonHuntState.HuntEntry hunt) {}
    private record HuntClaimResult(PokemonHuntState state, List<PokemonHuntState.HuntEntry> rewards) {}

}
