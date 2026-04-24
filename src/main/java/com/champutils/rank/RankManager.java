package com.champutils.rank;

import com.champutils.config.Config;
import com.champutils.config.Rank;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import net.minecraft.core.particles.ParticleTypes;

import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;

import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.EntityType;

public class RankManager {

    // =========================
    // GET RANK
    // =========================
    public static Rank getRank(int elo) {

        Rank best = null;

        for (Rank rank : Config.ranks) {

            if (elo >= rank.min_elo) {

                if (best == null ||
                        rank.min_elo > best.min_elo) {

                    best = rank;
                }
            }
        }

        return best;
    }

    // =========================
    // UPDATE RANK
    // =========================
    public static void updatePlayerRank(
            ServerPlayer player,
            int oldElo,
            int newElo
    ) {

        Rank oldRank = getRank(oldElo);
        Rank newRank = getRank(newElo);

        if (oldRank == null || newRank == null)
            return;

        if (oldRank.name.equals(newRank.name))
            return;

        boolean rankUp =
                newRank.min_elo > oldRank.min_elo;

        if (rankUp) {
            onRankUp(player, oldRank, newRank);
        }
        else {
            onRankDown(player, oldRank, newRank);
        }

        // hook for future NameTagManager / visuals
        applyRankVisuals(
                player,
                newRank
        );
    }

    // =========================
    // RANK UP
    // =========================
    private static void onRankUp(
            ServerPlayer player,
            Rank oldRank,
            Rank newRank
    ) {

        sendPersonalTitle(
                player,
                "§aRank Up!",
                oldRank.name + " → " + newRank.name
        );

        String rank =
                newRank.name
                        .toLowerCase()
                        .replace(" ","");

        // =========================
        // MONARCH
        // =========================
        if (rank.equals("monarch")) {

            globalTitle(
                    player,
                    "§5MONARCH ASCENSION",
                    player.getName().getString()
                            + " has reached Monarch"
            );

            broadcast(
                    player,
                    "§5===================================="
            );

            broadcast(
                    player,
                    "§dA NEW MONARCH HAS RISEN"
            );

            broadcast(
                    player,
                    "§5"
                            + player.getName().getString()
                            + " has ascended to Monarch"
            );

            broadcast(
                    player,
                    "§5===================================="
            );

            playGlobalSound(
                    player,
                    SoundEvents.ENDER_DRAGON_DEATH,
                    2f,
                    1f
            );

            lightning(player);
            lightning(player);

            for (int i=0;i<5;i++) {
                megaParticles(
                        player,
                        300
                );
            }

            return;
        }

        // =========================
        // GRAND MASTER
        // =========================
        if (rank.equals("grandmaster")) {

            globalTitle(
                    player,
                    "§cGRAND MASTER",
                    player.getName().getString()
                            + " has reached Grand Master"
            );

            broadcast(
                    player,
                    "§6================================"
            );

            broadcast(
                    player,
                    "§c"
                            + player.getName().getString()
                            + " has reached Grand Master"
            );

            broadcast(
                    player,
                    "§7A new elite trainer has emerged"
            );

            broadcast(
                    player,
                    "§6================================"
            );

            playGlobalSound(
                    player,
                    SoundEvents.WITHER_SPAWN,
                    1.7f,
                    1f
            );

            lightning(player);

            for (int i=0;i<3;i++) {
                megaParticles(
                        player,
                        180
                );
            }

            return;
        }

        // =========================
        // HIGH TIERS
        // =========================
        if (newRank.min_elo >= 1200) {

            player.playNotifySound(
                    SoundEvents.TOTEM_USE,
                    SoundSource.PLAYERS,
                    1.4f,
                    1.2f
            );

            megaParticles(
                    player,
                    100
            );

            broadcast(
                    player,
                    "§b"
                            + player.getName().getString()
                            + " promoted to "
                            + newRank.name
            );

            return;
        }

        // =========================
        // NORMAL RANKS
        // =========================
        player.playNotifySound(
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS,
                1.2f,
                1.2f
        );

        megaParticles(
                player,
                50
        );

        broadcast(
                player,
                "§a"
                        + player.getName().getString()
                        + " ranked up to "
                        + newRank.name
        );
    }

    // =========================
    // DERANK
    // =========================
    private static void onRankDown(
            ServerPlayer player,
            Rank oldRank,
            Rank newRank
    ) {

        sendPersonalTitle(
                player,
                "§cRank Down",
                oldRank.name + " → " + newRank.name
        );

        player.playNotifySound(
                SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS,
                1f,
                .7f
        );

        player.serverLevel().sendParticles(
                ParticleTypes.SMOKE,
                player.getX(),
                player.getY()+1,
                player.getZ(),
                50,
                1,1,1,
                .05
        );

        broadcast(
                player,
                "§c"
                        + player.getName().getString()
                        + " deranked to "
                        + newRank.name
        );
    }

    // =========================
    // FUTURE HOOK
    // =========================
    private static void applyRankVisuals(
            ServerPlayer player,
            Rank rank
    ) {

        // intentionally blank for now

        // future:
        // RankNameTagManager.update(player,rank);
        // TabListManager.update(player,rank);
        // ChatPrefixManager.update(player,rank);
    }

    // =========================
    // PARTICLES
    // =========================
    private static void megaParticles(
            ServerPlayer player,
            int amount
    ) {

        player.serverLevel().sendParticles(
                ParticleTypes.FIREWORK,
                player.getX(),
                player.getY()+1.5,
                player.getZ(),
                amount,
                2,2,2,
                .1
        );

        player.serverLevel().sendParticles(
                ParticleTypes.END_ROD,
                player.getX(),
                player.getY()+1.5,
                player.getZ(),
                amount,
                2,2,2,
                .02
        );

        player.serverLevel().sendParticles(
                ParticleTypes.TOTEM_OF_UNDYING,
                player.getX(),
                player.getY()+1,
                player.getZ(),
                amount/2,
                1,1,1,
                .05
        );
    }

    // =========================
    // LIGHTNING
    // =========================
    private static void lightning(
            ServerPlayer player
    ) {

        LightningBolt bolt =
                EntityType.LIGHTNING_BOLT.create(
                        player.level()
                );

        if (bolt == null)
            return;

        bolt.moveTo(
                player.getX(),
                player.getY(),
                player.getZ()
        );

        bolt.setVisualOnly(true);

        player.serverLevel()
                .addFreshEntity(
                        bolt
                );
    }

    // =========================
    // PERSONAL TITLE
    // =========================
    private static void sendPersonalTitle(
            ServerPlayer player,
            String title,
            String subtitle
    ) {

        player.connection.send(
                new ClientboundSetTitlesAnimationPacket(
                        10,50,15
                )
        );

        player.connection.send(
                new ClientboundSetTitleTextPacket(
                        Component.literal(title)
                )
        );

        player.connection.send(
                new ClientboundSetSubtitleTextPacket(
                        Component.literal(subtitle)
                )
        );
    }

    // =========================
    // GLOBAL TITLE
    // =========================
    private static void globalTitle(
            ServerPlayer source,
            String title,
            String subtitle
    ) {

        var server =
                source.getServer();

        if (server == null)
            return;

        for (ServerPlayer p :
                server.getPlayerList()
                        .getPlayers()) {

            p.connection.send(
                    new ClientboundSetTitlesAnimationPacket(
                            10,60,20
                    )
            );

            p.connection.send(
                    new ClientboundSetTitleTextPacket(
                            Component.literal(title)
                    )
            );

            p.connection.send(
                    new ClientboundSetSubtitleTextPacket(
                            Component.literal(subtitle)
                    )
            );
        }
    }

    // =========================
    // GLOBAL SOUND
    // =========================
    private static void playGlobalSound(
            ServerPlayer source,
            net.minecraft.sounds.SoundEvent sound,
            float vol,
            float pitch
    ) {

        var server =
                source.getServer();

        if (server == null)
            return;

        for (ServerPlayer p :
                server.getPlayerList()
                        .getPlayers()) {

            p.playNotifySound(
                    sound,
                    SoundSource.PLAYERS,
                    vol,
                    pitch
            );
        }
    }

    // =========================
    // BROADCAST
    // =========================
    private static void broadcast(
            ServerPlayer player,
            String msg
    ) {

        var server =
                player.getServer();

        if (server == null)
            return;

        for (ServerPlayer p :
                server.getPlayerList()
                        .getPlayers()) {

            p.sendSystemMessage(
                    Component.literal(msg)
            );
        }
    }
}