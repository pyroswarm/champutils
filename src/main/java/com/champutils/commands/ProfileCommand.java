package com.champutils.commands;

import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfileGameMode;
import com.champutils.profile.ProfileMainMenuManager;
import com.champutils.profile.ProfileLobbyDebug;
import com.champutils.profile.ProfileNetworkTransferFlow;
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

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ProfileCommand {
    private static final String[] MODES = {"normal", "ironman", "monotype", "islander", "nuzlocke"};
    private static final String[] TYPES = {"normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"};

    private ProfileCommand() {}

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
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(PlayerProfileManager.profileNamesBlocking(context.getSource().getPlayerOrException()), builder))
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
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(PlayerProfileManager.profileNamesBlocking(context.getSource().getPlayerOrException()), builder))
                                    .executes(context -> delete(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name"))))));
            dispatcher.register(literal("profilemode").redirect(dispatcher.getRoot().getChild("profiles")));
        });
    }

    private static int list(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Your profiles:").withStyle(ChatFormatting.AQUA));
        for (var profile : PlayerProfileManager.listBlocking(player)) {
            String active = profile.active() ? "* " : "  ";
            String suffix = profile.gameMode() == ProfileGameMode.MONOTYPE && profile.monotypeType() != null ? ": " + profile.monotypeType() : "";
            String pending = profile.pendingDelete() ? " §c(Pending delete)" : "";
            player.sendSystemMessage(Component.literal(active + profile.profileName() + " [" + profile.gameMode().displayName() + suffix + "]" + pending).withStyle(profile.active() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
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
        String result = PlayerProfileManager.convertActiveToNormalBlocking(player);
        boolean ok = result.startsWith("Converted") || result.contains("already Normal");
        player.sendSystemMessage(Component.literal(result).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
        return ok ? 1 : 0;
    }

    private static int openOrEnterMenu(ServerPlayer player) {
        if (ProfileNetworkTransferFlow.isSurvivalServer()) {
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
        String result = PlayerProfileManager.createBlocking(player, name, mode, type);
        player.sendSystemMessage(Component.literal(result).withStyle(result.startsWith("Created") ? ChatFormatting.GREEN : ChatFormatting.RED));
        return result.startsWith("Created") ? 1 : 0;
    }

    private static int delete(ServerPlayer player, String name) {
        if (PlayerProfileManager.isInMainMenu(player)) {
            player.sendSystemMessage(Component.literal("Use the profile menu to delete profiles.").withStyle(ChatFormatting.YELLOW));
            ProfileSelectionMenu.open(player);
            return 0;
        }
        String result = PlayerProfileManager.deleteBlocking(player, name);
        player.sendSystemMessage(Component.literal(result).withStyle(result.startsWith("Deleted") || result.startsWith("Profile") ? ChatFormatting.GREEN : ChatFormatting.RED));
        return result.startsWith("Deleted") || result.startsWith("Profile") ? 1 : 0;
    }
}
