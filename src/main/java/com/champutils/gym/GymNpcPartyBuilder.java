package com.champutils.gym;

import com.champutils.badge.BadgeType;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

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


/* =========================
 IMPORTANT:
 REINITIALIZE NPC
========================= */

            npc.initialize(
                    gym.levelCap
            );



/* =========================
 BUILD PARTY
========================= */

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
                                set
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



/* =========================
 APPLY PARTY
========================= */

            npc.setParty(
                    party
            );


/*
 Force refresh
*/
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

            return true;

        }
        catch(Exception e){
            e.printStackTrace();
            return false;
        }

    }



    private static Pokemon createPokemon(
            GymConfig.PokemonSet set
    ){

        try{

            String spec =
                    "species=\"cobblemon:"
                            + set.species.toLowerCase()
                            + "\" level="
                            + set.level;

            return PokemonProperties.Companion
                    .parse(
                            spec
                    )
                    .create();

        }
        catch(Exception e){

            e.printStackTrace();

            return null;
        }

    }

}