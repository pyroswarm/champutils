package com.champutils.guild;

/**
 * @deprecated Use com.champutils.buff.BuffRegistry plus GuildBuffProvider.
 */
@Deprecated
public final class GuildBuffRegistry {
    private GuildBuffRegistry() {}

    public static void rebuildFromConfig() {
        GuildBuffProvider.rebuildFromConfig();
    }
}
