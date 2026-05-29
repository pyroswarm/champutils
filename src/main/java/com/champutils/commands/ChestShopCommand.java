package com.champutils.commands;

import com.champutils.economy.EconomyManager;
import com.champutils.shop.ChestShopClaimCompat;
import com.champutils.shop.ChestShopRegistry;
import com.champutils.shop.ChestShopService;
import com.champutils.profile.ProfileRestrictions;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ChestShopCommand {

    private static final double SHOP_RANGE = 6.0D;

    private ChestShopCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    literal("chestshop")
                            .then(literal("sell")
                                    .then(argument("price", LongArgumentType.longArg(1L, 9_000_000_000_000_000L))
                                            .executes(context -> create(
                                                    context.getSource().getPlayerOrException(),
                                                    ChestShopRegistry.ShopMode.SELL,
                                                    LongArgumentType.getLong(context, "price"),
                                                    1
                                            ))
                                            .then(argument("amount", IntegerArgumentType.integer(1, 3456))
                                                    .executes(context -> create(
                                                            context.getSource().getPlayerOrException(),
                                                            ChestShopRegistry.ShopMode.SELL,
                                                            LongArgumentType.getLong(context, "price"),
                                                            IntegerArgumentType.getInteger(context, "amount")
                                                    )))))
                            .then(literal("buy")
                                    .then(argument("price", LongArgumentType.longArg(1L, 9_000_000_000_000_000L))
                                            .executes(context -> create(
                                                    context.getSource().getPlayerOrException(),
                                                    ChestShopRegistry.ShopMode.BUY,
                                                    LongArgumentType.getLong(context, "price"),
                                                    1
                                            ))
                                            .then(argument("amount", IntegerArgumentType.integer(1, 3456))
                                                    .executes(context -> create(
                                                            context.getSource().getPlayerOrException(),
                                                            ChestShopRegistry.ShopMode.BUY,
                                                            LongArgumentType.getLong(context, "price"),
                                                            IntegerArgumentType.getInteger(context, "amount")
                                                    )))))
                            .then(literal("info")
                                    .executes(context -> info(context.getSource().getPlayerOrException())))
                            .then(literal("remove")
                                    .executes(context -> remove(context.getSource().getPlayerOrException(), false)))
                            .then(literal("adminremove")
                                    .requires(source -> source.hasPermission(4))
                                    .executes(context -> remove(context.getSource().getPlayerOrException(), true)))
            );

            dispatcher.register(
                    literal("shop")
                            .redirect(dispatcher.getRoot().getChild("chestshop"))
            );
        });
    }

    private static int create(ServerPlayer player, ChestShopRegistry.ShopMode mode, long price, int amount) {
        if (ProfileRestrictions.blockIronmanTrade(player, "chest shops")) {
            return 0;
        }

        Target target = getTargetChest(player);
        if (target == null) {
            player.sendSystemMessage(Component.literal("Look at a chest or barrel within 6 blocks first.").withStyle(ChatFormatting.RED));
            return 0;
        }

        ChestShopClaimCompat.ClaimCheckResult claimResult = ChestShopClaimCompat.canCreateShop(player, target.level, target.pos);
        if (claimResult != ChestShopClaimCompat.ClaimCheckResult.ALLOWED) {
            player.sendSystemMessage(claimFailureMessage(claimResult));
            return 0;
        }

        ChestShopRegistry.ChestShop existing = ChestShopRegistry.getAt(target.level, target.pos);
        if (existing != null && !existing.isOwner(player.getUUID()) && !player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("That chest is already someone else's shop.").withStyle(ChatFormatting.RED));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold the item this shop should trade.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (held.getMaxStackSize() <= 1) {
            player.sendSystemMessage(Component.literal("Chest shops only support stackable items. Use the Auction House for unique tools or special items.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (amount > 3456) {
            player.sendSystemMessage(Component.literal("Amount cannot be higher than 3456 items.").withStyle(ChatFormatting.RED));
            return 0;
        }

        ChestShopRegistry.ChestShop shop = ChestShopRegistry.createOrUpdate(
                mode,
                target.level,
                target.pos,
                player.getUUID(),
                player.getName().getString(),
                held,
                amount,
                price
        );

        if (shop == null) {
            player.sendSystemMessage(Component.literal("Could not create chest shop.").withStyle(ChatFormatting.RED));
            return 0;
        }

        String modeText = mode == ChestShopRegistry.ShopMode.SELL ? "SELL shop" : "BUY shop";
        player.sendSystemMessage(Component.literal("Created " + modeText + " for " + amount + "x " + shop.itemName + " at " + EconomyManager.format(price) + ".").withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.literal("Others can right-click this chest to trade. Sneak-right-click to manage the chest.").withStyle(ChatFormatting.GRAY));
        return 1;
    }


    private static Component claimFailureMessage(ChestShopClaimCompat.ClaimCheckResult result) {
        String message = switch (result) {
            case NO_CLAIM_MOD -> "Chest shops require Flan claims to be installed/enabled.";
            case UNCLAIMED -> "Chest shops can only be created inside claimed land.";
            case NOT_TRUSTED -> "You can only create chest shops in claims where you are trusted to build.";
            case CHECK_FAILED -> "Could not verify this claim. Ask an admin to check Flan/ChampUtils compatibility.";
            default -> "You cannot create a chest shop here.";
        };
        return Component.literal(message).withStyle(ChatFormatting.RED);
    }

    private static int info(ServerPlayer player) {
        Target target = getTargetChest(player);
        if (target == null) {
            player.sendSystemMessage(Component.literal("Look at a chest shop within 6 blocks first.").withStyle(ChatFormatting.RED));
            return 0;
        }

        ChestShopRegistry.ChestShop shop = ChestShopRegistry.getAt(target.level, target.pos);
        if (shop == null) {
            player.sendSystemMessage(Component.literal("That chest is not a shop.").withStyle(ChatFormatting.RED));
            return 0;
        }

        ChestShopService.sendInfo(player, shop);
        return 1;
    }

    private static int remove(ServerPlayer player, boolean admin) {
        Target target = getTargetChest(player);
        if (target == null) {
            player.sendSystemMessage(Component.literal("Look at a chest shop within 6 blocks first.").withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean removed = ChestShopRegistry.remove(target.level, target.pos, player.getUUID(), admin || player.hasPermissions(4));
        if (!removed) {
            player.sendSystemMessage(Component.literal("Could not remove that shop. You must be the owner or an admin.").withStyle(ChatFormatting.RED));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Chest shop removed. You can now break or reuse the chest.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static Target getTargetChest(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.x * SHOP_RANGE, look.y * SHOP_RANGE, look.z * SHOP_RANGE);

        BlockHitResult hit = level.clip(new ClipContext(
                eye,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));

        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos pos = hit.getBlockPos();
        if (!ChestShopRegistry.isValidShopContainer(level, pos)) {
            return null;
        }

        return new Target(level, pos);
    }

    private record Target(ServerLevel level, BlockPos pos) {
    }
}
