package com.champutils.commands;

import com.champutils.economy.EconomyManager;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Credit-backed egg-move tutor with a player GUI and staff recovery command. */
public final class EggMoveCommand {
    private static final Set<UUID> PENDING_PURCHASES = ConcurrentHashMap.newKeySet();

    private EggMoveCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(root("eggmoves"));
            dispatcher.register(root("eggmove"));
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .executes(ctx -> openMenu(ctx.getSource()))
                .then(Commands.literal("list")
                        .then(Commands.argument("partySlot", IntegerArgumentType.integer(1, 6))
                                .executes(ctx -> list(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "partySlot")))))
                .then(Commands.literal("teach")
                        .then(Commands.argument("partySlot", IntegerArgumentType.integer(1, 6))
                                .then(Commands.argument("move", StringArgumentType.word())
                                        .executes(ctx -> teachPlayer(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "partySlot"),
                                                StringArgumentType.getString(ctx, "move"), 0))
                                        .then(Commands.argument("replaceMoveSlot", IntegerArgumentType.integer(1, 4))
                                                .executes(ctx -> teachPlayer(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "partySlot"),
                                                        StringArgumentType.getString(ctx, "move"),
                                                        IntegerArgumentType.getInteger(ctx, "replaceMoveSlot")))))))
                .then(Commands.literal("give")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("partySlot", IntegerArgumentType.integer(1, 6))
                                        .then(Commands.argument("move", StringArgumentType.word())
                                                .executes(ctx -> teachAdmin(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "partySlot"),
                                                        StringArgumentType.getString(ctx, "move"), 0))
                                                .then(Commands.argument("replaceMoveSlot", IntegerArgumentType.integer(1, 4))
                                                        .executes(ctx -> teachAdmin(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                                IntegerArgumentType.getInteger(ctx, "partySlot"),
                                                                StringArgumentType.getString(ctx, "move"),
                                                                IntegerArgumentType.getInteger(ctx, "replaceMoveSlot"))))))));
    }

    private static int openMenu(CommandSourceStack source) {
        try {
            EggMoveMenu.open(source.getPlayerOrException());
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can open the egg-move tutor."));
            return 0;
        }
    }

    private static int list(CommandSourceStack source, int partySlot) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            Pokemon pokemon = partyPokemon(player, partySlot);
            if (pokemon == null) {
                source.sendFailure(Component.literal("There is no Pokémon in that party slot."));
                return 0;
            }
            List<String> moves = learnableEggMoveIds(pokemon);
            if (moves.isEmpty()) {
                player.sendSystemMessage(Component.literal(displayName(pokemon) + " has no unlearned egg moves available.").withStyle(ChatFormatting.YELLOW));
                return 1;
            }
            player.sendSystemMessage(Component.literal("Available egg moves for " + displayName(pokemon) + ":").withStyle(ChatFormatting.AQUA));
            player.sendSystemMessage(Component.literal(String.join(", ", moves.stream().map(EggMoveCommand::prettyMove).toList())).withStyle(ChatFormatting.WHITE));
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can inspect their party."));
            return 0;
        }
    }

    private static int teachPlayer(CommandSourceStack source, int partySlot, String move, int replaceSlot) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            Pokemon pokemon = partyPokemon(player, partySlot);
            if (pokemon == null) {
                player.sendSystemMessage(Component.literal("There is no Pokémon in party slot " + partySlot + ".").withStyle(ChatFormatting.RED));
                return 0;
            }
            beginPurchase(player, pokemon.getUuid(), move, replaceSlot);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can teach egg moves."));
            return 0;
        }
    }

    private static int teachAdmin(CommandSourceStack source, ServerPlayer target, int partySlot, String move, int replaceSlot) {
        TeachResult result = teach(target, partySlot, move, replaceSlot);
        if (!result.success) {
            source.sendFailure(Component.literal(result.message));
            return 0;
        }
        target.sendSystemMessage(Component.literal(result.message).withStyle(ChatFormatting.GREEN));
        source.sendSuccess(() -> Component.literal("Egg move granted to " + target.getName().getString() + "."), true);
        return 1;
    }

    static void beginPurchase(ServerPlayer player, UUID pokemonId, String rawMove, int replaceSlot) {
        if (player == null || pokemonId == null) return;
        String moveId = normalizeMove(rawMove);
        Pokemon target = findPartyPokemon(player, pokemonId);
        TeachResult validation = validate(target, moveId, replaceSlot);
        if (!validation.success) {
            player.sendSystemMessage(Component.literal(validation.message).withStyle(ChatFormatting.RED));
            return;
        }
        if (!PENDING_PURCHASES.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Your previous egg-move purchase is still processing.").withStyle(ChatFormatting.YELLOW));
            return;
        }

        long price = priceCents(moveId);
        player.closeContainer();
        EconomyManager.withdrawAsync(player, price, "Egg move tutor: " + moveId).whenComplete((result, error) -> {
            if (player.getServer() == null) {
                PENDING_PURCHASES.remove(player.getUUID());
                return;
            }
            player.getServer().execute(() -> {
                try {
                    if (error != null || result == null || !result.success) {
                        String message = result != null && result.error != null ? result.error : "The credit charge failed.";
                        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
                        return;
                    }
                    Pokemon current = findPartyPokemon(player, pokemonId);
                    TeachResult applied = teach(current, moveId, replaceSlot);
                    if (applied.success) {
                        player.sendSystemMessage(Component.literal(applied.message + " Cost: " + EconomyManager.format(price) + ".").withStyle(ChatFormatting.GREEN));
                    } else {
                        EconomyManager.depositAsync(player, price, "Egg move tutor refund: " + moveId);
                        player.sendSystemMessage(Component.literal(applied.message + " Your credits were refunded.").withStyle(ChatFormatting.RED));
                    }
                } finally {
                    PENDING_PURCHASES.remove(player.getUUID());
                }
            });
        });
    }

    static long priceCents(String moveId) {
        return EconomyManager.creditsToCents(EggMoveConfig.priceCreditsForMove(moveId));
    }

    static Pokemon partyPokemon(ServerPlayer player, int oneBasedSlot) {
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int index = oneBasedSlot - 1;
        return party == null || index < 0 || index >= party.size() ? null : party.get(index);
    }

    static Pokemon findPartyPokemon(ServerPlayer player, UUID pokemonId) {
        if (player == null || pokemonId == null) return null;
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party == null) return null;
        for (int i = 0; i < party.size(); i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon != null && pokemonId.equals(pokemon.getUuid())) return pokemon;
        }
        return null;
    }

    static List<String> eggMoveIds(Pokemon pokemon) {
        List<String> moves = new ArrayList<>();
        if (pokemon == null || pokemon.getForm() == null || pokemon.getForm().getMoves() == null) return moves;
        for (MoveTemplate template : pokemon.getForm().getMoves().getEggMoves()) {
            if (template != null) moves.add(normalizeMove(template.getName()));
        }
        moves.sort(String::compareTo);
        return moves;
    }

    static List<String> learnableEggMoveIds(Pokemon pokemon) {
        List<String> current = currentMoveIds(pokemon);
        return eggMoveIds(pokemon).stream().filter(move -> !current.contains(move)).toList();
    }

    static List<String> currentMoveIds(Pokemon pokemon) {
        List<String> moves = new ArrayList<>();
        if (pokemon == null) return moves;
        for (Move move : pokemon.getMoveSet().getMoves()) {
            if (move != null) moves.add(normalizeMove(move.getName()));
        }
        return moves;
    }

    static String normalizeMove(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
    }

    static String prettyMove(String value) {
        return com.champutils.tm.TMManager.prettyMove(normalizeMove(value));
    }

    static String displayName(Pokemon pokemon) {
        try { return pokemon.getDisplayName(true).getString(); }
        catch (Throwable ignored) { return pokemon == null ? "Pokémon" : pokemon.getSpecies().getName(); }
    }

    private static TeachResult teach(ServerPlayer player, int partySlot, String rawMove, int replaceSlot) {
        return teach(partyPokemon(player, partySlot), normalizeMove(rawMove), replaceSlot);
    }

    private static TeachResult validate(Pokemon pokemon, String moveId, int replaceSlot) {
        if (pokemon == null) return TeachResult.fail("That Pokémon is no longer in your party.");
        MoveTemplate template = Moves.getByName(moveId);
        if (template == null) return TeachResult.fail("Unknown Cobblemon move: " + moveId + ".");
        if (!eggMoveIds(pokemon).contains(moveId)) return TeachResult.fail(displayName(pokemon) + " cannot learn " + prettyMove(moveId) + " as an egg move.");
        List<String> current = currentMoveIds(pokemon);
        if (current.contains(moveId)) return TeachResult.fail(displayName(pokemon) + " already knows " + prettyMove(moveId) + ".");
        if (current.size() >= 4 && (replaceSlot < 1 || replaceSlot > 4)) return TeachResult.fail(displayName(pokemon) + " already knows four moves. Choose one to replace.");
        return TeachResult.ok("Ready");
    }

    private static TeachResult teach(Pokemon pokemon, String moveId, int replaceSlot) {
        TeachResult validation = validate(pokemon, moveId, replaceSlot);
        if (!validation.success) return validation;
        MoveTemplate template = Moves.getByName(moveId);
        try {
            List<String> current = currentMoveIds(pokemon);
            if (current.size() < 4) pokemon.getMoveSet().add(template.create());
            else pokemon.getMoveSet().setMove(replaceSlot - 1, template.create());
            return TeachResult.ok(displayName(pokemon) + " learned egg move " + prettyMove(moveId) + "!");
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return TeachResult.fail("The egg move could not be applied safely.");
        }
    }

    private static final class TeachResult {
        final boolean success;
        final String message;
        private TeachResult(boolean success, String message) { this.success = success; this.message = message; }
        static TeachResult ok(String message) { return new TeachResult(true, message); }
        static TeachResult fail(String message) { return new TeachResult(false, message); }
    }
}
