package com.champutils.menu;

import com.champutils.leaderboard.ProfileLeaderboardRepository;
import com.champutils.leaderboard.ProfileLeaderboardRepository.Board;
import com.champutils.leaderboard.ProfileLeaderboardRepository.Entry;
import com.champutils.profession.ProfessionType;

import com.mojang.authlib.GameProfile;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LeaderboardMenu {
    private static final Map<UUID, GameProfile> TEXTURE_PROFILE_CACHE = new ConcurrentHashMap<>();

    public static void open(ServerPlayer player) {
        open(player, Board.RANKED);
    }

    public static void openProfessionOverall(ServerPlayer player) {
        open(player, Board.PROFESSIONS_OVERALL);
    }

    public static void openEconomy(ServerPlayer player) {
        open(player, Board.ECONOMY);
    }

    public static void openProfession(ServerPlayer player, ProfessionType type) {
        open(player, switch (type) {
            case MINING -> Board.PROFESSIONS_MINING;
            case FORESTRY -> Board.PROFESSIONS_FORESTRY;
            case FARMING -> Board.PROFESSIONS_FARMING;
            case BATTLING -> Board.PROFESSIONS_BATTLING;
            default -> Board.PROFESSIONS_OVERALL;
        });
    }

    private static void open(ServerPlayer player, Board board) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setTitle(Component.literal("Leaderboards - " + title(board)));

        MenuUtil.fillBorders(gui,
                0,1,2,3,4,5,6,7,8,
                9,17,18,26,27,35,36,44,
                45,46,47,48,49,50,51,52,53
        );

        tab(gui, player, 0, Items.NETHER_STAR, "§6Ranked", board, Board.RANKED);
        tab(gui, player, 1, Items.EXPERIENCE_BOTTLE, "§aProfessions", board, Board.PROFESSIONS_OVERALL);
        tab(gui, player, 2, Items.DIAMOND_PICKAXE, "§bMining", board, Board.PROFESSIONS_MINING);
        tab(gui, player, 3, Items.DIAMOND_AXE, "§bForestry", board, Board.PROFESSIONS_FORESTRY);
        tab(gui, player, 4, Items.DIAMOND_HOE, "§bFarming", board, Board.PROFESSIONS_FARMING);
        tab(gui, player, 5, Items.DIAMOND_SWORD, "§6Battle", board, Board.PROFESSIONS_BATTLING);
        tab(gui, player, 6, Items.EMERALD, "§aEconomy", board, Board.ECONOMY);
        tab(gui, player, 7, Items.WHITE_BANNER, "§fGuilds", board, Board.GUILDS);

        // Profile-specific leaderboards live together in the lower-right corner.
        tab(gui, player, 48, Items.CLOCK, "§ePlaytime", board, Board.PLAYTIME);
        tab(gui, player, 49, Items.BOOK, "§dPokédex", board, Board.POKEDEX);
        tab(gui, player, 50, Items.SHIELD, "§cGyms", board, Board.GYMS);
        tab(gui, player, 51, Items.DRAGON_HEAD, "§5Nuzlocke", board, Board.NUZLOCKE);
        tab(gui, player, 52, Items.GRASS_BLOCK, "§2Islander", board, Board.ISLANDER);

        List<Entry> rows = ProfileLeaderboardRepository.topFresh(board, 28);
        int[] slots = contentSlots();
        for (int i = 0; i < rows.size() && i < slots.length; i++) {
            gui.setSlot(slots[i], entryItem(player, rows.get(i), i + 1, board));
        }

        if (rows.isEmpty()) {
            gui.setSlot(22, new GuiElementBuilder(Items.BARRIER)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§cNo leaderboard data yet"))
                    .addLoreLine(Component.literal("§7Run the SQL views first, then let data sync.")));
        }

        MenuUtil.addBackButton(gui, 45, () -> MainMenu.open(player));
        gui.open();
    }

    private static void tab(SimpleGui gui, ServerPlayer player, int slot, Item icon, String name, Board selected, Board target) {
        GuiElementBuilder builder = new GuiElementBuilder(icon)
                .hideDefaultTooltip()
                .setName(Component.literal((selected == target ? "§a▶ " : "") + name))
                .addLoreLine(Component.literal(selected == target ? "§7Currently viewing" : "§eClick to view"))
                .setCallback((i, c, t) -> open(player, target));
        gui.setSlot(slot, builder);
    }

    private static GuiElementBuilder entryItem(ServerPlayer viewer, Entry entry, int rank, Board board) {
        ItemStack stack = new ItemStack(board == Board.GUILDS ? Items.WHITE_BANNER : Items.PLAYER_HEAD);
        if (board != Board.GUILDS) applyProfile(viewer, stack, entry.playerUuid(), entry.playerName());

        GuiElementBuilder item = new GuiElementBuilder(stack)
                .hideDefaultTooltip()
                .setName(Component.literal(medal(rank) + "§f" + safe(entry.playerName())));

        if (board == Board.GUILDS) {
            item.addLoreLine(Component.literal("§6Guild: §f" + safe(entry.profileName())))
                    .addLoreLine(Component.literal("§7Owner: §f" + safe(entry.playerName())))
                    .addLoreLine(Component.literal("§7Level: §f" + entry.level()))
                    .addLoreLine(Component.literal("§7XP: §f" + entry.value()))
                    .addLoreLine(Component.literal("§7Members: §f" + entry.secondaryValue()));
            return item;
        }

        item.addLoreLine(Component.literal("§7Profile: §f" + safe(entry.profileName())))
                .addLoreLine(Component.literal("§7Mode: §f" + safe(entry.mode())));

        if (board == Board.RANKED) {
            item.addLoreLine(Component.literal("§6RP: §f" + entry.rp()))
                    .addLoreLine(Component.literal("§7W/L: §f" + entry.wins() + "/" + entry.losses()));
        } else {
            item.addLoreLine(Component.literal("§6" + entry.label() + ": §f" + formatValue(board, entry.value())));
        }

        item.addLoreLine(Component.literal("§8Profile leaderboard entry"));
        return item;
    }

    private static void applyProfile(ServerPlayer viewer, ItemStack head, UUID uuid, String playerName) {
        try {
            GameProfile profile = resolveProfile(viewer, uuid, playerName);
            if (profile != null) {
                head.set(DataComponents.PROFILE, new ResolvableProfile(profile));
            }
        } catch (Exception ignored) {}
    }

    private static GameProfile resolveProfile(ServerPlayer viewer, UUID uuid, String playerName) {
        if (uuid != null) {
            GameProfile cached = TEXTURE_PROFILE_CACHE.get(uuid);
            if (cached != null) return cached;

            try {
                GameProfile fetched = viewer.server.getSessionService().fetchProfile(uuid, true).profile();
                if (fetched != null) {
                    TEXTURE_PROFILE_CACHE.put(uuid, fetched);
                    return fetched;
                }
            } catch (Exception ignored) {}

            GameProfile fallback = new GameProfile(uuid, playerName);
            TEXTURE_PROFILE_CACHE.putIfAbsent(uuid, fallback);
            return fallback;
        }

        Optional<GameProfile> cachedByName = viewer.server.getProfileCache().get(playerName);
        return cachedByName.orElse(playerName == null || playerName.isBlank() ? null : new GameProfile(null, playerName));
    }

    private static String title(Board board) {
        return switch (board) {
            case RANKED -> "Ranked";
            case PROFESSIONS_OVERALL -> "Professions Overall";
            case PROFESSIONS_MINING -> "Mining";
            case PROFESSIONS_FORESTRY -> "Forestry";
            case PROFESSIONS_FARMING -> "Farming";
            case PLAYTIME -> "Playtime";
            case POKEDEX -> "Pokédex";
            case GYMS -> "Gyms";
            case NUZLOCKE -> "Nuzlocke";
            case ISLANDER -> "Islander";
            case PROFESSIONS_BATTLING -> "Battle";
            case ECONOMY -> "Economy";
            case GUILDS -> "Guilds";
        };
    }

    private static String formatValue(Board board, long value) {
        if (board == Board.PLAYTIME) return value + "h";
        if (board == Board.ECONOMY) return com.champutils.economy.EconomyManager.format(value);
        return Long.toString(value);
    }

    private static String safe(String value) { return value == null || value.isBlank() ? "Unknown" : value; }

    private static String medal(int rank) {
        return switch (rank) {
            case 1 -> "§6#1 ";
            case 2 -> "§7#2 ";
            case 3 -> "§c#3 ";
            default -> "§e#" + rank + " ";
        };
    }

    private static int[] contentSlots() {
        return new int[]{10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    }
}
