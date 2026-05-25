package com.champutils.moderation;

import com.champutils.antilag.AntiLagConfig;
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
    private static long resetKey = DailyResetManager.currentResetKeyMillis();

    private ModerationManager() {}

    public static void tick(MinecraftServer server) {
        long key = DailyResetManager.currentResetKeyMillis();
        if (key != resetKey) {
            records.clear();
            tempBans.clear();
            chatWindows.clear();
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
            } else {
                tempBans.remove(player.getUUID());
            }
        }
    }

    public static boolean allowChat(ServerPlayer player, String message) {
        if (player == null) return false;
        tick(player.server);
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
        boolean wasMuted = r != null && r.mutedUntil > now;
        if (r != null) {
            r.mutedUntil = 0L;
        }

        String staffName = (actorName == null || actorName.isBlank()) ? "Console" : actorName;
        if (wasMuted) {
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

            if (!isSafeNormalizedWord(normalized) && normalized.contains(normalizedPattern)) {
                String extra = ModerationConfig.DATA.normalizedBypassExtraSeverity == null
                        ? "filter evasion"
                        : ModerationConfig.DATA.normalizedBypassExtraSeverity;
                return new ChatViolation(category, rawPattern, true, normalized, category + " / " + extra + ": " + rawPattern);
            }
        }
        return null;
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
