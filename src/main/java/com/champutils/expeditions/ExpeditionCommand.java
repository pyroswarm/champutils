package com.champutils.expeditions;

import com.champutils.profile.PlayerProfileManager;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.ActivePokemonState;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ExpeditionCommand {
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private ExpeditionCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(literal("expeditions")
                .executes(ctx -> { menu(ctx.getSource().getPlayerOrException()); return 1; })
                .then(literal("start")
                        .then(argument("slot", IntegerArgumentType.integer(1, 6))
                                .executes(ctx -> { preview(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot")); return 1; })))
                .then(argument("slot", IntegerArgumentType.integer(1, 6))
                        .executes(ctx -> { preview(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot")); return 1; }))
                .then(literal("confirm")
                        .executes(ctx -> { confirm(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(literal("claim")
                        .executes(ctx -> { ExpeditionManager.claim(ctx.getSource().getPlayerOrException()); return 1; }))));
    }

    static void menu(ServerPlayer player) {
        ExpeditionMenu.open(player);
    }

    static void preview(ServerPlayer player, int slot) {
        if (hasAnySentOutPokemon(player)) {
            player.sendSystemMessage(Component.literal("Recall all of your Pokémon before starting an expedition.").withStyle(ChatFormatting.RED));
            return;
        }
        if (ExpeditionManager.hasActive(player)) {
            player.sendSystemMessage(Component.literal("You already have an active expedition. Use /expeditions claim when it is finished.").withStyle(ChatFormatting.RED));
            ExpeditionManager.status(player);
            return;
        }

        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        Pokemon pokemon = party == null ? null : party.get(slot - 1);
        if (pokemon == null) {
            player.sendSystemMessage(Component.literal("No Pokémon in that slot.").withStyle(ChatFormatting.RED));
            return;
        }

        ExpeditionMenu.chooseType(player, slot, pokemon);
    }

    static void previewType(ServerPlayer player, int slot, String type) {
        if (hasAnySentOutPokemon(player)) {
            player.sendSystemMessage(Component.literal("Recall all of your Pokémon before starting an expedition.").withStyle(ChatFormatting.RED));
            return;
        }
        if (ExpeditionManager.hasActive(player)) {
            player.sendSystemMessage(Component.literal("You already have an active expedition. Use /expeditions claim when it is finished.").withStyle(ChatFormatting.RED));
            ExpeditionManager.status(player);
            return;
        }
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        Pokemon pokemon = party == null ? null : party.get(slot - 1);
        if (pokemon == null) {
            player.sendSystemMessage(Component.literal("No Pokémon in that slot.").withStyle(ChatFormatting.RED));
            return;
        }
        ExpeditionConfig.Tier tier = ExpeditionConfig.tier(pokemon.getLevel());
        long hours = Math.max(1, tier.hours);
        long endsAt = System.currentTimeMillis() + hours * 3_600_000L;
        String normalizedType = ExpeditionConfig.normalizeType(type);
        PENDING.put(PlayerProfileManager.activeProfileId(player), new Pending(slot, endsAt, normalizedType));
        ExpeditionMenu.preview(player, slot, pokemon, endsAt, normalizedType);
    }

    static void confirm(ServerPlayer player) {
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        Pending pending = PENDING.remove(profileId);
        if (pending == null) {
            player.sendSystemMessage(Component.literal("No pending expedition. Start one with /expeditions start <slot>.").withStyle(ChatFormatting.RED));
            return;
        }
        if (ExpeditionManager.hasActive(player)) {
            player.sendSystemMessage(Component.literal("You already have an active expedition. Use /expeditions claim when it is finished.").withStyle(ChatFormatting.RED));
            return;
        }
        if (hasAnySentOutPokemon(player)) {
            player.sendSystemMessage(Component.literal("Recall all of your Pokémon before starting an expedition.").withStyle(ChatFormatting.RED));
            return;
        }

        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        Pokemon pokemon = party == null ? null : party.get(pending.slot - 1);
        if (pokemon == null) {
            player.sendSystemMessage(Component.literal("That Pokémon is no longer in that slot.").withStyle(ChatFormatting.RED));
            return;
        }

        try {
            String sentName = ExpeditionManager.startFromPartySlot(player, pending.slot - 1, pending.endsAt, pending.type);
            player.closeContainer();
            player.sendSystemMessage(Component.literal(sentName + " was sent on an expedition. Check it with /expeditions and claim it with /expeditions claim.").withStyle(ChatFormatting.GREEN));
        } catch (Exception e) {
            e.printStackTrace();
            player.sendSystemMessage(Component.literal("Could not start that expedition safely. Your Pokémon was not duplicated; check console for details.").withStyle(ChatFormatting.RED));
        }
    }

    private static boolean hasAnySentOutPokemon(ServerPlayer player) {
        try {
            PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            if (party == null) return false;
            for (int i = 0; i < 6; i++) {
                Pokemon pokemon = party.get(i);
                if (pokemon == null) continue;
                if (pokemon.getState() instanceof ActivePokemonState activeState) {
                    PokemonEntity entity = activeState.getEntity();
                    if (entity != null && entity.isAlive()) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private record Pending(int slot, long endsAt, String type) {}
}
