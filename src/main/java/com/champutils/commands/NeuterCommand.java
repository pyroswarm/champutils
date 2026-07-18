package com.champutils.commands;

import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.breeding.PokemonBreedability;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public final class NeuterCommand {
    private NeuterCommand() {}
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("neuter")
                        // Default player command: no OP level or LuckPerms node required.
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                .executes(ctx -> open(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot") - 1)))
        ));
    }

    private static int open(ServerPlayer player, int slot) {
        Pokemon pokemon = AuctionPokemonSerializer.getPartyPokemon(player, slot);
        if (pokemon == null) {
            player.sendSystemMessage(Component.literal("That party slot is empty.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!PokemonBreedability.isBreedable(pokemon)) {
            player.sendSystemMessage(Component.literal("That Pokémon is already permanently unbreedable.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setTitle(Component.literal("Permanently Neuter Pokémon?"));
        gui.setSlot(4, new GuiElementBuilder(CobblemonItemHelper.icon()).hideDefaultTooltip()
                .setName(Component.literal("§d" + pokemon.getDisplayName(true).getString()))
                .addLoreLine(Component.literal("§7Party Slot: §f" + (slot + 1)))
                .addLoreLine(Component.literal("§cThis action is permanent."))
                .addLoreLine(Component.literal("§cThis Pokémon will never breed again.")));
        gui.setSlot(11, new GuiElementBuilder(Items.LIME_CONCRETE).hideDefaultTooltip().setName(Component.literal("§aConfirm Permanent Neuter"))
                .addLoreLine(Component.literal("§7This cannot be undone."))
                .setCallback((s,c,a) -> {
                    Pokemon current = AuctionPokemonSerializer.getPartyPokemon(player, slot);
                    if (current == null || !current.getUuid().equals(pokemon.getUuid())) {
                        player.sendSystemMessage(Component.literal("The Pokémon in that slot changed. Nothing happened.").withStyle(ChatFormatting.RED));
                    } else {
                        PokemonBreedability.makeUnbreedable(current, "Neutered by owner");
                        player.sendSystemMessage(Component.literal(current.getDisplayName(true).getString() + " is now permanently unbreedable.").withStyle(ChatFormatting.GREEN));
                    }
                    gui.close();
                }));
        gui.setSlot(15, new GuiElementBuilder(Items.RED_CONCRETE).hideDefaultTooltip().setName(Component.literal("§cCancel"))
                .setCallback((s,c,a) -> gui.close()));
        gui.open();
        return 1;
    }

    private static final class CobblemonItemHelper {
        static net.minecraft.world.item.Item icon() {
            try { return com.cobblemon.mod.common.CobblemonItems.POKE_BALL; }
            catch (Throwable ignored) { return Items.EGG; }
        }
    }
}
