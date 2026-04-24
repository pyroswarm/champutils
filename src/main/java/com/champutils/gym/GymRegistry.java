package com.champutils.gym;

import com.champutils.badge.BadgeType;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GymRegistry {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private static final File FILE =
            new File(
                    "config/champutils/gymleaders.json"
            );


    private static final Map<UUID, BadgeType> GYMS =
            new HashMap<>();



/* =========================
 LOAD
========================= */

    static{
        load();
    }


    public static void load(){

        try{

            if(!FILE.exists()){

                save();
                return;
            }

            try(
                    FileReader reader=
                            new FileReader(FILE)
            ){

                SaveData raw=
                        GSON.fromJson(
                                reader,
                                SaveData.class
                        );

                GYMS.clear();

                if(
                        raw != null &&
                                raw.gyms != null
                ){

                    for(
                            Map.Entry<String,String> entry :
                            raw.gyms.entrySet()
                    ){

                        try{

                            UUID uuid=
                                    UUID.fromString(
                                            entry.getKey()
                                    );

                            BadgeType badge=
                                    BadgeType.fromString(
                                            entry.getValue()
                                    );

                            if(
                                    badge!=null
                            ){
                                GYMS.put(
                                        uuid,
                                        badge
                                );
                            }

                        }catch(Exception ignored){}
                    }
                }

            }

        }catch(Exception e){
            e.printStackTrace();
        }

    }



/* =========================
 SAVE
========================= */

    public static void save(){

        try(
                FileWriter writer=
                        new FileWriter(
                                FILE
                        )
        ){

            SaveData raw=
                    new SaveData();

            for(
                    Map.Entry<UUID,BadgeType> entry :
                    GYMS.entrySet()
            ){

                raw.gyms.put(
                        entry.getKey().toString(),
                        entry.getValue().name()
                );
            }

            GSON.toJson(
                    raw,
                    writer
            );

        }catch(Exception e){
            e.printStackTrace();
        }

    }



/* =========================
 REGISTRY
========================= */

    public static void bindGym(
            UUID npcUUID,
            BadgeType badge
    ){

        GYMS.put(
                npcUUID,
                badge
        );

        save();
    }


    public static void unbindBadge(
            BadgeType badge
    ){

        UUID remove=null;

        for(
                Map.Entry<UUID,BadgeType> entry :
                GYMS.entrySet()
        ){

            if(
                    entry.getValue()==badge
            ){
                remove=
                        entry.getKey();

                break;
            }
        }

        if(remove!=null){

            GYMS.remove(
                    remove
            );

            save();
        }
    }


    public static BadgeType getBadgeForNpc(
            UUID npcUUID
    ){

        return GYMS.get(
                npcUUID
        );
    }


    public static boolean isGymNpc(
            UUID npcUUID
    ){

        return GYMS.containsKey(
                npcUUID
        );
    }


    public static Map<UUID,BadgeType> getAllGyms(){

        return new HashMap<>(
                GYMS
        );
    }



    /* ========================= */

    private static class SaveData{

        Map<String,String> gyms=
                new HashMap<>();
    }

}