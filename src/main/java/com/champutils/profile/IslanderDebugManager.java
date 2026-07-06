package com.champutils.profile;

import com.champutils.territory.TerritoryRepository;
import com.champutils.debug.ChampDebugManager;
import com.champutils.network.NetworkServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary diagnostics for Islander/profile territory routing.
 * Keep this lightweight: it only logs when enabled per-player or globally.
 */
public final class IslanderDebugManager {
    private static volatile boolean globalEnabled = false;
    private static final Map<UUID, Long> enabledPlayers = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_LOG = new ConcurrentHashMap<>();
    private static final long PLAYER_DEBUG_TTL_MS = 10L * 60L * 1000L;
    private static final long THROTTLE_MS = 1500L;

    private IslanderDebugManager() {}

    public static void setGlobalEnabled(boolean enabled) {
        globalEnabled = enabled;
        ChampDebugManager.log(ChampDebugManager.Category.ISLANDER, "[ISLANDER-DEBUG] global=" + enabled);
    }

    public static boolean isGlobalEnabled() {
        return globalEnabled;
    }

    public static void enableFor(ServerPlayer player) {
        if (player != null) {
            enabledPlayers.put(player.getUUID(), System.currentTimeMillis() + PLAYER_DEBUG_TTL_MS);
            ChampDebugManager.log(ChampDebugManager.Category.ISLANDER, "[ISLANDER-DEBUG] enabled for " + player.getGameProfile().getName() + " for 10 minutes");
        }
    }

    public static void disableFor(ServerPlayer player) {
        if (player != null) enabledPlayers.remove(player.getUUID());
    }

    public static boolean isEnabled(ServerPlayer player) {
        if (ChampDebugManager.isEnabled(ChampDebugManager.Category.ISLANDER) || globalEnabled) return true;
        if (player == null) return false;
        Long expires = enabledPlayers.get(player.getUUID());
        if (expires == null) return false;
        if (expires < System.currentTimeMillis()) {
            enabledPlayers.remove(player.getUUID());
            return false;
        }
        return true;
    }

    public static void log(ServerPlayer player, String phase, TerritoryRepository.Territory territory, String decision, String reason) {
        if (!isEnabled(player)) return;
        String key = (player == null ? "null" : player.getUUID().toString()) + ":" + phase + ":" + decision + ":" + reason;
        long now = System.currentTimeMillis();
        Long last = LAST_LOG.get(key);
        if (last != null && now - last < THROTTLE_MS) return;
        LAST_LOG.put(key, now);
        ChampDebugManager.log(ChampDebugManager.Category.ISLANDER, describeLine(player, phase, territory, decision, reason));
    }

    public static void sendSnapshot(ServerPlayer player) {
        if (player == null) return;
        TerritoryRepository.Territory territory = null;
        if (player.level() instanceof ServerLevel level) {
            territory = TerritoryRepository.findAt(level, player.blockPosition());
        }
        player.sendSystemMessage(Component.literal("==== Islander Debug ====").withStyle(ChatFormatting.GOLD));
        for (String line : describeMultiline(player, territory)) {
            player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.YELLOW));
        }
        ChampDebugManager.log(ChampDebugManager.Category.ISLANDER, describeLine(player, "COMMAND", territory, "SNAPSHOT", "manual"));
    }

    public static String describeLine(ServerPlayer player, String phase, TerritoryRepository.Territory territory, String decision, String reason) {
        return "[ISLANDER-DEBUG] phase=" + safe(phase)
                + " decision=" + safe(decision)
                + " reason=" + safe(reason)
                + " " + String.join(" | ", describeMultiline(player, territory));
    }

    public static java.util.List<String> describeMultiline(ServerPlayer player, TerritoryRepository.Territory territory) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (player == null) {
            lines.add("player=null");
            return lines;
        }
        UUID activeId = PlayerProfileManager.activeProfileId(player);
        PlayerProfileManager.ProfileRecord active = PlayerProfileManager.active(player);
        ProfileGameMode activeMode = active == null ? null : active.gameMode();
        boolean hasActive = PlayerProfileManager.hasActiveProfile(player);
        boolean loading = ProfileLoadingStateManager.isLoading(player);
        boolean isIslander = PlayerProfileManager.isIslander(player);
        String dimension = player.level() instanceof ServerLevel level ? level.dimension().location().toString() : "unknown";
        BlockPos pos = player.blockPosition();

        lines.add("player=" + player.getGameProfile().getName() + " uuid=" + player.getUUID());
        lines.add("profile activeId=" + activeId + " hasActive=" + hasActive + " activeMode=" + activeMode + " isIslander=" + isIslander + " loading=" + loading);
        lines.add("serverId=" + NetworkServerConfig.serverId() + " dim=" + dimension + " pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ());
        if (territory == null) {
            lines.add("territory=null");
        } else {
            boolean targetIsIslander = IslanderProfileManager.isIslanderTerritory(territory);
            boolean ownerMatchesActive = activeId != null && territory.ownerId != null && territory.ownerId.equalsIgnoreCase(activeId.toString());
            ProfileGameMode ownerMode = ownerMatchesActive ? activeMode : IslanderProfileManager.cachedProfileMode(territory.ownerId);
            TerritoryRepository.TrustLevel trust = TerritoryRepository.getTrust(territory.id, player.getUUID());
            lines.add("territory id=" + territory.id + " ownerType=" + territory.ownerType + " ownerId=" + territory.ownerId + " ownerName=" + territory.ownerName);
            lines.add("territory world=" + territory.serverId + ":" + territory.worldName + " ready=" + territory.isReady() + " targetIsIslander=" + targetIsIslander + " ownerMode=" + (ownerMode == null ? "cached_pending" : ownerMode));
            lines.add("territory ownerMatchesActiveProfile=" + ownerMatchesActive
                    + " trust=" + trust
                    + " public=" + territory.isPublic
                    + " allowVisitors=" + territory.allowVisitors);
        }
        return lines;
    }

    private static String safe(String value) {
        return value == null ? "null" : value.replace('\n', ' ').replace('\r', ' ').toLowerCase(Locale.ROOT);
    }
}
