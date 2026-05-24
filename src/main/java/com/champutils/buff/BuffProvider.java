package com.champutils.buff;

/**
 * A source of shared MMO buffs.
 *
 * Examples: guilds, server-wide cash shop boosters, weekend events, holiday
 * events, ranks, consumables, territories, raids, or future skill trees.
 */
public interface BuffProvider {
    String id();

    /** Lower values are evaluated first for predictable debug/UI output. */
    default int priority() {
        return 1000;
    }

    /** Return decimal bonus. Example: 0.10D = +10%. */
    double getBuff(BuffContext context, BuffType type);
}
