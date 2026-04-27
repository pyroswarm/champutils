package com.champutils.gym;

import com.champutils.badge.BadgeType;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

public class GymNpcPartyBuilder {

    public static boolean applyGymTeam(
            NPCEntity npc,
            BadgeType badge
    ){

        try{

            System.out.println(
                    "[ChampUtils] Running applyGymTeam..."
            );

            GymConfig.GymDefinition gym=
                    GymConfig.getGym(
                            badge
                    );

            if(
                    gym==null ||
                            gym.party==null ||
                            gym.party.isEmpty()
            ){
                return false;
            }

            boolean debug=gym.debug;

            if(debug){

                System.out.println(
                        "=============================="
                );

                System.out.println(
                        "[ChampUtils] DEBUG TEAM BUILD "
                                + badge.name()
                );
            }


/* =========================
 RESET NPC
========================= */

            npc.initialize(
                    gym.levelCap
            );

            NPCPartyStore party=
                    new NPCPartyStore(
                            npc
                    );

            int slot=0;


/* =========================
 BUILD TEAM
========================= */

            for(
                    GymConfig.PokemonSet set :
                    gym.party
            ){

                Pokemon pokemon=
                        createPokemon(
                                set,
                                debug
                        );

                if(
                        pokemon!=null
                ){

                    try{
                        pokemon.heal();
                    }
                    catch(Exception ignored){}

                    party.set(
                            slot++,
                            pokemon
                    );
                }

            }

            party.initialize();

            npc.setParty(
                    party
            );


/* =========================
 FORCE HEAL ENTIRE PARTY
========================= */

            try{

                for(
                        int i=0;
                        i<6;
                        i++
                ){

                    Pokemon p=
                            party.get(i);

                    if(
                            p!=null
                    ){
                        p.heal();
                    }
                }

            }catch(Exception ignored){}


            npc.setHealth(
                    npc.getMaxHealth()
            );

            npc.setPersistenceRequired();

            System.out.println(
                    "[ChampUtils] Applied "
                            + slot
                            + " Pokemon to "
                            + badge.name()
            );

            if(debug){

                System.out.println(
                        "=============================="
                );
            }

            return true;

        }
        catch(Exception e){

            e.printStackTrace();
            return false;
        }

    }





/* =========================
 CREATE POKEMON
========================= */

    private static Pokemon createPokemon(
            GymConfig.PokemonSet set,
            boolean debug
    ){

        try{

            Pokemon pokemon=
                    PokemonProperties.Companion
                            .parse(
                                    "species=\"cobblemon:"
                                            + set.species.toLowerCase()
                                            + "\" level="
                                            + set.level
                            )
                            .create();


/* =========================
 ABILITY
========================= */

            boolean abilityApplied=false;

            try{

                if(
                        set.ability!=null &&
                                !set.ability.isBlank()
                ){

                    pokemon.updateAbility(
                            Abilities.INSTANCE
                                    .getOrException(
                                            set.ability
                                                    .toLowerCase()
                                                    .replaceAll(
                                                            "[^a-z0-9_]",
                                                            ""
                                                    )
                                    )
                                    .create(
                                            false,
                                            Priority.NORMAL
                                    )
                    );

                    abilityApplied=true;
                }

            }
            catch(Exception ignored){}


            if(debug){

                System.out.println(
                        "Pokemon: "
                                + set.species
                );

                System.out.println(
                        "Level: "
                                + set.level
                );

                System.out.println(
                        "Ability: "
                                + set.ability
                                + " "
                                + (
                                abilityApplied ?
                                        "[OK]" :
                                        "[FAILED]"
                        )
                );
            }


/* =========================
 NATURE
========================= */

            boolean natureApplied=false;

            try{

                if(
                        set.nature!=null &&
                                !set.nature.isBlank()
                ){

                    pokemon.setNature(
                            Natures.INSTANCE.getNature(
                                    net.minecraft.resources.ResourceLocation.parse(
                                            "cobblemon:"
                                                    + set.nature.toLowerCase()
                                    )
                            )
                    );

                    natureApplied=true;
                }

            }
            catch(Exception ignored){}

            if(debug){

                System.out.println(
                        "Nature: "
                                + set.nature
                                + " "
                                + (
                                natureApplied?
                                        "[OK]":
                                        "[FAILED]"
                        )
                );
            }



/* =========================
 HELD ITEMS DISABLED
========================= */

            if(debug){
                System.out.println(
                        "Held Item: disabled"
                );
            }



/* =========================
 MOVES
========================= */

            try{

                if(
                        set.moves!=null &&
                                !set.moves.isEmpty()
                ){

                    pokemon.getMoveSet().clear();

                    for(
                            String move :
                            set.moves
                    ){

                        boolean learned=false;

                        try{

                            pokemon.getMoveSet().add(
                                    Moves.getByName(
                                            move
                                    ).create()
                            );

                            learned=true;

                        }catch(Exception ignored){}

                        if(debug){

                            System.out.println(
                                    "Move: "
                                            + move
                                            + " "
                                            + (
                                            learned ?
                                                    "[OK]" :
                                                    "[FAILED]"
                                    )
                            );
                        }

                    }

                }

            }catch(Exception ignored){}



/* =========================
 IVs
========================= */

            applyIVs(
                    pokemon,
                    set,
                    debug
            );



/* =========================
 EVs DEBUG ONLY FOR NOW
========================= */

            if(
                    debug &&
                            set.evs!=null
            ){

                System.out.println(
                        "EVs (not applied yet): "
                                + set.evs.hp+"/"
                                + set.evs.atk+"/"
                                + set.evs.def+"/"
                                + set.evs.spa+"/"
                                + set.evs.spd+"/"
                                + set.evs.spe
                );
            }


            if(debug){

                System.out.println(
                        "-----------------------"
                );
            }


            return pokemon;

        }
        catch(Exception e){

            e.printStackTrace();
            return null;
        }

    }




/* =========================
 IV APPLICATION
========================= */

    private static void applyIVs(
            Pokemon pokemon,
            GymConfig.PokemonSet set,
            boolean debug
    ){

        if(
                set.ivs==null
        ){
            return;
        }

        try{

            var ivs=
                    pokemon.getIvs();


            ivs.set(
                    Stats.HP,
                    Integer.valueOf(
                            set.ivs.hp
                    )
            );

            ivs.set(
                    Stats.ATTACK,
                    Integer.valueOf(
                            set.ivs.atk
                    )
            );

            ivs.set(
                    Stats.DEFENCE,
                    Integer.valueOf(
                            set.ivs.def
                    )
            );

            ivs.set(
                    Stats.SPECIAL_ATTACK,
                    Integer.valueOf(
                            set.ivs.spa
                    )
            );

            ivs.set(
                    Stats.SPECIAL_DEFENCE,
                    Integer.valueOf(
                            set.ivs.spd
                    )
            );

            ivs.set(
                    Stats.SPEED,
                    Integer.valueOf(
                            set.ivs.spe
                    )
            );


            /* force stat refresh */
            pokemon.heal();


            if(debug){

                System.out.println(
                        "IVs: "
                                + set.ivs.hp+"/"
                                + set.ivs.atk+"/"
                                + set.ivs.def+"/"
                                + set.ivs.spa+"/"
                                + set.ivs.spd+"/"
                                + set.ivs.spe
                                + " [OK]"
                );
            }

        }
        catch(Exception e){

            if(debug){

                e.printStackTrace();

                System.out.println(
                        "IVs [FAILED]"
                );
            }
        }

    }

}