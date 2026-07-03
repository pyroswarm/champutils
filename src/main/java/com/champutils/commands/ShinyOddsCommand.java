package com.champutils.commands;

import com.champutils.buff.BuffContext;
import com.champutils.buff.BuffManager;
import com.champutils.buff.BuffType;
import com.champutils.dex.CatchStreakManager;
import com.champutils.dex.TrueCaughtDexManager;
import com.champutils.profession.ProfessionTrinketManager;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ShinyOddsCommand {
    private ShinyOddsCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(literal("shinyodds")
                .executes(ctx -> {
                    show(ctx.getSource().getPlayerOrException(), null);
                    return 1;
                })
                .then(argument("species", StringArgumentType.word())
                        .executes(ctx -> {
                            show(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "species"));
                            return 1;
                        }))));
    }

    private static void show(ServerPlayer player, String rawSpecies) {
        CatchStreakManager.load();

        CatchStreakManager.CatchStreak streak = CatchStreakManager.getActiveStreak(player);
        String species = rawSpecies == null || rawSpecies.isBlank()
                ? (streak != null && streak.species != null && !streak.species.isBlank() ? streak.species : "pikachu")
                : rawSpecies;
        species = TrueCaughtDexManager.normalizeSpecies(species);

        Pokemon previewPokemon = createPreviewPokemon(species);
        if (previewPokemon == null) {
            player.sendSystemMessage(Component.literal("Unknown Pokémon species: " + species).withStyle(ChatFormatting.RED));
            return;
        }

        double base = safeChance(CatchStreakManager.CONFIG.baseShinyChance, CatchStreakManager.BASE_SHINY_CHANCE);
        double charmExtra = ProfessionTrinketManager.shinyCharmExtraChance(player);
        BuffContext context = BuffContext.trueWildCatch(player, previewPokemon);
        double buffMultiplier = BuffManager.getTotalBuff(context, BuffType.SHINY_CHANCE);

        // Shiny buffs stack additively from the original base chance. Example:
        // two +1% shiny buffs = base * (0.01 + 0.01), not base * 1.01 * 1.01.
        double buffExtra = additiveBuffExtra(base, buffMultiplier);
        double currentAnyCatch = clampChance(base + charmExtra + buffExtra);

        double streakExtra = CatchStreakManager.getCatchStreakExtraChance(player, species);
        double catchStreakOdds = clampChance(currentAnyCatch + streakExtra);

        player.sendSystemMessage(Component.literal("Current shiny odds for any wild catch: ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(formatOdds(currentAnyCatch)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" (" + formatPercent(currentAnyCatch) + ")").withStyle(ChatFormatting.GRAY)));

        String streakLine = "none";
        ChatFormatting streakColor = ChatFormatting.WHITE;
        if (streak != null && streak.species != null && !streak.species.isBlank()) {
            boolean matches = species.equals(streak.species);
            if (matches) {
                streakLine = pretty(streak.species) + " " + streak.count + "x: " + formatOdds(catchStreakOdds)
                        + " (" + formatPercent(catchStreakOdds) + ", streak adds +" + formatPercent(streakExtra) + ")";
                streakColor = streakExtra > 0.0D ? ChatFormatting.AQUA : ChatFormatting.WHITE;
            } else {
                streakLine = pretty(streak.species) + " " + streak.count + "x does not apply to " + pretty(species);
            }
        }
        player.sendSystemMessage(Component.literal("Catch streak shiny odds: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(streakLine).withStyle(streakColor)));

        player.sendSystemMessage(Component.literal("Base shiny odds: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(formatOdds(base) + " (" + formatPercent(base) + ")").withStyle(ChatFormatting.WHITE)));

        player.sendSystemMessage(Component.literal("Shiny Charm: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(charmExtra > 0.0D ? "+" + formatPercent(charmExtra) : "none").withStyle(charmExtra > 0.0D ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE)));

        player.sendSystemMessage(Component.literal("Active shiny buffs: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(buffMultiplier > 0.0D
                        ? "+" + BuffManager.percent(buffMultiplier) + " of base = +" + formatPercent(buffExtra) + " (additive, not compounded)"
                        : "none").withStyle(buffMultiplier > 0.0D ? ChatFormatting.GREEN : ChatFormatting.WHITE)));

        List<String> breakdown = BuffManager.debugBreakdown(context, BuffType.SHINY_CHANCE);
        for (String line : breakdown) {
            player.sendSystemMessage(Component.literal("  - " + line).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static Pokemon createPreviewPokemon(String species) {
        String clean = species == null || species.isBlank() ? "pikachu" : species;
        for (String candidate : List.of(clean, "cobblemon:" + clean, "pikachu", "cobblemon:pikachu")) {
            try {
                return PokemonProperties.Companion.parse("species=\"" + candidate + "\" level=1").create();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static double additiveBuffExtra(double baseChance, double multiplierBonus) {
        if (baseChance <= 0.0D || multiplierBonus <= 0.0D) return 0.0D;
        return clampChance(baseChance * multiplierBonus);
    }

    private static double safeChance(double value, double fallback) {
        if (Double.isNaN(value) || value <= 0.0D) return fallback;
        return clampChance(value);
    }

    private static double clampChance(double value) {
        if (Double.isNaN(value)) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static String formatOdds(double chance) {
        if (chance <= 0.0D) return "disabled";
        double oneIn = 1.0D / chance;
        if (oneIn >= 100.0D) return "1 in " + Math.round(oneIn);
        return String.format(Locale.US, "1 in %.1f", oneIn);
    }

    private static String formatPercent(double chance) {
        return String.format(Locale.US, "%.5f%%", clampChance(chance) * 100.0D)
                .replaceAll("0+%$", "%")
                .replace(".%", "%");
    }

    private static String pretty(String species) {
        String normalized = TrueCaughtDexManager.normalizeSpecies(species).replace('_', ' ');
        StringBuilder builder = new StringBuilder();
        for (String word : normalized.split("\\s+")) {
            if (word.isBlank()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            if (word.length() > 1) builder.append(word.substring(1));
        }
        return builder.length() == 0 ? species : builder.toString();
    }
}
