package com.champutils.commands;

import com.champutils.crate.CrateConfig;
import com.champutils.crate.CrateCreditManager;
import com.champutils.crate.OpenCratesMenu;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class OpenCratesCommand {
    private OpenCratesCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("opencrates")
                        .executes(context -> {
                            OpenCratesMenu.open(context.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(Commands.literal("givecredit")
                                .requires(source -> source.hasPermission(2))
                                .then(creditTargetArgument(CreditAction.ADD)))
                        .then(Commands.literal("givekey")
                                .requires(source -> source.hasPermission(2))
                                .then(creditTargetArgument(CreditAction.ADD)))
                        .then(Commands.literal("takecredit")
                                .requires(source -> source.hasPermission(2))
                                .then(creditTargetArgument(CreditAction.TAKE)))
                        .then(Commands.literal("takekey")
                                .requires(source -> source.hasPermission(2))
                                .then(creditTargetArgument(CreditAction.TAKE)))
                        .then(Commands.literal("setcredit")
                                .requires(source -> source.hasPermission(2))
                                .then(creditTargetArgument(CreditAction.SET)))
                        .then(Commands.literal("setkey")
                                .requires(source -> source.hasPermission(2))
                                .then(creditTargetArgument(CreditAction.SET)))
        ));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<net.minecraft.commands.CommandSourceStack, ?> creditTargetArgument(CreditAction action) {
        return Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("crate", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            if (CrateConfig.CRATES.isEmpty()) CrateConfig.load();
                            for (String id : CrateConfig.CRATES.keySet()) builder.suggest(id);
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("amount", IntegerArgumentType.integer(action == CreditAction.SET ? 0 : 1, 9999))
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                    String crate = StringArgumentType.getString(context, "crate");
                                    int amount = IntegerArgumentType.getInteger(context, "amount");

                                    String normalized = CrateCreditManager.normalize(crate);
                                    if (CrateConfig.getCrate(normalized) == null) {
                                        context.getSource().sendFailure(Component.literal("Unknown crate: " + crate).withStyle(ChatFormatting.RED));
                                        return 0;
                                    }

                                    switch (action) {
                                        case ADD -> CrateCreditManager.addCredits(target, normalized, amount);
                                        case TAKE -> CrateCreditManager.removeCredits(target, normalized, amount);
                                        case SET -> CrateCreditManager.setCredits(target, normalized, amount);
                                    }

                                    context.getSource().sendSuccess(() -> Component.literal(action.pastTense + " " + amount + " " + normalized + " crate key" + (amount == 1 ? "" : "s") + " for " + target.getName().getString() + ".").withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                })));
    }

    private enum CreditAction {
        ADD("Gave"),
        TAKE("Removed"),
        SET("Set");

        private final String pastTense;
        CreditAction(String pastTense) { this.pastTense = pastTense; }
    }
}
