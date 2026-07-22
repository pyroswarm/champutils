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
    private static final String[] RARITIES = {"F","E","D","C","B","A","S"};
    private static final int[] RARITY_ROW_SLOTS = {19,20,21,22,23,24,25};

    private FragmentCraftingMenu() {}

    public static void open(ServerPlayer player) { open(player, GearWorkshopMenu::open); }

    public static void open(ServerPlayer player, Consumer<ServerPlayer> backTarget) {
        openTabs(player, backTarget);
    }

    private static void openTabs(ServerPlayer player, Consumer<ServerPlayer> backTarget) {
        SimpleGui gui = base(player, "Essence Crafting");
        gui.setSlot(4, new GuiElementBuilder(Items.EMERALD).hideDefaultTooltip()
                .setName(Component.literal("§aEssence Crafting"))
                .addLoreLine(Component.literal("§7Choose an item type or essence action.")));

        addTab(gui, player, 10, "Pickaxe", Items.DIAMOND_PICKAXE, () -> openCraft(player, backTarget, "pickaxe", "Pickaxe", Items.DIAMOND_PICKAXE));
        addTab(gui, player, 11, "Axe", Items.DIAMOND_AXE, () -> openCraft(player, backTarget, "axe", "Axe", Items.DIAMOND_AXE));
        addTab(gui, player, 12, "Hoe", Items.DIAMOND_HOE, () -> openCraft(player, backTarget, "hoe", "Hoe", Items.DIAMOND_HOE));
        addTab(gui, player, 13, "Shovel", Items.DIAMOND_SHOVEL, () -> openCraft(player, backTarget, "shovel", "Shovel", Items.DIAMOND_SHOVEL));
        addTab(gui, player, 14, "Sword", Items.DIAMOND_SWORD, () -> openCraft(player, backTarget, "sword", "Sword", Items.DIAMOND_SWORD));
        addTab(gui, player, 15, "Armor", Items.DIAMOND_CHESTPLATE, () -> openArmorTabs(player, backTarget));
        addTab(gui, player, 16, "Trinkets", Items.AMETHYST_SHARD, () -> openTrinketTabs(player, backTarget));
        addTab(gui, player, 20, "Upgrade Essence", Items.AMETHYST_SHARD, () -> openUpgrade(player, backTarget));
        addTab(gui, player, 22, "Withdraw Essence", Items.CHEST, () -> openWithdraw(player, backTarget));

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
        addTab(gui, player, 22, "Totem of Growth", Items.MOSS_BLOCK, () -> openCraft(player, backTarget, "totem_of_growth", "Totem of Growth", Items.MOSS_BLOCK));
        addTab(gui, player, 23, "Poke Snax", Items.COOKIE, () -> openCraft(player, backTarget, "poke_snax", "Poke Snax", Items.COOKIE));
        addTab(gui, player, 24, "Seed Pouch", Items.WHEAT_SEEDS, () -> openCraft(player, backTarget, "seed_pouch", "Seed Pouch", Items.WHEAT_SEEDS));
        addTab(gui, player, 25, "Incubator", Items.TURTLE_EGG, () -> openCraft(player, backTarget, "incubator", "Incubator", Items.TURTLE_EGG));
        addTab(gui, player, 26, "Trinket Pouch", Items.ENDER_CHEST, () -> openCraft(player, backTarget, "trinket_pouch", "Trinket Pouch", Items.ENDER_CHEST));
        MenuUtil.addBackButton(gui, 49, () -> openTabs(player, backTarget));
        gui.open();
    }

    private static void openCraft(ServerPlayer player, Consumer<ServerPlayer> backTarget, String toolType, String title, Item icon) {
        SimpleGui gui = base(player, "Craft " + title);
        gui.setSlot(4, new GuiElementBuilder(icon).hideDefaultTooltip().setName(Component.literal("§aCraft " + title))
                .addLoreLine(Component.literal("§7Craft using stored essence.")));
        for (int i = 0; i < Math.min(RARITIES.length, RARITY_ROW_SLOTS.length); i++) {
            addCraftButton(gui, player, RARITY_ROW_SLOTS[i], RARITIES[i], toolType, iconFor(toolType, RARITIES[i]));
        }
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
                    "Unidentified " + ProfessionFragmentManager.displayRankName(normalizedRarity) + " " + ProfessionFragmentManager.formatWords(normalizedType)
            );
            if (!stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static Item fallbackIconFor(String toolType, String rarity) {
        String normalizedRarity = ProfessionFragmentConfig.normalizeRarity(rarity);
        int tier = switch (normalizedRarity) {
            case "F" -> 0;
            case "E" -> 1;
            case "D" -> 2;
            case "C" -> 3;
            case "B" -> 4;
            case "A" -> 5;
            case "S" -> 6;
            default -> 0;
        };
        Item[] icons = switch (toolType) {
            case "axe" -> new Item[]{Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE, Items.GOLDEN_AXE, Items.NETHERITE_AXE};
            case "hoe" -> new Item[]{Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE, Items.GOLDEN_HOE, Items.NETHERITE_HOE};
            case "shovel" -> new Item[]{Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL, Items.GOLDEN_SHOVEL, Items.NETHERITE_SHOVEL};
            case "sword" -> new Item[]{Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD, Items.GOLDEN_SWORD, Items.NETHERITE_SWORD};
            case "helmet" -> new Item[]{Items.LEATHER_HELMET, Items.IRON_HELMET, Items.DIAMOND_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET, Items.NETHERITE_HELMET, Items.NETHERITE_HELMET};
            case "chestplate" -> new Item[]{Items.LEATHER_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_CHESTPLATE};
            case "leggings" -> new Item[]{Items.LEATHER_LEGGINGS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS, Items.NETHERITE_LEGGINGS, Items.NETHERITE_LEGGINGS};
            case "boots" -> new Item[]{Items.LEATHER_BOOTS, Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS, Items.NETHERITE_BOOTS, Items.NETHERITE_BOOTS};
            case "magnet" -> new Item[]{Items.IRON_INGOT, Items.IRON_INGOT, Items.GOLD_INGOT, Items.GOLD_INGOT, Items.NETHERITE_INGOT, Items.NETHERITE_INGOT, Items.NETHERITE_INGOT};
            case "shiny_charm" -> new Item[]{Items.AMETHYST_SHARD, Items.AMETHYST_SHARD, Items.ECHO_SHARD, Items.ECHO_SHARD, Items.NETHER_STAR, Items.NETHER_STAR, Items.NETHER_STAR};
            case "profession_xp_gem" -> new Item[]{Items.EMERALD, Items.EMERALD, Items.EMERALD, Items.EMERALD, Items.EMERALD_BLOCK, Items.EMERALD_BLOCK, Items.EMERALD_BLOCK};
            case "pokemon_xp_egg" -> new Item[]{Items.PRISMARINE_CRYSTALS, Items.PRISMARINE_CRYSTALS, Items.PRISMARINE_SHARD, Items.PRISMARINE_SHARD, Items.AMETHYST_SHARD, Items.AMETHYST_SHARD, Items.NETHER_STAR};
            case "friendship_charm" -> new Item[]{Items.HEART_OF_THE_SEA, Items.HEART_OF_THE_SEA, Items.HEART_OF_THE_SEA, Items.HEART_OF_THE_SEA, Items.NETHER_STAR, Items.NETHER_STAR, Items.NETHER_STAR};
            case "level_charm" -> new Item[]{Items.NETHER_STAR, Items.NETHER_STAR, Items.AMETHYST_SHARD, Items.AMETHYST_SHARD, Items.DRAGON_BREATH, Items.DRAGON_BREATH, Items.DRAGON_EGG};
            case "rare_pokemon_charm" -> new Item[]{Items.PRISMARINE_CRYSTALS, Items.PRISMARINE_CRYSTALS, Items.PRISMARINE_SHARD, Items.PRISMARINE_SHARD, Items.NETHER_STAR, Items.NETHER_STAR, Items.NETHER_STAR};
            case "chunky_brick" -> new Item[]{Items.BRICK, Items.BRICK, Items.NETHER_BRICK, Items.NETHER_BRICK, Items.NETHERITE_SCRAP, Items.NETHERITE_SCRAP, Items.NETHERITE_BLOCK};
            case "totem_of_growth" -> new Item[]{Items.MOSS_BLOCK, Items.MOSS_BLOCK, Items.FLOWERING_AZALEA, Items.FLOWERING_AZALEA, Items.SPORE_BLOSSOM, Items.SPORE_BLOSSOM, Items.BEACON};
            case "poke_snax" -> new Item[]{Items.COOKIE, Items.COOKIE, Items.COOKIE, Items.COOKIE, Items.COOKIE, Items.COOKIE, Items.COOKIE};
            case "seed_pouch" -> new Item[]{Items.WHEAT_SEEDS, Items.WHEAT_SEEDS, Items.PUMPKIN_SEEDS, Items.MELON_SEEDS, Items.BEETROOT_SEEDS, Items.TORCHFLOWER_SEEDS, Items.PITCHER_POD};
            case "incubator" -> new Item[]{Items.TURTLE_EGG, Items.TURTLE_EGG, Items.TURTLE_EGG, Items.TURTLE_EGG, Items.TURTLE_EGG, Items.TURTLE_EGG, Items.TURTLE_EGG};
            case "trinket_pouch" -> new Item[]{Items.ENDER_CHEST, Items.ENDER_CHEST, Items.ENDER_CHEST, Items.ENDER_CHEST, Items.ENDER_CHEST, Items.ENDER_CHEST, Items.ENDER_CHEST};
            default -> new Item[]{Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE, Items.GOLDEN_PICKAXE, Items.NETHERITE_PICKAXE};
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
                .setName(Component.literal("Craft " + ProfessionFragmentManager.displayRankName(normalizedRarity) + " " + ProfessionFragmentManager.formatWords(toolType)).withStyle(getRarityColor(normalizedRarity)))
                .addLoreLine(Component.literal("§7Requires: §6" + cost + " " + ProfessionFragmentManager.displayRankName(fragmentKey) + " Essence"))
                .addLoreLine(Component.literal("§7Credits: §6" + EconomyManager.formatWholeCredits(creditCost)))
                .addLoreLine(Component.literal("§7Essence: §e" + available))
                .addLoreLine(Component.literal("§7Balance: §e" + EconomyManager.format(EconomyManager.getBalance(player))));
        boolean trinketCraft = toolType.equals("magnet") || toolType.equals("shiny_charm") || toolType.equals("profession_xp_gem") || toolType.equals("pokemon_xp_egg") || toolType.equals("friendship_charm") || toolType.equals("level_charm") || toolType.equals("rare_pokemon_charm") || toolType.equals("chunky_brick") || toolType.equals("totem_of_growth") || toolType.equals("poke_snax") || toolType.equals("seed_pouch") || toolType.equals("incubator") || toolType.equals("trinket_pouch");
        if (trinketCraft) {
            for (Component line : trinketDescription(toolType, normalizedRarity)) builder.addLoreLine(line);
        }
        builder.addLoreLine(Component.literal(available >= cost && hasCredits ? "§eClick to craft" : (!hasCredits ? "§cNot enough Credits" : "§cNot enough essence")))
                .setCallback((i,c,t) -> {
                    player.closeContainer();
                    player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "essence craft " + normalizedRarity.toLowerCase() + " " + toolType);
                });
        gui.setSlot(slot, builder);
    }

    private static List<Component> trinketDescription(String toolType, String rarity) {
        List<Component> lines = new ArrayList<>();
        com.champutils.profession.ProfessionTrinketConfig.Tier tier = com.champutils.profession.ProfessionTrinketConfig.tier(rarity);
        switch (toolType) {
            case "magnet" -> {
                lines.add(Component.literal("§7Effect: pulls eligible item drops toward you."));
                lines.add(Component.literal("§7Effectiveness: §a+" + fmt(tier.magnetRadiusBonus) + " block radius"));
            }
            case "shiny_charm" -> {
                lines.add(Component.literal("§7Effect: adds a flat shiny roll to catches and nearby wild spawns."));
                lines.add(Component.literal("§7Extra Shiny Chance: §d+" + fmt(tier.shinyChancePercent) + "%"));
            }
            case "profession_xp_gem" -> {
                lines.add(Component.literal("§7Effect: always increases earned profession XP."));
                lines.add(Component.literal("§7Profession XP: §a+" + fmt(tier.professionXpDoubleChancePercent) + "%"));
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
                lines.add(Component.literal("§7Effect: sets nearby wild spawns to a minimum level based on your gym cap."));
                lines.add(Component.literal("§7Effectiveness: §e" + fmt(tier.levelCharmGymCapPercent) + "% of gym cap"));
            }
            case "rare_pokemon_charm" -> {
                lines.add(Component.literal("§7Effect: boosts rare non-special wild spawns."));
                lines.add(Component.literal("§7Relative Rare Spawn Weight: §6+" + fmt(tier.rarePokemonSpawnBonusPercent) + "%"));
            }
            case "chunky_brick" -> {
                lines.add(Component.literal("§7Effect: boosts profession chunk odds."));
                lines.add(Component.literal("§7Relative Chunk Odds: §6+" + fmt(tier.chunkChanceBonusPercent) + "%"));
            }
            case "totem_of_growth" -> {
                lines.add(Component.literal("§7Effect: accelerates nearby crops, berries, apricorns, and profession plants."));
                lines.add(Component.literal("§7Radius: §a" + tier.growthRadiusBlocks + " blocks"));
                lines.add(Component.literal("§7Growth Speed: §a+" + fmt(tier.growthSpeedBonusPercent) + "%"));
            }
            case "poke_snax" -> {
                lines.add(Component.literal("§7Effect: reduces hunger drain from all activities."));
                lines.add(Component.literal(tier.hungerReductionPercent >= 100.0D ? "§7Effectiveness: §aHunger no longer drops" : "§7Effectiveness: §a-" + fmt(tier.hungerReductionPercent) + "% hunger drain"));
            }
            case "seed_pouch" -> {
                lines.add(Component.literal("§7Effect: copies the planted seed, berry, or mint into nearby valid spots."));
                lines.add(Component.literal("§7Effectiveness: §aUp to " + tier.seedPouchExtraPlacements + " extra placements"));
                lines.add(Component.literal("§8Consumes matching items from inventory first, then the Farming backpack; respects claims."));
            }
            case "incubator" -> {
                lines.add(Component.literal("§7Effect: multiplicatively reduces breeding cooldown after profession bonuses."));
                lines.add(Component.literal("§7Remaining Cooldown: §a-" + fmt(tier.incubatorCooldownReductionPercent) + "%"));
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

    private static void openUpgrade(ServerPlayer player, Consumer<ServerPlayer> backTarget) {
        SimpleGui gui = base(player, "Upgrade Essence");
        gui.setSlot(4, new GuiElementBuilder(Items.AMETHYST_SHARD).hideDefaultTooltip()
                .setName(Component.literal("§aUpgrade Essence"))
                .addLoreLine(Component.literal("§7Upgrade up to C Rank only. B/A/S are prestige drops.")));
        String[] ids = new String[]{"F_TO_E","E_TO_D","D_TO_C","C_TO_B"};
        int[] slots = new int[]{20,21,22,23};
        for (int i = 0; i < ids.length; i++) {
            addUpgradeButton(gui, player, slots[i], ids[i], Items.AMETHYST_SHARD);
        }
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
                .addLoreLine(Component.literal("§7Requires: §6" + cost + " " + ProfessionFragmentManager.displayRankName(from) + " Essence"))
                .addLoreLine(Component.literal("§7Output: §a" + output + " " + ProfessionFragmentManager.displayRankName(to) + " Essence"))
                .addLoreLine(Component.literal("§7You have: §e" + available))
                .addLoreLine(Component.literal(available >= cost ? "§eClick to convert" : "§cNot enough essence"))
                .setCallback((i,c,t) -> {
                    player.closeContainer();
                    player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "essence upgrade " + upgradeId);
                }));
    }

    private static void openWithdraw(ServerPlayer player, Consumer<ServerPlayer> backTarget) {
        SimpleGui gui = base(player, "Withdraw Essence");
        gui.setSlot(4, new GuiElementBuilder(Items.CHEST).hideDefaultTooltip().setName(Component.literal("§aWithdraw Essence")));
        for (int i = 0; i < Math.min(RARITIES.length, RARITY_ROW_SLOTS.length); i++) {
            addWithdrawButton(gui, player, RARITY_ROW_SLOTS[i], RARITIES[i], Items.PAPER);
        }
        MenuUtil.addBackButton(gui, 49, () -> openTabs(player, backTarget));
        gui.open();
    }

    private static void addWithdrawButton(SimpleGui gui, ServerPlayer player, int slot, String rarity, Item icon) {
        String normalizedRarity = ProfessionFragmentConfig.normalizeRarity(rarity);
        int available = ProfessionFragmentManager.countFragments(player, normalizedRarity);
        int amount = Math.min(16, Math.max(1, available));
        gui.setSlot(slot, new GuiElementBuilder(icon).hideDefaultTooltip()
                .setName(Component.literal("Withdraw " + ProfessionFragmentManager.displayRankName(normalizedRarity) + " Essence").withStyle(getRarityColor(normalizedRarity)))
                .addLoreLine(Component.literal("§7Stored: §e" + available))
                .addLoreLine(Component.literal(available > 0 ? "§eClick to withdraw " + amount : "§cNo stored essence"))
                .setCallback((i,c,t) -> {
                    if (available <= 0) return;
                    player.closeContainer();
                    player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "essence withdraw " + normalizedRarity.toLowerCase() + " " + amount);
                }));
    }

    private static ChatFormatting getRarityColor(String rarity) {
        if (rarity == null) return ChatFormatting.WHITE;
        return switch (rarity.trim().toUpperCase()) {
            case "E" -> ChatFormatting.GREEN;
            case "D" -> ChatFormatting.BLUE;
            case "C" -> ChatFormatting.LIGHT_PURPLE;
            case "B" -> ChatFormatting.DARK_AQUA;
            case "A" -> ChatFormatting.GOLD;
            case "S" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }
}
