package com.champutils.flan;

import com.champutils.profile.PlayerProfileManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FlanOnlineClaimBlockManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File STATE_FILE = new File("config/champutils/flan_claim_blocks_state.json");
    private static final Map<String, PlayerState> STATES = new HashMap<>();

    private FlanOnlineClaimBlockManager() {
    }

    public static void load() {
        try {
            File parent = STATE_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            if (!STATE_FILE.exists()) {
                save();
                return;
            }

            try (FileReader reader = new FileReader(STATE_FILE)) {
                StateRoot root = GSON.fromJson(reader, StateRoot.class);
                STATES.clear();
                if (root != null && root.players != null) {
                    STATES.putAll(root.players);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            STATES.clear();
        }
    }

    public static void save() {
        try {
            File parent = STATE_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            StateRoot root = new StateRoot();
            root.players = new HashMap<>(STATES);
            try (FileWriter writer = new FileWriter(STATE_FILE)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void handleJoin(ServerPlayer player) {
        if (player == null) return;
        STATES.computeIfAbsent(player.getUUID().toString(), ignored -> new PlayerState()).lastKnownName = player.getGameProfile().getName();
    }

    public static void handleDisconnect(ServerPlayer player) {
        if (player == null) return;
        PlayerState state = STATES.computeIfAbsent(player.getUUID().toString(), ignored -> new PlayerState());
        state.lastKnownName = player.getGameProfile().getName();
        save();
    }

    public static void tick(MinecraftServer server) {
        if (server == null || !FlanOnlineClaimBlockConfig.enabled()) return;
        if (server.getTickCount() <= 0 || server.getTickCount() % 1200 != 0) return;

        int intervalSeconds = FlanOnlineClaimBlockConfig.intervalMinutes() * 60;
        int reward = FlanOnlineClaimBlockConfig.rewardClaimBlocks();
        if (intervalSeconds <= 0 || reward <= 0) return;

        boolean changed = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null || PlayerProfileManager.isInMainMenu(player)) continue;

            PlayerState state = STATES.computeIfAbsent(player.getUUID().toString(), ignored -> new PlayerState());
            state.lastKnownName = player.getGameProfile().getName();
            state.onlineSeconds += 60;

            int intervals = state.onlineSeconds / intervalSeconds;
            if (intervals <= 0) {
                changed = true;
                continue;
            }

            int amount = intervals * reward;
            FlanClaimBlockCompat.Result result = FlanClaimBlockCompat.addClaimBlocks(player, amount);
            if (result.success) {
                state.onlineSeconds -= intervals * intervalSeconds;
                player.sendSystemMessage(Component.literal(formatRewardMessage(amount)).withStyle(ChatFormatting.GOLD));
            } else {
                System.err.println("[ChampUtils] Failed to award Flan claim blocks to " + player.getGameProfile().getName() + ": " + result.message);
            }
            changed = true;
        }

        if (changed && server.getTickCount() % 6000 == 0) {
            save();
        }
    }

    public static int grantNow(ServerPlayer player, int amount) {
        FlanClaimBlockCompat.Result result = FlanClaimBlockCompat.addClaimBlocks(player, amount);
        if (!result.success) {
            player.sendSystemMessage(Component.literal(result.message == null ? "Could not add Flan claim blocks." : result.message).withStyle(ChatFormatting.RED));
            return 0;
        }
        player.sendSystemMessage(Component.literal(formatRewardMessage(amount)).withStyle(ChatFormatting.GOLD));
        return 1;
    }

    public static int pendingSeconds(UUID uuid) {
        PlayerState state = STATES.get(uuid.toString());
        return state == null ? 0 : Math.max(0, state.onlineSeconds);
    }

    private static String formatRewardMessage(int amount) {
        return FlanOnlineClaimBlockConfig.rewardMessage()
                .replace("{amount}", String.valueOf(amount))
                .replace("{claim_blocks}", String.valueOf(amount));
    }

    private static final class StateRoot {
        Map<String, PlayerState> players = new HashMap<>();
    }

    private static final class PlayerState {
        int onlineSeconds = 0;
        String lastKnownName = "";
    }
}
