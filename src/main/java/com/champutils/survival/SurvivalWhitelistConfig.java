package com.champutils.survival;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SurvivalWhitelistConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("champutils").resolve("survival_whitelist.json");
    public static Data DATA = defaults();

    private SurvivalWhitelistConfig() {}

    public static void load() {
        try {
            Files.createDirectories(PATH.getParent());
            if (!Files.exists(PATH)) { DATA = defaults(); save(); return; }
            try (Reader reader = Files.newBufferedReader(PATH)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                DATA = loaded == null ? defaults() : loaded;
            }
            normalize();
            save();
        } catch (Exception e) {
            e.printStackTrace();
            DATA = defaults();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) { GSON.toJson(DATA, writer); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static Data defaults() {
        Data d = new Data();
        d.enabled = false;
        d.kickMessage = "The survival server is currently whitelisted.";
        d.allowedNames = new ArrayList<>();
        d.allowedUuids = new ArrayList<>();
        return d;
    }

    private static void normalize() {
        if (DATA.kickMessage == null || DATA.kickMessage.isBlank()) DATA.kickMessage = defaults().kickMessage;
        if (DATA.allowedNames == null) DATA.allowedNames = new ArrayList<>();
        if (DATA.allowedUuids == null) DATA.allowedUuids = new ArrayList<>();
        DATA.allowedNames.replaceAll(name -> name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
        DATA.allowedNames.removeIf(String::isBlank);
        DATA.allowedUuids.replaceAll(uuid -> uuid == null ? "" : uuid.trim().toLowerCase(Locale.ROOT));
        DATA.allowedUuids.removeIf(String::isBlank);
    }

    public static final class Data {
        public boolean enabled;
        public String kickMessage;
        public List<String> allowedNames;
        public List<String> allowedUuids;
    }
}
