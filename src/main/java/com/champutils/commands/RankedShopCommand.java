package com.champutils.commands;

import com.champutils.menu.RankedShopMenu;
import com.champutils.rank.RankedTokenConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class RankedShopCommand {
    private RankedShopCommand() {}
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("rankedshop")
                .executes(ctx -> { RankedShopMenu.open(ctx.getSource().getPlayerOrException()); return 1; })
                .then(Commands.literal("items").executes(ctx -> { RankedShopMenu.openItems(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("add").requires(source -> source.hasPermission(4))
                        .then(Commands.literal("item")
                                .then(Commands.argument("item", StringArgumentType.string())
                                        .then(Commands.argument("cost", IntegerArgumentType.integer(1))
                                                .executes(ctx -> addItem(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx,"item"), IntegerArgumentType.getInteger(ctx,"cost"), 1))
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> addItem(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx,"item"), IntegerArgumentType.getInteger(ctx,"cost"), IntegerArgumentType.getInteger(ctx,"amount")))))))
                        .then(Commands.literal("pokemon")
                                .then(Commands.argument("species", StringArgumentType.word())
                                        .then(Commands.argument("cost", IntegerArgumentType.integer(1))
                                                .executes(ctx -> addPokemon(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx,"species"), IntegerArgumentType.getInteger(ctx,"cost")))))))));
    }
    private static int addItem(ServerPlayer player, String item, int cost, int amount) { RankedTokenConfig.CONFIG.items.add(new RankedTokenConfig.ItemEntry(item, item, cost, amount)); RankedTokenConfig.save(); player.sendSystemMessage(Component.literal("§aAdded ranked shop item: " + item)); return 1; }
    private static int addPokemon(ServerPlayer player, String species, int cost) { RankedTokenConfig.CONFIG.pokemon.add(new RankedTokenConfig.PokemonEntry(species, cost)); RankedTokenConfig.save(); player.sendSystemMessage(Component.literal("§aAdded ranked shop Pokémon: " + species)); return 1; }
}
