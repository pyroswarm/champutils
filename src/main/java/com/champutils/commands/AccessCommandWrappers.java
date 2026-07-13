package com.champutils.commands;

import com.champutils.permissions.LuckPermsHook;
import com.champutils.permissions.PermissionUtil;
import com.champutils.profile.PlayerProfileManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Player-facing wrappers for external Cobblemon utility commands.
 *
 * The native Cobblemon /pc command creates a PermissiblePcLink. Cobblemon checks
 * cobblemon.command.pc again every time the client attempts to move a Pokemon.
 * Therefore the command must be executed with the player's real permission
 * context. Elevating only the initial command source opens a visually usable PC
 * whose transfer packets are then rejected by Cobblemon.
 */
public final class AccessCommandWrappers {
    private static final String CHAMPUTILS_PC = "champutils.command.pc";
    private static final String COBBLEMON_PC = "cobblemon.command.pc";
    private static final String CHAMPUTILS_HEAL = "champutils.command.pokeheal";
    private static final String COBBLEMON_HEAL_SELF = "cobblemon.command.healpokemon.self";
    private static final String COBBLEMON_HEAL_OTHER = "cobblemon.command.healpokemon.other";

    private AccessCommandWrappers() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("pc")
                    .executes(ctx -> runRankCommand(
                            ctx.getSource(), CHAMPUTILS_PC, COBBLEMON_PC, "VIP", "cobblemon:pc"))
                    .then(Commands.argument("box", IntegerArgumentType.integer(1))
                            .executes(ctx -> runRankCommand(
                                    ctx.getSource(),
                                    CHAMPUTILS_PC,
                                    COBBLEMON_PC,
                                    "VIP",
                                    "cobblemon:pc " + IntegerArgumentType.getInteger(ctx, "box")))));

            dispatcher.register(Commands.literal("pokeheal")
                    .executes(ctx -> runRankCommand(
                            ctx.getSource(), CHAMPUTILS_HEAL, COBBLEMON_HEAL_SELF, "VIP", "cobblemon:healpokemon"))
                    .then(Commands.argument("target", StringArgumentType.greedyString())
                            .requires(AccessCommandWrappers::canHealOther)
                            .executes(ctx -> runNative(
                                    ctx.getSource(),
                                    "cobblemon:healpokemon " + StringArgumentType.getString(ctx, "target")))));

            dispatcher.register(Commands.literal("healpokemon")
                    .executes(ctx -> runRankCommand(
                            ctx.getSource(), CHAMPUTILS_HEAL, COBBLEMON_HEAL_SELF, "VIP", "cobblemon:healpokemon"))
                    .then(Commands.argument("target", StringArgumentType.greedyString())
                            .requires(AccessCommandWrappers::canHealOther)
                            .executes(ctx -> runNative(
                                    ctx.getSource(),
                                    "cobblemon:healpokemon " + StringArgumentType.getString(ctx, "target")))));

            // PokeIVs is unrelated to the native PC-link problem. Keep its existing
            // VIP+ wrapper behavior and staff-only argument path.
            dispatcher.register(Commands.literal("pokeivs")
                    .executes(ctx -> runElevated(ctx.getSource(), "champutils.command.pokeivs", "cobblemonextras:pokeivs", "VIP+"))
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .requires(source -> PermissionUtil.has(source, "champutils.admin"))
                            .executes(ctx -> runRawElevated(
                                    ctx.getSource(),
                                    "cobblemonextras:pokeivs " + StringArgumentType.getString(ctx, "args")))));
        });
    }

    private static int runRankCommand(
            CommandSourceStack source,
            String wrapperPermission,
            String nativePermission,
            String featureName,
            String nativeCommand
    ) {
        if (source == null || !hasLoadedProfile(source)) return 0;
        if (!hasRealRankAccess(source, wrapperPermission, nativePermission)) {
            deny(source, "§cThis command is unlocked in game through the account upgrader or online at the cobblechamps.com store.");
            return 0;
        }

        // Never call withPermission(...) here. Cobblemon stores a permission-backed
        // PC link and validates the actual player again when move packets arrive.
        return runNative(source, nativeCommand);
    }

    private static boolean hasRealRankAccess(
            CommandSourceStack source,
            String wrapperPermission,
            String nativePermission
    ) {
        if (source == null) return false;
        if (source.getEntity() == null) return true;

        ServerPlayer player = source.getPlayer();
        if (player == null) return false;

        // Check the effective LuckPerms value without treating vanilla OP status
        // or an account-database flag as rank access.
        return LuckPermsHook.hasPermissionStrict(player, wrapperPermission)
                && LuckPermsHook.hasPermissionStrict(player, nativePermission);
    }

    private static boolean canHealOther(CommandSourceStack source) {
        if (source == null || source.getEntity() == null) return true;
        ServerPlayer player = source.getPlayer();
        if (player == null) return false;
        return hasRealRankAccess(source, CHAMPUTILS_HEAL, COBBLEMON_HEAL_SELF)
                && LuckPermsHook.hasPermissionStrict(player, COBBLEMON_HEAL_OTHER);
    }

    private static int runElevated(
            CommandSourceStack source,
            String permission,
            String namespacedCommand,
            String featureName
    ) {
        if (source == null || !hasLoadedProfile(source)) return 0;
        if (!PermissionUtil.has(source, permission)) {
            deny(source, "§cThis is a " + featureName + " feature. VIP ranks are unlocked through Tebex.");
            return 0;
        }
        return runRawElevated(source, namespacedCommand);
    }

    private static boolean hasLoadedProfile(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayer();
            if (player == null) return true;
            if (PlayerProfileManager.hasActiveProfile(player)) return true;
            player.sendSystemMessage(Component.literal("§cSelect and load a profile before using this command."));
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static int runNative(CommandSourceStack source, String command) {
        if (source == null || source.getServer() == null) return 0;
        source.getServer().getCommands().performPrefixedCommand(source.withSuppressedOutput(), command);
        return 1;
    }

    private static int runRawElevated(CommandSourceStack source, String command) {
        if (source == null || source.getServer() == null) return 0;
        source.getServer().getCommands().performPrefixedCommand(
                source.withSuppressedOutput().withPermission(4),
                command
        );
        return 1;
    }

    private static void deny(CommandSourceStack source, String message) {
        try {
            source.getPlayerOrException().sendSystemMessage(Component.literal(message));
        } catch (Exception ignored) {
        }
    }
}
