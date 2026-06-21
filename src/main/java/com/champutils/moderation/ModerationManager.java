package com.champutils.moderation;

import com.champutils.antilag.AntiLagConfig;
import com.champutils.database.DatabaseManager;
import com.champutils.permissions.LuckPermsHook;
import com.champutils.time.DailyResetManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

public final class ModerationManager {
    private enum ModerationTrack {
        CHAT("Chat AutoMod", "ChatMod"),
        XRAY("Xray", "Xray"),
        ANTILAG("AntiLag", "AntiLag"),
        GENERIC("AutoMod", "AutoMod");

        final String label;
        final String webhookLabel;

        ModerationTrack(String label, String webhookLabel) {
            this.label = label;
            this.webhookLabel = webhookLabel;
        }
    }

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");
    private static final Pattern REPEATED_CHARS = Pattern.compile("(.)\\1{2,}");
    private static final Map<TrackKey, Record> records = new HashMap<>();
    private static final Map<UUID, TempBan> tempBans = new HashMap<>();
    private static final Map<UUID, ChatWindow> chatWindows = new HashMap<>();
    private static final Map<UUID, Integer> staffWarningCounts = new HashMap<>();
    /**
     * Chat is called on the server thread for every message. Never touch SQL from that path.
     * Active mutes are hydrated asynchronously on join/manual changes and enforced from this cache.
     */
    private static final Map<UUID, CachedMute> activeMuteCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static long resetKey = DailyResetManager.currentResetKeyMillis();

    private ModerationManager() {}

    public static void tick(MinecraftServer server) {
        long key = DailyResetManager.currentResetKeyMillis();
        if (key != resetKey) {
            records.clear();
            tempBans.clear();
            chatWindows.clear();
            staffWarningCounts.clear();
            activeMuteCache.clear();
            XrayDetectionManager.dailyReset();
            resetKey = key;
            alertAdmins(server, "§a[AutoMod] Daily moderation stages wiped at " + DailyResetManager.formatResetTime() + ". Chat, Xray, and AntiLag tracks were reset separately.");
            webhook("AutoMod daily reset: chat/xray/antilag stages cleared at " + DailyResetManager.formatResetTime() + ".");
        }
    }

    public static void handleJoin(ServerPlayer player) {
        if (player == null) return;
        tick(player.server);

        TempBan ban = tempBans.get(player.getUUID());
        long now = System.currentTimeMillis();
        if (ban != null) {
            if (ban.untilMillis > now) {
                player.connection.disconnect(Component.literal("You are temporarily banned for " + format(ban.untilMillis - now) + ". Reason: " + ban.reason));
                return;
            } else {
                tempBans.remove(player.getUUID());
            }
        }

        UUID uuid = player.getUUID();
        String name = player.getGameProfile().getName();
        MinecraftServer server = player.server;
        DatabaseManager.runAsync("active punishments check", connection -> {
            ModerationActionRepository.ActivePunishment dbMute = ModerationActionRepository.findActive(uuid, name, ModerationActionRepository.ActionType.MUTE);
            if (dbMute == null) activeMuteCache.remove(uuid);
            else activeMuteCache.put(uuid, CachedMute.from(dbMute));

            ModerationActionRepository.ActivePunishment dbBan = ModerationActionRepository.findActive(uuid, name, ModerationActionRepository.ActionType.BAN);
            if (dbBan == null) return;
            server.execute(() -> {
                ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                if (online == null || online.hasDisconnected()) return;
                String duration = dbBan.expiresAt() == null ? "permanently" : "for " + format(Math.max(1, dbBan.expiresAt().toEpochMilli() - System.currentTimeMillis()));
                online.connection.disconnect(Component.literal("You are banned " + duration + ". Reason: " + dbBan.reason()));
            });
        });
    }

    public static boolean allowChat(ServerPlayer player, String message) {
        if (player == null) return false;
        tick(player.server);
        CachedMute dbMute = activeMuteCache.get(player.getUUID());
        if (dbMute != null) {
            if (dbMute.isExpired()) {
                activeMuteCache.remove(player.getUUID());
            } else {
                String duration = dbMute.expiresAt == null ? "permanently" : "for " + format(Math.max(1, dbMute.expiresAt.toEpochMilli() - System.currentTimeMillis()));
                player.sendSystemMessage(Component.literal("You are muted " + duration + ". Reason: " + dbMute.reason).withStyle(ChatFormatting.RED));
                return false;
            }
        }
        Record r = record(player.getUUID(), ModerationTrack.CHAT);
        long now = System.currentTimeMillis();
        if (r.mutedUntil > now) {
            player.sendSystemMessage(Component.literal("You are muted for " + format(r.mutedUntil - now) + ".").withStyle(ChatFormatting.RED));
            return false;
        }
        if (!ModerationConfig.DATA.chatModEnabled) return true;
        String spamReason = spamViolation(player, message);
        if (spamReason != null) {
            report(player, ModerationTrack.CHAT.webhookLabel, spamReason + " | original=`" + trimForLog(message) + "`", true);
            advanceChatStage(player, r, spamReason);
            return false;
        }
        ChatViolation hit = blockedWord(message);
        if (hit == null) return true;

        String reason = hit.reason();
        report(player, ModerationTrack.CHAT.webhookLabel, reason + " | original=`" + trimForLog(message) + "` | normalized=`" + hit.normalizedMessage() + "`", true);
        advanceChatStage(player, r, reason);
        return false;
    }

    public static void manualEscalate(ServerPlayer actor, ServerPlayer target, String reason) {
        Record r = record(target.getUUID(), ModerationTrack.CHAT);
        advanceChatStage(target, r, reason == null ? "manual staff escalation" : reason);
        alertAdmins(target.server, "§c[AutoMod] §f" + actor.getGameProfile().getName() + " §7manually escalated chat AutoMod for §f" + target.getGameProfile().getName() + "§7. Reason: §e" + reason);
    }

    public static boolean canModerate(ServerPlayer p) {
        return p.hasPermissions(4) || LuckPermsHook.hasPermission(p, ModerationConfig.DATA.moderatorPermission);
    }

    public static void manualMute(String actorName, ServerPlayer target, Duration duration, String reason) {
        if (target == null || duration == null) return;
        tick(target.server);

        String staffName = (actorName == null || actorName.isBlank()) ? "Console" : actorName;
        String finalReason = (reason == null || reason.isBlank()) ? "Manual staff mute" : reason;
        long until = System.currentTimeMillis() + duration.toMillis();

        Record r = record(target.getUUID(), ModerationTrack.CHAT);
        r.mutedUntil = until;
        activeMuteCache.put(target.getUUID(), new CachedMute(finalReason, Instant.ofEpochMilli(until)));
        ModerationActionRepository.ActionDraft draft = ModerationActionRepository.draftOffline(null, target.getGameProfile().getName(), target.getUUID(), ModerationActionRepository.ActionType.MUTE, finalReason);
        draft.moderatorName = staffName;
        draft.expiresAt = Instant.ofEpochMilli(until);
        ModerationActionRepository.insert(draft);

        target.sendSystemMessage(Component.literal("You were muted by staff for " + format(duration.toMillis()) + ". Reason: " + finalReason).withStyle(ChatFormatting.RED));
        alertAdmins(target.server, "§c[Staff Mute] §f" + staffName + " §7muted §f" + target.getGameProfile().getName() + " §7for §e" + format(duration.toMillis()) + "§7. Reason: §c" + finalReason);
        webhook("Staff mute: actor=" + staffName + " | player=" + target.getGameProfile().getName() + " | duration=" + format(duration.toMillis()) + " | reason=" + finalReason);
    }

    public static boolean manualUnmute(String actorName, ServerPlayer target) {
        if (target == null) return false;
        tick(target.server);

        TrackKey key = new TrackKey(target.getUUID(), ModerationTrack.CHAT);
        Record r = records.get(key);
        long now = System.currentTimeMillis();
        boolean wasMuted = (r != null && r.mutedUntil > now) || activeMuteCache.containsKey(target.getUUID());
        if (r != null) {
            r.mutedUntil = 0L;
        }
        activeMuteCache.remove(target.getUUID());

        String staffName = (actorName == null || actorName.isBlank()) ? "Console" : actorName;
        if (wasMuted) {
            ModerationActionRepository.revokeActive(target.getUUID(), target.getGameProfile().getName(), ModerationActionRepository.ActionType.MUTE, null, "Legacy staff unmute", staffName);
            ModerationActionRepository.ActionDraft reversal = ModerationActionRepository.draftOffline(null, target.getGameProfile().getName(), target.getUUID(), ModerationActionRepository.ActionType.UNMUTE, "Legacy staff unmute");
            reversal.moderatorName = staffName;
            reversal.active = false;
            ModerationActionRepository.insert(reversal);
            target.sendSystemMessage(Component.literal("You have been unmuted by staff.").withStyle(ChatFormatting.GREEN));
            alertAdmins(target.server, "§a[Staff Unmute] §f" + staffName + " §7unmuted §f" + target.getGameProfile().getName() + "§7.");
            webhook("Staff unmute: actor=" + staffName + " | player=" + target.getGameProfile().getName());
        }
        return wasMuted;
    }

    public static boolean systemViolation(ServerPlayer player, String system, String reason, boolean confidentEnoughForAction) {
        if (player == null) return false;
        tick(player.server);

        ModerationTrack track = trackFor(system);
        report(player, track.webhookLabel, reason, confidentEnoughForAction);
        if (!confidentEnoughForAction) return false;

        if (player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("[" + track.label + "] You matched a rule, but ops are exempt: " + reason).withStyle(ChatFormatting.YELLOW));
            return false;
        }

        Record r = record(player.getUUID(), track);
        if (track == ModerationTrack.CHAT || track == ModerationTrack.GENERIC) {
            return advanceChatStage(player, r, track.label + ": " + reason);
        }
        return advanceSecurityStage(player, r, track, reason);
    }

    private static boolean advanceChatStage(ServerPlayer player, Record r, String reason) {
        r.offenses++;
        int stage = r.offenses;
        long now = System.currentTimeMillis();

        if (stage == 1) {
            player.sendSystemMessage(Component.literal(ModerationConfig.DATA.warnMessage).withStyle(ChatFormatting.RED));
            alertAdmins(player.server, "§e[Chat AutoMod] Warned §f" + player.getGameProfile().getName() + "§7. Stage 1. Reason: §c" + reason);
            webhook("Chat AutoMod action: warning | player=" + player.getGameProfile().getName() + " | stage=1 | reason=" + reason);
            return true;
        }

        if (stage == 2) {
            r.mutedUntil = now + Duration.ofMinutes(15).toMillis();
            notifyMute(player, "15 minutes", reason);
            alertAction(player, "Chat AutoMod", "15 minute mute", stage, reason);
        } else if (stage == 3) {
            r.mutedUntil = now + Duration.ofHours(1).toMillis();
            notifyMute(player, "1 hour", reason);
            alertAction(player, "Chat AutoMod", "1 hour mute", stage, reason);
        } else if (stage == 4) {
            r.mutedUntil = now + Duration.ofHours(2).toMillis();
            notifyMute(player, "2 hours", reason);
            alertAction(player, "Chat AutoMod", "2 hour mute", stage, reason);
        } else {
            applyTempBan(player, Duration.ofDays(1), "Chat AutoMod", stage, reason);
        }
        return true;
    }

    private static boolean advanceSecurityStage(ServerPlayer player, Record r, ModerationTrack track, String reason) {
        r.offenses++;
        int stage = r.offenses;

        if (stage == 1) {
            player.sendSystemMessage(Component.literal("[" + track.label + "] Staff were alerted to suspicious behavior. This is a warning. Reason: " + reason).withStyle(ChatFormatting.RED));
            alertAction(player, track.label, "warning", stage, reason);
        } else if (stage == 2) {
            alertAction(player, track.label, "kick", stage, reason);
            player.connection.disconnect(Component.literal("Kicked by " + track.label + ". Reason: " + reason));
        } else if (stage == 3) {
            applyTempBan(player, Duration.ofMinutes(30), track.label, stage, reason);
        } else if (stage == 4) {
            applyTempBan(player, Duration.ofHours(6), track.label, stage, reason);
        } else {
            applyTempBan(player, Duration.ofDays(1), track.label, stage, reason);
        }
        return true;
    }

    private static void applyTempBan(ServerPlayer player, Duration duration, String system, int stage, String reason) {
        long until = System.currentTimeMillis() + duration.toMillis();
        String durationText = format(duration.toMillis());
        tempBans.put(player.getUUID(), new TempBan(until, reason));
        ModerationActionRepository.ActionDraft draft = ModerationActionRepository.draftOffline(null, player.getGameProfile().getName(), player.getUUID(), ModerationActionRepository.ActionType.BAN, reason);
        draft.moderatorName = system;
        draft.expiresAt = Instant.ofEpochMilli(until);
        draft.metadataJson = "{\"source\":\"automod_escalation\",\"stage\":" + stage + "}";
        ModerationActionRepository.insert(draft);
        player.connection.disconnect(Component.literal("You are temporarily banned for " + durationText + " by " + system + ". Reason: " + reason));
        alertAction(player, system, durationText + " temporary ban", stage, reason);
    }

    private static void alertAction(ServerPlayer player, String system, String action, int stage, String reason) {
        alertAdmins(player.server, "§c[" + system + "] Applied §e" + action + " §7to §f" + player.getGameProfile().getName() + "§7. Stage " + stage + ". Reason: §c" + reason);
        webhook(system + " action: " + action + " | player=" + player.getGameProfile().getName() + " | stage=" + stage + " | reason=" + reason);
    }

    private static void report(ServerPlayer player, String system, String reason, boolean actionEligible) {
        String msg = "§6[" + system + "] §7Caught §f" + player.getGameProfile().getName() + "§7. " + reason + "§7. actionEligible=" + actionEligible;
        alertAdmins(player.server, msg);
        webhook(system + " caught: player=" + player.getGameProfile().getName() + " | actionEligible=" + actionEligible + " | " + reason);
    }

    public static void staffWarn(ServerPlayer actor, ServerPlayer target, String reason) {
        if (target == null) return;
        tick(target.server);

        String finalReason = cleanReason(reason, "Staff warning");
        int warningCount = currentStaffWarningCount(target);

        ModerationActionRepository.ActionDraft draft = ModerationActionRepository.draft(actor, target, ModerationActionRepository.ActionType.WARN, finalReason);
        draft.metadataJson = "{\"daily_warning_count\":" + warningCount + ",\"reset_key_millis\":" + resetKey + "}";
        ModerationActionRepository.insert(draft);

        target.sendSystemMessage(Component.literal("You received a staff warning (" + warningCount + "/5). Reason: " + finalReason).withStyle(ChatFormatting.YELLOW));
        String staffName = actorName(actor);
        alertAdmins(target.server, "§e[Staff Warn] §f" + staffName + " §7warned §f" + target.getGameProfile().getName() + "§7. Daily warnings: §e" + warningCount + "/5§7. Reason: §c" + finalReason);
        webhook("Staff warn: actor=" + staffName + " | player=" + target.getGameProfile().getName() + " | daily_warnings=" + warningCount + " | reason=" + finalReason);

        applyStaffWarningThreshold(actor, target, warningCount, finalReason);
    }

    private static int currentStaffWarningCount(ServerPlayer target) {
        // Hot command path: never block the server tick on SQL. The daily warning counter is
        // held in memory and all records are still persisted asynchronously. It resets at the
        // existing AutoMod daily reset, matching the intended staff warning behavior.
        int next = staffWarningCounts.getOrDefault(target.getUUID(), 0) + 1;
        staffWarningCounts.put(target.getUUID(), next);
        return next;
    }

    private static void applyStaffWarningThreshold(ServerPlayer actor, ServerPlayer target, int warningCount, String latestReason) {
        if (warningCount == 3) {
            staffMute(actor, target, Duration.ofHours(1), "Reached 3 staff warnings before the daily AutoMod reset. Latest warning: " + latestReason);
        } else if (warningCount == 4) {
            staffBan(actor, target, Duration.ofHours(1), false, "Reached 4 staff warnings before the daily AutoMod reset. Latest warning: " + latestReason);
        } else if (warningCount >= 5) {
            staffBan(actor, target, Duration.ofHours(24), false, "Reached 5 staff warnings before the daily AutoMod reset. Latest warning: " + latestReason);
        }
    }

    public static void staffKick(ServerPlayer actor, ServerPlayer target, String reason) {
        if (target == null) return;
        String finalReason = cleanReason(reason, "Staff kick");
        ModerationActionRepository.insert(ModerationActionRepository.draft(actor, target, ModerationActionRepository.ActionType.KICK, finalReason));
        String staffName = actorName(actor);
        alertAdmins(target.server, "§c[Staff Kick] §f" + staffName + " §7kicked §f" + target.getGameProfile().getName() + "§7. Reason: §c" + finalReason);
        webhook("Staff kick: actor=" + staffName + " | player=" + target.getGameProfile().getName() + " | reason=" + finalReason);
        target.connection.disconnect(Component.literal("Kicked from Cobble Champs. Reason: " + finalReason));
    }

    public static void staffMute(ServerPlayer actor, ServerPlayer target, Duration duration, String reason) {
        if (target == null || duration == null) return;
        String finalReason = cleanReason(reason, "Staff mute");
        ModerationActionRepository.ActionDraft draft = ModerationActionRepository.draft(actor, target, ModerationActionRepository.ActionType.MUTE, finalReason);
        draft.expiresAt = Instant.now().plus(duration);
        ModerationActionRepository.insert(draft);
        Record r = record(target.getUUID(), ModerationTrack.CHAT);
        r.mutedUntil = System.currentTimeMillis() + duration.toMillis();
        String staffName = actorName(actor);
        target.sendSystemMessage(Component.literal("You were muted for " + format(duration.toMillis()) + ". Reason: " + finalReason).withStyle(ChatFormatting.RED));
        alertAdmins(target.server, "§c[Staff Mute] §f" + staffName + " §7muted §f" + target.getGameProfile().getName() + " §7for §e" + format(duration.toMillis()) + "§7. Reason: §c" + finalReason);
        webhook("Staff mute: actor=" + staffName + " | player=" + target.getGameProfile().getName() + " | duration=" + format(duration.toMillis()) + " | reason=" + finalReason);
    }

    public static void staffBan(ServerPlayer actor, ServerPlayer target, Duration duration, boolean permanent, String reason) {
        if (target == null) return;
        String finalReason = cleanReason(reason, "Staff ban");
        ModerationActionRepository.ActionDraft draft = ModerationActionRepository.draft(actor, target, ModerationActionRepository.ActionType.BAN, finalReason);
        if (!permanent && duration != null) draft.expiresAt = Instant.now().plus(duration);
        ModerationActionRepository.insert(draft);
        String staffName = actorName(actor);
        String durationText = permanent ? "permanently" : "for " + format(duration.toMillis());
        alertAdmins(target.server, "§4[Staff Ban] §f" + staffName + " §7banned §f" + target.getGameProfile().getName() + " §7" + durationText + "§7. Reason: §c" + finalReason);
        webhook("Staff ban: actor=" + staffName + " | player=" + target.getGameProfile().getName() + " | duration=" + durationText + " | reason=" + finalReason);
        target.connection.disconnect(Component.literal("You are banned " + durationText + ". Reason: " + finalReason));
    }

    public static boolean staffUnmute(ServerPlayer actor, ServerPlayer target, String targetName, String reason) {
        UUID targetUuid = target == null ? null : target.getUUID();
        String name = target == null ? targetName : target.getGameProfile().getName();
        ModerationActionRepository.revokeActiveAsync(targetUuid, name, ModerationActionRepository.ActionType.MUTE, actor == null ? null : actor.getUUID(), cleanReason(reason, "Staff unmute"), actorName(actor));
        boolean changed = true;
        ModerationActionRepository.ActionDraft reversal = target == null
                ? ModerationActionRepository.draftOffline(actor, name, targetUuid, ModerationActionRepository.ActionType.UNMUTE, cleanReason(reason, "Staff unmute"))
                : ModerationActionRepository.draft(actor, target, ModerationActionRepository.ActionType.UNMUTE, cleanReason(reason, "Staff unmute"));
        reversal.active = false;
        ModerationActionRepository.insert(reversal);
        if (target != null) {
            Record r = record(target.getUUID(), ModerationTrack.CHAT);
            r.mutedUntil = 0L;
            target.sendSystemMessage(Component.literal("You have been unmuted. Reason: " + cleanReason(reason, "Staff unmute")).withStyle(ChatFormatting.GREEN));
            alertAdmins(target.server, "§a[Staff Unmute] §f" + actorName(actor) + " §7unmuted §f" + target.getGameProfile().getName() + "§7. Reason: §e" + cleanReason(reason, "Staff unmute"));
        }
        webhook("Staff unmute: actor=" + actorName(actor) + " | player=" + name + " | changed=" + changed + " | reason=" + cleanReason(reason, "Staff unmute"));
        return changed;
    }

    public static boolean staffUnban(ServerPlayer actor, String targetName, UUID targetUuid, MinecraftServer server, String reason) {
        ModerationActionRepository.revokeActiveAsync(targetUuid, targetName, ModerationActionRepository.ActionType.BAN, actor == null ? null : actor.getUUID(), cleanReason(reason, "Staff unban"), actorName(actor));
        boolean changed = true;
        ModerationActionRepository.ActionDraft reversal = ModerationActionRepository.draftOffline(actor, targetName, targetUuid, ModerationActionRepository.ActionType.UNBAN, cleanReason(reason, "Staff unban"));
        reversal.active = false;
        ModerationActionRepository.insert(reversal);
        if (server != null) alertAdmins(server, "§a[Staff Unban] §f" + actorName(actor) + " §7unbanned §f" + targetName + "§7. Reason: §e" + cleanReason(reason, "Staff unban"));
        webhook("Staff unban: actor=" + actorName(actor) + " | player=" + targetName + " | changed=" + changed + " | reason=" + cleanReason(reason, "Staff unban"));
        return changed;
    }

    public static java.util.List<ModerationActionRepository.ActionRecord> staffHistory(UUID targetUuid, String targetName) {
        return ModerationActionRepository.history(targetUuid, targetName);
    }

    private static String actorName(ServerPlayer actor) {
        return actor == null ? "Console" : actor.getGameProfile().getName();
    }

    private static String cleanReason(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason.trim();
    }

    private static Record record(UUID playerId, ModerationTrack track) {
        return records.computeIfAbsent(new TrackKey(playerId, track), id -> new Record());
    }

    private static ModerationTrack trackFor(String system) {
        if (system == null) return ModerationTrack.GENERIC;
        String normalized = system.toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "").replace("_", "");
        if (normalized.contains("chat")) return ModerationTrack.CHAT;
        if (normalized.contains("xray")) return ModerationTrack.XRAY;
        if (normalized.contains("antilag") || normalized.contains("lag") || normalized.contains("crash")) return ModerationTrack.ANTILAG;
        return ModerationTrack.GENERIC;
    }

    private static void notifyMute(ServerPlayer p, String dur, String reason) {
        p.sendSystemMessage(Component.literal("You are muted for " + dur + ". Reason: " + reason).withStyle(ChatFormatting.RED));
    }


    private static String spamViolation(ServerPlayer player, String message) {
        if (player == null || message == null) return null;
        long now = System.currentTimeMillis();
        long windowMs = 5000L;
        ChatWindow w = chatWindows.computeIfAbsent(player.getUUID(), id -> new ChatWindow(now + windowMs));
        if (now > w.expiresAt) {
            w.expiresAt = now + windowMs;
            w.count = 0;
            w.lastNormalized = "";
            w.repeatCount = 0;
        }
        w.count++;
        String normalized = normalizeChat(message);
        if (!normalized.isBlank() && normalized.equals(w.lastNormalized)) w.repeatCount++;
        else { w.lastNormalized = normalized; w.repeatCount = 1; }
        if (w.count >= 7) return "chat spam: too many messages in 5 seconds";
        if (w.repeatCount >= 3) return "chat spam: repeated message";
        if (message.length() > 240) return "chat spam: message too long";
        return null;
    }

    private static ChatViolation blockedWord(String msg) {
        if (msg == null || msg.isBlank()) return null;

        String lower = basicLower(msg);
        String normalized = normalizeChat(msg);
        if (normalized.isBlank()) return null;

        ChatViolation exact = matchCategory("slur/hate term", ModerationConfig.DATA.blockedExact, lower, normalized, true);
        if (exact != null) return exact;

        ChatViolation shortened = matchShortenedSlur(lower, normalized);
        if (shortened != null) return shortened;

        ChatViolation threat = matchCategory("severe threat", ModerationConfig.DATA.blockedSevereThreats, lower, normalized, false);
        if (threat != null) return threat;

        ChatViolation sexual = matchCategory("sexual harassment", ModerationConfig.DATA.blockedSexualHarassment, lower, normalized, false);
        if (sexual != null) return sexual;

        return null;
    }

    private static ChatViolation matchCategory(String category, List<String> patterns, String lower, String normalized, boolean requireRawWordBoundary) {
        if (patterns == null) return null;

        for (String rawPattern : patterns) {
            if (rawPattern == null || rawPattern.isBlank() || rawPattern.equalsIgnoreCase("replace_slurs_here")) continue;
            String pattern = basicLower(rawPattern.trim());
            String normalizedPattern = normalizeChat(pattern);
            if (normalizedPattern.isBlank()) continue;

            boolean rawMatch = requireRawWordBoundary
                    ? wordOrPhraseMatch(lower, pattern)
                    : lower.contains(pattern);
            if (rawMatch) {
                return new ChatViolation(category, rawPattern, false, normalized, category + ": " + rawPattern);
            }

            // Do not join normal words together for slur checks. This prevents false positives like
            // "does pickaxe" -> "doespickaxe" containing a blocked substring across the space.
            for (String token : normalizedTokens(lower)) {
                if (!isSafeNormalizedWord(token) && token.equals(normalizedPattern)) {
                    String extra = ModerationConfig.DATA.normalizedBypassExtraSeverity == null
                            ? "filter evasion"
                            : ModerationConfig.DATA.normalizedBypassExtraSeverity;
                    return new ChatViolation(category, rawPattern, true, normalized, category + " / " + extra + ": " + rawPattern);
                }
            }
            if (!lower.matches(".*\\s+.*") && normalized.length() <= Math.max(12, normalizedPattern.length() + 4) && !isSafeNormalizedWord(normalized) && normalized.contains(normalizedPattern)) {
                String extra = ModerationConfig.DATA.normalizedBypassExtraSeverity == null
                        ? "filter evasion"
                        : ModerationConfig.DATA.normalizedBypassExtraSeverity;
                return new ChatViolation(category, rawPattern, true, normalized, category + " / " + extra + ": " + rawPattern);
            }
        }
        return null;
    }


    private static ChatViolation matchShortenedSlur(String lower, String normalized) {
        List<String> patterns = ModerationConfig.DATA.blockedShortSlurs;
        if (patterns == null || patterns.isEmpty()) return null;

        List<String> normalizedTokens = normalizedTokens(lower);
        for (String rawPattern : patterns) {
            if (rawPattern == null || rawPattern.isBlank() || rawPattern.equalsIgnoreCase("replace_slurs_here")) continue;

            String pattern = normalizeChat(rawPattern.trim());
            if (pattern.length() < 3) continue;

            for (String token : normalizedTokens) {
                if (isShortenedSlurToken(token, pattern)) {
                    return new ChatViolation("shortened slur/hate term", rawPattern, !token.equals(rawPattern), normalized, "shortened slur/hate term: " + rawPattern);
                }
            }

            // Handles separator-only evasion like "n i g" or "n.i.g" while avoiding long normal words.
            if (normalized.equals(pattern) || isShortenedSlurToken(normalized, pattern)) {
                String extra = ModerationConfig.DATA.normalizedBypassExtraSeverity == null
                        ? "filter evasion"
                        : ModerationConfig.DATA.normalizedBypassExtraSeverity;
                return new ChatViolation("shortened slur/hate term", rawPattern, true, normalized, "shortened slur/hate term / " + extra + ": " + rawPattern);
            }
        }
        return null;
    }

    private static boolean isShortenedSlurToken(String token, String pattern) {
        if (token == null || token.isBlank() || pattern == null || pattern.isBlank()) return false;
        if (isSafeNormalizedWord(token)) return false;
        if (token.equals(pattern)) return true;

        // Allow repeated final-letter spam, e.g. "nigg" / "niggg", without matching words like "night".
        if (token.startsWith(pattern) && token.length() <= pattern.length() + 3) {
            char repeat = pattern.charAt(pattern.length() - 1);
            for (int i = pattern.length(); i < token.length(); i++) {
                if (token.charAt(i) != repeat) return false;
            }
            return true;
        }
        return false;
    }

    private static List<String> normalizedTokens(String input) {
        if (input == null || input.isBlank()) return Collections.emptyList();
        String[] rawTokens = input.split("[^\\p{L}\\p{N}]+");
        List<String> tokens = new ArrayList<>();
        for (String raw : rawTokens) {
            String token = normalizeChat(raw);
            if (!token.isBlank()) tokens.add(token);
        }
        return tokens;
    }

    private static boolean wordOrPhraseMatch(String lower, String pattern) {
        if (pattern.indexOf(' ') >= 0) return lower.contains(pattern);
        return lower.matches(".*(^|[^a-z0-9])" + Pattern.quote(pattern) + "([^a-z0-9]|$).*");
    }

    private static boolean isSafeNormalizedWord(String normalized) {
        if (ModerationConfig.DATA.safeWords == null) return false;
        for (String safe : ModerationConfig.DATA.safeWords) {
            String safeNorm = normalizeChat(safe);
            if (!safeNorm.isBlank() && normalized.equals(safeNorm)) return true;
        }
        return false;
    }

    private static String basicLower(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    public static String normalizeChat(String input) {
        if (input == null) return "";
        String normalized = basicLower(input);

        normalized = normalized
                .replace("@", "a").replace("4", "a")
                .replace("!", "i").replace("|", "i").replace("1", "i").replace("l", "i")
                .replace("3", "e")
                .replace("0", "o")
                .replace("5", "s").replace("$", "s")
                .replace("7", "t")
                .replace("+", "t");

        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        normalized = NON_ALPHANUMERIC.matcher(normalized).replaceAll("");
        normalized = REPEATED_CHARS.matcher(normalized).replaceAll("$1$1");
        return normalized;
    }

    private static String trimForLog(String text) {
        if (text == null) return "";
        String clean = text.replace("`", "'").replace("\n", " ").replace("\r", " ");
        return clean.length() <= 160 ? clean : clean.substring(0, 160) + "...";
    }

    public static void alertAdmins(MinecraftServer server, String message) {
        if (server == null || !ModerationConfig.DATA.alertAdmins) return;
        Component c = Component.literal(message);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.hasPermissions(4) || LuckPermsHook.hasPermission(p, ModerationConfig.DATA.adminAlertPermission)) p.sendSystemMessage(c);
        }
        System.out.println(message.replace('§', '&'));
    }

    public static void webhook(String text) {
        String hook = ModerationConfig.DATA.discordWebhookUrl;
        if ((hook == null || hook.isBlank()) && AntiLagConfig.DATA.discordWebhookUrl != null) hook = AntiLagConfig.DATA.discordWebhookUrl;
        if (hook == null || hook.isBlank()) return;
        String finalHook = hook;
        new Thread(() -> {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(finalHook).openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setDoOutput(true);
                String safe = text.replace("\\", "\\\\").replace("\"", "\\\"");
                String json = "{\"content\":\"" + safe + "\"}";
                try (OutputStream os = con.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                con.getInputStream().close();
            } catch (Exception ignored) {}
        }, "ChampUtils-DiscordWebhook").start();
    }

    private static String format(long ms) {
        long s = Math.max(1, ms / 1000);
        if (s >= 86400) return (s / 86400) + "d " + ((s % 86400) / 3600) + "h";
        if (s >= 3600) return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
        if (s >= 60) return (s / 60) + "m " + (s % 60) + "s";
        return s + "s";
    }

    private static final class CachedMute {
        final String reason;
        final Instant expiresAt;

        CachedMute(String reason, Instant expiresAt) {
            this.reason = reason == null || reason.isBlank() ? "Muted" : reason;
            this.expiresAt = expiresAt;
        }

        static CachedMute from(ModerationActionRepository.ActivePunishment punishment) {
            return new CachedMute(punishment.reason(), punishment.expiresAt());
        }

        boolean isExpired() {
            return expiresAt != null && expiresAt.toEpochMilli() <= System.currentTimeMillis();
        }
    }

    private record TrackKey(UUID playerId, ModerationTrack track) {}

    private record ChatViolation(String category, String matched, boolean evasion, String normalizedMessage, String reason) {}

    private static final class Record {
        int offenses;
        long mutedUntil;
    }

    private record TempBan(long untilMillis, String reason) {}

    private static final class ChatWindow {
        int count;
        int repeatCount;
        long expiresAt;
        String lastNormalized = "";
        ChatWindow(long expiresAt) { this.expiresAt = expiresAt; }
    }
}
