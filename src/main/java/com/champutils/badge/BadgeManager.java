package com.champutils.badge;

import com.champutils.profile.PlayerProfileManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BadgeManager {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private static final Map<UUID, BadgeData> CACHE =
            new ConcurrentHashMap<>();

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

    public static void initSql(){
        BadgeSqlRepository.initAsync();
    }

    static void acceptSqlSnapshot(UUID profileId, Set<BadgeType> badges){
        if(profileId == null || badges == null){
            return;
        }

        BadgeData existing = CACHE.get(profileId);
        if(badges.isEmpty() && existing != null && existing.getBadgeCount() > 0){
            BadgeSqlRepository.saveBadgeSnapshotAsync(profileId, existing.getBadges());
            return;
        }

        BadgeData data = new BadgeData();
        data.setBadges(badges);
        CACHE.put(profileId, data);
        savePlayerByProfileId(profileId, data);
    }



/* =========================
 FILE
========================= */

    private static UUID profileKey(UUID uuid){
        return PlayerProfileManager.activeProfileId(uuid);
    }

    private static File getPlayerFile(
            UUID uuid
    ){

        return getProfileFileById(profileKey(uuid));
    }

    private static File getProfileFileById(
            UUID profileId
    ){

        File dir = new File(BADGE_FOLDER, "profiles");
        if(!dir.exists()){
            dir.mkdirs();
        }
        return new File(
                dir,
                profileId.toString()+".json"
        );
    }



/* =========================
 LOAD
========================= */

    public static BadgeData loadPlayer(
            UUID uuid
    ){

        UUID key = profileKey(uuid);

        if(
                CACHE.containsKey(
                        key
                )
        ){
            return CACHE.get(
                    key
            );
        }


        BadgeSqlRepository.warmProfileAsync(key);

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
                    key,
                    data
            );

            if(data.getBadgeCount() > 0){
                BadgeSqlRepository.saveBadgeSnapshotAsync(key, data.getBadges());
            }

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
                    key,
                    data
            );

            if(data.getBadgeCount() > 0){
                BadgeSqlRepository.saveBadgeSnapshotAsync(key, data.getBadges());
            }

            return data;

        }
        catch(Exception e){

            e.printStackTrace();

            BadgeData data =
                    new BadgeData();

            CACHE.put(
                    key,
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

        UUID key = profileKey(uuid);
        savePlayerByProfileId(key, data);
        BadgeSqlRepository.saveBadgeSnapshotAsync(key, data.getBadges());
    }

    private static void savePlayerByProfileId(
            UUID profileId,
            BadgeData data
    ){

        try(
                FileWriter writer =
                        new FileWriter(
                                getProfileFileById(
                                        profileId
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


        BadgeSqlRepository.saveBadgeAwardAsync(
                player,
                badge
        );


        sendBadgeMessage(
                player,
                badge
        );

        // Global badge chat announcements are intentionally disabled.
        // Title announcements now handle this moment without duplicate chat noise.


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