package com.champutils.quest;

import com.champutils.profile.PlayerProfileManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One persisted Adventurer Board objective/contract tracker per profile. */
public final class QuestTrackerManager {
    private static final Map<UUID, CustomBossEvent> BARS = new ConcurrentHashMap<>();
    private QuestTrackerManager() {}

    public static boolean isTracked(QuestDataManager.QuestData data, String kind, QuestDataManager.Objective objective) {
        if (data == null || objective == null || data.trackedKind == null) return false;
        if (!data.trackedKind.equalsIgnoreCase(kind)) return false;
        if ("contract".equalsIgnoreCase(kind) && objective instanceof QuestDataManager.Contract c) {
            return data.trackedContractPurchasedAt == c.purchasedAtMillis;
        }
        return data.trackedObjectiveId != null && data.trackedObjectiveId.equalsIgnoreCase(objective.id);
    }

    public static void track(ServerPlayer player, String kind, QuestDataManager.Objective objective) {
        if (player == null || objective == null) return;
        QuestDataManager.QuestData data = QuestManager.getData(player);
        data.trackedKind = kind;
        data.trackedObjectiveId = objective.id;
        data.trackedContractPurchasedAt = objective instanceof QuestDataManager.Contract c ? c.purchasedAtMillis : 0L;
        QuestManager.markTrackerDirty(player);
        refresh(player);
        player.displayClientMessage(Component.literal("§aNow tracking: §f" + objective.description), true);
    }

    public static void toggle(ServerPlayer player, String kind, QuestDataManager.Objective objective) {
        if (player == null || objective == null) return;
        QuestDataManager.QuestData data = QuestManager.getData(player);
        if (isTracked(data, kind, objective)) {
            clear(player);
            player.displayClientMessage(Component.literal("§7Quest tracking cleared."), true);
        } else {
            track(player, kind, objective);
        }
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        QuestDataManager.QuestData data = QuestManager.getData(player);
        data.trackedKind = null; data.trackedObjectiveId = null; data.trackedContractPurchasedAt = 0L;
        QuestManager.markTrackerDirty(player);
        remove(player);
    }

    public static void refresh(ServerPlayer player) {
        if (player == null) return;
        QuestDataManager.QuestData data = QuestManager.getData(player);
        QuestDataManager.Objective objective = resolve(data);
        if (objective == null) { remove(player); return; }
        int required = Math.max(1, objective.required);
        int progress = Math.max(0, Math.min(objective.progress, required));
        UUID profile = PlayerProfileManager.activeProfileId(player);
        CustomBossEvent bar = BARS.get(profile);
        if (bar == null) {
            bar = new CustomBossEvent(ResourceLocation.fromNamespaceAndPath("champutils", "quest_tracker_" + profile.toString().replace("-", "")), Component.literal("Tracked Quest"));
            bar.setColor(BossEvent.BossBarColor.YELLOW);
            bar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
            bar.addPlayer(player);
            BARS.put(profile, bar);
        }
        String prefix = "contract".equalsIgnoreCase(data.trackedKind) ? "§6Tracked Contract §7- §f" : "§eTracked Quest §7- §f";
        bar.setName(Component.literal(prefix + objective.description + " §7(" + progress + "/" + required + ")"));
        bar.setProgress(progress / (float) required);
    }

    public static void remove(ServerPlayer player) {
        if (player == null) return;
        CustomBossEvent bar = BARS.remove(PlayerProfileManager.activeProfileId(player));
        if (bar != null) bar.removeAllPlayers();
    }

    private static QuestDataManager.Objective resolve(QuestDataManager.QuestData data) {
        if (data == null || data.trackedKind == null) return null;
        if ("daily".equalsIgnoreCase(data.trackedKind)) return find(data.daily, data.trackedObjectiveId);
        if ("weekly".equalsIgnoreCase(data.trackedKind)) return find(data.weekly, data.trackedObjectiveId);
        if ("contract".equalsIgnoreCase(data.trackedKind) && data.contracts != null) {
            long now = System.currentTimeMillis();
            for (QuestDataManager.Contract c : data.contracts) if (c != null && !c.completed && now < c.expiresAtMillis && c.purchasedAtMillis == data.trackedContractPurchasedAt) return c;
        }
        return null;
    }

    private static QuestDataManager.Objective find(QuestDataManager.QuestSet set, String id) {
        if (set == null || set.completed || set.objectives == null || id == null) return null;
        for (QuestDataManager.Objective o : set.objectives) if (o != null && id.equalsIgnoreCase(o.id)) return o;
        return null;
    }
}
