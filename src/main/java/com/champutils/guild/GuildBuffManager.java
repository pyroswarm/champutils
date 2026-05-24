package com.champutils.guild;

import com.champutils.buff.BuffContext;
import com.champutils.buff.BuffManager;
import com.champutils.buff.BuffType;
import com.champutils.profession.ProfessionType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Guild-facing compatibility wrapper.
 *
 * Core gameplay should use com.champutils.buff.BuffManager. This class remains
 * for guild commands, guild level-up messages, and old call sites.
 */
public final class GuildBuffManager {
    private GuildBuffManager() {}

    public static void init() {
        com.champutils.buff.ServerBuffManager.init();
        GuildBuffProvider.rebuildFromConfig();
        BuffManager.registerProvider(GuildBuffProvider.INSTANCE);
    }

    public static void clearCache() {
        GuildBuffProvider.clearCache();
    }

    public static double getProfessionXpBonus(ServerPlayer player, ProfessionType profession) {
        if (player == null || profession == null) return 0.0D;
        BuffType type = BuffType.fromProfession(profession);
        return type == null ? 0.0D : BuffManager.getTotalBuff(BuffContext.professionXp(player, profession), type);
    }

    public static double getProfessionXpBonus(UUID playerUuid, ProfessionType profession) {
        return 0.0D;
    }

    public static void applyCatchBuffs(ServerPlayer player, Pokemon pokemon) {
        BuffManager.applyCatchBuffs(player, pokemon);
    }

    public static void applyCatchBuffs(BuffContext context) {
        BuffManager.applyCatchBuffs(context);
    }

    public static List<Component> activeBuffLines(UUID playerUuid) {
        return GuildBuffProvider.activeBuffLines(playerUuid);
    }

    public static List<Component> nextBuffLines(UUID playerUuid) {
        return GuildBuffProvider.nextBuffLines(playerUuid);
    }

    public static List<Component> unlockedBuffMessages(int oldLevel, int newLevel) {
        return GuildBuffProvider.unlockedBuffMessages(oldLevel, newLevel);
    }

    public static String percent(double value) {
        return BuffManager.percent(value);
    }
}
