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
import net.minecraft.server.MinecraftServer;
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
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class LeaderboardMenu {
    private static final Map<UUID, GameProfile> TEXTURE_PROFILE_CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> TEXTURE_LOOKUPS_IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> MENU_REFRESH_PENDING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Board> OPEN_BOARDS = new ConcurrentHashMap<>();

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
            case BREEDING -> Board.PROFESSIONS_BREEDING;
            default -> Board.PROFESSIONS_OVERALL;
        });
    }

    private static void open(ServerPlayer player, Board board) {
        render(player, board, ProfileLeaderboardRepository.topFresh(board, 28), false);
    }

    private static void render(ServerPlayer player, Board board, List<Entry> rows, boolean loadCompleted) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false) {
            @Override
            public void onClose() {
                super.onClose();
                OPEN_BOARDS.remove(player.getUUID(), board);
            }
        };
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
        tab(gui, player, 6, Items.EGG, "§dBreeding", board, Board.PROFESSIONS_BREEDING);
        tab(gui, player, 7, Items.EMERALD, "§aEconomy", board, Board.ECONOMY);
        tab(gui, player, 8, Items.WHITE_BANNER, "§fGuilds", board, Board.GUILDS);
        tab(gui, player, 48, Items.CLOCK, "§ePlaytime", board, Board.PLAYTIME);
        tab(gui, player, 49, Items.BOOK, "§dPokédex", board, Board.POKEDEX);
        tab(gui, player, 50, Items.SHIELD, "§cGyms", board, Board.GYMS);
        tab(gui, player, 51, Items.DRAGON_HEAD, "§5Nuzlocke", board, Board.NUZLOCKE);
        tab(gui, player, 52, Items.GRASS_BLOCK, "§2Islander", board, Board.ISLANDER);

        int[] slots = contentSlots();
        for (int i = 0; i < rows.size() && i < slots.length; i++) {
            gui.setSlot(slots[i], entryItem(player, rows.get(i), i + 1, board));
        }

        if (rows.isEmpty() && !loadCompleted) {
            gui.setSlot(22, new GuiElementBuilder(Items.CLOCK).hideDefaultTooltip()
                    .setName(Component.literal("§eLoading leaderboard..."))
                    .addLoreLine(Component.literal("§7Loading asynchronously.")));
            ProfileLeaderboardRepository.topAsync(board, 28).whenComplete((freshRows, error) -> player.server.execute(() -> {
                if (player.hasDisconnected()) return;
                if (error != null) {
                    error.printStackTrace();
                    render(player, board, List.of(), true);
                    player.sendSystemMessage(Component.literal("Could not load leaderboard. Check console."));
                    return;
                }
                // Render the completed state directly. Never recursively restart an empty load.
                render(player, board, freshRows == null ? List.of() : freshRows, true);
            }));
        } else if (rows.isEmpty()) {
            gui.setSlot(22, new GuiElementBuilder(Items.BARRIER).hideDefaultTooltip()
                    .setName(Component.literal("§7No leaderboard entries yet"))
                    .addLoreLine(Component.literal("§8Complete progress in this mode to appear here.")));
        }

        MenuUtil.addBackButton(gui, 45, () -> MainMenu.open(player));
        gui.open();
        OPEN_BOARDS.put(player.getUUID(), board);
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
        if (board != Board.GUILDS) applyProfile(viewer, stack, entry.playerUuid(), entry.playerName(), board);

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

    private static void applyProfile(ServerPlayer viewer, ItemStack head, UUID uuid, String playerName, Board board) {
        try {
            GameProfile profile = resolveProfile(viewer, uuid, playerName, board);
            if (profile != null) head.set(DataComponents.PROFILE, new ResolvableProfile(profile));
        } catch (Exception ignored) {}
    }

    private static GameProfile resolveProfile(ServerPlayer viewer, UUID uuid, String playerName, Board board) {
        if (uuid == null) {
            Optional<GameProfile> cachedByName = viewer.server.getProfileCache().get(playerName == null ? "" : playerName);
            return cachedByName.orElse(playerName == null || playerName.isBlank() ? null : new GameProfile(null, playerName));
        }

        ServerPlayer online = viewer.server.getPlayerList().getPlayer(uuid);
        if (online != null && hasTexture(online.getGameProfile())) {
            TEXTURE_PROFILE_CACHE.put(uuid, online.getGameProfile());
            return online.getGameProfile();
        }

        GameProfile cached = TEXTURE_PROFILE_CACHE.get(uuid);
        if (hasTexture(cached)) return cached;

        Optional<GameProfile> serverCached = viewer.server.getProfileCache().get(playerName == null ? "" : playerName);
        if (serverCached.isPresent() && hasTexture(serverCached.get())) {
            TEXTURE_PROFILE_CACHE.put(uuid, serverCached.get());
            return serverCached.get();
        }

        requestTextureProfile(viewer, uuid, playerName, board);
        // A textureless fallback gives the correct owner identity while the asynchronous
        // session lookup completes, but is deliberately never cached.
        return new GameProfile(uuid, playerName == null ? "" : playerName);
    }

    private static boolean hasTexture(GameProfile profile) {
        return profile != null && profile.getProperties() != null && profile.getProperties().containsKey("textures");
    }

    private static void requestTextureProfile(ServerPlayer viewer, UUID uuid, String playerName, Board board) {
        if (viewer == null || uuid == null || !TEXTURE_LOOKUPS_IN_FLIGHT.add(uuid)) return;
        MinecraftServer server = viewer.server;
        Object sessionService = server.getSessionService();
        CompletableFuture.supplyAsync(() -> fetchTextureProfile(sessionService, uuid, playerName))
                .whenComplete((profile, error) -> server.execute(() -> {
                    TEXTURE_LOOKUPS_IN_FLIGHT.remove(uuid);
                    if (error != null) {
                        System.err.println("[ChampUtils] Leaderboard skin lookup failed for " + uuid + ": " + error.getMessage());
                        return;
                    }
                    if (!hasTexture(profile)) return;
                    TEXTURE_PROFILE_CACHE.put(uuid, profile);
                    scheduleMenuRefresh(viewer, board);
                }));
    }

    private static GameProfile fetchTextureProfile(Object sessionService, UUID uuid, String playerName) {
        try {
            Method fetch = sessionService.getClass().getMethod("fetchProfile", UUID.class, boolean.class);
            Object result = fetch.invoke(sessionService, uuid, false);
            if (result instanceof GameProfile profile) return profile;
            if (result != null) {
                for (String methodName : List.of("profile", "getProfile")) {
                    try {
                        Method profileMethod = result.getClass().getMethod(methodName);
                        Object value = profileMethod.invoke(result);
                        if (value instanceof GameProfile profile) return profile;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return new GameProfile(uuid, playerName == null ? "" : playerName);
    }

    private static void scheduleMenuRefresh(ServerPlayer viewer, Board board) {
        UUID viewerId = viewer.getUUID();
        if (!MENU_REFRESH_PENDING.add(viewerId)) return;
        CompletableFuture.runAsync(
                () -> viewer.server.execute(() -> {
                    MENU_REFRESH_PENDING.remove(viewerId);
                    ServerPlayer current = viewer.server.getPlayerList().getPlayer(viewerId);
                    if (current != null && !current.hasDisconnected() && OPEN_BOARDS.get(viewerId) == board) {
                        render(current, board, ProfileLeaderboardRepository.topFresh(board, 28), true);
                    }
                }),
                CompletableFuture.delayedExecutor(350L, TimeUnit.MILLISECONDS)
        );
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
            case PROFESSIONS_BREEDING -> "Breeding";
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
