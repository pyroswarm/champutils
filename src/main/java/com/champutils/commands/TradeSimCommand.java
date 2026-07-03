package com.champutils.commands;

import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.dex.TradeEvolutionTrueDexListener;
import com.champutils.profile.PlayerProfileManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class TradeSimCommand {
    private TradeSimCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("tradesim")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            player.sendSystemMessage(Component.literal("Use /tradesim <party slot 1-6> to trigger an Ironman solo trade evolution.").withStyle(ChatFormatting.AQUA));
                            return 1;
                        })
                        .then(argument("slot", IntegerArgumentType.integer(1, 6))
                                .executes(context -> run(
                                        context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "slot"),
                                        null
                                ))
                                .then(argument("partnerSlot", IntegerArgumentType.integer(1, 6))
                                        .executes(context -> run(
                                                context.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(context, "slot"),
                                                IntegerArgumentType.getInteger(context, "partnerSlot")
                                        )))
                        )
        ));
    }

    private static int run(ServerPlayer player, int slot, Integer partnerSlot) {
        if (!PlayerProfileManager.isIronman(player) && !player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("/tradesim is only for Ironman profiles.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (partnerSlot != null && partnerSlot == slot) {
            player.sendSystemMessage(Component.literal("The partner slot must be a different party slot.").withStyle(ChatFormatting.RED));
            return 0;
        }

        Object pokemon = AuctionPokemonSerializer.getPartyPokemon(player, slot - 1);
        if (pokemon == null) {
            player.sendSystemMessage(Component.literal("No Pokémon found in party slot " + slot + ".").withStyle(ChatFormatting.RED));
            return 0;
        }

        Object partner = pokemon;
        if (partnerSlot != null) {
            partner = AuctionPokemonSerializer.getPartyPokemon(player, partnerSlot - 1);
            if (partner == null) {
                player.sendSystemMessage(Component.literal("No Pokémon found in party slot " + partnerSlot + ".").withStyle(ChatFormatting.RED));
                return 0;
            }
        }

        String before = speciesName(pokemon);
        String partnerBefore = partnerSlot == null ? null : speciesName(partner);
        boolean attempted = attemptTradeEvolutions(pokemon, partner);
        if (partnerSlot != null) {
            attempted = attemptTradeEvolutions(partner, pokemon) || attempted;
        }
        String after = speciesName(pokemon);
        String partnerAfter = partnerSlot == null ? null : speciesName(partner);

        if (!attempted) {
            player.sendSystemMessage(Component.literal(partnerSlot == null
                    ? "That Pokémon does not currently have a trade evolution available. Check level, held item, and trade requirements."
                    : "Those Pokémon do not currently have a paired trade evolution available. Check species pairing, level, held item, and trade requirements."
            ).withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        TradeEvolutionTrueDexListener.markTradeEvolution(player, pokemon);
        if (partnerSlot != null) {
            TradeEvolutionTrueDexListener.markTradeEvolution(player, partner);
        }
        player.sendSystemMessage(Component.literal(partnerSlot == null
                ? "Trade simulation complete for slot " + slot + "."
                : "Trade simulation complete for slots " + slot + " and " + partnerSlot + "."
        ).withStyle(ChatFormatting.GREEN));
        if (before != null && after != null && !before.equalsIgnoreCase(after)) {
            player.sendSystemMessage(Component.literal(pretty(before) + " evolved into " + pretty(after) + ".").withStyle(ChatFormatting.AQUA));
        }
        if (partnerBefore != null && partnerAfter != null && !partnerBefore.equalsIgnoreCase(partnerAfter)) {
            player.sendSystemMessage(Component.literal(pretty(partnerBefore) + " evolved into " + pretty(partnerAfter) + ".").withStyle(ChatFormatting.AQUA));
        }
        if ((before == null || after == null || before.equalsIgnoreCase(after))
                && (partnerBefore == null || partnerAfter == null || partnerBefore.equalsIgnoreCase(partnerAfter))) {
            player.sendSystemMessage(Component.literal("If Cobblemon shows an evolution prompt, click Evolve to finish it.").withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static boolean attemptTradeEvolutions(Object pokemon, Object tradeContext) {
        boolean attempted = false;
        try {
            Object evolutions = firstValue(pokemon, "lockedEvolutions", "getLockedEvolutions");
            if (!(evolutions instanceof Iterable<?> iterable)) return false;
            for (Object evolution : iterable) {
                if (evolution == null) continue;
                String className = evolution.getClass().getName().toLowerCase(Locale.ROOT);
                if (!className.contains("tradeevolution")) continue;
                attempted = true;
                for (Method method : evolution.getClass().getMethods()) {
                    if (!method.getName().equals("attemptEvolution")) continue;
                    if (method.getParameterCount() != 2) continue;
                    try {
                        method.setAccessible(true);
                        method.invoke(evolution, pokemon, tradeContext);
                        return true;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return attempted;
    }

    private static Object firstValue(Object source, String... names) {
        if (source == null) return null;
        for (String name : names) {
            try {
                if (name.startsWith("get")) {
                    Method method = source.getClass().getMethod(name);
                    method.setAccessible(true);
                    if (method.getParameterCount() == 0) {
                        Object value = method.invoke(source);
                        if (value != null) return value;
                    }
                } else {
                    try {
                        java.lang.reflect.Field field = source.getClass().getDeclaredField(name);
                        field.setAccessible(true);
                        Object value = field.get(source);
                        if (value != null) return value;
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String speciesName(Object pokemon) {
        try {
            Object species = firstValue(pokemon, "species", "getSpecies");
            Object name = firstValue(species, "name", "getName");
            if (name != null) return String.valueOf(name);
        } catch (Throwable ignored) {}
        return null;
    }

    private static String pretty(String raw) {
        if (raw == null || raw.isBlank()) return "Pokémon";
        String text = raw.replace('_', ' ').replace('-', ' ');
        StringBuilder out = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.length() == 0 ? raw : out.toString();
    }
}
