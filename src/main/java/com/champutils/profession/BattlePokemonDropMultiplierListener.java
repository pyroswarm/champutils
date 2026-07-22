package com.champutils.profession;

import com.cobblemon.mod.common.api.drop.DropEntry;
import com.cobblemon.mod.common.api.drop.ItemDropEntry;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.drops.LootDroppedEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies Battling profession progression to Cobblemon's native defeated-Pokémon drops.
 *
 * Two separate benefits are applied:
 *  1. Failed native drop rolls receive a recovery roll so profession/main mastery
 *     levels raise the actual probability of obtaining entries in the Pokémon's table.
 *  2. Successfully selected entries can receive additional copies.
 */
public final class BattlePokemonDropMultiplierListener {
    private BattlePokemonDropMultiplierListener() {}

    public static void register() {
        CobblemonEvents.LOOT_DROPPED.subscribe(BattlePokemonDropMultiplierListener::handleLootDropped);
    }

    private static void handleLootDropped(LootDroppedEvent event) {
        try {
            if (!BattleProfessionLootConfig.enabled) return;
            if (event == null || event.getPlayer() == null || event.getEntity() == null || event.getDrops() == null) return;
            if (!(event.getEntity() instanceof PokemonEntity pokemonEntity)) return;

            ServerPlayer player = event.getPlayer();
            int level = Math.max(0, ProfessionManager.getBenefitLevel(player, ProfessionType.BATTLING));
            if (level < BattleProfessionLootConfig.extraPokemonDropsMinBattlingLevel) return;

            int maxAdded = Math.max(0, BattleProfessionLootConfig.extraPokemonDropsMaxAddedEntries);
            int added = 0;
            Map<String, Integer> recoveredRareDrops = new LinkedHashMap<>();
            Map<String, Integer> extraCopyDrops = new LinkedHashMap<>();

            // Increase the real per-entry percentage. Since Cobblemon has already made
            // its native roll by the time this event fires, roll only the missing
            // probability. This produces the requested effective chance without
            // rerolling entries that already succeeded.
            if (BattleProfessionLootConfig.nativeDropChanceBoostEnabled && maxAdded > 0) {
                double relativeBonus = calculateNativeChanceRelativeBonus(player, pokemonEntity, level);
                if (relativeBonus > 0.0D) {
                    List<DropEntry> tableEntries = new ArrayList<>(event.getTable().getEntries());
                    for (DropEntry entry : tableEntries) {
                        if (entry == null || added >= maxAdded) break;
                        if (!entry.canDrop(pokemonEntity.getPokemon())) continue;

                        int selectedCount = countIdentity(event.getDrops(), entry);
                        int maxSelectable = Math.max(1, entry.getMaxSelectableTimes());
                        if (selectedCount >= maxSelectable) continue;

                        double baseChance = clamp01(entry.getPercentage() / 100.0D);
                        if (baseChance <= 0.0D || baseChance >= 1.0D) continue;

                        double effectiveChance = clamp01(baseChance * (1.0D + relativeBonus));
                        // Conditional recovery probability after the original roll failed:
                        // base + (1-base)*recovery = effective.
                        double recoveryChance = (effectiveChance - baseChance) / (1.0D - baseChance);
                        if (recoveryChance > 0.0D && ThreadLocalRandom.current().nextDouble() < recoveryChance) {
                            event.getDrops().add(entry);
                            added++;
                            recordBonusDrop(recoveredRareDrops, entry);
                        }
                    }
                }
            }

            if (BattleProfessionLootConfig.extraPokemonDropsEnabled && added < maxAdded && !event.getDrops().isEmpty()) {
                int extraCopies = calculateExtraCopies(level);
                if (extraCopies > 0) {
                    List<DropEntry> original = new ArrayList<>(event.getDrops());
                    for (DropEntry drop : original) {
                        if (drop == null) continue;
                        for (int i = 0; i < extraCopies && added < maxAdded; i++) {
                            if (rollExtraCopy(level)) {
                                event.getDrops().add(drop);
                                added++;
                                recordBonusDrop(extraCopyDrops, drop);
                            }
                        }
                        if (added >= maxAdded) break;
                    }
                }
            }

            announceBonusDrops(player, recoveredRareDrops, extraCopyDrops);
        } catch (Throwable throwable) {
            System.err.println("[ChampUtils] Battle Pokémon drop multiplier failed safely: " + throwable.getMessage());
        }
    }

    private static void recordBonusDrop(Map<String, Integer> totals, DropEntry entry) {
        String name = describeDrop(entry);
        totals.merge(name, 1, Integer::sum);
    }

    private static String describeDrop(DropEntry entry) {
        if (entry instanceof ItemDropEntry itemDrop) {
            try {
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemDrop.getItem());
                if (item != null) return new ItemStack(item).getHoverName().getString();
            } catch (Throwable ignored) {
                // Fall through to a stable identifier instead of breaking loot processing.
            }
            if (itemDrop.getItem() != null) return itemDrop.getItem().toString();
        }
        return "special Pokémon loot";
    }

    private static void announceBonusDrops(
            ServerPlayer player,
            Map<String, Integer> recoveredRareDrops,
            Map<String, Integer> extraCopyDrops
    ) {
        if (player == null) return;

        if (!recoveredRareDrops.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "§6Battling Profession §7rare-drop bonus: §e" + formatDrops(recoveredRareDrops)
            ));
        }
        if (!extraCopyDrops.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "§6Battling Profession §7extra loot: §e" + formatDrops(extraCopyDrops)
            ));
        }
    }

    private static String formatDrops(Map<String, Integer> drops) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : drops.entrySet()) {
            int count = Math.max(1, entry.getValue());
            parts.add((count > 1 ? count + "x " : "") + entry.getKey());
        }
        return String.join("§7, §e", parts);
    }

    private static double calculateNativeChanceRelativeBonus(ServerPlayer player, PokemonEntity pokemonEntity, int battlingLevel) {
        double levelBonus = Math.max(0, battlingLevel)
                * Math.max(0.0D, BattleProfessionLootConfig.nativeDropChanceBonusPerBattlingLevel);

        Set<String> defeatedTypes = new HashSet<>();
        pokemonEntity.getPokemon().getTypes().forEach(type -> {
            if (type != null && type.getName() != null) defeatedTypes.add(type.getName().toLowerCase(Locale.ROOT));
        });

        int matchingMasteryLevels = 0;
        for (var entry : ProfessionSubLevelManager.sublevels(player, ProfessionType.BATTLING)) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String[] parts = entry.getKey().split(":", 3);
            if (parts.length != 3 || !"TYPE".equalsIgnoreCase(parts[1])) continue;
            String masteryType = parts[2].toLowerCase(Locale.ROOT);
            int namespace = masteryType.indexOf(':');
            if (namespace >= 0 && namespace + 1 < masteryType.length()) masteryType = masteryType.substring(namespace + 1);
            if (defeatedTypes.contains(masteryType)) {
                matchingMasteryLevels += Math.max(1, Math.min(100, entry.getValue().level));
            }
        }

        double masteryBonus = matchingMasteryLevels
                * Math.max(0.0D, BattleProfessionLootConfig.nativeDropChanceBonusPerMatchingMasteryLevel);
        double maxBonus = Math.max(0.0D, BattleProfessionLootConfig.nativeDropChanceMaxRelativeBonus);
        return Math.max(0.0D, Math.min(maxBonus, levelBonus + masteryBonus));
    }

    private static int countIdentity(List<DropEntry> drops, DropEntry target) {
        int count = 0;
        for (DropEntry drop : drops) if (drop == target) count++;
        return count;
    }

    private static int calculateExtraCopies(int battlingLevel) {
        int minimumLevel = Math.max(0, BattleProfessionLootConfig.extraPokemonDropsMinBattlingLevel);
        if (battlingLevel < minimumLevel) return 0;
        int every = Math.max(1, BattleProfessionLootConfig.extraPokemonDropsCopyEveryLevels);
        int levelsSinceUnlock = Math.max(0, battlingLevel - minimumLevel);
        int copies = 1 + (levelsSinceUnlock / every);
        return Math.max(0, Math.min(copies, BattleProfessionLootConfig.extraPokemonDropsMaxExtraCopies));
    }

    private static boolean rollExtraCopy(int battlingLevel) {
        int minimumLevel = Math.max(0, BattleProfessionLootConfig.extraPokemonDropsMinBattlingLevel);
        int levelsSinceUnlock = Math.max(0, battlingLevel - minimumLevel);
        double chance = BattleProfessionLootConfig.extraPokemonDropsBaseChance
                + (levelsSinceUnlock * BattleProfessionLootConfig.extraPokemonDropsChancePerLevel);
        chance = Math.max(0.0D, Math.min(BattleProfessionLootConfig.extraPokemonDropsMaxChance, chance));
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
