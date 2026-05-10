package com.champutils.menu;

import com.champutils.profession.ProfessionDataManager;
import com.champutils.profession.ProfessionType;
import com.champutils.profile.PlayerDataManager;

import com.mojang.authlib.GameProfile;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

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

public class ProfessionLeaderboardMenu {

    public static void open(
            ServerPlayer player
    ) {
        openOverall(
                player
        );
    }

    public static void openOverall(
            ServerPlayer player
    ) {
        open(
                player,
                null
        );
    }

    public static void openProfession(
            ServerPlayer player,
            ProfessionType type
    ) {
        open(
                player,
                type
        );
    }

    private static void open(
            ServerPlayer player,
            ProfessionType selected
    ) {
        SimpleGui gui =
                new SimpleGui(
                        MenuType.GENERIC_9x6,
                        player,
                        false
                );

        gui.setTitle(
                Component.literal(
                        selected == null
                                ? "Profession Leaderboard - Overall"
                                : "Profession Leaderboard - " + format(selected)
                )
        );

        MenuUtil.fillBorders(
                gui,
                0,1,2,3,4,
                10,11,12,13,14,15,16,
                19,20,21,22,23,24,25,
                28,29,30,31,32,33,34,
                37,38,39,40,41,42,43,
                49
        );

        setTab(
                gui,
                0,
                Items.NETHER_STAR,
                "§6Overall",
                selected == null,
                () -> openOverall(
                        player
                )
        );

        setTab(
                gui,
                1,
                Items.DIAMOND_SWORD,
                "§bBattling",
                selected == ProfessionType.BATTLING,
                () -> openProfession(
                        player,
                        ProfessionType.BATTLING
                )
        );

        setTab(
                gui,
                2,
                Items.DIAMOND_PICKAXE,
                "§bMining",
                selected == ProfessionType.MINING,
                () -> openProfession(
                        player,
                        ProfessionType.MINING
                )
        );

        setTab(
                gui,
                3,
                Items.DIAMOND_AXE,
                "§bForestry",
                selected == ProfessionType.FORESTRY,
                () -> openProfession(
                        player,
                        ProfessionType.FORESTRY
                )
        );

        setTab(
                gui,
                4,
                Items.DIAMOND_HOE,
                "§bFarming",
                selected == ProfessionType.FARMING,
                () -> openProfession(
                        player,
                        ProfessionType.FARMING
                )
        );

        List<ProfessionDataManager.ProfessionData> top =
                new ArrayList<>(
                        ProfessionDataManager.getAllPlayers()
                );

        top.sort(
                Comparator.comparingInt(
                                (ProfessionDataManager.ProfessionData data) ->
                                        score(
                                                data,
                                                selected
                                        )
                        )
                        .reversed()
        );

        int[] slots = {
                10,11,12,13,14,15,16,
                19,20,21,22,23,24,25,
                28,29,30,31,32,33,34,
                37,38,39,40,41,42,43
        };

        int count =
                Math.min(
                        top.size(),
                        slots.length
                );

        for (int i = 0; i < count; i++) {
            ProfessionDataManager.ProfessionData entry =
                    top.get(i);

            int value =
                    score(
                            entry,
                            selected
                    );

            if (value <= 0) {
                continue;
            }

            gui.setSlot(
                    slots[i],
                    buildPlayerHead(
                            player,
                            entry,
                            i + 1,
                            value,
                            selected
                    )
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
            int slot,
            Item icon,
            String name,
            boolean selected,
            Runnable action
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
                (i, c, t) -> action.run()
        );

        gui.setSlot(
                slot,
                builder
        );
    }

    private static GuiElementBuilder buildPlayerHead(
            ServerPlayer viewer,
            ProfessionDataManager.ProfessionData entry,
            int rank,
            int value,
            ProfessionType selected
    ) {
        ItemStack head =
                new ItemStack(
                        Items.PLAYER_HEAD
                );

        applyProfile(
                viewer,
                head,
                entry
        );

        String medal =
                switch (rank) {
                    case 1 -> "§6#1 ";
                    case 2 -> "§7#2 ";
                    case 3 -> "§c#3 ";
                    default -> "§e#" + rank + " ";
                };

        String label =
                selected == null
                        ? "Overall Level"
                        : format(selected) + " Level";

        return new GuiElementBuilder(
                head
        )
                .setName(
                        Component.literal(
                                medal + "§f" + entry.name
                        )
                )
                .addLoreLine(
                        Component.literal(
                                "§6" + label + ": §f" + value
                        )
                )
                .addLoreLine(
                        Component.literal(
                                "§7Click to inspect profile"
                        )
                )
                .setCallback(
                        (index, click, type) ->
                                PlayerProfileMenu.open(
                                        viewer,
                                        entry.name
                                )
                );
    }

    private static void applyProfile(
            ServerPlayer viewer,
            ItemStack head,
            ProfessionDataManager.ProfessionData entry
    ) {
        try {
            ServerPlayer online =
                    viewer.server
                            .getPlayerList()
                            .getPlayerByName(
                                    entry.name
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

            try {
                UUID uuid =
                        UUID.fromString(
                                entry.uuid
                        );

                head.set(
                        DataComponents.PROFILE,
                        new ResolvableProfile(
                                new GameProfile(
                                        uuid,
                                        entry.name
                                )
                        )
                );

                return;
            } catch (Exception ignored) {}

            for (var p :
                    PlayerDataManager.getAllPlayers()) {
                if (p.name.equalsIgnoreCase(entry.name)) {
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
                                    entry.name
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
            ProfessionType selected
    ) {
        ProfessionDataManager.ensureProfessionDefaults(
                data
        );

        if (selected == null) {
            return ProfessionDataManager.getOverallLevel(
                    data
            );
        }

        return data.levels.getOrDefault(
                selected.name(),
                1
        );
    }

    private static String format(
            ProfessionType type
    ) {
        String lower =
                type.name().toLowerCase();

        return Character.toUpperCase(
                lower.charAt(0)
        ) + lower.substring(1);
    }
}
