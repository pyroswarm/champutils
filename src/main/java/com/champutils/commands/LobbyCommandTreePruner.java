package com.champutils.commands;

import com.champutils.network.NetworkServerConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * PROFILE_LOBBY command-tree safety filter.
 *
 * Velocity is disconnecting OP players when the lobby sends the full modded command tree.
 * The profile lobby does not need survival gameplay/mod commands, so keep only vanilla setup
 * commands plus the two ChampUtils lobby commands. This preserves OP setup ability while
 * preventing unrelated mod command argument types from being serialized to Velocity.
 */
public final class LobbyCommandTreePruner {
    private static final Set<String> ALLOWED_ROOTS = new HashSet<>();

    static {
        // ChampUtils lobby commands.
        ALLOWED_ROOTS.add("profiles");
        ALLOWED_ROOTS.add("menunpc");
        ALLOWED_ROOTS.add("spawnblanknpc");
        ALLOWED_ROOTS.add("npcedit");
        ALLOWED_ROOTS.add("npcdelete");

        // F Rank vanilla setup/admin commands needed to build and manage the lobby.
        ALLOWED_ROOTS.add("advancement");
        ALLOWED_ROOTS.add("attribute");
        ALLOWED_ROOTS.add("ban");
        ALLOWED_ROOTS.add("ban-ip");
        ALLOWED_ROOTS.add("banlist");
        ALLOWED_ROOTS.add("clear");
        ALLOWED_ROOTS.add("clone");
        ALLOWED_ROOTS.add("damage");
        ALLOWED_ROOTS.add("data");
        ALLOWED_ROOTS.add("datapack");
        ALLOWED_ROOTS.add("debug");
        ALLOWED_ROOTS.add("defaultgamemode");
        ALLOWED_ROOTS.add("deop");
        ALLOWED_ROOTS.add("difficulty");
        ALLOWED_ROOTS.add("effect");
        ALLOWED_ROOTS.add("enchant");
        ALLOWED_ROOTS.add("execute");
        ALLOWED_ROOTS.add("experience");
        ALLOWED_ROOTS.add("xp");
        ALLOWED_ROOTS.add("fill");
        ALLOWED_ROOTS.add("fillbiome");
        ALLOWED_ROOTS.add("forceload");
        ALLOWED_ROOTS.add("function");
        ALLOWED_ROOTS.add("gamemode");
        ALLOWED_ROOTS.add("gamerule");
        ALLOWED_ROOTS.add("give");
        ALLOWED_ROOTS.add("help");
        ALLOWED_ROOTS.add("item");
        ALLOWED_ROOTS.add("jfr");
        ALLOWED_ROOTS.add("kick");
        ALLOWED_ROOTS.add("kill");
        ALLOWED_ROOTS.add("list");
        ALLOWED_ROOTS.add("locate");
        ALLOWED_ROOTS.add("loot");
        ALLOWED_ROOTS.add("me");
        ALLOWED_ROOTS.add("op");
        ALLOWED_ROOTS.add("pardon");
        ALLOWED_ROOTS.add("pardon-ip");
        ALLOWED_ROOTS.add("particle");
        ALLOWED_ROOTS.add("perf");
        ALLOWED_ROOTS.add("place");
        ALLOWED_ROOTS.add("playsound");
        ALLOWED_ROOTS.add("publish");
        ALLOWED_ROOTS.add("recipe");
        ALLOWED_ROOTS.add("reload");
        ALLOWED_ROOTS.add("return");
        ALLOWED_ROOTS.add("ride");
        ALLOWED_ROOTS.add("rotate");
        ALLOWED_ROOTS.add("save-all");
        ALLOWED_ROOTS.add("save-off");
        ALLOWED_ROOTS.add("save-on");
        ALLOWED_ROOTS.add("say");
        ALLOWED_ROOTS.add("schedule");
        ALLOWED_ROOTS.add("scoreboard");
        ALLOWED_ROOTS.add("seed");
        ALLOWED_ROOTS.add("setblock");
        ALLOWED_ROOTS.add("setidletimeout");
        ALLOWED_ROOTS.add("setworldspawn");
        ALLOWED_ROOTS.add("spawnpoint");
        ALLOWED_ROOTS.add("spectate");
        ALLOWED_ROOTS.add("spreadplayers");
        ALLOWED_ROOTS.add("stop");
        ALLOWED_ROOTS.add("stopsound");
        ALLOWED_ROOTS.add("summon");
        ALLOWED_ROOTS.add("tag");
        ALLOWED_ROOTS.add("team");
        ALLOWED_ROOTS.add("teammsg");
        ALLOWED_ROOTS.add("tm");
        ALLOWED_ROOTS.add("teleport");
        ALLOWED_ROOTS.add("tellraw");
        ALLOWED_ROOTS.add("tick");
        ALLOWED_ROOTS.add("time");
        ALLOWED_ROOTS.add("title");
        ALLOWED_ROOTS.add("tp");
        ALLOWED_ROOTS.add("transfer");
        ALLOWED_ROOTS.add("trigger");
        ALLOWED_ROOTS.add("weather");
        ALLOWED_ROOTS.add("whitelist");
    }

    private LobbyCommandTreePruner() {}

    public static void register() {
        if (NetworkServerConfig.serverRole() != NetworkServerConfig.ServerRole.PROFILE_LOBBY) return;
        ServerLifecycleEvents.SERVER_STARTED.register(LobbyCommandTreePruner::pruneNow);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void pruneNow(MinecraftServer server) {
        if (NetworkServerConfig.serverRole() != NetworkServerConfig.ServerRole.PROFILE_LOBBY) return;

        try {
            CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
            RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();

            Set<String> before = new HashSet<>();
            for (CommandNode<CommandSourceStack> child : root.getChildren()) {
                before.add(child.getName());
            }

            Field childrenField = CommandNode.class.getDeclaredField("children");
            Field literalsField = CommandNode.class.getDeclaredField("literals");
            Field argumentsField = CommandNode.class.getDeclaredField("arguments");
            childrenField.setAccessible(true);
            literalsField.setAccessible(true);
            argumentsField.setAccessible(true);

            Map children = (Map) childrenField.get(root);
            Map literals = (Map) literalsField.get(root);
            Map arguments = (Map) argumentsField.get(root);

            children.keySet().removeIf(name -> !ALLOWED_ROOTS.contains(String.valueOf(name)));
            literals.keySet().removeIf(name -> !ALLOWED_ROOTS.contains(String.valueOf(name)));
            arguments.keySet().removeIf(name -> !ALLOWED_ROOTS.contains(String.valueOf(name)));

            Set<String> after = new HashSet<>();
            for (CommandNode<CommandSourceStack> child : root.getChildren()) {
                after.add(child.getName());
            }

            before.removeAll(after);
        } catch (Throwable ignored) {
        }
    }
}
