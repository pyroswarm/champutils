package com.champutils.rank;

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

    public static void registerCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("claimseasonrewards")
                        .executes(ctx -> {
                            try {
                                return claim(ctx.getSource().getPlayerOrException());
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

        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID profileId = PlayerProfileManager.activeProfileId(player);
                PlayerDataManager.PlayerData data = PlayerDataManager.load(player.getUUID(), player.getName().getString());
                snapshotPlayer(safeSeason, seasonName, profileId.toString(), player.getName().getString(), data);
            }
        }
        save();
    }

    private static void snapshotPlayer(int season, String seasonName, String profileId, String playerName, PlayerDataManager.PlayerData data) {
        int games = Math.max(0, data.rankedWins) + Math.max(0, data.rankedLosses);
        if (games < MIN_RANKED_GAMES) return;
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

    public static synchronized int claim(ServerPlayer player) {
        load();
        int season = Math.max(0, SeasonManager.CURRENT_SEASON - 1);
        if (season <= 0) {
            player.sendSystemMessage(Component.literal("No completed season rewards are available yet.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        String profileId = PlayerProfileManager.activeProfileId(player).toString();
        String key = key(profileId, season);
        if (STATE.claimedAt.containsKey(key)) {
            player.sendSystemMessage(Component.literal("You already claimed your Season " + season + " rewards on this profile.").withStyle(ChatFormatting.RED));
            return 0;
        }
        SeasonRewardSnapshot snapshot = STATE.rewards.get(key);
        if (snapshot == null) {
            player.sendSystemMessage(Component.literal("No Season " + season + " rewards found. You need at least " + MIN_RANKED_GAMES + " ranked games in that season.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        STATE.claimedAt.put(key, Instant.now().toString());
        save();

        for (RewardEntry reward : snapshot.rewards) grant(player, reward, season);
        player.sendSystemMessage(Component.literal("Claimed Season " + season + " rewards for peak rating " + snapshot.peakRp + " with " + snapshot.rankedGames + " ranked games.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static List<RewardEntry> rewardsForRating(int rating) {
        List<RewardEntry> out = new ArrayList<>();
        if (rating <= 300) {
            money(out, 100); crate(out, "common", 3); item(out, "cobblemon:poke_ball", 16); item(out, "cobblemon:potion", 8); tm(out, "COMMON", 1);
        } else if (rating < 600) {
            money(out, 250); crate(out, "common", 4); crate(out, "uncommon", 1); item(out, "cobblemon:great_ball", 12); item(out, "cobblemon:exp_candy_s", 6); tm(out, "UNCOMMON", 1);
        } else if (rating < 900) {
            money(out, 600); crate(out, "common", 3); crate(out, "uncommon", 3); crate(out, "rare", 1); item(out, "cobblemon:ultra_ball", 10); item(out, "cobblemon:rare_candy", 2); tm(out, "RARE", 1);
        } else if (rating < 1200) {
            money(out, 1400); crate(out, "uncommon", 3); crate(out, "rare", 3); crate(out, "epic", 1); item(out, "cobblemon:ability_capsule", 1); item(out, "genesisforms:mega_bracelet", 1); tm(out, "EPIC", 1);
        } else if (rating < 1500) {
            money(out, 3500); crate(out, "rare", 3); crate(out, "epic", 3); crate(out, "legendary", 1); item(out, "cobblemon:ability_patch", 1); item(out, "genesisforms:tera_orb", 1); item(out, "cobblemon:master_ball", 1); tm(out, "LEGENDARY", 1);
        } else {
            money(out, 10000); crate(out, "epic", 3); crate(out, "legendary", 2); crate(out, "mythic", 1); item(out, "cobblemon:ability_patch", 2); item(out, "cobblemon:master_ball", 2); item(out, "genesisforms:tera_orb", 1); item(out, "genesisforms:adamant_crystal", 1); item(out, "genesisforms:lustrous_globe", 1); item(out, "genesisforms:griseous_core", 1); tm(out, "MYTHIC", 2);
        }
        return out;
    }

    private static void money(List<RewardEntry> out, int credits) { out.add(new RewardEntry("MONEY", "credits", Math.max(1, credits) * 100)); }
    private static void crate(List<RewardEntry> out, String id, int amount) { out.add(new RewardEntry("CRATE", id, amount)); }
    private static void item(List<RewardEntry> out, String id, int amount) { out.add(new RewardEntry("ITEM", id, amount)); }
    private static void tm(List<RewardEntry> out, String rarity, int amount) { /* TMs are shop-only now. */ }

    private static void grant(ServerPlayer player, RewardEntry reward, int season) {
        if (reward == null || reward.amount <= 0) return;
        String type = reward.type == null ? "" : reward.type.toUpperCase(Locale.ROOT);
        switch (type) {
            case "MONEY" -> EconomyManager.deposit(player, reward.amount, "Season " + season + " reward");
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
        if (loaded) return;
        loaded = true;
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) { save(); return; }
            try (FileReader reader = new FileReader(FILE)) {
                RewardState read = GSON.fromJson(reader, RewardState.class);
                STATE = read == null ? new RewardState() : read;
                if (STATE.rewards == null) STATE.rewards = new LinkedHashMap<>();
                if (STATE.claimedAt == null) STATE.claimedAt = new LinkedHashMap<>();
            }
        } catch (Exception e) {
            e.printStackTrace();
            STATE = new RewardState();
        }
    }

    private static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(STATE, writer); }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
