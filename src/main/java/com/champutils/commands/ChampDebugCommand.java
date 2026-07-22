package com.champutils.commands;

import com.champutils.debug.ChampDebugManager;
import com.champutils.profession.ProfessionChunkManager;
import com.champutils.profession.ProfessionType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class ChampDebugCommand {
    private ChampDebugCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("champdebug")
                        .requires(source -> source.hasPermission(4))
                        .executes(context -> status(context.getSource()))
                        .then(Commands.literal("chunkchance")
                                .then(Commands.argument("profession", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (ProfessionType type : ProfessionType.values()) builder.suggest(type.name().toLowerCase(java.util.Locale.ROOT));
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("chunk", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    for (String chunk : com.champutils.profession.ProfessionChunkConfig.CONFIG.chunks.keySet()) builder.suggest(chunk.toLowerCase(java.util.Locale.ROOT));
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> chunkChance(context.getSource(),
                                                        StringArgumentType.getString(context, "profession"),
                                                        StringArgumentType.getString(context, "chunk"), null))
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 100))
                                                        .executes(context -> chunkChance(context.getSource(),
                                                                StringArgumentType.getString(context, "profession"),
                                                                StringArgumentType.getString(context, "chunk"),
                                                                IntegerArgumentType.getInteger(context, "level")))))))
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String suggestion : ChampDebugManager.suggestions()) builder.suggest(suggestion);
                                    return builder.buildFuture();
                                })
                                .executes(context -> apply(context.getSource(), StringArgumentType.getString(context, "mode"))))
        ));
    }

    private static int status(net.minecraft.commands.CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6ChampDebug§7: §f" + ChampDebugManager.status()), false);
        source.sendSuccess(() -> Component.literal("§7Usage: §f/champdebug off§7, §f/champdebug all§7, category names, or §f/champdebug chunkchance <profession> <chunk> [level]"), false);
        return 1;
    }

    private static int apply(net.minecraft.commands.CommandSourceStack source, String mode) {
        String raw = mode == null ? "" : mode.trim();
        if (raw.equalsIgnoreCase("off") || raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("false")) {
            ChampDebugManager.off();
            source.sendSuccess(() -> Component.literal("§6ChampDebug§7: §coff"), true);
            return 1;
        }
        if (raw.equalsIgnoreCase("all") || raw.equalsIgnoreCase("true")) {
            ChampDebugManager.all();
            source.sendSuccess(() -> Component.literal("§6ChampDebug§7: §aall debugging enabled"), true);
            return 1;
        }
        if (!ChampDebugManager.setOnly(raw)) {
            source.sendFailure(Component.literal("§cUnknown debug category: " + raw + ". Try /champdebug for the category list."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§6ChampDebug§7: §aenabled §f" + ChampDebugManager.status() + " §7only"), true);
        return 1;
    }
    private static int chunkChance(net.minecraft.commands.CommandSourceStack source, String professionRaw, String chunkRaw, Integer levelOverride) {
        net.minecraft.server.level.ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cThis debug calculation requires an in-game player so mastery and trinket bonuses can be read."));
            return 0;
        }

        ProfessionType profession;
        try {
            profession = ProfessionType.valueOf(professionRaw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cUnknown profession: " + professionRaw));
            return 0;
        }

        int level = levelOverride == null
                ? Math.max(1, com.champutils.profession.ProfessionManager.getBenefitLevel(player, profession))
                : levelOverride;
        ProfessionChunkManager.ChunkChance chance = ProfessionChunkManager.calculateChance(player, profession, chunkRaw, level, 1.0D);
        if (!chance.configured()) {
            source.sendFailure(Component.literal("§cNo configured chunk roll for " + profession.name() + " / " + chunkRaw + "."));
            return 0;
        }
        if (!chance.unlocked()) {
            source.sendSuccess(() -> Component.literal("§6Chunk Chance§7: §f" + profession.name() + " " + chance.chunk() + " §cLocked until level " + chance.unlockLevel() + " §7(test level " + level + ")"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.US,
                "§6Chunk Chance§7: §f%s %s §7Lv.%d = §a%.8f%% §8(roll compares random 0-100 directly to this percentage)",
                profession.name(), chance.chunk(), level, chance.effectiveChancePercent())), false);
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.US,
                "§7base scaled/capped: §f%.8f%% / %.8f%% §7activity: §f%.6fx §7event: §f%.6fx §7level bonus: §f+%.4f%%",
                chance.scaledBaseChancePercent(), chance.cappedBaseChancePercent(), chance.activityMultiplier(), chance.eventMultiplier(), chance.overallLevelBonus() * 100.0D)), false);
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.US,
                "§7mastery find: §f+%.4f%% §7rarity weight: §f%.4f §7weighted rarity: §f+%.4f%% §7Chunky Brick: §f+%.4f%% §7pre-trinket: §f%.8f%%",
                chance.masteryFindBonus() * 100.0D, chance.rarityWeight(), chance.masteryRarityBonus() * 100.0D,
                chance.trinketBonus() * 100.0D, chance.preTrinketChancePercent())), false);
        return 1;
    }

}
