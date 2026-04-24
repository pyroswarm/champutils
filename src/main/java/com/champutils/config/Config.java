package com.champutils.config;

import com.google.gson.Gson;
import com.champutils.matchmaking.ArenaManager;

import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Map;

public class Config {

    public static Map<String, Format> formats;
    public static Matchmaking matchmaking;
    public static List<Rank> ranks;

    // Arenas from rules.json
    public static List<ArenaManager.Arena> arenas;


    public static void load(File file) {

        try {
            Gson gson = new Gson();

            FileReader reader =
                    new FileReader(file);

            Wrapper data =
                    gson.fromJson(
                            reader,
                            Wrapper.class
                    );

            formats = data.formats;
            matchmaking = data.matchmaking;
            ranks = data.ranks;
            arenas = data.arenas;

            System.out.println(
                    "[ChampUtils] Config loaded successfully."
            );

            System.out.println(
                    "[ChampUtils] Loaded "
                            + (arenas == null ? 0 : arenas.size())
                            + " arenas."
            );

        } catch (Exception e) {

            System.out.println(
                    "[ChampUtils] Failed to load config!"
            );

            e.printStackTrace();
        }
    }


    public static class Wrapper {

        public Map<String, Format> formats;
        public Matchmaking matchmaking;
        public List<Rank> ranks;

        public List<ArenaManager.Arena> arenas;
    }
}