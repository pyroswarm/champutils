package com.champutils.menu;

import com.champutils.dungeon.DungeonConfig;
import com.champutils.dungeon.DungeonCrateCreditManager;
import com.champutils.dungeon.DungeonManager;
import com.champutils.dungeon.DungeonRarity;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.Map;

public final class DungeonMenu {

    private static final int[] DUNGEON_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private DungeonMenu() {}

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x4, player);
        gui.setTitle(Component.literal("Dungeons"));
        MenuUtil.fillBorders(gui, combine(DUNGEON_SLOTS, 27, 30));

        int index = 0;
        for (Map.Entry<String, DungeonConfig.DungeonData> entry : DungeonConfig.DUNGEONS.entrySet()) {
            if (index >= DUNGEON_SLOTS.length) break;
            String dungeonId = entry.getKey();
            DungeonConfig.DungeonData data = entry.getValue();
            DungeonRarity rarity = DungeonRarity.parse(data.rarity);
            int level = rarityLevel(rarity);

            gui.setSlot(
                    DUNGEON_SLOTS[index++],
                    new GuiElementBuilder(iconFor(rarity))
                            .hideDefaultTooltip()
                            .setName(Component.literal(rarityPrefix(rarity) + safeDisplayName(dungeonId, data)))
                            .addLoreLine(Component.literal("§7Tier: " + rarity.name()))
                            .addLoreLine(Component.literal("§7Pokemon Level: " + level))
                            .addLoreLine(Component.literal("§7Trainers: " + Math.max(1, data.trainerCount)))
                            .addLoreLine(Component.literal("§7Key: " + data.keyId))
                            .addLoreLine(Component.literal("§eClick to enter"))
                            .setCallback((i, c, t) -> {
                                player.closeContainer();
                                DungeonManager.startDungeon(player, dungeonId);
                            })
            );
        }

        gui.setSlot(
                30,
                new GuiElementBuilder(Items.CHEST)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§6Crate Credits"))
                        .addLoreLine(Component.literal("§7Normal/Pokemon spawn crate credits"))
                        .addLoreLine(Component.literal(formatCreditsLine(player, DungeonRarity.COMMON)))
                        .addLoreLine(Component.literal(formatCreditsLine(player, DungeonRarity.UNCOMMON)))
                        .addLoreLine(Component.literal(formatCreditsLine(player, DungeonRarity.RARE)))
                        .addLoreLine(Component.literal(formatCreditsLine(player, DungeonRarity.EPIC)))
                        .addLoreLine(Component.literal(formatCreditsLine(player, DungeonRarity.LEGENDARY)))
                        .addLoreLine(Component.literal(formatCreditsLine(player, DungeonRarity.MYTHIC)))
        );

        MenuUtil.addBackButton(gui, 27, () -> MainMenu.open(player));

        gui.open();
    }

    private static int[] combine(int[] base, int... extra) {
        int[] combined = new int[base.length + extra.length];
        System.arraycopy(base, 0, combined, 0, base.length);
        System.arraycopy(extra, 0, combined, base.length, extra.length);
        return combined;
    }

    private static String safeDisplayName(String dungeonId, DungeonConfig.DungeonData data) {
        if (data == null || data.displayName == null || data.displayName.isBlank()) return dungeonId;
        return data.displayName;
    }

    private static String formatCreditsLine(ServerPlayer player, DungeonRarity rarity) {
        int normal = DungeonCrateCreditManager.getNormalCredits(player.getUUID(), rarity);
        int pokemon = DungeonCrateCreditManager.getPokemonCredits(player.getUUID(), rarity);
        return "§7" + rarity.name() + ": §f" + normal + "§7 normal, §f" + pokemon + "§7 Pokemon";
    }

    private static String rarityPrefix(DungeonRarity rarity) {
        return switch (rarity) {
            case COMMON -> "§f";
            case UNCOMMON -> "§a";
            case RARE -> "§9";
            case EPIC -> "§5";
            case LEGENDARY -> "§6";
            case MYTHIC -> "§d";
        };
    }

    private static net.minecraft.world.item.Item iconFor(DungeonRarity rarity) {
        return switch (rarity) {
            case COMMON -> Items.STONE_SWORD;
            case UNCOMMON -> Items.IRON_SWORD;
            case RARE -> Items.DIAMOND_SWORD;
            case EPIC -> Items.ENCHANTED_BOOK;
            case LEGENDARY -> Items.NETHER_STAR;
            case MYTHIC -> Items.DRAGON_EGG;
        };
    }

    private static int rarityLevel(DungeonRarity rarity) {
        return switch (rarity) {
            case COMMON -> 20;
            case UNCOMMON -> 40;
            case RARE -> 60;
            case EPIC -> 80;
            case LEGENDARY -> 90;
            case MYTHIC -> 100;
        };
    }
}
