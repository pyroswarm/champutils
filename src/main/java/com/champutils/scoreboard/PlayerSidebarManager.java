package com.champutils.scoreboard;


import com.champutils.dex.DexProgressManager;
import com.champutils.economy.EconomyManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionType;
import com.champutils.profile.PlayerDataManager;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfilePlaytimeManager;
import com.champutils.profile.ProfileNetworkTransferFlow;
import com.champutils.rank.RankManager;
import com.champutils.config.Rank;
import com.champutils.specialspawn.SpecialWildSpawnManager;
import com.champutils.guild.GuildBossManager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlayerSidebarManager {

    private static final String TITLE = "Cobble Champs";
    private static final int MAX_SCORE_OWNER_LENGTH = 40;

    private static final Map<UUID, List<String>> LAST_LINES = new HashMap<>();
    private static final Map<UUID, Boolean> CREATED = new HashMap<>();
    private static final Map<UUID, Long> LAST_BUILD_MILLIS = new HashMap<>();

    private static final long BUILD_COOLDOWN_MILLIS = 5000L;

    private PlayerSidebarManager() {
    }

    public static void tick(MinecraftServer server) {
        if (server == null || ProfileNetworkTransferFlow.isProfileLobbyServer()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ScoreboardPreferenceManager.isEnabled(player.getUUID())) {
                update(player);
            } else {
                clear(player);
            }
        }
    }

    public static void update(ServerPlayer player) {
        if (player == null || player.connection == null || ProfileNetworkTransferFlow.isProfileLobbyServer()) {
            return;
        }

        try {
            String objectiveName = objectiveName(player);
            Objective objective = createPacketObjective(objectiveName);

            boolean created = CREATED.getOrDefault(player.getUUID(), false);

            if (!created) {
                player.connection.send(new ClientboundSetObjectivePacket(objective, 0));
                player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
                CREATED.put(player.getUUID(), true);
            }

            UUID uuid = player.getUUID();
            long now = System.currentTimeMillis();
            List<String> oldLines = LAST_LINES.getOrDefault(uuid, List.of());
            if (!oldLines.isEmpty() && now - LAST_BUILD_MILLIS.getOrDefault(uuid, 0L) < BUILD_COOLDOWN_MILLIS) {
                return;
            }

            List<String> newLines = buildLines(player);
            LAST_BUILD_MILLIS.put(uuid, now);

            if (!oldLines.isEmpty() && oldLines.equals(newLines)) {
                return;
            }

            /*
             * Recreate the objective when line content changes so old lines do not linger.
             */
            if (!oldLines.isEmpty() && !oldLines.equals(newLines)) {
                player.connection.send(new ClientboundSetObjectivePacket(objective, 1));
                player.connection.send(new ClientboundSetObjectivePacket(objective, 0));
                player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
            }

            int score = newLines.size();

            for (String line : newLines) {
                player.connection.send(new ClientboundSetScorePacket(
                        line,
                        objectiveName,
                        score,
                        Optional.empty(),
                        Optional.of(BlankFormat.INSTANCE)
                ));
                score--;
            }

            LAST_LINES.put(player.getUUID(), newLines);
        } catch (Exception exception) {
            ScoreboardPreferenceManager.setEnabled(player.getUUID(), false);
            clear(player);
            System.err.println("[ChampUtils] Failed to render sidebar for " + player.getName().getString() + ": " + exception.getMessage());
        }
    }

    public static void clear(ServerPlayer player) {
        if (player == null || player.connection == null || ProfileNetworkTransferFlow.isProfileLobbyServer()) {
            return;
        }

        UUID uuid = player.getUUID();

        try {
            if (CREATED.getOrDefault(uuid, false)) {
                Objective objective = createPacketObjective(objectiveName(player));
                player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, null));
                player.connection.send(new ClientboundSetObjectivePacket(objective, 1));
            }
        } catch (Exception ignored) {
        }

        CREATED.remove(uuid);
        LAST_LINES.remove(uuid);
        LAST_BUILD_MILLIS.remove(uuid);
    }

    private static Objective createPacketObjective(String objectiveName) {
        Scoreboard scoreboard = new Scoreboard();

        Objective existing = scoreboard.getObjective(objectiveName);
        if (existing != null) {
            return existing;
        }

        return scoreboard.addObjective(
                objectiveName,
                ObjectiveCriteria.DUMMY,
                Component.literal(TITLE).withStyle(ChatFormatting.GOLD),
                RenderType.INTEGER,
                false,
                null
        );
    }

    private static List<String> buildLines(ServerPlayer player) {
        List<String> lines = new ArrayList<>();

        int rp = PlayerDataManager.getRp(
                player.getUUID(),
                player.getName().getString()
        );

        long balance = EconomyManager.getBalance(player);

        int caught = DexProgressManager.getCaughtCount(player);
        int total = DexProgressManager.getTotalPokemon();
        double dexPercent = DexProgressManager.getCompletionPercent(player);

        lines.add("§8§m----------------");
        lines.add("§6Money §f" + EconomyManager.format(balance));
        lines.add("§bRank §f" + rankName(rp));
        lines.add("§bRP §f" + rp);
        lines.add("§dDex §f" + caught + "§7/§f" + total);
        lines.add("§7" + formatPercent(dexPercent) + "% Complete");
        lines.add("§eProfile Time §f" + formatPlaytime(ProfilePlaytimeManager.getDisplayPlaytimeSeconds(player)));
        if (PlayerProfileManager.isIslander(player)) {
            lines.add("§6Island Special §f" + SpecialWildSpawnManager.formatLastIslanderSpawnAgo());
        } else {
            lines.add("§6Last Special §f" + SpecialWildSpawnManager.formatLastNormalSpawnAgo());
        }
        lines.add("§5Last Boss §f" + GuildBossManager.formatLastWorldBossSpawnAgo());
        lines.add("§8§m----------------");
        lines.add(professionLine("§cBattling", player, ProfessionType.BATTLING));
        lines.add(professionLine("§7Mining", player, ProfessionType.MINING));
        lines.add(professionLine("§2Forestry", player, ProfessionType.FORESTRY));
        lines.add(professionLine("§aFarming", player, ProfessionType.FARMING));

        return makeUniqueAndSafe(lines);
    }

    private static String rankName(int rp) {
        try {
            Rank rank = RankManager.getRank(rp);
            if (rank != null && rank.name != null && !rank.name.isBlank()) {
                return rank.name;
            }
        } catch (Exception ignored) {
        }

        return "Youngster";
    }

    private static String professionLine(String label, ServerPlayer player, ProfessionType type) {
        int level = safeLevel(player, type);
        int xp = safeXp(player, type);
        int required = Math.max(1, ProfessionManager.xpRequired(level));
        int percent = Math.max(0, Math.min(100, (int) Math.floor((xp * 100.0D) / required)));

        return label + " §f" + level + " §7(" + percent + "%)";
    }

    private static int safeLevel(ServerPlayer player, ProfessionType type) {
        try {
            return ProfessionManager.getLevel(player, type);
        } catch (Exception ignored) {
            return 1;
        }
    }

    private static int safeXp(ServerPlayer player, ProfessionType type) {
        try {
            return ProfessionManager.getXp(player, type);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String formatPercent(double value) {
        return String.format("%.1f", value);
    }

    private static String formatPlaytime(long seconds) {
        long safe = Math.max(0L, seconds);
        long hours = safe / 3600L;
        long minutes = (safe % 3600L) / 60L;
        return hours + "h " + minutes + "m";
    }

    private static List<String> makeUniqueAndSafe(List<String> source) {
        List<String> result = new ArrayList<>();

        String[] suffixes = {
                "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9",
                "§a", "§b", "§c", "§d", "§e", "§f"
        };

        for (int i = 0; i < source.size(); i++) {
            String line = source.get(i);

            int maxBaseLength = Math.max(1, MAX_SCORE_OWNER_LENGTH - 2);
            if (line.length() > maxBaseLength) {
                line = line.substring(0, maxBaseLength);
            }

            result.add(line + suffixes[i % suffixes.length]);
        }

        return result;
    }

    private static String objectiveName(ServerPlayer player) {
        String compact = player.getUUID().toString().replace("-", "");
        return "cu_sb_" + compact.substring(0, 10);
    }
}
