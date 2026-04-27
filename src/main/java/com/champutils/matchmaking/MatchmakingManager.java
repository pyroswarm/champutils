package com.champutils.matchmaking;

import com.champutils.validation.TeamSnapshotManager;
import com.champutils.validation.TeamValidator;
import com.champutils.battle.BattleStateManager;
import com.champutils.rank.RankManager;
import com.champutils.config.Config;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;

import java.util.*;

public class MatchmakingManager {

    private static final Map<String,List<ServerPlayer>> QUEUES =
            new HashMap<>();

    private static final Map<UUID,String> MATCH_TYPE =
            new HashMap<>();

    private static final Map<UUID,UUID> OPPONENTS =
            new HashMap<>();

    private static final Map<UUID,Integer> QUEUE_TIME =
            new HashMap<>();

    private static final Map<UUID,Map<UUID,Integer>> RECENT_MATCHES =
            new HashMap<>();

    private static final List<DelayedTask> TASKS =
            new ArrayList<>();


    // NEW:
    // players currently in "match found but not launched"
    private static final Set<UUID> PENDING_MATCH =
            new HashSet<>();


    private static class DelayedTask {

        int ticks;
        Runnable action;

        DelayedTask(
                int ticks,
                Runnable action
        ){
            this.ticks=ticks;
            this.action=action;
        }
    }



    public static void joinQueue(
            ServerPlayer player,
            String type
    ){

        if(isInAnyQueue(player)){
            player.sendSystemMessage(
                    Component.literal(
                            "§cAlready in queue."
                    )
            );
            return;
        }

        if(BattleStateManager.isInBattle(player)){
            player.sendSystemMessage(
                    Component.literal(
                            "§cAlready in battle."
                    )
            );
            return;
        }

        if(TeamPreviewManager.isInPreview(player)){
            return;
        }

        String error=
                TeamValidator.validate(
                        player,
                        type
                );

        if(error!=null){
            player.sendSystemMessage(
                    Component.literal(
                            "§c"+error
                    )
            );
            return;
        }

        QUEUES.computeIfAbsent(
                type,
                k->new ArrayList<>()
        ).add(player);

        QUEUE_TIME.putIfAbsent(
                player.getUUID(),
                0
        );

        if("ranked".equalsIgnoreCase(type)){
            TeamSnapshotManager.saveSnapshot(
                    player
            );
        }

        QueueBossBarManager.start(
                player,
                type
        );
    }



    public static void leaveQueue(
            ServerPlayer player
    ){

        for(List<ServerPlayer> q:
                QUEUES.values()){
            q.remove(player);
        }

        QueueBossBarManager.stop(player);

        TeamSnapshotManager.clear(player);

        QUEUE_TIME.remove(
                player.getUUID()
        );

        PENDING_MATCH.remove(
                player.getUUID()
        );
    }



    public static boolean isRankedMatch(
            ServerPlayer player
    ){
        return "ranked".equalsIgnoreCase(
                MATCH_TYPE.get(
                        player.getUUID()
                )
        );
    }



    public static void clearMatch(
            ServerPlayer player
    ){
        MATCH_TYPE.remove(
                player.getUUID()
        );

        OPPONENTS.remove(
                player.getUUID()
        );

        PENDING_MATCH.remove(
                player.getUUID()
        );
    }



    private static boolean rankedType(
            String type
    ){
        return "ranked".equalsIgnoreCase(
                type
        );
    }



    private static boolean isInAnyQueue(
            ServerPlayer player
    ){

        for(List<ServerPlayer> q:
                QUEUES.values()){
            if(q.contains(player))
                return true;
        }

        return false;
    }



    private static boolean canMatch(
            ServerPlayer player
    ){
        return player != null
                &&
                player.isAlive()
                &&
                !BattleStateManager.isInBattle(player)
                &&
                !TeamPreviewManager.isInPreview(player)
                &&
                !PENDING_MATCH.contains(
                        player.getUUID()
                );
    }



    public static void tick(){

        for(UUID id :
                new ArrayList<>(
                        QUEUE_TIME.keySet()
                )){

            QUEUE_TIME.put(
                    id,
                    QUEUE_TIME.get(id)+1
            );
        }


        for(var map :
                RECENT_MATCHES.values()){

            Iterator<Map.Entry<UUID,Integer>>
                    it=
                    map.entrySet().iterator();

            while(it.hasNext()){

                var e=it.next();

                int t=e.getValue()+1;

                if(t>6000){
                    it.remove();
                }
                else{
                    e.setValue(t);
                }
            }
        }



        List<DelayedTask> run=
                new ArrayList<>();

        Iterator<DelayedTask> taskIt=
                TASKS.iterator();

        while(taskIt.hasNext()){

            DelayedTask t=
                    taskIt.next();

            t.ticks--;

            if(t.ticks<=0){
                run.add(t);
                taskIt.remove();
            }
        }

        for(DelayedTask t:run){
            t.action.run();
        }



        for(String type:
                QUEUES.keySet()){

            List<ServerPlayer> queue=
                    QUEUES.get(type);

            if(queue.size()<2)
                continue;


            ServerPlayer bestP1=null;
            ServerPlayer bestP2=null;

            int bestRankDiff=
                    Integer.MAX_VALUE;

            int bestEloDiff=
                    Integer.MAX_VALUE;


            for(int i=0;i<queue.size();i++){

                ServerPlayer p1=
                        queue.get(i);

                if(!canMatch(p1))
                    continue;


                for(int j=i+1;
                    j<queue.size();
                    j++){

                    ServerPlayer p2=
                            queue.get(j);

                    if(!canMatch(p2))
                        continue;


                    if(rankedType(type)
                            && recentlyPlayed(
                            p1,p2
                    )){
                        continue;
                    }


                    if(!rankedType(type)){
                        bestP1=p1;
                        bestP2=p2;
                        break;
                    }


                    int rank1=
                            getRankIndex(p1);

                    int rank2=
                            getRankIndex(p2);

                    int range1=
                            getRankSearchWindow(p1);

                    int range2=
                            getRankSearchWindow(p2);

                    int rankDiff=
                            Math.abs(
                                    rank1-rank2
                            );

                    if(rankDiff>range1
                            || rankDiff>range2)
                        continue;


                    int eloDiff=
                            Math.abs(
                                    getElo(p1)-getElo(p2)
                            );


                    if(rankDiff<bestRankDiff
                            ||
                            (
                                    rankDiff==bestRankDiff
                                            &&
                                            eloDiff<bestEloDiff
                            )){

                        bestRankDiff=rankDiff;
                        bestEloDiff=eloDiff;

                        bestP1=p1;
                        bestP2=p2;
                    }
                }

                if(bestP1!=null
                        && !rankedType(type)){
                    break;
                }
            }


            if(bestP1!=null){

                // mark pending
                PENDING_MATCH.add(
                        bestP1.getUUID()
                );

                PENDING_MATCH.add(
                        bestP2.getUUID()
                );

                startMatch(
                        bestP1,
                        bestP2,
                        type
                );

                return;
            }
        }
    }



    private static int getRankSearchWindow(
            ServerPlayer player
    ){

        int ticks=
                QUEUE_TIME.getOrDefault(
                        player.getUUID(),
                        0
                );

        int seconds=ticks/20;

        if(getRankIndex(player)
                >= Config.ranks.size()-1){

            if(seconds<120)
                return 1;

            return 2;
        }

        if(seconds<60)
            return 0;

        if(seconds<120)
            return 1;

        return 2;
    }



    private static int getRankIndex(
            ServerPlayer player
    ){

        var rank=
                RankManager.getRank(
                        getElo(player)
                );

        if(rank==null)
            return 0;

        for(int i=0;i<Config.ranks.size();i++){

            if(
                    Config.ranks
                            .get(i)
                            .name
                            .equals(rank.name)
            ){
                return i;
            }
        }

        return 0;
    }



    private static void startMatch(
            ServerPlayer p1,
            ServerPlayer p2,
            String type
    ){

        // if either entered battle,
        // dissolve pending match
        if(
                BattleStateManager.isInBattle(p1)
                        ||
                        BattleStateManager.isInBattle(p2)
        ){
            clearMatch(p1);
            clearMatch(p2);
            return;
        }


        MATCH_TYPE.put(
                p1.getUUID(),
                type
        );

        MATCH_TYPE.put(
                p2.getUUID(),
                type
        );


        sendTitle(
                p1,
                "§aMATCH FOUND",
                "§fPreparing..."
        );

        sendTitle(
                p2,
                "§aMATCH FOUND",
                "§fPreparing..."
        );


        TASKS.add(
                new DelayedTask(
                        40,
                        ()->runCountdown(
                                p1,p2,type
                        )
                )
        );
    }



    private static void runCountdown(
            ServerPlayer p1,
            ServerPlayer p2,
            String type
    ){

        if(
                BattleStateManager.isInBattle(p1)
                        ||
                        BattleStateManager.isInBattle(p2)
        ){
            clearMatch(p1);
            clearMatch(p2);
            return;
        }


        boolean useArenas=
                Config.arenas!=null
                        &&
                        !Config.arenas.isEmpty();

        if(useArenas){

            if(!ArenaManager.hasOpenArena()){
                clearMatch(p1);
                clearMatch(p2);
                return;
            }

            var arena=
                    ArenaManager.reserveArena(
                            p1,p2
                    );

            if(arena==null){
                clearMatch(p1);
                clearMatch(p2);
                return;
            }

            ArenaManager.teleportPlayersToArena(
                    p1,p2,arena
            );
        }


        countdown(p1,p2,"§c3",0);
        countdown(p1,p2,"§e2",20);
        countdown(p1,p2,"§a1",40);
        countdown(p1,p2,"§6GO!",60);


        TASKS.add(
                new DelayedTask(
                        80,
                        ()->{

                            if(
                                    BattleStateManager.isInBattle(p1)
                                            ||
                                            BattleStateManager.isInBattle(p2)
                            ){
                                clearMatch(p1);
                                clearMatch(p2);
                                return;
                            }

                            // NOW remove from queue
                            leaveQueue(p1);
                            leaveQueue(p2);

                            TeamPreviewManager.startPreview(
                                    p1,p2
                            );
                        }
                )
        );
    }



    private static void countdown(
            ServerPlayer p1,
            ServerPlayer p2,
            String text,
            int delay
    ){

        TASKS.add(
                new DelayedTask(
                        delay,
                        ()->{

                            sendTitle(
                                    p1,text,""
                            );

                            sendTitle(
                                    p2,text,""
                            );

                            p1.level().playSound(
                                    null,
                                    p1.blockPosition(),
                                    SoundEvents.NOTE_BLOCK_PLING.value(),
                                    SoundSource.PLAYERS,
                                    1f,1.2f
                            );

                            p2.level().playSound(
                                    null,
                                    p2.blockPosition(),
                                    SoundEvents.NOTE_BLOCK_PLING.value(),
                                    SoundSource.PLAYERS,
                                    1f,1.2f
                            );

                        })
        );
    }



    private static void sendTitle(
            ServerPlayer player,
            String title,
            String subtitle
    ){

        player.connection.send(
                new ClientboundSetTitlesAnimationPacket(
                        5,20,5
                )
        );

        player.connection.send(
                new ClientboundSetTitleTextPacket(
                        Component.literal(title)
                )
        );

        if(!subtitle.isEmpty()){

            player.connection.send(
                    new ClientboundSetSubtitleTextPacket(
                            Component.literal(subtitle)
                    )
            );
        }
    }



    private static void recordMatch(
            ServerPlayer p1,
            ServerPlayer p2
    ){

        RECENT_MATCHES
                .computeIfAbsent(
                        p1.getUUID(),
                        k->new HashMap<>()
                )
                .put(
                        p2.getUUID(),
                        0
                );

        RECENT_MATCHES
                .computeIfAbsent(
                        p2.getUUID(),
                        k->new HashMap<>()
                )
                .put(
                        p1.getUUID(),
                        0
                );
    }



    private static boolean recentlyPlayed(
            ServerPlayer p1,
            ServerPlayer p2
    ){

        Map<UUID,Integer> map=
                RECENT_MATCHES.get(
                        p1.getUUID()
                );

        if(map==null)
            return false;

        return map.containsKey(
                p2.getUUID()
        );
    }



    private static int getElo(
            ServerPlayer player
    ){

        var sb=
                player.getScoreboard();

        var obj=
                sb.getObjective(
                        "elo"
                );

        if(obj==null)
            return 0;

        return sb.getOrCreatePlayerScore(
                player,
                obj
        ).get();
    }
}