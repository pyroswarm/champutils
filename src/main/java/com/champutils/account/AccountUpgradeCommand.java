package com.champutils.account;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class AccountUpgradeCommand {
    private AccountUpgradeCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("accountupgrade")
                        .executes(ctx -> {
                            AccountUpgradeMenu.open(ctx.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(Commands.literal("buy")
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("vip");
                                            builder.suggest("vipplus");
                                            builder.suggest("vip+");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> buy(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "tier")))))
        ));
    }

    private static int buy(ServerPlayer player, String rawTier) {
        String normalized = rawTier == null ? "" : rawTier.trim().toLowerCase(java.util.Locale.ROOT).replace("+", "plus");
        AccountUpgradeManager.Tier tier = switch (normalized) {
            case "vip" -> AccountUpgradeManager.Tier.VIP;
            case "vipplus", "vip_plus" -> AccountUpgradeManager.Tier.VIP_PLUS;
            default -> null;
        };
        if (tier == null) {
            player.sendSystemMessage(Component.literal("§cUnknown upgrade. Use vip or vipplus."));
            return 0;
        }
        AccountUpgradeManager.PurchaseResult result = AccountUpgradeManager.purchase(player, tier);
        player.sendSystemMessage(Component.literal((result.success() ? "§a" : "§c") + result.message()));
        return result.success() ? 1 : 0;
    }
}
