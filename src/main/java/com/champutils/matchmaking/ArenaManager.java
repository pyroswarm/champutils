package com.champutils.matchmaking;

import com.champutils.config.Config;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;

import java.util.*;

public class ArenaManager {

    public static class Arena {

        public String id;

        public double centerX;
        public double y;
        public double centerZ;

        public String world = "multiworld:spawn1";

        public String theme;
        public String music;
    }

    private static class ReturnLocation {

        String world;
        double x;
        double y;
        double z;

        float yaw;
        float pitch;

        ReturnLocation(
                String world,
                double x,
                double y,
                double z,
                float yaw,
                float pitch
        ){
            this.world=world;
            this.x=x;
            this.y=y;
            this.z=z;
            this.yaw=yaw;
            this.pitch=pitch;
        }
    }


    private static final Random RANDOM =
            new Random();

    private static final Set<String> IN_USE =
            new HashSet<>();

    private static final Map<UUID,String> PLAYER_ARENAS =
            new HashMap<>();

    private static final Map<UUID,ReturnLocation> RETURNS =
            new HashMap<>();


    // recent arenas weighted lower
    private static final Deque<String> RECENT_ARENAS =
            new ArrayDeque<>();

    private static final int RECENT_MEMORY=3;


    public static boolean hasOpenArena(){
        return getOpenArena()!=null;
    }


    public static Arena getOpenArena(){

        if(Config.arenas==null
                || Config.arenas.isEmpty()){
            return null;
        }

        List<Arena> open=
                new ArrayList<>();

        for(Arena arena:
                Config.arenas){

            if(!IN_USE.contains(
                    arena.id
            )){
                open.add(arena);
            }
        }

        if(open.isEmpty())
            return null;

        if(open.size()==1)
            return open.get(0);


        // weighted anti-repeat
        List<Arena> weighted =
                new ArrayList<>();

        for(Arena arena : open){

            int weight=6;

            if(RECENT_ARENAS.contains(
                    arena.id
            )){
                weight=1;
            }

            for(int i=0;i<weight;i++){
                weighted.add(arena);
            }
        }

        return weighted.get(
                RANDOM.nextInt(
                        weighted.size()
                )
        );
    }


    public static Arena reserveArena(
            ServerPlayer p1,
            ServerPlayer p2
    ){

        Arena arena=
                getOpenArena();

        if(arena==null)
            return null;

        saveReturnLocation(p1);
        saveReturnLocation(p2);

        IN_USE.add(
                arena.id
        );

        PLAYER_ARENAS.put(
                p1.getUUID(),
                arena.id
        );

        PLAYER_ARENAS.put(
                p2.getUUID(),
                arena.id
        );

        rememberArena(
                arena.id
        );

        return arena;
    }


    private static void rememberArena(
            String id
    ){

        RECENT_ARENAS.addLast(
                id
        );

        while(
                RECENT_ARENAS.size()
                        > RECENT_MEMORY
        ){
            RECENT_ARENAS.removeFirst();
        }
    }


    private static void saveReturnLocation(
            ServerPlayer player
    ){

        RETURNS.put(
                player.getUUID(),

                new ReturnLocation(
                        player.serverLevel().dimension().location().toString(),
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        player.getYRot(),
                        player.getXRot()
                )
        );
    }


    public static void teleportPlayersToArena(
            ServerPlayer p1,
            ServerPlayer p2,
            Arena arena
    ){

        double spacing=7.5;

        String world =
                arena.world == null || arena.world.isBlank()
                        ? "multiworld:spawn1"
                        : arena.world;

        ServerLevel level =
                getLevel(
                        p1,
                        world
                );

        if(level == null){
            p1.sendSystemMessage(Component.literal("§cArena world is not loaded: " + world));
            p2.sendSystemMessage(Component.literal("§cArena world is not loaded: " + world));
            return;
        }

        p1.teleportTo(
                level,
                arena.centerX-spacing,
                arena.y,
                arena.centerZ,
                -90f,
                0f
        );

        p1.setYRot(-90f);
        p1.setYHeadRot(-90f);
        p1.setXRot(0f);

        p2.teleportTo(
                level,
                arena.centerX+spacing,
                arena.y,
                arena.centerZ,
                90f,
                0f
        );

        p2.setYRot(90f);
        p2.setYHeadRot(90f);
        p2.setXRot(0f);


        if(arena.theme!=null){

            Component msg=
                    Component.literal(
                            "§6Entering "
                                    + arena.theme
                                    + " Arena"
                    );

            p1.sendSystemMessage(msg);
            p2.sendSystemMessage(msg);
        }

        playArenaMusic(p1, arena.music);
        playArenaMusic(p2, arena.music);
    }


    private static void playArenaMusic(ServerPlayer player, String music){

        if(player == null || music == null || music.isBlank()){
            return;
        }

        String soundId = music.trim().toLowerCase(Locale.ROOT);
        if(!soundId.contains(":")){
            soundId = "champutils:" + soundId;
        }

        try{
            String command = "playsound " + soundId + " music " + player.getGameProfile().getName() + " ~ ~ ~ 1 1";
            player.getServer().getCommands().performPrefixedCommand(
                    player.getServer().createCommandSourceStack().withSuppressedOutput(),
                    command
            );
        }
        catch(Exception ignored){
        }
    }


    public static void returnPlayer(
            ServerPlayer player
    ){

        ReturnLocation loc=
                RETURNS.remove(
                        player.getUUID()
                );

        if(loc==null)
            return;

        ServerLevel level =
                getLevel(
                        player,
                        loc.world
                );

        if(level == null){
            level = player.serverLevel();
        }

        player.teleportTo(
                level,
                loc.x,
                loc.y,
                loc.z,
                loc.yaw,
                loc.pitch
        );

        player.setYRot(
                loc.yaw
        );

        player.setYHeadRot(
                loc.yaw
        );

        player.setXRot(
                loc.pitch
        );
    }


    public static void releaseArena(
            ServerPlayer player
    ){

        String arenaId=
                PLAYER_ARENAS.remove(
                        player.getUUID()
                );

        if(arenaId==null)
            return;

        PLAYER_ARENAS.values().removeIf(
                id->id.equals(arenaId)
        );

        IN_USE.remove(
                arenaId
        );
    }


    private static ServerLevel getLevel(
            ServerPlayer player,
            String world
    ){
        if(player == null || player.getServer() == null){
            return null;
        }

        String normalized =
                normalizeWorld(
                        world
                );

        try{
            ResourceKey<Level> key =
                    ResourceKey.create(
                            Registries.DIMENSION,
                            ResourceLocation.parse(
                                    normalized
                            )
                    );

            return player.getServer().getLevel(
                    key
            );
        }
        catch(Exception ignored){
            return null;
        }
    }


    private static String normalizeWorld(
            String world
    ){
        if(world == null || world.isBlank()){
            return "multiworld:spawn1";
        }

        String trimmed =
                world.trim();

        if(trimmed.equalsIgnoreCase("spawn1")){
            return "multiworld:spawn1";
        }

        if(trimmed.equalsIgnoreCase("overworld")){
            return "minecraft:overworld";
        }

        if(trimmed.equalsIgnoreCase("nether")){
            return "minecraft:the_nether";
        }

        if(trimmed.equalsIgnoreCase("end")){
            return "minecraft:the_end";
        }

        if(!trimmed.contains(":")){
            return "minecraft:" + trimmed;
        }

        return trimmed;
    }

}
