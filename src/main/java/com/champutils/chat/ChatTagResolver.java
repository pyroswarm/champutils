package com.champutils.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ChatTagResolver {
    private ChatTagResolver() {}

    public static MutableComponent tagsFor(ServerPlayer player) {
        MutableComponent result = Component.empty();

        if (ChatTagConfig.INSTANCE.showLuckPermsPrefix) {
            String prefix = luckPermsMeta(player, "getPrefix");
            if (prefix != null && !prefix.isBlank()) result.append(legacy(prefix)).append(Component.literal(" "));
        }

        List<ChatTagConfig.TagDefinition> tags = new ArrayList<>(ChatTagConfig.INSTANCE.tags);
        for (ChatTagConfig.TagDefinition tag : tags) {
            if (tag == null || tag.display == null || tag.display.isBlank()) continue;
            if (tag.permission != null && !tag.permission.isBlank() && !player.hasPermissions(4) && !hasPermission(player, tag.permission)) continue;
            result.append(legacy(tag.display)).append(Component.literal(" "));
        }

        String selectedTitle = com.champutils.cosmetic.TitleManager.selected(player.getUUID());
        if (selectedTitle != null && !selectedTitle.isBlank()) {
            String titleDisplay = com.champutils.cosmetic.TitleManager.displayFor(selectedTitle);
            if (titleDisplay != null && !titleDisplay.isBlank()) {
                result.append(legacy(titleDisplay)).append(Component.literal(" "));
            }
        }

        if (ChatTagConfig.INSTANCE.showLuckPermsSuffix) {
            String suffix = luckPermsMeta(player, "getSuffix");
            if (suffix != null && !suffix.isBlank()) result.append(legacy(suffix)).append(Component.literal(" "));
        }

        return result;
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
