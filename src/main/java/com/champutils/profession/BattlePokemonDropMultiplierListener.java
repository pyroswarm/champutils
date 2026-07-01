package com.champutils.profession;

import com.cobblemon.mod.common.api.drop.DropEntry;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.drops.LootDroppedEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Adds Battling-profession scaling to Cobblemon's normal defeated-Pokémon drop table.
 * This listens to Cobblemon's drop event and adds extra selected drop entries before
 * Cobblemon spawns the items, avoiding fake duplicate entity scans or tick loops.
 */
public final class BattlePokemonDropMultiplierListener {
    private BattlePokemonDropMultiplierListener() {}

    public static void register() {
        CobblemonEvents.LOOT_DROPPED.subscribe(BattlePokemonDropMultiplierListener::handleLootDropped);
    }

    private static void handleLootDropped(LootDroppedEvent event) {
        try {
            if (!BattleProfessionLootConfig.enabled || !BattleProfessionLootConfig.extraPokemonDropsEnabled) return;
            if (event == null || event.getPlayer() == null || event.getEntity() == null || event.getDrops() == null || event.getDrops().isEmpty()) return;
            if (!isPokemonEntity(event.getEntity())) return;

            ServerPlayer player = event.getPlayer();
            int level = Math.max(0, ProfessionManager.getLevel(player, ProfessionType.BATTLING));
            if (level < BattleProfessionLootConfig.extraPokemonDropsMinBattlingLevel) return;

            int extraCopies = calculateExtraCopies(level);
            if (extraCopies <= 0) return;

            List<DropEntry> original = new ArrayList<>(event.getDrops());
            int added = 0;
            int maxAdded = Math.max(0, BattleProfessionLootConfig.extraPokemonDropsMaxAddedEntries);
            for (DropEntry drop : original) {
                if (drop == null) continue;
                for (int i = 0; i < extraCopies && added < maxAdded; i++) {
                    if (roll(level)) {
                        event.getDrops().add(drop);
                        added++;
                    }
                }
                if (added >= maxAdded) break;
            }
        } catch (Throwable throwable) {
            System.err.println("[ChampUtils] Battle Pokémon drop multiplier failed safely: " + throwable.getMessage());
        }
    }

    private static int calculateExtraCopies(int battlingLevel) {
        int every = Math.max(1, BattleProfessionLootConfig.extraPokemonDropsCopyEveryLevels);
        int copies = battlingLevel / every;
        return Math.max(0, Math.min(copies, BattleProfessionLootConfig.extraPokemonDropsMaxExtraCopies));
    }

    private static boolean roll(int battlingLevel) {
        double chance = BattleProfessionLootConfig.extraPokemonDropsBaseChance + (Math.max(0, battlingLevel) * BattleProfessionLootConfig.extraPokemonDropsChancePerLevel);
        chance = Math.max(0.0D, Math.min(BattleProfessionLootConfig.extraPokemonDropsMaxChance, chance));
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    private static boolean isPokemonEntity(Object entity) {
        String name = entity.getClass().getName();
        return name.equals("com.cobblemon.mod.common.entity.pokemon.PokemonEntity") || name.endsWith(".PokemonEntity");
    }
}
