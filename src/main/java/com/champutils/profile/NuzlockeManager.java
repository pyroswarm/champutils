package com.champutils.profile;

import com.champutils.database.DatabaseManager;
import com.champutils.hunt.PokemonHuntReflection;
import com.champutils.util.CobblemonEventReflection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

/**
 * Server-side Nuzlocke rules:
 * - Ironman restrictions while active.
 * - No PvP.
 * - Catching is unlimited by area/chunk.
 * - A Nuzlocke profile may only keep one living/caught copy of each species.
 * - Duplicate species catches are moved into the SQL Graveyard and removed from active Cobblemon storage when possible.
 * - Fainted owned Pokémon are marked dead in SQL Graveyard.
 * - Graveyard access is locked while the profile is an active Nuzlocke and unlocks after completion or conversion to Normal.
 * - Champion completion converts the profile to Normal and persists permanent rewards/title flags.
 */
public final class NuzlockeManager {
    private static boolean registered = false;

    private NuzlockeManager() {}

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure nuzlocke schema", connection -> {
            try (Statement s = connection.createStatement()) {
                s.executeUpdate("create table if not exists profile_nuzlocke_species (profile_id uuid not null references player_profiles(id) on delete cascade, species text not null, first_pokemon_uuid uuid, first_caught_at timestamptz not null default now(), primary key(profile_id, species))");
                s.executeUpdate("create table if not exists profile_nuzlocke_graveyard (profile_id uuid not null references player_profiles(id) on delete cascade, pokemon_uuid uuid not null, species text, entered_at timestamptz not null default now(), reason text not null default 'unknown', source text not null default 'nuzlocke', primary key(profile_id, pokemon_uuid))");
                s.executeUpdate("create table if not exists profile_nuzlocke_completion_rewards (profile_id uuid primary key references player_profiles(id) on delete cascade, completed_at timestamptz not null default now(), champion_id text not null default 'champion', title_key text not null default 'nuzlocke_champion', shiny_bonus numeric(8,4) not null default 0.0000, profession_xp_multiplier numeric(8,4) not null default 1.0000, rewards jsonb not null default '{}'::jsonb)");
            }
        });
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        registerCaptureListener();
        registerFaintListener();
    }

    public static String completeActiveRun(ServerPlayer player, String championId) {
        if (player == null) return "No player.";
        if (!PlayerProfileManager.isNuzlocke(player)) return "Only active Nuzlocke profiles can complete a Nuzlocke run.";
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        String champ = championId == null || championId.isBlank() ? "champion" : championId.trim().toLowerCase(Locale.ROOT);
        try (var ps = DatabaseManager.getConnection().prepareStatement("insert into profile_nuzlocke_completion_rewards (profile_id, champion_id, shiny_bonus, profession_xp_multiplier, rewards) values (?, ?, 0.0005, 1.0500, jsonb_build_object('completed_by', ?, 'reward_note', 'Nuzlocke Champion')) on conflict (profile_id) do nothing")) {
            ps.setObject(1, profileId);
            ps.setString(2, champ);
            ps.setString(3, player.getGameProfile().getName());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            return "Could not save Nuzlocke completion rewards.";
        }
        String converted = PlayerProfileManager.convertActiveToNormalBlocking(player);
        return "Nuzlocke complete! Rewards saved: title=nuzlocke_champion, shiny bonus=+0.05%, profession XP=+5%. " + converted;
    }

    public static boolean hasCompletedReward(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled()) return false;
        try (var ps = DatabaseManager.getConnection().prepareStatement("select 1 from profile_nuzlocke_completion_rewards where profile_id = ?")) {
            ps.setObject(1, PlayerProfileManager.activeProfileId(player));
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (Exception ignored) { return false; }
    }

    private static void registerCaptureListener() {
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Object observable = findObservable(eventsClass, "capture", "caught", "catch");
            if (observable == null) return;
            CobblemonEventReflection.subscribe(observable, event -> {
                try {
                    ServerPlayer player = PokemonHuntReflection.extractPlayer(event);
                    Object pokemon = PokemonHuntReflection.extractPokemon(event);
                    if (player == null || pokemon == null || !PlayerProfileManager.isNuzlocke(player)) return;
                    UUID profileId = PlayerProfileManager.activeProfileId(player);
                    String species = PokemonHuntReflection.speciesId(pokemon);
                    if (species == null || species.isBlank()) return;

                    if (speciesAlreadyKept(profileId, species)) {
                        recordGraveyard(profileId, pokemon, species, "duplicate_species_catch");
                        boolean removed = removeFromCurrentStore(pokemon);
                        player.sendSystemMessage(Component.literal(
                                removed
                                        ? "Nuzlocke: duplicate " + species + " caught. It was moved to your locked Graveyard."
                                        : "Nuzlocke: duplicate " + species + " caught. It was marked for the locked Graveyard; remove it from active storage if it still appears."
                        ).withStyle(ChatFormatting.RED));
                        return;
                    }

                    recordSpecies(profileId, species, pokemon);
                    player.sendSystemMessage(Component.literal("Nuzlocke: " + species + " registered. You can never keep another one on this run.").withStyle(ChatFormatting.GOLD));
                } catch (Throwable t) { t.printStackTrace(); }
            });
        } catch (Throwable ignored) {}
    }

    private static void registerFaintListener() {
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Object observable = findObservable(eventsClass, "faint");
            if (observable == null) return;
            CobblemonEventReflection.subscribe(observable, event -> {
                try {
                    Object killed = firstValue(event, "killed", "getKilled", "pokemon", "getPokemon");
                    Object pokemon = firstValue(killed, "effectedPokemon", "getEffectedPokemon", "pokemon", "getPokemon");
                    ServerPlayer owner = ownerOfPokemon(pokemon);
                    if (owner == null || !PlayerProfileManager.isNuzlocke(owner)) return;
                    recordDeath(PlayerProfileManager.activeProfileId(owner), pokemon, "battle_faint");
                    owner.sendSystemMessage(Component.literal("Nuzlocke: a Pokémon fainted and is now dead. Box or release it permanently.").withStyle(ChatFormatting.RED));
                } catch (Throwable t) { t.printStackTrace(); }
            });
        } catch (Throwable ignored) {}
    }

    private static boolean speciesAlreadyKept(UUID profileId, String species) throws Exception {
        try (var ps = DatabaseManager.getConnection().prepareStatement("select 1 from profile_nuzlocke_species where profile_id = ? and species = ?")) {
            ps.setObject(1, profileId);
            ps.setString(2, species);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private static void recordSpecies(UUID profileId, String species, Object pokemon) throws Exception {
        try (var ps = DatabaseManager.getConnection().prepareStatement("insert into profile_nuzlocke_species (profile_id, species, first_pokemon_uuid) values (?, ?, ?) on conflict do nothing")) {
            ps.setObject(1, profileId);
            ps.setString(2, species);
            ps.setObject(3, pokemonUuid(pokemon));
            ps.executeUpdate();
        }
    }

    private static void recordDeath(UUID profileId, Object pokemon, String reason) throws Exception {
        String species = PokemonHuntReflection.speciesId(pokemon);
        recordGraveyard(profileId, pokemon, species, reason);
    }

    private static void recordGraveyard(UUID profileId, Object pokemon, String species, String reason) throws Exception {
        UUID uuid = pokemonUuid(pokemon);
        if (uuid == null) uuid = UUID.randomUUID();
        try (var ps = DatabaseManager.getConnection().prepareStatement("insert into profile_nuzlocke_graveyard (profile_id, pokemon_uuid, species, reason) values (?, ?, ?, ?) on conflict (profile_id, pokemon_uuid) do update set reason = excluded.reason, entered_at = now()")) {
            ps.setObject(1, profileId);
            ps.setObject(2, uuid);
            ps.setString(3, species);
            ps.setString(4, reason);
            ps.executeUpdate();
        }
    }

    public static boolean canAccessGraveyard(ServerPlayer player) {
        if (player == null) return false;
        if (!DatabaseManager.isEnabled()) return false;
        // The Graveyard is intentionally locked during active Nuzlocke play.
        // It unlocks after the run has completed, or after the profile is converted away from NUZLOCKE.
        if (!PlayerProfileManager.isNuzlocke(player)) return true;
        return hasCompletedReward(player);
    }

    private static boolean removeFromCurrentStore(Object pokemon) {
        try {
            Object coordinates = firstValue(pokemon, "storeCoordinates", "getStoreCoordinates");
            if (coordinates != null) {
                Object resolved = firstValue(coordinates, "get", "getValue", "value");
                if (resolved != null) {
                    Object store = firstValue(resolved, "store", "getStore");
                    if (store != null) {
                        for (Method m : store.getClass().getMethods()) {
                            if (!m.getName().equals("remove") || m.getParameterCount() != 1) continue;
                            if (!m.getParameterTypes()[0].isAssignableFrom(pokemon.getClass())) continue;
                            m.setAccessible(true);
                            Object result = m.invoke(store, pokemon);
                            return !(result instanceof Boolean) || (Boolean) result;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static UUID pokemonUuid(Object pokemon) {
        Object value = firstValue(pokemon, "uuid", "getUuid", "getUUID", "id", "getId");
        if (value instanceof UUID u) return u;
        try { return value == null ? null : UUID.fromString(String.valueOf(value)); } catch (Exception ignored) { return null; }
    }

    private static ServerPlayer ownerOfPokemon(Object pokemon) {
        Object owner = firstValue(pokemon, "ownerPlayer", "getOwnerPlayer", "owner", "getOwner");
        return owner instanceof ServerPlayer p ? p : null;
    }

    private static Object findObservable(Class<?> eventsClass, String... needles) {
        for (Field f : eventsClass.getFields()) {
            String lower = f.getName().toLowerCase(Locale.ROOT);
            boolean match = false;
            for (String needle : needles) if (lower.contains(needle)) match = true;
            if (!match || lower.contains("pre") || lower.contains("attempt") || lower.contains("fail")) continue;
            try { Object value = f.get(null); if (value != null) return value; } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object firstValue(Object source, String... names) {
        if (source == null) return null;
        for (String name : names) {
            try { Field f = source.getClass().getDeclaredField(name); f.setAccessible(true); Object v = f.get(source); if (v != null) return v; } catch (Throwable ignored) {}
            try { Method m = source.getClass().getMethod(name); m.setAccessible(true); if (m.getParameterCount() == 0) { Object v = m.invoke(source); if (v != null) return v; } } catch (Throwable ignored) {}
        }
        return null;
    }
}
