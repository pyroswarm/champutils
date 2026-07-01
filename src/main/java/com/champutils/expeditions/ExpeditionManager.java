package com.champutils.expeditions;

import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.economy.EconomyManager;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profession.ProfessionChunkManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionType;
import com.champutils.dex.TrueCaughtDexManager;
import com.champutils.specialspawn.SpecialWildSpawnConfig;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class ExpeditionManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils/expeditions/profiles");

    private ExpeditionManager() {}

    public static void load() {
        if (!DIR.exists()) DIR.mkdirs();
        ExpeditionConfig.load();
    }

    public static boolean hasActive(ServerPlayer player) {
        return loadSave(player).active;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || server.getTickCount() % 1200 != 0) return; // once per minute; no per-tick disk spam
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Save save = loadSave(player);
            if (!save.active) continue;
            if (save.startedAt <= 0L) save.startedAt = now;
            long last = save.lastOnlineProgressAt <= 0L ? now : save.lastOnlineProgressAt;
            long delta = Math.max(0L, Math.min(120_000L, now - last));
            if (delta > 0L && now < save.endsAt) {
                // Online players earn one extra millisecond of progress per real millisecond online.
                // That makes active expeditions complete at 2x speed while the player is online.
                save.endsAt = Math.max(now, save.endsAt - delta);
            }
            save.lastOnlineProgressAt = now;
            save(player, save);
            if (save.active && now >= save.endsAt) notifyIfReady(player);
        }
    }

    static void start(ServerPlayer player, int slot, Pokemon pokemon, long endsAt) {
        start(player, slot, pokemon, endsAt, "general");
    }

    static void start(ServerPlayer player, int slot, Pokemon pokemon, long endsAt, String expeditionType) {
        Save save = loadSave(player);
        long now = System.currentTimeMillis();
        save.active = true;
        save.startedAt = now;
        save.baseDurationMillis = Math.max(0L, endsAt - now);
        save.lastOnlineProgressAt = now;
        save.endsAt = endsAt;
        save.level = pokemon.getLevel();
        save.name = pokemon.getDisplayName(true).getString();
        save.expeditionType = ExpeditionConfig.normalizeType(expeditionType);
        save.payload = AuctionPokemonSerializer.toPayload(player, pokemon).toString();
        save(player, save);
    }

    public static void notifyIfReady(ServerPlayer player) {
        Save save = loadSave(player);
        if (save.active && System.currentTimeMillis() >= save.endsAt) {
            player.sendSystemMessage(Component.literal("Your expedition is done! Use /expeditions claim to get your Pokémon and rewards.").withStyle(ChatFormatting.GOLD));
        }
    }

    public static void status(ServerPlayer player) {
        Save save = loadSave(player);
        if (!save.active) {
            player.sendSystemMessage(Component.literal("No active expedition.").withStyle(ChatFormatting.GRAY));
            return;
        }
        long remainingSeconds = remainingMillis(save) / 1000L;
        String status = remainingSeconds <= 0L ? "ready to claim" : ((remainingSeconds + 59L) / 60L) + "m remaining";
        player.sendSystemMessage(Component.literal(save.name + " expedition: " + status + " (online speed: 2x).").withStyle(ChatFormatting.AQUA));
    }

    public static String activeStatusText(ServerPlayer player) {
        Save save = loadSave(player);
        if (!save.active) return "No active expedition.";
        long remainingSeconds = remainingMillis(save) / 1000L;
        if (remainingSeconds <= 0L) return "Ready to claim";
        long minutes = (remainingSeconds + 59L) / 60L;
        if (minutes < 60L) return minutes + "m remaining · online speed 2x";
        long hours = minutes / 60L;
        long leftover = minutes % 60L;
        return hours + "h " + leftover + "m remaining · online speed 2x";
    }

    private static long remainingMillis(Save save) {
        if (save == null || !save.active) return 0L;
        return Math.max(0L, save.endsAt - System.currentTimeMillis());
    }

    public static void claim(ServerPlayer player) {
        Save save = loadSave(player);
        if (!save.active) {
            player.sendSystemMessage(Component.literal("No active expedition.").withStyle(ChatFormatting.RED));
            return;
        }
        if (remainingMillis(save) > 0L) {
            status(player);
            return;
        }

        int battlingLevel = ProfessionManager.getLevel(player, ProfessionType.BATTLING);
        List<ItemStack> rewards = ExpeditionConfig.itemStacks(save.level, battlingLevel, save.expeditionType);
        if (!canFit(player, rewards)) {
            player.sendSystemMessage(Component.literal("Make inventory space before claiming expedition rewards.").withStyle(ChatFormatting.RED));
            return;
        }

        Pokemon pokemon;
        try {
            pokemon = AuctionPokemonSerializer.fromPayload(player, JsonParser.parseString(save.payload).getAsJsonObject());
        } catch (Exception e) {
            e.printStackTrace();
            player.sendSystemMessage(Component.literal("Could not restore that expedition Pokémon. Check console before trying again.").withStyle(ChatFormatting.RED));
            return;
        }

        AuctionPokemonSerializer.DeliveryResult delivered = AuctionPokemonSerializer.deliverToPartyOrPc(player, pokemon);
        if (delivered == AuctionPokemonSerializer.DeliveryResult.FAILED) {
            player.sendSystemMessage(Component.literal("Could not return Pokémon. Make party or PC space and try again.").withStyle(ChatFormatting.RED));
            return;
        }

        ExpeditionConfig.Tier tier = ExpeditionConfig.tier(save.level);
        if (tier.credits > 0L) EconomyManager.deposit(player, tier.credits, "expedition_reward");
        for (ItemStack stack : rewards) player.getInventory().add(stack.copy());

        if ("pokemon".equals(ExpeditionConfig.normalizeType(save.expeditionType))) {
            Pokemon found = createPokemonReward(battlingLevel);
            if (found != null) {
                AuctionPokemonSerializer.DeliveryResult foundDelivery = AuctionPokemonSerializer.deliverToPartyOrPc(player, found);
                if (foundDelivery != AuctionPokemonSerializer.DeliveryResult.FAILED) {
                    TrueCaughtDexManager.markTrueCaught(player, found);
                    player.sendSystemMessage(Component.literal("Your expedition found a wild " + found.getDisplayName(true).getString() + "! It was sent to " + foundDelivery.name() + ".").withStyle(ChatFormatting.AQUA));
                }
            }
        }

        List<ExpeditionConfig.ChunkReward> chunkRewards = ExpeditionConfig.chunkRewards(save.level);
        if (!chunkRewards.isEmpty()) {
            StringBuilder chunkSummary = new StringBuilder();
            for (ExpeditionConfig.ChunkReward reward : chunkRewards) {
                String chunk = reward.chunk == null ? "" : reward.chunk.trim();
                if (chunk.isEmpty()) continue;
                int amount = Math.max(1, reward.amount);
                ProfessionChunkManager.addChunk(player, chunk, amount, false);
                if (chunkSummary.length() > 0) chunkSummary.append(", ");
                chunkSummary.append(amount).append("x ").append(chunk.toLowerCase(java.util.Locale.ROOT));
            }
            if (chunkSummary.length() > 0) {
                player.sendSystemMessage(Component.literal("Expedition chunk rewards: " + chunkSummary).withStyle(ChatFormatting.GOLD));
            }
        }

        save.active = false;
        save(player, save);
        player.sendSystemMessage(Component.literal("Expedition claimed. Pokémon returned to " + delivered.name() + ".").withStyle(ChatFormatting.GREEN));
    }

    private static boolean canFit(ServerPlayer player, List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) return true;
        int emptySlots = 0;
        for (ItemStack slot : player.getInventory().items) {
            if (slot.isEmpty()) emptySlots++;
        }
        int needed = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            needed += Math.max(1, (int) Math.ceil(stack.getCount() / (double) stack.getMaxStackSize()));
        }
        return emptySlots >= needed;
    }

    private static Save loadSave(ServerPlayer player) {
        File file = file(player);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Save save = GSON.fromJson(reader, Save.class);
                if (save != null) return save;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new Save();
    }

    private static void save(ServerPlayer player, Save save) {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            try (FileWriter writer = new FileWriter(file(player))) {
                GSON.toJson(save, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static File file(ServerPlayer player) {
        if (!DIR.exists()) DIR.mkdirs();
        return new File(DIR, PlayerProfileManager.activeProfileId(player) + ".json");
    }

    static final class Save {
        boolean active;
        long endsAt;
        long startedAt;
        long baseDurationMillis;
        long lastOnlineProgressAt;
        int level;
        String name = "";
        String payload = "";
        String expeditionType = "general";
    }

    private static Pokemon createPokemonReward(int battlingLevel) {
        int level = Math.max(1, Math.min(100, battlingLevel));
        double roll = ThreadLocalRandom.current().nextDouble();
        String species;
        if (level >= 100 && roll < 0.025D) {
            species = randomFrom(SpecialWildSpawnConfig.DATA.legendarySpawns, SpecialWildSpawnConfig.DATA.mythicalSpawns);
        } else if (level >= 100 && roll < 0.075D) {
            species = randomFrom(SpecialWildSpawnConfig.DATA.paradoxSpawns, SpecialWildSpawnConfig.DATA.ultraBeastSpawns);
        } else if (level >= 75 && roll < 0.03D) {
            species = randomCommon("dratini", "larvitar", "bagon", "beldum", "gible", "goomy", "dreepy", "frigibax");
        } else if (level >= 45 && roll < 0.10D) {
            species = randomCommon("eevee", "riolu", "ralts", "rotom", "zorua", "ditto", "togepi", "munchlax");
        } else {
            species = randomCommon("pidgey", "rattata", "magikarp", "shinx", "starly", "bunnelby", "mareep", "wooper", "machop", "gastly");
        }
        if (species == null || species.isBlank()) return null;
        try {
            String clean = species.contains(":") ? species : "cobblemon:" + species;
            int pokemonLevel = Math.max(5, Math.min(70, 5 + level / 2));
            return PokemonProperties.Companion.parse("species=\"" + clean + "\" level=" + pokemonLevel).create();
        } catch (Throwable throwable) {
            return null;
        }
    }

    @SafeVarargs
    private static String randomFrom(List<SpecialWildSpawnConfig.SpawnEntry>... lists) {
        List<String> species = new ArrayList<>();
        if (lists != null) {
            for (List<SpecialWildSpawnConfig.SpawnEntry> list : lists) {
                if (list == null) continue;
                for (SpecialWildSpawnConfig.SpawnEntry entry : list) {
                    if (entry != null && entry.species != null && !entry.species.isBlank()) species.add(entry.species);
                }
            }
        }
        if (species.isEmpty()) return null;
        return species.get(ThreadLocalRandom.current().nextInt(species.size()));
    }

    private static String randomCommon(String... species) {
        if (species == null || species.length == 0) return "magikarp";
        return species[ThreadLocalRandom.current().nextInt(species.length)];
    }
}
