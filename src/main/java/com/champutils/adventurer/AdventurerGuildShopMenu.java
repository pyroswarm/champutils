package com.champutils.adventurer;

import com.champutils.adventureguide.AdventureGuideManager;
import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.dex.TrueCaughtDexManager;
import com.champutils.economy.EconomyManager;
import com.champutils.matchmaking.PokemonIconUtil;
import com.champutils.menu.MenuUtil;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AdventurerGuildShopMenu {
    private static final int PAGE_SIZE = 36;
    private static final String ALL_TYPES = "all";
    private static final List<String> TYPE_FILTERS = List.of(
            "all", "normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground",
            "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"
    );

    private static final Set<String> LEGENDARY = set("articuno","zapdos","moltres","mewtwo","raikou","entei","suicune","lugia","hooh","ho_oh","regirock","regice","registeel","latias","latios","kyogre","groudon","rayquaza","uxie","mesprit","azelf","dialga","palkia","heatran","regigigas","giratina","cresselia","cobalion","terrakion","virizion","tornadus","thundurus","reshiram","zekrom","landorus","kyurem","xerneas","yveltal","zygarde","typenull","type_null","silvally","tapukoko","tapu_koko","tapulele","tapu_lele","tapubulu","tapu_bulu","tapufini","tapu_fini","cosmog","cosmoem","solgaleo","lunala","necrozma","zacian","zamazenta","eternatus","kubfu","urshifu","regieleki","regidrago","glastrier","spectrier","calyrex","enamorus","wochien","wo_chien","chienpao","chien_pao","tinglu","ting_lu","chiyu","chi_yu","okidogi","munkidori","fezandipiti","ogerpon","terapagos","koraidon","miraidon");
    private static final Set<String> MYTHICAL = set("mew","celebi","jirachi","deoxys","phione","manaphy","darkrai","shaymin","arceus","victini","keldeo","meloetta","genesect","diancie","hoopa","volcanion","magearna","marshadow","zeraora","meltan","melmetal","zarude","pecharunt");
    private static final Set<String> ULTRA_BEAST = set("nihilego","buzzwole","pheromosa","xurkitree","celesteela","kartana","guzzlord","poipole","naganadel","stakataka","blacephalon");
    private static final Set<String> PARADOX = set("greattusk","great_tusk","screamtail","scream_tail","brutebonnet","brute_bonnet","fluttermane","flutter_mane","slitherwing","slither_wing","sandyshocks","sandy_shocks","roaringmoon","roaring_moon","walkingwake","walking_wake","gougingfire","gouging_fire","ragingbolt","raging_bolt","irontreads","iron_treads","ironbundle","iron_bundle","ironhands","iron_hands","ironjugulis","iron_jugulis","ironmoth","iron_moth","ironthorns","iron_thorns","ironvaliant","iron_valiant","ironleaves","iron_leaves","ironboulder","iron_boulder","ironcrown","iron_crown");

    private static final Set<String> E_RANK = set("rattata","zigzagoon","bidoof","sentret","pidgey","hoothoot","starly","pidove","fletchling","rookidee","caterpie","weedle","wurmple","scatterbug","snom","magikarp","goldeen","sunkern","patrat","yungoos","skwovet","lechonk","poochyena","nickit","bunnelby","wooloo","nacli","wooper","woper","lotad","seedot","spearow","nidoranf","nidoranm","nidoran_f","nidoran_m");
    private static final Set<String> D_RANK = set("pikachu","eevee","shinx","mareep","growlithe","vulpix","sandshrew","ekans","oddish","bellsprout","psyduck","poliwag","machop","geodude","slowpoke","magnemite","gastly","onix","cubone","horsea","staryu","chikorita","cyndaquil","totodile","treecko","torchic","mudkip","turtwig","chimchar","piplup","snivy","tepig","oshawott","chespin","fennekin","froakie","rowlet","litten","popplio","grookey","scorbunny","sobble","sprigatito","fuecoco","quaxly");
    private static final Set<String> C_RANK = set("riolu","ralts","togepi","tyrogue","sneasel","heracross","scyther","pinsir","skarmory","drilbur","rotom","zorua","larvesta","mienfoo","pawniard","noibat","salandit","mimikyu","toxel","applin","sinistea","charcadet","frigibax","tinkatink","finizen","varoom","dreepy","goomy","deino","jangmoo","jangmo_o");
    private static final Set<String> B_RANK = set("dratini","larvitar","bagon","beldum","gible","axew","goomy","dreepy","frigibax","ditto","eevee","porygon","aerodactyl","omanyte","kabuto","lileep","anorith","cranidos","shieldon","tirtouga","archen","tyrunt","amaura","dracozolt","arctozolt","dracovish","arctovish","spiritomb","rotom","zorua","riolu");

    private AdventurerGuildShopMenu() {}

    public static void open(ServerPlayer player) {
        AdventureGuideManager.increment(player, "shop", 1);
        open(player, AdventurerGuildManager.currentRankId(player), ALL_TYPES, 0);
    }

    public static void open(ServerPlayer player, String requestedRank, String typeFilter, int page) {
        if (player == null) return;
        String playerRank = AdventurerGuildManager.currentRankId(player);
        String rank = AdventurerRankUtil.normalizeRank(requestedRank);
        if (!AdventurerRankUtil.atLeast(playerRank, rank)) rank = playerRank;
        String filter = normalizeType(typeFilter);

        List<ShopSpecies> species = availableSpecies(rank, filter);
        int maxPage = Math.max(0, (species.size() - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(maxPage, page));

        final String selectedRank = rank;
        final String selectedFilter = filter;
        final int selectedPage = safePage;

        AdventurerGuildDataManager.PlayerData data = AdventurerGuildManager.getData(player);
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Adventurer Shop " + rank + " " + (safePage + 1) + "/" + (maxPage + 1)));

        int start = safePage * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = start + slot;
            if (index >= species.size()) break;
            ShopSpecies entry = species.get(index);
            gui.setSlot(slot, speciesButton(player, entry, selectedRank, selectedFilter, selectedPage, data));
        }

        gui.setSlot(36, new GuiElementBuilder(Items.BELL)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Adventurer Shop"))
                .addLoreLine(Component.literal("§7Your Rank: §f" + AdventurerRankUtil.displayRank(playerRank)))
                .addLoreLine(Component.literal("§7Viewing: §f" + AdventurerRankUtil.displayRank(selectedRank)))
                .addLoreLine(Component.literal("§7Filter: §f" + pretty(selectedFilter)))
                .addLoreLine(Component.literal("§7Adventurer's Marks: §b" + data.guildMarks))
                .addLoreLine(Component.literal("§7All Pokémon are §eLevel 1§7."))
                .addLoreLine(Component.literal("§7Purchases count for True Dex.")));

        int[] rankSlots = {38, 39, 40, 41, 42, 43};
        String[] ranks = {"E", "D", "C", "B", "A", "S"};
        for (int i = 0; i < ranks.length; i++) {
            String r = ranks[i];
            boolean unlocked = AdventurerRankUtil.atLeast(playerRank, r);
            gui.setSlot(rankSlots[i], new GuiElementBuilder(unlocked ? rankIcon(r) : Items.GRAY_DYE)
                    .hideDefaultTooltip()
                    .setName(Component.literal((unlocked ? AdventurerRankUtil.color(r) : ChatFormatting.DARK_GRAY) + r + " Rank Shop"))
                    .addLoreLine(Component.literal(unlocked ? "§eClick to view this rank." : "§cLocked"))
                    .setCallback((s, c, a) -> { if (unlocked) open(player, r, selectedFilter, 0); }));
        }

        gui.setSlot(45, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eBack to Adventurer's Guild")).setCallback((s,c,a) -> AdventurerGuildMenu.open(player)));
        if (selectedPage > 0) gui.setSlot(46, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§ePrevious Page")).setCallback((s,c,a) -> open(player, selectedRank, selectedFilter, selectedPage - 1)));
        gui.setSlot(49, new GuiElementBuilder(Items.HOPPER).hideDefaultTooltip().setName(Component.literal("§bType Filter: §f" + pretty(selectedFilter))).addLoreLine(Component.literal("§eClick to choose a type.")).setCallback((s,c,a) -> openTypeFilter(player, selectedRank, selectedFilter)));
        if (selectedPage < maxPage) gui.setSlot(52, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eNext Page")).setCallback((s,c,a) -> open(player, selectedRank, selectedFilter, selectedPage + 1)));
        gui.setSlot(53, new GuiElementBuilder(CobblemonItems.POKE_BALL).hideDefaultTooltip().setName(Component.literal("§f" + species.size() + " Pokémon available")));
        gui.open();
    }

    private static void openTypeFilter(ServerPlayer player, String rank, String current) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Choose Pokémon Type"));
        for (int i = 0; i < TYPE_FILTERS.size(); i++) {
            String type = TYPE_FILTERS.get(i);
            gui.setSlot(i, new GuiElementBuilder(typeIcon(type))
                    .hideDefaultTooltip()
                    .setName(Component.literal((type.equals(current) ? "§a" : "§b") + pretty(type)))
                    .addLoreLine(Component.literal("§eClick to filter the shop."))
                    .setCallback((s,c,a) -> open(player, rank, type, 0)));
        }
        gui.setSlot(26, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eBack to Adventurer's Guild")).setCallback((s,c,a) -> AdventurerGuildMenu.open(player)));
        gui.open();
    }

    private static GuiElementBuilder speciesButton(ServerPlayer player, ShopSpecies entry, String rank, String filter, int page, AdventurerGuildDataManager.PlayerData data) {
        Price price = priceForRank(entry.rank());
        ItemStack icon = PokemonIconUtil.createPokemonIcon(entry.id(), false, "cobblemon:poke_ball", false);
        if (icon.isEmpty()) icon = new ItemStack(CobblemonItems.POKE_BALL);
        boolean canAfford = data.guildMarks >= price.marks && EconomyManager.getBalance(player) >= EconomyManager.wholeCreditsToCents(price.credits);
        return new GuiElementBuilder(icon)
                .hideDefaultTooltip()
                .setName(Component.literal(AdventurerRankUtil.color(entry.rank()) + entry.displayName()))
                .addLoreLine(Component.literal("§7Shop Rank: §f" + AdventurerRankUtil.displayRank(entry.rank())))
                .addLoreLine(Component.literal("§7Type: §f" + String.join("/", entry.types())))
                .addLoreLine(Component.literal("§7Level: §f1 §8(random IVs)"))
                .addLoreLine(Component.literal("§7Cost: §b" + price.marks + " Adventurer's Marks §7+ §6" + EconomyManager.formatWholeCredits(price.credits)))
                .addLoreLine(Component.literal(canAfford ? "§eClick to purchase" : "§cNot enough Marks or Credits"))
                .setCallback((s,c,a) -> {
                    if (purchase(player, entry, price)) open(player, rank, filter, page);
                });
    }

    private static boolean purchase(ServerPlayer player, ShopSpecies entry, Price price) {
        if (player == null || entry == null || price == null) return false;
        if (!AdventurerGuildManager.hasRank(player, entry.rank())) {
            player.sendSystemMessage(Component.literal("You need " + AdventurerRankUtil.displayRank(entry.rank()) + " to buy that Pokémon.").withStyle(ChatFormatting.RED));
            return false;
        }
        AdventurerGuildDataManager.PlayerData data = AdventurerGuildManager.getData(player);
        if (data.guildMarks < price.marks) {
            player.sendSystemMessage(Component.literal("You need " + price.marks + " Adventurer's Marks for that Pokémon.").withStyle(ChatFormatting.RED));
            return false;
        }
        EconomyManager.TransactionResult credits = EconomyManager.withdraw(player, EconomyManager.wholeCreditsToCents(price.credits), "adventurer_shop_pokemon:" + entry.id());
        if (!credits.success) {
            player.sendSystemMessage(Component.literal(credits.error == null ? "Not enough Credits." : credits.error).withStyle(ChatFormatting.RED));
            return false;
        }
        if (!AdventurerGuildManager.spendGuildMarks(player, price.marks)) {
            EconomyManager.deposit(player, EconomyManager.wholeCreditsToCents(price.credits), "adventurer_shop_refund:" + entry.id());
            player.sendSystemMessage(Component.literal("You no longer have enough Adventurer's Marks.").withStyle(ChatFormatting.RED));
            return false;
        }
        Pokemon pokemon;
        try {
            String species = entry.id().contains(":") ? entry.id() : "cobblemon:" + entry.id();
            pokemon = PokemonProperties.Companion.parse("species=\"" + species + "\" level=1").create();
            try { pokemon.setLevel(1); } catch (Throwable ignored) {}
            try { pokemon.heal(); } catch (Throwable ignored) {}
        } catch (Throwable throwable) {
            AdventurerGuildManager.addGuildMarks(player, price.marks);
            EconomyManager.deposit(player, EconomyManager.wholeCreditsToCents(price.credits), "adventurer_shop_refund_invalid:" + entry.id());
            player.sendSystemMessage(Component.literal("Could not create that Pokémon. No currency was lost.").withStyle(ChatFormatting.RED));
            return false;
        }
        AuctionPokemonSerializer.DeliveryResult delivery = AuctionPokemonSerializer.deliverToPartyOrPc(player, pokemon);
        if (delivery == AuctionPokemonSerializer.DeliveryResult.FAILED) {
            AdventurerGuildManager.addGuildMarks(player, price.marks);
            EconomyManager.deposit(player, EconomyManager.wholeCreditsToCents(price.credits), "adventurer_shop_refund_full:" + entry.id());
            player.sendSystemMessage(Component.literal("Your party and PC appear full. Make space and try again. No currency was lost.").withStyle(ChatFormatting.RED));
            return false;
        }
        TrueCaughtDexManager.markTrueCaught(player, pokemon);
        player.sendSystemMessage(Component.literal("Purchased a level 1 " + entry.displayName() + " from the Adventurer's Guild. Sent to " + delivery.name() + ".").withStyle(ChatFormatting.GREEN));
        return true;
    }

    private static List<ShopSpecies> availableSpecies(String rank, String filter) {
        String safeRank = AdventurerRankUtil.normalizeRank(rank);
        String safeFilter = normalizeType(filter);
        List<ShopSpecies> out = new ArrayList<>();
        for (ShopSpecies entry : allBaseSpecies()) {
            if (!AdventurerRankUtil.normalizeRank(entry.rank()).equals(safeRank)) continue;
            if (!ALL_TYPES.equals(safeFilter) && entry.types().stream().noneMatch(t -> t.equalsIgnoreCase(safeFilter))) continue;
            out.add(entry);
        }
        out.sort(Comparator.comparingInt((ShopSpecies e) -> e.dexNumber() <= 0 ? Integer.MAX_VALUE : e.dexNumber()).thenComparing(ShopSpecies::displayName));
        return out;
    }

    private static List<ShopSpecies> allBaseSpecies() {
        Map<String, ShopSpecies> out = new LinkedHashMap<>();
        try {
            Class<?> pokemonSpeciesClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies");
            Object registry = pokemonSpeciesClass.getField("INSTANCE").get(null);
            for (String methodName : List.of("getImplemented", "getSpecies", "getSpeciesList", "all", "allSpecies")) {
                try {
                    Method method = registry.getClass().getMethod(methodName);
                    Object result = method.invoke(registry);
                    collectSpecies(result, out);
                    if (!out.isEmpty()) break;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        if (out.isEmpty()) addFallbackSpecies(out);
        return new ArrayList<>(out.values());
    }

    private static void collectSpecies(Object result, Map<String, ShopSpecies> out) {
        if (result == null) return;
        if (result instanceof Map<?, ?> map) { for (Object value : map.values()) collectSpecies(value, out); return; }
        if (result instanceof Iterable<?> iterable) { for (Object value : iterable) collectSpecies(value, out); return; }
        if (result.getClass().isArray()) { for (int i = 0; i < Array.getLength(result); i++) collectSpecies(Array.get(result, i), out); return; }
        if (result instanceof Species species) {
            String key = speciesKey(species);
            if (key.isBlank() || out.containsKey(key)) return;
            if (!isBaseShopSpecies(species, key)) return;
            List<String> types = speciesTypes(species);
            out.put(key, new ShopSpecies(dexNumber(species), key, title(key), types, rankFor(key, species, types)));
        }
    }

    private static boolean isBaseShopSpecies(Species species, String key) {
        String k = normalize(key);
        if (isSpecial(k)) return true;
        if (hasPreEvolution(species)) return false;
        return !EVOLVED_FALLBACK.contains(k);
    }

    private static boolean hasPreEvolution(Species species) {
        Object value = firstValue(species, "getPreEvolution", "preEvolution", "getPreEvolutionSpecies", "preEvolutionSpecies", "getPreEvolutions", "preEvolutions");
        if (value == null) return false;
        if (value instanceof Iterable<?> iterable) return iterable.iterator().hasNext();
        if (value.getClass().isArray()) return Array.getLength(value) > 0;
        String text = String.valueOf(value);
        return !text.isBlank() && !"null".equalsIgnoreCase(text) && !"[]".equals(text);
    }

    private static String rankFor(String key, Species species, List<String> types) {
        String k = normalize(key);
        if (LEGENDARY.contains(k) || MYTHICAL.contains(k)) return "S";
        if (ULTRA_BEAST.contains(k) || PARADOX.contains(k)) return "A";
        if (B_RANK.contains(k)) return "B";
        if (C_RANK.contains(k)) return "C";
        if (D_RANK.contains(k)) return "D";
        if (E_RANK.contains(k)) return "E";
        int dex = dexNumber(species);
        if (dex > 0 && dex <= 151) return "D";
        if (types.contains("dragon") || types.contains("steel") || types.contains("ghost")) return "C";
        return "C";
    }

    private static Price priceForRank(String rank) {
        return switch (AdventurerRankUtil.normalizeRank(rank)) {
            case "S" -> new Price(125, 25000);
            case "A" -> new Price(45, 6000);
            case "B" -> new Price(15, 1500);
            case "C" -> new Price(7, 750);
            case "D" -> new Price(3, 300);
            default -> new Price(1, 100);
        };
    }

    private static String speciesKey(Species species) {
        try { return normalize(String.valueOf(species.getResourceIdentifier())); } catch (Throwable ignored) {}
        try { return normalize(String.valueOf(species.getName())); } catch (Throwable ignored) { return ""; }
    }

    private static int dexNumber(Species species) {
        Object value = firstValue(species, "nationalPokedexNumber", "getNationalPokedexNumber", "nationalDexNumber", "getNationalDexNumber", "pokedexNumber", "getPokedexNumber", "dexNumber", "getDexNumber");
        if (value instanceof Number number) return number.intValue();
        if (value != null) try { return Integer.parseInt(String.valueOf(value).replaceAll("[^0-9]", "")); } catch (Throwable ignored) {}
        return 0;
    }

    private static List<String> speciesTypes(Species species) {
        List<String> out = new ArrayList<>();
        Object value = firstValue(species, "getTypes", "types");
        if (value instanceof Iterable<?> iterable) {
            for (Object type : iterable) {
                String name = normalizeType(typeName(type));
                if (!name.isBlank() && !out.contains(name)) out.add(name);
            }
        }
        if (out.isEmpty()) out.add("normal");
        return out;
    }

    private static String typeName(Object type) {
        if (type == null) return "";
        Object name = firstValue(type, "getName", "name", "getShowdownId", "showdownId");
        return name == null ? String.valueOf(type) : String.valueOf(name);
    }

    private static Object firstValue(Object source, String... names) {
        if (source == null) return null;
        for (String name : names) {
            try { Method m = source.getClass().getMethod(name); m.setAccessible(true); if (m.getParameterCount() == 0) return m.invoke(source); } catch (Throwable ignored) {}
            try { java.lang.reflect.Field f = source.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(source); } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean isSpecial(String key) { return LEGENDARY.contains(key) || MYTHICAL.contains(key) || ULTRA_BEAST.contains(key) || PARADOX.contains(key); }
    private static String normalize(String raw) { if (raw == null) return ""; String v = raw.trim().toLowerCase(Locale.ROOT); int colon = v.lastIndexOf(':'); if (colon >= 0 && colon + 1 < v.length()) v = v.substring(colon + 1); return v.replace('-', '_').replace(' ', '_').replaceAll("[^a-z0-9_]", ""); }
    private static String normalizeType(String raw) { String v = normalize(raw); return v.isBlank() ? ALL_TYPES : v; }
    private static String pretty(String raw) { String v = normalize(raw); if (v.equals(ALL_TYPES)) return "All"; return title(v); }
    private static String title(String raw) { String[] parts = normalize(raw).split("_"); StringBuilder b = new StringBuilder(); for (String p : parts) { if (p.isBlank()) continue; if (!b.isEmpty()) b.append(' '); b.append(Character.toUpperCase(p.charAt(0))).append(p.length() > 1 ? p.substring(1) : ""); } return b.isEmpty() ? "Pokemon" : b.toString(); }
    private static Set<String> set(String... values) { Set<String> out = new HashSet<>(); for (String v : values) out.add(normalize(v)); return out; }

    private static net.minecraft.world.item.Item rankIcon(String rank) {
        return switch (AdventurerRankUtil.normalizeRank(rank)) {
            case "S" -> Items.NETHER_STAR;
            case "A" -> Items.TOTEM_OF_UNDYING;
            case "B" -> Items.DIAMOND;
            case "C" -> Items.LAPIS_LAZULI;
            case "D" -> Items.EMERALD;
            default -> Items.IRON_NUGGET;
        };
    }

    private static net.minecraft.world.item.Item typeIcon(String type) {
        return switch (normalizeType(type)) {
            case "fire" -> Items.BLAZE_POWDER;
            case "water" -> Items.WATER_BUCKET;
            case "grass" -> Items.OAK_SAPLING;
            case "electric" -> Items.REDSTONE;
            case "ice" -> Items.ICE;
            case "fighting" -> Items.IRON_SWORD;
            case "poison" -> Items.SPIDER_EYE;
            case "ground" -> Items.DIRT;
            case "flying" -> Items.FEATHER;
            case "psychic" -> Items.AMETHYST_SHARD;
            case "bug" -> Items.HONEYCOMB;
            case "rock" -> Items.STONE;
            case "ghost" -> Items.SOUL_LANTERN;
            case "dragon" -> Items.DRAGON_BREATH;
            case "dark" -> Items.BLACK_DYE;
            case "steel" -> Items.IRON_INGOT;
            case "fairy" -> Items.PINK_DYE;
            default -> CobblemonItems.POKE_BALL;
        };
    }

    private static void addFallbackSpecies(Map<String, ShopSpecies> out) {
        for (String s : E_RANK) out.put(s, new ShopSpecies(0, s, title(s), List.of("normal"), "E"));
        for (String s : D_RANK) out.put(s, new ShopSpecies(0, s, title(s), List.of("normal"), "D"));
        for (String s : C_RANK) out.put(s, new ShopSpecies(0, s, title(s), List.of("normal"), "C"));
        for (String s : B_RANK) out.put(s, new ShopSpecies(0, s, title(s), List.of("normal"), "B"));
        for (String s : ULTRA_BEAST) out.put(s, new ShopSpecies(0, s, title(s), List.of("normal"), "A"));
        for (String s : PARADOX) out.put(s, new ShopSpecies(0, s, title(s), List.of("normal"), "A"));
        for (String s : LEGENDARY) out.put(s, new ShopSpecies(0, s, title(s), List.of("normal"), "S"));
        for (String s : MYTHICAL) out.put(s, new ShopSpecies(0, s, title(s), List.of("normal"), "S"));
    }

    private record ShopSpecies(int dexNumber, String id, String displayName, List<String> types, String rank) {}
    private record Price(long marks, int credits) {}

    private static final Set<String> EVOLVED_FALLBACK = set(
            "raticate","furret","pidgeotto","pidgeot","fearow","arbok","raichu","sandslash","nidorina","nidoqueen","nidorino","nidoking","clefable","ninetales","wigglytuff","golbat","gloom","vileplume","parasect","venomoth","dugtrio","persian","golduck","primeape","arcanine","poliwhirl","poliwrath","kadabra","alakazam","machoke","machamp","weepinbell","victreebel","tentacruel","graveler","golem","rapidash","slowbro","magneton","dodrio","dewgong","muk","cloyster","haunter","gengar","hypno","kingler","electrode","exeggutor","marowak","weezing","rhydon","seadra","seaking","starmie","gyarados","vaporeon","jolteon","flareon","dragonair","dragonite",
            "bayleef","meganium","quilava","typhlosion","croconaw","feraligatr","noctowl","ledian","ariados","crobat","lanturn","togetic","xatu","flaaffy","ampharos","bellossom","azumarill","jumpluff","quagsire","espeon","umbreon","forretress","steelix","granbull","scizor","ursaring","magcargo","piloswine","octillery","houndoom","kingdra","donphan","porygon2","hitmontop","blissey","pupitar","tyranitar",
            "grovyle","sceptile","combusken","blaziken","marshtomp","swampert","mightyena","linoone","silcoon","beautifly","cascoon","dustox","lombre","ludicolo","nuzleaf","shiftry","swellow","pelipper","kirlia","gardevoir","masquerain","breloom","vigoroth","slaking","ninjask","shedinja","loudred","exploud","hariyama","delcatty","mawile","lairon","aggron","medicham","manectric","swalot","sharpedo","wailord","camerupt","grumpig","vibrava","flygon","cacturne","altaria","whiscash","crawdaunt","claydol","cradily","armaldo","milotic","dusclops","banette","sealeo","walrein","huntail","gorebyss","shelgon","salamence","metang","metagross",
            "grotle","torterra","monferno","infernape","prinplup","empoleon","staravia","staraptor","bibarel","kricketune","luxio","luxray","roserade","rampardos","bastiodon","wormadam","mothim","vespiquen","floatzel","cherrim","gastrodon","ambipom","drifblim","lopunny","purugly","skuntank","bronzong","gabite","garchomp","lucario","hippowdon","drapion","toxicroak","lumineon","abomasnow","weavile","magnezone","lickilicky","rhyperior","tangrowth","electivire","magmortar","togekiss","yanmega","leafeon","glaceon","gliscor","mamoswine","porygonz","gallade","probopass","dusknoir","froslass"
    );
}
