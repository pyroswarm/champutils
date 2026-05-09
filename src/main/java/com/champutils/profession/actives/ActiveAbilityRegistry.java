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
        register(
                new ProspectAbility()
        );
        register(
                new ExcavationAbility()
        );
        register(
                new AutoSmeltBurstAbility()
        );
        register(
                new AutoSmeltToggleAbility()
        );
        register(
                new OreMagnetToggleAbility()
        );
        register(
                new VeinMinerBurstAbility()
        );
        register(
                new MinersFocusAbility()
        );
        register(
                new TreasureSenseAbility()
        );
        register(
                new BlastMineAbility()
        );
        register(
                new StonebreakerAbility()
        );
    }

    public static void register(
            ProfessionActiveAbility ability
    ) {

        if (
                ability == null ||
                        ability.id() == null ||
                        ability.id().isBlank()
        ) {
            return;
        }

        ABILITIES.put(
                normalize(
                        ability.id()
                ),
                ability
        );
    }

    public static boolean use(
            String abilityId,
            ServerPlayer player,
            ItemStack stack
    ) {

        ProfessionActiveAbility ability =
                ABILITIES.get(
                        normalize(
                                abilityId
                        )
                );

        if (ability == null) {
            return false;
        }

        return ability.use(
                player,
                stack
        );
    }

    private static String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value.trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }
}
