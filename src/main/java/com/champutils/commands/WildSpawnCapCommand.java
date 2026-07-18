package com.champutils.commands;

import com.champutils.badge.BadgeManager;
import com.champutils.database.SharedJsonStateRepository;
import com.champutils.badge.BadgeType;
import com.champutils.gym.GymConfig;
import com.champutils.gym.GymLevelCapUtil;
import com.champutils.permissions.PermissionUtil;
import com.champutils.profile.IslanderSpawnInfluence;
import com.champutils.profile.PlayerProfileManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class WildSpawnCapCommand {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/wild_spawn_caps.json");
    private static Data data = new Data();
    private static final String STATE_KEY = "wild_spawn_cap";
    private static boolean loaded = false;

    private WildSpawnCapCommand() {}

    public static synchronized void preload(UUID profileId) {
        if (profileId == null) return;
        ensureLoaded();
        int fallback = data.profileCaps.getOrDefault(profileId.toString(), 0);
        CapState shared = SharedJsonStateRepository.loadProfile(profileId, STATE_KEY, CapState.class, new CapState(fallback));
        int cap = shared == null ? fallback : Math.max(0, Math.min(100, shared.level));
        if (cap <= 0) data.profileCaps.remove(profileId.toString()); else data.profileCaps.put(profileId.toString(), cap);
        save();
    }

    private static void persist(UUID profileId) {
        if (profileId == null) return;
        SharedJsonStateRepository.saveProfile(profileId, STATE_KEY, new CapState(data.profileCaps.getOrDefault(profileId.toString(), 0)));
        save();
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(literal("setcap")
                .requires(source -> PermissionUtil.has(source, "champutils.command.setcap") || PermissionUtil.has(source, "champutils.rank.vip"))
                .then(literal("off").executes(ctx -> { setCap(ctx.getSource().getPlayerOrException(), 0); return 1; }))
                .then(literal("status").executes(ctx -> { status(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(argument("level", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> { setCap(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "level")); return 1; }))
                .executes(ctx -> { status(ctx.getSource().getPlayerOrException()); return 1; })));
    }

    public static int capFor(ServerPlayer player) {
        ensureLoaded();
        if (player == null) return 0;
        int gymCap = currentGymCap(player);
        if (gymCap <= 0) return 0;

        UUID profile = PlayerProfileManager.activeProfileId(player);
        Integer requested = data.profileCaps.get(profile == null ? "" : profile.toString());

        // Gym progression is the default cap. /setcap is only a player preference to go lower,
        // not a required toggle. This keeps new players from seeing high-level wild spawns before
        // earning badges, while still letting VIPs choose a stricter personal cap.
        if (requested == null || requested <= 0) return gymCap;
        return Math.min(requested, gymCap);
    }

    public static void applyToWildSpawn(ServerPlayer player, Pokemon pokemon) {
        if (player == null || pokemon == null) return;
        int cap = capFor(player);
        if (cap <= 0) return;

        // Wild Pokemon are shared server entities. Never reroll their level from a nearby
        // player's profile, because that mutates the same Pokemon for every viewer and can
        // fire again during capture/battle lifecycle events. Only enforce a hard upper cap.
        if (pokemon.getLevel() > cap) {
            pokemon.setLevel(Math.max(1, Math.min(100, cap)));
        }
    }

    public static int currentGymCap(ServerPlayer player) {
        return GymLevelCapUtil.currentWildCap(player);
    }

    private static void setCap(ServerPlayer player, int requested) {
        ensureLoaded();
        UUID profile = PlayerProfileManager.activeProfileId(player);
        if (profile == null) {
            player.sendSystemMessage(Component.literal("No active profile is loaded yet.").withStyle(ChatFormatting.RED));
            return;
        }
        if (requested <= 0) {
            data.profileCaps.remove(profile.toString());
            persist(profile);
            player.sendSystemMessage(Component.literal("Custom wild spawn cap removed. Gym progression cap still applies automatically.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        int gymCap = currentGymCap(player);
        int capped = Math.min(requested, gymCap);
        data.profileCaps.put(profile.toString(), capped);
        persist(profile);
        player.sendSystemMessage(Component.literal("Wild spawn cap set to level " + capped + " for this profile. Your current gym cap is " + gymCap + ".").withStyle(ChatFormatting.GREEN));
    }

    private static void status(ServerPlayer player) {
        int cap = capFor(player);
        int gymCap = currentGymCap(player);
        UUID profile = PlayerProfileManager.activeProfileId(player);
        Integer requested = data.profileCaps.get(profile == null ? "" : profile.toString());
        if (requested == null || requested <= 0) {
            player.sendSystemMessage(Component.literal("Wild spawn cap: " + gymCap + " from gym progression. No custom lower cap is set.").withStyle(ChatFormatting.AQUA));
        } else {
            player.sendSystemMessage(Component.literal("Wild spawn cap: " + cap + " (custom cap " + requested + ", current gym cap " + gymCap + ").").withStyle(ChatFormatting.AQUA));
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();
            if (FILE.exists()) {
                try (FileReader reader = new FileReader(FILE)) {
                    Data loadedData = GSON.fromJson(reader, Data.class);
                    if (loadedData != null) data = loadedData;
                }
            }
            if (data.profileCaps == null) data.profileCaps = new HashMap<>();
        } catch (Exception e) {
            e.printStackTrace();
            data = new Data();
        }
    }

    private static void save() {
        try {
            if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(data, writer); }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final class CapState { int level; CapState() {} CapState(int level) { this.level = level; } }
    private static final class Data {
        Map<String, Integer> profileCaps = new HashMap<>();
    }
}
