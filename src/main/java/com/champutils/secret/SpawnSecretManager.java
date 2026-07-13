package com.champutils.secret;

import com.champutils.cosmetic.TitleManager;
import com.champutils.cosmetic.TitleConfig;
import com.champutils.protection.SpawnRealmProtectionListener;
import com.champutils.worldfirst.WorldFirstManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.SignBlock;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnSecretManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/spawn_secrets.json");
    private static Data data = new Data();
    private static final Map<UUID, String> pending = new ConcurrentHashMap<>();
    private static final String[] QUESTIONS = {
            "What is Shedinja's signature ability called?",
            "My body's yellow, my cheeks round. I can be recognized by my sound. I evolve only once, and my line has a baby. What Pokemon am I?",
            "What Pokemon has the dual type of Normal and Water?"
    };
    private static final String[] ANSWERS = {"wonderguard", "chingling", "bibarel"};
    private SpawnSecretManager() {}

    public static void register() {
        load();
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)) return InteractionResult.PASS;
            if (!(level.getBlockState(hit.getBlockPos()).getBlock() instanceof SignBlock)) return InteractionResult.PASS;
            String bind = pending.remove(sp.getUUID());
            if (bind != null) {
                data.bindings.put(bind, Key.of(level, hit.getBlockPos())); save();
                sp.sendSystemMessage(Component.literal("Bound this sign as " + bind + ".").withStyle(ChatFormatting.GREEN));
                return InteractionResult.SUCCESS;
            }
            String type = bindingAt(level, hit.getBlockPos());
            if (type == null) return InteractionResult.PASS;
            if (type.equals("secretstart1")) start(sp);
            else if (type.equals("secretcomplete1")) complete(sp);
            return InteractionResult.SUCCESS;
        });
    }

    public static int beginBind(ServerPlayer player, String raw) {
        String type = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if (!type.equals("secretstart1") && !type.equals("secretcomplete1")) {
            player.sendSystemMessage(Component.literal("Valid types: secretstart1, secretcomplete1").withStyle(ChatFormatting.RED)); return 0;
        }
        pending.put(player.getUUID(), type);
        player.sendSystemMessage(Component.literal("Right-click the sign to bind as " + type + ".").withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    public static boolean consumeChat(ServerPlayer player, String message) {
        int stage = data.progress.getOrDefault(player.getUUID().toString(), 0);
        if (stage < 1 || stage > 3) return false;
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (!normalized.equals(ANSWERS[stage - 1])) {
            player.sendSystemMessage(Component.literal("That is not the answer. Try again.").withStyle(ChatFormatting.RED));
            return true;
        }
        int next = stage + 1;
        data.progress.put(player.getUUID().toString(), next); save();
        if (next <= 3) ask(player, next);
        else {
            player.sendSystemMessage(Component.literal("All three answers are correct.").withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(Component.literal("Where cheers grow quiet and champions depart,\nThe answer waits behind the heart.\nSeek not the front where battles begin,\nBut where the last footsteps fade within").withStyle(ChatFormatting.GOLD));
        }
        return true;
    }

    private static void start(ServerPlayer player) {
        int stage = data.progress.getOrDefault(player.getUUID().toString(), 0);
        if (stage >= 4) { player.sendSystemMessage(Component.literal("You have solved the riddles. Follow the location clue.").withStyle(ChatFormatting.GOLD)); return; }
        data.progress.put(player.getUUID().toString(), 1); save(); ask(player, 1);
    }
    private static void ask(ServerPlayer player, int stage) {
        player.sendSystemMessage(Component.literal("Secret Riddle " + stage + "/3: " + QUESTIONS[stage - 1]).withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("Answer in chat.").withStyle(ChatFormatting.GRAY));
    }
    private static void complete(ServerPlayer player) {
        String id = player.getUUID().toString();
        int stage = data.progress.getOrDefault(id, 0);
        if (stage < 4) return;
        boolean newlyCompleted = data.completed.add(id);
        if (newlyCompleted) save();
        String titleId = TitleConfig.findTitleIdByUnlockType("spawn_secret");
        boolean titleUnlocked = titleId != null && TitleManager.unlock(player, titleId);
        boolean first = WorldFirstManager.awardByTrigger(player, "spawn_secret");
        String titleName = titleId == null ? "configured secret title" : Optional.ofNullable(TitleConfig.get(titleId)).map(t -> t.name).orElse(titleId);
        player.sendSystemMessage(Component.literal("Secret discovered!" + (titleUnlocked ? " You unlocked the " + titleName + " title." : "") + (first ? " You also claimed the world first!" : "")).withStyle(ChatFormatting.GOLD));
    }
    private static String bindingAt(ServerLevel level, BlockPos pos) {
        Key key = Key.of(level, pos);
        for (var entry : data.bindings.entrySet()) if (entry.getValue() != null && entry.getValue().equals(key)) return entry.getKey();
        return null;
    }
    private static void load() { try { FILE.getParentFile().mkdirs(); if (FILE.exists()) try (Reader r = new FileReader(FILE)) { Data d = GSON.fromJson(r, Data.class); if (d != null) data = d; } save(); } catch (Exception e) { e.printStackTrace(); } }
    private static void save() { try { FILE.getParentFile().mkdirs(); try (Writer w = new FileWriter(FILE)) { GSON.toJson(data, w); } } catch (Exception e) { e.printStackTrace(); } }
    private static final class Data { Map<String, Key> bindings = new LinkedHashMap<>(); Map<String,Integer> progress = new HashMap<>(); Set<String> completed = new HashSet<>(); }
    private record Key(String world, int x, int y, int z) { static Key of(ServerLevel l, BlockPos p) { return new Key(l.dimension().location().toString(), p.getX(), p.getY(), p.getZ()); } }
}
