package com.champutils.commands;

import com.champutils.tm.TMManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class TMCommand {
    private TMCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("tms")
                        .executes(ctx -> usage(ctx.getSource()))
                        .then(Commands.literal("help")
                                .executes(ctx -> usage(ctx.getSource())))
                        .then(Commands.literal("teach")
                                .then(Commands.argument("partySlot", IntegerArgumentType.integer(1, 6))
                                        .executes(ctx -> teach(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "partySlot"), 0))
                                        .then(Commands.argument("replaceMoveSlot", IntegerArgumentType.integer(1, 4))
                                                .executes(ctx -> teach(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "partySlot"), IntegerArgumentType.getInteger(ctx, "replaceMoveSlot"))))))
                        .then(Commands.literal("confirm")
                                .then(Commands.argument("token", StringArgumentType.word())
                                        .executes(ctx -> confirm(ctx.getSource(), StringArgumentType.getString(ctx, "token")))))
                        .then(Commands.literal("give")
                                .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.staff"))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("move", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(TMManager.registeredMoveIds(), builder))
                                                .executes(ctx -> give(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "move"), 1))
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                        .executes(ctx -> give(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "move"), IntegerArgumentType.getInteger(ctx, "amount")))))))
                        .then(Commands.literal("list")
                                .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.staff"))
                                .executes(ctx -> list(ctx.getSource())))
                        .then(Commands.literal("rarity")
                                .then(Commands.argument("move", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(TMManager.registeredMoveIds(), builder))
                                        .executes(ctx -> rarity(ctx.getSource(), StringArgumentType.getString(ctx, "move")))))
                        .then(Commands.literal("craft")
                                .then(Commands.argument("rarity", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(com.champutils.tm.TMConfig.RARITIES, builder))
                                        .executes(ctx -> craft(ctx.getSource(), StringArgumentType.getString(ctx, "rarity")))))
                        .then(Commands.literal("craftmove")
                                .then(Commands.argument("move", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(TMManager.registeredMoveIds(), builder))
                                        .executes(ctx -> craftMove(ctx.getSource(), StringArgumentType.getString(ctx, "move")))))
        ));
    }

    private static int usage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("TM commands: /tms teach <partySlot> [replaceMoveSlot], then click Confirm in chat. Admin: /tms give <player> <move> [amount], /tms list, /tms rarity <move>. Craft: /tms craft <rarity> for a random TM, /tms craftmove <move> for an exact TM at the same cost"), false);
        return 1;
    }

    private static int teach(CommandSourceStack source, int partySlot, int replaceSlot) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            TMManager.TeachPreview preview = TMManager.previewHeldTM(player, partySlot, replaceSlot);
            if (!preview.success()) {
                player.sendSystemMessage(Component.literal(preview.message()).withStyle(ChatFormatting.RED));
                return 0;
            }
            java.util.UUID token = TMManager.createPendingTeach(player, partySlot, replaceSlot);
            if (token == null) {
                player.sendSystemMessage(Component.literal("Hold the TM in your main hand first.").withStyle(ChatFormatting.RED));
                return 0;
            }
            Component confirm = Component.literal("[CONFIRM]")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.GREEN)
                            .withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tms confirm " + token))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to use this TM."))));
            Component cancel = Component.literal("  [Cancel]").withStyle(ChatFormatting.GRAY);
            player.sendSystemMessage(Component.literal(preview.message()).withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(confirm.copy().append(cancel));
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can use /tms teach."));
            return 0;
        }
    }

    private static int confirm(CommandSourceStack source, String token) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            TMManager.TeachResult result = TMManager.confirmPendingTeach(player, token);
            player.sendSystemMessage(Component.literal(result.message()).withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
            return result.success() ? 1 : 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can confirm TMs."));
            return 0;
        }
    }

    private static int give(CommandSourceStack source, ServerPlayer target, String move, int amount) {
        if (!TMManager.isRegisteredMove(move)) {
            source.sendFailure(Component.literal("Unknown/unregistered TM move: " + move));
            return 0;
        }
        for (int i = 0; i < amount; i++) {
            ItemStack stack = TMManager.createTMStack(move, 1);
            if (!stack.isEmpty()) target.getInventory().add(stack);
        }
        source.sendSuccess(() -> Component.literal("Gave " + amount + " TM " + TMManager.prettyMove(move) + " to " + target.getName().getString() + "."), true);
        return 1;
    }

    private static int rarity(CommandSourceStack source, String move) {
        String moveId = TMManager.sanitizeMove(move);
        if (!TMManager.isRegisteredMove(moveId)) {
            source.sendFailure(Component.literal("Unknown/unregistered TM move: " + move));
            return 0;
        }
        String rarity = TMManager.rarityForMove(moveId);
        source.sendSuccess(() -> Component.literal("TM - " + TMManager.prettyMove(moveId) + " is " + TMManager.prettyRarity(rarity) + "."), false);
        return 1;
    }

    private static int craft(CommandSourceStack source, String rarity) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            TMManager.CraftResult result = TMManager.craftRandom(player, rarity);
            player.sendSystemMessage(Component.literal(result.message()));
            return result.success() ? 1 : 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can craft TMs."));
            return 0;
        }
    }

    private static int craftMove(CommandSourceStack source, String move) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            TMManager.CraftResult result = TMManager.craftSpecific(player, move);
            player.sendSystemMessage(Component.literal(result.message()));
            return result.success() ? 1 : 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can craft TMs."));
            return 0;
        }
    }

    private static int list(CommandSourceStack source) {
        String moves = TMManager.registeredMoveIds().stream()
                .sorted(Comparator.naturalOrder())
                .limit(80)
                .map(id -> TMManager.prettyMove(id) + " [" + TMManager.prettyRarity(TMManager.rarityForMove(id)) + "]")
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal("Registered TMs (" + TMManager.registeredMoveIds().size() + "). First 80: " + moves), false);
        return 1;
    }
}
