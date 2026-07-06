package com.champutils.menu;

import com.champutils.adventureguide.AdventureGuideManager;
import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.dex.TrueCaughtDexManager;
import com.champutils.matchmaking.PokemonIconUtil;
import com.champutils.profession.ProfessionFragmentManager;
import com.champutils.rank.RankedTokenConfig;
import com.champutils.rank.RankedTokenManager;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Locale;

public final class RankedShopMenu {
    private static final int[] CONTENT = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private RankedShopMenu() {}

    public static void open(ServerPlayer player) { openPokemon(player, 0); }

    public static void openPokemon(ServerPlayer player, int page) {
        AdventureGuideManager.increment(player, "shop", 1);
        SimpleGui gui = base(player, "Ranked Pokémon Shop");
        header(gui, player, Items.DRAGON_EGG, "§dRanked Pokémon Shop", "§7Level 1, random IVs/nature/ability. Counts for True Dex.");
        tabs(gui, player, true);
        int maxPage = Math.max(0, (RankedTokenConfig.CONFIG.pokemon.size() - 1) / CONTENT.length);
        int fixed = Math.max(0, Math.min(page, maxPage));
        int start = fixed * CONTENT.length;
        for (int i=0;i<CONTENT.length && start+i<RankedTokenConfig.CONFIG.pokemon.size();i++) {
            RankedTokenConfig.PokemonEntry e = RankedTokenConfig.CONFIG.pokemon.get(start+i);
            if (e == null || e.species == null) continue;
            String canonical = PokemonIconUtil.resolveSpeciesId(e.species);
            boolean loaded = canonical != null && !canonical.isBlank();
            ItemStack icon = PokemonIconUtil.createPokemonIcon(loaded ? canonical : e.species, false, "cobblemon:poke_ball", false);
            GuiElementBuilder builder = new GuiElementBuilder(icon).hideDefaultTooltip()
                    .setName(Component.literal((loaded ? "§e" : "§c") + pretty(e.species)))
                    .addLoreLine(Component.literal("§7Cost: §d" + e.cost + " Ranked Tokens"));
            if (loaded) {
                builder.addLoreLine(Component.literal("§eClick to buy."));
            } else {
                builder.addLoreLine(Component.literal("§cSpecies is not loaded on this server."));
                builder.addLoreLine(Component.literal("§7Check that the matching datapack/mod is installed."));
            }
            final int pageForCallback = fixed;
            builder.setCallback((slot, click, type) -> {
                if (loaded) buyPokemon(player, canonical, e.species, e.cost, pageForCallback);
            });
            gui.setSlot(CONTENT[i], builder);
        }
        if (fixed > 0) gui.setSlot(45, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§ePrevious Page")).setCallback((i,c,t)->openPokemon(player, fixed-1)));
        gui.setSlot(49, new GuiElementBuilder(Items.BOOK).hideDefaultTooltip().setName(Component.literal("§7Page §f" + (fixed+1) + "§7/§f" + (maxPage+1))));
        if (fixed < maxPage) gui.setSlot(53, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eNext Page")).setCallback((i,c,t)->openPokemon(player, fixed+1)));
        gui.open();
    }

    public static void openItems(ServerPlayer player) {
        SimpleGui gui = base(player, "Ranked Item Shop");
        header(gui, player, Items.NETHER_STAR, "§dRanked Item Shop", "§7Spend ranked tokens on competitive items.");
        tabs(gui, player, false);
        for (int i=0;i<CONTENT.length && i<RankedTokenConfig.CONFIG.items.size();i++) {
            RankedTokenConfig.ItemEntry e = RankedTokenConfig.CONFIG.items.get(i);
            ItemStack displayStack = createConfiguredItemStack(e, false);
            if (displayStack.isEmpty()) displayStack = new ItemStack(Items.BARRIER);
            gui.setSlot(CONTENT[i], new GuiElementBuilder(displayStack).hideDefaultTooltip()
                    .setName(Component.literal("§e" + displayName(e)))
                    .addLoreLine(Component.literal("§7Amount: §a" + Math.max(1, e.amount)))
                    .addLoreLine(Component.literal("§7Cost: §d" + e.cost + " Ranked Tokens"))
                    .addLoreLine(Component.literal(displayStack.getItem() == Items.BARRIER ? "§cThis item is unavailable." : "§eClick to buy."))
                    .setCallback((slot, click, type) -> buyItem(player, e)));
        }
        gui.open();
    }

    private static SimpleGui base(ServerPlayer player, String title) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal(title));
        MenuUtil.fillBorders(gui, 4, 10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43,45,49,53);
        return gui;
    }

    private static void header(SimpleGui gui, ServerPlayer player, Item icon, String title, String lore) {
        gui.setSlot(4, new GuiElementBuilder(icon).hideDefaultTooltip().setName(Component.literal(title))
                .addLoreLine(Component.literal(lore))
                .addLoreLine(Component.literal("§7Balance: §d" + RankedTokenManager.cachedBalance(player) + " Ranked Tokens")));
    }

    private static void tabs(SimpleGui gui, ServerPlayer player, boolean pokemon) {
        gui.setSlot(0, new GuiElementBuilder(Items.DRAGON_EGG).hideDefaultTooltip().setName(Component.literal((pokemon ? "§a" : "§7") + "Pokémon")).setCallback((i,c,t)->openPokemon(player,0)));
        gui.setSlot(1, new GuiElementBuilder(Items.NETHER_STAR).hideDefaultTooltip().setName(Component.literal((pokemon ? "§7" : "§a") + "Items")).setCallback((i,c,t)->openItems(player)));
    }

    private static void buyPokemon(ServerPlayer player, String canonicalSpecies, String displaySpecies, int cost, int page) {
        int price = Math.max(1, cost);
        try {
            String speciesToCreate = canonicalSpecies == null || canonicalSpecies.isBlank() ? displaySpecies : canonicalSpecies;
            Pokemon pokemon = PokemonProperties.Companion.parse("species=\"" + speciesToCreate + "\" level=1").create();
            if (!RankedTokenManager.spend(player, price)) { player.sendSystemMessage(Component.literal("§cNot enough Ranked Tokens.")); return; }
            boolean added = AuctionPokemonSerializer.addToFirstOpenPartySlot(player, pokemon) || AuctionPokemonSerializer.addToPc(player, pokemon);
            if (!added) { player.sendSystemMessage(Component.literal("§cCould not add Pokémon. Contact staff. Your tokens were already reserved; contact staff if this happens.")); return; }
            TrueCaughtDexManager.markTrueCaught(player, pokemon);
            player.sendSystemMessage(Component.literal("§aPurchased §e" + pretty(displaySpecies) + "§a for §d" + price + " Ranked Tokens§a."));
        } catch (Throwable throwable) { player.sendSystemMessage(Component.literal("§cCould not create Pokémon: " + displaySpecies)); }
        openPokemon(player, page);
    }

    private static void buyItem(ServerPlayer player, RankedTokenConfig.ItemEntry e) {
        if (e == null) return;
        ItemStack stack = createConfiguredItemStack(e, true);
        if (stack.isEmpty() || stack.getItem() == Items.AIR || stack.getItem() == Items.BARRIER) { player.sendSystemMessage(Component.literal("§cThat shop item is unavailable right now.")); return; }
        int cost = Math.max(1, e.cost);
        if (!RankedTokenManager.spend(player, cost)) { player.sendSystemMessage(Component.literal("§cNot enough Ranked Tokens.")); return; }
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        player.sendSystemMessage(Component.literal("§aPurchased §e" + displayName(e) + "§a for §d" + cost + " Ranked Tokens§a."));
        openItems(player);
    }

    private static ItemStack createConfiguredItemStack(RankedTokenConfig.ItemEntry e, boolean purchaseStack) {
        if (e == null || e.item == null || e.item.isBlank()) return ItemStack.EMPTY;
        ItemStack bottleCap = bottleCapStack(e.item, purchaseStack ? Math.max(1, e.amount) : 1);
        if (!bottleCap.isEmpty()) return bottleCap;

        Item item = resolveItem(e.item);
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, purchaseStack ? Math.max(1, e.amount) : 1);
    }

    private static ItemStack bottleCapStack(String id, int amount) {
        String path = normalizedPath(id);
        if (path.isBlank()) return ItemStack.EMPTY;

        if (path.equals("gold_bottle_cap") || path.equals("golden_bottle_cap")) {
            ItemStack stack = new ItemStack(Items.PAPER, Math.max(1, amount));
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(2));
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Golden Bottle Cap").withStyle(ChatFormatting.GOLD));
            stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Utility Items").withStyle(ChatFormatting.BLUE))));
            return stack;
        }

        SilverBottleCap silver = silverBottleCap(path);
        if (silver != null) {
            ItemStack stack = new ItemStack(Items.PAPER, Math.max(1, amount));
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(silver.requiredName));
            stack.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal(silver.displayName).withStyle(ChatFormatting.GRAY),
                    Component.literal("Utility Items").withStyle(ChatFormatting.BLUE)
            )));
            return stack;
        }

        return ItemStack.EMPTY;
    }

    private record SilverBottleCap(String requiredName, String displayName) {}

    private static SilverBottleCap silverBottleCap(String path) {
        return switch (path) {
            case "bottle_cap", "silver_bottle_cap", "silver_bottle_cap_atk", "silver_bottle_cap_attack", "attack_bottle_cap" -> new SilverBottleCap("Atk", "Attack Bottle Cap");
            case "silver_bottle_cap_def", "silver_bottle_cap_defence", "silver_bottle_cap_defense", "defence_bottle_cap", "defense_bottle_cap" -> new SilverBottleCap("Def", "Defence Bottle Cap");
            case "silver_bottle_cap_hp", "hp_bottle_cap" -> new SilverBottleCap("HP", "HP Bottle Cap");
            case "silver_bottle_cap_sp_atk", "silver_bottle_cap_special_attack", "special_attack_bottle_cap", "sp_atk_bottle_cap" -> new SilverBottleCap("Sp.Atk", "Special Attack Bottle Cap");
            case "silver_bottle_cap_sp_def", "silver_bottle_cap_special_defence", "silver_bottle_cap_special_defense", "special_defence_bottle_cap", "special_defense_bottle_cap", "sp_def_bottle_cap" -> new SilverBottleCap("Sp.Def", "Special Defence Bottle Cap");
            case "silver_bottle_cap_speed", "speed_bottle_cap" -> new SilverBottleCap("Speed", "Speed Bottle Cap");
            default -> null;
        };
    }

    private static String normalizedPath(String id) {
        String value = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (value.contains(":")) value = value.substring(value.indexOf(':') + 1);
        return value.replace('-', '_').replace(' ', '_');
    }

    private static Item resolveItem(String id) {
        try {
            if (!bottleCapStack(id, 1).isEmpty()) return Items.PAPER;
            return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        } catch (Throwable ignored) { return Items.AIR; }
    }

    private static String displayName(RankedTokenConfig.ItemEntry e) {
        if (e == null) return "Item";
        if (e.displayName != null && !e.displayName.isBlank()) return e.displayName;
        SilverBottleCap silver = silverBottleCap(normalizedPath(e.item));
        if (silver != null) return silver.displayName;
        String path = normalizedPath(e.item);
        if (path.equals("gold_bottle_cap") || path.equals("golden_bottle_cap")) return "Golden Bottle Cap";
        return ProfessionFragmentManager.formatWords(e.item);
    }

    private static String pretty(String raw) { String s = raw == null ? "Pokemon" : raw; int c=s.lastIndexOf(':'); if(c>=0)s=s.substring(c+1); String[] parts=s.replace('_',' ').replace('-', ' ').split(" "); StringBuilder b=new StringBuilder(); for(String p:parts){ if(p.isBlank())continue; if(!b.isEmpty())b.append(' '); b.append(p.substring(0,1).toUpperCase(Locale.ROOT)).append(p.substring(1)); } return b.toString(); }
}
