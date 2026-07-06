package com.champutils.survival;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public final class SurvivalWorldConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/survival_worlds.json");
    private static Data data = new Data();

    private SurvivalWorldConfig() {}

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) { save(); return; }
            try (FileReader reader = new FileReader(FILE)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                data = loaded == null ? new Data() : loaded.withDefaults();
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load survival_worlds.json. Using defaults.");
            e.printStackTrace();
            data = new Data();
        }
    }

    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(data.withDefaults(), writer);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save survival_worlds.json.");
            e.printStackTrace();
        }
    }

    public static Data get() { return data.withDefaults(); }

    public static final class Data {
        public boolean enabled = true;
        public int overworldStartIndex = 1;
        public int overworldCount = 1;
        public int netherStartIndex = 1;
        public int netherWorldCount = 1;
        public int endStartIndex = 1;
        public int endWorldCount = 1;
        public String overworldPrefix = "multiworld:survival_overworld";
        public String netherPrefix = "multiworld:survival_nether";
        public String endPrefix = "multiworld:survival_end";
        public int borderRadius = 10000;
        public int spawnY = 100;
        public boolean runWorldCommands = true;
        public int defaultMaxHomes = 3;

        /** Soft cap used only by /rtp world selection. Homes, claims, tpa, portals, etc. are not blocked by this. */
        public int maxRtpPlayersPerWorld = 50;

        /** Future proxy/backend hook for split survival servers. Local RTP still only checks worlds loaded on this server. */
        public String remoteRtpTransferCommand = "server {player} {target_server}";

        public List<String> overworldCreateCommands = new ArrayList<>(List.of(
                "mw create {world_id} NORMAL -g=NORMAL",
                "mw load {world_id}"
        ));
        public List<String> netherCreateCommands = new ArrayList<>(List.of(
                "mw create {world_id} NETHER -g=NETHER",
                "mw load {world_id}"
        ));
        public List<String> endCreateCommands = new ArrayList<>(List.of(
                "mw create {world_id} THE_END -g=THE_END",
                "mw load {world_id}"
        ));
        public List<String> postCreateCommands = new ArrayList<>(List.of(
                "worldborder center 0 0",
                "worldborder set {border_diameter}"
        ));

        private Data withDefaults() {
            if (overworldStartIndex < 1) overworldStartIndex = 1;
            if (overworldCount < 1) overworldCount = 1;
            if (netherStartIndex < 1) netherStartIndex = 1;
            if (netherWorldCount < 0) netherWorldCount = 1;
            if (endStartIndex < 1) endStartIndex = 1;
            if (endWorldCount < 0) endWorldCount = 1;
            if (overworldPrefix == null || overworldPrefix.isBlank()) overworldPrefix = "multiworld:survival_overworld";
            if (netherPrefix == null || netherPrefix.isBlank()) netherPrefix = "multiworld:survival_nether";
            if (endPrefix == null || endPrefix.isBlank()) endPrefix = "multiworld:survival_end";
            if (borderRadius != 10000) borderRadius = 10000;
            if (spawnY < -64) spawnY = 100;
            if (defaultMaxHomes < 1) defaultMaxHomes = 3;
            if (maxRtpPlayersPerWorld < 1) maxRtpPlayersPerWorld = 50;
            if (maxRtpPlayersPerWorld > 500) maxRtpPlayersPerWorld = 500;
            if (remoteRtpTransferCommand == null || remoteRtpTransferCommand.isBlank()) remoteRtpTransferCommand = "server {player} {target_server}";
            if (overworldCreateCommands == null || overworldCreateCommands.isEmpty()) overworldCreateCommands = new ArrayList<>(List.of("mw create {world_id} NORMAL -g=NORMAL", "mw load {world_id}"));
            if (netherCreateCommands == null || netherCreateCommands.isEmpty()) netherCreateCommands = new ArrayList<>(List.of("mw create {world_id} NETHER -g=NETHER", "mw load {world_id}"));
            if (endCreateCommands == null || endCreateCommands.isEmpty()) endCreateCommands = new ArrayList<>(List.of("mw create {world_id} THE_END -g=THE_END", "mw load {world_id}"));
            if (postCreateCommands == null) postCreateCommands = new ArrayList<>();
            return this;
        }
    }
}
