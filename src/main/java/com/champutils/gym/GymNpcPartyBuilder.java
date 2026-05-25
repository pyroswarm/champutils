package com.champutils.gym;

import com.champutils.badge.BadgeType;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Builds gym NPC parties.
 *
 * Gym parties now work like the world/guild boss pools:
 * - gyms.json party is treated as a competitive pool when randomizeCompetitiveTeam is true,
 *   when partySize is smaller than the configured pool, or when role/tags are present.
 * - Generated teams always try to lead with setup and finish with an anchor.
 * - Every gym NPC is forced to max Cobblemon AI skill.
 * - Missing IVs default to 31s. Missing EVs default by role so old configs remain usable.
 */
public class GymNpcPartyBuilder {
    private static final Random RANDOM = new Random();
    private static final int MAX_MOVES = 4;

    private static final List<String> SAFE_FALLBACK_MOVES = List.of(
            "earthquake", "thunderbolt", "flamethrower", "icebeam",
            "shadowball", "closecombat", "psychic", "dragonpulse"
    );

    public static boolean applyGymTeam(NPCEntity npc, BadgeType badge) {
        try {
            GymConfig.GymDefinition gym = GymConfig.getGym(badge);
            if (gym == null || gym.party == null || gym.party.isEmpty()) return false;

            boolean debug = gym.debug;
            int level = Math.max(1, Math.min(100, gym.levelCap <= 0 ? 50 : gym.levelCap));
            int partySize = Math.max(1, Math.min(6, gym.partySize <= 0 ? Math.min(6, gym.party.size()) : gym.partySize));

            if (debug) {
                System.out.println("===========================");
                System.out.println("[ChampUtils] DEBUG COMPETITIVE GYM TEAM BUILD " + badge.name());
            }

            npc.initialize(level);

            NPCPartyStore party = new NPCPartyStore(npc);
            List<GymConfig.PokemonSet> team = selectCompetitiveTeam(gym, partySize);

            int slot = 0;
            for (GymConfig.PokemonSet set : team) {
                if (set == null || slot >= 6) continue;
                Pokemon pokemon = createPokemon(set, level, debug);
                if (pokemon == null) continue;
                try { pokemon.heal(); } catch (Exception ignored) {}
                party.set(slot++, pokemon);
            }

            if (slot <= 0) return false;

            party.initialize();
            npc.setParty(party);

            // Hard mode for all gyms. We already built the advanced AI elsewhere; this makes the NPC use it at max skill.
            try { npc.setSkill(5); } catch (Exception ignored) {}

            for (int i = 0; i < 6; i++) {
                try {
                    Pokemon p = party.get(i);
                    if (p != null) p.heal();
                } catch (Exception ignored) {}
            }

            npc.setHealth(npc.getMaxHealth());
            npc.setPersistenceRequired();

            try {
                String name = gym.spawnName != null && !gym.spawnName.isBlank() ? gym.spawnName : gym.leaderName;
                if (name != null && !name.isBlank()) {
                    npc.setCustomName(net.minecraft.network.chat.Component.literal(name));
                    npc.setCustomNameVisible(true);
                }
            } catch (Exception ignored) {}

            System.out.println("[ChampUtils] Applied competitive gym team: " + slot + " Pokemon to " + badge.name());

            if (debug) System.out.println("===========================");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static List<GymConfig.PokemonSet> selectCompetitiveTeam(GymConfig.GymDefinition gym, int partySize) {
        List<GymConfig.PokemonSet> pool = new ArrayList<>();
        for (GymConfig.PokemonSet set : gym.party) if (set != null && set.species != null && !set.species.isBlank()) pool.add(set);
        if (pool.isEmpty()) return pool;

        boolean hasRoleData = pool.stream().anyMatch(p ->
                (p.role != null && !p.role.isBlank()) || (p.tags != null && !p.tags.isEmpty()) || p.weight > 1
        );
        boolean randomize = gym.randomizeCompetitiveTeam != null ? gym.randomizeCompetitiveTeam : (pool.size() > partySize || hasRoleData);
        if (!randomize) return pool.subList(0, Math.min(partySize, pool.size()));

        List<GymConfig.PokemonSet> team = new ArrayList<>();

        // Boss-style pacing: utility pressure first, bulky final answer last.
        addRolePick(team, pool, "lead/setup");
        if (partySize > 2) addRolePick(team, pool, "pivot");
        if (partySize > 3) addRolePick(team, pool, "wallbreaker");
        if (partySize > 4) addRolePick(team, pool, "sweeper");

        Collections.shuffle(pool, RANDOM);
        for (GymConfig.PokemonSet pokemon : pool) {
            if (team.size() >= Math.max(1, partySize - 1)) break;
            if (!team.contains(pokemon) && !isRole(pokemon, "anchor")) team.add(pokemon);
        }

        // Force anchor last when possible.
        if (team.size() < partySize) addRolePick(team, pool, "anchor");
        if (team.stream().noneMatch(p -> isRole(p, "anchor"))) {
            GymConfig.PokemonSet anchor = weightedPick(filterByRole(pool, "anchor"), team);
            if (anchor != null) {
                if (team.size() >= partySize) team.remove(team.size() - 1);
                team.add(anchor);
            }
        }

        for (GymConfig.PokemonSet pokemon : pool) {
            if (team.size() >= partySize) break;
            if (!team.contains(pokemon)) team.add(pokemon);
        }

        // If a setup was picked but shuffled into the wrong slot through fallback, put it first.
        int setupIndex = indexOfRole(team, "lead/setup");
        if (setupIndex > 0) Collections.swap(team, 0, setupIndex);

        int anchorIndex = indexOfRole(team, "anchor");
        if (anchorIndex >= 0 && anchorIndex != team.size() - 1) Collections.swap(team, anchorIndex, team.size() - 1);

        return team.subList(0, Math.min(partySize, team.size()));
    }

    private static void addRolePick(List<GymConfig.PokemonSet> team, List<GymConfig.PokemonSet> pool, String role) {
        GymConfig.PokemonSet pick = weightedPick(filterByRole(pool, role), team);
        if (pick != null) team.add(pick);
    }

    private static List<GymConfig.PokemonSet> filterByRole(List<GymConfig.PokemonSet> pool, String role) {
        List<GymConfig.PokemonSet> matches = new ArrayList<>();
        for (GymConfig.PokemonSet set : pool) if (isRole(set, role)) matches.add(set);
        return matches;
    }

    private static boolean isRole(GymConfig.PokemonSet set, String role) {
        return normalizeRole(set == null ? null : set.role).equals(normalizeRole(role));
    }

    private static int indexOfRole(List<GymConfig.PokemonSet> team, String role) {
        for (int i = 0; i < team.size(); i++) if (isRole(team.get(i), role)) return i;
        return -1;
    }

    private static GymConfig.PokemonSet weightedPick(List<GymConfig.PokemonSet> candidates, List<GymConfig.PokemonSet> alreadyPicked) {
        List<GymConfig.PokemonSet> clean = new ArrayList<>();
        for (GymConfig.PokemonSet p : candidates) if (p != null && !alreadyPicked.contains(p)) clean.add(p);
        if (clean.isEmpty()) return null;
        int total = 0;
        for (GymConfig.PokemonSet p : clean) total += Math.max(1, p.weight);
        int roll = RANDOM.nextInt(Math.max(1, total));
        for (GymConfig.PokemonSet p : clean) {
            roll -= Math.max(1, p.weight);
            if (roll < 0) return p;
        }
        return clean.get(0);
    }

    private static String normalizeRole(String role) {
        String clean = role == null ? "" : role.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (clean.isBlank()) return "sweeper";
        if (clean.equals("lead") || clean.equals("setup") || clean.equals("lead-setup") || clean.equals("hazard") || clean.equals("weather") || clean.equals("screen")) return "lead/setup";
        if (clean.equals("tank") || clean.equals("wall") || clean.equals("stall") || clean.equals("bulky") || clean.equals("closer")) return "anchor";
        if (clean.equals("breaker")) return "wallbreaker";
        if (clean.equals("utility")) return "pivot";
        if (clean.equals("cleaner") || clean.equals("offense")) return "sweeper";
        return clean;
    }

    private static Pokemon createPokemon(GymConfig.PokemonSet set, int defaultLevel, boolean debug) {
        try {
            int level = Math.max(1, Math.min(100, set.level <= 0 ? defaultLevel : set.level));
            Pokemon pokemon = PokemonProperties.Companion
                    .parse("species=\"" + normalizeSpecies(set.species) + "\" level=" + level)
                    .create();

            boolean abilityApplied = applyAbility(pokemon, set.ability);
            boolean natureApplied = applyNature(pokemon, set.nature);
            int learnedMoves = applyMoves(pokemon, set.moves, debug);
            applyIVsOrPerfect(pokemon, set, debug);
            applyEVsOrRoleDefault(pokemon, set, debug);
            boolean heldItemApplied = applyHeldItem(pokemon, set.heldItem);
            pokemon.heal();

            if (debug) {
                System.out.println("Pokemon: " + set.species + " | role=" + normalizeRole(set.role));
                System.out.println("Level: " + level);
                System.out.println("Ability: " + set.ability + (abilityApplied ? " [OK]" : " [FAILED/NONE]"));
                System.out.println("Nature: " + set.nature + (natureApplied ? " [OK]" : " [FAILED/NONE]"));
                System.out.println("Moves learned: " + learnedMoves);
                System.out.println("Held Item: " + set.heldItem + (heldItemApplied ? " [OK]" : " [NONE/FAILED]"));
                System.out.println("------------------");
            }
            return pokemon;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static boolean applyAbility(Pokemon pokemon, String ability) {
        try {
            if (ability == null || ability.isBlank()) return false;
            pokemon.updateAbility(Abilities.INSTANCE.getOrException(cleanKey(ability)).create(false, Priority.NORMAL));
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static boolean applyNature(Pokemon pokemon, String nature) {
        try {
            if (nature == null || nature.isBlank()) return false;
            pokemon.setNature(Natures.INSTANCE.getNature(ResourceLocation.parse("cobblemon:" + cleanKey(nature))));
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static int applyMoves(Pokemon pokemon, List<String> configuredMoves, boolean debug) {
        int learned = 0;
        try { pokemon.getMoveSet().clear(); } catch (Exception ignored) {}

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (configuredMoves != null) {
            for (String move : configuredMoves) {
                String key = cleanMoveKey(move);
                if (!key.isBlank()) candidates.add(key);
            }
        }
        for (String fallback : SAFE_FALLBACK_MOVES) candidates.add(fallback);

        for (String move : candidates) {
            if (learned >= MAX_MOVES) break;
            boolean ok = tryAddMove(pokemon, move);
            if (ok) learned++;
            if (debug) System.out.println("Move: " + move + (ok ? " [OK]" : " [FAILED]"));
        }
        return learned;
    }

    private static boolean tryAddMove(Pokemon pokemon, String moveKey) {
        try {
            if (pokemon == null || moveKey == null || moveKey.isBlank()) return false;
            pokemon.getMoveSet().add(Moves.getByName(moveKey).create());
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static boolean applyHeldItem(Pokemon pokemon, String heldItemId) {
        try {
            if (heldItemId == null || heldItemId.isBlank()) return false;
            ItemStack heldItem = CobblemonHeldItemUtil.createHeldItemStack(heldItemId);
            if (heldItem.isEmpty()) return false;
            pokemon.swapHeldItem(heldItem, false, false);
            return !pokemon.heldItem().isEmpty();
        } catch (Exception ignored) { return false; }
    }

    private static void applyIVsOrPerfect(Pokemon pokemon, GymConfig.PokemonSet set, boolean debug) {
        try {
            var ivs = pokemon.getIvs();
            int hp = set.ivs == null ? 31 : set.ivs.hp;
            int atk = set.ivs == null ? 31 : set.ivs.atk;
            int def = set.ivs == null ? 31 : set.ivs.def;
            int spa = set.ivs == null ? 31 : set.ivs.spa;
            int spd = set.ivs == null ? 31 : set.ivs.spd;
            int spe = set.ivs == null ? 31 : set.ivs.spe;
            ivs.set(Stats.HP, clampIv(hp));
            ivs.set(Stats.ATTACK, clampIv(atk));
            ivs.set(Stats.DEFENCE, clampIv(def));
            ivs.set(Stats.SPECIAL_ATTACK, clampIv(spa));
            ivs.set(Stats.SPECIAL_DEFENCE, clampIv(spd));
            ivs.set(Stats.SPEED, clampIv(spe));
            if (debug) System.out.println("IVs: " + hp + "/" + atk + "/" + def + "/" + spa + "/" + spd + "/" + spe + " [OK]");
        } catch (Exception e) {
            if (debug) e.printStackTrace();
        }
    }

    private static void applyEVsOrRoleDefault(Pokemon pokemon, GymConfig.PokemonSet set, boolean debug) {
        int hp, atk, def, spa, spd, spe;
        if (set.evs != null) {
            hp = set.evs.hp; atk = set.evs.atk; def = set.evs.def; spa = set.evs.spa; spd = set.evs.spd; spe = set.evs.spe;
        } else {
            int[] defaults = defaultEvsForRole(set.role, set.moves);
            hp = defaults[0]; atk = defaults[1]; def = defaults[2]; spa = defaults[3]; spd = defaults[4]; spe = defaults[5];
        }
        applyEVs(pokemon, hp, atk, def, spa, spd, spe, debug);
    }

    private static int[] defaultEvsForRole(String role, List<String> moves) {
        String r = normalizeRole(role);
        boolean special = looksSpecial(moves);
        if (r.equals("anchor")) return new int[]{252, 0, 128, 0, 128, 0};
        if (r.equals("lead/setup") || r.equals("pivot")) return new int[]{252, 0, 4, 0, 0, 252};
        if (special) return new int[]{4, 0, 0, 252, 0, 252};
        return new int[]{4, 252, 0, 0, 0, 252};
    }

    private static boolean looksSpecial(List<String> moves) {
        if (moves == null) return false;
        Set<String> specialHints = new HashSet<>(List.of(
                "flamethrower", "fireblast", "hydropump", "surf", "scald", "icebeam", "blizzard",
                "thunderbolt", "voltswitch", "psychic", "psyshock", "shadowball", "moonblast",
                "energyball", "gigadrain", "dracometeor", "dragonpulse", "flashcannon", "earthpower",
                "sludgebomb", "focusblast", "hurricane"
        ));
        int special = 0;
        int physical = 0;
        for (String move : moves) {
            String key = cleanMoveKey(move);
            if (specialHints.contains(key)) special++;
            else if (!key.isBlank()) physical++;
        }
        return special >= physical;
    }

    private static void applyEVs(Pokemon pokemon, int hp, int atk, int def, int spa, int spd, int spe, boolean debug) {
        try {
            hp = clampEv(hp); atk = clampEv(atk); def = clampEv(def); spa = clampEv(spa); spd = clampEv(spd); spe = clampEv(spe);
            int total = hp + atk + def + spa + spd + spe;
            if (total > 510) {
                double scale = 510D / total;
                hp = (int) (hp * scale); atk = (int) (atk * scale); def = (int) (def * scale);
                spa = (int) (spa * scale); spd = (int) (spd * scale); spe = (int) (spe * scale);
            }
            var evs = pokemon.getEvs();
            evs.set(Stats.HP, hp);
            evs.set(Stats.ATTACK, atk);
            evs.set(Stats.DEFENCE, def);
            evs.set(Stats.SPECIAL_ATTACK, spa);
            evs.set(Stats.SPECIAL_DEFENCE, spd);
            evs.set(Stats.SPEED, spe);
            if (debug) System.out.println("EVs applied: " + hp + "/" + atk + "/" + def + "/" + spa + "/" + spd + "/" + spe + " [OK]");
        } catch (Exception e) {
            if (debug) e.printStackTrace();
        }
    }

    private static int clampIv(int value) { return Math.max(0, Math.min(31, value)); }
    private static int clampEv(int value) { return Math.max(0, Math.min(252, value)); }

    private static String normalizeSpecies(String species) {
        if (species == null || species.isBlank()) return "cobblemon:mewtwo";
        String s = species.trim().toLowerCase(Locale.ROOT);
        if (!s.contains(":")) s = "cobblemon:" + s;
        return s;
    }

    private static String cleanKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("cobblemon:", "").replaceAll("[^a-z0-9_]", "");
    }

    private static String cleanMoveKey(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replace("cobblemon:", "").replaceAll("[^a-z0-9]", "");
    }
}
