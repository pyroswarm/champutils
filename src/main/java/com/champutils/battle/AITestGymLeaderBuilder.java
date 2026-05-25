package com.champutils.battle;

import com.champutils.trainer.ChampTrainerProtectionManager;
import com.champutils.trainer.ChampTrainerSpawner;
import com.champutils.util.CobblemonHeldItemUtil;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

public final class AITestGymLeaderBuilder {
    public static final String TAG = "champutils_ai_test_gym";

    private AITestGymLeaderBuilder() {}

    public static NPCEntity spawn(ServerLevel level, Vec3 pos, float yaw) {
        NPCEntity npc = ChampTrainerSpawner.createProtectedNpc(level, pos, yaw, "AI Test Gym Leader", "");
        if (npc == null) return null;
        applyTeam(npc);
        npc.addTag(TAG);
        ChampTrainerProtectionManager.track(npc, "ai-test-gym", ChampTrainerSpawner.TrainerKind.GYM, pos, yaw);
        return npc;
    }

    public static boolean applyTeam(NPCEntity npc) {
        if (npc == null) return false;
        try {
            npc.initialize(100);
            NPCPartyStore party = new NPCPartyStore(npc);
            party.set(0, pokemon("skarmory", 100, "sturdy", "impish", "rocky_helmet",
                    evs(252, 0, 232, 0, 0, 24), List.of("stealthrock", "spikes", "roost", "bravebird")));
            party.set(1, pokemon("dragonite", 100, "multiscale", "adamant", "lum_berry",
                    evs(0, 252, 0, 0, 4, 252), List.of("dragondance", "extremespeed", "earthquake", "dragonclaw")));
            party.set(2, pokemon("umbreon", 100, "synchronize", "calm", "leftovers",
                    evs(252, 0, 4, 0, 252, 0), List.of("toxic", "protect", "wish", "foulplay")));
            party.initialize();
            npc.setParty(party);
            npc.setSkill(5);
            npc.setCustomName(Component.literal("AI Test Gym Leader"));
            npc.setCustomNameVisible(true);
            npc.setHealth(npc.getMaxHealth());
            npc.setPersistenceRequired();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static Pokemon pokemon(String species, int level, String ability, String nature, String heldItem, int[] evs, List<String> moves) {
        Pokemon pokemon = PokemonProperties.Companion
                .parse("species=\"cobblemon:" + clean(species) + "\" level=" + level)
                .create();
        try { pokemon.updateAbility(Abilities.INSTANCE.getOrException(clean(ability)).create(false, Priority.NORMAL)); } catch (Exception ignored) {}
        try { pokemon.setNature(Natures.INSTANCE.getNature(ResourceLocation.parse("cobblemon:" + clean(nature)))); } catch (Exception ignored) {}
        try {
            pokemon.getMoveSet().clear();
            for (String move : moves) pokemon.getMoveSet().add(Moves.getByName(clean(move)).create());
        } catch (Exception ignored) {}
        applyPerfectIvs(pokemon);
        applyEvs(pokemon, evs);
        applyHeldItem(pokemon, heldItem);
        try { pokemon.heal(); } catch (Exception ignored) {}
        return pokemon;
    }

    private static int[] evs(int hp, int atk, int def, int spa, int spd, int spe) {
        return new int[]{hp, atk, def, spa, spd, spe};
    }

    private static void applyPerfectIvs(Pokemon pokemon) {
        try {
            var ivs = pokemon.getIvs();
            ivs.set(Stats.HP, 31);
            ivs.set(Stats.ATTACK, 31);
            ivs.set(Stats.DEFENCE, 31);
            ivs.set(Stats.SPECIAL_ATTACK, 31);
            ivs.set(Stats.SPECIAL_DEFENCE, 31);
            ivs.set(Stats.SPEED, 31);
        } catch (Exception ignored) {}
    }

    private static void applyEvs(Pokemon pokemon, int[] evsArray) {
        try {
            var evs = pokemon.getEvs();
            evs.set(Stats.HP, clampEv(evsArray[0]));
            evs.set(Stats.ATTACK, clampEv(evsArray[1]));
            evs.set(Stats.DEFENCE, clampEv(evsArray[2]));
            evs.set(Stats.SPECIAL_ATTACK, clampEv(evsArray[3]));
            evs.set(Stats.SPECIAL_DEFENCE, clampEv(evsArray[4]));
            evs.set(Stats.SPEED, clampEv(evsArray[5]));
        } catch (Exception ignored) {}
    }

    private static int clampEv(int value) {
        return Math.max(0, Math.min(252, value));
    }

    private static void applyHeldItem(Pokemon pokemon, String itemId) {
        try {
            ItemStack item = CobblemonHeldItemUtil.createHeldItemStack(itemId);
            if (!item.isEmpty()) pokemon.swapHeldItem(item, false, false);
        } catch (Exception ignored) {}
    }

    private static String clean(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
    }
}
