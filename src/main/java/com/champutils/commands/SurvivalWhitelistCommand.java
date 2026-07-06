package com.champutils.commands;

import com.champutils.survival.SurvivalWhitelistConfig;
import com.champutils.survival.SurvivalWhitelistManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class SurvivalWhitelistCommand {
    private SurvivalWhitelistCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("survivalwhitelist")
                        .requires(source -> source.hasPermission(4))
                        .then(literal("on").executes(ctx -> { SurvivalWhitelistConfig.DATA.enabled = true; SurvivalWhitelistConfig.save(); ctx.getSource().sendSuccess(() -> Component.literal("Survival whitelist enabled.").withStyle(ChatFormatting.GREEN), true); return 1; }))
                        .then(literal("off").executes(ctx -> { SurvivalWhitelistConfig.DATA.enabled = false; SurvivalWhitelistConfig.save(); ctx.getSource().sendSuccess(() -> Component.literal("Survival whitelist disabled.").withStyle(ChatFormatting.YELLOW), true); return 1; }))
                        .then(literal("add").then(argument("player", StringArgumentType.word()).executes(ctx -> { String name = StringArgumentType.getString(ctx, "player"); SurvivalWhitelistManager.addName(name); ctx.getSource().sendSuccess(() -> Component.literal("Added " + name + " to the survival whitelist.").withStyle(ChatFormatting.GREEN), true); return 1; })))
                        .then(literal("remove").then(argument("player", StringArgumentType.word()).executes(ctx -> { String name = StringArgumentType.getString(ctx, "player"); boolean removed = SurvivalWhitelistManager.removeName(name); ctx.getSource().sendSuccess(() -> Component.literal((removed ? "Removed " : "Could not find ") + name + (removed ? " from" : " on") + " the survival whitelist.").withStyle(removed ? ChatFormatting.GREEN : ChatFormatting.RED), true); return removed ? 1 : 0; })))
                        .then(literal("reload").executes(ctx -> { SurvivalWhitelistConfig.load(); ctx.getSource().sendSuccess(() -> Component.literal("Survival whitelist reloaded.").withStyle(ChatFormatting.GREEN), false); return 1; }))
        ));
    }
}
