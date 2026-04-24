package com.champutils.badge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import java.util.*;

public class BadgeManager {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private static final Map<UUID, BadgeData> CACHE =
            new HashMap<>();

    private static final File BADGE_FOLDER =
            new File(
                    "config/champutils/playerbadges"
            );


/* =========================
 INIT
========================= */

    static{

        if(!BADGE_FOLDER.exists()){
            BADGE_FOLDER.mkdirs();
        }

    }



/* =========================
 FILE
========================= */

    private static File getPlayerFile(
            UUID uuid
    ){

        return new File(
                BADGE_FOLDER,
                uuid.toString()+".json"
        );
    }



/* =========================
 LOAD
========================= */

    public static BadgeData loadPlayer(
            UUID uuid
    ){

        if(
                CACHE.containsKey(
                        uuid
                )
        ){
            return CACHE.get(
                    uuid
            );
        }


        File file =
                getPlayerFile(
                        uuid
                );


        if(
                !file.exists()
        ){

            BadgeData data =
                    new BadgeData();

            savePlayer(
                    uuid,
                    data
            );

            CACHE.put(
                    uuid,
                    data
            );

            return data;
        }


        try(
                FileReader reader =
                        new FileReader(
                                file
                        )
        ){

            SaveData raw =
                    GSON.fromJson(
                            reader,
                            SaveData.class
                    );

            BadgeData data =
                    new BadgeData();


            if(
                    raw != null &&
                            raw.badges != null
            ){

                Set<BadgeType> loaded =
                        new HashSet<>();

                for(
                        String badge :
                        raw.badges
                ){

                    BadgeType parsed =
                            BadgeType.fromString(
                                    badge
                            );

                    if(
                            parsed != null
                    ){
                        loaded.add(
                                parsed
                        );
                    }
                }

                data.setBadges(
                        loaded
                );
            }


            CACHE.put(
                    uuid,
                    data
            );

            return data;

        }
        catch(Exception e){

            e.printStackTrace();

            BadgeData data =
                    new BadgeData();

            CACHE.put(
                    uuid,
                    data
            );

            return data;
        }

    }



/* =========================
 SAVE
========================= */

    public static void savePlayer(
            UUID uuid,
            BadgeData data
    ){

        try(
                FileWriter writer =
                        new FileWriter(
                                getPlayerFile(
                                        uuid
                                )
                        )
        ){

            SaveData raw =
                    new SaveData();

            for(
                    BadgeType badge :
                    data.getBadges()
            ){

                raw.badges.add(
                        badge.name()
                );
            }

            GSON.toJson(
                    raw,
                    writer
            );

        }
        catch(Exception e){
            e.printStackTrace();
        }

    }



/* =========================
 ACCESS
========================= */

    public static boolean hasBadge(
            ServerPlayer player,
            BadgeType badge
    ){

        return loadPlayer(
                player.getUUID()
        ).hasBadge(
                badge
        );
    }



    public static int getBadgeCount(
            ServerPlayer player
    ){

        return loadPlayer(
                player.getUUID()
        ).getBadgeCount();
    }



    public static Set<BadgeType> getBadges(
            ServerPlayer player
    ){

        return new HashSet<>(
                loadPlayer(
                        player.getUUID()
                ).getBadges()
        );
    }



/* =========================
 AWARD
========================= */

    public static boolean awardBadge(
            ServerPlayer player,
            BadgeType badge
    ){

        BadgeData data =
                loadPlayer(
                        player.getUUID()
                );


        boolean added =
                data.addBadge(
                        badge
                );


        if(
                !added
        ){
            return false;
        }


        savePlayer(
                player.getUUID(),
                data
        );


        sendBadgeMessage(
                player,
                badge
        );


        announceBadge(
                player,
                badge
        );


        return true;
    }



/* =========================
 MESSAGES
========================= */

    private static void sendBadgeMessage(
            ServerPlayer player,
            BadgeType badge
    ){

        player.sendSystemMessage(
                Component.literal(
                        "§6You earned the "
                                + badge.getDisplayName()
                                + "!"
                )
        );

    }



    private static void announceBadge(
            ServerPlayer player,
            BadgeType badge
    ){

        MinecraftServer server =
                player.getServer();

        if(
                server == null
        ){
            return;
        }


        server.getPlayerList()
                .broadcastSystemMessage(
                        Component.literal(
                                "§e🏅 "
                                        + player.getName().getString()
                                        + " earned the "
                                        + badge.getDisplayName()
                                        + "!"
                        ),
                        false
                );


        if(
                loadPlayer(
                        player.getUUID()
                ).hasAllBadges()
        ){

            server.getPlayerList()
                    .broadcastSystemMessage(
                            Component.literal(
                                    "§6"
                                            + player.getName().getString()
                                            + " has become a Champion Candidate!"
                            ),
                            false
                    );
        }

    }



/* =========================
 SAVE MODEL
========================= */

    private static class SaveData {

        List<String> badges =
                new ArrayList<>();

    }

}