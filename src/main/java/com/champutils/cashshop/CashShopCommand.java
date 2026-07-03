package com.champutils.cashshop;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class CashShopCommand {
    private CashShopCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("cashshop")
                    .executes(ctx -> {
                        CashShopMenu.open(ctx.getSource().getPlayerOrException());
                        return 1;
                    })
                    .then(Commands.literal("giveboost")
                            .requires(source -> source.hasPermission(4))
                            .then(Commands.argument("player", EntityArgument.player())
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                    .executes(ctx -> {
                                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                        String id = StringArgumentType.getString(ctx, "id");
                                                        int count = IntegerArgumentType.getInteger(ctx, "count");
                                                        var stack = CashShopBoostItemManager.createItem(id, count);
                                                        if (stack.isEmpty()) {
                                                            ctx.getSource().sendFailure(Component.literal("Unknown boost id."));
                                                            return 0;
                                                        }
                                                        target.getInventory().add(stack);
                                                        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + count + "x " + id + " boost item(s) to " + target.getName().getString() + "."), true);
                                                        return 1;
                                                    })))))
                    .then(Commands.literal("givecredits")
                            .requires(source -> source.hasPermission(4))
                            .then(Commands.argument("player", EntityArgument.player())
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, 999))
                                            .executes(ctx -> {
                                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                BoosterCreditManager.addPurchasedCredits(target.getUUID(), amount);
                                                target.sendSystemMessage(Component.literal("You received " + amount + " purchased booster credit(s)."));
                                                ctx.getSource().sendSuccess(() -> Component.literal("Gave " + amount + " purchased booster credit(s) to " + target.getName().getString() + "."), true);
                                                return 1;
                                            })))));
        });
    }
}
