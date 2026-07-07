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
import java.util.concurrent.CompletableFuture;

public final class AccountCommerceCommand {
    private AccountCommerceCommand() {}
    private record RankGrantResult(AccountCommerceRepository.ResolvedAccount account, boolean recorded, boolean granted) {}

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
                .thenCompose(account -> AccountCommerceRepository.grantBoosterCreditsAsync(account, amount, reference))
                .thenAccept(result -> server.execute(() -> {
                    AccountCommerceRepository.ResolvedAccount account = result == null ? null : result.account();
                    boolean inserted = result != null && result.inserted();
                    if (account == null) {
                        source.sendFailure(Component.literal("Purchase account could not be resolved."));
                        return;
                    }
                    BoosterCreditManager.setCachedPurchasedCredits(account.accountUuid(), result.balance());
                    ServerPlayer online = server.getPlayerList().getPlayer(account.accountUuid());
                    if (inserted) {
                        if (online != null) online.sendSystemMessage(Component.literal("You received " + amount + " purchased booster credit(s).").withStyle(ChatFormatting.GREEN));
                        source.sendSuccess(() -> Component.literal("Granted " + amount + " account booster credit(s) to " + account.username() + "."), true);
                    } else {
                        source.sendSuccess(() -> Component.literal("Booster credit purchase was already delivered to " + account.username() + "."), true);
                    }
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
        boolean vipPlus = tier.equals("vipplus") || tier.equals("vip_plus");
        AccountCommerceRepository.resolveAccountAsync(server, playerName)
                .thenCompose(account -> {
                    if (account == null) return CompletableFuture.completedFuture(new RankGrantResult(null, false, false));
                    return AccountCommerceRepository.recordPurchaseAsync(account, "TEBEX", "rank_" + tier, 1, reference, "Purchased rank " + tier)
                            .thenCompose(recorded -> grantRankGroups(account, group, vipPlus)
                                    .thenApply(granted -> new RankGrantResult(account, recorded, granted)));
                })
                .thenAccept(result -> server.execute(() -> {
                    AccountCommerceRepository.ResolvedAccount account = result.account();
                    if (account == null) {
                        source.sendFailure(Component.literal("Purchase account could not be resolved."));
                        return;
                    }
                    if (!result.granted()) {
                        source.sendFailure(Component.literal("Purchase recorded, but the account rank grant failed. Re-run this same command after LuckPerms SQL is healthy."));
                        return;
                    }
                    ServerPlayer online = server.getPlayerList().getPlayer(account.accountUuid());
                    String display = vipPlus ? "VIP+" : "VIP";
                    if (online != null) online.sendSystemMessage(Component.literal("Your " + display + " account upgrade is now active.").withStyle(ChatFormatting.GREEN));
                    String action = result.recorded() ? "Granted " : "Ensured already-recorded ";
                    source.sendSuccess(() -> Component.literal(action + display + " for " + account.username() + "."), true);
                }))
                .exceptionally(error -> {
                    server.execute(() -> source.sendFailure(Component.literal("Failed to process purchase: " + error.getMessage())));
                    return null;
                });
        return 1;
    }

    private static CompletableFuture<Boolean> grantRankGroups(AccountCommerceRepository.ResolvedAccount account, String group, boolean vipPlus) {
        CompletableFuture<Boolean> grant = LuckPermsHook.addGroupAsync(account.accountUuid(), group);
        if (vipPlus && AccountUpgradeConfig.CONFIG.vip != null && AccountUpgradeConfig.CONFIG.vip.luckPermsGroup != null && !AccountUpgradeConfig.CONFIG.vip.luckPermsGroup.isBlank()) {
            grant = grant.thenCompose(ok -> ok
                    ? LuckPermsHook.addGroupAsync(account.accountUuid(), AccountUpgradeConfig.CONFIG.vip.luckPermsGroup)
                    : CompletableFuture.completedFuture(false));
        }
        return grant;
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
