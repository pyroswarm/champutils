package com.champutils.commands;

import com.champutils.permissions.PermissionUtil;
import com.champutils.permissions.LuckPermsHook;
import com.champutils.adventurer.AdventurerGuildManager;
import com.champutils.adventureguide.AdventureGuideManager;
import com.champutils.profile.PlayerProfileManager;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Server-wide command guards.
 *
 * Important: some external mods register commands directly, so registration-level wrappers are not enough.
 * The CommandsLockedLobbyMixin calls this class at command execution time so restricted commands are blocked
 * even when Cobblemon/CobblemonExtras registered the winning command node.
 */
public final class CommandBlocker {
    private CommandBlocker() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("script")
                    .requires(source -> source.hasPermission(4))
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .executes(ctx -> 0))
                    .executes(ctx -> 0));

            dispatcher.register(Commands.literal("trigger")
                    .requires(source -> source.hasPermission(4))
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .executes(ctx -> 0))
                    .executes(ctx -> 0));
        });
    }

    /**
     * Returns true when the command was denied and command execution should be cancelled.
     */
    public static boolean denyIfBlocked(CommandSourceStack source, String command) {
        if (source == null || command == null) return false;

        ParsedCommand parsed = ParsedCommand.parse(command);
        if (parsed.root.isEmpty()) return false;

        // Console stays able to use real command paths for internal server actions.
        if (source.getEntity() == null) return false;

        // /msg, /tell and /w are intentionally gone for every player, including OPs.
        // Staff should use /pm too so there is only one private messaging path to support.
        ServerPlayer sourcePlayer = source.getPlayer();
        if (isVanillaPrivateMessageRoot(parsed.root)) {
            deny(source, Component.literal("§c/msg is disabled on this server. Use §d/pm <player> <message>§c or §d/r <message>§c."));
            return true;
        }


        if (sourcePlayer != null && !sourcePlayer.hasPermissions(4) && AdventureGuideManager.isLockedUntilTalk(sourcePlayer) && !isFirstGuideAllowedRoot(parsed.root)) {
            deny(source, Component.literal("§eTalk to the Adventurer's Guild Representative first. §7They are beside you at spawn."));
            return true;
        }

        String permission = requiredPermission(parsed.root);
        if (permission == null) return false;

        // Do not let vanilla OP bypass VIP progression commands. Staff still bypass through explicit LuckPerms nodes.
        if (sourcePlayer != null && (LuckPermsHook.hasExactPermissionNode(sourcePlayer, "champutils.admin") || LuckPermsHook.hasExactPermissionNode(sourcePlayer, "champutils.staff"))) return false;

        if (sourcePlayer != null && !sourcePlayer.hasPermissions(4) && !PlayerProfileManager.hasActiveProfile(sourcePlayer)) {
            deny(source, Component.literal("§cSelect and load a profile before using this command."));
            return true;
        }

        // /pokeheal <player> and /healpokemon <player> should remain staff/admin only.
        // VIP should only get self-heal.
        if ((parsed.root.equals("pokeheal") || parsed.root.equals("healpokemon") || parsed.root.equals("healparty") || parsed.root.equals("pokehealother") || parsed.root.equals("pokemonheal")) && !parsed.arguments.isBlank()) {
            deny(source, Component.literal("§cYou can only use /pokeheal on yourself."));
            return true;
        }

        // /pokeivs <player> / /ivs <player> should remain staff/admin only as well.
        if ((parsed.root.equals("pokeivs") || parsed.root.equals("ivs")) && !parsed.arguments.isBlank()) {
            deny(source, Component.literal("§cYou can only use this command on yourself."));
            return true;
        }

        if (sourcePlayer != null && AdventurerGuildManager.isAttemptingBattleTower(sourcePlayer) && isBattleTowerHealRoot(parsed.root)) {
            deny(source, Component.literal("§cYou cannot heal or access Pokémon storage during a Battle Tower attempt. You will be healed at checkpoints."));
            return true;
        }

        if (sourcePlayer == null || !PermissionUtil.has(source, permission)) {
            deny(source, denyMessageFor(parsed.root));
            return true;
        }

        return false;
    }

    public static boolean isBlockedRoot(String command) {
        if (command == null) return false;
        String root = ParsedCommand.parse(command).root;
        return root.equals("script") || root.equals("trigger") || isVanillaPrivateMessageRoot(root);
    }

    private static boolean isVanillaPrivateMessageRoot(String root) {
        return root != null && (root.equals("msg") || root.equals("tell") || root.equals("w"));
    }

    private static boolean isFirstGuideAllowedRoot(String root) {
        return root != null && (root.equals("help")
                || root.equals("discord")
                || root.equals("rules")
                || root.equals("profile")
                || root.equals("profiles"));
    }

    private static String requiredPermission(String root) {
        return switch (root) {
            case "ec", "enderchest" -> "champutils.command.ec";
            case "pc" -> "champutils.command.pc";
            case "pokeheal", "healpokemon", "healparty", "pokehealother", "pokemonheal" -> "champutils.command.pokeheal";
            case "pokeivs", "ivs" -> "champutils.command.pokeivs";
            default -> null;
        };
    }

    private static boolean isBattleTowerHealRoot(String root) {
        return root != null && (root.equals("pc")
                || root.equals("pokeheal")
                || root.equals("healpokemon")
                || root.equals("healparty")
                || root.equals("pokehealother")
                || root.equals("pokemonheal"));
    }

    private static Component denyMessageFor(String root) {
        String feature = switch (root) {
            case "ec", "enderchest", "pc", "pokeheal", "healpokemon", "healparty", "pokehealother", "pokemonheal" -> "VIP";
            case "pokeivs", "ivs" -> "VIP+";
            default -> "locked";
        };
        return Component.literal("§cThis is a " + feature + " feature. VIP ranks are unlocked through Tebex.");
    }

    private static void deny(CommandSourceStack source, Component message) {
        try {
            source.getPlayerOrException().sendSystemMessage(message);
        } catch (Exception ignored) {
        }
    }

    public static Component denyMessage() {
        return Component.literal("§cThat command is disabled on this server.");
    }

    private static final class ParsedCommand {
        final String root;
        final String arguments;

        private ParsedCommand(String root, String arguments) {
            this.root = root;
            this.arguments = arguments;
        }

        static ParsedCommand parse(String command) {
            String cleaned = command == null ? "" : command.trim();
            while (cleaned.startsWith("/")) cleaned = cleaned.substring(1).trim();
            if (cleaned.isEmpty()) return new ParsedCommand("", "");

            int split = cleaned.indexOf(' ');
            String rawRoot = split >= 0 ? cleaned.substring(0, split) : cleaned;
            String arguments = split >= 0 ? cleaned.substring(split + 1).trim() : "";

            // Namespaced command forms like cobblemon:pokeheal should still be treated as pokeheal.
            int namespaceSplit = rawRoot.indexOf(':');
            if (namespaceSplit >= 0 && namespaceSplit < rawRoot.length() - 1) {
                rawRoot = rawRoot.substring(namespaceSplit + 1);
            }

            return new ParsedCommand(rawRoot.toLowerCase(Locale.ROOT), arguments);
        }
    }
}
