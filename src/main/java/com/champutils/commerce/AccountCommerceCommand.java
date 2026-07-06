package com.champutils.commerce;

import com.champutils.account.AccountUpgradeConfig;
import com.champutils.cashshop.BoosterCreditManager;
import com.champutils.permissions.LuckPermsHook;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public final class AccountCommerceCommand {
    private AccountCommerceCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    Commands.literal("champpurchase")
                            .requires(source -> source.hasPermission(4))
                            .then(Commands.literal("boostercredits")
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000))
                                                    .then(Commands.argument("reference", StringArgumentType.word())
                                                            .executes(ctx -> grantBoosterCredits(
                                                                    ctx.getSource(),
                                                                    StringArgumentType.getString(ctx, "player"),
                                                                    IntegerArgumentType.getInteger(ctx, "amount"),
                                                                    StringArgumentType.getString(ctx, "reference")
                                                            ))))))
                            .then(Commands.literal("rank")
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .then(Commands.argument("tier", StringArgumentType.word())
                                                    .suggests((ctx, builder) -> {
                                                        builder.suggest("vip");
                                                        builder.suggest("vipplus");
                                                        builder.suggest("vip+");
                                                        return builder.buildFuture();
                                                    })
                                                    .then(Commands.argument("reference", StringArgumentType.word())
                                                            .executes(ctx -> grantRank(
                                                                    ctx.getSource(),
                                                                    StringArgumentType.getString(ctx, "player"),
                                                                    StringArgumentType.getString(ctx, "tier"),
                                                                    StringArgumentType.getString(ctx, "reference")
                                                            ))))))
                            .then(Commands.literal("record")
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .then(Commands.argument("package", StringArgumentType.word())
                                                    .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 100000))
                                                            .then(Commands.argument("reference", StringArgumentType.word())
                                                                    .executes(ctx -> recordOnly(
                                                                            ctx.getSource(),
                                                                            StringArgumentType.getString(ctx, "player"),
                                                                            StringArgumentType.getString(ctx, "package"),
                                                                            IntegerArgumentType.getInteger(ctx, "quantity"),
                                                                            StringArgumentType.getString(ctx, "reference")
                                                                    )))))))
            );

            dispatcher.register(
                    Commands.literal("votes")
                            .executes(ctx -> showVoteBalance(ctx.getSource().getPlayerOrException()))
                            .then(Commands.literal("points")
                                    .executes(ctx -> showVoteBalance(ctx.getSource().getPlayerOrException())))
            );
        });
    }

    private static int grantBoosterCredits(CommandSourceStack source, String playerName, int amount, String reference) {
        MinecraftServer server = source.getServer();
        AccountCommerceRepository.resolveAccountAsync(server, playerName)
                .thenCompose(account -> AccountCommerceRepository.recordPurchaseAsync(account, "TEBEX", "boostercredits", amount, reference, "Purchased booster credits")
                        .thenApply(inserted -> new Object[]{account, inserted}))
                .thenAccept(result -> server.execute(() -> {
                    AccountCommerceRepository.ResolvedAccount account = (AccountCommerceRepository.ResolvedAccount) result[0];
                    boolean inserted = (Boolean) result[1];
                    if (account == null || !inserted) {
                        source.sendFailure(Component.literal("Purchase already processed or account could not be resolved."));
                        return;
                    }
                    BoosterCreditManager.addPurchasedCredits(account.accountUuid(), amount);
                    ServerPlayer online = server.getPlayerList().getPlayer(account.accountUuid());
                    if (online != null) online.sendSystemMessage(Component.literal("You received " + amount + " purchased booster credit(s).").withStyle(ChatFormatting.GREEN));
                    source.sendSuccess(() -> Component.literal("Granted " + amount + " account booster credit(s) to " + account.username() + "."), true);
                }))
                .exceptionally(error -> {
                    server.execute(() -> source.sendFailure(Component.literal("Failed to process purchase: " + error.getMessage())));
                    return null;
                });
        return 1;
    }

    private static int grantRank(CommandSourceStack source, String playerName, String tierRaw, String reference) {
        MinecraftServer server = source.getServer();
        String tier = tierRaw == null ? "" : tierRaw.trim().toLowerCase(Locale.ROOT).replace("+", "plus");
        String group = switch (tier) {
            case "vip" -> AccountUpgradeConfig.CONFIG.vip.luckPermsGroup;
            case "vipplus", "vip_plus" -> AccountUpgradeConfig.CONFIG.vipPlus.luckPermsGroup;
            default -> "";
        };
        if (group == null || group.isBlank()) {
            source.sendFailure(Component.literal("Unknown rank tier. Use vip or vipplus."));
            return 0;
        }
        AccountCommerceRepository.resolveAccountAsync(server, playerName)
                .thenCompose(account -> AccountCommerceRepository.recordPurchaseAsync(account, "TEBEX", "rank_" + tier, 1, reference, "Purchased rank " + tier)
                        .thenApply(inserted -> new Object[]{account, inserted}))
                .thenAccept(result -> server.execute(() -> {
                    AccountCommerceRepository.ResolvedAccount account = (AccountCommerceRepository.ResolvedAccount) result[0];
                    boolean inserted = (Boolean) result[1];
                    if (account == null || !inserted) {
                        source.sendFailure(Component.literal("Purchase already processed or account could not be resolved."));
                        return;
                    }
                    boolean ok = LuckPermsHook.addGroup(account.accountUuid(), group);
                    if (ok && (tier.equals("vipplus") || tier.equals("vip_plus")) && AccountUpgradeConfig.CONFIG.vip != null) {
                        LuckPermsHook.addGroup(account.accountUuid(), AccountUpgradeConfig.CONFIG.vip.luckPermsGroup);
                    }
                    if (!ok) {
                        source.sendFailure(Component.literal("Purchase recorded, but the account rank grant failed."));
                        return;
                    }
                    ServerPlayer online = server.getPlayerList().getPlayer(account.accountUuid());
                    String display = (tier.equals("vipplus") || tier.equals("vip_plus")) ? "VIP+" : "VIP";
                    if (online != null) online.sendSystemMessage(Component.literal("Your " + display + " account upgrade is now active.").withStyle(ChatFormatting.GREEN));
                    source.sendSuccess(() -> Component.literal("Granted " + display + " to " + account.username() + "."), true);
                }))
                .exceptionally(error -> {
                    server.execute(() -> source.sendFailure(Component.literal("Failed to process purchase: " + error.getMessage())));
                    return null;
                });
        return 1;
    }

    private static int recordOnly(CommandSourceStack source, String playerName, String packageKey, int quantity, String reference) {
        MinecraftServer server = source.getServer();
        AccountCommerceRepository.resolveAccountAsync(server, playerName)
                .thenCompose(account -> AccountCommerceRepository.recordPurchaseAsync(account, "TEBEX", packageKey, quantity, reference, "Recorded by Tebex command"))
                .thenAccept(inserted -> server.execute(() -> {
                    if (inserted) source.sendSuccess(() -> Component.literal("Recorded account purchase."), true);
                    else source.sendFailure(Component.literal("Purchase already processed or account could not be resolved."));
                }))
                .exceptionally(error -> {
                    server.execute(() -> source.sendFailure(Component.literal("Failed to record purchase: " + error.getMessage())));
                    return null;
                });
        return 1;
    }

    private static int showVoteBalance(ServerPlayer player) {
        AccountCommerceRepository.voteBalanceAsync(player.getUUID())
                .thenAccept(balance -> player.server.execute(() -> player.sendSystemMessage(
                        Component.literal("Account vote points: " + balance.points() + " (lifetime " + balance.lifetimePoints() + ")")
                                .withStyle(ChatFormatting.AQUA)
                )));
        return 1;
    }
}
