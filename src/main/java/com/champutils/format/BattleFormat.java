package com.champutils.format;

public class BattleFormat {

    public String id;           // "ranked", "casual"
    public boolean ranked;      // true = uses ELO
    public int maxEloDiff;      // matchmaking tolerance
    public int levelCap;        // optional (you already use this)

    public BattleFormat(String id, boolean ranked, int maxEloDiff, int levelCap) {
        this.id = id;
        this.ranked = ranked;
        this.maxEloDiff = maxEloDiff;
        this.levelCap = levelCap;
    }
}