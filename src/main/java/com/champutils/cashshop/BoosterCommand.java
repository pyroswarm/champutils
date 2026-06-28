package com.champutils.cashshop;

import com.champutils.buff.ServerBuffManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class BoosterCommand {
    private BoosterCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("booster")
                        .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    StringBuilder builder = new StringBuilder("Booster ids:");
                                    for (CashShopBoostItemManager.Def def : CashShopBoostItemManager.defs()) {
                                        builder.append("\n- ").append(def.id).append(" = ").append(def.cleanName());
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal(builder.toString()), false);
                                    return 1;
                                }))
                        .then(Commands.literal("active")
                                .executes(ctx -> {
                                    var active = ServerBuffManager.activeBoostViews();
                                    if (active.isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("Current Booster None"), false);
                                        return 1;
                                    }
                                    StringBuilder builder = new StringBuilder("Active boosters:");
                                    for (var boost : active) {
                                        builder.append("\n- ").append(boost.displayName()).append(" by ").append(boost.activatorName()).append(" - ").append(ServerBuffManager.formatDuration(boost.remainingMillis())).append(" left");
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal(builder.toString()), false);
                                    return 1;
                                }))
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                        .executes(ctx -> {
                                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                            String id = StringArgumentType.getString(ctx, "id");
                                                            int count = IntegerArgumentType.getInteger(ctx, "count");
                                                            var stack = CashShopBoostItemManager.createItem(id, count);
                                                            if (stack.isEmpty()) {
                                                                ctx.getSource().sendFailure(Component.literal("Unknown booster id. Use /booster list."));
                                                                return 0;
                                                            }
                                                            target.getInventory().add(stack);
                                                            ctx.getSource().sendSuccess(() -> Component.literal("Gave " + count + "x " + id + " booster item(s) to " + target.getName().getString() + "."), true);
                                                            return 1;
                                                        })))))
                        .then(Commands.literal("credits")
                                .then(Commands.literal("give")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 999))
                                                        .executes(ctx -> {
                                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                            BoosterCreditManager.addPurchasedCredits(target.getUUID(), amount);
                                                            target.sendSystemMessage(Component.literal("You received " + amount + " purchased booster credit(s)."));
                                                            ctx.getSource().sendSuccess(() -> Component.literal("Gave " + amount + " purchased booster credit(s) to " + target.getName().getString() + "."), true);
                                                            return 1;
                                                        })))))
                        .then(Commands.literal("activate")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            ServerPlayer player = ctx.getSource().getPlayer();
                                            boolean ok = CashShopBoostItemManager.activateFromAdmin(ctx.getSource().getServer(), player, id);
                                            if (!ok) {
                                                ctx.getSource().sendFailure(Component.literal("Could not activate booster. It may be unknown or already active."));
                                                return 0;
                                            }
                                            ctx.getSource().sendSuccess(() -> Component.literal("Activated booster " + id + "."), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("stop")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            CashShopBoostItemManager.deactivateAdmin(id);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Stopped booster " + id + "."), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("stopall")
                                .executes(ctx -> {
                                    for (CashShopBoostItemManager.Def def : CashShopBoostItemManager.defs()) {
                                        CashShopBoostItemManager.deactivateAdmin(def.id);
                                    }
                                    ServerBuffManager.clearAllBoosters();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Stopped all tracked server boosters."), true);
                                    return 1;
                                }))
        ));
    }
}
