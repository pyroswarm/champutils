package com.champutils.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.champutils.matchmaking.ArenaManager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Config {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File loadedFile;

    public static Map<String, Format> formats;

    public static Matchmaking matchmaking;

    public static List<Rank> ranks;

    public static List<ArenaManager.Arena> arenas;

    public static ProfileConversion profileConversion = new ProfileConversion();



/* =========================
 NEW WARP CONFIGS
========================= */

    public static Warp evTrainingWarp =
            new Warp(
                    100,
                    70,
                    100
            );

    public static Warp eliteFourWarp =
            new Warp(
                    200,
                    70,
                    200
            );



    public static void load(
            File file
    ){

        loadedFile = file;

        try{

            FileReader reader =
                    new FileReader(
                            file
                    );


            Wrapper data =
                    GSON.fromJson(
                            reader,
                            Wrapper.class
                    );


            reader.close();


            if(
                    data == null
            ){
                return;
            }


            formats =
                    data.formats;

            matchmaking =
                    data.matchmaking;

            ranks =
                    data.ranks;

            arenas =
                    data.arenas;

            profileConversion = data.profileConversion != null ? data.profileConversion : new ProfileConversion();
            profileConversion.normalize();

            ensureDefaultArenas();
            ensureDefaultBattleRules();

            save();



/* =========================
 LOAD WARPS
========================= */

            if(
                    data.evTrainingWarp != null
            ){
                evTrainingWarp =
                        data.evTrainingWarp;
            }

            if(
                    data.eliteFourWarp != null
            ){
                eliteFourWarp =
                        data.eliteFourWarp;
            }



            System.out.println(
                    "[ChampUtils] Config loaded successfully."
            );

            System.out.println(
                    "[ChampUtils] Loaded "
                            +
                            (
                                    arenas == null
                                            ? 0
                                            : arenas.size()
                            )
                            +
                            " arenas."
            );


        }
        catch(Exception e){

            System.out.println(
                    "[ChampUtils] Failed to load config!"
            );

            e.printStackTrace();
        }

    }



    public static void ensureDefaultBattleRules(){
        if(formats == null){
            return;
        }

        Format ranked = formats.get("ranked");
        if(ranked == null){
            return;
        }

        if(ranked.battle_rules == null){
            ranked.battle_rules = new ArrayList<>();
        }

        addBattleRuleIfMissing(ranked, "Sleep Clause Mod");
        addBattleRuleIfMissing(ranked, "Species Clause");
        addBattleRuleIfMissing(ranked, "OHKO Clause");
        addBattleRuleIfMissing(ranked, "Evasion Moves Clause");
        addBattleRuleIfMissing(ranked, "Endless Battle Clause");
        addBattleRuleIfMissing(ranked, "Moody Clause");
        addBattleRuleIfMissing(ranked, "Baton Pass Clause");
        addBattleRuleIfMissing(ranked, "Swagger Clause");

        if(ranked.cobblemon_format == null || ranked.cobblemon_format.isBlank()){
            ranked.cobblemon_format = "gen9singles";
        }
    }
    private static void addBattleRuleIfMissing(Format format, String ruleName){
        if(format == null){
            return;
        }

        if(format.battle_rules == null){
            format.battle_rules = new ArrayList<>();
        }

        String expected = compactBattleRule(ruleName);
        for(String existing : format.battle_rules){
            if(expected.equals(compactBattleRule(existing))){
                return;
            }
        }

        format.battle_rules.add(ruleName);
    }

    private static String compactBattleRule(String rule){
        if(rule == null){
            return "";
        }

        String compact = rule
                .toLowerCase()
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "");

        // Treat Sleep Clause and Sleep Clause Mod as the same ChampUtils setting.
        if(compact.equals("sleepclausemod")){
            return "sleepclause";
        }

        return compact;
    }



/* =========================
 ARENA CONFIG HELPERS
========================= */

    public static void ensureDefaultArenas(){

        if(arenas == null){
            arenas = new ArrayList<>();
        }

        for(int i=1;i<=10;i++){
            String id = "arena" + i;

            if(getArena(id) != null){
                continue;
            }

            ArenaManager.Arena arena = new ArenaManager.Arena();
            arena.id = id;
            arena.world = "multiworld:spawn1";
            arena.centerX = 100 + ((i - 1) * 30);
            arena.y = 65;
            arena.centerZ = 100;
            arena.theme = defaultTheme(i);
            arena.music = defaultMusic(i);
            arenas.add(arena);
        }
    }

    public static ArenaManager.Arena getArena(String id){

        if(id == null || arenas == null){
            return null;
        }

        for(ArenaManager.Arena arena : arenas){
            if(arena != null && arena.id != null && arena.id.equalsIgnoreCase(id.trim())){
                return arena;
            }
        }

        return null;
    }

    public static ArenaManager.Arena createArena(String id){

        ensureDefaultArenas();

        ArenaManager.Arena existing = getArena(id);
        if(existing != null){
            return existing;
        }

        ArenaManager.Arena arena = new ArenaManager.Arena();
        arena.id = id.trim().toLowerCase();
        arena.world = "multiworld:spawn1";
        arena.centerX = 0;
        arena.y = 65;
        arena.centerZ = 0;
        arena.theme = "Custom";
        arena.music = "gym";
        arenas.add(arena);
        save();
        return arena;
    }

    public static boolean deleteArena(String id){
        if(id == null || arenas == null){
            return false;
        }

        boolean removed = arenas.removeIf(arena -> arena != null && arena.id != null && arena.id.equalsIgnoreCase(id.trim()));
        if(removed){
            save();
        }
        return removed;
    }

    public static void save(){

        if(loadedFile == null){
            loadedFile = new File("config/champutils/rules.json");
        }

        try{
            File parent = loadedFile.getParentFile();
            if(parent != null && !parent.exists()){
                parent.mkdirs();
            }

            Wrapper wrapper = new Wrapper();
            wrapper.formats = formats;
            wrapper.matchmaking = matchmaking;
            wrapper.ranks = ranks;
            wrapper.arenas = arenas;
            if (profileConversion == null) profileConversion = new ProfileConversion();
            profileConversion.normalize();
            wrapper.profileConversion = profileConversion;
            wrapper.evTrainingWarp = evTrainingWarp;
            wrapper.eliteFourWarp = eliteFourWarp;

            try(FileWriter writer = new FileWriter(loadedFile)){
                GSON.toJson(wrapper, writer);
            }
        }
        catch(Exception e){
            System.out.println("[ChampUtils] Failed to save rules.json!");
            e.printStackTrace();
        }
    }

    private static String defaultTheme(int index){
        return switch(index){
            case 1 -> "Grass";
            case 2 -> "Fire";
            case 3 -> "Water";
            case 4 -> "Electric";
            case 5 -> "Ice";
            case 6 -> "Dragon";
            case 7 -> "Ghost";
            case 8 -> "Steel";
            case 9 -> "Fairy";
            case 10 -> "Monarch Coliseum";
            default -> "Custom";
        };
    }

    private static String defaultMusic(int index){
        return switch(index){
            case 1 -> "gym";
            case 2 -> "volcano";
            case 3 -> "ocean";
            case 4 -> "power";
            case 5 -> "glacier";
            case 6 -> "legend";
            case 7 -> "haunted";
            case 8 -> "factory";
            case 9 -> "mystic";
            case 10 -> "final";
            default -> "gym";
        };
    }


/* =========================
 WRAPPER
========================= */

    public static class Wrapper {

        public Map<String, Format> formats;

        public Matchmaking matchmaking;

        public List<Rank> ranks;

        public List<ArenaManager.Arena> arenas;

        public ProfileConversion profileConversion;



        /* NEW */
        public Warp evTrainingWarp;

        public Warp eliteFourWarp;

    }



/* =========================
 PROFILE CONVERSION CONFIG
========================= */

    public static class ProfileConversion {
        /**
         * Nuzlocke, Ironman, and Monotype profiles must be this many hours old before players can convert them to Normal.
         * Islander profiles can never convert to Normal. Set to 0 to disable the age gate. Default: 24 hours.
         */
        public int minAgeHoursBeforeNormal = 24;

        /** Backwards compatibility for older rules.json files. */
        public int minAgeDaysBeforeNormal = -1;

        public void normalize(){
            if(minAgeHoursBeforeNormal < 0){
                minAgeHoursBeforeNormal = 0;
            }
            // minAgeDaysBeforeNormal is kept only so old configs deserialize cleanly; conversion now uses hours.
        }
    }


/* =========================
 WARP CLASS
========================= */

    public static class Warp {

        public int x;
        public int y;
        public int z;

        public Warp(){}

        public Warp(
                int x,
                int y,
                int z
        ){
            this.x=x;
            this.y=y;
            this.z=z;
        }

    }

}