package com.champutils.guild;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class GuildCommand {

    private GuildCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("guild")
                    .executes(context -> info(context.getSource().getPlayerOrException()))
                    .then(Commands.literal("info")
                            .executes(context -> info(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("buffs")
                            .executes(context -> buffs(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("create")
                            .then(Commands.argument("name", StringArgumentType.string())
                                    .executes(context -> create(
                                            context.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(context, "name"),
                                            null
                                    ))
                                    .then(Commands.argument("tag", StringArgumentType.string())
                                            .executes(context -> create(
                                                    context.getSource().getPlayerOrException(),
                                                    StringArgumentType.getString(context, "name"),
                                                    StringArgumentType.getString(context, "tag")
                                            )))))
                    .then(Commands.literal("invite")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> invite(
                                            context.getSource().getPlayerOrException(),
                                            EntityArgument.getPlayer(context, "player")
                                    ))))
                    .then(Commands.literal("accept")
                            .executes(context -> accept(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("deny")
                            .executes(context -> deny(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("leave")
                            .executes(context -> leave(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("kick")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> kick(
                                            context.getSource().getPlayerOrException(),
                                            EntityArgument.getPlayer(context, "player")
                                    ))))
                    .then(Commands.literal("promote")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> promote(
                                            context.getSource().getPlayerOrException(),
                                            EntityArgument.getPlayer(context, "player")
                                    ))))
                    .then(Commands.literal("demote")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> demote(
                                            context.getSource().getPlayerOrException(),
                                            EntityArgument.getPlayer(context, "player")
                                    ))))
                    .then(Commands.literal("chat")
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(context -> guildChat(
                                            context.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(context, "message")
                                    ))))
                    .then(Commands.literal("debugreload")
                            .requires(source -> source.hasPermission(4))
                            .executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                GuildConfig.load();
                                GuildBuffConfig.load();
                                GuildRepository.loadForPlayer(player.getUUID(), player.getGameProfile().getName());
                                player.sendSystemMessage(Component.literal("Reloaded your guild cache and guild buff config.").withStyle(ChatFormatting.GREEN));
                                return 1;
                            })));

            dispatcher.register(Commands.literal("g")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(context -> guildChat(
                                    context.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(context, "message")
                            ))));
        });
    }

    private static int create(ServerPlayer player, String name, String tag) {
        String cleanName = GuildRepository.cleanName(name);
        String cleanTag = GuildRepository.cleanTag(tag);

        if (cleanName.length() < 3) {
            player.sendSystemMessage(Component.literal("Guild names must be at least 3 characters.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (cleanTag != null && cleanTag.length() < 2) {
            player.sendSystemMessage(Component.literal("Guild tags must be 2-5 letters/numbers.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!com.champutils.database.DatabaseManager.isEnabled()) {
            player.sendSystemMessage(Component.literal("The database is not connected, so guild creation is unavailable.").withStyle(ChatFormatting.RED));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Creating guild in the database...").withStyle(ChatFormatting.YELLOW));
        GuildRepository.createGuild(player.getUUID(), player.getGameProfile().getName(), cleanName, cleanTag, (success, message) ->
                player.server.execute(() -> player.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED)))
        );
        return 1;
    }

    private static int invite(ServerPlayer inviter, ServerPlayer target) {
        if (!databaseReady(inviter)) {
            return 0;
        }
        GuildRepository.invite(inviter.getUUID(), inviter.getGameProfile().getName(), target.getUUID(), target.getGameProfile().getName(), (success, message) ->
                inviter.server.execute(() -> {
                    inviter.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
                    if (success) {
                        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(inviter.getUUID());
                        target.sendSystemMessage(Component.literal(inviter.getGameProfile().getName() + " invited you to join " + (guild == null ? "their guild" : guild.name) + ". Use /guild accept or /guild deny.").withStyle(ChatFormatting.GOLD));
                    }
                })
        );
        return 1;
    }

    private static int accept(ServerPlayer player) {
        if (!databaseReady(player)) {
            return 0;
        }
        GuildRepository.acceptInvite(player.getUUID(), player.getGameProfile().getName(), (success, message) ->
                player.server.execute(() -> player.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED)))
        );
        return 1;
    }

    private static int deny(ServerPlayer player) {
        if (!databaseReady(player)) {
            return 0;
        }
        GuildRepository.denyInvites(player.getUUID(), (success, message) ->
                player.server.execute(() -> player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.YELLOW)))
        );
        return 1;
    }

    private static int leave(ServerPlayer player) {
        if (!databaseReady(player)) {
            return 0;
        }
        GuildRepository.leave(player.getUUID(), player.getGameProfile().getName(), (success, message) ->
                player.server.execute(() -> player.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED)))
        );
        return 1;
    }

    private static int kick(ServerPlayer actor, ServerPlayer target) {
        if (!databaseReady(actor)) {
            return 0;
        }
        GuildRepository.kick(actor.getUUID(), target.getUUID(), target.getGameProfile().getName(), (success, message) ->
                actor.server.execute(() -> {
                    actor.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
                    if (success) {
                        target.sendSystemMessage(Component.literal("You were kicked from your guild.").withStyle(ChatFormatting.RED));
                    }
                })
        );
        return 1;
    }

    private static int promote(ServerPlayer actor, ServerPlayer target) {
        if (!databaseReady(actor)) {
            return 0;
        }
        GuildRepository.promote(actor.getUUID(), target.getUUID(), target.getGameProfile().getName(), (success, message) ->
                actor.server.execute(() -> {
                    actor.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
                    if (success) {
                        target.sendSystemMessage(Component.literal("Your guild role changed. Use /guild info to check it.").withStyle(ChatFormatting.GOLD));
                    }
                })
        );
        return 1;
    }

    private static int demote(ServerPlayer actor, ServerPlayer target) {
        if (!databaseReady(actor)) {
            return 0;
        }
        GuildRepository.demote(actor.getUUID(), target.getUUID(), target.getGameProfile().getName(), (success, message) ->
                actor.server.execute(() -> {
                    actor.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
                    if (success) {
                        target.sendSystemMessage(Component.literal("Your guild role changed. Use /guild info to check it.").withStyle(ChatFormatting.GOLD));
                    }
                })
        );
        return 1;
    }

    private static int guildChat(ServerPlayer player, String message) {
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(player.getUUID());
        if (guild == null) {
            player.sendSystemMessage(Component.literal("You are not in a guild.").withStyle(ChatFormatting.RED));
            return 0;
        }

        String cleanMessage = message == null ? "" : message.trim();
        if (cleanMessage.isBlank()) {
            player.sendSystemMessage(Component.literal("Usage: /g <message>").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        Component formatted = Component.literal("[Guild] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(player.getGameProfile().getName() + ": ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(cleanMessage).withStyle(ChatFormatting.WHITE));

        for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
            GuildRepository.GuildSnapshot otherGuild = GuildRepository.cachedGuild(online.getUUID());
            if (otherGuild != null && guild.id.equals(otherGuild.id)) {
                online.sendSystemMessage(formatted);
            }
        }
        return 1;
    }

    private static int info(ServerPlayer player) {
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(player.getUUID());

        if (guild == null) {
            player.sendSystemMessage(Component.literal("You are not in a guild. Use /guild create <name> [tag].").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        long currentLevelXp = GuildConfig.xpIntoCurrentLevel(guild.xp);
        long neededForNext = GuildConfig.xpNeededForNextLevel(guild.xp);
        String xpLine = neededForNext <= 0
                ? "XP: " + guild.xp + " / MAX"
                : "XP: " + currentLevelXp + " / " + neededForNext;

        player.sendSystemMessage(Component.literal("Guild: " + guild.name + (guild.tag == null ? "" : " [" + guild.tag + "]")).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("Guild Level: " + guild.level).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal(xpLine + " | Total XP: " + guild.xp).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Members: " + guild.memberCount + " | Your role: " + guild.role).withStyle(ChatFormatting.GRAY));

        java.util.List<Component> activeBuffs = GuildBuffManager.activeBuffLines(player.getUUID());
        java.util.List<Component> nextBuffs = GuildBuffManager.nextBuffLines(player.getUUID());
        player.sendSystemMessage(Component.literal("Guild Buffs").withStyle(ChatFormatting.GOLD));
        if (activeBuffs.isEmpty()) {
            player.sendSystemMessage(Component.literal("✦ No active buffs yet.").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (Component line : activeBuffs) player.sendSystemMessage(line);
        }
        for (Component line : nextBuffs) player.sendSystemMessage(line);
        return 1;
    }

    private static int buffs(ServerPlayer player) {
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(player.getUUID());
        if (guild == null) {
            player.sendSystemMessage(Component.literal("You are not in a guild. Use /guild create <name> [tag].").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        java.util.List<Component> activeBuffs = GuildBuffManager.activeBuffLines(player.getUUID());
        java.util.List<Component> nextBuffs = GuildBuffManager.nextBuffLines(player.getUUID());

        player.sendSystemMessage(Component.literal("Guild Buffs - " + guild.name + " Lv. " + guild.level).withStyle(ChatFormatting.GOLD));
        if (activeBuffs.isEmpty()) {
            player.sendSystemMessage(Component.literal("✦ No active buffs yet.").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (Component line : activeBuffs) player.sendSystemMessage(line);
        }

        if (!nextBuffs.isEmpty()) {
            player.sendSystemMessage(Component.literal("Upcoming Guild Buffs").withStyle(ChatFormatting.GRAY));
            for (Component line : nextBuffs) player.sendSystemMessage(line);
        }
        return 1;
    }

    private static boolean databaseReady(ServerPlayer player) {
        if (!com.champutils.database.DatabaseManager.isEnabled()) {
            player.sendSystemMessage(Component.literal("The database is not connected, so guild actions are unavailable.").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }
}
