package com.champutils.profile;

import com.champutils.buff.BuffContext;
import com.champutils.buff.BuffManager;
import com.champutils.buff.BuffType;
import com.champutils.xplock.XpLockManager;
import com.champutils.battle.BattleContextManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionTrinketManager;
import com.champutils.profession.ProfessionType;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.ExperienceGainedEvent;
import com.cobblemon.mod.common.api.pokemon.experience.BattleExperienceSource;
import net.minecraft.server.level.ServerPlayer;

/** Applies equipped-title Pokemon XP bonuses at the final Cobblemon XP grant event. */
public final class PokemonExperienceBuffListener {
    private static boolean registered = false;

    private PokemonExperienceBuffListener() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(event -> {
            if (!(event instanceof ExperienceGainedEvent.Pre pre)) return;
            if (!(pre.getSource() instanceof BattleExperienceSource)) return;
            if (pre.getExperience() <= 0) return;
            if (XpLockManager.isLocked(pre.getPokemon())) {
                pre.setExperience(0);
                return;
            }
            ServerPlayer player = pre.getPokemon().getOwnerPlayer();
            if (player == null) return;
            double bonus = BuffManager.getTotalBuff(
                    BuffContext.builder(player, BuffContext.Source.NPC_BATTLE).pokemon(pre.getPokemon()).build(),
                    BuffType.POKEMON_XP
            );
            int battlingLevel = Math.max(1, ProfessionManager.getLevel(player, ProfessionType.BATTLING));
            double battlingBonus = Math.max(0, Math.min(100, battlingLevel)) / 100.0D;
            BattleContextManager.BattleType battleType = BattleContextManager.getContext(player.getUUID());
            double pvpBonus = battleType == BattleContextManager.BattleType.RANKED
                    ? 2.0D
                    : battleType == BattleContextManager.BattleType.CASUAL ? 1.0D : 0.0D;
            double trinketBonus = ProfessionTrinketManager.pokemonXpBonus(player);
            double totalBonus = Math.max(0.0D, bonus) + battlingBonus + pvpBonus + trinketBonus;
            if (totalBonus <= 0.0D) return;
            int boosted = (int) Math.round(pre.getExperience() * (1.0D + totalBonus));
            pre.setExperience(Math.max(pre.getExperience(), boosted));
        });
    }
}
