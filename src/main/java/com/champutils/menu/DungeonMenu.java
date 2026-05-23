package com.champutils.menu;

import com.champutils.dungeon.DungeonConfig;
import com.champutils.dungeon.DungeonCrateCreditManager;
import com.champutils.dungeon.DungeonCrateOpeningGui;
import com.champutils.dungeon.DungeonDigitalKeyManager;
import com.champutils.dungeon.DungeonKeyConfig;
import com.champutils.dungeon.DungeonKeyManager;
import com.champutils.dungeon.DungeonManager;
import com.champutils.dungeon.DungeonNativeCrateRegistry;
import com.champutils.dungeon.DungeonRarity;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Map;

public final class DungeonMenu {

    private static final int DUNGEONS_BUTTON_SLOT = 2;
    private static final int KEYS_INFO_SLOT = 4;
    private static final int CHESTS_BUTTON_SLOT = 6;

    private static final int[] DUNGEON_SLOTS = new int[]{10, 11, 12, 13, 14, 15};
    private static final int[] NORMAL_CHEST_SLOTS = new int[]{10, 11, 12, 13, 14, 15};
    private static final int[] POKEMON_CHEST_SLOTS = new int[]{19, 20, 21, 22, 23, 24};

    private DungeonMenu() {
    }

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x1, player);
        gui.setTitle(Component.literal("Expeditions"));

        gui.setSlot(
                DUNGEONS_BUTTON_SLOT,
                new GuiElementBuilder(Items.MAP)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§aExpeditions").withStyle(ChatFormatting.BOLD))
                        .addLoreLine(Component.literal("§7View available expeditions."))
                        .addLoreLine(Component.literal("§7Expedition entry requires expedition keys."))
                        .addLoreLine(Component.literal("§eClick to open"))
                        .setCallback((i, c, t) -> openExpeditions(player))
        );

        gui.setSlot(
                KEYS_INFO_SLOT,
                new GuiElementBuilder(Items.TRIPWIRE_HOOK)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§6Your Expedition Keys").withStyle(ChatFormatting.BOLD))
                        .addLoreLine(Component.literal("§7Expedition keys cannot be dropped,"))
                        .addLoreLine(Component.literal("§7traded, or duplicated."))
                        .addLoreLine(Component.literal("§8Total: §f" + DungeonDigitalKeyManager.getTotalKeys(player.getUUID())))
                        .addLoreLine(Component.literal(" "))
                        .addLoreLine(Component.literal(formatKeyLine(player, "common_dungeon_key")))
                        .addLoreLine(Component.literal(formatKeyLine(player, "uncommon_dungeon_key")))
                        .addLoreLine(Component.literal(formatKeyLine(player, "rare_dungeon_key")))
                        .addLoreLine(Component.literal(formatKeyLine(player, "epic_dungeon_key")))
                        .addLoreLine(Component.literal(formatKeyLine(player, "legendary_dungeon_key")))
                        .addLoreLine(Component.literal(formatKeyLine(player, "mythic_dungeon_key")))
        );

        gui.setSlot(
                CHESTS_BUTTON_SLOT,
                new GuiElementBuilder(gildedChest("cobblemon:gilded_chest"))
                        .hideDefaultTooltip()
                        .setName(Component.literal("§bCrates").withStyle(ChatFormatting.BOLD))
                        .addLoreLine(Component.literal("§7Open earned crate rewards."))
                        .addLoreLine(Component.literal("§7Crates can be earned from many activities."))
                        .addLoreLine(Component.literal("§eClick to open"))
                        .setCallback((i, c, t) -> openChests(player))
        );

        gui.open();
    }

    public static void openExpeditions(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Dungeon List"));

        int index = 0;
        for (Map.Entry<String, DungeonConfig.DungeonData> entry : DungeonConfig.DUNGEONS.entrySet()) {
            if (index >= DUNGEON_SLOTS.length) break;

            String dungeonId = entry.getKey();
            DungeonConfig.DungeonData data = entry.getValue();
            DungeonRarity rarity = DungeonRarity.parse(data.rarity);
            int keyCount = DungeonKeyManager.getKeyCount(player, data.keyId);
            int level = rarityLevel(rarity);

            gui.setSlot(
                    DUNGEON_SLOTS[index++],
                    new GuiElementBuilder(iconFor(rarity))
                            .hideDefaultTooltip()
                            .setName(Component.literal(rarityPrefix(rarity) + safeDisplayName(dungeonId, data)).withStyle(ChatFormatting.BOLD))
                            .addLoreLine(Component.literal("§7Tier: " + rarity.name()))
                            .addLoreLine(Component.literal("§7Pokemon Level: " + level))
                            .addLoreLine(Component.literal("§7Trainers: " + Math.max(1, data.trainerCount)))
                            .addLoreLine(Component.literal("§7Digital Key: §f" + keyCount + "§7x"))
                            .addLoreLine(Component.literal(keyCount > 0 ? "§eClick to enter" : "§cYou need a digital key"))
                            .setCallback((i, c, t) -> {
                                player.closeContainer();
                                DungeonManager.startDungeon(player, dungeonId);
                            })
            );
        }

        MenuUtil.addBackButton(gui, 26, () -> open(player));
        gui.open();
    }

    public static void openChests(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x4, player);
        gui.setTitle(Component.literal("Crates"));

        gui.setSlot(
                4,
                new GuiElementBuilder(gildedChest("cobblemon:gilded_chest"))
                        .hideDefaultTooltip()
                        .setName(Component.literal("§6Your Chest Credits").withStyle(ChatFormatting.BOLD))
                        .addLoreLine(Component.literal("§7Hover here to view your current"))
                        .addLoreLine(Component.literal("§7normal and Pokemon chest credits."))
                        .addLoreLine(Component.literal(" "))
                        .addLoreLine(Component.literal(formatCreditsLine(player, DungeonRarity.COMMON)))
                        .addLoreLine(Component.literal(formatCreditsLine(player, DungeonRarity.UNCOMMON)))
                        .addLoreLine(Component.literal(formatCreditsLine(player, DungeonRarity.RARE)))
                        .addLoreLine(Component.literal(formatCreditsLine(player, DungeonRarity.EPIC)))
                        .addLoreLine(Component.literal(formatCreditsLine(player, DungeonRarity.LEGENDARY)))
                        .addLoreLine(Component.literal(formatCreditsLine(player, DungeonRarity.MYTHIC)))
        );

        DungeonRarity[] rarities = DungeonRarity.values();
        for (int index = 0; index < rarities.length && index < NORMAL_CHEST_SLOTS.length; index++) {
            DungeonRarity rarity = rarities[index];
            int normalCredits = DungeonCrateCreditManager.getNormalCredits(player.getUUID(), rarity);
            int pokemonCredits = DungeonCrateCreditManager.getPokemonCredits(player.getUUID(), rarity);

            gui.setSlot(
                    NORMAL_CHEST_SLOTS[index],
                    new GuiElementBuilder(chestIconFor(rarity))
                            .hideDefaultTooltip()
                            .setName(Component.literal(rarityPrefix(rarity) + nice(rarity.name()) + " Loot Chest").withStyle(ChatFormatting.BOLD))
                            .addLoreLine(Component.literal("§7Credits: §f" + normalCredits))
                            .addLoreLine(Component.literal(normalCredits > 0 ? "§eClick to open" : "§cNo credits available"))
                            .setCallback((i, c, t) -> DungeonCrateOpeningGui.open(player, rarity, DungeonNativeCrateRegistry.CrateType.NORMAL))
            );

            gui.setSlot(
                    POKEMON_CHEST_SLOTS[index],
                    new GuiElementBuilder(chestIconFor(rarity))
                            .hideDefaultTooltip()
                            .setName(Component.literal(rarityPrefix(rarity) + nice(rarity.name()) + " Pokemon Chest").withStyle(ChatFormatting.BOLD))
                            .addLoreLine(Component.literal("§7Credits: §f" + pokemonCredits))
                            .addLoreLine(Component.literal(pokemonCredits > 0 ? "§eClick to open" : "§cNo credits available"))
                            .setCallback((i, c, t) -> DungeonCrateOpeningGui.open(player, rarity, DungeonNativeCrateRegistry.CrateType.POKEMON))
            );
        }

        MenuUtil.addBackButton(gui, 35, () -> open(player));
        gui.open();
    }

    private static String safeDisplayName(String dungeonId, DungeonConfig.DungeonData data) {
        if (data == null || data.displayName == null || data.displayName.isBlank()) return dungeonId;
        return data.displayName;
    }

    private static String formatKeyLine(ServerPlayer player, String keyId) {
        DungeonKeyConfig.KeyData data = DungeonKeyConfig.KEYS.get(keyId);
        String displayName = data == null || data.displayName == null || data.displayName.isBlank() ? keyId : data.displayName;
        int amount = DungeonKeyManager.getKeyCount(player, keyId);
        return "§7" + displayName + ": §f" + amount;
    }

    private static String formatCreditsLine(ServerPlayer player, DungeonRarity rarity) {
        int normal = DungeonCrateCreditManager.getNormalCredits(player.getUUID(), rarity);
        int pokemon = DungeonCrateCreditManager.getPokemonCredits(player.getUUID(), rarity);
        return "§7" + nice(rarity.name()) + ": §f" + normal + "§7 normal, §f" + pokemon + "§7 Pokemon";
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

    private static Item iconFor(DungeonRarity rarity) {
        return switch (rarity) {
            case COMMON -> Items.STONE_SWORD;
            case UNCOMMON -> Items.IRON_SWORD;
            case RARE -> Items.DIAMOND_SWORD;
            case EPIC -> Items.ENCHANTED_BOOK;
            case LEGENDARY -> Items.NETHER_STAR;
            case MYTHIC -> Items.DRAGON_EGG;
        };
    }

    private static Item chestIconFor(DungeonRarity rarity) {
        return switch (rarity) {
            case COMMON -> gildedChest("cobblemon:white_gilded_chest");
            case UNCOMMON -> gildedChest("cobblemon:green_gilded_chest");
            case RARE -> gildedChest("cobblemon:blue_gilded_chest");
            case EPIC -> gildedChest("cobblemon:pink_gilded_chest");
            case LEGENDARY -> gildedChest("cobblemon:yellow_gilded_chest");
            case MYTHIC -> gildedChest("cobblemon:black_gilded_chest");
        };
    }

    private static Item gildedChest(String itemId) {
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            return item == null || item == Items.AIR ? Items.CHEST : item;
        } catch (Exception ignored) {
            return Items.CHEST;
        }
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

    private static String nice(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        String lower = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        String[] words = lower.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }
}
