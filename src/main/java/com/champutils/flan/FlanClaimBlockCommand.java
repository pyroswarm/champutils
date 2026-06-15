package com.champutils.flan;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class FlanClaimBlockCommand {

    private FlanClaimBlockCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("flanblocks")
                        .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                        .executes(ctx -> info(ctx.getSource()))
                        .then(literal("info")
                                .executes(ctx -> info(ctx.getSource())))
                        .then(literal("reload")
                                .executes(ctx -> reload(ctx.getSource())))
                        .then(literal("set")
                                .then(literal("enabled")
                                        .then(argument("enabled", BoolArgumentType.bool())
                                                .executes(ctx -> setEnabled(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled")))))
                                .then(literal("reward")
                                        .then(argument("claimBlocks", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setReward(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "claimBlocks")))))
                                .then(literal("intervalMinutes")
                                        .then(argument("minutes", IntegerArgumentType.integer(1))
                                                .executes(ctx -> setInterval(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "minutes")))))
                                .then(literal("message")
                                        .then(argument("message", StringArgumentType.greedyString())
                                                .executes(ctx -> setMessage(ctx.getSource(), StringArgumentType.getString(ctx, "message"))))))
                        .then(literal("grant")
                                .then(argument("player", EntityArgument.player())
                                        .then(argument("claimBlocks", IntegerArgumentType.integer(1))
                                                .executes(ctx -> grant(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "claimBlocks"))))))
        ));
    }

    private static int info(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Flan online claim block rewards").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Enabled: " + FlanOnlineClaimBlockConfig.enabled()).withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("Reward: " + FlanOnlineClaimBlockConfig.rewardClaimBlocks() + " claim blocks every " + FlanOnlineClaimBlockConfig.intervalMinutes() + " minutes").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("Message: " + FlanOnlineClaimBlockConfig.rewardMessage()).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("Flan loaded: " + FlanClaimBlockCompat.isFlanLoaded()).withStyle(FlanClaimBlockCompat.isFlanLoaded() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        FlanOnlineClaimBlockConfig.load();
        FlanOnlineClaimBlockManager.load();
        source.sendSuccess(() -> Component.literal("Reloaded flan_claim_blocks.json and flan claim block reward state.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setEnabled(CommandSourceStack source, boolean enabled) {
        FlanOnlineClaimBlockConfig.setEnabled(enabled);
        source.sendSuccess(() -> Component.literal("Flan online claim block rewards enabled = " + enabled).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setReward(CommandSourceStack source, int amount) {
        FlanOnlineClaimBlockConfig.setRewardClaimBlocks(amount);
        source.sendSuccess(() -> Component.literal("Set Flan online reward to " + amount + " claim blocks.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setInterval(CommandSourceStack source, int minutes) {
        FlanOnlineClaimBlockConfig.setIntervalMinutes(minutes);
        source.sendSuccess(() -> Component.literal("Set Flan online reward interval to " + minutes + " minutes.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setMessage(CommandSourceStack source, String message) {
        FlanOnlineClaimBlockConfig.setRewardMessage(message);
        source.sendSuccess(() -> Component.literal("Set Flan online reward message.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int grant(CommandSourceStack source, ServerPlayer player, int amount) {
        FlanClaimBlockCompat.Result result = FlanClaimBlockCompat.addClaimBlocks(player, amount);
        if (!result.success) {
            source.sendFailure(Component.literal(result.message == null ? "Could not add Flan claim blocks." : result.message));
            return 0;
        }
        player.sendSystemMessage(Component.literal("For playing for 1+ hours you have earned " + amount + " claim blocks!").withStyle(ChatFormatting.GOLD));
        source.sendSuccess(() -> Component.literal("Granted " + amount + " Flan claim blocks to " + player.getGameProfile().getName() + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
