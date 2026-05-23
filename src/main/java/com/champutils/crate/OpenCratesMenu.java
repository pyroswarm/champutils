package com.champutils.crate;

import com.champutils.profession.ProfessionFragmentManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionToolManager;
import com.champutils.shop.NpcShopService;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OpenCratesMenu {
    private static final Random RANDOM = new Random();
    private static final String[] ORDER = {"common","uncommon","rare","epic","legendary","mythic","event","guild","world_boss"};
    private static final Map<UUID, Opening> OPENINGS = new ConcurrentHashMap<>();

    private static final int[] SPIN_SLOTS = new int[]{9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int CENTER_SLOT = 13;
    private static final int CENTER_INDEX_IN_REEL = 4;
    private static final int CENTER_MARKER_SLOT = 4;
    private static final int SPIN_END_TICKS = 78;
    private static final int TOTAL_TICKS = 108;

    private OpenCratesMenu() {}

    private enum RewardType { POKEMON, ITEM, TOOL, SHARDS }

    private static final class RewardPlan {
        RewardType type = RewardType.SHARDS;
        String summary = "bonus shards";
        String species;
        int level;
        boolean shiny;
        NpcShopService.PokemonCratePool pool = NpcShopService.PokemonCratePool.REGULAR;
        String itemId;
        int amount;
        String toolId;
        String shardRarity;
        int shardAmount;
        ItemStack icon = ItemStack.EMPTY;
    }

    private static final class Opening {
        final UUID playerId;
        final String crateId;
        final CrateConfig.CrateDefinition crate;
        final SimpleGui gui;
        final RewardPlan guaranteedShards;
        final RewardPlan mainReward;
        final List<RewardPlan> reel;
        int tick;
        int offset;

        Opening(ServerPlayer player, String crateId, CrateConfig.CrateDefinition crate, SimpleGui gui, RewardPlan guaranteedShards, RewardPlan mainReward) {
            this.playerId = player.getUUID();
            this.crateId = crateId;
            this.crate = crate;
            this.gui = gui;
            this.guaranteedShards = guaranteedShards;
            this.mainReward = mainReward;
            this.reel = buildSpinRewards(crate, crateId, mainReward);
        }
    }

    public static void open(ServerPlayer player) {
        if (player == null) return;
        CrateConfig.load();
        CrateCreditManager.load();
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x4, player, false);
        gui.setTitle(Component.literal("Open Crates"));
        for (int i = 0; i < gui.getSize(); i++) gui.setSlot(i, filler());

        int[] crateSlots = {9,10,11,12,13,14,15,16,17};
        int[] previewSlots = {18,19,20,21,22,23,24,25,26};
        for (int i = 0; i < ORDER.length; i++) {
            String id = ORDER[i];
            CrateConfig.CrateDefinition crate = CrateConfig.getCrate(id);
            if (crate == null || !crate.enabled) continue;
            int credits = CrateCreditManager.getCredits(player, id);
            Item icon = resolveItem(crate.iconItem);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("Credits: " + credits).withStyle(credits > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
            lore.add(Component.literal("Guaranteed: " + crate.guaranteedShardMin + "-" + crate.guaranteedShardMax + " " + ProfessionFragmentManager.formatWords(crate.guaranteedShardRarity) + " shards").withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("Roulette opening animation.").withStyle(ChatFormatting.DARK_GRAY));
            lore.add(Component.literal("Click to open.").withStyle(ChatFormatting.YELLOW));
            GuiElementBuilder b = new GuiElementBuilder(icon).setName(Component.literal(crate.displayName).withStyle(colorFor(id)));
            for (Component line : lore) b.addLoreLine(line);
            gui.setSlot(crateSlots[i], b.setCallback((index, type, action) -> openOne(player, id)));

            gui.setSlot(previewSlots[i], new GuiElementBuilder(Items.BOOK)
                    .setName(Component.literal("Preview " + crate.displayName).withStyle(ChatFormatting.AQUA))
                    .addLoreLine(Component.literal("See possible Pokémon, items, tools,").withStyle(ChatFormatting.GRAY))
                    .addLoreLine(Component.literal("and guaranteed shard range.").withStyle(ChatFormatting.GRAY))
                    .addLoreLine(Component.literal("Click to preview.").withStyle(ChatFormatting.YELLOW))
                    .setCallback((index, type, action) -> openPreview(player, id)));
        }
        gui.open();
    }

    private static GuiElementBuilder filler() {
        return new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Component.literal(" "));
    }

    public static void openPreview(ServerPlayer player, String id) {
        openPreview(player, id, 0);
    }

    private static void openPreview(ServerPlayer player, String id, int page) {
        if (player == null) return;
        CrateConfig.CrateDefinition crate = CrateConfig.getCrate(id);
        if (crate == null || !crate.enabled) {
            player.sendSystemMessage(Component.literal("That crate is not enabled.").withStyle(ChatFormatting.RED));
            return;
        }

        List<PreviewEntry> entries = buildPreviewEntries(crate);
        int pageSize = 36;
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) pageSize));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setTitle(Component.literal(crate.displayName + " Preview " + (safePage + 1) + "/" + totalPages));
        for (int i = 0; i < gui.getSize(); i++) gui.setSlot(i, filler());

        double pokemonTotal = totalWeight(crate.pokemon);
        double itemTotal = totalWeight(crate.items);
        double toolTotal = totalWeight(crate.tools);

        gui.setSlot(4, new GuiElementBuilder(resolveItem(crate.iconItem))
                .setName(Component.literal(crate.displayName).withStyle(colorFor(id)))
                .addLoreLine(Component.literal("Guaranteed: " + crate.guaranteedShardMin + "-" + crate.guaranteedShardMax + " " + ProfessionFragmentManager.formatWords(crate.guaranteedShardRarity) + " shards").withStyle(ChatFormatting.LIGHT_PURPLE))
                .addLoreLine(Component.literal("Shiny chance: " + crate.shinyChance + "%").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Reward chances below are within each reward section.").withStyle(ChatFormatting.DARK_GRAY)));

        int startIndex = safePage * pageSize;
        int endIndex = Math.min(entries.size(), startIndex + pageSize);
        int slot = 9;
        String lastSection = "";
        for (int i = startIndex; i < endIndex && slot < 45; i++) {
            PreviewEntry entry = entries.get(i);
            if (!entry.section.equals(lastSection) && slot < 45) {
                gui.setSlot(slot++, new GuiElementBuilder(sectionIcon(entry.section))
                        .setName(Component.literal(entry.section).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
                lastSection = entry.section;
                if (slot >= 45) break;
            }

            double sectionTotal = switch (entry.section) {
                case "Pokémon" -> pokemonTotal;
                case "Items" -> itemTotal;
                case "Tools" -> toolTotal;
                default -> 1.0D;
            };
            gui.setSlot(slot++, previewElement(entry.reward, entry.fallbackIcon, sectionTotal));
        }

        if (safePage > 0) {
            gui.setSlot(45, new GuiElementBuilder(Items.ARROW)
                    .setName(Component.literal("Previous Page").withStyle(ChatFormatting.YELLOW))
                    .setCallback((index, type, action) -> openPreview(player, id, safePage - 1)));
        }

        gui.setSlot(49, new GuiElementBuilder(Items.ARROW)
                .setName(Component.literal("Back to Crates").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, type, action) -> open(player)));

        if (safePage + 1 < totalPages) {
            gui.setSlot(53, new GuiElementBuilder(Items.ARROW)
                    .setName(Component.literal("Next Page").withStyle(ChatFormatting.YELLOW))
                    .setCallback((index, type, action) -> openPreview(player, id, safePage + 1)));
        }

        gui.open();
    }

    private record PreviewEntry(String section, Object reward, Item fallbackIcon) {}

    private static List<PreviewEntry> buildPreviewEntries(CrateConfig.CrateDefinition crate) {
        List<PreviewEntry> entries = new ArrayList<>();
        if (crate.pokemon != null) for (CrateConfig.WeightedPokemon p : crate.pokemon) entries.add(new PreviewEntry("Pokémon", p, Items.EGG));
        if (crate.items != null) for (CrateConfig.WeightedItem i : crate.items) entries.add(new PreviewEntry("Items", i, Items.CHEST));
        if (crate.tools != null) for (CrateConfig.WeightedTool t : crate.tools) entries.add(new PreviewEntry("Tools", t, Items.DIAMOND_PICKAXE));
        return entries;
    }

    private static Item sectionIcon(String section) {
        return switch (section) {
            case "Pokémon" -> Items.EGG;
            case "Items" -> Items.CHEST;
            case "Tools" -> Items.DIAMOND_PICKAXE;
            default -> Items.PAPER;
        };
    }

    private static double totalWeight(List<?> entries) {
        if (entries == null || entries.isEmpty()) return 1.0D;
        double total = 0.0D;
        for (Object entry : entries) total += Math.max(0, weightOf(entry));
        return total <= 0.0D ? 1.0D : total;
    }

    private static GuiElementBuilder previewElement(Object entry, Item fallbackIcon, double totalWeight) {
        if (entry instanceof CrateConfig.WeightedPokemon p) {
            NpcShopService.PokemonCratePool pool = pool(p.pool, p.species);
            ItemStack icon = createPokemonIcon(p.species, 70, false, pool);
            double percent = (Math.max(0, p.weight) / Math.max(1.0D, totalWeight)) * 100.0D;
            return new GuiElementBuilder(icon)
                    .setName(Component.literal(prettyName(p.species)).withStyle(poolColor(pool)))
                    .addLoreLine(Component.literal("Chance: " + formatPercent(percent)).withStyle(ChatFormatting.GOLD))
                    .addLoreLine(Component.literal("Pool: " + poolLabel(pool)).withStyle(ChatFormatting.GRAY));
        }
        if (entry instanceof CrateConfig.WeightedItem i) {
            Item icon = resolveItem(i.itemId);
            if (icon == Items.AIR) icon = fallbackIcon;
            double percent = (Math.max(0, i.weight) / Math.max(1.0D, totalWeight)) * 100.0D;
            return new GuiElementBuilder(icon)
                    .setName(Component.literal(prettyItem(i.itemId)).withStyle(ChatFormatting.AQUA))
                    .addLoreLine(Component.literal("Chance: " + formatPercent(percent)).withStyle(ChatFormatting.GOLD))
                    .addLoreLine(Component.literal("Amount: " + i.amountMin + "-" + i.amountMax).withStyle(ChatFormatting.GRAY));
        }
        if (entry instanceof CrateConfig.WeightedTool t) {
            ItemStack icon = unidentifiedToolIcon(t.toolId, fallbackIcon);
            double percent = (Math.max(0, t.weight) / Math.max(1.0D, totalWeight)) * 100.0D;
            return new GuiElementBuilder(icon)
                    .setName(Component.literal(prettyName(t.toolId)).withStyle(ChatFormatting.LIGHT_PURPLE))
                    .addLoreLine(Component.literal("Chance: " + formatPercent(percent)).withStyle(ChatFormatting.GOLD))
                    .addLoreLine(Component.literal("Unidentified profession tool").withStyle(ChatFormatting.GRAY));
        }
        return new GuiElementBuilder(fallbackIcon).setName(Component.literal("Unknown reward"));
    }

    private static void openOne(ServerPlayer player, String id) {
        if (player == null) return;
        if (OPENINGS.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Your crate is still opening.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        CrateConfig.CrateDefinition crate = CrateConfig.getCrate(id);
        if (crate == null || !crate.enabled) { player.sendSystemMessage(Component.literal("That crate is not enabled.").withStyle(ChatFormatting.RED)); return; }
        if (!CrateCreditManager.spendCredit(player, id)) { player.sendSystemMessage(Component.literal("You do not have a " + crate.displayName + " credit.").withStyle(ChatFormatting.RED)); return; }

        RewardPlan shards = planGuaranteedShards(crate);
        RewardPlan main = rollMainReward(crate, id);
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setTitle(Component.literal(crate.displayName + " Roulette"));
        for (int i = 0; i < gui.getSize(); i++) gui.setSlot(i, filler());
        gui.setSlot(CENTER_MARKER_SLOT, centerMarkerElement());
        gui.open();

        Opening opening = new Opening(player, id, crate, gui, shards, main);
        OPENINGS.put(player.getUUID(), opening);
        updateSpin(opening, false);
        playLocalSound(player, "minecraft:ui.button.click", 0.6F, 1.2F);
    }

    public static void tick(MinecraftServer server) {
        if (server == null || OPENINGS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Opening>> iterator = OPENINGS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Opening> entry = iterator.next();
            Opening opening = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(opening.playerId);
            if (player == null) continue;

            opening.tick++;
            boolean finalLock = opening.tick >= SPIN_END_TICKS;
            int speed = spinSpeed(opening.tick);
            if (finalLock || opening.tick % speed == 0) {
                if (!finalLock) opening.offset++;
                updateSpin(opening, finalLock);
                if (opening.tick == SPIN_END_TICKS) {
                    playLocalSound(player, "minecraft:entity.player.levelup", 0.7F, isSpecial(opening.mainReward) ? 1.55F : 1.15F);
                } else if (!finalLock) {
                    playCrateTickSound(player, opening.tick);
                }
            }

            if (opening.tick >= TOTAL_TICKS) {
                iterator.remove();
                player.closeContainer();
                grantReward(player, opening.guaranteedShards);
                grantReward(player, opening.mainReward);
                player.sendSystemMessage(Component.literal("Opened " + opening.crate.displayName + ": ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(opening.mainReward.summary).withStyle(ChatFormatting.WHITE)));
                playLocalSound(player, isSpecial(opening.mainReward) ? "minecraft:ui.toast.challenge_complete" : "minecraft:entity.experience_orb.pickup", 0.8F, isSpecial(opening.mainReward) ? 1.0F : 1.25F);
                open(player);
            }
        }
    }

    private static RewardPlan planGuaranteedShards(CrateConfig.CrateDefinition crate) {
        RewardPlan plan = new RewardPlan();
        plan.type = RewardType.SHARDS;
        int min = Math.max(1, Math.min(crate.guaranteedShardMin, crate.guaranteedShardMax));
        int max = Math.max(min, Math.max(crate.guaranteedShardMin, crate.guaranteedShardMax));
        plan.shardAmount = min + RANDOM.nextInt((max - min) + 1);
        plan.shardRarity = crate.guaranteedShardRarity;
        plan.summary = plan.shardAmount + " " + ProfessionFragmentManager.formatWords(plan.shardRarity) + " shards";
        plan.icon = new ItemStack(Items.AMETHYST_SHARD);
        return plan;
    }

    private static RewardPlan rollMainReward(CrateConfig.CrateDefinition crate, String crateId) {
        int pokemonWeight = crateId.equals("mythic") ? 48 : crateId.equals("legendary") || crateId.equals("world_boss") ? 42 : 35;
        int itemWeight = 50;
        int toolWeight = Math.max(1, crate.tools == null ? 0 : crate.tools.stream().mapToInt(t -> Math.max(0, t.weight)).sum());
        int total = pokemonWeight + itemWeight + toolWeight;
        int roll = RANDOM.nextInt(Math.max(1, total));
        if ((roll -= pokemonWeight) < 0) return planPokemon(crate, crateId);
        if ((roll -= itemWeight) < 0) return planItem(crate);
        return planTool(crate);
    }

    private static RewardPlan planPokemon(CrateConfig.CrateDefinition crate, String crateId) {
        CrateConfig.WeightedPokemon wp = weighted(crate.pokemon);
        if (wp == null) return planItem(crate);
        int min = Math.max(1, crate.minPokemonLevel); int max = Math.max(min, crate.maxPokemonLevel);
        int level = min + RANDOM.nextInt((max - min) + 1);
        boolean shiny = RANDOM.nextDouble() * 100.0D < adjustedShinyChance(crate, crateId, wp.species);
        NpcShopService.PokemonCratePool pool = pool(wp.pool, wp.species);
        NpcShopService.PlannedPokemonCrateReward reward = NpcShopService.restorePlannedPokemonCrateReward(wp.species, level, shiny, pool);
        if (reward == null) return planItem(crate);
        RewardPlan plan = new RewardPlan();
        plan.type = RewardType.POKEMON;
        plan.species = wp.species;
        plan.level = level;
        plan.shiny = shiny;
        plan.pool = pool;
        plan.summary = (shiny ? "Shiny " : "") + pretty(wp.species);
        plan.icon = reward.icon() == null ? new ItemStack(Items.EGG) : reward.icon().copy();
        return plan;
    }

    private static RewardPlan planItem(CrateConfig.CrateDefinition crate) {
        CrateConfig.WeightedItem wi = weighted(crate.items);
        if (wi == null) return planBonusShard(crate);
        Item item = resolveItem(wi.itemId);
        if (item == Items.AIR) return planBonusShard(crate);
        int min = Math.max(1, Math.min(wi.amountMin, wi.amountMax)); int max = Math.max(min, Math.max(wi.amountMin, wi.amountMax));
        int amount = min + RANDOM.nextInt((max - min) + 1);
        RewardPlan plan = new RewardPlan();
        plan.type = RewardType.ITEM;
        plan.itemId = wi.itemId;
        plan.amount = amount;
        plan.summary = amount + "x " + wi.itemId;
        plan.icon = new ItemStack(item, amount);
        return plan;
    }

    private static RewardPlan planTool(CrateConfig.CrateDefinition crate) {
        CrateConfig.WeightedTool wt = weighted(crate.tools);
        if (wt == null) return planItem(crate);
        ItemStack stack = ProfessionToolManager.createLootTool(wt.toolId, false);
        if (stack.isEmpty()) return planItem(crate);
        RewardPlan plan = new RewardPlan();
        plan.type = RewardType.TOOL;
        plan.toolId = wt.toolId;
        plan.summary = "full tool: " + wt.toolId;
        plan.icon = stack.copy();
        return plan;
    }

    private static RewardPlan planBonusShard(CrateConfig.CrateDefinition crate) {
        RewardPlan plan = new RewardPlan();
        plan.type = RewardType.SHARDS;
        plan.shardRarity = crate.guaranteedShardRarity;
        plan.shardAmount = 1;
        plan.summary = "bonus shards";
        plan.icon = new ItemStack(Items.AMETHYST_SHARD);
        return plan;
    }

    private static void grantReward(ServerPlayer player, RewardPlan plan) {
        if (player == null || plan == null) return;
        switch (plan.type) {
            case SHARDS -> {
                ProfessionManager.addFragments(player, plan.shardRarity, Math.max(1, plan.shardAmount));
                player.sendSystemMessage(Component.literal("+" + Math.max(1, plan.shardAmount) + " " + ProfessionFragmentManager.formatWords(plan.shardRarity) + " shards").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            case POKEMON -> {
                NpcShopService.PlannedPokemonCrateReward reward = NpcShopService.restorePlannedPokemonCrateReward(plan.species, plan.level, plan.shiny, plan.pool);
                if (reward != null) NpcShopService.grantPlannedPokemonCrateReward(player, reward);
            }
            case ITEM -> {
                Item item = resolveItem(plan.itemId);
                if (item == Items.AIR) return;
                ItemStack stack = new ItemStack(item, Math.max(1, plan.amount));
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                if (plan.itemId != null && plan.itemId.toLowerCase(Locale.ROOT).contains("master_ball")) player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.6F);
            }
            case TOOL -> {
                ItemStack stack = ProfessionToolManager.createLootTool(plan.toolId, false);
                if (stack.isEmpty()) return;
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.75F, 1.35F);
            }
        }
    }

    private static void updateSpin(Opening opening, boolean finalLock) {
        for (int i = 0; i < SPIN_SLOTS.length; i++) {
            int spinSlot = SPIN_SLOTS[i];
            boolean center = spinSlot == CENTER_SLOT;
            RewardPlan line = finalLock && center ? opening.mainReward : opening.reel.get((opening.offset + i) % opening.reel.size());
            GuiElementBuilder rewardBuilder = rewardElement(line).hideDefaultTooltip();
            if (center && finalLock) {
                rewardBuilder.setName(Component.literal("§e§lYOUR REWARD - " + line.summary)).addLoreLine(Component.literal("§aThis is what you won."));
            } else {
                rewardBuilder.setName(Component.literal(" "));
            }
            opening.gui.setSlot(spinSlot, rewardBuilder);
            opening.gui.setSlot(spinSlot + 9, rarityGlassElement(line));
        }
    }

    private static GuiElementBuilder centerMarkerElement() {
        return new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE).hideDefaultTooltip().setName(Component.literal("§7§l▲ Winning Slot"));
    }

    private static GuiElementBuilder rarityGlassElement(RewardPlan reward) {
        return new GuiElementBuilder(glassForReward(reward)).hideDefaultTooltip().setName(Component.literal("§7↑ " + rarityLabel(reward)));
    }

    private static String rarityLabel(RewardPlan reward) {
        if (reward == null) return "§7Unknown";
        if (reward.shiny) return "§6§lSHINY";
        if (reward.type == RewardType.TOOL) return "§d§lTOOL";
        if (reward.type == RewardType.ITEM) return reward.itemId != null && reward.itemId.toLowerCase(Locale.ROOT).contains("master_ball") ? "§6§lRARE ITEM" : "§bITEM";
        if (reward.type == RewardType.SHARDS) return "§dSHARDS";
        return switch (reward.pool) {
            case LEGENDARY -> "§6§lLEGENDARY";
            case ULTRA_BEAST -> "§5§lULTRA BEAST";
            case PARADOX -> "§5§lPARADOX";
            case MYTHICAL -> "§c§lMYTHICAL";
            default -> "§aRegular";
        };
    }

    private static Item glassForReward(RewardPlan reward) {
        if (reward == null) return Items.GRAY_STAINED_GLASS_PANE;
        if (reward.shiny) return Items.YELLOW_STAINED_GLASS_PANE;
        if (reward.type == RewardType.TOOL) return Items.PURPLE_STAINED_GLASS_PANE;
        if (reward.type == RewardType.ITEM) return Items.CYAN_STAINED_GLASS_PANE;
        if (reward.type == RewardType.SHARDS) return Items.MAGENTA_STAINED_GLASS_PANE;
        return switch (reward.pool) {
            case LEGENDARY -> Items.ORANGE_STAINED_GLASS_PANE;
            case ULTRA_BEAST, PARADOX -> Items.PURPLE_STAINED_GLASS_PANE;
            case MYTHICAL -> Items.RED_STAINED_GLASS_PANE;
            default -> Items.LIME_STAINED_GLASS_PANE;
        };
    }

    private static GuiElementBuilder rewardElement(RewardPlan line) {
        ItemStack stack = line == null || line.icon == null || line.icon.isEmpty() ? new ItemStack(Items.CHEST) : line.icon.copy();
        try { return new GuiElementBuilder(stack); } catch (Throwable ignored) { return new GuiElementBuilder(stack.getItem()); }
    }

    private static List<RewardPlan> buildSpinRewards(CrateConfig.CrateDefinition crate, String crateId, RewardPlan finalReward) {
        List<RewardPlan> expanded = new ArrayList<>();
        while (expanded.size() < 36) expanded.add(rollDisplayReward(crate, crateId));
        int finalOffset = calculateFinalOffsetBeforeLock();
        int landingIndex = Math.floorMod(finalOffset + CENTER_INDEX_IN_REEL, expanded.size());
        expanded.set(landingIndex, finalReward);
        return expanded;
    }

    private static RewardPlan rollDisplayReward(CrateConfig.CrateDefinition crate, String crateId) {
        RewardPlan plan = rollMainReward(crate, crateId);
        if (plan == null) return planBonusShard(crate);
        return plan;
    }

    private static boolean isSpecial(RewardPlan reward) {
        if (reward == null) return false;
        if (reward.shiny || reward.type == RewardType.TOOL) return true;
        if (reward.type == RewardType.POKEMON && reward.pool != NpcShopService.PokemonCratePool.REGULAR) return true;
        return reward.type == RewardType.ITEM && reward.itemId != null && (reward.itemId.toLowerCase(Locale.ROOT).contains("master_ball") || reward.itemId.toLowerCase(Locale.ROOT).contains("netherite"));
    }

    private static int calculateFinalOffsetBeforeLock() { int advances = 0; for (int tick = 1; tick < SPIN_END_TICKS; tick++) { int speed = spinSpeed(tick); if (tick % speed == 0) advances++; } return advances; }
    private static int spinSpeed(int tick) { if (tick < 28) return 2; if (tick < 44) return 3; if (tick < 58) return 4; if (tick < 68) return 5; return 6; }
    private static void playCrateTickSound(ServerPlayer player, int tick) { float pitch = Math.min(1.85F, 0.85F + (tick / 70.0F)); playLocalSound(player, "minecraft:block.note_block.hat", 0.45F, pitch); }
    private static void playLocalSound(ServerPlayer player, String soundId, float volume, float pitch) { if (player == null || soundId == null || soundId.isBlank()) return; try { SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(soundId)); if (sound != null) player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch); } catch (Throwable ignored) {} }

    private static String formatPercent(double value) {
        return String.format(Locale.US, "%.2f%%", value);
    }

    private static String prettyName(String value) {
        String pretty = pretty(value);
        if (pretty == null || pretty.isBlank()) return "Unknown";
        String[] words = pretty.split(" ");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }

    private static String prettyItem(String id) {
        String value = id == null ? "item" : id;
        int colon = value.indexOf(':');
        if (colon >= 0 && colon + 1 < value.length()) value = value.substring(colon + 1);
        return prettyName(value);
    }

    private static ItemStack unidentifiedToolIcon(String toolId, Item fallbackIcon) {
        try {
            ItemStack stack = ProfessionToolManager.createTool(toolId, false);
            if (stack != null && !stack.isEmpty()) return stack;
        } catch (Throwable ignored) {
        }
        return new ItemStack(fallbackIcon == null ? Items.DIAMOND_PICKAXE : fallbackIcon);
    }

    private static ItemStack createPokemonIcon(String species, int level, boolean shiny, NpcShopService.PokemonCratePool pool) {
        try {
            Pokemon pokemon = PokemonProperties.Companion.parse("species=\"" + species + "\" level=" + level).create();
            try {
                Pokemon.class.getMethod("setShiny", boolean.class).invoke(pokemon, shiny);
            } catch (Throwable ignored) {
            }
            return PokemonItem.from(pokemon, 1);
        } catch (Throwable ignored) {
            return new ItemStack(shiny ? Items.NETHER_STAR : pool == NpcShopService.PokemonCratePool.REGULAR ? Items.EGG : Items.DRAGON_EGG);
        }
    }

    private static double adjustedShinyChance(CrateConfig.CrateDefinition crate, String crateId, String species) { if (crateId.equals("mythic") && isHighValuePokemon(species)) return 1.0D; return Math.max(0D, crate.shinyChance); }
    private static <T> T weighted(List<T> list) { if (list == null || list.isEmpty()) return null; int total = 0; for (T t : list) total += Math.max(0, weightOf(t)); if (total <= 0) return list.get(RANDOM.nextInt(list.size())); int roll = RANDOM.nextInt(total); for (T t : list) { roll -= Math.max(0, weightOf(t)); if (roll < 0) return t; } return list.get(0); }
    private static int weightOf(Object o) { if (o instanceof CrateConfig.WeightedPokemon p) return p.weight; if (o instanceof CrateConfig.WeightedItem i) return i.weight; if (o instanceof CrateConfig.WeightedTool t) return t.weight; return 1; }
    private static Item resolveItem(String id) { try { Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); return item == null ? Items.AIR : item; } catch (Exception e) { return Items.AIR; } }
    private static ChatFormatting colorFor(String id) { return switch (id) { case "common" -> ChatFormatting.WHITE; case "uncommon" -> ChatFormatting.GREEN; case "rare" -> ChatFormatting.AQUA; case "epic", "guild" -> ChatFormatting.DARK_PURPLE; case "legendary", "event", "world_boss" -> ChatFormatting.GOLD; case "mythic" -> ChatFormatting.LIGHT_PURPLE; default -> ChatFormatting.GRAY; }; }
    private static ChatFormatting poolColor(NpcShopService.PokemonCratePool pool) { return switch (pool) { case LEGENDARY -> ChatFormatting.GOLD; case ULTRA_BEAST, PARADOX -> ChatFormatting.LIGHT_PURPLE; case MYTHICAL -> ChatFormatting.RED; default -> ChatFormatting.AQUA; }; }
    private static String poolLabel(NpcShopService.PokemonCratePool pool) { return switch (pool) { case LEGENDARY -> "Legendary"; case ULTRA_BEAST -> "Ultra Beast"; case PARADOX -> "Paradox"; case MYTHICAL -> "Mythical"; default -> "Regular"; }; }
    private static String pretty(String species) { if (species == null) return "Pokemon"; int c=species.indexOf(':'); if(c>=0) species=species.substring(c+1); return species.replace('_',' '); }
    private static boolean isHighValuePokemon(String s) {
        NpcShopService.PokemonCratePool pool = pool(null, s);
        return pool != NpcShopService.PokemonCratePool.REGULAR;
    }

    private static NpcShopService.PokemonCratePool pool(String configured, String species) {
        String c=configured==null?"":configured.toUpperCase(Locale.ROOT);
        if (c.contains("ULTRA")) return NpcShopService.PokemonCratePool.ULTRA_BEAST;
        if (c.contains("PARADOX")) return NpcShopService.PokemonCratePool.PARADOX;
        if (c.contains("MYTH")) return NpcShopService.PokemonCratePool.MYTHICAL;
        if (c.contains("LEGEND")) return NpcShopService.PokemonCratePool.LEGENDARY;

        String x = species == null ? "" : species.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        int colon = x.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < x.length()) x = x.substring(colon + 1);
        String compact = x.replace("_", "");

        if (matchesAny(x, compact, "nihilego","buzzwole","pheromosa","xurkitree","celesteela","kartana","guzzlord","poipole","naganadel","stakataka","blacephalon")) return NpcShopService.PokemonCratePool.ULTRA_BEAST;
        if (matchesAny(x, compact, "great_tusk","scream_tail","brute_bonnet","flutter_mane","slither_wing","sandy_shocks","roaring_moon","walking_wake","gouging_fire","raging_bolt","iron_treads","iron_bundle","iron_hands","iron_jugulis","iron_moth","iron_thorns","iron_valiant","iron_leaves","iron_boulder","iron_crown")) return NpcShopService.PokemonCratePool.PARADOX;
        if (matchesAny(x, compact, "mew","celebi","jirachi","deoxys","phione","manaphy","darkrai","shaymin","arceus","victini","keldeo","meloetta","genesect","diancie","hoopa","volcanion","magearna","marshadow","zeraora","meltan","melmetal","zarude","pecharunt")) return NpcShopService.PokemonCratePool.MYTHICAL;
        if (matchesAny(x, compact, "articuno","zapdos","moltres","mewtwo","raikou","entei","suicune","lugia","ho_oh","regirock","regice","registeel","latias","latios","kyogre","groudon","rayquaza","uxie","mesprit","azelf","dialga","palkia","heatran","regigigas","giratina","cresselia","cobalion","terrakion","virizion","tornadus","thundurus","reshiram","zekrom","landorus","kyurem","xerneas","yveltal","zygarde","type_null","silvally","tapu_koko","tapu_lele","tapu_bulu","tapu_fini","cosmog","cosmoem","solgaleo","lunala","necrozma","zacian","zamazenta","eternatus","kubfu","urshifu","regieleki","regidrago","glastrier","spectrier","calyrex","enamorus","wo_chien","chien_pao","ting_lu","chi_yu","okidogi","munkidori","fezandipiti","ogerpon","terapagos","koraidon","miraidon")) return NpcShopService.PokemonCratePool.LEGENDARY;
        return NpcShopService.PokemonCratePool.REGULAR;
    }

    private static boolean matchesAny(String species, String compact, String... values) {
        for (String value : values) {
            String normalized = value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if (species.equals(normalized) || compact.equals(normalized.replace("_", ""))) return true;
        }
        return false;
    }
}
