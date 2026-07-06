package com.champutils.profession.actives;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ActiveAbilityRegistry {

    private static final Map<String, ProfessionActiveAbility> ABILITIES =
            new HashMap<>();

    private ActiveAbilityRegistry() {
    }

    public static void registerDefaults() {

        ABILITIES.clear();

        // Mining
        register(new ProspectAbility());
        register(new ExcavationAbility());
        register(new AutoSmeltBurstAbility());
        register(new AutoSmeltToggleAbility());
        register(new OreMagnetToggleAbility());
        register(new VeinMinerBurstAbility());
        register(new MinersFocusAbility());
        register(new TreasureSenseAbility());
        register(new BlastMineAbility());
        register(new StonebreakerAbility());

        // Forestry
        register(new TimberBurstAbility());
        register(new LeafstormAbility());
        register(new LumberjackFocusAbility());
        register(new ForestersFocusAbility());
        register(new TreeReplantToggleAbility());

        // Farming
        register(new HarvestWaveAbility());
        register(new AutoReplantToggleAbility());
        register(new GoldenRainAbility());
    }

    public static void register(ProfessionActiveAbility ability) {
        if (ability == null || ability.id() == null || ability.id().isBlank()) {
            return;
        }

        ABILITIES.put(normalize(ability.id()), ability);
    }

    public static boolean use(String abilityId, ServerPlayer player, ItemStack stack) {
        String normalized = normalize(abilityId);
        ProfessionActiveAbility ability = ABILITIES.get(normalized);

        if (ability == null) {
            // Older/generated tool configs sometimes store the effect id instead of the
            // registered active ability id. Accept those ids so existing tools do not
            // become dead actives after config or naming changes.
            ability = switch (normalized) {
                case "tree_replant", "forestry_replant" -> ABILITIES.get("tree_replant_toggle");
                case "auto_replant" -> ABILITIES.get("auto_replant_toggle");
                case "auto_smelt" -> ABILITIES.get("auto_smelt_toggle");
                case "ore_magnet" -> ABILITIES.get("ore_magnet_toggle");
                default -> ABILITIES.get(normalized + "_toggle");
            };
        }

        if (ability == null) {
            return false;
        }

        return ability.use(player, stack);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
