package com.champutils.adventurer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ten editable Battle Tower pools. Sets are intentionally explicit so illegal/random coverage cannot leak in. */
public final class BattleTowerPoolConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/battle_tower_pools.json");
    public static Config DATA = defaults();
    private BattleTowerPoolConfig() {}

    public static synchronized void load() {
        try {
            FILE.getParentFile().mkdirs();
            if (FILE.exists()) try (FileReader r = new FileReader(FILE)) { Config c = GSON.fromJson(r, Config.class); if (c != null) DATA = c; }
            normalize(); save();
        } catch (Exception e) { e.printStackTrace(); DATA = defaults(); }
    }
    public static synchronized void save() { try { FILE.getParentFile().mkdirs(); try (FileWriter w = new FileWriter(FILE)) { GSON.toJson(DATA,w); } } catch (Exception e) { e.printStackTrace(); } }
    public static Tier tierForFloor(int floor) { int tier=Math.max(1,Math.min(10,((Math.max(1,floor)-1)/10)+1)); return tier(tier); }
    public static Tier tier(int tier) { int safe=Math.max(1,Math.min(10,tier)); return DATA.tiers.getOrDefault(String.valueOf(safe), defaults().tiers.get(String.valueOf(safe))); }
    private static void normalize() { if (DATA.tiers==null) DATA.tiers=new LinkedHashMap<>(); Config d=defaults(); for(int i=1;i<=10;i++){String k=String.valueOf(i); Tier t=DATA.tiers.get(k); if(t==null||t.pool==null||t.pool.isEmpty()) DATA.tiers.put(k,d.tiers.get(k)); else {t.tier=i;t.pokemonCount=Math.max(1,Math.min(6,t.pokemonCount));t.aiSkill=Math.max(0,Math.min(5,t.aiSkill));}} }

    public static final class Config { public Map<String,Tier> tiers=new LinkedHashMap<>(); }
    public static final class Tier { public int tier; public int pokemonCount=2; public int aiSkill=2; public List<SetEntry> pool=new ArrayList<>(); }
    public static final class SetEntry { public String species=""; public String ability=""; public String nature=""; public String heldItem=""; public List<String> moves=new ArrayList<>(); public Map<String,Integer> evs=maxEvs(); public Map<String,Integer> ivs=maxIvs(); public double weight=1.0; }
    private static Map<String,Integer> maxEvs(){Map<String,Integer> m=new LinkedHashMap<>(); for(String s:List.of("hp","attack","defence","special_attack","special_defence","speed"))m.put(s,252); return m;}
    private static Map<String,Integer> maxIvs(){Map<String,Integer> m=new LinkedHashMap<>(); for(String s:List.of("hp","attack","defence","special_attack","special_defence","speed"))m.put(s,31); return m;}
    private static SetEntry set(String species,String ability,String nature,String item,String...moves){SetEntry e=new SetEntry();e.species=species;e.ability=ability;e.nature=nature;e.heldItem=item;e.moves=List.of(moves);return e;}
    private static Config defaults(){
        Config c=new Config();
        String[][] species={{"arcanine","gastrodon","corviknight","gardevoir","mamoswine","breloom"},{"rotomwash","scizor","gliscor","primarina","hydreigon","amoonguss"},{"garchomp","volcarona","slowking","kingambit","meowscarada","skarmory"},{"dragapult","greattusk","gholdengo","rillaboom","clefable","zapdos"},{"ironvaliant","roaringmoon","heatran","toxapex","weavile","dragonite"},{"landorustherian","ogerponwellspring","darkrai","corviknight","primarina","gliscor"},{"zamazenta","kyurem","garganacl","gholdengo","ragingbolt","greattusk"},{"hooh","lunala","necrozmaduskmane","arceuswater","eternatus","tinglu"},{"koraidon","miraidon","calyrexshadow","zacian","arceusground","hooh"},{"miraidon","koraidon","calyrexshadow","zacian","necrozmaduskmane","arceus"}};
        String[][][] moves={
          {{"flareblitz","wildcharge","extremespeed","closecombat"},{"earthpower","icebeam","recover","sludgebomb"},{"bravebird","bodypress","roost","uturn"},{"moonblast","psychic","mysticalfire","calmmind"},{"earthquake","iciclecrash","knockoff","iceshard"},{"spore","machpunch","bulletseed","rocktomb"}},
          {{"hydropump","voltswitch","thunderbolt","willowisp"},{"bulletpunch","uturn","closecombat","knockoff"},{"earthquake","facade","knockoff","roost"},{"moonblast","sparklingaria","icebeam","psychicnoise"},{"dracometeor","darkpulse","flashcannon","flamethrower"},{"spore","gigadrain","sludgebomb","foulplay"}},
          {{"earthquake","dragonclaw","stoneedge","firefang"},{"fierydance","bugbuzz","gigadrain","quiverdance"},{"future sight","surf","flamethrower","slackoff"},{"kowtowcleave","ironhead","suckerpunch","lowkick"},{"flowertrick","knockoff","tripleaxel","uturn"},{"bodypress","bravebird","roost","spikes"}},
          {{"dracometeor","shadowball","flamethrower","uturn"},{"headlongrush","closecombat","knockoff","rapidspin"},{"makeitrain","shadowball","focusblast","recover"},{"grassyglide","woodhammer","knockoff","uturn"},{"moonblast","flamethrower","thunderwave","moonlight"},{"hurricane","thunderbolt","heatwave","roost"}},
          {{"moonblast","closecombat","thunderbolt","psyshock"},{"knockoff","acrobatics","earthquake","dragondance"},{"magmastorm","earthpower","flashcannon","stealthrock"},{"scald","sludgebomb","recover","haze"},{"tripleaxel","knockoff","lowkick","iceshard"},{"extremespeed","earthquake","ice spinner","dragondance"}},
          {{"earthquake","uturn","stoneedge","knockoff"},{"ivy cudgel","hornleech","playrough","superpower"},{"darkpulse","icebeam","focusblast","nastyplot"},{"bravebird","bodypress","roost","uturn"},{"moonblast","sparklingaria","icebeam","psychicnoise"},{"earthquake","facade","knockoff","roost"}},
          {{"bodypress","crunch","stoneedge","heavy slam"},{"freezedry","dracometeor","earthpower","icebeam"},{"saltcure","bodypress","recover","earthquake"},{"makeitrain","shadowball","focusblast","recover"},{"thunderclap","dracometeor","flamethrower","voltswitch"},{"headlongrush","closecombat","knockoff","rapidspin"}},
          {{"sacredfire","bravebird","earthquake","recover"},{"moongeistbeam","moonblast","focusblast","roost"},{"sunsteelstrike","earthquake","knockoff","morning sun"},{"judgment","icebeam","recover","calmmind"},{"dynamaxcannon","flamethrower","sludgebomb","recover"},{"ruination","earthquake","whirlwind","rest"}},
          {{"collisioncourse","flareblitz","dragonclaw","uturn"},{"electrodrift","dracometeor","overheat","voltswitch"},{"astralbarrage","psychic","drainingkiss","nastyplot"},{"behemothblade","playrough","closecombat","wildcharge"},{"judgment","icebeam","recover","calmmind"},{"sacredfire","bravebird","earthquake","recover"}},
          {{"electrodrift","dracometeor","overheat","voltswitch"},{"collisioncourse","flareblitz","dragonclaw","uturn"},{"astralbarrage","psychic","drainingkiss","nastyplot"},{"behemothblade","playrough","closecombat","wildcharge"},{"sunsteelstrike","earthquake","knockoff","morning sun"},{"judgment","earthpower","icebeam","recover"}}
        };
        for(int t=1;t<=10;t++){Tier tier=new Tier();tier.tier=t;tier.pokemonCount=2;tier.aiSkill=Math.min(5,1+(t/2)); for(int i=0;i<species[t-1].length;i++){String sp=species[t-1][i]; String[] mv=moves[t-1][i]; tier.pool.add(set(sp,"","jolly","leftovers",mv));} c.tiers.put(String.valueOf(t),tier);} return c;
    }
}
