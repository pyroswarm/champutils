package com.champutils.expeditions;

import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.profile.PlayerProfileManager;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
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
        PENDING.put(PlayerProfileManager.activeProfileId(player), new Pending(slot, endsAt));
        ExpeditionMenu.preview(player, slot, pokemon, endsAt);
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

        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        Pokemon pokemon = party == null ? null : party.get(pending.slot - 1);
        if (pokemon == null) {
            player.sendSystemMessage(Component.literal("That Pokémon is no longer in that slot.").withStyle(ChatFormatting.RED));
            return;
        }

        try {
            ExpeditionManager.start(player, pending.slot, pokemon, pending.endsAt);
            AuctionPokemonSerializer.clearPartySlot(player, pending.slot - 1);
            player.closeContainer();
            player.sendSystemMessage(Component.literal(pokemon.getDisplayName(true).getString() + " was sent on an expedition. Check it with /expeditions and claim it with /expeditions claim.").withStyle(ChatFormatting.GREEN));
        } catch (Exception e) {
            e.printStackTrace();
            player.sendSystemMessage(Component.literal("Could not start that expedition. Check console for details.").withStyle(ChatFormatting.RED));
        }
    }

    private record Pending(int slot, long endsAt) {}
}
