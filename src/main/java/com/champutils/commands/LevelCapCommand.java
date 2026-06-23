package com.champutils.commands;

import com.champutils.xplock.XpLockManager;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class LevelCapCommand {
    private static final Map<UUID, Integer> PLAYER_CAPS = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/levelcaps.json");
    private static int tickCounter = 0;
    private LevelCapCommand() {}

    public static void register() {
        load();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter % 100 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) applyStoredCap(player);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("levelcap")
                        .executes(ctx -> status(ctx.getSource()))
                        .then(literal("set")
                                .then(argument("level", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> setCap(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "level")))))
                        .then(literal("on").executes(ctx -> enableCap(ctx.getSource())))
                        .then(literal("off").executes(ctx -> disableCap(ctx.getSource())))
        ));
    }

    private static synchronized void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) return;
            try (FileReader reader = new FileReader(FILE)) {
                java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, Integer>>(){}.getType();
                Map<String, Integer> loaded = GSON.fromJson(reader, type);
                if (loaded == null) return;
                PLAYER_CAPS.clear();
                for (Map.Entry<String, Integer> entry : loaded.entrySet()) {
                    try { PLAYER_CAPS.put(UUID.fromString(entry.getKey()), Math.max(1, Math.min(100, entry.getValue()))); } catch (Throwable ignored) {}
                }
            }
        } catch (Exception e) { System.err.println("[ChampUtils] Failed to load levelcaps.json"); e.printStackTrace(); }
    }

    private static synchronized void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Map<String, Integer> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<UUID, Integer> entry : PLAYER_CAPS.entrySet()) out.put(entry.getKey().toString(), entry.getValue());
            try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(out, writer); }
        } catch (Exception e) { System.err.println("[ChampUtils] Failed to save levelcaps.json"); e.printStackTrace(); }
    }

    private static int status(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Only players can use this command.")); return 0; }
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int cap = storedCap(player);
        if (cap <= 0) cap = firstPartyCap(party);
        if (cap > 0) {
            int finalCap = cap;
            source.sendSuccess(() -> Component.literal("Your party levelcap is ON at level " + finalCap + ". Use /levelcap set <level> to change it or /levelcap off to disable it.").withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.literal("Your party levelcap is OFF. Use /levelcap set <level> or /levelcap on.").withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int setCap(CommandSourceStack source, int level) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Only players can use this command.")); return 0; }
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party == null) { source.sendFailure(Component.literal("Could not access your Cobblemon party.")); return 0; }
        PLAYER_CAPS.put(player.getUUID(), level);
        save();
        int applied = applyCap(party, level);
        int finalApplied = applied;
        source.sendSuccess(() -> Component.literal("Your party levelcap is now ON at level " + level + ". Future EXP is blocked once each Pokémon reaches the cap. Applied to " + finalApplied + " Pokémon.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int enableCap(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Only players can use this command.")); return 0; }
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party == null) { source.sendFailure(Component.literal("Could not access your Cobblemon party.")); return 0; }
        int existing = storedCap(player);
        if (existing <= 0) existing = firstPartyCap(party);
        int level = existing > 0 ? existing : 100;
        PLAYER_CAPS.put(player.getUUID(), level);
        save();
        int applied = applyCap(party, level);
        int finalApplied = applied;
        int finalLevel = level;
        source.sendSuccess(() -> Component.literal("Your party levelcap is ON at level " + finalLevel + ". Future EXP is blocked once each Pokémon reaches the cap. Applied to " + finalApplied + " Pokémon. Use /levelcap set <level> to choose a different cap.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int disableCap(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Only players can use this command.")); return 0; }
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party == null) { source.sendFailure(Component.literal("Could not access your Cobblemon party.")); return 0; }
        PLAYER_CAPS.remove(player.getUUID());
        save();
        int cleared = 0;
        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon == null) continue;
            XpLockManager.clearLevelCap(pokemon);
            cleared++;
        }
        int finalCleared = cleared;
        source.sendSuccess(() -> Component.literal("Your party levelcap is OFF. Cleared level caps from " + finalCleared + " Pokémon.").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int storedCap(ServerPlayer player) {
        if (player == null) return 0;
        return Math.max(0, Math.min(100, PLAYER_CAPS.getOrDefault(player.getUUID(), 0)));
    }

    public static void applyStoredCap(ServerPlayer player) {
        int cap = storedCap(player);
        if (cap <= 0) return;
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party != null) applyCap(party, cap);
    }

    private static int applyCap(PartyStore party, int level) {
        int applied = 0;
        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon == null) continue;
            XpLockManager.setLevelCap(pokemon, level);
            // Do NOT lower existing Pokémon. Levelcap only prevents future EXP/levelups once the cap is reached.
            applied++;
        }
        return applied;
    }

    private static int firstPartyCap(PartyStore party) {
        if (party == null) return 0;
        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party.get(i);
            int cap = XpLockManager.getLevelCap(pokemon);
            if (cap > 0) return cap;
        }
        return 0;
    }
}
