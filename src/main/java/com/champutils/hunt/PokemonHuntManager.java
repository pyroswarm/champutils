package com.champutils.hunt;

import com.champutils.economy.EconomyManager;
import com.champutils.crate.CrateCreditManager;
import com.champutils.profession.ProfessionFragmentManager;
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

    private PokemonHuntManager() {}

    public static synchronized void load() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            if (!FILE.exists()) {
                STATE = new PokemonHuntState();
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                PokemonHuntState loaded = GSON.fromJson(reader, PokemonHuntState.class);
                STATE = loaded == null ? new PokemonHuntState() : loaded;
            }
            if (STATE.hunts == null) STATE.hunts = new ArrayList<>();
            if (STATE.pendingRewards == null) STATE.pendingRewards = new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            STATE = new PokemonHuntState();
        }
    }

    public static synchronized void save() {
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
        long now = System.currentTimeMillis();
        if (STATE.hunts == null || STATE.hunts.isEmpty() || STATE.nextRefreshAtMillis <= 0L) {
            refresh(server, false);
            return;
        }
        if (now >= STATE.nextRefreshAtMillis) refresh(server, true);
    }

    public static synchronized void tick(MinecraftServer server) {
        if (server == null || server.getTickCount() % 20 != 0) return;
        ensureStarted(server);
    }

    public static synchronized void forceRefresh(MinecraftServer server) {
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
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§b[Hunts] §fA new group of Pokémon hunts is available! Use §e/hunts§f."),
                    false
            );
        }
    }

    private static PokemonHuntState.HuntEntry createEntry(PokemonHuntConfig.HuntTarget target) {
        PokemonHuntState.HuntEntry entry = new PokemonHuntState.HuntEntry();
        entry.id = UUID.randomUUID().toString();
        entry.species = normalSpecies(target.species);
        entry.nature = pick(target.natures, "jolly").toLowerCase(Locale.ROOT);
        entry.gender = normalizeGender(pick(target.genders, "male"));
        entry.ability = normalizeAbility(pick(target.abilities, "any"));
        entry.difficulty = target.difficulty == null ? "COMMON" : target.difficulty.trim().toUpperCase(Locale.ROOT);
        entry.rewards = target.rewards == null ? new PokemonHuntConfig.Rewards() : target.rewards;
        return entry;
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

    public static synchronized void handleCatch(ServerPlayer player, Object pokemon) {
        if (player == null || pokemon == null) return;
        if (!PokemonHuntConfig.DATA.settings.enabled) return;
        if (STATE.hunts == null || STATE.hunts.isEmpty()) return;

        String species = normalSpecies(PokemonHuntReflection.speciesId(pokemon));
        String nature = PokemonHuntReflection.natureName(pokemon).toLowerCase(Locale.ROOT);
        String gender = normalizeGender(PokemonHuntReflection.genderName(pokemon));
        String ability = normalizeAbility(PokemonHuntReflection.abilityName(pokemon));

        for (PokemonHuntState.HuntEntry hunt : STATE.hunts) {
            if (hunt == null || hunt.claimed) continue;
            if (!normalSpecies(hunt.species).equals(species)) continue;
            if (!matchesAny(normalizeNature(hunt.nature), normalizeNature(nature))) continue;
            if (!matchesAny(normalizeGender(hunt.gender), gender)) continue;
            if (!abilityMatches(hunt.ability, ability)) continue;

            completeHunt(player, hunt);
            return;
        }
    }

    private static void completeHunt(ServerPlayer player, PokemonHuntState.HuntEntry hunt) {
        hunt.claimed = true;
        hunt.winnerUuid = player.getUUID().toString();
        hunt.winnerName = player.getName().getString();
        hunt.completedAtMillis = System.currentTimeMillis();
        hunt.rewardClaimed = false;
        addPendingReward(hunt);
        save();

        String target = displayTarget(hunt);
        player.sendSystemMessage(Component.literal("§a[Hunts] You completed the hunt for §e" + target + "§a! Use §f/hunts claim§a to claim your reward."));
        if (PokemonHuntConfig.DATA.settings.announceWinners && player.server != null) {
            player.server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§b[Hunts] §f" + player.getName().getString() + " caught the hunted §e" + target + "§f! Use §e/hunts claim§f to claim the reward."),
                    false
            );
        }
    }

    public static synchronized boolean claimRewards(ServerPlayer player) {
        if (player == null) return false;
        if (STATE.pendingRewards == null) STATE.pendingRewards = new ArrayList<>();

        int claimedCount = 0;
        String playerUuid = player.getUUID().toString();
        List<PokemonHuntState.HuntEntry> remaining = new ArrayList<>();
        for (PokemonHuntState.HuntEntry hunt : STATE.pendingRewards) {
            if (hunt == null || hunt.rewardClaimed || !playerUuid.equals(hunt.winnerUuid)) {
                if (hunt != null && !hunt.rewardClaimed) remaining.add(hunt);
                continue;
            }

            grantRewards(player, hunt);
            hunt.rewardClaimed = true;
            markActiveHuntRewardClaimed(hunt.id);
            claimedCount++;
        }
        STATE.pendingRewards = remaining;

        if (claimedCount > 0) {
            save();
            player.sendSystemMessage(Component.literal("§a[Hunts] Claimed reward" + (claimedCount == 1 ? "" : "s") + " for §f" + claimedCount + "§a completed hunt" + (claimedCount == 1 ? "" : "s") + "."));
            return true;
        }

        player.sendSystemMessage(Component.literal("§c[Hunts] You do not have any unclaimed hunt rewards."));
        return false;
    }

    private static void addPendingReward(PokemonHuntState.HuntEntry hunt) {
        if (hunt == null || hunt.id == null || hunt.id.isBlank()) return;
        if (STATE.pendingRewards == null) STATE.pendingRewards = new ArrayList<>();
        for (PokemonHuntState.HuntEntry existing : STATE.pendingRewards) {
            if (existing != null && hunt.id.equals(existing.id)) return;
        }
        STATE.pendingRewards.add(copyHunt(hunt));
    }

    private static void markActiveHuntRewardClaimed(String huntId) {
        if (huntId == null || STATE.hunts == null) return;
        for (PokemonHuntState.HuntEntry hunt : STATE.hunts) {
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
        long credits = Math.max(0L, rewards.credits);
        if (credits > 0L) {
            EconomyManager.deposit(player, credits, "Pokémon hunt reward: " + hunt.species);
            player.sendSystemMessage(Component.literal("+" + EconomyManager.format(credits)).withStyle(ChatFormatting.GOLD));
        }

        awardCrateCredit(player, hunt.difficulty);
        awardFragments(player, hunt.difficulty);

        if (rewards.items != null) {
            for (PokemonHuntConfig.RewardItem reward : rewards.items) {
                if (reward == null) continue;
                giveRewardItem(player, reward);
            }
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
        player.sendSystemMessage(Component.literal("+" + amount + " " + displayCrateForDifficulty(difficulty) + " Fragment" + (amount == 1 ? "" : "s")).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    public static String crateIdForDifficulty(String difficulty) {
        String value = difficulty == null ? "" : difficulty.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return switch (value) {
            case "uncommon" -> "uncommon";
            case "rare" -> "rare";
            case "epic" -> "epic";
            case "legendary" -> "legendary";
            case "mythic" -> "mythic";
            default -> "common";
        };
    }

    public static String displayCrateForDifficulty(String difficulty) {
        return title(crateIdForDifficulty(difficulty)) + " Crate Credit";
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
}
