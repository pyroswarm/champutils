package com.champutils.cosmetic;

import com.champutils.emblem.EmblemManager;
import com.champutils.hunt.PokemonHuntReflection;
import com.champutils.util.CobblemonEventReflection;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Awards the config-backed quirky catch titles from one shared Cobblemon capture subscription. */
public final class QuirkyCatchTitleListener {
    private static boolean registered;
    private static final Set<String> MYTHICALS = set("mew","celebi","jirachi","deoxys","phione","manaphy","darkrai","shaymin","arceus","victini","keldeo","meloetta","genesect","diancie","hoopa","volcanion","magearna","marshadow","zeraora","meltan","melmetal","zarude","pecharunt");
    private static final Set<String> STARTERS = set("bulbasaur","charmander","squirtle","chikorita","cyndaquil","totodile","treecko","torchic","mudkip","turtwig","chimchar","piplup","snivy","tepig","oshawott","chespin","fennekin","froakie","rowlet","litten","popplio","grookey","scorbunny","sobble","sprigatito","fuecoco","quaxly");
    private static final Set<String> PIKACHU_LINE = set("pichu","pikachu","raichu");
    private static final Set<String> EEVEE_LINE = set("eevee","vaporeon","jolteon","flareon","espeon","umbreon","leafeon","glaceon","sylveon");
    private static final Set<String> PSEUDOS = set("dratini","dragonair","dragonite","larvitar","pupitar","tyranitar","bagon","shelgon","salamence","beldum","metang","metagross","gible","gabite","garchomp","deino","zweilous","hydreigon","goomy","sliggoo","goodra","jangmoo","jangmo_o","hakamoo","hakamo_o","kommoo","kommo_o","dreepy","drakloak","dragapult","frigibax","arctibax","baxcalibur");
    private static final Set<String> FOSSILS = set("omanyte","omastar","kabuto","kabutops","aerodactyl","lileep","cradily","anorith","armaldo","cranidos","rampardos","shieldon","bastiodon","tirtouga","carracosta","archen","archeops","tyrunt","tyrantrum","amaura","aurorus","dracozolt","arctozolt","dracovish","arctovish");
    private static final Set<String> BABIES = set("pichu","cleffa","igglybuff","togepi","tyrogue","smoochum","elekid","magby","azurill","wynaut","budew","chingling","bonsly","mimejr","mime_jr","happiny","munchlax","riolu","mantyke","toxel");
    private static final Set<String> ICONIC = set("pikachu","charizard","eevee","lucario","gengar","snorlax","greninja","mimikyu","gardevoir","garchomp","dragonite","gyarados","lapras","ditto","magikarp","psyduck","meowth","jigglypuff","slowpoke","bidoof");

    private QuirkyCatchTitleListener() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Object observable = captureObservable(eventsClass);
            if (observable == null) return;
            CobblemonEventReflection.subscribe(observable, event -> {
                try {
                    ServerPlayer player = PokemonHuntReflection.extractPlayer(event);
                    Object pokemon = PokemonHuntReflection.extractPokemon(event);
                    if (player != null && pokemon != null) evaluate(player, pokemon);
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private static void evaluate(ServerPlayer player, Object pokemon) {
        String species = speciesId(pokemon);
        int level = number(read(pokemon, "getLevel"));
        boolean shiny = bool(read(pokemon, "getShiny")) || bool(read(pokemon, "isShiny"));
        List<Integer> ivs = ivValues(pokemon);
        long ones = ivs.stream().filter(v -> v == 1).count();
        long zeros = ivs.stream().filter(v -> v == 0).count();
        long perfect = ivs.stream().filter(v -> v == 31).count();
        int total = ivs.stream().mapToInt(Integer::intValue).sum();

        unlock(player, "quirky_first_catch");
        if (ones >= 3) unlock(player, "quirky_unlucky");
        if (zeros >= 3) unlock(player, "quirky_cursed_stats");
        if (zeros == 6) unlock(player, "quirky_zero_hero");
        if (perfect >= 3) unlock(player, "quirky_genetic_lottery");
        if (perfect == 6) unlock(player, "quirky_flawless_find");
        if (ivs.size() >= 6 && total <= 30) unlock(player, "quirky_built_different");
        if (ivs.size() >= 6 && total >= 180) unlock(player, "quirky_statistically_blessed");
        if (level == 1) unlock(player, "quirky_fresh_out_the_ball");
        if (level >= 100) unlock(player, "quirky_apex_capture");
        if (shiny) unlock(player, "quirky_sparkle_spotted");
        if (shiny && level == 1) unlock(player, "quirky_tiny_shiny");
        if (shiny && perfect >= 3) unlock(player, "quirky_glittering_genetics");

        boolean legendary = EmblemManager.isLegendary(species);
        boolean ultra = EmblemManager.isUltraBeast(species);
        boolean paradox = EmblemManager.isParadox(species);
        boolean mythical = MYTHICALS.contains(species);
        if (legendary) unlock(player, "quirky_living_legend");
        if (mythical) unlock(player, "quirky_myth_believer");
        if (ultra) unlock(player, "quirky_beast_containment");
        if (paradox) unlock(player, "quirky_out_of_time");
        if (shiny && (legendary || mythical)) unlock(player, "quirky_radiant_myth");
        if (STARTERS.contains(species)) unlock(player, "quirky_starter_pack");
        if (PIKACHU_LINE.contains(species)) unlock(player, "quirky_pocket_mascot");
        if (EEVEE_LINE.contains(species)) unlock(player, "quirky_evolutionary_options");
        if (PSEUDOS.contains(species)) unlock(player, "quirky_future_powerhouse");
        if (FOSSILS.contains(species)) unlock(player, "quirky_ancient_history");
        if (BABIES.contains(species)) unlock(player, "quirky_babysitter");
        if (ICONIC.contains(species)) unlock(player, "quirky_box_art_energy");

        switch (species) {
            case "magikarp" -> { unlock(player, "quirky_karp_connoisseur"); if (shiny) unlock(player, "quirky_golden_splash"); if (level >= 100) unlock(player, "quirky_splash_master"); }
            case "bidoof" -> unlock(player, "quirky_bidoof_believer");
            case "ditto" -> unlock(player, "quirky_identity_crisis");
            case "snom" -> unlock(player, "quirky_snom_nom");
            case "wooper", "paldean_wooper" -> unlock(player, "quirky_wooper_trooper");
            case "shuckle" -> unlock(player, "quirky_dont_shuckle");
            case "psyduck" -> unlock(player, "quirky_headache_club");
            case "slowpoke" -> unlock(player, "quirky_eventually");
            case "trubbish" -> unlock(player, "quirky_treasure_not_trash");
            case "lechonk" -> unlock(player, "quirky_absolute_unit");
            case "mimikyu" -> unlock(player, "quirky_under_the_costume");
            case "snorlax" -> unlock(player, "quirky_power_napper");
            case "gengar" -> unlock(player, "quirky_shadow_smile");
            case "charizard" -> unlock(player, "quirky_not_a_dragon");
            case "lucario" -> unlock(player, "quirky_aura_reader");
            case "unown" -> unlock(player, "quirky_alphabet_soup");
            case "spinda" -> unlock(player, "quirky_dizzy_business");
            case "wobbuffet" -> unlock(player, "quirky_counter_culture");
            case "farfetchd", "farfetch_d" -> unlock(player, "quirky_leek_freak");
            case "delibird" -> unlock(player, "quirky_special_delivery");
            case "dunsparce", "dudunsparce" -> unlock(player, "quirky_dun_done_it");
            case "feebas" -> unlock(player, "quirky_hidden_beauty");
            case "shedinja" -> unlock(player, "quirky_one_hp_wonder");
            case "rotom" -> unlock(player, "quirky_appliance_inspector");
            case "porygon", "porygon2", "porygonz", "porygon_z" -> unlock(player, "quirky_download_complete");
        }
    }

    private static void unlock(ServerPlayer player, String id) { TitleManager.unlock(player, id); }

    private static List<Integer> ivValues(Object pokemon) {
        Object ivs = read(pokemon, "getIvs");
        if (ivs == null) ivs = read(pokemon, "getIVs");
        List<Integer> out = new ArrayList<>();
        collectNumbers(ivs, out, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        if (out.size() > 6) out = new ArrayList<>(out.subList(0, 6));
        return out;
    }

    private static void collectNumbers(Object value, List<Integer> out, Set<Object> seen, int depth) {
        if (value == null || out.size() >= 6 || depth > 4) return;
        if (value instanceof Number n) { int v=n.intValue(); if (v >= 0 && v <= 31) out.add(v); return; }
        if (!seen.add(value)) return;
        if (value instanceof Map<?,?> map) { for (Object v : map.values()) collectNumbers(v,out,seen,depth+1); return; }
        if (value instanceof Iterable<?> iterable) { for (Object v : iterable) collectNumbers(v,out,seen,depth+1); return; }
        if (value.getClass().isArray()) { for (int i=0;i<Array.getLength(value);i++) collectNumbers(Array.get(value,i),out,seen,depth+1); return; }
        for (String method : new String[]{"getHp","getAttack","getDefence","getDefense","getSpecialAttack","getSpecialDefence","getSpecialDefense","getSpeed","getValue"}) {
            Object nested = read(value, method);
            if (nested != null && nested != value) collectNumbers(nested,out,seen,depth+1);
        }
    }

    private static Object captureObservable(Class<?> eventsClass) {
        for (String name : new String[]{"POKEMON_CAPTURED","POKEMON_CAPTURED_EVENT","POKEMON_CAPTURED_POST","POKEMON_CAUGHT","POKEMON_CATCH_SUCCEEDED"}) {
            try { Field f=eventsClass.getField(name); Object v=f.get(null); if (v!=null) return v; } catch (Throwable ignored) {}
        }
        return null;
    }
    private static String speciesId(Object pokemon) {
        Object species=read(pokemon,"getSpecies"); Object id=read(species,"getResourceIdentifier"); if(id==null) id=read(species,"getName");
        String s=id==null?"":id.toString().toLowerCase(Locale.ROOT); int colon=s.indexOf(':'); if(colon>=0)s=s.substring(colon+1); return s.replace('-','_').replace(' ','_');
    }
    private static Object read(Object target, String method) { if(target==null)return null; try { Method m=target.getClass().getMethod(method); m.setAccessible(true); return m.invoke(target); } catch(Throwable ignored){return null;} }
    private static boolean bool(Object v) { return v instanceof Boolean b && b; }
    private static int number(Object v) { return v instanceof Number n ? n.intValue() : 0; }
    private static Set<String> set(String... values) { return new HashSet<>(Arrays.asList(values)); }
}
