package com.champutils.shop;

import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.dex.PokemonOriginManager;
import com.champutils.economy.EconomyManager;
import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolManager;
import com.champutils.crate.CrateCreditManager;
import com.champutils.wondertrade.WonderTradePokemonUtil;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.item.PokemonItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcShopService {

    private static final Random RANDOM = new Random();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File LAST_RARE_ROLL_FILE = new File("config/champutils/shop_pokemon_crate_last_rare_rolls.json");
    private static final Map<UUID, String> LAST_RARE_ROLLS = new ConcurrentHashMap<>();
    private static boolean loadedLastRareRolls = false;

    public enum PokemonCratePool {
        REGULAR,
        LEGENDARY,
        ULTRA_BEAST,
        PARADOX,
        MYTHICAL
    }

    // These sets intentionally include both normal Pokemon naming and compact Cobblemon/modded naming.
    // Example: Great Tusk may resolve as great_tusk or greattusk depending on the species provider.
    private static final Set<String> ULTRA_BEAST_SPECIES = normalizedSpeciesSet(
            "nihilego", "buzzwole", "pheromosa", "xurkitree", "celesteela", "kartana", "guzzlord",
            "poipole", "naganadel", "stakataka", "blacephalon"
    );

    private static final Set<String> PARADOX_SPECIES = normalizedSpeciesSet(
            "great_tusk", "greattusk",
            "scream_tail", "screamtail",
            "brute_bonnet", "brutebonnet",
            "flutter_mane", "fluttermane",
            "slither_wing", "slitherwing",
            "sandy_shocks", "sandyshocks",
            "roaring_moon", "roaringmoon",
            "walking_wake", "walkingwake",
            "gouging_fire", "gougingfire",
            "raging_bolt", "ragingbolt",
            "iron_treads", "irontreads",
            "iron_bundle", "ironbundle",
            "iron_hands", "ironhands",
            "iron_jugulis", "ironjugulis",
            "iron_moth", "ironmoth",
            "iron_thorns", "ironthorns",
            "iron_valiant", "ironvaliant",
            "iron_leaves", "ironleaves",
            "iron_boulder", "ironboulder",
            "iron_crown", "ironcrown"
    );

    private static final Set<String> MYTHICAL_SPECIES = normalizedSpeciesSet(
            "mew", "celebi", "jirachi", "deoxys", "phione", "manaphy", "darkrai", "shaymin", "arceus",
            "victini", "keldeo", "meloetta", "genesect", "diancie", "hoopa", "volcanion", "magearna",
            "marshadow", "zeraora", "meltan", "melmetal", "zarude", "pecharunt"
    );

    private static final Set<String> LEGENDARY_SPECIES = normalizedSpeciesSet(
            "articuno", "zapdos", "moltres", "mewtwo",
            "raikou", "entei", "suicune", "lugia", "ho_oh", "hooh",
            "regirock", "regice", "registeel", "latias", "latios", "kyogre", "groudon", "rayquaza",
            "uxie", "mesprit", "azelf", "dialga", "palkia", "heatran", "regigigas", "giratina", "cresselia",
            "cobalion", "terrakion", "virizion", "tornadus", "thundurus", "reshiram", "zekrom", "landorus", "kyurem",
            "xerneas", "yveltal", "zygarde", "type_null", "typenull", "silvally",
            "tapu_koko", "tapukoko", "tapu_lele", "tapulele", "tapu_bulu", "tapubulu", "tapu_fini", "tapufini",
            "cosmog", "cosmoem", "solgaleo", "lunala", "necrozma",
            "zacian", "zamazenta", "eternatus", "kubfu", "urshifu", "regieleki", "regidrago", "glastrier", "spectrier", "calyrex",
            "enamorus", "wo_chien", "wochien", "chien_pao", "chienpao", "ting_lu", "tinglu", "chi_yu", "chiyu",
            "okidogi", "munkidori", "fezandipiti", "ogerpon", "terapagos", "koraidon", "miraidon"
    );

    private NpcShopService() {
    }

    public static void buy(ServerPlayer player, NpcShopConfig.ShopEntry entry) {
        if (player == null || entry == null) {
            return;
        }

        if ("pokemon_crate".equals(normalize(entry.type)) && ShopPokemonCrateOpeningGui.isOpening(player)) {
            player.sendSystemMessage(Component.literal("Your current Store Pokémon Crate is still opening.").withStyle(ChatFormatting.YELLOW));
            return;
        }

        long price = Math.max(0L, entry.price);
        if (price > 0L) {
            EconomyManager.TransactionResult result = EconomyManager.withdraw(player, price, "NPC shop purchase: " + safeName(entry));
            if (!result.success) {
                player.sendSystemMessage(Component.literal(result.error == null ? "You cannot afford that." : result.error).withStyle(ChatFormatting.RED));
                return;
            }
        }

        boolean success = switch (normalize(entry.type)) {
            case "tool" -> giveTool(player, entry);
            case "pokemon_crate" -> givePokemonCrate(player, entry);
            case "crate_credit", "crate_key" -> giveCrateCredit(player, entry);
            case "command" -> runCommands(player, entry);
            case "item" -> giveItem(player, entry);
            default -> false;
        };

        if (!success) {
            if (price > 0L) {
                EconomyManager.deposit(player, price, "NPC shop refund: " + safeName(entry));
            }
            player.sendSystemMessage(Component.literal("That shop purchase could not be completed. No credits were spent.").withStyle(ChatFormatting.RED));
            return;
        }

        player.sendSystemMessage(Component.literal("Purchased " + stripColor(safeName(entry)) + " for " + EconomyManager.format(price) + ".").withStyle(ChatFormatting.GREEN));
    }

    private static boolean giveItem(ServerPlayer player, NpcShopConfig.ShopEntry entry) {
        Item item = resolveItem(entry.id);
        if (item == Items.AIR) {
            return false;
        }

        int amount = Math.max(1, entry.amount);
        int max = Math.max(1, item.getDefaultMaxStackSize());

        while (amount > 0) {
            int give = Math.min(max, amount);
            giveOrDrop(player, new ItemStack(item, give));
            amount -= give;
        }
        return true;
    }

    private static boolean giveTool(ServerPlayer player, NpcShopConfig.ShopEntry entry) {
        List<String> candidates = findToolCandidates(entry.rarity, entry.toolType);
        if (candidates.isEmpty()) {
            return false;
        }

        String selected = candidates.get(RANDOM.nextInt(candidates.size()));
        ItemStack stack = ProfessionToolManager.createTool(selected, false);
        if (stack.isEmpty()) {
            return false;
        }

        giveOrDrop(player, stack);
        return true;
    }

    private static boolean givePokemonCrate(ServerPlayer player, NpcShopConfig.ShopEntry entry) {
        return ShopPokemonCrateOpeningGui.open(player, entry);
    }

    private static boolean giveCrateCredit(ServerPlayer player, NpcShopConfig.ShopEntry entry) {
        String crateId = normalize(entry.id);
        if (crateId.endsWith("_crate_credit")) {
            crateId = crateId.substring(0, crateId.length() - "_crate_credit".length());
        }
        if (crateId.endsWith("_crate_key")) {
            crateId = crateId.substring(0, crateId.length() - "_crate_key".length());
        }
        if (crateId.isBlank()) {
            return false;
        }

        int amount = Math.max(1, entry.amount);
        CrateCreditManager.addCredits(player, crateId, amount);
        player.sendSystemMessage(Component.literal("Added " + amount + " " + niceSpeciesName(crateId) + " crate credit" + (amount == 1 ? "" : "s") + " to your account.").withStyle(ChatFormatting.GREEN));
        return true;
    }

    public record PlannedPokemonCrateReward(
            String species,
            int level,
            boolean shiny,
            PokemonCratePool pool,
            Component title,
            Component detail,
            ItemStack icon
    ) {
        public boolean special() {
            return shiny || pool != PokemonCratePool.REGULAR;
        }
    }

    public static PlannedPokemonCrateReward planPokemonCrateReward(NpcShopConfig.ShopEntry entry) {
        return planPokemonCrateReward(null, entry);
    }

    public static PlannedPokemonCrateReward planPokemonCrateReward(ServerPlayer player, NpcShopConfig.ShopEntry entry) {
        if (entry == null) return null;

        PlannedPokemonCrateReward selected = null;
        for (int attempts = 0; attempts < 75; attempts++) {
            PlannedPokemonCrateReward candidate = rollSinglePokemonCrateReward(entry);
            if (candidate == null) continue;
            selected = candidate;

            // Only protect special/rare moments from repeating back-to-back.
            // Regular common Pokémon can repeat normally.
            if (player == null || !candidate.special() || !isSameAsLastRareRoll(player.getUUID(), candidate)) {
                break;
            }
        }

        return selected;
    }

    private static PlannedPokemonCrateReward rollSinglePokemonCrateReward(NpcShopConfig.ShopEntry entry) {
        PokemonCratePool pool = rollPokemonCratePool(entry);
        boolean shiny = pool == PokemonCratePool.REGULAR && rollPercent(entry.shinyChance);
        int minLevel = Math.max(1, entry.minLevel);
        int maxLevel = Math.max(minLevel, entry.maxLevel);
        int level = minLevel + RANDOM.nextInt((maxLevel - minLevel) + 1);

        String species = pickRandomSpecies(pool);
        if (species == null || species.isBlank()) return null;

        return restorePlannedPokemonCrateReward(species, level, shiny, pool);
    }


    public static PlannedPokemonCrateReward randomDisplayPokemonCrateReward(PlannedPokemonCrateReward finalReward) {
        for (int attempts = 0; attempts < 25; attempts++) {
            PokemonCratePool pool = rollDisplayPool();
            boolean shiny = pool == PokemonCratePool.REGULAR && RANDOM.nextDouble() < 0.08D;
            int level = 50 + RANDOM.nextInt(51);
            String species = pickRandomSpecies(pool);

            if (species == null || species.isBlank()) {
                species = fallbackRegularSpecies().get(RANDOM.nextInt(fallbackRegularSpecies().size()));
                pool = PokemonCratePool.REGULAR;
                shiny = false;
            }

            PlannedPokemonCrateReward reward = restorePlannedPokemonCrateReward(species, level, shiny, pool);
            if (reward != null && !samePlannedReward(reward, finalReward)) {
                return reward;
            }
        }

        return restorePlannedPokemonCrateReward("cobblemon:eevee", 50, false, PokemonCratePool.REGULAR);
    }

    private static PokemonCratePool rollDisplayPool() {
        double roll = RANDOM.nextDouble() * 100.0D;
        if (roll < 2.0D) return PokemonCratePool.LEGENDARY;
        if (roll < 5.0D) return PokemonCratePool.ULTRA_BEAST;
        if (roll < 8.0D) return PokemonCratePool.PARADOX;
        return PokemonCratePool.REGULAR;
    }

    private static boolean samePlannedReward(PlannedPokemonCrateReward a, PlannedPokemonCrateReward b) {
        if (a == null || b == null) return false;
        if (a.shiny() != b.shiny()) return false;
        if (a.pool() != b.pool()) return false;
        if (a.species() == null || b.species() == null) return false;
        return speciesKey(a.species()).equals(speciesKey(b.species()));
    }

    public static PlannedPokemonCrateReward restorePlannedPokemonCrateReward(String species, int level, boolean shiny, PokemonCratePool pool) {
        if (species == null || species.isBlank()) return null;
        if (pool == null) pool = PokemonCratePool.REGULAR;

        String resolvedSpecies = resolveRealSpeciesId(species);
        if (resolvedSpecies == null || resolvedSpecies.isBlank()) {
            return null;
        }

        String niceSpecies = niceSpeciesName(resolvedSpecies);
        ChatFormatting color = shiny ? ChatFormatting.GOLD : poolColor(pool);
        Component title = Component.literal((shiny ? "Shiny " : "") + niceSpecies).withStyle(color);
        Component detail = Component.literal("Level " + Math.max(1, level) + " • " + poolLabel(pool)).withStyle(ChatFormatting.GRAY);
        ItemStack icon;
        try {
            Pokemon iconPokemon = PokemonProperties.Companion.parse("species=\"" + resolvedSpecies + "\" level=" + Math.max(1, level)).create();
            if (!speciesKey(iconPokemon.getSpecies().getResourceIdentifier().toString()).equals(speciesKey(resolvedSpecies))) {
                return null;
            }
            setBooleanProperty(iconPokemon, "setShiny", shiny);
            icon = PokemonItem.from(iconPokemon, 1);
        } catch (Throwable ignored) {
            icon = new ItemStack(shiny ? Items.NETHER_STAR : pool == PokemonCratePool.REGULAR ? Items.EGG : Items.DRAGON_EGG);
        }

        return new PlannedPokemonCrateReward(resolvedSpecies, Math.max(1, level), shiny, pool, title, detail, icon);
    }

    public static boolean grantDexPokemonReward(ServerPlayer player, PokemonCratePool pool, boolean shiny, int level) {
        if (player == null) return false;
        if (pool == null) pool = PokemonCratePool.REGULAR;

        String species = pickRandomSpecies(pool);
        if (species == null || species.isBlank()) {
            return false;
        }

        PlannedPokemonCrateReward reward = restorePlannedPokemonCrateReward(species, Math.max(1, level), shiny, pool);
        if (reward == null) {
            return false;
        }

        return grantPlannedPokemonCrateReward(player, reward);
    }

    public static boolean grantPlannedPokemonCrateReward(ServerPlayer player, PlannedPokemonCrateReward plan) {
        return grantPlannedPokemonCrateReward(player, plan, "Store Pokémon Crate", true);
    }

    public static boolean grantPlannedPokemonCrateReward(ServerPlayer player, PlannedPokemonCrateReward plan, String sourceLabel, boolean broadcastSpecial) {
        if (player == null || plan == null || plan.species == null || plan.species.isBlank()) {
            return false;
        }

        String resolvedSpecies = resolveRealSpeciesId(plan.species);
        if (resolvedSpecies == null || resolvedSpecies.isBlank()) {
            return false;
        }

        Pokemon pokemon;
        try {
            pokemon = PokemonProperties.Companion.parse("species=\"" + resolvedSpecies + "\" level=" + plan.level).create();
            if (!speciesKey(pokemon.getSpecies().getResourceIdentifier().toString()).equals(speciesKey(resolvedSpecies))) {
                return false;
            }
        } catch (Throwable throwable) {
            return false;
        }

        setBooleanProperty(pokemon, "setShiny", plan.shiny);
        setIntProperty(pokemon, "setLevel", plan.level);
        PokemonOriginManager.markOrigin(pokemon, PokemonOriginManager.ORIGIN_CRATE);

        boolean sentToPc = false;
        if (!AuctionPokemonSerializer.addToFirstOpenPartySlot(player, pokemon)) {
            if (!AuctionPokemonSerializer.addToPc(player, pokemon)) {
                player.sendSystemMessage(Component.literal("Could not access your party or PC. Please contact staff.").withStyle(ChatFormatting.RED));
                return false;
            }
            sentToPc = true;
        }

        String displayName;
        try {
            displayName = pokemon.getDisplayName(true).getString();
        } catch (Throwable ignored) {
            displayName = niceSpeciesName(plan.species);
        }

        if (sourceLabel != null && !sourceLabel.isBlank()) {
            player.sendSystemMessage(Component.literal("Your " + sourceLabel + " opened into ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(displayName).withStyle(plan.shiny ? ChatFormatting.GOLD : poolColor(plan.pool)))
                    .append(Component.literal(sentToPc ? "! It was sent to your PC." : "!").withStyle(ChatFormatting.GOLD)));
        } else if (sentToPc) {
            player.sendSystemMessage(Component.literal("Your party was full. ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(displayName).withStyle(plan.shiny ? ChatFormatting.GOLD : poolColor(plan.pool)))
                    .append(Component.literal(" was sent to your PC.").withStyle(ChatFormatting.GRAY)));
        }

        if (plan.special()) {
            playSpecialPokemonSound(player, plan.pool, plan.shiny);
            rememberLastRareRoll(player.getUUID(), plan);

            if (broadcastSpecial) {
                String rarityText = plan.shiny ? "a shiny" : "a " + poolLabel(plan.pool);
                String label = sourceLabel == null || sourceLabel.isBlank() ? "a crate" : sourceLabel;
                player.server.getPlayerList().broadcastSystemMessage(
                        Component.literal(player.getName().getString() + " opened " + rarityText + " Pokémon from " + label + ": " + displayName + "!").withStyle(ChatFormatting.GOLD),
                        false
                );
            }
        }
        return true;
    }

    private static boolean isSameAsLastRareRoll(UUID playerId, PlannedPokemonCrateReward candidate) {
        if (playerId == null || candidate == null || !candidate.special()) return false;
        loadLastRareRolls();
        String previous = LAST_RARE_ROLLS.get(playerId);
        return previous != null && previous.equals(rareRollKey(candidate));
    }

    private static void rememberLastRareRoll(UUID playerId, PlannedPokemonCrateReward reward) {
        if (playerId == null || reward == null || !reward.special()) return;
        loadLastRareRolls();
        LAST_RARE_ROLLS.put(playerId, rareRollKey(reward));
        saveLastRareRolls();
    }

    private static String rareRollKey(PlannedPokemonCrateReward reward) {
        if (reward == null) return "";
        String rarity = reward.shiny() ? "SHINY" : String.valueOf(reward.pool());
        return rarity + ":" + speciesKey(reward.species());
    }

    private static synchronized void loadLastRareRolls() {
        if (loadedLastRareRolls) return;
        loadedLastRareRolls = true;
        LAST_RARE_ROLLS.clear();
        if (!LAST_RARE_ROLL_FILE.exists()) return;

        try (FileReader reader = new FileReader(LAST_RARE_ROLL_FILE)) {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = GSON.fromJson(reader, type);
            if (loaded == null) return;
            for (Map.Entry<String, String> entry : loaded.entrySet()) {
                try {
                    if (entry.getValue() != null && !entry.getValue().isBlank()) {
                        LAST_RARE_ROLLS.put(UUID.fromString(entry.getKey()), entry.getValue());
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Exception exception) {
            System.err.println("[ChampUtils] Failed to load shop Pokémon crate last rare rolls.");
            exception.printStackTrace();
        }
    }

    private static synchronized void saveLastRareRolls() {
        try {
            File parent = LAST_RARE_ROLL_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            Map<String, String> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<UUID, String> entry : LAST_RARE_ROLLS.entrySet()) {
                out.put(entry.getKey().toString(), entry.getValue());
            }

            try (FileWriter writer = new FileWriter(LAST_RARE_ROLL_FILE)) {
                GSON.toJson(out, writer);
            }
        } catch (Exception exception) {
            System.err.println("[ChampUtils] Failed to save shop Pokémon crate last rare rolls.");
            exception.printStackTrace();
        }
    }

    private static boolean rollPercent(double chancePercent) {
        if (chancePercent <= 0.0D) return false;
        return RANDOM.nextDouble() * 100.0D < chancePercent;
    }

    private static PokemonCratePool rollPokemonCratePool(NpcShopConfig.ShopEntry entry) {
        double roll = RANDOM.nextDouble() * 100.0D;
        double legendaryChance = Math.max(0.0D, entry.legendaryChance);
        double ultraBeastChance = Math.max(0.0D, entry.ultraBeastChance);
        double paradoxChance = Math.max(0.0D, entry.paradoxChance);

        if (roll < legendaryChance) return PokemonCratePool.LEGENDARY;
        roll -= legendaryChance;
        if (roll < ultraBeastChance) return PokemonCratePool.ULTRA_BEAST;
        roll -= ultraBeastChance;
        if (roll < paradoxChance) return PokemonCratePool.PARADOX;
        return PokemonCratePool.REGULAR;
    }

    private static String pickRandomSpecies(PokemonCratePool pool) {
        List<String> species = loadSpeciesPool(pool);
        if (species.isEmpty()) {
            species = switch (pool) {
                case LEGENDARY -> fallbackLegendarySpecies();
                case ULTRA_BEAST -> fallbackUltraBeastSpecies();
                case PARADOX -> fallbackParadoxSpecies();
                default -> fallbackRegularSpecies();
            };
        }
        if (species.isEmpty()) {
            return null;
        }
        return species.get(RANDOM.nextInt(species.size()));
    }

    private static List<String> loadSpeciesPool(PokemonCratePool pool) {
        Set<String> result = new LinkedHashSet<>();
        try {
            for (Species species : reflectAllSpecies()) {
                if (species == null) continue;
                String id = speciesId(species);
                if (id == null || id.isBlank()) continue;
                if (pool == classifySpeciesForCrate(id)) {
                    result.add(id);
                }
            }
        } catch (Throwable ignored) {
        }
        return new ArrayList<>(result);
    }

    private static PokemonCratePool classifySpeciesForCrate(String rawSpecies) {
        String species = speciesKey(rawSpecies);
        String compact = compactSpeciesKey(rawSpecies);

        if (ULTRA_BEAST_SPECIES.contains(species) || ULTRA_BEAST_SPECIES.contains(compact)) return PokemonCratePool.ULTRA_BEAST;
        if (MYTHICAL_SPECIES.contains(species) || MYTHICAL_SPECIES.contains(compact)) return PokemonCratePool.MYTHICAL;
        if (LEGENDARY_SPECIES.contains(species) || LEGENDARY_SPECIES.contains(compact) || WonderTradePokemonUtil.isLegendarySpecies(species)) return PokemonCratePool.LEGENDARY;
        if (PARADOX_SPECIES.contains(species) || PARADOX_SPECIES.contains(compact)) return PokemonCratePool.PARADOX;
        return PokemonCratePool.REGULAR;
    }


    private static String resolveRealSpeciesId(String rawSpecies) {
        Species species = findSpeciesByAnyName(rawSpecies);
        if (species == null) return null;
        try {
            return species.getResourceIdentifier().toString();
        } catch (Throwable ignored) {
        }
        try {
            String name = species.getName();
            if (name != null && !name.isBlank()) return "cobblemon:" + speciesKey(name);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Species findSpeciesByAnyName(String rawSpecies) {
        if (rawSpecies == null || rawSpecies.isBlank()) return null;

        String cleaned = rawSpecies.trim().toLowerCase(Locale.ROOT);
        String noNamespace = cleaned;
        int colon = noNamespace.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < noNamespace.length()) noNamespace = noNamespace.substring(colon + 1);

        List<String> candidates = new ArrayList<>();
        candidates.add(cleaned);
        candidates.add(noNamespace);
        candidates.add(noNamespace.replace('_', '-'));
        candidates.add(noNamespace.replace('-', '_'));
        candidates.add(compactSpeciesKey(noNamespace));

        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            try {
                String idPath = candidate.contains(":") ? candidate : "cobblemon:" + candidate;
                Species byId = PokemonSpecies.getByIdentifier(ResourceLocation.parse(idPath));
                if (byId != null) return byId;
            } catch (Throwable ignored) {
            }
            try {
                Species byName = PokemonSpecies.getByName(candidate);
                if (byName != null) return byName;
            } catch (Throwable ignored) {
            }
        }

        String targetKey = speciesKey(noNamespace);
        String targetCompact = compactSpeciesKey(noNamespace);
        for (Species candidate : reflectAllSpecies()) {
            if (candidate == null) continue;
            try {
                String id = candidate.getResourceIdentifier().toString();
                if (speciesKey(id).equals(targetKey) || compactSpeciesKey(id).equals(targetCompact)) return candidate;
            } catch (Throwable ignored) {
            }
            try {
                String name = candidate.getName();
                if (speciesKey(name).equals(targetKey) || compactSpeciesKey(name).equals(targetCompact)) return candidate;
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static Set<String> normalizedSpeciesSet(String... values) {
        Set<String> set = new LinkedHashSet<>();
        if (values == null) return set;
        for (String value : values) {
            set.add(speciesKey(value));
            set.add(compactSpeciesKey(value));
        }
        return set;
    }

    private static String speciesKey(String rawSpecies) {
        if (rawSpecies == null) return "";
        String species = rawSpecies.toLowerCase(Locale.ROOT).trim();
        int colon = species.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < species.length()) species = species.substring(colon + 1);
        species = species.replace('-', '_').replace(' ', '_').replace('.', '_');
        while (species.contains("__")) species = species.replace("__", "_");
        return species;
    }

    private static String compactSpeciesKey(String rawSpecies) {
        return speciesKey(rawSpecies).replace("_", "");
    }

    private static List<Species> reflectAllSpecies() {
        List<Species> result = new ArrayList<>();
        Object instance = null;
        try {
            Field field = PokemonSpecies.class.getField("INSTANCE");
            instance = field.get(null);
        } catch (Throwable ignored) {
        }

        for (Method method : PokemonSpecies.class.getMethods()) {
            if (method.getParameterCount() != 0) continue;
            Object target = Modifier.isStatic(method.getModifiers()) ? null : instance;
            if (target == null && !Modifier.isStatic(method.getModifiers())) continue;

            try {
                Object value = method.invoke(target);
                collectSpecies(value, result);
            } catch (Throwable ignored) {
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void collectSpecies(Object value, List<Species> out) {
        if (value == null) return;
        if (value instanceof Species species) {
            out.add(species);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object entryValue : map.values()) {
                collectSpecies(entryValue, out);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                collectSpecies(element, out);
            }
        }
    }

    private static String speciesId(Species species) {
        try {
            return String.valueOf(species.getResourceIdentifier()).toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
        }
        try {
            return "cobblemon:" + String.valueOf(species.getName()).toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean addPokemonToPc(ServerPlayer player, Pokemon pokemon) {
        if (player == null || pokemon == null) return false;
        try {
            Object storage = Cobblemon.INSTANCE.getStorage();
            Object pc = null;
            for (String methodName : new String[] { "getPC", "getPc", "getPCStore", "getPcStore" }) {
                for (Method method : storage.getClass().getMethods()) {
                    if (!method.getName().equals(methodName)) continue;
                    if (method.getParameterCount() != 1) continue;
                    try {
                        Class<?> param = method.getParameterTypes()[0];
                        Object arg = param.isAssignableFrom(ServerPlayer.class) ? player : player.getUUID();
                        pc = method.invoke(storage, arg);
                        if (pc != null) break;
                    } catch (Throwable ignored) {}
                }
                if (pc != null) break;
            }
            if (pc == null) return false;
            for (Method method : pc.getClass().getMethods()) {
                if (!method.getName().equals("add")) continue;
                if (method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(Pokemon.class)) continue;
                Object result = method.invoke(pc, pokemon);
                return !(result instanceof Boolean) || (Boolean) result;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void playSpecialPokemonSound(ServerPlayer player, PokemonCratePool pool, boolean shiny) {
        try {
            float pitch = shiny ? 1.85F : pool == PokemonCratePool.PARADOX ? 1.55F : pool == PokemonCratePool.ULTRA_BEAST ? 1.25F : 1.0F;
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, pitch);
        } catch (Throwable ignored) {
        }
    }

    private static void setIntProperty(Pokemon pokemon, String methodName, int value) {
        try {
            Method method = pokemon.getClass().getMethod(methodName, int.class);
            method.invoke(pokemon, value);
        } catch (Exception ignored) {}
    }

    private static void setBooleanProperty(Pokemon pokemon, String methodName, boolean value) {
        try {
            Method method = pokemon.getClass().getMethod(methodName, boolean.class);
            method.invoke(pokemon, value);
        } catch (Exception ignored) {}
    }

    public static Component cratePoolTitle(PokemonCratePool pool, boolean shiny) {
        return Component.literal(shiny ? "Shiny Pokémon" : poolLabel(pool)).withStyle(shiny ? ChatFormatting.GOLD : poolColor(pool));
    }

    private static ChatFormatting poolColor(PokemonCratePool pool) {
        return switch (pool) {
            case LEGENDARY -> ChatFormatting.LIGHT_PURPLE;
            case ULTRA_BEAST -> ChatFormatting.DARK_PURPLE;
            case PARADOX -> ChatFormatting.BLUE;
            case MYTHICAL -> ChatFormatting.RED;
            default -> ChatFormatting.AQUA;
        };
    }

    private static String poolLabel(PokemonCratePool pool) {
        return switch (pool) {
            case LEGENDARY -> "Legendary";
            case ULTRA_BEAST -> "Ultra Beast";
            case PARADOX -> "Paradox";
            case MYTHICAL -> "Mythical";
            default -> "Regular";
        };
    }

    private static String niceSpeciesName(String speciesId) {
        String key = speciesKey(speciesId);
        if (key.isBlank()) return "Pokémon";
        String[] words = key.split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private static List<String> fallbackRegularSpecies() {
        return List.of(
                "cobblemon:bulbasaur", "cobblemon:charmander", "cobblemon:squirtle", "cobblemon:pikachu", "cobblemon:eevee",
                "cobblemon:chikorita", "cobblemon:cyndaquil", "cobblemon:totodile", "cobblemon:treecko", "cobblemon:torchic",
                "cobblemon:mudkip", "cobblemon:turtwig", "cobblemon:chimchar", "cobblemon:piplup", "cobblemon:snivy",
                "cobblemon:tepig", "cobblemon:oshawott", "cobblemon:chespin", "cobblemon:fennekin", "cobblemon:froakie",
                "cobblemon:rowlet", "cobblemon:litten", "cobblemon:popplio", "cobblemon:grookey", "cobblemon:scorbunny",
                "cobblemon:sobble", "cobblemon:sprigatito", "cobblemon:fuecoco", "cobblemon:quaxly"
        );
    }

    private static List<String> fallbackLegendarySpecies() {
        return List.of(
                "cobblemon:articuno", "cobblemon:zapdos", "cobblemon:moltres", "cobblemon:mewtwo", "cobblemon:raikou",
                "cobblemon:entei", "cobblemon:suicune", "cobblemon:lugia", "cobblemon:ho_oh", "cobblemon:latias",
                "cobblemon:latios", "cobblemon:kyogre", "cobblemon:groudon", "cobblemon:rayquaza", "cobblemon:dialga",
                "cobblemon:palkia", "cobblemon:giratina", "cobblemon:reshiram", "cobblemon:zekrom", "cobblemon:kyurem",
                "cobblemon:xerneas", "cobblemon:yveltal", "cobblemon:zygarde", "cobblemon:zacian", "cobblemon:zamazenta",
                "cobblemon:koraidon", "cobblemon:miraidon"
        );
    }

    private static List<String> fallbackUltraBeastSpecies() {
        return List.of(
                "cobblemon:nihilego", "cobblemon:buzzwole", "cobblemon:pheromosa", "cobblemon:xurkitree",
                "cobblemon:celesteela", "cobblemon:kartana", "cobblemon:guzzlord", "cobblemon:poipole",
                "cobblemon:naganadel", "cobblemon:stakataka", "cobblemon:blacephalon"
        );
    }

    private static List<String> fallbackParadoxSpecies() {
        return List.of(
                "cobblemon:great_tusk", "cobblemon:scream_tail", "cobblemon:brute_bonnet", "cobblemon:flutter_mane",
                "cobblemon:slither_wing", "cobblemon:sandy_shocks", "cobblemon:roaring_moon", "cobblemon:walking_wake",
                "cobblemon:gouging_fire", "cobblemon:raging_bolt", "cobblemon:iron_treads", "cobblemon:iron_bundle",
                "cobblemon:iron_hands", "cobblemon:iron_jugulis", "cobblemon:iron_moth", "cobblemon:iron_thorns",
                "cobblemon:iron_valiant", "cobblemon:iron_leaves", "cobblemon:iron_boulder", "cobblemon:iron_crown"
        );
    }

    private static boolean runCommands(ServerPlayer player, NpcShopConfig.ShopEntry entry) {
        if (player.getServer() == null || entry.commands == null || entry.commands.isEmpty()) {
            return false;
        }

        for (String raw : entry.commands) {
            if (raw == null || raw.isBlank()) {
                continue;
            }

            String command = raw.replace("%player%", player.getName().getString());
            player.getServer().getCommands().performPrefixedCommand(
                    player.getServer().createCommandSourceStack(),
                    command
            );
        }

        return true;
    }

    public static List<String> findToolCandidates(String rarity, String toolType) {
        String wantedRarity = normalize(rarity);
        String wantedType = normalize(toolType);
        List<String> candidates = new ArrayList<>();

        for (Map.Entry<String, ProfessionToolConfig.ToolData> mapEntry : ProfessionToolConfig.TOOLS.entrySet()) {
            ProfessionToolConfig.ToolData data = mapEntry.getValue();
            if (data == null) continue;
            if (!normalize(data.rarity).equals(wantedRarity)) continue;

            String base = data.baseItem == null ? "" : data.baseItem.toLowerCase(Locale.ROOT);
            if (base.contains(wantedType)) {
                candidates.add(mapEntry.getKey());
            }
        }

        return candidates;
    }

    public static Item resolveItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Items.AIR;
        }

        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId.trim()));
            return item == null ? Items.AIR : item;
        } catch (Exception exception) {
            return Items.AIR;
        }
    }

    public static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }

        boolean added = player.getInventory().add(stack);
        if (!added) {
            player.drop(stack, false);
        }
    }

    private static String safeName(NpcShopConfig.ShopEntry entry) {
        if (entry == null || entry.displayName == null || entry.displayName.isBlank()) {
            return "shop item";
        }
        return entry.displayName;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String stripColor(String value) {
        return value == null ? "" : value.replaceAll("§.", "");
    }
}
