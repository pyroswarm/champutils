package com.champutils.menu;

import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfileGameMode;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ProfileSelectionMenu {
    private ProfileSelectionMenu() {}

    private record ProfileColor(String name, Item icon, ChatFormatting style) {}

    private static final ProfileColor[] PROFILE_COLORS = {
            new ProfileColor("Red", Items.RED_WOOL, ChatFormatting.RED),
            new ProfileColor("Blue", Items.BLUE_WOOL, ChatFormatting.BLUE),
            new ProfileColor("Green", Items.GREEN_WOOL, ChatFormatting.GREEN),
            new ProfileColor("Yellow", Items.YELLOW_WOOL, ChatFormatting.YELLOW),
            new ProfileColor("Gold", Items.GOLD_INGOT, ChatFormatting.GOLD),
            new ProfileColor("Silver", Items.IRON_INGOT, ChatFormatting.GRAY),
            new ProfileColor("Crystal", Items.AMETHYST_SHARD, ChatFormatting.LIGHT_PURPLE),
            new ProfileColor("Ruby", Items.REDSTONE, ChatFormatting.RED),
            new ProfileColor("Sapphire", Items.LAPIS_LAZULI, ChatFormatting.BLUE),
            new ProfileColor("Emerald", Items.EMERALD, ChatFormatting.GREEN),
            new ProfileColor("Diamond", Items.DIAMOND, ChatFormatting.AQUA),
            new ProfileColor("Pearl", Items.QUARTZ, ChatFormatting.WHITE),
            new ProfileColor("Black", Items.BLACK_WOOL, ChatFormatting.DARK_GRAY),
            new ProfileColor("White", Items.WHITE_WOOL, ChatFormatting.WHITE)
    };

    public static void open(ServerPlayer player) {
        if (player == null) return;
        String finalized = PlayerProfileManager.finalizePendingDeletesBlocking(player);
        if (!finalized.isBlank()) player.sendSystemMessage(Component.literal(finalized).withStyle(ChatFormatting.GRAY));

        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Select Profile"));

        List<PlayerProfileManager.ProfileRecord> profiles = PlayerProfileManager.listBlocking(player);
        PlayerProfileManager.ProfileLimit limit = PlayerProfileManager.limitBlocking(player);

        int[] slots = {10, 11, 12, 13, 14, 15};
        for (int i = 0; i < profiles.size() && i < slots.length; i++) {
            var profile = profiles.get(i);
            GuiElementBuilder item = new GuiElementBuilder(icon(profile.gameMode()))
                    .hideDefaultTooltip()
                    .setName(Component.literal((profile.active() ? "★ " : "") + profile.profileName()).withStyle(profile.active() ? ChatFormatting.GREEN : ChatFormatting.AQUA))
                    .addLoreLine(Component.literal("Mode: " + profile.gameMode().displayName() + PlayerProfileManager.modeSuffix(profile)).withStyle(ChatFormatting.GRAY));
            if (profile.pendingDelete()) {
                item.addLoreLine(Component.literal("Pending deletion").withStyle(ChatFormatting.RED));
                if (profile.deleteAvailableAt() != null) {
                    item.addLoreLine(Component.literal("Frees at: " + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(profile.deleteAvailableAt())).withStyle(ChatFormatting.DARK_RED));
                }
            } else if (profile.active()) {
                item.addLoreLine(Component.literal("Currently loaded").withStyle(ChatFormatting.GREEN));
            } else {
                item.addLoreLine(Component.literal("Click to load this profile").withStyle(ChatFormatting.YELLOW));
            }
            gui.setSlot(slots[i], item.setCallback((index, clickType, action, gui1) -> {
                if (profile.pendingDelete()) return;
                String result = PlayerProfileManager.switchBlocking(player, profile.profileName());
                player.sendSystemMessage(Component.literal(result).withStyle(result.startsWith("Loaded") ? ChatFormatting.GREEN : ChatFormatting.RED));
                gui.close();
            }));
        }

        gui.setSlot(22, new GuiElementBuilder(Items.EMERALD)
                .hideDefaultTooltip()
                .setName(Component.literal("Create a Profile").withStyle(ChatFormatting.GREEN))
                .addLoreLine(Component.literal("Slots: " + profiles.size() + " / " + limit.maxProfiles()).withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Click to choose mode, then color.").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, action, gui1) -> {
                    if (profiles.size() >= limit.maxProfiles()) {
                        player.sendSystemMessage(Component.literal("You already have the max of " + limit.maxProfiles() + " profiles.").withStyle(ChatFormatting.RED));
                        open(player);
                        return;
                    }
                    openCreateModeMenu(player);
                }));

        gui.setSlot(26, new GuiElementBuilder(Items.BARRIER)
                .hideDefaultTooltip()
                .setName(Component.literal("Delete Profiles").withStyle(ChatFormatting.RED))
                .addLoreLine(Component.literal(limit.instantDelete() ? "VIP instant deletion active." : "Normal deletion has a 24 hour cooldown.").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Click to choose a profile.").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, action, gui1) -> openDeleteMenu(player)));

        MenuUtil.fillBordersForced(gui, slots[0], slots[1], slots[2], slots[3], slots[4], slots[5], 22, 26);
        gui.open();
    }

    private static void openCreateModeMenu(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Choose Profile Type"));

        setModeButton(gui, player, 11, ProfileGameMode.NORMAL, Items.GRASS_BLOCK,
                "Normal", "Standard profile with normal trading and gameplay.");
        setModeButton(gui, player, 13, ProfileGameMode.IRONMAN, Items.IRON_INGOT,
                "Ironman", "No player trading, auction, chest shops, or handouts.");
        setModeButton(gui, player, 15, ProfileGameMode.MONOTYPE, Items.BLAZE_POWDER,
                "Monotype", "Choose one Pokémon type for battle restrictions next.");

        MenuUtil.addBackButton(gui, 18, () -> open(player));
        MenuUtil.fillBordersForced(gui, 11, 13, 15, 18);
        gui.open();
    }

    private static void setModeButton(SimpleGui gui, ServerPlayer player, int slot, ProfileGameMode mode, Item icon, String title, String lore) {
        gui.setSlot(slot, new GuiElementBuilder(icon)
                .hideDefaultTooltip()
                .setName(Component.literal(title).withStyle(ChatFormatting.AQUA))
                .addLoreLine(Component.literal(lore).withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Click to continue.").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, action, gui1) -> openColorMenu(player, mode, null)));
    }

    private static void openColorMenu(ServerPlayer player, ProfileGameMode mode, String monotype) {
        if (mode == ProfileGameMode.MONOTYPE && (monotype == null || monotype.isBlank())) {
            openMonotypeMenu(player);
            return;
        }

        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Choose Profile Color"));

        Set<String> used = usedProfileNames(player);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < PROFILE_COLORS.length && i < slots.length; i++) {
            ProfileColor color = PROFILE_COLORS[i];
            boolean taken = used.contains(color.name().toLowerCase(Locale.ROOT));
            GuiElementBuilder builder = new GuiElementBuilder(taken ? Items.GRAY_DYE : color.icon())
                    .hideDefaultTooltip()
                    .setName(Component.literal(color.name()).withStyle(taken ? ChatFormatting.DARK_GRAY : color.style()))
                    .addLoreLine(Component.literal(taken ? "Already used on your account." : "Click to create this profile.").withStyle(taken ? ChatFormatting.RED : ChatFormatting.YELLOW))
                    .addLoreLine(Component.literal("Mode: " + mode.displayName() + (monotype == null ? "" : ": " + monotype)).withStyle(ChatFormatting.GRAY));
            if (!taken) {
                builder.setCallback((index, clickType, action, gui1) -> {
                    String result = PlayerProfileManager.createBlocking(player, color.name(), mode, monotype);
                    player.sendSystemMessage(Component.literal(result).withStyle(result.startsWith("Created") ? ChatFormatting.GREEN : ChatFormatting.RED));
                    if (result.startsWith("Created")) open(player); else openColorMenu(player, mode, monotype);
                });
            }
            gui.setSlot(slots[i], builder);
        }

        MenuUtil.addBackButton(gui, 45, () -> {
            if (mode == ProfileGameMode.MONOTYPE) {
                openMonotypeMenu(player);
            } else {
                openCreateModeMenu(player);
            }
        });
        MenuUtil.fillBordersForced(gui, concat(slots, 45));
        gui.open();
    }

    private static void openMonotypeMenu(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Choose Monotype Type"));
        String[] types = {"normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"};
        Item[] icons = {Items.WHITE_WOOL, Items.BLAZE_POWDER, Items.WATER_BUCKET, Items.OAK_SAPLING, Items.REDSTONE_TORCH, Items.ICE, Items.IRON_SWORD, Items.SPIDER_EYE, Items.DIRT, Items.FEATHER, Items.ENDER_PEARL, Items.STRING, Items.COBBLESTONE, Items.SOUL_LANTERN, Items.DRAGON_BREATH, Items.BLACK_DYE, Items.IRON_INGOT, Items.PINK_DYE};
        int[] slots = {9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26};
        for (int i = 0; i < types.length; i++) {
            String type = types[i];
            gui.setSlot(slots[i], new GuiElementBuilder(icons[i])
                    .hideDefaultTooltip()
                    .setName(Component.literal(cap(type) + " Type").withStyle(ChatFormatting.AQUA))
                    .addLoreLine(Component.literal("Click to choose profile color next.").withStyle(ChatFormatting.YELLOW))
                    .setCallback((index, clickType, action, gui1) -> openColorMenu(player, ProfileGameMode.MONOTYPE, type)));
        }
        MenuUtil.addBackButton(gui, 45, () -> openCreateModeMenu(player));
        MenuUtil.fillBordersForced(gui, concat(slots, 45));
        gui.open();
    }

    private static void openDeleteMenu(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Delete Profile"));
        List<PlayerProfileManager.ProfileRecord> profiles = PlayerProfileManager.listBlocking(player);
        int[] slots = {10, 11, 12, 13, 14, 15};
        for (int i = 0; i < profiles.size() && i < slots.length; i++) {
            var profile = profiles.get(i);
            boolean disabled = profile.pendingDelete() || profile.active();
            GuiElementBuilder builder = new GuiElementBuilder(disabled ? Items.GRAY_DYE : Items.BARRIER)
                    .hideDefaultTooltip()
                    .setName(Component.literal(profile.profileName()).withStyle(disabled ? ChatFormatting.DARK_GRAY : ChatFormatting.RED))
                    .addLoreLine(Component.literal(profile.active() ? "Load another profile before deleting this one." : profile.pendingDelete() ? "Already pending deletion." : "Click to queue/delete this profile.").withStyle(disabled ? ChatFormatting.GRAY : ChatFormatting.YELLOW));
            if (!disabled) {
                builder.setCallback((index, clickType, action, gui1) -> {
                    String result = PlayerProfileManager.deleteBlocking(player, profile.profileName());
                    player.sendSystemMessage(Component.literal(result).withStyle(result.startsWith("Deleted") || result.startsWith("Profile") ? ChatFormatting.GREEN : ChatFormatting.RED));
                    open(player);
                });
            }
            gui.setSlot(slots[i], builder);
        }
        MenuUtil.addBackButton(gui, 18, () -> open(player));
        MenuUtil.fillBordersForced(gui, slots[0], slots[1], slots[2], slots[3], slots[4], slots[5], 18);
        gui.open();
    }

    private static Set<String> usedProfileNames(ServerPlayer player) {
        Set<String> used = new HashSet<>();
        for (PlayerProfileManager.ProfileRecord profile : PlayerProfileManager.listBlocking(player)) {
            if (profile != null && !profile.pendingDelete()) used.add(profile.profileName().toLowerCase(Locale.ROOT));
        }
        return used;
    }

    private static Item icon(ProfileGameMode mode) {
        return switch (mode) {
            case IRONMAN -> Items.IRON_INGOT;
            case MONOTYPE -> Items.BLAZE_POWDER;
            case NORMAL -> Items.GRASS_BLOCK;
        };
    }

    private static String cap(String s) {
        if (s == null || s.isBlank()) return "";
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private static int[] concat(int[] base, int extra) {
        int[] out = new int[base.length + 1];
        System.arraycopy(base, 0, out, 0, base.length);
        out[base.length] = extra;
        return out;
    }
}
