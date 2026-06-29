package com.champutils.commands;

import com.champutils.specialspawn.SpecialWildSpawnConfig;
import com.champutils.specialspawn.SpecialWildSpawnManager;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public final class SpecialWildSpawnCommand {
    private SpecialWildSpawnCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("pity")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        SpecialWildSpawnManager.PityView view = SpecialWildSpawnManager.pityView(player);
                        String pool = view.islanderRoll() ? "Islander" : "Normal";
                        ctx.getSource().sendSuccess(() -> Component.literal("§dSpecial Spawn Pity"), false);
                        ctx.getSource().sendSuccess(() -> Component.literal("§7Pool: §f" + pool), false);
                        ctx.getSource().sendSuccess(() -> Component.literal("§7Eligible: " + (view.eligible() ? "§aYes" : "§cNo") + " §8(" + view.eligibilityMessage() + ")"), false);
                        ctx.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "§7Global roll chance/check: §f%.3f%%", view.globalChancePerCheck() * 100.0D)), false);
                        ctx.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "§7Your pity priority: §f+%.1f%% §8(%d missed eligible roll%s)", view.pityPercent() * 100.0D, view.missedEligibleRolls(), view.missedEligibleRolls() == 1 ? "" : "s")), false);
                        ctx.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "§7Your estimated chance/check: §f%.3f%% §8(weight x%.2f)", view.estimatedPersonalChancePerCheck() * 100.0D, view.priorityWeight())), false);
                        ctx.getSource().sendSuccess(() -> Component.literal("§7Last special spawn in this pool: §f" + view.lastSpecialSpawnAgo()), false);
                        return 1;
                    }));

            dispatcher.register(literal("specialspawns")
                    .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.staff"))
                    .then(literal("reload").executes(ctx -> {
                        SpecialWildSpawnConfig.load();
                        ctx.getSource().sendSuccess(() -> Component.literal("Reloaded special wild spawn config."), false);
                        return 1;
                    }))
                    .then(literal("save").executes(ctx -> {
                        SpecialWildSpawnConfig.save();
                        ctx.getSource().sendSuccess(() -> Component.literal("Saved special wild spawn config."), false);
                        return 1;
                    }))
                    .then(literal("force").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        SpecialWildSpawnManager.ForceSpawnResult result = SpecialWildSpawnManager.forceSpawnForResult(player);
                        ctx.getSource().sendSuccess(() -> Component.literal(result.message), false);
                        return result.success ? 1 : 0;
                    })));
        });
    }

}
