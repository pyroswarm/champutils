package com.champutils.hunt;

import java.util.ArrayList;
import java.util.List;

public class PokemonHuntState {
    public long cycleStartedAtMillis = 0L;
    public long nextRefreshAtMillis = 0L;
    public List<HuntEntry> hunts = new ArrayList<>();
    public List<HuntEntry> pendingRewards = new ArrayList<>();

    public static class HuntEntry {
        public String id;
        public String species;
        public String nature;
        public String gender;
        public String ability;
        public String difficulty;
        public boolean claimed = false;
        public boolean rewardClaimed = false;
        public String winnerUuid = "";
        public String winnerName = "";
        public long completedAtMillis = 0L;
        public PokemonHuntConfig.Rewards rewards = new PokemonHuntConfig.Rewards();
    }
}
