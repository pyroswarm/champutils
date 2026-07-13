package com.champutils.chat;

import com.champutils.guild.GuildRepository;
import com.champutils.moderation.ModerationManager;
import com.champutils.network.NetworkEventManager;
import com.champutils.party.PartyManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public final class ServerChatManager {
    private ServerChatManager() {}

    public static boolean handleChat(ServerPlayer sender, String rawMessage) {
        if (sender == null) return false;
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isBlank()) return false;
        return send(sender, ChatPreferenceManager.get(sender.getUUID()), message, true);
    }

    public static boolean send(ServerPlayer sender, ChatMode mode, String message, boolean warn) {
        if (mode == null) mode = ChatMode.LOCAL;
        if (!ModerationManager.allowChat(sender, message)) return false;
        List<ServerPlayer> recipients = recipients(sender, mode);
        if (recipients.isEmpty()) {
            if (warn) sender.sendSystemMessage(unavailable(mode));
            return false;
        }
        Component formatted = format(sender, mode, message);
        for (ServerPlayer player : recipients) player.sendSystemMessage(formatted);
        publishCrossServer(sender, mode, message);
        return true;
    }

    public static List<ServerPlayer> recipients(ServerPlayer sender, ChatMode mode) {
        List<ServerPlayer> result = new ArrayList<>();
        if (sender == null || sender.server == null) return result;
        List<ServerPlayer> online = sender.server.getPlayerList().getPlayers();
        switch (mode) {
            case LOCAL -> {
                for (ServerPlayer player : online) if (player.level().dimension().equals(sender.level().dimension())) result.add(player);
            }
            case GLOBAL -> result.addAll(online);
            case GUILD -> {
                GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(sender.getUUID());
                if (guild == null) return result;
                for (ServerPlayer player : online) {
                    GuildRepository.GuildSnapshot other = GuildRepository.cachedGuild(player.getUUID());
                    if (other != null && guild.id.equals(other.id)) result.add(player);
                }
            }
            case PARTY -> result.addAll(PartyManager.onlineMembers(sender));
        }
        return result;
    }

    private static Component unavailable(ChatMode mode) {
        if (mode == ChatMode.GUILD) return Component.literal("You are not in a guild.").withStyle(ChatFormatting.RED);
        if (mode == ChatMode.PARTY) return Component.literal("You are not in a chat party yet.").withStyle(ChatFormatting.RED);
        return Component.literal("No one can hear you in that chat.").withStyle(ChatFormatting.YELLOW);
    }

    public static MutableComponent format(ServerPlayer sender, ChatMode mode, String message) {
        MutableComponent component = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(mode.prefix).withStyle(mode.color))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
        component.append(ChatTagResolver.tagsFor(sender));
        NicknameManager.refreshIfStale(sender);
        component.append(NicknameManager.displayComponent(sender));
        component.append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
        component.append(Component.literal(message).withStyle(ChatFormatting.WHITE));
        return component;
    }

    private static void publishCrossServer(ServerPlayer sender, ChatMode mode, String message) {
        if (sender == null || mode == null || message == null || message.isBlank()) {
            return;
        }
        if (mode == ChatMode.GLOBAL) {
            NetworkEventManager.publishGlobalChat(sender, plainFormat(sender, mode, message));
        }
        else if (mode == ChatMode.GUILD) {
            GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(sender.getUUID());
            if (guild != null) {
                NetworkEventManager.publishGuildChat(sender, guild.id, plainFormat(sender, mode, message));
            }
        }
        else if (mode == ChatMode.PARTY) {
            PartyManager.PartySnapshot party = PartyManager.snapshot(sender.getUUID());
            if (party != null) {
                NetworkEventManager.publishPartyChat(sender, party.ownerId(), plainFormat(sender, mode, message));
            }
        }
    }

    private static String plainFormat(ServerPlayer sender, ChatMode mode, String message) {
        String tags = ChatTagResolver.tagsForLegacy(sender);
        String color = switch (mode) {
            case GLOBAL -> "§b";
            case GUILD -> "§a";
            case PARTY -> "§d";
            case LOCAL -> "§7";
        };
        return "§8[" + color + mode.prefix + "§8] §r" + tags + "§f" + NicknameManager.displayName(sender) + "§7: §f" + message;
    }
}
