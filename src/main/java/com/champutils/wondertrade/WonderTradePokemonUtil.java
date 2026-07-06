package com.champutils.wondertrade;

import com.champutils.auction.AuctionPokemonSerializer;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public final class WonderTradePokemonUtil {

    private static final Random RANDOM = new Random();

    private static final List<String> SEED_SPECIES = List.of(
            "cobblemon:bulbasaur", "cobblemon:charmander", "cobblemon:squirtle", "cobblemon:caterpie", "cobblemon:weedle",
            "cobblemon:pidgey", "cobblemon:rattata", "cobblemon:spearow", "cobblemon:ekans", "cobblemon:pikachu",
            "cobblemon:sandshrew", "cobblemon:nidoran_f", "cobblemon:nidoran_m", "cobblemon:vulpix", "cobblemon:jigglypuff",
            "cobblemon:zubat", "cobblemon:oddish", "cobblemon:paras", "cobblemon:venonat", "cobblemon:diglett",
            "cobblemon:meowth", "cobblemon:psyduck", "cobblemon:mankey", "cobblemon:growlithe", "cobblemon:poliwag",
            "cobblemon:abra", "cobblemon:machop", "cobblemon:bellsprout", "cobblemon:tentacool", "cobblemon:geodude",
            "cobblemon:ponyta", "cobblemon:slowpoke", "cobblemon:magnemite", "cobblemon:doduo", "cobblemon:seel",
            "cobblemon:grimer", "cobblemon:shellder", "cobblemon:gastly", "cobblemon:onix", "cobblemon:drowzee",
            "cobblemon:krabby", "cobblemon:voltorb", "cobblemon:exeggcute", "cobblemon:cubone", "cobblemon:lickitung",
            "cobblemon:koffing", "cobblemon:rhyhorn", "cobblemon:tangela", "cobblemon:horsea", "cobblemon:goldeen",
            "cobblemon:staryu", "cobblemon:scyther", "cobblemon:pinsir", "cobblemon:magikarp", "cobblemon:eevee",
            "cobblemon:chikorita", "cobblemon:cyndaquil", "cobblemon:totodile", "cobblemon:sentret", "cobblemon:hoothoot",
            "cobblemon:ledyba", "cobblemon:spinarak", "cobblemon:chinchou", "cobblemon:pichu", "cobblemon:cleffa",
            "cobblemon:igglybuff", "cobblemon:togepi", "cobblemon:natu", "cobblemon:mareep", "cobblemon:marill",
            "cobblemon:sudowoodo", "cobblemon:hoppip", "cobblemon:aipom", "cobblemon:sunkern", "cobblemon:yanma",
            "cobblemon:wooper", "cobblemon:murkrow", "cobblemon:misdreavus", "cobblemon:pineco", "cobblemon:dunsparce",
            "cobblemon:gligar", "cobblemon:snubbull", "cobblemon:qwilfish", "cobblemon:shuckle", "cobblemon:heracross",
            "cobblemon:sneasel", "cobblemon:teddiursa", "cobblemon:slugma", "cobblemon:swinub", "cobblemon:corsola",
            "cobblemon:remoraid", "cobblemon:delibird", "cobblemon:mantine", "cobblemon:skarmory", "cobblemon:houndour",
            "cobblemon:phanpy", "cobblemon:stantler", "cobblemon:smeargle", "cobblemon:tyrogue", "cobblemon:larvitar"
    );

    private static final Set<String> LEGENDARY_SPECIES = Set.of(
            "articuno", "zapdos", "moltres", "mewtwo", "mew", "raikou", "entei", "suicune", "lugia", "ho_oh", "celebi",
            "regirock", "regice", "registeel", "latias", "latios", "kyogre", "groudon", "rayquaza", "jirachi", "deoxys",
            "uxie", "mesprit", "azelf", "dialga", "palkia", "heatran", "regigigas", "giratina", "cresselia", "phione", "manaphy", "darkrai", "shaymin", "arceus",
            "victini", "cobalion", "terrakion", "virizion", "tornadus", "thundurus", "reshiram", "zekrom", "landorus", "kyurem", "keldeo", "meloetta", "genesect",
            "xerneas", "yveltal", "zygarde", "diancie", "hoopa", "volcanion", "tapu_koko", "tapu_lele", "tapu_bulu", "tapu_fini", "cosmog", "cosmoem", "solgaleo", "lunala", "nihilego", "buzzwole", "pheromosa", "xurkitree", "celesteela", "kartana", "guzzlord", "necrozma", "magearna", "marshadow", "zeraora", "meltan", "melmetal",
            "zacian", "zamazenta", "eternatus", "kubfu", "urshifu", "zarude", "regieleki", "regidrago", "glastrier", "spectrier", "calyrex",
            "enamorus", "wo_chien", "chien_pao", "ting_lu", "chi_yu", "koraidon", "miraidon", "walking_wake", "iron_leaves", "ogerpon", "terapagos", "pecharunt"
    );

    private WonderTradePokemonUtil() {}

    public static Pokemon createRandomSeedPokemon(boolean shiny) {
        String species = SEED_SPECIES.get(RANDOM.nextInt(SEED_SPECIES.size()));
        Pokemon pokemon = PokemonProperties.Companion.parse("species=\"" + species + "\"").create();
        setIntProperty(pokemon, "setLevel", 5 + RANDOM.nextInt(41));
        setBooleanProperty(pokemon, "setShiny", shiny);
        return pokemon;
    }

    public static JsonObject toPayload(ServerPlayer player, Pokemon pokemon) {
        JsonObject payload = AuctionPokemonSerializer.toPayload(player, pokemon);
        String species = payload.has("species") ? payload.get("species").getAsString() : "unknown";
        payload.addProperty("a", isLegendarySpecies(species));
        payload.addProperty("wondertrade", true);
        return payload;
    }

    public static boolean isLegendary(Pokemon pokemon) {
        if (pokemon == null) return false;
        try {
            String species = String.valueOf(pokemon.getSpecies().getResourceIdentifier());
            return isLegendarySpecies(species);
        } catch (Exception ignored) {}
        try {
            String species = String.valueOf(pokemon.getSpecies().getName());
            return isLegendarySpecies(species);
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean isLegendarySpecies(String rawSpecies) {
        if (rawSpecies == null) return false;
        String species = rawSpecies.toLowerCase(Locale.ROOT).trim();
        int colon = species.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < species.length()) species = species.substring(colon + 1);
        species = species.replace('-', '_').replace(' ', '_');
        return LEGENDARY_SPECIES.contains(species);
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
}
