package com.champutils.commands;

import com.champutils.database.DatabaseManager;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfileGameMode;
import com.champutils.profile.ProfileMainMenuManager;
import com.champutils.profile.ProfileLobbyDebug;
import com.champutils.profile.ProfileNetworkTransferFlow;
import com.champutils.profile.ProfileStateFlushService;
import com.champutils.profile.ProxyTransferBridge;
import com.champutils.network.NetworkServerConfig;
import com.champutils.profile.NuzlockeManager;
import com.champutils.menu.ProfileSelectionMenu;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ProfileCommand {
    private static final String[] MODES = {"normal", "ironman", "monotype", "islander", "nuzlocke"};
    private static final String[] TYPES = {"normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"};
    private static final Set<UUID> PROFILE_MUTATIONS_IN_PROGRESS = ConcurrentHashMap.newKeySet();

    private ProfileCommand() {}

    private static boolean beginProfileMutation(ServerPlayer player) {
        if (player == null) return false;
        if (PROFILE_MUTATIONS_IN_PROGRESS.add(player.getUUID())) return true;
        player.sendSystemMessage(Component.literal("A profile change is already in progress.").withStyle(ChatFormatting.YELLOW));
        return false;
    }

    private static void endProfileMutation(ServerPlayer player) {
        if (player != null) PROFILE_MUTATIONS_IN_PROGRESS.remove(player.getUUID());
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("profiles")
                    .executes(context -> openOrEnterMenu(context.getSource().getPlayerOrException()))
                    .then(literal("list").executes(context -> list(context.getSource().getPlayerOrException())))
                    .then(literal("menu").executes(context -> openOrEnterMenu(context.getSource().getPlayerOrException())))
                    .then(literal("current").executes(context -> current(context.getSource().getPlayerOrException())))
                    .then(literal("converttonormal").executes(context -> convertToNormal(context.getSource().getPlayerOrException())))
                    .then(literal("nuzlockecomplete")
                            .requires(source -> source.hasPermission(4))
                            .executes(context -> nuzlockeComplete(context.getSource().getPlayerOrException(), "champion"))
                            .then(argument("champion", StringArgumentType.word())
                                    .executes(context -> nuzlockeComplete(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "champion")))))
                    .then(literal("switch")
                            .then(argument("name", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(PlayerProfileManager.profileNamesCached(context.getSource().getPlayerOrException()), builder))
                                    .executes(context -> load(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name")))))
                    .then(literal("create")
                            .then(argument("name", StringArgumentType.word())
                                    .then(argument("mode", StringArgumentType.word())
                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(MODES, builder))
                                            .executes(context -> create(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "mode"), null))
                                            .then(argument("type", StringArgumentType.word())
                                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(TYPES, builder))
                                                    .executes(context -> create(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "mode"), StringArgumentType.getString(context, "type")))))))
                    .then(literal("delete")
                            .then(argument("name", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(PlayerProfileManager.profileNamesCached(context.getSource().getPlayerOrException()), builder))
                                    .executes(context -> delete(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name"))))));
            dispatcher.register(literal("profilemode").redirect(dispatcher.getRoot().getChild("profiles")));
        });
    }

    private static int list(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Loading your profiles...").withStyle(ChatFormatting.GRAY));
        DatabaseManager.supplyAsync("list profiles command", connection ->
                        PlayerProfileManager.readProfiles(connection, player.getUUID()))
                .whenComplete((profiles, error) -> player.server.execute(() -> {
                    if (player.hasDisconnected()) return;
                    if (error != null || profiles == null) {
                        if (error != null) error.printStackTrace();
                        player.sendSystemMessage(Component.literal("Could not load profiles. Please try again.").withStyle(ChatFormatting.RED));
                        return;
                    }
                    player.sendSystemMessage(Component.literal("Your profiles:").withStyle(ChatFormatting.AQUA));
                    for (var profile : profiles) {
                        String active = profile.active() ? "* " : "  ";
                        String suffix = profile.gameMode() == ProfileGameMode.MONOTYPE && profile.monotypeType() != null ? ": " + profile.monotypeType() : "";
                        String pending = profile.pendingDelete() ? " §c(Pending delete)" : "";
                        player.sendSystemMessage(Component.literal(active + profile.profileName() + " [" + profile.gameMode().displayName() + suffix + "]" + pending).withStyle(profile.active() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                    }
                }));
        return 1;
    }

    private static int current(ServerPlayer player) {
        var profile = PlayerProfileManager.active(player);
        if (profile == null) {
            player.sendSystemMessage(Component.literal("No profile loaded. Use /profiles to select one.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        String suffix = profile.gameMode() == ProfileGameMode.MONOTYPE && profile.monotypeType() != null ? ": " + profile.monotypeType() : "";
        player.sendSystemMessage(Component.literal("Active profile: " + profile.profileName() + " [" + profile.gameMode().displayName() + suffix + "]").withStyle(ChatFormatting.AQUA));
        return 1;
    }

    private static int nuzlockeComplete(ServerPlayer player, String champion) {
        String result = NuzlockeManager.completeActiveRun(player, champion);
        boolean ok = result.startsWith("Nuzlocke complete");
        player.sendSystemMessage(Component.literal(result).withStyle(ok ? ChatFormatting.GOLD : ChatFormatting.RED));
        return ok ? 1 : 0;
    }

    private static int convertToNormal(ServerPlayer player) {
        if (!beginProfileMutation(player)) return 0;
        player.sendSystemMessage(Component.literal("Checking profile conversion...").withStyle(ChatFormatting.GRAY));
        DatabaseManager.supplyAsync("convert active profile to normal", connection ->
                        PlayerProfileManager.convertActiveToNormalBlocking(player))
                .whenComplete((result, error) -> {
                    endProfileMutation(player);
                    player.server.execute(() -> {
                        if (player.hasDisconnected()) return;
                        String finalResult = error == null && result != null ? result : "Could not convert profile. Please try again or contact staff.";
                        if (error != null) error.printStackTrace();
                        boolean ok = finalResult.startsWith("Converted") || finalResult.contains("already Normal");
                        player.sendSystemMessage(Component.literal(finalResult).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
                    });
                });
        return 1;
    }

    private static int openOrEnterMenu(ServerPlayer player) {
        if (ProfileNetworkTransferFlow.isSurvivalServer()) {
            transferToProfileLobbyAfterHardSave(player);
            return 1;
        }

        if (ProfileNetworkTransferFlow.isProfileLobbyServer()) {
            ProfileLobbyDebug.log("profilesCommand.openMenu.profileLobby", player);
            ProfileSelectionMenu.open(player);
            return 1;
        }

        if (PlayerProfileManager.hasActiveProfile(player)) {
            ProfileMainMenuManager.enter(player, true);
        }
        ProfileLobbyDebug.log("profilesCommand.openMenu.allInOne", player);
        ProfileSelectionMenu.open(player);
        return 1;
    }


    private static void transferToProfileLobbyAfterHardSave(ServerPlayer player) {
        if (player == null) return;
        ProfileStateFlushService.TransferFlushSnapshot transferSnapshot =
                ProfileStateFlushService.captureBeforeTransfer(player, "return_to_profile_lobby");
        if (!ProfileStateFlushService.queueAncillaryStateBeforeTransfer(player, "return_to_profile_lobby")) {
            player.sendSystemMessage(Component.literal("Could not safely save your profile yet. Please wait a moment and try again.").withStyle(ChatFormatting.RED));
            return;
        }

        player.sendSystemMessage(Component.literal("Saving your profile before changing servers...").withStyle(ChatFormatting.YELLOW));

        com.champutils.database.DatabaseManager.runAsync("hard save before profile lobby transfer", connection ->
                ProfileStateFlushService.commitTransferSnapshot(connection, transferSnapshot, "return_to_profile_lobby")
        ).whenComplete((ignored, error) -> player.server.execute(() -> {
            if (!com.champutils.teleport.SafeTeleportManager.isLive(player)) return;
            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                System.err.println("[ChampUtils] Failed hard profile save before profile lobby transfer for " + player.getGameProfile().getName() + ": " + cause.getMessage());
                cause.printStackTrace();
                player.sendSystemMessage(Component.literal("Could not safely save your profile before changing servers. Please try again.").withStyle(ChatFormatting.RED));
                return;
            }

            NetworkServerConfig config = NetworkServerConfig.get();
            String targetServer = config.profileLobbyServerId == null || config.profileLobbyServerId.isBlank() ? "profile_lobby" : config.profileLobbyServerId;
            String command = config.returnToProfileLobbyCommand == null || config.returnToProfileLobbyCommand.isBlank()
                    ? "server {player} {target_server}"
                    : config.returnToProfileLobbyCommand;
            command = command
                    .replace("{player}", player.getGameProfile().getName())
                    .replace("{target_server}", targetServer)
                    .replace("{profile}", "")
                    .replace("{token}", "");
            if (command.startsWith("/")) command = command.substring(1);
            ProfileLobbyDebug.log("profilesCommand.transferToLobby", player);

            boolean transferRequested = ProxyTransferBridge.connect(player, targetServer);
            if (!transferRequested) {
                player.server.getCommands().performPrefixedCommand(player.server.createCommandSourceStack().withSuppressedOutput(), command);
            }

            player.sendSystemMessage(Component.literal("Sending you to the profile lobby...").withStyle(ChatFormatting.YELLOW));
        }));
    }

    private static int load(ServerPlayer player, String name) {
        if (PlayerProfileManager.isInMainMenu(player)) {
            player.sendSystemMessage(Component.literal("Use the profile menu to select a profile.").withStyle(ChatFormatting.YELLOW));
            ProfileSelectionMenu.open(player);
            return 0;
        }
        if (!PlayerProfileManager.isInMainMenu(player)) {
            ProfileMainMenuManager.enter(player, true);
            ProfileSelectionMenu.open(player);
            player.sendSystemMessage(Component.literal("Profile swapping is only allowed from the profile menu. Select the profile again to load it.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        ProfileNetworkTransferFlow.sendLoadingTitle(player, name);
        PlayerProfileManager.switchAsync(player, name, result -> {
            boolean ok = result != null && result.startsWith("Loaded");
            if (!ok) {
                player.sendSystemMessage(Component.literal(result == null ? "Could not switch profile." : result).withStyle(ChatFormatting.RED));
            }
        });
        return 1;
    }

    private static int create(ServerPlayer player, String name, String rawMode, String type) {
        if (PlayerProfileManager.isInMainMenu(player)) {
            player.sendSystemMessage(Component.literal("Use the profile menu to create profiles.").withStyle(ChatFormatting.YELLOW));
            ProfileSelectionMenu.open(player);
            return 0;
        }
        ProfileGameMode mode = ProfileGameMode.parse(rawMode);
        if (!beginProfileMutation(player)) return 0;
        player.sendSystemMessage(Component.literal("Creating profile " + name + "...").withStyle(ChatFormatting.YELLOW));
        DatabaseManager.supplyAsync("create profile command " + name, connection ->
                        PlayerProfileManager.createBlocking(player, name, mode, type))
                .whenComplete((result, error) -> {
                    endProfileMutation(player);
                    player.server.execute(() -> {
                        if (player.hasDisconnected()) return;
                        String finalResult = error == null && result != null ? result : "Could not create profile. Please try again or contact staff.";
                        if (error != null) error.printStackTrace();
                        player.sendSystemMessage(Component.literal(finalResult).withStyle(finalResult.startsWith("Created") ? ChatFormatting.GREEN : ChatFormatting.RED));
                    });
                });
        return 1;
    }

    private static int delete(ServerPlayer player, String name) {
        if (PlayerProfileManager.isInMainMenu(player)) {
            player.sendSystemMessage(Component.literal("Use the profile menu to delete profiles.").withStyle(ChatFormatting.YELLOW));
            ProfileSelectionMenu.open(player);
            return 0;
        }
        if (!beginProfileMutation(player)) return 0;
        player.sendSystemMessage(Component.literal("Updating profile deletion...").withStyle(ChatFormatting.YELLOW));
        DatabaseManager.supplyAsync("delete profile command " + name, connection ->
                        PlayerProfileManager.deleteBlocking(player, name))
                .whenComplete((result, error) -> {
                    endProfileMutation(player);
                    player.server.execute(() -> {
                        if (player.hasDisconnected()) return;
                        String finalResult = error == null && result != null ? result : "Could not delete profile. Please try again or contact staff.";
                        if (error != null) error.printStackTrace();
                        player.sendSystemMessage(Component.literal(finalResult).withStyle(finalResult.startsWith("Deleted") || finalResult.startsWith("Profile") ? ChatFormatting.GREEN : ChatFormatting.RED));
                    });
                });
        return 1;
    }
}
