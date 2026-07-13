package com.champutils.breeding;

import com.champutils.profession.ProfessionChunkManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionType;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Breeding profession rewards and the server's special Ditto + Ditto pool.
 * All benefit scaling intentionally soft-caps at profession level 100.
 */
public final class BreedingProfessionService {
    private static final Random RANDOM = new Random();
    private static final List<Stat> PERMANENT_STATS = List.of(
            Stats.HP, Stats.ATTACK, Stats.DEFENCE,
            Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
    );
    private static final Set<String> EXCLUDED_LABELS = Set.of(
            "legendary", "mythical", "ultrabeast", "paradox"
    );

    private BreedingProfessionService() {}

    public enum RarityTier {
        COMMON("Common"),
        UNCOMMON("Uncommon"),
        RARE("Rare"),
        VERY_RARE("Very Rare");

        private final String displayName;

        RarityTier(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public record DittoSelection(Species species, RarityTier rarity) {}

    public static DittoSelection selectDittoOffspring(ServerPlayer player) {
        List<Species> pool = new ArrayList<>();
        for (Species species : PokemonSpecies.getImplemented()) {
            if (isEligibleDittoPoolSpecies(species)) pool.add(species);
        }
        if (pool.isEmpty()) {
            throw new IllegalStateException("No eligible base-stage Pokémon were found for Ditto breeding.");
        }

        // Stable ordering makes the same config deterministic apart from the random roll.
        pool.sort(Comparator.comparing(species -> species.getResourceIdentifier().toString()));
        int level = benefitLevel(player);
        double total = 0.0D;
        List<Double> weights = new ArrayList<>(pool.size());
        for (Species species : pool) {
            double weight = weightedChance(rarityFor(species), level);
            weights.add(weight);
            total += weight;
        }

        double roll = RANDOM.nextDouble() * Math.max(0.000001D, total);
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0.0D) {
                Species selected = pool.get(i);
                return new DittoSelection(selected, rarityFor(selected));
            }
        }
        Species selected = pool.get(pool.size() - 1);
        return new DittoSelection(selected, rarityFor(selected));
    }

    public static boolean isEligibleDittoPoolSpecies(Species species) {
        if (species == null || !species.getImplemented()) return false;
        if (species.getPreEvolution() != null) return false;
        String id = species.getResourceIdentifier().toString();
        if (BreedingEggData.EGG_SPECIES.equals(id) || "cobblemon:ditto".equals(id)) return false;
        for (String label : species.getLabels()) {
            if (EXCLUDED_LABELS.contains(normalize(label))) return false;
        }
        return true;
    }

    public static RarityTier rarityFor(Species species) {
        if (species == null) return RarityTier.COMMON;
        Set<String> labels = species.getLabels().stream().map(BreedingProfessionService::normalize).collect(java.util.stream.Collectors.toSet());
        int catchRate = species.getCatchRate();
        int baseStatTotal = species.getBaseStats().values().stream().mapToInt(Integer::intValue).sum();
        int baseExperience = species.getBaseExperienceYield();

        if (labels.contains("starter") || labels.contains("fossil") || labels.contains("pseudolegendary")
                || catchRate < 60 || baseStatTotal >= 500 || baseExperience >= 220) {
            return RarityTier.VERY_RARE;
        }
        if (catchRate < 120 || baseStatTotal >= 450 || baseExperience >= 170) return RarityTier.RARE;
        if (catchRate < 190 || baseStatTotal >= 380 || baseExperience >= 120) return RarityTier.UNCOMMON;
        return RarityTier.COMMON;
    }

    public static RarityTier rarityFor(Pokemon pokemon) {
        return pokemon == null ? RarityTier.COMMON : rarityFor(pokemon.getSpecies());
    }

    public static void applyExtraPerfectIv(ServerPlayer player, Pokemon child) {
        if (player == null || child == null) return;
        double chance = scaledPercent(BreedingConfig.get().breedingLevel100ExtraPerfectIvChancePercent, player);
        if (RANDOM.nextDouble() >= chance) return;

        List<Stat> available = new ArrayList<>();
        for (Stat stat : PERMANENT_STATS) {
            if (child.getIvs().getOrDefault(stat) < 31) available.add(stat);
        }
        if (available.isEmpty()) return;
        Stat chosen = available.get(RANDOM.nextInt(available.size()));
        child.getIvs().set(chosen, 31);
    }

    public static double hiddenAbilityBonus(ServerPlayer player) {
        return scaledPercent(BreedingConfig.get().breedingLevel100HiddenAbilityBonusPercent, player);
    }

    public static void rollDittoAbility(ServerPlayer player, Pokemon child) {
        List<PotentialAbility> common = new ArrayList<>();
        List<PotentialAbility> hidden = new ArrayList<>();
        for (PotentialAbility potential : child.getForm().getAbilities()) {
            if (potential instanceof HiddenAbility) hidden.add(potential);
            else common.add(potential);
        }
        if (common.isEmpty() && hidden.isEmpty()) return;

        double hiddenChance = Math.max(0.0D, BreedingConfig.get().dittoEggBaseHiddenAbilityChancePercent / 100.0D)
                + hiddenAbilityBonus(player);
        PotentialAbility chosen;
        if (!hidden.isEmpty() && RANDOM.nextDouble() < Math.min(1.0D, hiddenChance)) {
            chosen = hidden.get(RANDOM.nextInt(hidden.size()));
        } else if (!common.isEmpty()) {
            chosen = common.get(RANDOM.nextInt(common.size()));
        } else {
            chosen = hidden.get(RANDOM.nextInt(hidden.size()));
        }
        child.updateAbility(chosen.getTemplate().create(false, chosen.getPriority()));
    }

    /** Additional independent roll that increases the final shiny chance by at most 2% relative. */
    public static boolean rollShinyProfessionBonus(ServerPlayer player, int denominator, int standardRolls) {
        if (player == null || denominator <= 0 || standardRolls <= 0) return false;
        double standardChance = 1.0D - Math.pow(1.0D - (1.0D / denominator), standardRolls);
        double relativeBonus = scaledPercent(BreedingConfig.get().breedingLevel100ShinyRelativeBonusPercent, player);
        return RANDOM.nextDouble() < Math.min(1.0D, standardChance * relativeBonus);
    }

    public static int effectiveCooldownSeconds(ServerPlayer player) {
        int base = Math.max(0, BreedingConfig.get().breedingCooldownSeconds);
        int level = player == null ? 0 : Math.max(0, Math.min(100, ProfessionManager.getLevel(player, ProfessionType.BREEDING)));
        double reduction = level * BreedingConfig.get().cooldownReductionPerBreedingLevelPercent / 100.0D;
        return (int)Math.ceil(base * Math.max(0.0D, 1.0D - reduction));
    }

    public static void rewardHatch(ServerPlayer player, Pokemon hatchling) {
        if (player == null || hatchling == null) return;
        RarityTier rarity = rarityFor(hatchling);
        ProfessionManager.addXp(player, ProfessionType.BREEDING, xpFor(rarity));
        ProfessionChunkManager.addChunk(player, rollChunk(player), 1, true);
    }

    public static int xpFor(RarityTier rarity) {
        BreedingConfig.Values config = BreedingConfig.get();
        return switch (rarity == null ? RarityTier.COMMON : rarity) {
            case COMMON -> config.hatchXpCommon;
            case UNCOMMON -> config.hatchXpUncommon;
            case RARE -> config.hatchXpRare;
            case VERY_RARE -> config.hatchXpVeryRare;
        };
    }

    public static String rollChunk(ServerPlayer player) {
        BreedingConfig.Values config = BreedingConfig.get();
        double progress = levelProgress(player);
        List<String> keys = List.of("COBBLESTONE", "COPPER", "IRON", "GOLD", "DIAMOND", "NETHERITE");
        double total = 0.0D;
        List<Double> weights = new ArrayList<>(keys.size());
        for (String key : keys) {
            double low = nonNegative(config.hatchChunkWeightsLevel1.get(key));
            double high = nonNegative(config.hatchChunkWeightsLevel100.get(key));
            double weight = low + ((high - low) * progress);
            weights.add(Math.max(0.0D, weight));
            total += Math.max(0.0D, weight);
        }
        if (total <= 0.0D) return "COBBLESTONE";
        double roll = RANDOM.nextDouble() * total;
        for (int i = 0; i < keys.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0.0D) return keys.get(i);
        }
        return "COBBLESTONE";
    }

    private static double weightedChance(RarityTier tier, int level) {
        BreedingConfig.Values config = BreedingConfig.get();
        double base = switch (tier) {
            case COMMON -> config.dittoPoolCommonWeight;
            case UNCOMMON -> config.dittoPoolUncommonWeight;
            case RARE -> config.dittoPoolRareWeight;
            case VERY_RARE -> config.dittoPoolVeryRareWeight;
        };
        double level100Multiplier = switch (tier) {
            case COMMON -> config.dittoPoolCommonLevel100Multiplier;
            case UNCOMMON -> config.dittoPoolUncommonLevel100Multiplier;
            case RARE -> config.dittoPoolRareLevel100Multiplier;
            case VERY_RARE -> config.dittoPoolVeryRareLevel100Multiplier;
        };
        double progress = Math.max(0.0D, Math.min(1.0D, (level - 1) / 99.0D));
        return Math.max(0.000001D, base * (1.0D + ((level100Multiplier - 1.0D) * progress)));
    }

    private static double scaledPercent(double level100Percent, ServerPlayer player) {
        return Math.max(0.0D, level100Percent) / 100.0D * levelProgress(player);
    }

    private static double levelProgress(ServerPlayer player) {
        return Math.max(0.0D, Math.min(1.0D, (benefitLevel(player) - 1) / 99.0D));
    }

    private static int benefitLevel(ServerPlayer player) {
        return player == null ? 1 : ProfessionManager.getBenefitLevel(player, ProfessionType.BREEDING);
    }

    private static double nonNegative(Double value) {
        return value == null ? 0.0D : Math.max(0.0D, value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
