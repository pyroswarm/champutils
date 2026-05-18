package com.champutils.menu;

import com.champutils.profession.ProfessionDataManager;
import com.champutils.profession.ProfessionType;
import com.champutils.profile.PlayerDataManager;
import com.champutils.rank.LeaderboardManager;
import com.champutils.rank.LeaderboardManager.Entry;

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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LeaderboardMenu {

    private enum BoardMode {
        RP,
        OVERALL,
        BATTLING,
        MINING,
        FORESTRY,
        FARMING
    }

    public static void open(
            ServerPlayer player
    ) {
        open(
                player,
                BoardMode.RP
        );
    }

    public static void openProfessionOverall(
            ServerPlayer player
    ) {
        open(
                player,
                BoardMode.OVERALL
        );
    }

    public static void openProfession(
            ServerPlayer player,
            ProfessionType type
    ) {
        open(
                player,
                modeForProfession(
                        type
                )
        );
    }

    private static void open(
            ServerPlayer player,
            BoardMode mode
    ) {
        SimpleGui gui =
                new SimpleGui(
                        MenuType.GENERIC_9x6,
                        player,
                        false
                );

        gui.setTitle(
                Component.literal(
                        titleFor(
                                mode
                        )
                )
        );

        MenuUtil.fillBorders(
                gui,
                0,1,2,3,4,5,
                10,11,12,13,14,15,16,
                19,20,21,22,23,24,25,
                28,29,30,31,32,33,34,
                37,38,39,40,41,42,43,
                49
        );

        setTab(
                gui,
                player,
                0,
                Items.NETHER_STAR,
                "§6RP",
                mode == BoardMode.RP,
                BoardMode.RP
        );

        setTab(
                gui,
                player,
                1,
                Items.EXPERIENCE_BOTTLE,
                "§aOverall",
                mode == BoardMode.OVERALL,
                BoardMode.OVERALL
        );

        setTab(
                gui,
                player,
                2,
                Items.DIAMOND_SWORD,
                "§bBattling",
                mode == BoardMode.BATTLING,
                BoardMode.BATTLING
        );

        setTab(
                gui,
                player,
                3,
                Items.DIAMOND_PICKAXE,
                "§bMining",
                mode == BoardMode.MINING,
                BoardMode.MINING
        );

        setTab(
                gui,
                player,
                4,
                Items.DIAMOND_AXE,
                "§bForestry",
                mode == BoardMode.FORESTRY,
                BoardMode.FORESTRY
        );

        setTab(
                gui,
                player,
                5,
                Items.DIAMOND_HOE,
                "§bFarming",
                mode == BoardMode.FARMING,
                BoardMode.FARMING
        );

        gui.setSlot(
                8,
                new GuiElementBuilder(
                        Items.WRITABLE_BOOK
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§dSeason History"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7View archived season results."
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eClick to open"
                                )
                        )
                        .setCallback(
                                (i, c, t) -> SeasonHistoryMenu.open(player)
                        )
        );

        if (mode == BoardMode.RP) {
            fillRpLeaderboard(
                    gui,
                    player
            );
        } else {
            fillProfessionLeaderboard(
                    gui,
                    player,
                    mode
            );
        }

        MenuUtil.addBackButton(
                gui,
                49,
                () -> MainMenu.open(
                        player
                )
        );

        gui.open();
    }

    private static void setTab(
            SimpleGui gui,
            ServerPlayer player,
            int slot,
            Item icon,
            String name,
            boolean selected,
            BoardMode mode
    ) {
        GuiElementBuilder builder =
                new GuiElementBuilder(
                        icon
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        (selected ? "§a▶ " : "") + name
                                )
                        );

        if (selected) {
            builder.addLoreLine(
                    Component.literal(
                            "§7Currently viewing"
                    )
            );
        } else {
            builder.addLoreLine(
                    Component.literal(
                            "§eClick to view"
                    )
            );
        }

        builder.setCallback(
                (i, c, t) -> open(
                        player,
                        mode
                )
        );

        gui.setSlot(
                slot,
                builder
        );
    }

    private static void fillRpLeaderboard(
            SimpleGui gui,
            ServerPlayer player
    ) {
        List<Entry> top =
                LeaderboardManager.getTop(
                        28
                );

        int[] slots =
                contentSlots();

        int count =
                Math.min(
                        top.size(),
                        slots.length
                );

        for (int i = 0; i < count; i++) {
            Entry entry =
                    top.get(i);

            gui.setSlot(
                    slots[i],
                    buildRpHead(
                            player,
                            entry,
                            i + 1
                    )
            );
        }
    }

    private static void fillProfessionLeaderboard(
            SimpleGui gui,
            ServerPlayer player,
            BoardMode mode
    ) {
        List<ProfessionDataManager.ProfessionData> top =
                new ArrayList<>(
                        ProfessionDataManager.getAllPlayers()
                );

        top.sort(
                Comparator.comparingInt(
                                (ProfessionDataManager.ProfessionData data) -> score(
                                        data,
                                        mode
                                )
                        )
                        .reversed()
        );

        int[] slots =
                contentSlots();

        int written =
                0;

        for (int i = 0; i < top.size() && written < slots.length; i++) {
            ProfessionDataManager.ProfessionData entry =
                    top.get(i);

            int value =
                    score(
                            entry,
                            mode
                    );

            if (value <= 0) {
                continue;
            }

            gui.setSlot(
                    slots[written],
                    buildProfessionHead(
                            player,
                            entry,
                            written + 1,
                            value,
                            mode
                    )
            );

            written++;
        }
    }

    private static GuiElementBuilder buildRpHead(
            ServerPlayer viewer,
            Entry entry,
            int rank
    ) {
        ItemStack head =
                new ItemStack(
                        Items.PLAYER_HEAD
                );

        applyProfile(
                viewer,
                head,
                entry.playerName
        );

        return new GuiElementBuilder(
                head
        )
                .setName(
                        Component.literal(
                                medal(
                                        rank
                                ) + "§f" + entry.playerName
                        )
                )
                .addLoreLine(
                        Component.literal(
                                "§6RP: §f" + entry.rp
                        )
                )
                .addLoreLine(
                        Component.literal(
                                "§7Click to inspect profile"
                        )
                )
                .setCallback(
                        (index, click, type) -> PlayerProfileMenu.open(
                                viewer,
                                entry.playerName
                        )
                );
    }

    private static GuiElementBuilder buildProfessionHead(
            ServerPlayer viewer,
            ProfessionDataManager.ProfessionData entry,
            int rank,
            int value,
            BoardMode mode
    ) {
        ItemStack head =
                new ItemStack(
                        Items.PLAYER_HEAD
                );

        applyProfile(
                viewer,
                head,
                entry.name
        );

        return new GuiElementBuilder(
                head
        )
                .setName(
                        Component.literal(
                                medal(
                                        rank
                                ) + "§f" + entry.name
                        )
                )
                .addLoreLine(
                        Component.literal(
                                "§6" + labelFor(
                                        mode
                                ) + ": §f" + value
                        )
                )
                .addLoreLine(
                        Component.literal(
                                "§7Click to inspect profile"
                        )
                )
                .setCallback(
                        (index, click, type) -> PlayerProfileMenu.open(
                                viewer,
                                entry.name
                        )
                );
    }

    private static void applyProfile(
            ServerPlayer viewer,
            ItemStack head,
            String playerName
    ) {
        try {
            ServerPlayer online =
                    viewer.server
                            .getPlayerList()
                            .getPlayerByName(
                                    playerName
                            );

            if (online != null) {
                head.set(
                        DataComponents.PROFILE,
                        new ResolvableProfile(
                                online.getGameProfile()
                        )
                );

                return;
            }

            for (var p :
                    PlayerDataManager.getAllPlayers()) {
                if (p.name.equalsIgnoreCase(
                        playerName
                )) {
                    try {
                        UUID uuid =
                                UUID.fromString(
                                        p.uuid
                                );

                        head.set(
                                DataComponents.PROFILE,
                                new ResolvableProfile(
                                        new GameProfile(
                                                uuid,
                                                p.name
                                        )
                                )
                        );

                        return;
                    } catch (Exception ignored) {}
                }
            }

            for (var p :
                    ProfessionDataManager.getAllPlayers()) {
                if (p.name.equalsIgnoreCase(
                        playerName
                )) {
                    try {
                        UUID uuid =
                                UUID.fromString(
                                        p.uuid
                                );

                        head.set(
                                DataComponents.PROFILE,
                                new ResolvableProfile(
                                        new GameProfile(
                                                uuid,
                                                p.name
                                        )
                                )
                        );

                        return;
                    } catch (Exception ignored) {}
                }
            }

            Optional<GameProfile> cached =
                    viewer.server
                            .getProfileCache()
                            .get(
                                    playerName
                            );

            cached.ifPresent(
                    profile -> head.set(
                            DataComponents.PROFILE,
                            new ResolvableProfile(
                                    profile
                            )
                    )
            );
        } catch (Exception ignored) {}
    }

    private static int score(
            ProfessionDataManager.ProfessionData data,
            BoardMode mode
    ) {
        ProfessionDataManager.ensureProfessionDefaults(
                data
        );

        if (mode == BoardMode.OVERALL) {
            return ProfessionDataManager.getOverallLevel(
                    data
            );
        }

        ProfessionType type =
                professionForMode(
                        mode
                );

        if (type == null) {
            return 0;
        }

        return data.levels.getOrDefault(
                type.name(),
                1
        );
    }

    private static ProfessionType professionForMode(
            BoardMode mode
    ) {
        return switch (mode) {
            case BATTLING -> ProfessionType.BATTLING;
            case MINING -> ProfessionType.MINING;
            case FORESTRY -> ProfessionType.FORESTRY;
            case FARMING -> ProfessionType.FARMING;
            default -> null;
        };
    }

    private static BoardMode modeForProfession(
            ProfessionType type
    ) {
        if (type == null) {
            return BoardMode.OVERALL;
        }

        return switch (type) {
            case BATTLING -> BoardMode.BATTLING;
            case MINING -> BoardMode.MINING;
            case FORESTRY -> BoardMode.FORESTRY;
            case FARMING -> BoardMode.FARMING;
        };
    }

    private static String titleFor(
            BoardMode mode
    ) {
        return switch (mode) {
            case RP -> "Leaderboard - RP";
            case OVERALL -> "Leaderboard - Overall Level";
            case BATTLING -> "Leaderboard - Battling";
            case MINING -> "Leaderboard - Mining";
            case FORESTRY -> "Leaderboard - Forestry";
            case FARMING -> "Leaderboard - Farming";
        };
    }

    private static String labelFor(
            BoardMode mode
    ) {
        return switch (mode) {
            case OVERALL -> "Overall Level";
            case BATTLING -> "Battling Level";
            case MINING -> "Mining Level";
            case FORESTRY -> "Forestry Level";
            case FARMING -> "Farming Level";
            default -> "Value";
        };
    }

    private static String medal(
            int rank
    ) {
        return switch (rank) {
            case 1 -> "§6#1 ";
            case 2 -> "§7#2 ";
            case 3 -> "§c#3 ";
            default -> "§e#" + rank + " ";
        };
    }

    private static int[] contentSlots() {
        return new int[]{
                10,11,12,13,14,15,16,
                19,20,21,22,23,24,25,
                28,29,30,31,32,33,34,
                37,38,39,40,41,42,43
        };
    }
}
