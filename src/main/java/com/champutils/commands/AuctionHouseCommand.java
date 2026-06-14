package com.champutils.commands;

import com.champutils.profile.ProfileRestrictions;
import com.champutils.auction.AuctionHouseBindInteractionListener;
import com.champutils.auction.AuctionHouseGui;
import com.champutils.auction.AuctionHouseNpcBindingRegistry;
import com.champutils.auction.AuctionHouseService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class AuctionHouseCommand {

    private AuctionHouseCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("ah")
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            if (ProfileRestrictions.blockIronmanTrade(player, "Auction House")) return 0;
                            AuctionHouseGui.openMain(player);
                            return 1;
                        })
                        .then(literal("sell")
                                .then(argument("price", LongArgumentType.longArg(1L, 9_000_000_000_000_000L))
                                        .executes(context -> {
                                            var player = context.getSource().getPlayerOrException();
                                                    if (ProfileRestrictions.blockIronmanTrade(player, "Auction House")) return 0;
                                                    AuctionHouseService.beginHeldItemListing(
                                                    player,
                                                    LongArgumentType.getLong(context, "price")
                                            );
                                            return 1;
                                        })))
                        .then(literal("sellpokemon")
                                .then(argument("slot", IntegerArgumentType.integer(1, 6))
                                        .then(argument("price", LongArgumentType.longArg(1L, 9_000_000_000_000_000L))
                                                .executes(context -> {
                                                    var player = context.getSource().getPlayerOrException();
                                                            if (ProfileRestrictions.blockIronmanTrade(player, "Auction House")) return 0;
                                                            AuctionHouseService.beginPokemonListing(
                                                            player,
                                                            IntegerArgumentType.getInteger(context, "slot"),
                                                            LongArgumentType.getLong(context, "price")
                                                    );
                                                    return 1;
                                                }))))
                        .then(literal("confirm")
                                .executes(context -> {
                                    var player = context.getSource().getPlayerOrException();
                                    if (ProfileRestrictions.blockIronmanTrade(player, "Auction House")) return 0;
                                    AuctionHouseService.confirmPending(player);
                                    return 1;
                                }))
                        .then(literal("cancel")
                                .executes(context -> {
                                    AuctionHouseService.cancelPending(context.getSource().getPlayerOrException());
                                    return 1;
                                })
                                .then(argument("listingId", StringArgumentType.word())
                                        .executes(context -> cancelListing(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "listingId")
                                        ))))
                        .then(literal("claim")
                                .executes(context -> {
                                    var player = context.getSource().getPlayerOrException();
                                    if (ProfileRestrictions.blockIronmanTrade(player, "Auction House")) return 0;
                                    AuctionHouseService.claimNext(player);
                                    return 1;
                                }))
                        .then(literal("mylistings")
                                .executes(context -> {
                                    var player = context.getSource().getPlayerOrException();
                                    if (ProfileRestrictions.blockIronmanTrade(player, "Auction House")) return 0;
                                    AuctionHouseGui.openMyListings(player);
                                    return 1;
                                }))
                        .then(literal("bind")
                                .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                                .executes(context -> {
                                    AuctionHouseBindInteractionListener.beginBind(context.getSource().getPlayerOrException());
                                    return 1;
                                }))
                        .then(literal("bindcancel")
                                .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                                .executes(context -> {
                                    boolean cancelled = AuctionHouseBindInteractionListener.cancelBind(context.getSource().getPlayerOrException());
                                    if (cancelled) {
                                        context.getSource().sendSuccess(() -> Component.literal("Cancelled pending auction NPC bind.").withStyle(ChatFormatting.YELLOW), false);
                                    } else {
                                        context.getSource().sendFailure(Component.literal("You do not have a pending auction NPC bind."));
                                    }
                                    return cancelled ? 1 : 0;
                                }))
                        .then(literal("unbind")
                                .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                                .executes(context -> {
                                    AuctionHouseNpcBindingRegistry.unbind();
                                    context.getSource().sendSuccess(() -> Component.literal("Unbound the Auction NPC.").withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                }))
        ));
    }

    private static int cancelListing(CommandSourceStack source, String rawId) {
        try {
            UUID listingId = UUID.fromString(rawId);
            AuctionHouseService.cancelListing(source.getPlayerOrException(), listingId);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Invalid listing ID. Open /ah > Your Listings and click the listing instead."));
            return 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can cancel auction listings."));
            return 0;
        }
    }
}
