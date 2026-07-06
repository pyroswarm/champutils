package com.champutils.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfileGameMode;
import com.champutils.permissions.LuckPermsHook;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

public final class ChatTagResolver {
    private static final long CACHE_TTL_MS = 30_000L;
    private static final Map<UUID, CachedTags> TAG_CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> GUILD_REFRESH_PENDING = ConcurrentHashMap.newKeySet();

    private ChatTagResolver() {}

    public static MutableComponent tagsFor(ServerPlayer player) {
        if (player == null) return Component.empty();
        CachedTags cached = TAG_CACHE.get(player.getUUID());
        long now = System.currentTimeMillis();
        if (cached != null && cached.profileId().equals(PlayerProfileManager.activeProfileId(player)) && now - cached.createdAtMillis() <= CACHE_TTL_MS) {
            return cached.component().copy();
        }

        MutableComponent result = Component.empty();

        appendProfileIcon(result, player);

        MutableComponent rankTag = rankTagFor(player);
        if (rankTag != null) {
            result.append(rankTag).append(Component.literal(" "));
        }

        String selectedTitle = com.champutils.cosmetic.TitleManager.selected(player.getUUID());
        if (selectedTitle != null && !selectedTitle.isBlank()) {
            String titleDisplay = com.champutils.cosmetic.TitleManager.displayFor(player.getUUID(), selectedTitle);
            if (titleDisplay != null && !titleDisplay.isBlank()) {
                MutableComponent titleComponent = legacy(titleDisplay);
                Component hover = com.champutils.cosmetic.TitleConfig.hoverText(selectedTitle);
                titleComponent.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
                result.append(titleComponent).append(Component.literal(" "));
            }
        }

        List<ChatTagConfig.TagDefinition> tags = new ArrayList<>(ChatTagConfig.INSTANCE.tags);
        for (ChatTagConfig.TagDefinition tag : tags) {
            if (tag == null || tag.display == null || tag.display.isBlank()) continue;
            if (tag.permission != null && !tag.permission.isBlank() && !player.hasPermissions(4) && !hasPermission(player, tag.permission)) continue;
            result.append(legacy(tag.display)).append(Component.literal(" "));
        }

        com.champutils.guild.GuildRepository.GuildSnapshot guild = com.champutils.guild.GuildRepository.cachedGuild(player.getUUID());
        if (guild == null) {
            // Never do blocking database fallback from the chat thread. A missing cache means the
            // guild tag may be absent for one message, then the async refresh will fill it.
            requestGuildRefresh(player);
        } else if (guild.tag != null && !guild.tag.isBlank()) {
            result.append(Component.literal("[" + guild.tag + "]").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)).append(Component.literal(" "));
        }

        if (ChatTagConfig.INSTANCE.showLuckPermsSuffix) {
            String suffix = luckPermsMeta(player, "getSuffix");
            if (suffix != null && !suffix.isBlank()) result.append(legacy(suffix)).append(Component.literal(" "));
        }

        TAG_CACHE.put(player.getUUID(), new CachedTags(PlayerProfileManager.activeProfileId(player), result.copy(), now));
        return result;
    }


    private static void appendProfileIcon(MutableComponent result, ServerPlayer player) {
        ProfileGameMode profileMode = PlayerProfileManager.gameMode(player);
        if (profileMode == ProfileGameMode.NORMAL) {
            result.append(Component.literal("🌿").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)).append(Component.literal(" "));
        } else if (profileMode == ProfileGameMode.IRONMAN) {
            result.append(Component.literal("⚒").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD)).append(Component.literal(" "));
        } else if (profileMode == ProfileGameMode.MONOTYPE) {
            String type = PlayerProfileManager.monotypeType(player);
            if (type == null || type.isBlank()) type = "Unknown";
            result.append(Component.literal(typeEmoji(type)).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)).append(Component.literal(" "));
        } else if (profileMode == ProfileGameMode.NUZLOCKE) {
            result.append(Component.literal("☠").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)).append(Component.literal(" "));
        } else if (profileMode == ProfileGameMode.ISLANDER) {
            result.append(Component.literal("🏝").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)).append(Component.literal(" "));
        }
    }

    private static MutableComponent rankTagFor(ServerPlayer player) {
        if (player == null) return null;

        if (LuckPermsHook.hasGroup(player, "vipplus")
                || LuckPermsHook.hasPermission(player, "champutils.rank.vipplus")
                || LuckPermsHook.hasPermission(player, "champutils.profiles.vipplus")
                || LuckPermsHook.hasPermission(player, "champutils.boosters.daily.vipplus")) {
            return Component.literal("[VIP+]").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        }

        if (LuckPermsHook.hasGroup(player, "vip")
                || LuckPermsHook.hasPermission(player, "champutils.rank.vip")
                || LuckPermsHook.hasPermission(player, "champutils.profiles.vip")) {
            return Component.literal("[VIP]").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        }

        if (ChatTagConfig.INSTANCE.showLuckPermsPrefix) {
            String prefix = luckPermsMeta(player, "getPrefix");
            if (prefix != null && !prefix.isBlank()) {
                prefix = removeDeprecatedRankTags(prefix);
                if (!prefix.isBlank()) return legacy(prefix);
            }
        }

        return null;
    }

    private static void requestGuildRefresh(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        if (!GUILD_REFRESH_PENDING.add(uuid)) return;
        String name = player.getGameProfile().getName();
        try {
            com.champutils.guild.GuildRepository.loadForPlayer(uuid, name);
        } catch (Throwable ignored) {
            GUILD_REFRESH_PENDING.remove(uuid);
        }
    }

    public static void invalidate(ServerPlayer player) {
        if (player != null) {
            TAG_CACHE.remove(player.getUUID());
            GUILD_REFRESH_PENDING.remove(player.getUUID());
        }
    }

    public static void invalidate(UUID uuid) {
        if (uuid != null) {
            TAG_CACHE.remove(uuid);
            GUILD_REFRESH_PENDING.remove(uuid);
        }
    }

    public static void clearCache() {
        TAG_CACHE.clear();
        GUILD_REFRESH_PENDING.clear();
    }

    private record CachedTags(UUID profileId, MutableComponent component, long createdAtMillis) {}

    private static String removeDeprecatedRankTags(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("(?i)&[0-9a-fk-or]?\\[(helper|champion)\\]", "")
                .replaceAll("(?i)§[0-9a-fk-or]?\\[(helper|champion)\\]", "")
                .replaceAll("(?i)\\[(helper|champion)\\]", "")
                .trim();
    }

    private static String typeEmoji(String raw) {
        if (raw == null) return "🔹";
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "fire" -> "🔥"; case "water" -> "💧"; case "grass" -> "🍃"; case "electric" -> "⚡";
            case "ice" -> "❄"; case "fighting" -> "🥊"; case "poison" -> "☠"; case "ground" -> "⛰";
            case "flying" -> "🪽"; case "psychic" -> "🔮"; case "bug" -> "🐛"; case "rock" -> "🪨";
            case "ghost" -> "👻"; case "dragon" -> "🐉"; case "dark" -> "🌑"; case "steel" -> "⚙";
            case "fairy" -> "✨"; case "normal" -> "⭐"; default -> "🔹";
        };
    }

    private static String prettyType(String raw) {
        if (raw == null || raw.isBlank()) return "Unknown";
        String lower = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + lower.substring(1);
    }

    private static boolean hasPermission(ServerPlayer player, String permission) {
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = providerClass.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class).invoke(userManager, player.getUUID());
            if (user == null) return player.hasPermissions(4);
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object permissionData = cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);
            Object result = permissionData.getClass().getMethod("checkPermission", String.class).invoke(permissionData, permission);
            Method asBoolean = result.getClass().getMethod("asBoolean");
            return Boolean.TRUE.equals(asBoolean.invoke(result));
        } catch (Exception ignored) {
            return player.hasPermissions(4);
        }
    }

    private static String luckPermsMeta(ServerPlayer player, String methodName) {
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = providerClass.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class).invoke(userManager, player.getUUID());
            if (user == null) return "";
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            Object value = metaData.getClass().getMethod(methodName).invoke(metaData);
            return value == null ? "" : value.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    public static MutableComponent legacy(String raw) {
        MutableComponent out = Component.empty();
        ChatFormatting active = ChatFormatting.WHITE;
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < raw.length()) {
                if (buffer.length() > 0) {
                    ChatFormatting style = active;
                    out.append(Component.literal(buffer.toString()).withStyle(style));
                    buffer.setLength(0);
                }
                ChatFormatting next = color(raw.charAt(++i));
                if (next != null) active = next;
            } else {
                buffer.append(c);
            }
        }
        if (buffer.length() > 0) {
            ChatFormatting style = active;
            out.append(Component.literal(buffer.toString()).withStyle(style));
        }
        return out;
    }

    private static ChatFormatting color(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> ChatFormatting.BLACK;
            case '1' -> ChatFormatting.DARK_BLUE;
            case '2' -> ChatFormatting.DARK_GREEN;
            case '3' -> ChatFormatting.DARK_AQUA;
            case '4' -> ChatFormatting.DARK_RED;
            case '5' -> ChatFormatting.DARK_PURPLE;
            case '6' -> ChatFormatting.GOLD;
            case '7' -> ChatFormatting.GRAY;
            case '8' -> ChatFormatting.DARK_GRAY;
            case '9' -> ChatFormatting.BLUE;
            case 'a' -> ChatFormatting.GREEN;
            case 'b' -> ChatFormatting.AQUA;
            case 'c' -> ChatFormatting.RED;
            case 'd' -> ChatFormatting.LIGHT_PURPLE;
            case 'e' -> ChatFormatting.YELLOW;
            case 'f' -> ChatFormatting.WHITE;
            default -> null;
        };
    }
}
