package com.champutils.commands;

import com.champutils.specialspawn.SpecialWildSpawnConfig;
import com.champutils.debug.ChampDebugManager;
import com.champutils.specialspawn.SpecialWildSpawnManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public final class SpecialWildSpawnCommand {
    private SpecialWildSpawnCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("pity")
                    .then(literal("a").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        sendPity(ctx.getSource(), SpecialWildSpawnManager.pityView(player), "§6Legendary Spawn Pity", "a");
                        return 1;
                    }))
                    .then(literal("special").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        sendPity(ctx.getSource(), SpecialWildSpawnManager.pityView(player), "§6Legendary Spawn Pity", "a");
                        return 1;
                    }))
                    .then(literal("paradox").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        sendPity(ctx.getSource(), SpecialWildSpawnManager.paradoxPityView(player), "§5Paradox Spawn Pity", "paradox");
                        return 1;
                    }))
                    .then(literal("ultrabeast").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        sendPity(ctx.getSource(), SpecialWildSpawnManager.ultraBeastPityView(player), "§dUltra Beast Spawn Pity", "ultra beast");
                        return 1;
                    }))
                    .then(literal("ultra_beast").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        sendPity(ctx.getSource(), SpecialWildSpawnManager.ultraBeastPityView(player), "§dUltra Beast Spawn Pity", "ultra beast");
                        return 1;
                    }))
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        sendPity(ctx.getSource(), SpecialWildSpawnManager.pityView(player), "§6Legendary Spawn Pity", "a");
                        return 1;
                    }));

            dispatcher.register(literal("specialspawns")
                    .requires(source -> source.hasPermission(4))
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
                    }))
                    .then(literal("forceparadox").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        SpecialWildSpawnManager.ForceSpawnResult result = SpecialWildSpawnManager.forceParadoxSpawnForResult(player);
                        ctx.getSource().sendSuccess(() -> Component.literal(result.message), false);
                        return result.success ? 1 : 0;
                    }))
                    .then(literal("forceultrabeast").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        SpecialWildSpawnManager.ForceSpawnResult result = SpecialWildSpawnManager.forceUltraBeastSpawnForResult(player);
                        ctx.getSource().sendSuccess(() -> Component.literal(result.message), false);
                        return result.success ? 1 : 0;
                    }))
                    .then(literal("debug").executes(ctx -> {
                        if (ChampDebugManager.isEnabled(ChampDebugManager.Category.SPAWNS)) {
                            ChampDebugManager.disable("spawns");
                            ctx.getSource().sendSuccess(() -> Component.literal("Special spawn debug logs are now disabled. Use /champdebug spawns to enable them again."), false);
                        } else {
                            ChampDebugManager.setOnly(ChampDebugManager.Category.SPAWNS);
                            ctx.getSource().sendSuccess(() -> Component.literal("Special spawn debug logs are now enabled through /champdebug spawns."), false);
                        }
                        return 1;
                    }))
                    .then(literal("status").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        sendDebugStatus(ctx.getSource(), SpecialWildSpawnManager.debugStatus(player));
                        return 1;
                    }))
                    .then(literal("report").executes(ctx -> {
                        sendReport(ctx.getSource(), SpecialWildSpawnManager.report());
                        return 1;
                    })));
        });
    }

    private static void sendDebugStatus(CommandSourceStack source, SpecialWildSpawnManager.DebugStatus status) {
        source.sendSuccess(() -> Component.literal("§5Special Spawn Debug Status"), false);
        source.sendSuccess(() -> Component.literal("§7System enabled: " + (status.enabled() ? "§aYes" : "§cNo")), false);
        source.sendSuccess(() -> Component.literal("§7Paradox timer enabled: " + (status.paradoxEnabled() ? "§aYes" : "§cNo")), false);
        source.sendSuccess(() -> Component.literal("§7Debug logs: " + (ChampDebugManager.isEnabled(ChampDebugManager.Category.SPAWNS) ? "§aOn" : "§cOff") + " §8(/champdebug spawns)"), false);
        source.sendSuccess(() -> Component.literal("§7Pool type here: §f" + (status.islanderRoll() ? "Islander" : "Normal")), false);
        source.sendSuccess(() -> Component.literal("§7Eligible here: " + (status.eligibleHere() ? "§aYes" : "§cNo") + " §8(" + status.eligibilityMessage() + ")"), false);
        source.sendSuccess(() -> Component.literal("§7Paradox pool size: §f" + status.paradoxPoolSize()), false);
        source.sendSuccess(() -> Component.literal("§7Tracked special Pokémon: §f" + status.trackedSpecials() + " / " + status.trackedLimit()), false);
        source.sendSuccess(() -> Component.literal("§7Next paradox check: §f~" + status.nextParadoxCheckSeconds() + "s"), false);
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "§7Paradox chance/check: §f%.3f%%", status.paradoxChancePerCheck() * 100.0D)), false);
        source.sendSuccess(() -> Component.literal("§7Last paradox spawn: §f" + status.lastParadoxSpawnAgo()), false);
    }

    private static void sendReport(CommandSourceStack source, SpecialWildSpawnManager.SpawnPoolReport report) {
        source.sendSuccess(() -> Component.literal("§6Special Spawn Pool Report"), false);
        source.sendSuccess(() -> Component.literal("§7Legendary/Mythical pool: §f" + report.legendaryCount() + " species"), false);
        source.sendSuccess(() -> Component.literal("§7Paradox pool: §f" + report.paradoxCount() + " species"), false);
        source.sendSuccess(() -> Component.literal("§7Ultra Beast pool: §f" + report.ultraBeastCount() + " species"), false);
        source.sendSuccess(() -> Component.literal("§7Legendary duplicates: §f" + noneOrJoin(report.legendaryDuplicates())), false);
        source.sendSuccess(() -> Component.literal("§7Paradox duplicates: §f" + noneOrJoin(report.paradoxDuplicates())), false);
        source.sendSuccess(() -> Component.literal("§7Ultra Beast duplicates: §f" + noneOrJoin(report.ultraBeastDuplicates())), false);
        source.sendSuccess(() -> Component.literal("§7Recent Legendary/Mythical rolling 5: §f" + noneOrJoin(report.recentLegendary())), false);
        source.sendSuccess(() -> Component.literal("§7Recent Paradox rolling 5: §f" + noneOrJoin(report.recentParadox())), false);
        source.sendSuccess(() -> Component.literal("§7Recent Ultra Beast rolling 5: §f" + noneOrJoin(report.recentUltraBeast())), false);
    }

    private static String noneOrJoin(java.util.List<String> values) {
        if (values == null || values.isEmpty()) return "None";
        return String.join(", ", values);
    }

    private static void sendPity(CommandSourceStack source, SpecialWildSpawnManager.PityView view, String header, String label) {
        String pool = view.islanderRoll() ? "Islander" : "Normal";
        source.sendSuccess(() -> Component.literal(header), false);
        source.sendSuccess(() -> Component.literal("§7Pool: §f" + pool), false);
        source.sendSuccess(() -> Component.literal("§7Eligible: " + (view.eligible() ? "§aYes" : "§cNo") + " §8(" + view.eligibilityMessage() + ")"), false);
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "§7Global roll chance/check: §f%.3f%%", view.globalChancePerCheck() * 100.0D)), false);
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "§7Your pity priority: §f+%.1f%% §8(%d missed eligible roll%s)", view.pityPercent() * 100.0D, view.missedEligibleRolls(), view.missedEligibleRolls() == 1 ? "" : "s")), false);
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "§7Your estimated chance/check: §f%.3f%% §8(weight x%.2f)", view.estimatedPersonalChancePerCheck() * 100.0D, view.priorityWeight())), false);
        source.sendSuccess(() -> Component.literal("§7Last " + label + " spawn in this pool: §f" + view.lastSpecialSpawnAgo()), false);
    }
}
