package com.champutils.config;

public class Matchmaking {
    // Ranked matchmaking now uses rank spread instead of raw RP spread.
    // 0 = same rank only, 1 = adjacent ranks, 2 = two ranks away, etc.
    public int initial_rank_spread = 0;
    public int expand_rank_spread = 1;

    // How often the allowed rank spread expands while waiting in queue.
    public int expand_time_seconds = 30;

    public int rematch_cooldown_seconds;

    // Legacy fields kept so older rules.config/rules.json files still parse safely.
    // They are no longer used for ranked matchmaking.
    public int initial_range;
    public int expand_range;
}
