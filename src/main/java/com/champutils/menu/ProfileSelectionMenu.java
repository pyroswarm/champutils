package com.champutils.menu;

import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfileLobbyLockManager;
import com.champutils.profile.ProfileGameMode;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ProfileSelectionMenu {
    private ProfileSelectionMenu() {}

    private static final long SNAPSHOT_TTL_MILLIS = 1500L;
    private static final long FINALIZE_CHECK_TTL_MILLIS = 10_000L;
    private static final ConcurrentMap<UUID, MenuSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Long> LAST_FINALIZE_CHECK = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Runnable> FORCED_REOPENERS = new ConcurrentHashMap<>();
    private static final Set<UUID> SUPPRESS_NEXT_CLOSE_REOPEN = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> PROFILE_CREATION_IN_PROGRESS = ConcurrentHashMap.newKeySet();

    private record MenuSnapshot(
            List<PlayerProfileManager.ProfileRecord> profiles,
            PlayerProfileManager.ProfileLimit limit,
            long createdAtMillis
    ) {}

    private static MenuSnapshot snapshot(ServerPlayer player) {
        UUID playerId = player.getUUID();
        long now = System.currentTimeMillis();
        MenuSnapshot cached = SNAPSHOTS.get(playerId);
        if (cached != null && now - cached.createdAtMillis() <= SNAPSHOT_TTL_MILLIS) {
            return cached;
        }

        MenuSnapshot fresh = new MenuSnapshot(
                List.copyOf(PlayerProfileManager.listBlocking(player)),
                PlayerProfileManager.limitBlocking(player),
                now
        );
        SNAPSHOTS.put(playerId, fresh);
        return fresh;
    }

    private static void invalidateSnapshot(ServerPlayer player) {
        if (player != null) SNAPSHOTS.remove(player.getUUID());
    }

    private static String finalizePendingDeletesIfDue(ServerPlayer player) {
        if (player == null) return "";
        UUID playerId = player.getUUID();
        long now = System.currentTimeMillis();
        Long last = LAST_FINALIZE_CHECK.get(playerId);
        if (last != null && now - last <= FINALIZE_CHECK_TTL_MILLIS) {
            return "";
        }
        LAST_FINALIZE_CHECK.put(playerId, now);
        return PlayerProfileManager.finalizePendingDeletesBlocking(player);
    }

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
        String finalized = finalizePendingDeletesIfDue(player);
        if (!finalized.isBlank()) {
            invalidateSnapshot(player);
            player.sendSystemMessage(Component.literal(finalized).withStyle(ChatFormatting.GRAY));
        }

        SimpleGui gui = createForcedGui(MenuType.GENERIC_9x3, player, () -> open(player));
        gui.setTitle(Component.literal("Select Profile"));

        MenuSnapshot snapshot = snapshot(player);
        List<PlayerProfileManager.ProfileRecord> profiles = snapshot.profiles();
        PlayerProfileManager.ProfileLimit limit = snapshot.limit();

        int[] slots = {10, 11, 12, 13, 14, 15};
        for (int i = 0; i < profiles.size() && i < slots.length; i++) {
            var profile = profiles.get(i);
            GuiElementBuilder item = new GuiElementBuilder(icon(profile.gameMode()))
                    .hideDefaultTooltip()
                    .setName(Component.literal((profile.active() ? "★ " : "") + profile.profileName()).withStyle(profile.active() ? ChatFormatting.GREEN : ChatFormatting.AQUA))
                    .addLoreLine(Component.literal("Mode: " + profile.gameMode().displayName() + PlayerProfileManager.modeSuffix(profile)).withStyle(ChatFormatting.GRAY));
            if (profile.pendingDelete()) {
                item.addLoreLine(Component.literal("⚠ DELETION QUEUED").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                item.addLoreLine(Component.literal("This profile cannot be loaded right now.").withStyle(ChatFormatting.DARK_RED));
                if (profile.deleteAvailableAt() != null) {
                    item.addLoreLine(Component.literal("Deletes in: " + formatRemaining(profile.deleteAvailableAt())).withStyle(ChatFormatting.GOLD));
                }
                item.addLoreLine(Component.literal("Open Delete Profiles to cancel it.").withStyle(ChatFormatting.YELLOW));
            } else if (profile.active()) {
                item.addLoreLine(Component.literal("Currently loaded").withStyle(ChatFormatting.GREEN));
            } else {
                item.addLoreLine(Component.literal("Click to load this profile").withStyle(ChatFormatting.YELLOW));
            }
            gui.setSlot(slots[i], item.setCallback((index, clickType, action, gui1) -> {
                if (profile.pendingDelete()) return;
                invalidateSnapshot(player);
                gui.setSlot(index, new GuiElementBuilder(Items.CLOCK)
                        .hideDefaultTooltip()
                        .setName(Component.literal("Loading profile...").withStyle(ChatFormatting.YELLOW))
                        .addLoreLine(Component.literal("Please wait. This no longer blocks the whole server.").withStyle(ChatFormatting.GRAY)));
                player.sendSystemMessage(Component.literal("Loading profile " + profile.profileName() + "...").withStyle(ChatFormatting.YELLOW));
                PlayerProfileManager.switchAsync(player, profile.profileName(), result -> {
                    boolean loaded = result.startsWith("Loaded");
                    if (loaded) clearForcedReopener(player);
                    player.sendSystemMessage(Component.literal(result).withStyle(loaded ? ChatFormatting.GREEN : ChatFormatting.RED));
                    if (loaded) {
                        gui.close();
                    } else {
                        navigate(player, () -> open(player));
                    }
                });
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
                        navigate(player, () -> open(player));
                        return;
                    }
                    navigate(player, () -> openCreateModeMenu(player));
                }));

        gui.setSlot(26, new GuiElementBuilder(Items.BARRIER)
                .hideDefaultTooltip()
                .setName(Component.literal("Delete Profiles").withStyle(ChatFormatting.RED))
                .addLoreLine(Component.literal(deletePerkText(limit)).withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Click to choose a profile.").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, action, gui1) -> navigate(player, () -> openDeleteMenu(player))));

        MenuUtil.fillBordersForced(gui, slots[0], slots[1], slots[2], slots[3], slots[4], slots[5], 22, 26);
        gui.open();
    }

    private static void openCreateModeMenu(ServerPlayer player) {
        SimpleGui gui = createForcedGui(MenuType.GENERIC_9x3, player, () -> openCreateModeMenu(player));
        gui.setTitle(Component.literal("Choose Profile Type"));

        setModeButton(gui, player, 10, ProfileGameMode.NORMAL, Items.GRASS_BLOCK,
                "Normal", "Standard profile with normal trading and gameplay.");
        setModeButton(gui, player, 12, ProfileGameMode.IRONMAN, Items.IRON_INGOT,
                "Ironman", "Solo self-found profile with no player trading, auction, chest shops, or handouts.");
        setModeButton(gui, player, 14, ProfileGameMode.MONOTYPE, Items.BLAZE_POWDER,
                "Monotype", "Choose one Pokémon type for battle restrictions next.");
        setModeButton(gui, player, 16, ProfileGameMode.ISLANDER, Items.OAK_SAPLING,
                "Islander", "Island-only survival profile for completing the full dex from Islander areas.");
        setModeButton(gui, player, 22, ProfileGameMode.NUZLOCKE, Items.SKELETON_SKULL,
                "Nuzlocke", "Challenge profile with Ironman-style restrictions and Nuzlocke tracking.");

        MenuUtil.addBackButton(gui, 18, () -> navigate(player, () -> open(player)));
        MenuUtil.fillBordersForced(gui, 10, 12, 14, 16, 18, 22);
        gui.open();
    }

    private static void setModeButton(SimpleGui gui, ServerPlayer player, int slot, ProfileGameMode mode, Item icon, String title, String lore) {
        gui.setSlot(slot, new GuiElementBuilder(icon)
                .hideDefaultTooltip()
                .setName(Component.literal(title).withStyle(ChatFormatting.AQUA))
                .addLoreLine(Component.literal(lore).withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Click to continue.").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, action, gui1) -> navigate(player, () -> openColorMenu(player, mode, null))));
    }

    private static void openColorMenu(ServerPlayer player, ProfileGameMode mode, String monotype) {
        if (player != null && PROFILE_CREATION_IN_PROGRESS.contains(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Profile creation is already in progress. Please wait for it to finish.").withStyle(ChatFormatting.YELLOW));
            return;
        }

        if (mode == ProfileGameMode.MONOTYPE && (monotype == null || monotype.isBlank())) {
            openMonotypeMenu(player);
            return;
        }

        SimpleGui gui = createForcedGui(MenuType.GENERIC_9x6, player, () -> openColorMenu(player, mode, monotype));
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
                    UUID playerId = player.getUUID();
                    if (!PROFILE_CREATION_IN_PROGRESS.add(playerId)) {
                        player.sendSystemMessage(Component.literal("Profile creation is already in progress. Please wait for it to finish.").withStyle(ChatFormatting.YELLOW));
                        return;
                    }

                    invalidateSnapshot(player);
                    gui.setSlot(index, new GuiElementBuilder(Items.CLOCK)
                            .hideDefaultTooltip()
                            .setName(Component.literal("Creating profile...").withStyle(ChatFormatting.YELLOW))
                            .addLoreLine(Component.literal("Please wait. Extra clicks are ignored.").withStyle(ChatFormatting.GRAY)));
                    player.sendSystemMessage(Component.literal("Creating profile " + color.name() + "...").withStyle(ChatFormatting.YELLOW));

                    CompletableFuture
                            .supplyAsync(() -> PlayerProfileManager.createBlocking(player, color.name(), mode, monotype))
                            .whenComplete((result, error) -> player.server.execute(() -> {
                                PROFILE_CREATION_IN_PROGRESS.remove(playerId);

                                String finalResult = result;
                                if (error != null) {
                                    error.printStackTrace();
                                    finalResult = "Could not create profile. Check console/database logs.";
                                }
                                invalidateSnapshot(player);
                                boolean created = finalResult.startsWith("Created");
                                player.sendSystemMessage(Component.literal(finalResult).withStyle(created ? ChatFormatting.GREEN : ChatFormatting.RED));
                                if (created) navigate(player, () -> open(player)); else navigate(player, () -> openColorMenu(player, mode, monotype));
                            }));
                });
            }
            gui.setSlot(slots[i], builder);
        }

        MenuUtil.addBackButton(gui, 45, () -> navigate(player, () -> {
            if (mode == ProfileGameMode.MONOTYPE) {
                openMonotypeMenu(player);
            } else {
                openCreateModeMenu(player);
            }
        }));
        MenuUtil.fillBordersForced(gui, concat(slots, 45));
        gui.open();
    }

    private static void openMonotypeMenu(ServerPlayer player) {
        SimpleGui gui = createForcedGui(MenuType.GENERIC_9x6, player, () -> openMonotypeMenu(player));
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
                    .setCallback((index, clickType, action, gui1) -> navigate(player, () -> openColorMenu(player, ProfileGameMode.MONOTYPE, type))));
        }
        MenuUtil.addBackButton(gui, 45, () -> navigate(player, () -> openCreateModeMenu(player)));
        MenuUtil.fillBordersForced(gui, concat(slots, 45));
        gui.open();
    }

    private static void openDeleteMenu(ServerPlayer player) {
        SimpleGui gui = createForcedGui(MenuType.GENERIC_9x3, player, () -> openDeleteMenu(player));
        gui.setTitle(Component.literal("Delete Profile"));
        List<PlayerProfileManager.ProfileRecord> profiles = snapshot(player).profiles();
        int[] slots = {10, 11, 12, 13, 14, 15};
        for (int i = 0; i < profiles.size() && i < slots.length; i++) {
            var profile = profiles.get(i);
            GuiElementBuilder builder;
            if (profile.active()) {
                builder = new GuiElementBuilder(Items.GRAY_DYE)
                        .hideDefaultTooltip()
                        .setName(Component.literal(profile.profileName()).withStyle(ChatFormatting.DARK_GRAY))
                        .addLoreLine(Component.literal("Load another profile before deleting this one.").withStyle(ChatFormatting.GRAY));
            } else if (profile.pendingDelete()) {
                builder = new GuiElementBuilder(Items.ORANGE_DYE)
                        .hideDefaultTooltip()
                        .setName(Component.literal("Cancel deletion: " + profile.profileName()).withStyle(ChatFormatting.GOLD))
                        .addLoreLine(Component.literal("⚠ This profile is queued for deletion.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                        .addLoreLine(Component.literal("Deletes in: " + formatRemaining(profile.deleteAvailableAt())).withStyle(ChatFormatting.GOLD))
                        .addLoreLine(Component.literal("Click to cancel and restore it.").withStyle(ChatFormatting.YELLOW))
                        .setCallback((index, clickType, action, gui1) -> navigate(player, () -> openCancelDeleteConfirmMenu(player, profile)));
            } else {
                builder = new GuiElementBuilder(Items.BARRIER)
                        .hideDefaultTooltip()
                        .setName(Component.literal(profile.profileName()).withStyle(ChatFormatting.RED))
                        .addLoreLine(Component.literal("Click to review deletion confirmation.").withStyle(ChatFormatting.YELLOW))
                        .setCallback((index, clickType, action, gui1) -> navigate(player, () -> openDeleteConfirmMenu(player, profile)));
            }
            gui.setSlot(slots[i], builder);
        }
        MenuUtil.addBackButton(gui, 18, () -> navigate(player, () -> open(player)));
        MenuUtil.fillBordersForced(gui, slots[0], slots[1], slots[2], slots[3], slots[4], slots[5], 18);
        gui.open();
    }

    private static void openDeleteConfirmMenu(ServerPlayer player, PlayerProfileManager.ProfileRecord profile) {
        SimpleGui gui = createForcedGui(MenuType.GENERIC_9x3, player, () -> openDeleteConfirmMenu(player, profile));
        gui.setTitle(Component.literal("Are you sure?"));

        gui.setSlot(13, new GuiElementBuilder(icon(profile.gameMode()))
                .hideDefaultTooltip()
                .setName(Component.literal("Delete " + profile.profileName() + "?").withStyle(ChatFormatting.RED))
                .addLoreLine(Component.literal("Mode: " + profile.gameMode().displayName() + PlayerProfileManager.modeSuffix(profile)).withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("This cannot be selected until deletion finishes.").withStyle(ChatFormatting.DARK_RED)));

        gui.setSlot(11, new GuiElementBuilder(Items.GREEN_CONCRETE)
                .hideDefaultTooltip()
                .setName(Component.literal("Yes, delete this profile").withStyle(ChatFormatting.GREEN))
                .addLoreLine(Component.literal("Confirm deletion for " + profile.profileName() + ".").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, action, gui1) -> {
                    String result = PlayerProfileManager.deleteBlocking(player, profile.profileName());
                    invalidateSnapshot(player);
                    player.sendSystemMessage(Component.literal(result).withStyle(result.startsWith("Deleted") || result.startsWith("Profile") ? ChatFormatting.GREEN : ChatFormatting.RED));
                    navigate(player, () -> open(player));
                }));

        gui.setSlot(15, new GuiElementBuilder(Items.RED_CONCRETE)
                .hideDefaultTooltip()
                .setName(Component.literal("No, keep this profile").withStyle(ChatFormatting.RED))
                .addLoreLine(Component.literal("Cancel and go back.").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, action, gui1) -> navigate(player, () -> openDeleteMenu(player))));

        MenuUtil.fillBordersForced(gui, 11, 13, 15);
        gui.open();
    }

    private static void openCancelDeleteConfirmMenu(ServerPlayer player, PlayerProfileManager.ProfileRecord profile) {
        SimpleGui gui = createForcedGui(MenuType.GENERIC_9x3, player, () -> openCancelDeleteConfirmMenu(player, profile));
        gui.setTitle(Component.literal("Cancel Deletion?"));

        gui.setSlot(13, new GuiElementBuilder(icon(profile.gameMode()))
                .hideDefaultTooltip()
                .setName(Component.literal("Restore " + profile.profileName() + "?").withStyle(ChatFormatting.GOLD))
                .addLoreLine(Component.literal("This cancels the pending deletion.").withStyle(ChatFormatting.YELLOW))
                .addLoreLine(Component.literal("The profile will become selectable again.").withStyle(ChatFormatting.GRAY)));

        gui.setSlot(11, new GuiElementBuilder(Items.GREEN_CONCRETE)
                .hideDefaultTooltip()
                .setName(Component.literal("Yes, cancel deletion").withStyle(ChatFormatting.GREEN))
                .addLoreLine(Component.literal("Restore this profile.").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, action, gui1) -> {
                    String result = PlayerProfileManager.cancelDeleteBlocking(player, profile.profileName());
                    invalidateSnapshot(player);
                    player.sendSystemMessage(Component.literal(result).withStyle(result.startsWith("Cancelled") ? ChatFormatting.GREEN : ChatFormatting.RED));
                    navigate(player, () -> open(player));
                }));

        gui.setSlot(15, new GuiElementBuilder(Items.RED_CONCRETE)
                .hideDefaultTooltip()
                .setName(Component.literal("No, keep deletion queued").withStyle(ChatFormatting.RED))
                .addLoreLine(Component.literal("Go back without changing it.").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, action, gui1) -> navigate(player, () -> openDeleteMenu(player))));

        MenuUtil.fillBordersForced(gui, 11, 13, 15);
        gui.open();
    }

    private static String deletePerkText(PlayerProfileManager.ProfileLimit limit) {
        if (limit.instantDelete()) return "VIP instant deletion active.";
        if (limit.fastDelete()) return "VIP fast deletion active: " + limit.deletionDelayMinutes() + " minutes.";
        return "Deletion delay: " + limit.deletionDelayMinutes() + " minutes. VIP can be faster.";
    }

    private static String formatRemaining(OffsetDateTime deleteAvailableAt) {
        if (deleteAvailableAt == null) return "soon";
        long seconds = Duration.between(OffsetDateTime.now(), deleteAvailableAt).getSeconds();
        if (seconds <= 0) return "ready now";
        long minutes = (seconds + 59L) / 60L;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60L;
        long mins = minutes % 60L;
        return mins == 0 ? hours + "h" : hours + "h " + mins + "m";
    }

    /**
     * Creates a profile-lobby menu that cannot be escaped while the player is still
     * in the no-profile-loaded lobby state. Each submenu registers itself as the
     * player's current forced menu, so pressing ESC, inventory key, or any client-side
     * close action reopens the same submenu instead of always falling back to the
     * root profile selector.
     */
    private static SimpleGui createForcedGui(MenuType<?> type, ServerPlayer player, Runnable reopenAction) {
        if (player != null && reopenAction != null) {
            FORCED_REOPENERS.put(player.getUUID(), reopenAction);
        }
        SimpleGui gui = new ForcedProfileGui(type, player);
        gui.setLockPlayerInventory(true);
        return gui;
    }

    private static void navigate(ServerPlayer player, Runnable action) {
        if (player != null) {
            SUPPRESS_NEXT_CLOSE_REOPEN.add(player.getUUID());
        }
        action.run();
    }

    public static void reopenForcedOrRoot(ServerPlayer player) {
        if (player == null) return;
        Runnable opener = FORCED_REOPENERS.get(player.getUUID());
        if (opener != null) {
            opener.run();
        } else {
            open(player);
        }
    }

    public static void clearForcedReopener(ServerPlayer player) {
        if (player != null) {
            FORCED_REOPENERS.remove(player.getUUID());
        }
    }

    private static final class ForcedProfileGui extends SimpleGui {
        private final ServerPlayer owner;

        private ForcedProfileGui(MenuType<?> type, ServerPlayer owner) {
            super(type, owner, false);
            this.owner = owner;
            this.setLockPlayerInventory(true);
        }

        @Override
        public void onClose() {
            super.onClose();
            if (owner == null || owner.server == null) return;
            if (SUPPRESS_NEXT_CLOSE_REOPEN.remove(owner.getUUID())) return;
            owner.server.execute(() -> {
                if (owner.isRemoved() || owner.hasDisconnected()) return;
                if (!ProfileLobbyLockManager.isLocked(owner) || ProfileLobbyLockManager.hasBypass(owner)) {
                    clearForcedReopener(owner);
                    return;
                }
                reopenForcedOrRoot(owner);
            });
        }
    }

    private static Set<String> usedProfileNames(ServerPlayer player) {
        Set<String> used = new HashSet<>();
        for (PlayerProfileManager.ProfileRecord profile : snapshot(player).profiles()) {
            if (profile != null && !profile.pendingDelete()) used.add(profile.profileName().toLowerCase(Locale.ROOT));
        }
        return used;
    }

    private static Item icon(ProfileGameMode mode) {
        return switch (mode) {
            case IRONMAN -> Items.IRON_INGOT;
            case MONOTYPE -> Items.BLAZE_POWDER;
            case ISLANDER -> Items.OAK_SAPLING;
            case NUZLOCKE -> Items.SKELETON_SKULL;
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
