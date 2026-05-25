package com.champutils.config;

import java.util.ArrayList;
import java.util.List;

public class Format {
    public int level_cap;
    public List<String> banned_pokemon;
    public List<String> banned_moves;
    public List<String> banned_items;
    public List<String> banned_abilities;
    public Matchmaking matchmaking;
    public boolean allow_battle_items = true;

    /**
     * Cobblemon/Pokemon Showdown battle rules applied when this ChampUtils format starts a PvP battle.
     * Examples: "Sleep Clause Mod", "Species Clause", "Evasion Moves Clause".
     */
    public List<String> battle_rules = new ArrayList<>();

    /**
     * Base Cobblemon battle format identifier. Defaults to standard Gen 9 singles.
     */
    public String cobblemon_format = "gen9singles";
}

