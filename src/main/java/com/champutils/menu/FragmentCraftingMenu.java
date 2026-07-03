package com.champutils.menu;

import com.champutils.economy.EconomyManager;
import com.champutils.profession.ProfessionFragmentConfig;
import com.champutils.profession.ProfessionFragmentManager;
import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolManager;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class FragmentCraftingMenu {
    private FragmentCraftingMenu() {}

    public static void open(ServerPlayer player) { open(player, GearWorkshopMenu::open); }

    public static void open(ServerPlayer player, Consumer<ServerPlayer> backTarget) {
        openTabs(player, backTarget);
    }

    private static void openTabs(ServerPlayer player, Consumer<ServerPlayer> backTarget) {
        SimpleGui gui = base(player, "Fragment Crafting");
        gui.setSlot(4, new GuiElementBuilder(Items.EMERALD).hideDefaultTooltip()
                .setName(Component.literal("§aFragment Crafting"))
                .addLoreLine(Component.literal("§7Choose an item type or fragment action.")));

        addTab(gui, player, 10, "Pickaxe", Items.DIAMOND_PICKAXE, () -> openCraft(player, backTarget, "pickaxe", "Pickaxe", Items.DIAMOND_PICKAXE));
        addTab(gui, player, 11, "Axe", Items.DIAMOND_AXE, () -> openCraft(player, backTarget, "axe", "Axe", Items.DIAMOND_AXE));
        addTab(gui, player, 12, "Hoe", Items.DIAMOND_HOE, () -> openCraft(player, backTarget, "hoe", "Hoe", Items.DIAMOND_HOE));
        addTab(gui, player, 13, "Shovel", Items.DIAMOND_SHOVEL, () -> openCraft(player, backTarget, "shovel", "Shovel", Items.DIAMOND_SHOVEL));
        addTab(gui, player, 14, "Sword", Items.DIAMOND_SWORD, () -> openCraft(player, backTarget, "sword", "Sword", Items.DIAMOND_SWORD));
        addTab(gui, player, 15, "Armor", Items.DIAMOND_CHESTPLATE, () -> openArmorTabs(player, backTarget));
        addTab(gui, player, 16, "Trinkets", Items.AMETHYST_SHARD, () -> openTrinketTabs(player, backTarget));
        addTab(gui, player, 20, "Upgrade Fragment", Items.AMETHYST_SHARD, () -> openUpgrade(player, backTarget, false));
        addTab(gui, player, 21, "Downgrade Fragment", Items.PAPER, () -> openUpgrade(player, backTarget, true));
        addTab(gui, player, 22, "Withdraw Fragment", Items.CHEST, () -> openWithdraw(player, backTarget));

        MenuUtil.addBackButton(gui, 49, () -> { if (backTarget != null) backTarget.accept(player); else GearWorkshopMenu.open(player); });
        gui.open();
    }

    private static SimpleGui base(ServerPlayer player, String title) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setTitle(Component.literal(title));
        MenuUtil.fillBorders(gui, 4, 10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43, 49);
        return gui;
    }

    private static void addTab(SimpleGui gui, ServerPlayer player, int slot, String name, Item icon, Runnable callback) {
        gui.setSlot(slot, new GuiElementBuilder(icon).hideDefaultTooltip()
                .setName(Component.literal("§e" + name))
                .addLoreLine(Component.literal("§7Click to open."))
                .setCallback((i,c,t) -> callback.run()));
    }

    private static void openArmorTabs(ServerPlayer player, Consumer<ServerPlayer> backTarget) {
        SimpleGui gui = base(player, "Profession Armor");
        gui.setSlot(4, new GuiElementBuilder(Items.DIAMOND_CHESTPLATE).hideDefaultTooltip()
                .setName(Component.literal("§aProfession Armor"))
                .addLoreLine(Component.literal("§7Choose an armor slot.")));
        addTab(gui, player, 20, "Helmets", Items.DIAMOND_HELMET, () -> openCraft(player, backTarget, "helmet", "Helmet", Items.DIAMOND_HELMET));
        addTab(gui, player, 21, "Chestplates", Items.DIAMOND_CHESTPLATE, () -> openCraft(player, backTarget, "chestplate", "Chestplate", Items.DIAMOND_CHESTPLATE));
        addTab(gui, player, 22, "Leggings", Items.DIAMOND_LEGGINGS, () -> openCraft(player, backTarget, "leggings", "Leggings", Items.DIAMOND_LEGGINGS));
        addTab(gui, player, 23, "Boots", Items.DIAMOND_BOOTS, () -> openCraft(player, backTarget, "boots", "Boots", Items.DIAMOND_BOOTS));
        MenuUtil.addBackButton(gui, 49, () -> openTabs(player, backTarget));
        gui.open();
    }

    private static void openTrinketTabs(ServerPlayer player, Consumer<ServerPlayer> backTarget) {
        SimpleGui gui = base(player, "Profession Trinkets");
        gui.setSlot(4, new GuiElementBuilder(Items.AMETHYST_SHARD).hideDefaultTooltip()
                .setName(Component.literal("§dProfession Trinkets"))
                .addLoreLine(Component.literal("§7Shift right-click a trinket while holding it to toggle.")));
        addTab(gui, player, 10, "Magnet", Items.IRON_INGOT, () -> openCraft(player, backTarget, "magnet", "Magnet", Items.IRON_INGOT));
        addTab(gui, player, 11, "Shiny Charm", Items.AMETHYST_SHARD, () -> openCraft(player, backTarget, "shiny_charm", "Shiny Charm", Items.AMETHYST_SHARD));
        addTab(gui, player, 12, "Profession XP Gem", Items.EMERALD, () -> openCraft(player, backTarget, "profession_xp_gem", "Profession XP Gem", Items.EMERALD));
        addTab(gui, player, 13, "Pokémon XP Egg", Items.PRISMARINE_CRYSTALS, () -> openCraft(player, backTarget, "pokemon_xp_egg", "Pokémon XP Egg", Items.PRISMARINE_CRYSTALS));
        addTab(gui, player, 14, "Friendship Charm", Items.HEART_OF_THE_SEA, () -> openCraft(player, backTarget, "friendship_charm", "Friendship Charm", Items.HEART_OF_THE_SEA));
        addTab(gui, player, 15, "Level Charm", Items.NETHER_STAR, () -> openCraft(player, backTarget, "level_charm", "Level Charm", Items.NETHER_STAR));
        addTab(gui, player, 16, "Rare Pokémon Charm", Items.PRISMARINE_CRYSTALS, () -> openCraft(player, backTarget, "rare_pokemon_charm", "Rare Pokémon Charm", Items.PRISMARINE_CRYSTALS));
        addTab(gui, player, 21, "Chunky Brick", Items.BRICK, () -> openCraft(player, backTarget, "chunky_brick", "Chunky Brick", Items.BRICK));
        addTab(gui, player, 22, "Trinket Pouch", Items.ENDER_CHEST, () -> openCraft(player, backTarget, "trinket_pouch", "Trinket Pouch", Items.ENDER_CHEST));
        MenuUtil.addBackButton(gui, 49, () -> openTabs(player, backTarget));
        gui.open();
    }

    private static void openCraft(ServerPlayer player, Consumer<ServerPlayer> backTarget, String toolType, String title, Item icon) {
        SimpleGui gui = base(player, "Craft " + title);
        gui.setSlot(4, new GuiElementBuilder(icon).hideDefaultTooltip().setName(Component.literal("§aCraft " + title))
                .addLoreLine(Component.literal("§7Craft using stored fragments.")));
        int[] slots = {20,21,22,23,24,25};
        String[] rarities = {"COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC"};
        for (int i = 0; i < rarities.length; i++) addCraftButton(gui, player, slots[i], rarities[i], toolType, iconFor(toolType, rarities[i]));
        MenuUtil.addBackButton(gui, 49, () -> openTabs(player, backTarget));
        gui.open();
    }

    private static ItemStack iconFor(String toolType, String rarity) {
        ItemStack toolPreview = professionToolPreview(toolType, rarity);
        if (!toolPreview.isEmpty()) return toolPreview;
        return new ItemStack(fallbackIconFor(toolType, rarity));
    }

    private static ItemStack professionToolPreview(String toolType, String rarity) {
        String normalizedType = toolType == null ? "" : toolType.trim().toLowerCase(Locale.ROOT);
        if (!(normalizedType.equals("pickaxe") || normalizedType.equals("axe") || normalizedType.equals("hoe") || normalizedType.equals("shovel") || normalizedType.equals("sword"))) {
            return ItemStack.EMPTY;
        }

        String normalizedRarity = ProfessionFragmentConfig.normalizeRarity(rarity);
        for (Map.Entry<String, ProfessionToolConfig.ToolData> entry : ProfessionToolConfig.TOOLS.entrySet()) {
            ProfessionToolConfig.ToolData data = entry.getValue();
            if (data == null || data.baseItem == null || data.baseItem.isBlank()) continue;
            if (!ProfessionFragmentConfig.normalizeRarity(data.rarity).equals(normalizedRarity)) continue;

            String base = data.baseItem.toLowerCase(Locale.ROOT);
            boolean matches = switch (normalizedType) {
                case "pickaxe" -> base.contains("pickaxe");
                case "axe" -> !base.contains("pickaxe") && base.contains("axe");
                case "hoe" -> base.contains("hoe");
                case "shovel" -> base.contains("shovel");
                case "sword" -> base.contains("sword");
                default -> false;
            };
            if (!matches) continue;

            ItemStack stack = ProfessionToolManager.createUnidentifiedPreviewStack(
                    entry.getKey(),
                    "Unidentified " + ProfessionFragmentManager.formatWords(normalizedRarity) + " " + ProfessionFragmentManager.formatWords(normalizedType)
            );
            if (!stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static Item fallbackIconFor(String toolType, String rarity) {
        String normalizedRarity = ProfessionFragmentConfig.normalizeRarity(rarity);
        int tier = switch (normalizedRarity) {
            case "COMMON" -> 0;
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            default -> 0;
        };
        Item[] icons = switch (toolType) {
            case "axe" -> new Item[]{Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE, Items.GOLDEN_AXE};
            case "hoe" -> new Item[]{Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE, Items.GOLDEN_HOE};
            case "shovel" -> new Item[]{Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL, Items.GOLDEN_SHOVEL};
            case "sword" -> new Item[]{Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD, Items.GOLDEN_SWORD};
            case "helmet" -> new Item[]{Items.LEATHER_HELMET, Items.IRON_HELMET, Items.DIAMOND_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET, Items.NETHERITE_HELMET};
            case "chestplate" -> new Item[]{Items.LEATHER_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_CHESTPLATE};
            case "leggings" -> new Item[]{Items.LEATHER_LEGGINGS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS, Items.NETHERITE_LEGGINGS};
            case "boots" -> new Item[]{Items.LEATHER_BOOTS, Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS, Items.NETHERITE_BOOTS};
            case "magnet" -> new Item[]{Items.IRON_INGOT, Items.IRON_INGOT, Items.GOLD_INGOT, Items.GOLD_INGOT, Items.NETHERITE_INGOT, Items.NETHERITE_INGOT};
            case "shiny_charm" -> new Item[]{Items.AMETHYST_SHARD, Items.AMETHYST_SHARD, Items.ECHO_SHARD, Items.ECHO_SHARD, Items.NETHER_STAR, Items.NETHER_STAR};
            case "profession_xp_gem" -> new Item[]{Items.EMERALD, Items.EMERALD, Items.EMERALD, Items.EMERALD, Items.EMERALD_BLOCK, Items.EMERALD_BLOCK};
            case "pokemon_xp_egg" -> new Item[]{Items.PRISMARINE_CRYSTALS, Items.PRISMARINE_CRYSTALS, Items.PRISMARINE_SHARD, Items.PRISMARINE_SHARD, Items.AMETHYST_SHARD, Items.AMETHYST_SHARD};
            case "friendship_charm" -> new Item[]{Items.HEART_OF_THE_SEA, Items.HEART_OF_THE_SEA, Items.HEART_OF_THE_SEA, Items.HEART_OF_THE_SEA, Items.NETHER_STAR, Items.NETHER_STAR};
            case "level_charm" -> new Item[]{Items.NETHER_STAR, Items.NETHER_STAR, Items.AMETHYST_SHARD, Items.AMETHYST_SHARD, Items.DRAGON_BREATH, Items.DRAGON_BREATH};
            case "rare_pokemon_charm" -> new Item[]{Items.PRISMARINE_CRYSTALS, Items.PRISMARINE_CRYSTALS, Items.PRISMARINE_SHARD, Items.PRISMARINE_SHARD, Items.NETHER_STAR, Items.NETHER_STAR};
            case "chunky_brick" -> new Item[]{Items.BRICK, Items.BRICK, Items.NETHER_BRICK, Items.NETHER_BRICK, Items.NETHERITE_SCRAP, Items.NETHERITE_SCRAP};
            case "trinket_pouch" -> new Item[]{Items.ENDER_CHEST, Items.ENDER_CHEST, Items.ENDER_CHEST, Items.ENDER_CHEST, Items.ENDER_CHEST, Items.ENDER_CHEST};
            default -> new Item[]{Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE, Items.GOLDEN_PICKAXE};
        };
        return icons[Math.max(0, Math.min(tier, icons.length - 1))];
    }


    private static void addCraftButton(SimpleGui gui, ServerPlayer player, int slot, String rarity, String toolType, ItemStack icon) {
        String normalizedRarity = ProfessionFragmentConfig.normalizeRarity(rarity);
        ProfessionFragmentConfig.ToolCraftingData trade = ProfessionFragmentConfig.TOOL_CRAFTING.get(normalizedRarity);
        if (trade == null) {
            gui.setSlot(slot, new GuiElementBuilder(Items.BARRIER).hideDefaultTooltip().setName(Component.literal("§cMissing Craft Config")));
            return;
        }
        String fragmentKey = normalizedRarity;
        int cost = 16;
        long creditCost = ProfessionFragmentManager.craftCreditCost(normalizedRarity);
        long creditCostCents = EconomyManager.wholeCreditsToCents(creditCost);
        int available = ProfessionFragmentManager.countFragments(player, fragmentKey);
        boolean hasCredits = EconomyManager.canAfford(player, creditCostCents);
        GuiElementBuilder builder = new GuiElementBuilder(icon.copy()).hideDefaultTooltip()
                .setName(Component.literal("Craft " + ProfessionFragmentManager.formatWords(normalizedRarity) + " " + ProfessionFragmentManager.formatWords(toolType)).withStyle(getRarityColor(normalizedRarity)))
                .addLoreLine(Component.literal("§7Cost: §6" + cost + "x " + ProfessionFragmentManager.formatWords(fragmentKey) + " Fragment"))
                .addLoreLine(Component.literal("§7Credits: §6" + EconomyManager.formatWholeCredits(creditCost)))
                .addLoreLine(Component.literal("§7Fragments: §e" + available))
                .addLoreLine(Component.literal("§7Balance: §e" + EconomyManager.format(EconomyManager.getBalance(player))));
        boolean trinketCraft = toolType.equals("magnet") || toolType.equals("shiny_charm") || toolType.equals("profession_xp_gem") || toolType.equals("pokemon_xp_egg") || toolType.equals("friendship_charm") || toolType.equals("level_charm") || toolType.equals("rare_pokemon_charm") || toolType.equals("chunky_brick") || toolType.equals("trinket_pouch");
        if (trinketCraft) {
            for (Component line : trinketDescription(toolType, normalizedRarity)) builder.addLoreLine(line);
        }
        builder.addLoreLine(Component.literal(available >= cost && hasCredits ? "§eClick to craft" : (!hasCredits ? "§cNot enough Credits" : "§cNot enough fragments")))
                .setCallback((i,c,t) -> {
                    player.closeContainer();
                    player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "fragments craft " + normalizedRarity.toLowerCase() + " " + toolType);
                });
        gui.setSlot(slot, builder);
    }

    private static List<Component> trinketDescription(String toolType, String rarity) {
        List<Component> lines = new ArrayList<>();
        com.champutils.profession.ProfessionTrinketConfig.Tier tier = com.champutils.profession.ProfessionTrinketConfig.tier(rarity);
        switch (toolType) {
            case "magnet" -> {
                lines.add(Component.literal("§7Effect: pulls eligible drops toward you."));
                lines.add(Component.literal("§7Effectiveness: §a+" + fmt(tier.magnetRadiusBonus) + " block radius"));
            }
            case "shiny_charm" -> {
                lines.add(Component.literal("§7Effect: improves catch/spawn shiny odds."));
                lines.add(Component.literal("§7Effectiveness: §d+" + fmt(tier.shinyChancePercent) + "% shiny chance"));
            }
            case "profession_xp_gem" -> {
                lines.add(Component.literal("§7Effect: can double profession XP."));
                lines.add(Component.literal("§7Effectiveness: §a" + fmt(tier.professionXpDoubleChancePercent) + "% double XP chance"));
            }
            case "pokemon_xp_egg" -> {
                lines.add(Component.literal("§7Effect: boosts Pokémon battle XP."));
                lines.add(Component.literal("§7Effectiveness: §b+" + fmt(tier.pokemonXpBonusPercent) + "% XP"));
            }
            case "friendship_charm" -> {
                lines.add(Component.literal("§7Effect: boosts friendship gains."));
                lines.add(Component.literal("§7Effectiveness: §d+" + fmt(tier.friendshipBonusPercent) + "% friendship"));
            }
            case "level_charm" -> {
                lines.add(Component.literal("§7Effect: raises nearby wild spawn minimums."));
                lines.add(Component.literal("§7Effectiveness: §e" + fmt(tier.levelCharmGymCapPercent) + "% of gym cap"));
            }
            case "rare_pokemon_charm" -> {
                lines.add(Component.literal("§7Effect: boosts rare non-special wild spawns."));
                lines.add(Component.literal("§7Effectiveness: §6+" + fmt(tier.rarePokemonSpawnBonusPercent) + "% rare weight"));
            }
            case "chunky_brick" -> {
                lines.add(Component.literal("§7Effect: boosts profession chunk odds."));
                lines.add(Component.literal("§7Effectiveness: §6+" + fmt(tier.chunkChanceBonusPercent) + "% chunk odds"));
            }
            case "trinket_pouch" -> {
                lines.add(Component.literal("§7Effect: unlocks/upgrades digital /tpouch storage."));
                lines.add(Component.literal("§7Effectiveness: §a" + tier.pouchSlots + " digital slots"));
                lines.add(Component.literal("§8Crafting an equal/lower pouch is blocked."));
            }
            default -> lines.add(Component.literal("§7Effect: profession trinket."));
        }
        return lines;
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.US, value >= 10 ? "%.0f" : "%.2f", value).replaceAll("\\.00$", "");
    }

    private static void openUpgrade(ServerPlayer player, Consumer<ServerPlayer> backTarget, boolean downgrade) {
        SimpleGui gui = base(player, downgrade ? "Downgrade Fragments" : "Upgrade Fragments");
        gui.setSlot(4, new GuiElementBuilder(downgrade ? Items.PAPER : Items.AMETHYST_SHARD).hideDefaultTooltip()
                .setName(Component.literal(downgrade ? "§cDowngrade Fragments" : "§aUpgrade Fragments"))
                .addLoreLine(Component.literal(downgrade ? "§7Legendary may downgrade to Epic. Mythic cannot downgrade." : "§7Upgrade up to Epic only. Legendary/Mythic are prestige drops.")));
        String[] ids = downgrade
                ? new String[]{"UNCOMMON_TO_COMMON_DOWNGRADE","RARE_TO_UNCOMMON_DOWNGRADE","EPIC_TO_RARE_DOWNGRADE","LEGENDARY_TO_EPIC_DOWNGRADE"}
                : new String[]{"COMMON_TO_UNCOMMON","UNCOMMON_TO_RARE","RARE_TO_EPIC"};
        int[] slots = downgrade ? new int[]{20,21,22,23} : new int[]{21,22,23};
        for (int i = 0; i < ids.length; i++) addUpgradeButton(gui, player, slots[i], ids[i], downgrade ? Items.PAPER : Items.AMETHYST_SHARD);
        MenuUtil.addBackButton(gui, 49, () -> openTabs(player, backTarget));
        gui.open();
    }

    private static void addUpgradeButton(SimpleGui gui, ServerPlayer player, int slot, String upgradeId, Item icon) {
        ProfessionFragmentConfig.UpgradeData upgrade = ProfessionFragmentConfig.UPGRADES.get(upgradeId);
        if (upgrade == null) {
            gui.setSlot(slot, new GuiElementBuilder(Items.BARRIER).hideDefaultTooltip().setName(Component.literal("§cMissing Upgrade")).addLoreLine(Component.literal("§7" + upgradeId)));
            return;
        }
        String from = ProfessionFragmentConfig.normalizeRarity(upgrade.fromFragment);
        String to = ProfessionFragmentConfig.normalizeRarity(upgrade.toFragment);
        int cost = Math.max(1, upgrade.cost);
        int output = Math.max(1, upgrade.output);
        int available = ProfessionFragmentManager.countFragments(player, from);
        gui.setSlot(slot, new GuiElementBuilder(icon).hideDefaultTooltip()
                .setName(Component.literal(ProfessionFragmentManager.formatWords(from) + " → " + ProfessionFragmentManager.formatWords(to)).withStyle(getRarityColor(to)))
                .addLoreLine(Component.literal("§7Cost: §6" + cost + "x " + ProfessionFragmentManager.formatWords(from) + " Fragment"))
                .addLoreLine(Component.literal("§7Output: §a" + output + "x " + ProfessionFragmentManager.formatWords(to) + " Fragment"))
                .addLoreLine(Component.literal("§7You have: §e" + available))
                .addLoreLine(Component.literal(available >= cost ? "§eClick to convert" : "§cNot enough fragments"))
                .setCallback((i,c,t) -> {
                    player.closeContainer();
                    player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "fragments upgrade " + upgradeId);
                }));
    }

    private static void openWithdraw(ServerPlayer player, Consumer<ServerPlayer> backTarget) {
        SimpleGui gui = base(player, "Withdraw Fragments");
        gui.setSlot(4, new GuiElementBuilder(Items.CHEST).hideDefaultTooltip().setName(Component.literal("§aWithdraw Fragments")));
        int[] slots = {20,21,22,23,24,25};
        String[] rarities = {"COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC"};
        for (int i = 0; i < rarities.length; i++) addWithdrawButton(gui, player, slots[i], rarities[i], Items.PAPER);
        MenuUtil.addBackButton(gui, 49, () -> openTabs(player, backTarget));
        gui.open();
    }

    private static void addWithdrawButton(SimpleGui gui, ServerPlayer player, int slot, String rarity, Item icon) {
        String normalizedRarity = ProfessionFragmentConfig.normalizeRarity(rarity);
        int available = ProfessionFragmentManager.countFragments(player, normalizedRarity);
        int amount = Math.min(16, Math.max(1, available));
        gui.setSlot(slot, new GuiElementBuilder(icon).hideDefaultTooltip()
                .setName(Component.literal("Withdraw " + ProfessionFragmentManager.formatWords(normalizedRarity) + " Fragments").withStyle(getRarityColor(normalizedRarity)))
                .addLoreLine(Component.literal("§7Stored: §e" + available))
                .addLoreLine(Component.literal(available > 0 ? "§eClick to withdraw " + amount : "§cNo stored fragments"))
                .setCallback((i,c,t) -> {
                    if (available <= 0) return;
                    player.closeContainer();
                    player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "fragments withdraw " + normalizedRarity.toLowerCase() + " " + amount);
                }));
    }

    private static ChatFormatting getRarityColor(String rarity) {
        if (rarity == null) return ChatFormatting.WHITE;
        return switch (rarity.trim().toUpperCase()) {
            case "UNCOMMON" -> ChatFormatting.GREEN;
            case "RARE" -> ChatFormatting.BLUE;
            case "EPIC" -> ChatFormatting.LIGHT_PURPLE;
            case "LEGENDARY" -> ChatFormatting.GOLD;
            case "MYTHIC" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }
}
