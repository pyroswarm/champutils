package com.champutils.chat;

import com.champutils.guild.GuildRepository;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
        List<ServerPlayer> recipients = recipients(sender, mode);
        if (recipients.isEmpty()) {
            if (warn) sender.sendSystemMessage(unavailable(mode));
            return false;
        }
        Component formatted = format(sender, mode, message);
        for (ServerPlayer player : recipients) player.sendSystemMessage(formatted);
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
            case PARTY -> {
                // Placeholder resolver for future party systems. For now, party chat is disabled instead of leaking messages globally.
            }
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
        component.append(Component.literal(sender.getGameProfile().getName()).withStyle(ChatFormatting.WHITE));
        component.append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
        component.append(Component.literal(message).withStyle(ChatFormatting.WHITE));
        return component;
    }
}
