package com.champutils.auction;

import com.champutils.economy.EconomyManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class AuctionHouseGui {

    private AuctionHouseGui() {}

    public static void openMain(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setTitle(Component.literal("Auction House"));
        fill(gui);

        gui.setSlot(10, cleanButton(Items.CHEST, "§6Browse Listings")
                .addLoreLine(Component.literal("§7Click listings to inspect and buy."))
                .setCallback((index, clickType, actionType, g) -> openBrowse(player)));

        gui.setSlot(11, cleanButton(Items.WRITABLE_BOOK, "§eYour Listings")
                .addLoreLine(Component.literal("§7View and cancel your active auctions."))
                .setCallback((index, clickType, actionType, g) -> openMyListings(player)));

        gui.setSlot(13, cleanButton(Items.GOLD_INGOT, "§eYour Credits")
                .addLoreLine(Component.literal("§7" + EconomyManager.format(EconomyManager.getBalance(player)))));

        gui.setSlot(15, cleanButton(Items.HOPPER, "§aClaim Purchases")
                .addLoreLine(Component.literal("§7Claim website purchases."))
                .setCallback((index, clickType, actionType, g) -> AuctionHouseService.claimNext(player)));

        gui.setSlot(16, cleanButton(Items.EGG, "§dSell Pokémon")
                .addLoreLine(Component.literal("§7Choose a party slot."))
                .addLoreLine(Component.literal("§7Then type the price command shown."))
                .addLoreLine(Component.literal("§eRequires /ah confirm before listing."))
                .setCallback((index, clickType, actionType, g) -> openSellPokemon(player)));

        gui.setSlot(22, cleanButton(Items.EMERALD, "§bSell Held Item")
                .addLoreLine(Component.literal("§7Hold the item stack you want to sell."))
                .addLoreLine(Component.literal("§7Type: §f/ah sell <price>"))
                .addLoreLine(Component.literal("§8Example: /ah sell 25000"))
                .addLoreLine(Component.literal("§eRequires /ah confirm before listing.")));

        gui.open();
    }

    public static void openSellPokemon(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x1, player, false);
        gui.setTitle(Component.literal("Sell Pokémon"));
        fill(gui);

        for (int i = 0; i < 6; i++) {
            int slotIndex = i;
            Pokemon pokemon = null;
            try { pokemon = AuctionPokemonSerializer.getPartyPokemon(player, slotIndex); } catch (Exception ignored) {}
            if (pokemon == null) {
                gui.setSlot(i, cleanButton(Items.GRAY_DYE, "§7Empty Slot " + (i + 1)));
                continue;
            }

            String name = pokemon.getDisplayName(true).getString();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§7Slot: §f" + (i + 1)));
            lore.add(Component.literal("§7Level: §f" + pokemon.getLevel()));
            lore.add(Component.literal("§7Shiny: §f" + (pokemon.getShiny() ? "Yes" : "No")));
            lore.add(Component.literal(""));
            lore.add(Component.literal("§eClick, then type:"));
            lore.add(Component.literal("§f/ah sellpokemon " + (i + 1) + " <price>"));
            lore.add(Component.literal("§8Example: /ah sellpokemon " + (i + 1) + " 50000"));

            gui.setSlot(i, pokemonButton(name, speciesIdFromPokemon(pokemon), pokemon.getShiny())
                    .setLore(lore)
                    .setCallback((index, clickType, actionType, g) -> {
                        player.closeContainer();
                        player.sendSystemMessage(Component.literal("Selected Pokémon slot " + (slotIndex + 1) + ": " + name).withStyle(ChatFormatting.LIGHT_PURPLE));
                        player.sendSystemMessage(Component.literal("Now type: /ah sellpokemon " + (slotIndex + 1) + " <price>").withStyle(ChatFormatting.YELLOW));
                        player.sendSystemMessage(Component.literal("Example: /ah sellpokemon " + (slotIndex + 1) + " 50000").withStyle(ChatFormatting.GRAY));
                    }));
        }

        gui.setSlot(8, cleanButton(Items.ARROW, "§eBack")
                .setCallback((index, clickType, actionType, g) -> openMain(player)));
        gui.open();
    }

    public static void openBrowse(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setTitle(Component.literal("Auction Listings"));
        fill(gui);
        gui.setSlot(45, cleanButton(Items.WRITABLE_BOOK, "§eYour Listings")
                .setCallback((index, clickType, actionType, g) -> openMyListings(player)));
        gui.setSlot(49, cleanButton(Items.ARROW, "§eBack")
                .setCallback((index, clickType, actionType, g) -> openMain(player)));
        gui.setSlot(53, cleanButton(Items.HOPPER, "§aClaim Purchases")
                .setCallback((index, clickType, actionType, g) -> AuctionHouseService.claimNext(player)));
        gui.open();

        CompletableFuture.supplyAsync(() -> {
            try { return AuctionHouseRepository.fetchActiveListings(45); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).whenComplete((listings, error) -> player.server.execute(() -> {
            if (error != null) {
                player.sendSystemMessage(Component.literal("Could not load auction listings. Check console.").withStyle(ChatFormatting.RED));
                error.printStackTrace();
                return;
            }

            if (listings.isEmpty()) {
                gui.setSlot(22, cleanButton(Items.BARRIER, "§7No active listings")
                        .addLoreLine(Component.literal("§8Listings will appear here once players sell items.")));
                return;
            }

            for (int i = 0; i < Math.min(45, listings.size()); i++) {
                AuctionHouseRepository.AuctionListingSummary listing = listings.get(i);
                List<Component> lore = listingLore(listing);
                lore.add(Component.literal(""));
                if (player.getUUID().toString().equalsIgnoreCase(String.valueOf(listing.sellerUuid))) {
                    lore.add(Component.literal("§eThis is your listing."));
                    lore.add(Component.literal("§7Click to inspect."));
                } else {
                    lore.add(Component.literal("§aClick to inspect and buy."));
                }

                GuiElementBuilder builder = iconFor(player, listing)
                        .setName(Component.literal("§f" + listing.title))
                        .setLore(lore)
                        .setCallback((index, clickType, actionType, g) -> openInspect(player, listing, "browse"));
                gui.setSlot(i, builder);
            }
        }));
    }

    public static void openMyListings(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setTitle(Component.literal("Your Auction Listings"));
        fill(gui);
        gui.setSlot(45, cleanButton(Items.CHEST, "§6Browse Listings")
                .setCallback((index, clickType, actionType, g) -> openBrowse(player)));
        gui.setSlot(49, cleanButton(Items.ARROW, "§eBack")
                .setCallback((index, clickType, actionType, g) -> openMain(player)));
        gui.open();

        CompletableFuture.supplyAsync(() -> {
            try { return AuctionHouseRepository.fetchSellerActiveListings(player.getUUID(), 45); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).whenComplete((listings, error) -> player.server.execute(() -> {
            if (error != null) {
                player.sendSystemMessage(Component.literal("Could not load your auction listings. Check console.").withStyle(ChatFormatting.RED));
                error.printStackTrace();
                return;
            }

            if (listings.isEmpty()) {
                gui.setSlot(22, cleanButton(Items.BARRIER, "§7No active listings")
                        .addLoreLine(Component.literal("§8Use /ah sell <price> or /ah sellpokemon <slot> <price>.")));
                return;
            }

            for (int i = 0; i < Math.min(45, listings.size()); i++) {
                AuctionHouseRepository.AuctionListingSummary listing = listings.get(i);
                List<Component> lore = listingLore(listing);
                lore.add(Component.literal(""));
                lore.add(Component.literal("§eClick to inspect."));
                lore.add(Component.literal("§cCancel button is inside inspect."));

                gui.setSlot(i, iconFor(player, listing)
                        .setName(Component.literal("§f" + listing.title))
                        .setLore(lore)
                        .setCallback((index, clickType, actionType, g) -> openInspect(player, listing, "mine")));
            }
        }));
    }

    public static void openInspect(ServerPlayer player, AuctionHouseRepository.AuctionListingSummary listing, String returnPage) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setTitle(Component.literal("Inspect Listing"));
        fill(gui);

        boolean pokemon = "POKEMON".equalsIgnoreCase(listing.kind);
        boolean mine = player.getUUID().toString().equalsIgnoreCase(String.valueOf(listing.sellerUuid));

        // Header / featured icon
        gui.setSlot(4, iconFor(player, listing)
                .setName(Component.literal((pokemon ? "§d" : "§b") + listing.title))
                .setLore(featureLore(listing)));

        // Left summary panel
        gui.setSlot(10, cleanButton(Items.NAME_TAG, "§6Listing")
                .addLoreLine(Component.literal("§7Seller: §f" + listing.sellerUsername))
                .addLoreLine(Component.literal("§7Type: §f" + cleanKind(listing.kind)))
                .addLoreLine(Component.literal("§7Quantity: §f" + listing.quantity))
                .addLoreLine(Component.literal("§7Price: §6" + EconomyManager.format(listing.price))));

        if (pokemon) {
            paintPokemonInspect(gui, listing.payload);
        } else {
            paintItemInspect(player, gui, listing);
        }

        gui.setSlot(45, cleanButton(Items.ARROW, "§cBack")
                .addLoreLine(Component.literal("§7Return without buying."))
                .setCallback((index, clickType, actionType, g) -> {
                    if ("mine".equals(returnPage)) openMyListings(player);
                    else openBrowse(player);
                }));

        if (mine) {
            gui.setSlot(49, cleanButton(Items.RED_CONCRETE, "§cCancel Auction")
                    .addLoreLine(Component.literal("§7Returns this listing to you."))
                    .setCallback((index, clickType, actionType, g) -> {
                        player.closeContainer();
                        AuctionHouseService.cancelListing(player, listing.id);
                    }));
        } else {
            gui.setSlot(49, cleanButton(Items.LIME_CONCRETE, "§aBuy Now")
                    .addLoreLine(Component.literal("§7Price: §6" + EconomyManager.format(listing.price)))
                    .addLoreLine(Component.literal("§7Your Balance: §e" + EconomyManager.format(EconomyManager.getBalance(player))))
                    .setCallback((index, clickType, actionType, g) -> {
                        player.closeContainer();
                        AuctionHouseService.buyListing(player, listing.id);
                    }));
        }

        gui.setSlot(53, cleanButton(Items.BARRIER, "§cClose")
                .setCallback((index, clickType, actionType, g) -> player.closeContainer()));

        gui.open();
    }

    private static void paintPokemonInspect(SimpleGui gui, JsonObject p) {
        gui.setSlot(12, cleanButton(Items.EXPERIENCE_BOTTLE, "§eCore Info")
                .addLoreLine(Component.literal("§7Level: §f" + get(p, "level")))
                .addLoreLine(Component.literal("§7Shiny: §f" + yesNo(get(p, "shiny"))))
                .addLoreLine(Component.literal("§7Gender: §f" + get(p, "gender")))
                .addLoreLine(Component.literal("§7Nature: §f" + prettify(get(p, "nature"))))
                .addLoreLine(Component.literal("§7Ability: §f" + prettify(get(p, "ability"))))
                .addLoreLine(Component.literal("§7Held Item: §f" + prettify(get(p, "heldItem")))));

        addStatCard(gui, 14, Items.RED_DYE, "HP", statValue(p, "ivs", "hp"), statValue(p, "evs", "hp"), 31);
        addStatCard(gui, 15, Items.ORANGE_DYE, "Attack", statValue(p, "ivs", "attack"), statValue(p, "evs", "attack"), 31);
        addStatCard(gui, 16, Items.BLUE_DYE, "Defense", statValue(p, "ivs", "defence"), statValue(p, "evs", "defence"), 31);
        addStatCard(gui, 23, Items.PURPLE_DYE, "Sp. Atk", statValue(p, "ivs", "special_attack"), statValue(p, "evs", "special_attack"), 31);
        addStatCard(gui, 24, Items.CYAN_DYE, "Sp. Def", statValue(p, "ivs", "special_defence"), statValue(p, "evs", "special_defence"), 31);
        addStatCard(gui, 25, Items.LIME_DYE, "Speed", statValue(p, "ivs", "speed"), statValue(p, "evs", "speed"), 31);

        List<Component> moves = new ArrayList<>();
        moves.add(Component.literal("§8Moves"));
        if (p != null && p.has("moves") && p.get("moves").isJsonArray()) {
            JsonArray array = p.getAsJsonArray("moves");
            if (array.size() == 0) moves.add(Component.literal("§7-"));
            for (int i = 0; i < array.size(); i++) moves.add(Component.literal("§f• " + prettify(array.get(i).getAsString())));
        } else {
            moves.add(Component.literal("§7-"));
        }
        gui.setSlot(32, cleanButton(Items.PAPER, "§bMoves").setLore(moves));
    }

    private static void paintItemInspect(ServerPlayer player, SimpleGui gui, AuctionHouseRepository.AuctionListingSummary listing) {
        JsonObject p = listing.payload;
        String rarity = prettify(firstPresent(p, "rarity", "tier"));
        String profession = prettify(firstPresent(p, "profession"));
        String quality = firstPresent(p, "overall_quality", "quality");
        String durability = durabilityText(p);
        String rerolls = firstPresent(p, "rerolls", "rolls");
        String active = prettify(firstPresent(p, "active_ability", "activeAbility", "active_ability_id"));

        gui.setSlot(12, cleanButton(Items.NETHER_STAR, "§eTool Summary")
                .addLoreLine(Component.literal("§7Rarity: §f" + blankDash(rarity)))
                .addLoreLine(Component.literal("§7Profession: §f" + blankDash(profession)))
                .addLoreLine(Component.literal("§7Quality: §f" + blankDash(quality) + suffixPercent(quality)))
                .addLoreLine(Component.literal("§7Durability: §f" + blankDash(durability)))
                .addLoreLine(Component.literal("§7Rerolls: §f" + blankDash(rerolls)))
                .addLoreLine(Component.literal("§7Active: §f" + blankDash(active))));

        JsonArray stats = p != null && p.has("stats") && p.get("stats").isJsonArray() ? p.getAsJsonArray("stats") : new JsonArray();
        int[] slots = {14, 15, 16, 23, 24, 25, 30, 31, 32, 33, 34};
        for (int i = 0; i < slots.length; i++) {
            if (i >= stats.size() || !stats.get(i).isJsonObject()) {
                gui.setSlot(slots[i], cleanButton(Items.GRAY_STAINED_GLASS_PANE, " "));
                continue;
            }
            JsonObject stat = stats.get(i).getAsJsonObject();
            String name = prettify(firstPresent(stat, "name", "key"));
            String value = firstPresent(stat, "value", "raw_value");
            double pct = doubleValue(firstPresent(stat, "roll_percent", "percent", "percentage"), -1D);
            gui.setSlot(slots[i], cleanButton(colorItemForPercent(pct), percentColor(pct) + blankDash(name))
                    .addLoreLine(Component.literal("§7Value: §f" + blankDash(value)))
                    .addLoreLine(Component.literal("§7Roll: " + percentColor(pct) + percentText(pct)))
                    .addLoreLine(Component.literal(progressBar(pct))));
        }
    }

    private static List<Component> listingLore(AuctionHouseRepository.AuctionListingSummary listing) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("§7Seller: §f" + listing.sellerUsername));
        lore.add(Component.literal("§7Kind: §f" + cleanKind(listing.kind)));
        lore.add(Component.literal("§7Quantity: §f" + listing.quantity));
        lore.add(Component.literal("§7Price: §6" + EconomyManager.format(listing.price)));

        if ("POKEMON".equalsIgnoreCase(listing.kind) && listing.payload != null) {
            JsonObject p = listing.payload;
            lore.add(Component.literal(""));
            lore.add(Component.literal("§7Level: §f" + get(p, "level")));
            lore.add(Component.literal("§7Shiny: §f" + yesNo(get(p, "shiny"))));
            lore.add(Component.literal("§7Nature: §f" + prettify(get(p, "nature"))));
            lore.add(Component.literal("§7Ability: §f" + prettify(get(p, "ability"))));
        } else if (listing.payload != null) {
            JsonObject p = listing.payload;
            lore.add(Component.literal(""));
            String quality = firstPresent(p, "overall_quality", "quality");
            if (!quality.isBlank()) lore.add(Component.literal("§7Quality: §f" + quality + suffixPercent(quality)));
            String rarity = firstPresent(p, "rarity", "tier");
            if (!rarity.isBlank()) lore.add(Component.literal("§7Rarity: §f" + prettify(rarity)));
            String durability = durabilityText(p);
            if (!durability.isBlank()) lore.add(Component.literal("§7Durability: §f" + durability));
        }
        return lore;
    }

    private static List<Component> featureLore(AuctionHouseRepository.AuctionListingSummary listing) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("§8Inspect listing"));
        lore.add(Component.literal("§7Seller: §f" + listing.sellerUsername));
        lore.add(Component.literal("§7Price: §6" + EconomyManager.format(listing.price)));
        return lore;
    }

    private static GuiElementBuilder iconFor(ServerPlayer player, AuctionHouseRepository.AuctionListingSummary listing) {
        if ("POKEMON".equalsIgnoreCase(listing.kind)) {
            String species = firstPresent(listing.payload, "species", "speciesId", "name", "displayName");
            boolean shiny = booleanValue(firstPresent(listing.payload, "shiny"));
            return pokemonButton(listing.title, species, shiny);
        }
        try {
            ItemStack stack = AuctionItemSerializer.fromPayload(player, listing.payload);
            if (stack != null && !stack.isEmpty()) return new GuiElementBuilder(stack.copy()).setLore(new ArrayList<>());
        } catch (Exception ignored) {}
        return cleanButton(Items.CHEST, "§f" + listing.title);
    }

    private static GuiElementBuilder pokemonButton(String title, String speciesName, boolean shiny) {
        ItemStack icon = createPokemonIcon(speciesName, shiny);
        GuiElementBuilder builder;
        if (icon != null && !icon.isEmpty() && icon.getItem() != Items.AIR) {
            builder = new GuiElementBuilder(icon.copy());
        } else {
            builder = new GuiElementBuilder(Items.EGG);
        }
        return builder.setName(Component.literal("§d" + blankDash(title))).setLore(new ArrayList<>());
    }

    private static ItemStack createPokemonIcon(String speciesName, boolean shiny) {
        Species species = findSpecies(speciesName);
        if (species == null) {
            return ItemStack.EMPTY;
        }
        try {
            Set<String> aspects = new HashSet<>();
            if (shiny) aspects.add("shiny");
            return PokemonItem.from(species, aspects, 1, null);
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static Species findSpecies(String speciesName) {
        if (speciesName == null || speciesName.isBlank()) return null;
        String cleaned = speciesName.trim().toLowerCase(Locale.ROOT);
        try {
            if (cleaned.contains(":")) {
                Species namespaced = PokemonSpecies.getByIdentifier(ResourceLocation.parse(cleaned));
                if (namespaced != null) return namespaced;
                cleaned = cleaned.substring(cleaned.indexOf(':') + 1);
            }
        } catch (Throwable ignored) {}
        cleaned = cleaned.replace("♀", "-f").replace("♂", "-m");
        cleaned = cleaned.replace(" ", "-").replace("_", "-");
        try {
            Species byName = PokemonSpecies.getByName(cleaned);
            if (byName != null) return byName;
        } catch (Throwable ignored) {}
        try {
            return PokemonSpecies.getByIdentifier(ResourceLocation.parse("cobblemon:" + cleaned));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String speciesIdFromPokemon(Pokemon pokemon) {
        if (pokemon == null) return "";
        try { return pokemon.getSpecies().getResourceIdentifier().toString(); }
        catch (Throwable ignored) {}
        try { return pokemon.getSpecies().getName(); }
        catch (Throwable ignored) { return ""; }
    }

    private static boolean booleanValue(String value) {
        if (value == null) return false;
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value.trim());
    }

    private static GuiElementBuilder cleanButton(net.minecraft.world.item.Item item, String name) {
        return new GuiElementBuilder(item).setName(Component.literal(name)).setLore(new ArrayList<>());
    }

    private static void addStatCard(SimpleGui gui, int slot, net.minecraft.world.item.Item item, String label, int iv, int ev, int maxIv) {
        double pct = iv < 0 ? -1 : (Math.max(0, Math.min(maxIv, iv)) / (double) maxIv) * 100D;
        gui.setSlot(slot, cleanButton(item, percentColor(pct) + label)
                .addLoreLine(Component.literal("§7IV: §f" + (iv < 0 ? "-" : iv + "/" + maxIv)))
                .addLoreLine(Component.literal("§7EV: §f" + (ev < 0 ? "-" : ev)))
                .addLoreLine(Component.literal(progressBar(pct))));
    }

    private static net.minecraft.world.item.Item colorItemForPercent(double percent) {
        if (percent < 0) return Items.GRAY_DYE;
        if (percent >= 90) return Items.LIME_DYE;
        if (percent >= 70) return Items.GREEN_DYE;
        if (percent >= 50) return Items.YELLOW_DYE;
        if (percent >= 30) return Items.ORANGE_DYE;
        return Items.RED_DYE;
    }

    private static String percentColor(double percent) {
        if (percent < 0) return "§7";
        if (percent >= 90) return "§a";
        if (percent >= 70) return "§2";
        if (percent >= 50) return "§e";
        if (percent >= 30) return "§6";
        return "§c";
    }

    private static String progressBar(double percent) {
        if (percent < 0) return "§8[----------] §7-";
        int filled = (int) Math.round(Math.max(0, Math.min(100, percent)) / 10D);
        StringBuilder sb = new StringBuilder("§8[");
        for (int i = 0; i < 10; i++) sb.append(i < filled ? percentColor(percent) + "■" : "§8■");
        sb.append("§8] ").append(percentColor(percent)).append(percentText(percent));
        return sb.toString();
    }

    private static String percentText(double percent) {
        if (percent < 0) return "-";
        if (Math.abs(percent - Math.round(percent)) < 0.05D) return String.valueOf((int) Math.round(percent)) + "%";
        return String.format(Locale.US, "%.1f%%", percent);
    }

    private static String cleanKind(String kind) {
        if (kind == null) return "Listing";
        if ("POKEMON".equalsIgnoreCase(kind)) return "Pokémon";
        if ("ITEM".equalsIgnoreCase(kind)) return "Item";
        return prettify(kind);
    }

    private static String yesNo(String value) {
        if ("true".equalsIgnoreCase(value)) return "Yes";
        if ("false".equalsIgnoreCase(value)) return "No";
        return blankDash(value);
    }

    private static String get(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "-";
        try { return object.get(key).getAsString(); }
        catch (Exception ignored) { return "-"; }
    }

    private static String firstPresent(JsonObject object, String... keys) {
        if (object == null || keys == null) return "";
        for (String key : keys) {
            if (key != null && object.has(key) && !object.get(key).isJsonNull()) {
                try { return object.get(key).getAsString(); } catch (Exception ignored) {}
            }
        }
        return "";
    }

    private static int statValue(JsonObject object, String group, String key) {
        if (object == null || group == null || key == null || !object.has(group) || !object.get(group).isJsonObject()) return -1;
        JsonObject stats = object.getAsJsonObject(group);
        String[] candidates = {key, key.replace("defence", "defense"), key.replace("special_", "special"), key.replace("special_defence", "specialDefense"), key.replace("special_attack", "specialAttack")};
        for (String candidate : candidates) {
            if (stats.has(candidate) && !stats.get(candidate).isJsonNull()) {
                try { return stats.get(candidate).getAsInt(); } catch (Exception ignored) {}
            }
        }
        return -1;
    }

    private static String movesText(JsonObject object) {
        if (object == null || !object.has("moves") || !object.get("moves").isJsonArray()) return "-";
        JsonArray array = object.getAsJsonArray("moves");
        List<String> moves = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) moves.add(prettify(array.get(i).getAsString()));
        return moves.isEmpty() ? "-" : String.join(", ", moves);
    }

    private static String durabilityText(JsonObject object) {
        String current = firstPresent(object, "durability", "current_durability");
        String max = firstPresent(object, "max_durability", "maximum_durability");
        if (!current.isBlank() && !max.isBlank()) return current + "/" + max;
        return current;
    }

    private static String prettify(String input) {
        if (input == null) return "-";
        String value = input.trim();
        if (value.isBlank() || "-".equals(value)) return "-";
        int lastDot = value.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < value.length()) value = value.substring(lastDot + 1);
        int lastColon = value.lastIndexOf(':');
        if (lastColon >= 0 && lastColon + 1 < value.length()) value = value.substring(lastColon + 1);
        value = value.replace("_", " ").replace("-", " ").replace("/", " ").replaceAll("(?i)ability$", "").trim();
        if (value.isBlank()) return "-";
        String[] parts = value.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.length() > 1 ? part.substring(1).toLowerCase(Locale.ROOT) : "");
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private static String blankDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String suffixPercent(String value) {
        if (value == null || value.isBlank()) return "";
        return value.contains("%") ? "" : "%";
    }

    private static double doubleValue(String value, double fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return Double.parseDouble(value.replace("%", "")); } catch (Exception ignored) { return fallback; }
    }

    private static void fill(SimpleGui gui) {
        // Intentionally left blank.
        // Auction menus should not use filler glass panes so the GUI stays clean and uncluttered.
    }
}
