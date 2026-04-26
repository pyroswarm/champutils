package com.champutils.gym;

import com.champutils.badge.BadgeType;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import java.lang.reflect.Method;

public class GymNpcPartyBuilder {

    public static boolean applyGymTeam(
            NPCEntity npc,
            BadgeType badge
    ){

        try{

            GymConfig.GymDefinition gym =
                    GymConfig.getGym(
                            badge
                    );

            if(
                    gym == null
                            ||
                            gym.party == null
                            ||
                            gym.party.isEmpty()
            ){
                return false;
            }

            boolean debug =
                    gym.debug;


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
 INIT NPC
========================= */

            npc.initialize(
                    gym.levelCap
            );


            NPCPartyStore party =
                    new NPCPartyStore(
                            npc
                    );

            int slot = 0;

            for(
                    GymConfig.PokemonSet set :
                    gym.party
            ){

                Pokemon pokemon =
                        createPokemon(
                                set,
                                debug
                        );

                if(
                        pokemon != null
                ){
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
 BUILD POKEMON
========================= */

    private static Pokemon createPokemon(
            GymConfig.PokemonSet set,
            boolean debug
    ){

        try{

            String spec =
                    "species=\"cobblemon:"
                            + set.species.toLowerCase()
                            + "\" level="
                            + set.level;


            Pokemon pokemon =
                    PokemonProperties.Companion
                            .parse(
                                    spec
                            )
                            .create();



/* =========================
 ABILITY
(disabled for stability)
========================= */

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
                                + " [DISABLED]"
                );
            }



/* =========================
 NATURE
========================= */

            boolean natureApplied =
                    false;

            if(
                    set.nature != null
                            &&
                            !set.nature.isBlank()
            ){

                try{

                    Class<?> naturesClass =
                            Class.forName(
                                    "com.cobblemon.mod.common.api.pokemon.Natures"
                            );

                    Method get =
                            naturesClass.getMethod(
                                    "getNature",
                                    String.class
                            );

                    Object nature =
                            get.invoke(
                                    null,
                                    set.nature
                            );

                    if(
                            nature != null
                    ){

                        Method setNature =
                                pokemon.getClass()
                                        .getMethod(
                                                "setNature",
                                                nature.getClass()
                                        );

                        setNature.invoke(
                                pokemon,
                                nature
                        );

                        natureApplied=true;
                    }

                }
                catch(Exception ignored){}



                if(
                        !natureApplied
                ){

                    try{

                        Method setNature =
                                pokemon.getClass()
                                        .getMethod(
                                                "setNature",
                                                String.class
                                        );

                        setNature.invoke(
                                pokemon,
                                set.nature
                        );

                        natureApplied=true;

                    }
                    catch(Exception ignored){}
                }

            }


            if(debug){

                System.out.println(
                        "Nature: "
                                + set.nature
                                + (
                                natureApplied
                                        ?
                                        " [OK]"
                                        :
                                        " [FAILED]"
                        )
                );

                System.out.println(
                        "Held Item: "
                                + set.heldItem
                );
            }



/* =========================
 MOVESETS
========================= */

            if(
                    set.moves != null
                            &&
                            !set.moves.isEmpty()
            ){

                try{

                    Object moveSet =
                            pokemon.getClass()
                                    .getMethod(
                                            "getMoveSet"
                                    )
                                    .invoke(
                                            pokemon
                                    );

                    Class<?> movesClass =
                            Class.forName(
                                    "com.cobblemon.mod.common.api.moves.Moves"
                            );

                    Method getByName =
                            movesClass.getMethod(
                                    "getByName",
                                    String.class
                            );


                    try{

                        moveSet.getClass()
                                .getMethod(
                                        "clear"
                                )
                                .invoke(
                                        moveSet
                                );

                    }
                    catch(Exception ignored){}


                    for(
                            String moveName :
                            set.moves
                    ){

                        boolean learned =
                                false;

                        try{

                            Object moveTemplate =
                                    getByName.invoke(
                                            null,
                                            moveName
                                    );

                            if(
                                    moveTemplate != null
                            ){

                                Object move =
                                        moveTemplate.getClass()
                                                .getMethod(
                                                        "create"
                                                )
                                                .invoke(
                                                        moveTemplate
                                                );


                                Method add =
                                        moveSet.getClass()
                                                .getMethod(
                                                        "add",
                                                        move.getClass()
                                                );

                                add.invoke(
                                        moveSet,
                                        move
                                );

                                learned=true;
                            }

                        }
                        catch(Exception ignored){}


                        if(debug){

                            System.out.println(
                                    "Move: "
                                            + moveName
                                            + (
                                            learned
                                                    ?
                                                    " [OK]"
                                                    :
                                                    " [FAILED]"
                                    )
                            );
                        }

                    }

                }
                catch(Exception e){
                    e.printStackTrace();
                }

            }



/* =========================
 DEBUG IVS
========================= */

            if(
                    debug
                            &&
                            set.ivs != null
            ){

                System.out.println(
                        "IVs: "
                                + set.ivs.hp + "/"
                                + set.ivs.atk + "/"
                                + set.ivs.def + "/"
                                + set.ivs.spa + "/"
                                + set.ivs.spd + "/"
                                + set.ivs.spe
                );
            }



/* =========================
 DEBUG EVS
========================= */

            if(
                    debug
                            &&
                            set.evs != null
            ){

                System.out.println(
                        "EVs: "
                                + set.evs.hp + "/"
                                + set.evs.atk + "/"
                                + set.evs.def + "/"
                                + set.evs.spa + "/"
                                + set.evs.spd + "/"
                                + set.evs.spe
                );
            }


            if(debug){

                System.out.println(
                        "------------------"
                );
            }

            return pokemon;

        }
        catch(Exception e){

            e.printStackTrace();

            return null;
        }

    }

}