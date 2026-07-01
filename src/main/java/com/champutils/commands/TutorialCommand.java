package com.champutils.commands;

import com.champutils.tutorial.SpawnGuideNpc;
import com.champutils.tutorial.TutorialManager;
import com.champutils.tutorial.TutorialNpcBindingRegistry;
import com.champutils.tutorial.TutorialNpcInteractionListener;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

public final class TutorialCommand {
    private TutorialCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("tutorial")
                    .executes(context -> show(context.getSource().getPlayerOrException()))
                    .then(Commands.literal("progress")
                            .executes(context -> show(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("resetself")
                            .executes(context -> resetSelf(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("bind")
                            .requires(TutorialCommand::canAdmin)
                            .then(Commands.argument("id", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(TutorialManager.npcIds(), builder))
                                    .executes(context -> bind(
                                            context.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(context, "id")
                                    ))))
                    .then(Commands.literal("bindcancel")
                            .requires(TutorialCommand::canAdmin)
                            .executes(context -> bindCancel(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("unbind")
                            .requires(TutorialCommand::canAdmin)
                            .then(Commands.argument("id", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(TutorialManager.npcIds(), builder))
                                    .executes(context -> unbind(
                                            context.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(context, "id")
                                    ))))
                    .then(Commands.literal("bindings")
                            .requires(TutorialCommand::canAdmin)
                            .executes(context -> bindings(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("reset")
                            .requires(TutorialCommand::canAdmin)
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                            context.getSource().getServer().getPlayerList().getPlayers().stream().map(p -> p.getGameProfile().getName()),
                                            builder
                                    ))
                                    .executes(context -> resetOther(
                                            context.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(context, "player")
                                    )))));

            dispatcher.register(Commands.literal("skiptutorial")
                    .executes(context -> skip(context.getSource().getPlayerOrException())));
        });
    }

    private static boolean canAdmin(CommandSourceStack source) {
        return source.hasPermission(2) || com.champutils.permissions.PermissionUtil.has(source, "champutils.admin");
    }

    private static int show(ServerPlayer player) {
        TutorialManager.showProgress(player);
        return 1;
    }

    private static int skip(ServerPlayer player) {
        TutorialManager.skip(player);
        return 1;
    }

    private static int resetSelf(ServerPlayer player) {
        TutorialManager.resetSelf(player);
        return 1;
    }

    private static int bind(ServerPlayer player, String id) {
        TutorialNpcInteractionListener.beginBind(player, id);
        return 1;
    }

    private static int bindCancel(ServerPlayer player) {
        if (TutorialNpcInteractionListener.cancelBind(player)) {
            player.sendSystemMessage(Component.literal("§aTutorial NPC bind cancelled."));
        } else {
            player.sendSystemMessage(Component.literal("§7You do not have a pending tutorial NPC bind."));
        }
        return 1;
    }

    private static int unbind(ServerPlayer player, String id) {
        String normalized = TutorialManager.normalizeNpcId(id);
        if (!TutorialManager.isValidNpcId(normalized)) {
            player.sendSystemMessage(Component.literal("§cUnknown tutorial NPC id: " + id));
            return 0;
        }
        boolean removed = TutorialNpcBindingRegistry.unbind(normalized);
        SpawnGuideNpc guide = TutorialManager.getNpc(normalized);
        player.sendSystemMessage(Component.literal(removed
                ? "§aUnbound " + (guide == null ? normalized : guide.displayName) + "."
                : "§7No NPC was bound for " + (guide == null ? normalized : guide.displayName) + "."));
        return 1;
    }

    private static int bindings(ServerPlayer player) {
        Map<String, TutorialNpcBindingRegistry.Binding> bindings = TutorialNpcBindingRegistry.snapshot();
        player.sendSystemMessage(Component.literal("§6§lTutorial NPC Bindings"));
        for (String id : TutorialManager.npcIds()) {
            SpawnGuideNpc guide = TutorialManager.getNpc(id);
            TutorialNpcBindingRegistry.Binding binding = bindings.get(id);
            if (binding == null || binding.uuid() == null) {
                player.sendSystemMessage(Component.literal("§c□ " + (guide == null ? id : guide.displayName) + " §7- not bound"));
            } else {
                player.sendSystemMessage(Component.literal("§a✔ " + (guide == null ? id : guide.displayName) + " §7- " + binding.npcUuid + " §8(" + binding.world + ")"));
            }
        }
        return 1;
    }

    private static int resetOther(ServerPlayer actor, String targetName) {
        ServerPlayer target = actor.server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            actor.sendSystemMessage(Component.literal("§cThat player is not online."));
            return 0;
        }
        TutorialManager.adminReset(target, actor);
        return 1;
    }
}
