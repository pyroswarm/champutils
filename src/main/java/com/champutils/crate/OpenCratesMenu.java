package com.champutils.crate;

import com.champutils.profession.ProfessionFragmentManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionToolManager;
import com.champutils.shop.NpcShopService;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class OpenCratesMenu {
    private static final Random RANDOM = new Random();
    private static final String[] ORDER = {"common","uncommon","rare","epic","legendary","mythic","event","guild","world_boss"};
    private OpenCratesMenu() {}

    public static void open(ServerPlayer player) {
        if (player == null) return;
        CrateConfig.load();
        CrateCreditManager.load();
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setTitle(Component.literal("Open Crates"));
        for (int i=0;i<gui.getSize();i++) gui.setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Component.literal(" ")));
        int[] slots = {9,10,11,12,13,14,15,16,22};
        for (int i=0;i<ORDER.length;i++) {
            String id = ORDER[i];
            CrateConfig.CrateDefinition crate = CrateConfig.getCrate(id);
            if (crate == null || !crate.enabled) continue;
            int credits = CrateCreditManager.getCredits(player, id);
            Item icon = resolveItem(crate.iconItem);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("Credits: " + credits).withStyle(credits > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
            lore.add(Component.literal("Guaranteed: " + crate.guaranteedShardMin + "-" + crate.guaranteedShardMax + " " + ProfessionFragmentManager.formatWords(crate.guaranteedShardRarity) + " shards").withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("Pokemon, items, shards, and tool chances scale by crate tier.").withStyle(ChatFormatting.DARK_GRAY));
            lore.add(Component.literal("Click to open.").withStyle(ChatFormatting.YELLOW));
            GuiElementBuilder b = new GuiElementBuilder(icon).setName(Component.literal(crate.displayName).withStyle(colorFor(id)));
            for (Component line : lore) b.addLoreLine(line);
            gui.setSlot(slots[i], b.setCallback((index, type, action) -> openOne(player, id)));
        }
        gui.open();
    }

    private static void openOne(ServerPlayer player, String id) {
        CrateConfig.CrateDefinition crate = CrateConfig.getCrate(id);
        if (crate == null || !crate.enabled) { player.sendSystemMessage(Component.literal("That crate is not enabled.").withStyle(ChatFormatting.RED)); return; }
        if (!CrateCreditManager.spendCredit(player, id)) { player.sendSystemMessage(Component.literal("You do not have a " + crate.displayName + " credit.").withStyle(ChatFormatting.RED)); return; }
        grantGuaranteedShards(player, crate);
        String summary = rollAndGrantMainReward(player, crate, id);
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_IN, SoundSource.PLAYERS, 0.6F, 1.15F);
        player.sendSystemMessage(Component.literal("Opened " + crate.displayName + ": ").withStyle(ChatFormatting.GOLD).append(Component.literal(summary).withStyle(ChatFormatting.WHITE)));
        open(player);
    }

    private static void grantGuaranteedShards(ServerPlayer player, CrateConfig.CrateDefinition crate) {
        int min = Math.max(1, Math.min(crate.guaranteedShardMin, crate.guaranteedShardMax));
        int max = Math.max(min, Math.max(crate.guaranteedShardMin, crate.guaranteedShardMax));
        int amount = min + RANDOM.nextInt((max - min) + 1);
        ProfessionManager.addFragments(player, crate.guaranteedShardRarity, amount);
        player.sendSystemMessage(Component.literal("+" + amount + " " + ProfessionFragmentManager.formatWords(crate.guaranteedShardRarity) + " shards").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static String rollAndGrantMainReward(ServerPlayer player, CrateConfig.CrateDefinition crate, String crateId) {
        int pokemonWeight = crateId.equals("mythic") ? 48 : crateId.equals("legendary") || crateId.equals("world_boss") ? 42 : 35;
        int itemWeight = 50;
        int toolWeight = Math.max(1, crate.tools == null ? 0 : crate.tools.stream().mapToInt(t -> Math.max(0, t.weight)).sum());
        int total = pokemonWeight + itemWeight + toolWeight;
        int roll = RANDOM.nextInt(Math.max(1, total));
        if ((roll -= pokemonWeight) < 0) return grantPokemon(player, crate, crateId);
        if ((roll -= itemWeight) < 0) return grantItem(player, crate);
        return grantTool(player, crate);
    }

    private static String grantPokemon(ServerPlayer player, CrateConfig.CrateDefinition crate, String crateId) {
        CrateConfig.WeightedPokemon wp = weighted(crate.pokemon);
        if (wp == null) return grantItem(player, crate);
        int min = Math.max(1, crate.minPokemonLevel); int max = Math.max(min, crate.maxPokemonLevel);
        int level = min + RANDOM.nextInt((max - min) + 1);
        boolean shiny = RANDOM.nextDouble() * 100.0D < adjustedShinyChance(crate, crateId, wp.species);
        NpcShopService.PokemonCratePool pool = pool(wp.pool, wp.species);
        NpcShopService.PlannedPokemonCrateReward reward = NpcShopService.restorePlannedPokemonCrateReward(wp.species, level, shiny, pool);
        if (reward != null && NpcShopService.grantPlannedPokemonCrateReward(player, reward)) return (shiny ? "Shiny " : "") + pretty(wp.species);
        return grantItem(player, crate);
    }

    private static double adjustedShinyChance(CrateConfig.CrateDefinition crate, String crateId, String species) {
        if (crateId.equals("mythic") && isHighValuePokemon(species)) return 1.0D;
        return Math.max(0D, crate.shinyChance);
    }

    private static String grantItem(ServerPlayer player, CrateConfig.CrateDefinition crate) {
        CrateConfig.WeightedItem wi = weighted(crate.items);
        if (wi == null) return "bonus shards";
        Item item = resolveItem(wi.itemId);
        if (item == Items.AIR) return "bonus shards";
        int min = Math.max(1, Math.min(wi.amountMin, wi.amountMax)); int max = Math.max(min, Math.max(wi.amountMin, wi.amountMax));
        int amount = min + RANDOM.nextInt((max - min) + 1);
        ItemStack stack = new ItemStack(item, amount);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        if (wi.itemId.toLowerCase(Locale.ROOT).contains("master_ball")) player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.6F);
        return amount + "x " + wi.itemId;
    }

    private static String grantTool(ServerPlayer player, CrateConfig.CrateDefinition crate) {
        CrateConfig.WeightedTool wt = weighted(crate.tools);
        if (wt == null) return grantItem(player, crate);
        ItemStack stack = ProfessionToolManager.createLootTool(wt.toolId, false);
        if (stack.isEmpty()) return grantItem(player, crate);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.75F, 1.35F);
        return "full tool: " + wt.toolId;
    }

    private static <T> T weighted(List<T> list) {
        if (list == null || list.isEmpty()) return null;
        int total = 0;
        for (T t : list) total += Math.max(0, weightOf(t));
        if (total <= 0) return list.get(RANDOM.nextInt(list.size()));
        int roll = RANDOM.nextInt(total);
        for (T t : list) { roll -= Math.max(0, weightOf(t)); if (roll < 0) return t; }
        return list.get(0);
    }
    private static int weightOf(Object o) { if (o instanceof CrateConfig.WeightedPokemon p) return p.weight; if (o instanceof CrateConfig.WeightedItem i) return i.weight; if (o instanceof CrateConfig.WeightedTool t) return t.weight; return 1; }

    private static Item resolveItem(String id) {
        try { Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); return item == null ? Items.AIR : item; } catch (Exception e) { return Items.AIR; }
    }
    private static ChatFormatting colorFor(String id) { return switch (id) { case "common" -> ChatFormatting.WHITE; case "uncommon" -> ChatFormatting.GREEN; case "rare" -> ChatFormatting.AQUA; case "epic", "guild" -> ChatFormatting.DARK_PURPLE; case "legendary", "event", "world_boss" -> ChatFormatting.GOLD; case "mythic" -> ChatFormatting.LIGHT_PURPLE; default -> ChatFormatting.GRAY; }; }
    private static String pretty(String species) { if (species == null) return "Pokemon"; int c=species.indexOf(':'); if(c>=0) species=species.substring(c+1); return species.replace('_',' '); }
    private static boolean isHighValuePokemon(String s) { String x=s==null?"":s.toLowerCase(Locale.ROOT); return x.contains("mewtwo")||x.contains("rayquaza")||x.contains("kyogre")||x.contains("groudon")||x.contains("zacian")||x.contains("koraidon")||x.contains("miraidon")||x.contains("iron_")||x.contains("roaring_")||x.contains("kartana")||x.contains("guzzlord"); }
    private static NpcShopService.PokemonCratePool pool(String configured, String species) { String c=configured==null?"":configured.toUpperCase(Locale.ROOT); if (c.contains("LEGEND")) return NpcShopService.PokemonCratePool.LEGENDARY; if (c.contains("ULTRA")) return NpcShopService.PokemonCratePool.ULTRA_BEAST; if (c.contains("PARADOX")) return NpcShopService.PokemonCratePool.PARADOX; if (c.contains("MYTH")) return NpcShopService.PokemonCratePool.MYTHICAL; if (isHighValuePokemon(species)) return NpcShopService.PokemonCratePool.LEGENDARY; return NpcShopService.PokemonCratePool.REGULAR; }
}
