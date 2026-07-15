package com.champutils.gamerule;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

public final class GlobalGameruleManager {

    private GlobalGameruleManager() {}

    public static void applyAll(MinecraftServer server) {
        if (server == null || !GlobalGameruleConfig.DATA.enabled) return;

        int applied = 0;
        for (ServerLevel level : server.getAllLevels()) {
            applyToLevel(server, level);
            applied++;
        }
        System.out.println("[ChampUtils] Applied global gamerules to " + applied + " loaded world(s).");
    }

    public static void applyToLevel(MinecraftServer server, ServerLevel level) {
        if (server == null || level == null || !GlobalGameruleConfig.DATA.enabled) return;

        GameRules rules = level.getGameRules();
        GlobalGameruleConfig.Data data = GlobalGameruleConfig.DATA;

        rules.getRule(GameRules.RULE_DAYLIGHT).set(data.doDaylightCycle, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(data.doWeatherCycle, server);
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(data.keepInventory, server);
        rules.getRule(GameRules.RULE_MOBGRIEFING).set(data.mobGriefing, server);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(data.doMobSpawning, server);
        rules.getRule(GameRules.RULE_DOINSOMNIA).set(data.doInsomnia, server);
    }
}
