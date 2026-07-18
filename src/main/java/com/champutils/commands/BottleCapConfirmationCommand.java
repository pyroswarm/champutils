package com.champutils.commands;

import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.breeding.PokemonBreedability;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.arguments.StringArgumentType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BottleCapConfirmationCommand {
    private static final Set<UUID> OPEN = ConcurrentHashMap.newKeySet();
    private BottleCapConfirmationCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("champutilsbottlecap")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> open(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "type"), InteractionHand.OFF_HAND)))
        ));
        UseItemCallback.EVENT.register((user, world, hand) -> {
            if (!(user instanceof ServerPlayer player) || world.isClientSide()) return InteractionResultHolder.pass(user.getItemInHand(hand));
            String type = capType(user.getItemInHand(hand));
            if (type == null) return InteractionResultHolder.pass(user.getItemInHand(hand));
            open(player, type, hand);
            return InteractionResultHolder.success(user.getItemInHand(hand));
        });
    }

    private static int open(ServerPlayer player, String type, InteractionHand hand) {
        if (!OPEN.add(player.getUUID())) return 0;
        Pokemon pokemon = AuctionPokemonSerializer.getPartyPokemon(player, 0);
        ItemStack cap = player.getItemInHand(hand);
        if (pokemon == null || cap.isEmpty() || cap.getItem() != Items.PAPER || !validCap(cap, type)) { OPEN.remove(player.getUUID()); return 0; }
        String label = pretty(type);
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false) {
            @Override public void onClose() { super.onClose(); OPEN.remove(player.getUUID()); }
        };
        gui.setTitle(Component.literal("Use Bottle Cap?"));
        gui.setSlot(4, new GuiElementBuilder(Items.EXPERIENCE_BOTTLE).hideDefaultTooltip()
                .setName(Component.literal("§e" + label + " Bottle Cap"))
                .addLoreLine(Component.literal("§7Target: §f" + pokemon.getDisplayName(true).getString()))
                .addLoreLine(Component.literal("§7Target party slot: §f1"))
                .addLoreLine(Component.literal("§cUsing this will permanently make"))
                .addLoreLine(Component.literal("§cthis Pokémon unbreedable."))
                .addLoreLine(Component.literal("§8This protects the breeding economy.")));
        gui.setSlot(11, new GuiElementBuilder(Items.LIME_CONCRETE).hideDefaultTooltip().setName(Component.literal("§aConfirm and Use"))
                .addLoreLine(Component.literal("§cPermanent: Breedable becomes No"))
                .setCallback((s,c,a) -> apply(player, pokemon, type, hand, gui)));
        gui.setSlot(15, new GuiElementBuilder(Items.RED_CONCRETE).hideDefaultTooltip().setName(Component.literal("§cCancel"))
                .addLoreLine(Component.literal("§7The Bottle Cap will not be consumed."))
                .setCallback((s,c,a) -> gui.close()));
        gui.open();
        return 1;
    }

    private static void apply(ServerPlayer player, Pokemon expected, String type, InteractionHand hand, SimpleGui gui) {
        Pokemon pokemon = AuctionPokemonSerializer.getPartyPokemon(player, 0);
        ItemStack cap = player.getItemInHand(hand);
        if (pokemon == null || !pokemon.getUuid().equals(expected.getUuid()) || cap.isEmpty() || !validCap(cap, type)) {
            player.sendSystemMessage(Component.literal("The Pokémon or Bottle Cap changed. Nothing was consumed.").withStyle(ChatFormatting.RED));
            gui.close();
            return;
        }
        for (Stat stat : stats(type)) pokemon.getIvs().set(stat, 31);
        PokemonBreedability.makeUnbreedable(pokemon, "Bottle Cap / Hyper Training used");
        PokemonBreedability.refreshIvBaseline(pokemon);
        cap.shrink(1);
        player.setItemInHand(hand, cap);
        player.sendSystemMessage(Component.literal("Bottle Cap applied. " + pokemon.getDisplayName(true).getString() + " is permanently unbreedable.").withStyle(ChatFormatting.GREEN));
        gui.close();
    }

    private static String capType(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.PAPER) return null;
        for (String type : List.of("golden", "attack", "defence", "hp", "special_attack", "special_defence", "speed")) {
            if (validCap(stack, type)) return type;
        }
        return null;
    }

    private static boolean validCap(ItemStack stack, String type) {
        String wanted = type == null ? "" : type.toLowerCase(Locale.ROOT);
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT).replace(".", "").replace(" ", "_");
        if (wanted.equals("golden")) return name.contains("golden") && name.contains("bottle") && name.contains("cap");
        return switch (wanted) {
            case "attack" -> name.equals("atk"); case "defence" -> name.equals("def"); case "hp" -> name.equals("hp");
            case "special_attack" -> name.equals("spatk"); case "special_defence" -> name.equals("spdef"); case "speed" -> name.equals("speed"); default -> false;
        };
    }

    private static List<Stat> stats(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "attack" -> List.of(Stats.ATTACK); case "defence" -> List.of(Stats.DEFENCE); case "hp" -> List.of(Stats.HP);
            case "special_attack" -> List.of(Stats.SPECIAL_ATTACK); case "special_defence" -> List.of(Stats.SPECIAL_DEFENCE); case "speed" -> List.of(Stats.SPEED);
            default -> List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED);
        };
    }
    private static String pretty(String value) { return value == null ? "Golden" : value.replace('_',' '); }
}
