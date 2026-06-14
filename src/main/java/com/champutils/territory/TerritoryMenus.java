package com.champutils.territory;

import com.champutils.guild.GuildRepository;
import com.champutils.profile.PlayerProfileManager;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TerritoryMenus {
    private static final int[] CONTENT_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int PAGE_SIZE = CONTENT_SLOTS.length;

    public enum BrowserType { ALL, PERSONAL, GUILD }

    private TerritoryMenus() {}

    /** Backwards-compatible entry point used by /pterritories and /gterritories. */
    public static void open(ServerPlayer player, TerritoryRepository.OwnerType type) {
        openBrowser(player, type == TerritoryRepository.OwnerType.GUILD ? BrowserType.GUILD : BrowserType.PERSONAL, "", 0);
    }

    public static void openHub(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("Territories"));
        fillAll(gui);

        gui.setSlot(10, new GuiElementBuilder(Items.GRASS_BLOCK)
                .hideDefaultTooltip()
                .setName(Component.literal("Your Territory").withStyle(ChatFormatting.GREEN))
                .addLoreLine(Component.literal("Create, visit, or manage your personal territory.").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Click to open options.").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, actionType) -> openPersonalManage(player)));

        gui.setSlot(12, new GuiElementBuilder(Items.BELL)
                .hideDefaultTooltip()
                .setName(Component.literal("Guild Territory").withStyle(ChatFormatting.GOLD))
                .addLoreLine(Component.literal("Create, visit, or manage your guild territory.").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Click to open options.").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, actionType) -> openGuildManage(player)));

        gui.setSlot(14, new GuiElementBuilder(Items.COMPASS)
                .hideDefaultTooltip()
                .setName(Component.literal("Public Territory Browser").withStyle(ChatFormatting.AQUA))
                .addLoreLine(Component.literal("Browse public personal and guild territories.").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Includes pages, search, and type filters.").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Click to browse.").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, actionType) -> openBrowser(player, BrowserType.ALL, "", 0)));

        gui.setSlot(16, new GuiElementBuilder(Items.OAK_SIGN)
                .hideDefaultTooltip()
                .setName(Component.literal("Search Public Territories").withStyle(ChatFormatting.LIGHT_PURPLE))
                .addLoreLine(Component.literal("Use chat command:").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("/territories search <name>").withStyle(ChatFormatting.WHITE))
                .addLoreLine(Component.literal("Example: /territories search Pyro").withStyle(ChatFormatting.DARK_GRAY)));


        gui.open();
    }


    public static void openCreationNpcMenu(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("Territory Creation"));
        fillAll(gui);

        gui.setSlot(10, button(Items.EMERALD_BLOCK, "Create Personal Territory", "Runs /territory create.", () -> {
            gui.close();
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "territory create");
        }));
        gui.setSlot(12, button(Items.GRASS_BLOCK, "Personal Territories", "Browse public personal territories.", () -> openBrowser(player, BrowserType.PERSONAL, "", 0)));
        gui.setSlot(14, button(Items.EMERALD_BLOCK, "Create Guild Territory", "Runs /gterritory create.", () -> {
            gui.close();
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "gterritory create");
        }));
        gui.setSlot(16, button(Items.BELL, "Guild Territories", "Browse public guild territories.", () -> openBrowser(player, BrowserType.GUILD, "", 0)));
        gui.open();
    }

    public static void openNpcManage(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (territory == null) territory = TerritoryRepository.cachedPersonal(player);
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("Territory Steward"));
        fillAll(gui);
        gui.setSlot(4, territoryCard(territory, Items.GRASS_BLOCK, "No personal territory yet"));
        gui.setSlot(10, button(Items.COMPARATOR, "Settings", "Toggle territory settings.", () -> openSettings(player, false)));
        gui.setSlot(12, button(Items.NAME_TAG, "Rename Territory", "Opens rename instructions.", () -> openRenameMenu(player)));
        if (PlayerProfileManager.isIslander(player)) {
            gui.setSlot(14, button(Items.IRON_PICKAXE, "Go to Islander Mine", "Teleport to the shared Islander mining world.", () -> {
                gui.close();
                player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "island mine");
            }));
            gui.setSlot(15, button(Items.RED_BED, "Return to Spawn", "Teleport back to server spawn.", () -> {
                gui.close();
                player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "spawn");
            }));
        } else {
            gui.setSlot(14, button(Items.RED_BED, "Return to Spawn", "Teleport back to server spawn.", () -> {
                gui.close();
                player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "spawn");
            }));
        }
        gui.setSlot(16, button(Items.PLAYER_HEAD, "Trusted Players", "Manage trusted players for this territory.", () -> openTrustedPlayersMenu(player)));
        gui.open();
    }

    public static void openRenameMenu(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x1, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("Rename Territory"));
        fillAll(gui);
        gui.setSlot(4, new GuiElementBuilder(Items.NAME_TAG)
                .hideDefaultTooltip()
                .setName(Component.literal("Rename Territory").withStyle(ChatFormatting.AQUA))
                .addLoreLine(Component.literal("Use this chat command:").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("/territory name <new name>").withStyle(ChatFormatting.WHITE))
                .addLoreLine(Component.literal("Example: /territory name Pyro Island").withStyle(ChatFormatting.DARK_GRAY)));
        gui.setSlot(8, new GuiElementBuilder(Items.ARROW)
                .hideDefaultTooltip()
                .setName(Component.literal("Back").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, actionType) -> openNpcManage(player, TerritoryRepository.cachedPersonal(player))));
        gui.open();
    }

    public static void openTrustedPlayersMenu(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("Trusted Players"));
        fillAll(gui);
        gui.setSlot(10, new GuiElementBuilder(Items.LIME_CONCRETE)
                .hideDefaultTooltip()
                .setName(Component.literal("Trust Player").withStyle(ChatFormatting.GREEN))
                .addLoreLine(Component.literal("Use: /territory trust <player>").withStyle(ChatFormatting.WHITE))
                .addLoreLine(Component.literal("Managers can manage more settings:").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("/territory trust <player> manager").withStyle(ChatFormatting.DARK_GRAY)));
        gui.setSlot(12, new GuiElementBuilder(Items.RED_CONCRETE)
                .hideDefaultTooltip()
                .setName(Component.literal("Remove Trust").withStyle(ChatFormatting.RED))
                .addLoreLine(Component.literal("Use: /territory untrust <player>").withStyle(ChatFormatting.WHITE)));
        gui.setSlot(14, new GuiElementBuilder(Items.BARRIER)
                .hideDefaultTooltip()
                .setName(Component.literal("Ban Player").withStyle(ChatFormatting.DARK_RED))
                .addLoreLine(Component.literal("Use: /territory ban <player>").withStyle(ChatFormatting.WHITE))
                .addLoreLine(Component.literal("Undo with: /territory unban <player>").withStyle(ChatFormatting.GRAY)));
        gui.setSlot(16, new GuiElementBuilder(Items.ENDER_PEARL)
                .hideDefaultTooltip()
                .setName(Component.literal("Visit Trusted Territories").withStyle(ChatFormatting.AQUA))
                .addLoreLine(Component.literal("Use: /territory trusted").withStyle(ChatFormatting.WHITE))
                .addLoreLine(Component.literal("Then /territory visit <name>").withStyle(ChatFormatting.GRAY)));
        gui.setSlot(22, new GuiElementBuilder(Items.ARROW)
                .hideDefaultTooltip()
                .setName(Component.literal("Back").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, actionType) -> openNpcManage(player, TerritoryRepository.cachedPersonal(player))));
        gui.open();
    }

    public static void openPersonalManage(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("Personal Territory"));
        fillAll(gui);

        TerritoryRepository.Territory territory = TerritoryRepository.cachedPersonal(player);
        gui.setSlot(4, territoryCard(territory, Items.GRASS_BLOCK, "No personal territory yet"));

        gui.setSlot(10, button(Items.EMERALD_BLOCK, "Create Territory", "Runs /territory create.", () -> {
            gui.close();
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "territory create");
        }));
        gui.setSlot(12, button(Items.ENDER_PEARL, "Go Home", "Teleports to your territory home.", () -> {
            gui.close();
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "territory home");
        }));
        gui.setSlot(14, button(Items.COMPARATOR, "Settings", "Toggle territory settings.", () -> openSettings(player, false)));
        gui.setSlot(16, button(Items.COMPASS, "Browse Public Personal Territories", "Opens the public personal territory browser.", () -> openBrowser(player, BrowserType.PERSONAL, "", 0)));

        gui.setSlot(18, button(Items.BARRIER, "Delete Territory", "Runs the delete confirmation prompt.", () -> {
            gui.close();
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "territory delete");
        }));
        gui.setSlot(22, button(Items.ARROW, "Back", "Return to territory menu.", () -> openHub(player)));
        gui.open();
    }

    public static void openGuildManage(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("Guild Territory"));
        fillAll(gui);

        TerritoryRepository.Territory territory = TerritoryRepository.cachedGuildForPlayer(player);
        gui.setSlot(4, territoryCard(territory, Items.BELL, "Your guild has no territory yet"));

        gui.setSlot(10, button(Items.EMERALD_BLOCK, "Create Guild Territory", "Runs /gterritory create.", () -> {
            gui.close();
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "gterritory create");
        }));
        gui.setSlot(12, button(Items.ENDER_PEARL, "Go Home", "Teleports to your guild territory home.", () -> {
            gui.close();
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "gterritory home");
        }));
        gui.setSlot(14, button(Items.COMPARATOR, "Settings", "Toggle guild territory settings.", () -> openSettings(player, true)));
        gui.setSlot(16, button(Items.COMPASS, "Browse Public Guild Territories", "Opens the public guild territory browser.", () -> openBrowser(player, BrowserType.GUILD, "", 0)));

        gui.setSlot(18, button(Items.BARRIER, "Delete Guild Territory", "Runs the delete confirmation prompt.", () -> {
            gui.close();
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "gterritory delete");
        }));
        gui.open();
    }


    public static void openSettings(ServerPlayer player, boolean guild) {
        TerritoryRepository.Territory territory = guild
                ? TerritoryRepository.cachedGuildForPlayer(player)
                : TerritoryRepository.cachedPersonal(player);

        if (guild) {
            GuildRepository.GuildSnapshot snapshot = GuildRepository.cachedGuild(player.getUUID());
            if (snapshot == null || !GuildRepository.canManageGuildTerritory(snapshot.role)) {
                player.sendSystemMessage(Component.literal("Only guild leaders and officers can manage guild territory settings.").withStyle(ChatFormatting.RED));
                return;
            }
        }

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x1, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal(guild ? "Guild Territory Settings" : "Territory Settings"));

        if (territory == null) {
            gui.setSlot(4, new GuiElementBuilder(Items.BARRIER)
                    .hideDefaultTooltip()
                    .setName(Component.literal(guild ? "No guild territory" : "No territory").withStyle(ChatFormatting.RED))
                    .addLoreLine(Component.literal(guild ? "Create a guild territory first." : "Create a territory first.").withStyle(ChatFormatting.GRAY)));
            gui.setSlot(8, new GuiElementBuilder(Items.ARROW)
                    .hideDefaultTooltip()
                    .setName(Component.literal("Back").withStyle(ChatFormatting.YELLOW))
                    .setCallback((index, clickType, actionType) -> { if (guild) openGuildManage(player); else openPersonalManage(player); }));
            gui.open();
            return;
        }

        setToggle(gui, 0, "Public Listing", "Show this territory in public browsers.", territory.isPublic, guild, "public", player);
        setToggle(gui, 1, "Visitors", "Allow players to visit this territory.", territory.allowVisitors, guild, "visitors", player);
        setToggle(gui, 2, "Visitor Build", "Allow visitors to build and break blocks.", territory.visitorsCanBuild, guild, "visitorbuild", player);
        setToggle(gui, 3, "Visitor Containers", "Allow visitors to open containers.", territory.visitorsCanOpenContainers, guild, "visitorcontainers", player);
        setToggle(gui, 4, "Visitor Entities", "Allow visitors to interact with entities.", territory.visitorsCanInteractEntities, guild, "visitorentities", player);
        setToggle(gui, 5, "Visitor Redstone", "Allow visitors to use buttons, levers, and redstone.", territory.visitorsCanUseRedstone, guild, "visitorredstone", player);
        gui.setSlot(8, new GuiElementBuilder(Items.ARROW)
                .hideDefaultTooltip()
                .setName(Component.literal("Back").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, actionType) -> { if (guild) openGuildManage(player); else openPersonalManage(player); }));
        gui.open();
    }

    private static void setToggle(SimpleGui gui, int slot, String label, String description, boolean enabled, boolean guild, String setting, ServerPlayer player) {
        Item icon = enabled ? Items.LIME_DYE : Items.GRAY_DYE;
        gui.setSlot(slot, new GuiElementBuilder(icon)
                .hideDefaultTooltip()
                .setName(Component.literal((enabled ? "§a" : "§c") + label + ": " + (enabled ? "ON" : "OFF")))
                .addLoreLine(Component.literal("§7" + description))
                .addLoreLine(Component.literal("§eClick to toggle"))
                .setCallback((index, clickType, actionType) -> {
                    String command = (guild ? "gterritory" : "territory") + " set " + setting + " " + (!enabled);
                    player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
                    player.getServer().execute(() -> openSettings(player, guild));
                }));
    }

    public static void openBrowser(ServerPlayer player, BrowserType type, String search, int page) {
        String query = search == null ? "" : search.trim();
        List<TerritoryRepository.Territory> territories = filteredPublic(type, query);
        int maxPage = Math.max(0, (territories.size() - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, maxPage));

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal(browserTitle(type, query, safePage, maxPage)));
        fillFooter(gui);

        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, territories.size());
        int slotIndex = 0;
        for (int i = start; i < end; i++) {
            TerritoryRepository.Territory territory = territories.get(i);
            gui.setSlot(CONTENT_SLOTS[slotIndex++], territoryVisitButton(player, gui, territory));
        }

        if (territories.isEmpty()) {
            gui.setSlot(22, new GuiElementBuilder(Items.BARRIER)
                    .hideDefaultTooltip()
                    .setName(Component.literal("No matching public territories").withStyle(ChatFormatting.RED))
                    .addLoreLine(Component.literal(query.isBlank() ? "No public territories are listed yet." : "No public territories match: " + query).withStyle(ChatFormatting.GRAY))
                    .addLoreLine(Component.literal("Players can list one with /territory set public true.").withStyle(ChatFormatting.GRAY)));
        }

        gui.setSlot(45, new GuiElementBuilder(Items.ARROW)
                .hideDefaultTooltip()
                .setName(Component.literal("Previous Page").withStyle(safePage > 0 ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY))
                .addLoreLine(Component.literal("Page " + (safePage + 1) + " of " + (maxPage + 1)).withStyle(ChatFormatting.GRAY))
                .setCallback((index, clickType, actionType) -> {
                    if (safePage > 0) openBrowser(player, type, query, safePage - 1);
                }));

        gui.setSlot(49, new GuiElementBuilder(Items.OAK_SIGN)
                .hideDefaultTooltip()
                .setName(Component.literal("Search by Name").withStyle(ChatFormatting.LIGHT_PURPLE))
                .addLoreLine(Component.literal(query.isBlank() ? "Current search: none" : "Current search: " + query).withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Use: /territories search <name>").withStyle(ChatFormatting.WHITE))
                .addLoreLine(Component.literal("Or: /territories " + typeCommand(type) + " search <name>").withStyle(ChatFormatting.DARK_GRAY))
                .addLoreLine(Component.literal("Click to clear search.").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, actionType) -> openBrowser(player, type, "", 0)));

        gui.setSlot(50, new GuiElementBuilder(typeIcon(type))
                .hideDefaultTooltip()
                .setName(Component.literal("Type Filter: " + typeLabel(type)).withStyle(ChatFormatting.AQUA))
                .addLoreLine(Component.literal("Click to cycle All → Personal → Guild.").withStyle(ChatFormatting.GRAY))
                .setCallback((index, clickType, actionType) -> openBrowser(player, nextType(type), query, 0)));

        gui.setSlot(51, new GuiElementBuilder(Items.COMPASS)
                .hideDefaultTooltip()
                .setName(Component.literal("All Public Territories").withStyle(ChatFormatting.AQUA))
                .addLoreLine(Component.literal("Show both personal and guild territories.").withStyle(ChatFormatting.GRAY))
                .setCallback((index, clickType, actionType) -> openBrowser(player, BrowserType.ALL, query, 0)));

        gui.setSlot(52, new GuiElementBuilder(Items.BOOK)
                .hideDefaultTooltip()
                .setName(Component.literal("Territory Menu").withStyle(ChatFormatting.GREEN))
                .addLoreLine(Component.literal("Go back to your territory options.").withStyle(ChatFormatting.GRAY))
                .setCallback((index, clickType, actionType) -> openHub(player)));

        gui.setSlot(53, new GuiElementBuilder(Items.ARROW)
                .hideDefaultTooltip()
                .setName(Component.literal("Next Page").withStyle(safePage < maxPage ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY))
                .addLoreLine(Component.literal("Page " + (safePage + 1) + " of " + (maxPage + 1)).withStyle(ChatFormatting.GRAY))
                .setCallback((index, clickType, actionType) -> {
                    if (safePage < maxPage) openBrowser(player, type, query, safePage + 1);
                }));

        gui.open();
    }

    private static GuiElementBuilder territoryVisitButton(ServerPlayer player, SimpleGui gui, TerritoryRepository.Territory territory) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Type: " + territory.displayType()).withStyle(territory.ownerType == TerritoryRepository.OwnerType.GUILD ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        lore.add(Component.literal("Owner: " + territory.ownerName).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Visitors: " + (territory.allowVisitors ? "Allowed" : "Trusted/List Only")).withStyle(territory.allowVisitors ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        lore.add(Component.literal("Status: " + (territory.isReady() ? "Ready" : cleanState(territory.generationState))).withStyle(territory.isReady() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        lore.add(Component.literal("Click to visit.").withStyle(ChatFormatting.AQUA));

        return new GuiElementBuilder(territory.ownerType == TerritoryRepository.OwnerType.GUILD ? Items.BELL : Items.GRASS_BLOCK)
                .hideDefaultTooltip()
                .setName(Component.literal(territory.publicName()).withStyle(ChatFormatting.GOLD))
                .setLore(lore)
                .setCallback((index, clickType, actionType) -> {
                    if (!territory.isReady() && !player.hasPermissions(4)) {
                        player.sendSystemMessage(Component.literal("That territory is still being created or loaded. Try again shortly.").withStyle(ChatFormatting.YELLOW));
                        return;
                    }
                    if (!TerritoryRepository.canEnter(player, territory)) {
                        player.sendSystemMessage(Component.literal("You cannot visit that territory.").withStyle(ChatFormatting.RED));
                        return;
                    }
                    gui.close();
                    if (!TerritoryTeleportUtil.teleportHome(player, territory)) {
                        player.sendSystemMessage(Component.literal("That territory world is not loaded yet. Try again shortly.").withStyle(ChatFormatting.RED));
                    }
                });
    }

    private static List<TerritoryRepository.Territory> filteredPublic(BrowserType type, String query) {
        List<TerritoryRepository.Territory> list = new ArrayList<>();
        for (TerritoryRepository.Territory territory : TerritoryRepository.allCached()) {
            if (territory == null || !territory.isPublic) continue;
            if (type == BrowserType.PERSONAL && territory.ownerType != TerritoryRepository.OwnerType.PLAYER) continue;
            if (type == BrowserType.GUILD && territory.ownerType != TerritoryRepository.OwnerType.GUILD) continue;
            if (!matches(territory, query)) continue;
            list.add(territory);
        }
        list.sort((a, b) -> {
            int typeCompare = a.displayType().compareToIgnoreCase(b.displayType());
            if (type == BrowserType.ALL && typeCompare != 0) return typeCompare;
            return a.publicName().compareToIgnoreCase(b.publicName());
        });
        return list;
    }

    private static boolean matches(TerritoryRepository.Territory territory, String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase(Locale.ROOT);
        return safe(territory.publicName()).contains(q)
                || safe(territory.ownerName).contains(q)
                || safe(territory.displayName).contains(q);
    }

    private static String safe(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String browserTitle(BrowserType type, String query, int page, int maxPage) {
        String title = switch (type) {
            case PERSONAL -> "Public Personal Territories";
            case GUILD -> "Public Guild Territories";
            case ALL -> "Public Territories";
        };
        if (query != null && !query.isBlank()) title += " | " + query;
        title += " | " + (page + 1) + "/" + (maxPage + 1);
        return title;
    }

    private static String typeCommand(BrowserType type) {
        return switch (type) {
            case PERSONAL -> "personal";
            case GUILD -> "guild";
            case ALL -> "all";
        };
    }

    private static BrowserType nextType(BrowserType type) {
        return switch (type) {
            case ALL -> BrowserType.PERSONAL;
            case PERSONAL -> BrowserType.GUILD;
            case GUILD -> BrowserType.ALL;
        };
    }

    private static String typeLabel(BrowserType type) {
        return switch (type) {
            case ALL -> "All";
            case PERSONAL -> "Personal";
            case GUILD -> "Guild";
        };
    }

    private static Item typeIcon(BrowserType type) {
        return switch (type) {
            case ALL -> Items.COMPASS;
            case PERSONAL -> Items.GRASS_BLOCK;
            case GUILD -> Items.BELL;
        };
    }

    private static String cleanState(String state) {
        return state == null || state.isBlank() ? "Preparing" : state.replace('_', ' ');
    }

    private static GuiElementBuilder territoryCard(TerritoryRepository.Territory territory, Item icon, String emptyText) {
        if (territory == null) {
            return new GuiElementBuilder(Items.BARRIER)
                    .hideDefaultTooltip()
                    .setName(Component.literal(emptyText).withStyle(ChatFormatting.RED));
        }
        return new GuiElementBuilder(icon)
                .hideDefaultTooltip()
                .setName(Component.literal(territory.publicName()).withStyle(ChatFormatting.GOLD))
                .addLoreLine(Component.literal("Owner: " + territory.ownerName).withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Type: " + territory.displayType()).withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Public: " + (territory.isPublic ? "Yes" : "No")).withStyle(territory.isPublic ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
                .addLoreLine(Component.literal("Status: " + (territory.isReady() ? "Ready" : cleanState(territory.generationState))).withStyle(territory.isReady() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
    }

    private static GuiElementBuilder button(Item icon, String name, String lore, Runnable action) {
        return new GuiElementBuilder(icon)
                .hideDefaultTooltip()
                .setName(Component.literal(name).withStyle(ChatFormatting.YELLOW))
                .addLoreLine(Component.literal(lore).withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Click to use.").withStyle(ChatFormatting.AQUA))
                .setCallback((index, clickType, actionType) -> action.run());
    }

    private static void fillFooter(SimpleGui gui) {
        GuiElementBuilder filler = new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).hideTooltip().setName(Component.literal(" "));
        for (int i = 45; i < 54; i++) gui.setSlot(i, filler);
    }

    private static void fillAll(SimpleGui gui) {
        GuiElementBuilder filler = new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE).hideTooltip().setName(Component.literal(" "));
        for (int i = 0; i < gui.getSize(); i++) gui.setSlot(i, filler);
    }
}
