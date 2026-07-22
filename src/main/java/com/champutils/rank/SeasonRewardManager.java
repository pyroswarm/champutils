package com.champutils.rank;

import com.champutils.database.SharedJsonStateRepository;
import com.champutils.crate.CrateCreditManager;
import com.champutils.economy.EconomyManager;
import com.champutils.profile.PlayerDataManager;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.shop.NpcShopService;
import com.champutils.tm.TMManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SeasonRewardManager {
    public static final int MIN_RANKED_GAMES = 25;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/season_reward_claims.json");
    private static final File CONFIG_FILE = new File("config/champutils/season_rewards.json");
    private static SeasonRewardsConfig REWARD_CONFIG = new SeasonRewardsConfig();
    private static final String STATE_KEY = "season_reward_claims";
    private static RewardState STATE = new RewardState();
    private static boolean loaded = false;

    private SeasonRewardManager() {}

    public static final class RewardState {
        public Map<String, SeasonRewardSnapshot> rewards = new LinkedHashMap<>();
        public Map<String, String> claimedAt = new LinkedHashMap<>();
    }

    public static final class SeasonRewardSnapshot {
        public int season;
        public String seasonName;
        public String profileId;
        public String playerName;
        public int finalRp;
        public int peakRp;
        public int rankedGames;
        public List<RewardEntry> rewards = new ArrayList<>();
    }

    public static final class RewardEntry {
        public String type;
        public String id;
        public int amount;
        public RewardEntry() {}
        public RewardEntry(String type, String id, int amount) {
            this.type = type;
            this.id = id;
            this.amount = amount;
        }
    }

    public static final class SeasonRewardsConfig {
        public int minimumRankedGames = 25;
        public List<RewardTier> tiers = new ArrayList<>();
    }

    public static final class RewardTier {
        public String id;
        public String displayName;
        public int minRp;
        public Integer maxRp;
        public List<RewardEntry> rewards = new ArrayList<>();
    }

    public static void registerCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("claimseasonrewards")
                        .executes(ctx -> {
                            try {
                                return claimLatest(ctx.getSource().getPlayerOrException());
                            } catch (Exception e) {
                                ctx.getSource().sendFailure(Component.literal("Only players can claim season rewards."));
                                return 0;
                            }
                        })
        ));
    }

    public static synchronized void prepareClaimableRewards(MinecraftServer server, int endedSeason) {
        load();
        int safeSeason = Math.max(0, endedSeason);
        if (safeSeason <= 0) return;
        String seasonName = SeasonManager.CURRENT_NAME == null || SeasonManager.CURRENT_NAME.isBlank() ? "Season " + safeSeason : SeasonManager.CURRENT_NAME;

        for (PlayerDataManager.OfflinePlayerEntry entry : PlayerDataManager.getAllProfilePlayers()) {
            if (entry == null || entry.data == null) continue;
            PlayerDataManager.PlayerData data = entry.data;
            String profileId = entry.uuid != null && !entry.uuid.isBlank() ? entry.uuid : data.uuid;
            if (profileId == null || profileId.isBlank()) continue;
            snapshotPlayer(safeSeason, seasonName, profileId, entry.name, data);
        }

        save();
    }

    private static void snapshotPlayer(int season, String seasonName, String profileId, String playerName, PlayerDataManager.PlayerData data) {
        int games = Math.max(0, data.rankedWins) + Math.max(0, data.rankedLosses);
        if (games < minimumRankedGames()) return;
        int rating = Math.max(data.rp, data.peakRp);
        SeasonRewardSnapshot snapshot = new SeasonRewardSnapshot();
        snapshot.season = season;
        snapshot.seasonName = seasonName;
        snapshot.profileId = profileId;
        snapshot.playerName = playerName == null || playerName.isBlank() ? data.name : playerName;
        snapshot.finalRp = data.rp;
        snapshot.peakRp = data.peakRp;
        snapshot.rankedGames = games;
        snapshot.rewards = rewardsForRating(rating);
        if (snapshot.rewards == null || snapshot.rewards.isEmpty()) return;
        STATE.rewards.put(key(profileId, season), snapshot);
    }

    public static int claimLatest(ServerPlayer player) {
        return claim(player, Math.max(0, SeasonManager.CURRENT_SEASON - 1));
    }

    public static int claim(ServerPlayer player, int season) {
        load();
        final int targetSeason = Math.max(0, season);
        if (targetSeason <= 0) {
            player.sendSystemMessage(Component.literal("No completed season rewards are available yet.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        UUID profileUuid = PlayerProfileManager.activeProfileId(player);
        String profileId = profileUuid.toString();
        String rewardKey = key(profileId, targetSeason);

        SharedJsonStateRepository.mutateGlobalAsync(STATE_KEY, RewardState.class, STATE, state -> {
            if (state.rewards == null) state.rewards = new LinkedHashMap<>();
            if (state.claimedAt == null) state.claimedAt = new LinkedHashMap<>();
            if (state.claimedAt.containsKey(rewardKey)) {
                return new ClaimMutation(state, null, "You already claimed your Season " + targetSeason + " rewards on this profile.");
            }
            SeasonRewardSnapshot snapshot = state.rewards.get(rewardKey);
            if (snapshot == null) {
                return new ClaimMutation(state, null, "No Season " + targetSeason + " rewards found. You need at least " + minimumRankedGames() + " ranked games in that season.");
            }
            state.claimedAt.put(rewardKey, Instant.now().toString());
            return new ClaimMutation(state, snapshot, "");
        }).whenComplete((result, error) -> player.server.execute(() -> {
            if (!com.champutils.teleport.SafeTeleportManager.isLive(player)
                    || !profileUuid.equals(PlayerProfileManager.activeProfileId(player))) return;
            if (error != null || result == null) {
                player.sendSystemMessage(Component.literal("Could not claim season rewards right now.").withStyle(ChatFormatting.RED));
                if (error != null) error.printStackTrace();
                return;
            }
            synchronized (SeasonRewardManager.class) {
                STATE = result.state();
                saveLocalMirror();
            }
            if (result.snapshot() == null) {
                player.sendSystemMessage(Component.literal(result.message()).withStyle(ChatFormatting.YELLOW));
                return;
            }
            for (RewardEntry reward : result.snapshot().rewards) grant(player, reward, targetSeason);
            com.champutils.config.Rank finishRank = com.champutils.rank.RankManager.getRank(result.snapshot().finalRp);
            if (finishRank != null && finishRank.name != null) {
                String rankId = finishRank.name.toLowerCase(java.util.Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
                com.champutils.cosmetic.TitleManager.unlock(player, "season_finish_" + rankId);
            }
            player.sendSystemMessage(Component.literal("Claimed Season " + targetSeason + " rewards for peak rating " + result.snapshot().peakRp + " with " + result.snapshot().rankedGames + " ranked games.").withStyle(ChatFormatting.GREEN));
        }));
        return 1;
    }

    public static synchronized List<SeasonRewardSnapshot> snapshotsFor(ServerPlayer player) {
        load();
        UUID profile = PlayerProfileManager.activeProfileId(player);
        List<SeasonRewardSnapshot> out = new ArrayList<>();
        for (SeasonRewardSnapshot snap : STATE.rewards.values()) {
            if (snap != null && profile.toString().equals(snap.profileId)) out.add(snap);
        }
        out.sort((a,b) -> Integer.compare(b.season, a.season));
        return out;
    }

    public static synchronized boolean isClaimed(ServerPlayer player, int season) {
        load();
        return STATE.claimedAt.containsKey(key(PlayerProfileManager.activeProfileId(player).toString(), season));
    }

    private static List<RewardEntry> rewardsForRating(int rating) {
        loadRewardConfig();
        RewardTier best = null;
        for (RewardTier tier : REWARD_CONFIG.tiers) {
            if (tier == null || tier.rewards == null) continue;
            if (rating < tier.minRp) continue;
            if (tier.maxRp != null && rating > tier.maxRp) continue;
            if (best == null || tier.minRp > best.minRp) best = tier;
        }
        if (best == null) return new ArrayList<>();
        List<RewardEntry> out = new ArrayList<>();
        for (RewardEntry entry : best.rewards) {
            if (entry == null || entry.amount <= 0) continue;
            out.add(new RewardEntry(entry.type, entry.id, entry.amount));
        }
        return out;
    }

    private static int minimumRankedGames() {
        loadRewardConfig();
        return Math.max(0, REWARD_CONFIG.minimumRankedGames);
    }

    private static synchronized void loadRewardConfig() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!CONFIG_FILE.exists()) {
                REWARD_CONFIG = defaultRewardConfig();
                saveRewardConfig();
                return;
            }
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                SeasonRewardsConfig loaded = GSON.fromJson(reader, SeasonRewardsConfig.class);
                REWARD_CONFIG = loaded == null ? defaultRewardConfig() : loaded;
            }
            normalizeRewardConfig();
            saveRewardConfig();
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load season_rewards.json; using defaults.");
            e.printStackTrace();
            REWARD_CONFIG = defaultRewardConfig();
        }
    }

    private static void normalizeRewardConfig() {
        if (REWARD_CONFIG == null) REWARD_CONFIG = defaultRewardConfig();
        if (REWARD_CONFIG.tiers == null || REWARD_CONFIG.tiers.isEmpty()) REWARD_CONFIG.tiers = defaultRewardConfig().tiers;
        for (RewardTier tier : REWARD_CONFIG.tiers) {
            if (tier != null && tier.rewards == null) tier.rewards = new ArrayList<>();
        }
    }

    private static void saveRewardConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) { GSON.toJson(REWARD_CONFIG, writer); }
        catch (Exception e) { System.err.println("[ChampUtils] Failed to save season_rewards.json: " + e.getMessage()); }
    }

    private static SeasonRewardsConfig defaultRewardConfig() {
        SeasonRewardsConfig cfg = new SeasonRewardsConfig();
        cfg.minimumRankedGames = MIN_RANKED_GAMES;
        cfg.tiers.add(tier("rookie", "300 RP or below", 0, 300,
                reward("MONEY", "credits", 500000), reward("CRATE", "d", 1), reward("ITEM", "cobblemon:great_ball", 16), reward("ITEM", "cobblemon:exp_candy_s", 8)));
        cfg.tiers.add(tier("bronze", "301-599 RP", 301, 599,
                reward("MONEY", "credits", 1000000), reward("CRATE", "d", 2), reward("ITEM", "cobblemon:ultra_ball", 16), reward("ITEM", "cobblemon:rare_candy", 3)));
        cfg.tiers.add(tier("silver", "600-899 RP", 600, 899,
                reward("MONEY", "credits", 2000000), reward("CRATE", "c", 2), reward("ITEM", "cobblemon:ability_capsule", 2), reward("ITEM", "cobblemon:rare_candy", 6)));
        cfg.tiers.add(tier("gold", "900-1199 RP", 900, 1199,
                reward("MONEY", "credits", 3500000), reward("CRATE", "b", 2), reward("CRATE", "a", 1), reward("ITEM", "cobblemon:ability_patch", 1), reward("ITEM", "cobblemon:master_ball", 1)));
        cfg.tiers.add(tier("grand_master", "1200-1499 RP", 1200, 1499,
                reward("MONEY", "credits", 6000000), reward("CRATE", "a", 2), reward("CRATE", "s", 1), reward("ITEM", "cobblemon:ability_patch", 1), reward("ITEM", "cobblemon:master_ball", 1)));
        cfg.tiers.add(tier("monarch", "Monarch", 1500, null,
                reward("MONEY", "credits", 10000000), reward("CRATE", "s", 2), reward("ITEM", "cobblemon:master_ball", 2), reward("ITEM", "cobblemon:ability_patch", 2), reward("ITEM", "cobblemon:rare_candy", 16), reward("ITEM", "cobblemon:exp_candy_xl", 16)));
        return cfg;
    }

    private static RewardTier tier(String id, String name, int min, Integer max, RewardEntry... rewards) {
        RewardTier tier = new RewardTier(); tier.id=id; tier.displayName=name; tier.minRp=min; tier.maxRp=max;
        tier.rewards = new ArrayList<>(java.util.Arrays.asList(rewards)); return tier;
    }
    private static RewardEntry reward(String type, String id, int amount) { return new RewardEntry(type, id, amount); }

    private static void money(List<RewardEntry> out, int credits) { out.add(new RewardEntry("MONEY", "credits", Math.max(1, credits) * 100)); }
    private static void crate(List<RewardEntry> out, String id, int amount) { out.add(new RewardEntry("CRATE", id, amount)); }
    private static void item(List<RewardEntry> out, String id, int amount) { out.add(new RewardEntry("ITEM", id, amount)); }
    private static void tm(List<RewardEntry> out, String rarity, int amount) { /* TMs are shop-only now. */ }

    private static void grant(ServerPlayer player, RewardEntry reward, int season) {
        if (reward == null || reward.amount <= 0) return;
        String type = reward.type == null ? "" : reward.type.toUpperCase(Locale.ROOT);
        switch (type) {
            case "MONEY" -> EconomyManager.depositAsync(player, reward.amount, "Season " + season + " reward");
            case "CRATE" -> CrateCreditManager.addCredits(player, reward.id, reward.amount);
            case "TM" -> {
                for (int i = 0; i < reward.amount; i++) {
                    ItemStack stack = TMManager.createRandomTMStack(reward.id, 1);
                    if (!stack.isEmpty()) NpcShopService.giveOrDrop(player, stack);
                }
            }
            case "ITEM" -> giveItem(player, reward.id, reward.amount);
        }
    }

    private static void giveItem(ServerPlayer player, String itemId, int amount) {
        if (itemId == null || itemId.isBlank()) return;
        Item item;
        try { item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId)); } catch (Exception e) { item = Items.AIR; }
        if (item == null || item == Items.AIR) {
            player.sendSystemMessage(Component.literal("Season reward item unavailable: " + itemId).withStyle(ChatFormatting.GRAY));
            return;
        }
        NpcShopService.giveOrDrop(player, new ItemStack(item, Math.max(1, amount)));
    }

    private static String key(String profileId, int season) { return profileId + "|" + season; }

    private static void load() {
        loadRewardConfig();
        if (loaded) return;
        loaded = true;
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) {
                save();
            }
            else {
                try (FileReader reader = new FileReader(FILE)) {
                    RewardState read = GSON.fromJson(reader, RewardState.class);
                    STATE = read == null ? new RewardState() : read;
                    if (STATE.rewards == null) STATE.rewards = new LinkedHashMap<>();
                    if (STATE.claimedAt == null) STATE.claimedAt = new LinkedHashMap<>();
                }
            }
            STATE = SharedJsonStateRepository.loadGlobal(STATE_KEY, RewardState.class, STATE);
            if (STATE.rewards == null) STATE.rewards = new LinkedHashMap<>();
            if (STATE.claimedAt == null) STATE.claimedAt = new LinkedHashMap<>();
        } catch (Exception e) {
            e.printStackTrace();
            STATE = new RewardState();
        }
    }

    private static void save() {
        saveLocalMirror();
        SharedJsonStateRepository.saveGlobal(STATE_KEY, STATE);
    }

    private static void saveLocalMirror() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(STATE, writer); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private record ClaimMutation(RewardState state, SeasonRewardSnapshot snapshot, String message) {}
}
