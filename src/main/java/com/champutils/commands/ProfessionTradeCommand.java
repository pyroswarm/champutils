package com.champutils.commands;

import com.champutils.menu.ProfessionTradeMenu;
import com.champutils.permissions.PermissionUtil;
import com.champutils.profession.ProfessionBackpackConfig;
import com.champutils.profession.ProfessionBackpackManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class ProfessionTradeCommand {
    private ProfessionTradeCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("professiontrade")
                        .executes(context -> {
                            ProfessionTradeMenu.open(context.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(Commands.literal("setcost")
                                .requires(source -> source.hasPermission(4))
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(context -> setCost(context.getSource(), StringArgumentType.getString(context, "item"), IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("setreward")
                                .requires(source -> source.hasPermission(4))
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .then(Commands.argument("rewardItem", StringArgumentType.word())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(context -> setReward(context.getSource(), StringArgumentType.getString(context, "item"), StringArgumentType.getString(context, "rewardItem"), IntegerArgumentType.getInteger(context, "amount")))))))
                        .then(Commands.literal("disable")
                                .requires(source -> source.hasPermission(4))
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .executes(context -> setTradeDisabled(context.getSource(), StringArgumentType.getString(context, "item"), true))))
                        .then(Commands.literal("enable")
                                .requires(source -> source.hasPermission(4))
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .executes(context -> setTradeDisabled(context.getSource(), StringArgumentType.getString(context, "item"), false))))
                        .then(Commands.literal("listdisabled")
                                .requires(source -> source.hasPermission(4))
                                .executes(context -> listDisabled(context.getSource())))
        ));
    }

    private static int setCost(CommandSourceStack source, String itemId, int amount) {
        String id = normalizeCommandItem(itemId);
        ProfessionBackpackConfig.ItemData data = ProfessionBackpackConfig.get(id);
        if (data == null) {
            source.sendFailure(Component.literal("§cItem is not in profession_backpack.json yet: " + id));
            return 0;
        }
        data.tradeCost = amount;
        ProfessionBackpackConfig.save();
        source.sendSuccess(() -> Component.literal("§aSet profession trade cost for §f" + id + "§a to §e" + amount + "§a."), true);
        return 1;
    }

    private static int setReward(CommandSourceStack source, String itemId, String rewardItem, int amount) {
        String id = normalizeCommandItem(itemId);
        String reward = normalizeCommandItem(rewardItem);
        ProfessionBackpackConfig.ItemData data = ProfessionBackpackConfig.get(id);
        if (data == null) {
            source.sendFailure(Component.literal("§cItem is not in profession_backpack.json yet: " + id));
            return 0;
        }
        if (ProfessionBackpackManager.item(reward) == net.minecraft.world.item.Items.AIR) {
            source.sendFailure(Component.literal("§cInvalid reward item: " + reward));
            return 0;
        }
        data.rewardItem = reward;
        data.rewardAmount = amount;
        ProfessionBackpackConfig.save();
        source.sendSuccess(() -> Component.literal("§aSet profession trade reward for §f" + id + "§a to §d" + amount + "x " + reward + "§a."), true);
        return 1;
    }

    private static int setTradeDisabled(CommandSourceStack source, String itemId, boolean disabled) {
        String id = normalizeCommandItem(itemId);
        if (ProfessionBackpackManager.item(id) == net.minecraft.world.item.Items.AIR) {
            source.sendFailure(Component.literal("§cInvalid item: " + id));
            return 0;
        }
        ProfessionBackpackConfig.setTradeDisabled(id, disabled);
        source.sendSuccess(() -> Component.literal((disabled ? "§cDisabled" : "§aEnabled") + " §f" + id + (disabled
                ? " §cfrom backpack Rare Candy trades. Players can still store it if backpack collection allows it."
                : " §afor backpack trades if the item entry also exists and has enough balance.")), true);
        return 1;
    }

    private static int listDisabled(CommandSourceStack source) {
        if (ProfessionBackpackConfig.CONFIG.tradeDisabledItems == null || ProfessionBackpackConfig.CONFIG.tradeDisabledItems.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§aNo backpack trade-disabled items are configured."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("§6Backpack trade-disabled items:"), false);
        ProfessionBackpackConfig.CONFIG.tradeDisabledItems.stream()
                .sorted()
                .forEach(item -> source.sendSuccess(() -> Component.literal("§7- §f" + item), false));
        return 1;
    }

    private static String normalizeCommandItem(String item) {
        String id = item.trim().toLowerCase(java.util.Locale.ROOT);
        if (!id.contains(":")) id = "minecraft:" + id;
        return ProfessionBackpackConfig.normalizeTradeItemId(id);
    }
}
