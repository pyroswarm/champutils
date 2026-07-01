package com.champutils.shop;

import com.champutils.profession.ProfessionToolManager;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfileGameMode;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;


import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;

public final class FirstJoinKitManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "first_join_claims.json");
    private static final Random RANDOM = new Random();

    private static ClaimRoot DATA = new ClaimRoot();

    private FirstJoinKitManager() {
    }

    private static final class ClaimRoot {
        Set<String> claimed = new HashSet<>();
    }

    public static void load() {
        FirstJoinKitConfig.load();

        try {
            if (!DIR.exists()) DIR.mkdirs();
            if (!FILE.exists()) {
                DATA = new ClaimRoot();
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                ClaimRoot loaded = GSON.fromJson(reader, ClaimRoot.class);
                DATA = loaded == null ? new ClaimRoot() : loaded;
            }

            if (DATA.claimed == null) DATA.claimed = new HashSet<>();
        } catch (Exception exception) {
            exception.printStackTrace();
            DATA = new ClaimRoot();
        }
    }

    public static void save() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(DATA, writer);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public static void handleJoin(ServerPlayer player) {
        if (player == null || !FirstJoinKitConfig.CONFIG.enabled) {
            return;
        }

        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null) {
            // Do not consume the starter kit in the lobby or during profile hydration.
            return;
        }
        String oldProfileKey = profileId.toString();
        String profileKey = "profile:" + profileId;
        String playerProfileKey = "player:" + player.getUUID() + ":profile:" + profileId;

        if (DATA.claimed.contains(oldProfileKey) || DATA.claimed.contains(profileKey) || DATA.claimed.contains(playerProfileKey)) {
            DATA.claimed.add(profileKey);
            DATA.claimed.add(playerProfileKey);
            save();
            return;
        }

        // Mark every stable key before giving anything so reconnects/profile reloads cannot double-claim.
        DATA.claimed.add(profileKey);
        DATA.claimed.add(playerProfileKey);
        save();

        for (FirstJoinKitConfig.KitEntry entry : FirstJoinKitConfig.CONFIG.entries) {
            give(player, entry);
        }

        if (PlayerProfileManager.gameMode(player) == ProfileGameMode.ISLANDER) {
            for (FirstJoinKitConfig.KitEntry entry : FirstJoinKitConfig.CONFIG.islanderEntries) {
                give(player, entry);
            }
        }

        player.sendSystemMessage(Component.literal("Welcome! This profile's starter kit has been added to your inventory.").withStyle(ChatFormatting.GREEN));
    }

    private static void give(ServerPlayer player, FirstJoinKitConfig.KitEntry entry) {
        if (entry == null) return;

        switch (normalize(entry.type)) {
            case "tool" -> giveTool(player, entry);
            case "command" -> runCommands(player, entry.commands);
            case "item" -> giveItem(player, entry.id, entry.amount);
            default -> {
            }
        }
    }

    private static void giveItem(ServerPlayer player, String id, int amount) {
        Item item;
        try {
            item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        } catch (Exception exception) {
            return;
        }

        if (item == null || item == Items.AIR) {
            return;
        }

        int remaining = Math.max(1, amount);
        int max = Math.max(1, item.getDefaultMaxStackSize());
        while (remaining > 0) {
            int give = Math.min(max, remaining);
            NpcShopService.giveOrDrop(player, new ItemStack(item, give));
            remaining -= give;
        }
    }

    private static void giveTool(ServerPlayer player, FirstJoinKitConfig.KitEntry entry) {
        List<String> candidates = NpcShopService.findToolCandidates(entry.rarity, entry.toolType);
        if (candidates.isEmpty()) {
            return;
        }

        String selected = candidates.get(RANDOM.nextInt(candidates.size()));
        ItemStack stack = ProfessionToolManager.createTool(selected, false);
        if (!stack.isEmpty()) {
            NpcShopService.giveOrDrop(player, stack);
        }
    }

    private static void runCommands(ServerPlayer player, List<String> commands) {
        if (player == null || player.getServer() == null || commands == null) {
            return;
        }

        for (String raw : commands) {
            if (raw == null || raw.isBlank()) continue;
            String command = raw.replace("%player%", player.getName().getString());
            player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack(), command);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
