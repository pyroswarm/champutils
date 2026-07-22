package com.champutils.commands;

import com.champutils.account.AccountUpgradeManager;
import com.champutils.permissions.LuckPermsHook;
import com.champutils.wiki.PokemonWikiIndex;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class SpawnTrackCommand {
    public static final String PERMISSION = "champutils.command.spawntrack";
    private static final int MAX_RESULTS = 5;

    private SpawnTrackCommand() {}

    private static final SuggestionProvider<CommandSourceStack> POKEMON_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        for (String species : PokemonWikiIndex.speciesSuggestions()) {
            if (species.startsWith(remaining)) builder.suggest(species);
        }
        return builder.buildFuture();
    };

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("spawntrack")
                        .requires(SpawnTrackCommand::canUse)
                        .then(argument("pokemon", StringArgumentType.word())
                                .suggests(POKEMON_SUGGESTIONS)
                                .executes(ctx -> track(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "pokemon")
                                )))
                ));
    }

    private static boolean canUse(CommandSourceStack source) {
        if (source == null) return false;
        if (source.hasPermission(4)) return true;
        ServerPlayer player = source.getPlayer();
        return player != null && (AccountUpgradeManager.hasVipPlus(player)
                || LuckPermsHook.hasPermission(player, PERMISSION));
    }

    private static int track(ServerPlayer player, String requested) {
        String target = normalize(requested);
        if (target.isBlank() || !PokemonWikiIndex.knowsSpecies(target)) {
            player.sendSystemMessage(Component.literal("§cUnknown Pokémon: §f" + requested));
            return 0;
        }

        List<TrackedSpawn> matches = new ArrayList<>();
        for (ServerLevel level : player.server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity raw : level.getAllEntities()) {
                if (!(raw instanceof PokemonEntity entity) || !entity.isAlive()) continue;
                if (entity.getPokemon() == null || !entity.getPokemon().isWild()) continue;
                String species = normalize(entity.getPokemon().getSpecies().getName());
                if (!species.equals(target)) continue;
                double distance = level == player.serverLevel()
                        ? entity.distanceToSqr(player)
                        : Double.POSITIVE_INFINITY;
                matches.add(new TrackedSpawn(level, entity, distance));
            }
        }

        if (matches.isEmpty()) {
            player.sendSystemMessage(Component.literal("§eNo loaded wild " + PokemonWikiIndex.displayName(target) + " spawns were found."));
            return 0;
        }

        matches.sort(Comparator.comparingDouble(TrackedSpawn::distanceSquared));
        player.sendSystemMessage(Component.literal("§6Spawn Tracker §7- §f" + PokemonWikiIndex.displayName(target)));
        int shown = Math.min(MAX_RESULTS, matches.size());
        for (int i = 0; i < shown; i++) {
            TrackedSpawn match = matches.get(i);
            int x = match.entity().blockPosition().getX();
            int y = match.entity().blockPosition().getY();
            int z = match.entity().blockPosition().getZ();
            String dimension = PokemonWikiIndex.prettyId(match.level().dimension().location().toString());
            String distanceText = match.level() == player.serverLevel()
                    ? " §8(" + (int)Math.sqrt(match.distanceSquared()) + " blocks away)"
                    : " §8(other dimension)";
            player.sendSystemMessage(Component.literal("§e" + (i + 1) + ". §f" + x + ", " + y + ", " + z
                    + " §7in §f" + dimension + distanceText));
        }
        if (matches.size() > shown) {
            player.sendSystemMessage(Component.literal("§7Showing " + shown + " of " + matches.size() + " loaded matches."));
        }
        return shown;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String clean = value.trim().toLowerCase(Locale.ROOT);
        int colon = clean.indexOf(':');
        if (colon >= 0 && colon + 1 < clean.length()) clean = clean.substring(colon + 1);
        return clean.replace("_", "").replace("-", "").replace(" ", "");
    }

    private record TrackedSpawn(ServerLevel level, PokemonEntity entity, double distanceSquared) {}
}
