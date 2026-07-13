package com.champutils.profile;

import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.database.DatabaseManager;
import com.champutils.hunt.PokemonHuntReflection;
import com.champutils.util.CobblemonEventReflection;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Server-side Nuzlocke death/species/graveyard rules. */
public final class NuzlockeManager {
    private static boolean registered = false;

    private NuzlockeManager() {}

    public record GraveyardEntry(UUID pokemonUuid, String species, String reason, Instant enteredAt, Instant claimedAt) {}

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure nuzlocke schema", connection -> {
            try (Statement s = connection.createStatement()) {
                s.executeUpdate("create table if not exists profile_nuzlocke_species (profile_id uuid not null references player_profiles(id) on delete cascade, species text not null, first_pokemon_uuid uuid, first_caught_at timestamptz not null default now(), primary key(profile_id, species))");
                s.executeUpdate("create table if not exists profile_nuzlocke_graveyard (profile_id uuid not null references player_profiles(id) on delete cascade, pokemon_uuid uuid not null, species text, entered_at timestamptz not null default now(), reason text not null default 'unknown', source text not null default 'nuzlocke', primary key(profile_id, pokemon_uuid))");
                s.executeUpdate("alter table profile_nuzlocke_graveyard add column if not exists pokemon_nbt_base64 text");
                s.executeUpdate("alter table profile_nuzlocke_graveyard add column if not exists claimed_at timestamptz");
                s.executeUpdate("alter table profile_nuzlocke_graveyard add column if not exists claimed_by uuid");
                s.executeUpdate("create index if not exists idx_profile_nuzlocke_graveyard_profile_unclaimed on profile_nuzlocke_graveyard(profile_id, claimed_at) where claimed_at is null");
                s.executeUpdate("create table if not exists profile_nuzlocke_completion_rewards (profile_id uuid primary key references player_profiles(id) on delete cascade, completed_at timestamptz not null default now(), champion_id text not null default 'champion', title_key text not null default 'nuzlocke_champion', shiny_bonus numeric(8,4) not null default 0.0000, profession_xp_multiplier numeric(8,4) not null default 1.0000, rewards jsonb not null default '{}'::jsonb)");
            }
        });
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        registerCaptureListener();
        registerFaintListener();
        registerReleaseListener();
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
        // Use the same Champion Umbra completion gate as every other profile mode.
        // This prevents the maintenance command from granting the account title early.
        ChallengeProfileTitleManager.handleChampionVictory(player);
        return "Nuzlocke completion record saved. The account title is awarded only after Champion Umbra and the full League are defeated on this profile.";
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
            Object calculatedObservable = findObservableExact(eventsClass, "POKE_BALL_CAPTURE_CALCULATED");
            if (calculatedObservable != null) {
                CobblemonEventReflection.subscribe(calculatedObservable, event -> {
                    try {
                        ServerPlayer player = playerFromCaptureEvent(event);
                        Object pokemon = pokemonFromCaptureEvent(event);
                        if (player == null || pokemon == null || !PlayerProfileManager.isNuzlocke(player)) return;
                        if (!captureResultSuccessful(event)) return;
                        UUID profileId = PlayerProfileManager.activeProfileId(player);
                        String species = PokemonHuntReflection.speciesId(pokemon);
                        if (species == null || species.isBlank()) return;
                        if (speciesAlreadyKept(profileId, species) || CobblemonProfileStorageBridge.activeCachedStoresHaveSpecies(profileId, species, pokemonUuid(pokemon))) {
                            forceFailedCapture(event);
                            player.sendSystemMessage(Component.literal("Nuzlocke: you have already owned " + species + ". Duplicate species catches are forbidden.").withStyle(ChatFormatting.RED));
                        }
                    } catch (Throwable t) { t.printStackTrace(); }
                });
            }

            Object capturedObservable = findObservableExact(eventsClass, "POKEMON_CAPTURED");
            if (capturedObservable == null) capturedObservable = findObservable(eventsClass, "captur", "caught", "catch");
            if (capturedObservable == null) return;
            CobblemonEventReflection.subscribe(capturedObservable, event -> {
                try {
                    ServerPlayer player = PokemonHuntReflection.extractPlayer(event);
                    if (player == null) player = playerFromCaptureEvent(event);
                    Object pokemon = PokemonHuntReflection.extractPokemon(event);
                    if (pokemon == null) pokemon = pokemonFromCaptureEvent(event);
                    if (player == null || pokemon == null || !PlayerProfileManager.isNuzlocke(player)) return;
                    UUID profileId = PlayerProfileManager.activeProfileId(player);
                    String species = PokemonHuntReflection.speciesId(pokemon);
                    if (species == null || species.isBlank()) return;
                    if (speciesAlreadyKept(profileId, species) || CobblemonProfileStorageBridge.activeCachedStoresHaveSpecies(profileId, species, pokemonUuid(pokemon))) {
                        recordGraveyard(profileId, player, pokemon, species, "duplicate_species_catch");
                        boolean removed = removeFromCurrentStore(pokemon);
                        try { CobblemonProfileStorageBridge.forceSaveActiveProfileStoresAsync(player); } catch (Throwable ignored) {}
                        player.sendSystemMessage(Component.literal(removed ? "Nuzlocke: duplicate " + species + " caught. It was moved to your locked Graveyard." : "Nuzlocke: duplicate " + species + " caught. It was marked for the locked Graveyard; remove it from active storage if it still appears.").withStyle(ChatFormatting.RED));
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
            // Use the exact Cobblemon faint observable first. A fuzzy reflection search can
            // accidentally miss the real battle faint event on some Cobblemon builds.
            CobblemonEvents.BATTLE_FAINTED.subscribe(event -> {
                try {
                    handleFaintEvent(event);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
            return;
        } catch (Throwable ignored) {
            // Fall back to reflection for compatibility with alternate Cobblemon mappings.
        }

        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Object observable = findObservableExact(eventsClass, "BATTLE_FAINTED");
            if (observable == null) observable = findObservable(eventsClass, "faint");
            if (observable == null) return;
            CobblemonEventReflection.subscribe(observable, event -> {
                try {
                    handleFaintEvent(event);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
        } catch (Throwable ignored) {}
    }


    private static void registerReleaseListener() {
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Object observable = findObservableExact(eventsClass, "POKEMON_RELEASED");
            if (observable == null) observable = findObservable(eventsClass, "release");
            if (observable == null) return;
            CobblemonEventReflection.subscribe(observable, event -> {
                try {
                    ServerPlayer player = PokemonHuntReflection.extractPlayer(event);
                    if (player == null) player = playerFromCaptureEvent(event);
                    Object pokemon = PokemonHuntReflection.extractPokemon(event);
                    if (pokemon == null) pokemon = pokemonFromCaptureEvent(event);
                    if (player == null || pokemon == null || !PlayerProfileManager.isNuzlocke(player)) return;
                    UUID profileId = PlayerProfileManager.activeProfileId(player);
                    if (profileId == null) return;

                    // A released Nuzlocke Pokémon is treated as dead/removed from the run.
                    // Record it before Cobblemon fully removes it so /graveyard can restore it after
                    // completion/conversion, and so releasing cannot bypass Nuzlocke consequences.
                    recordDeath(profileId, player, pokemon, "released");
                    try { CobblemonProfileStorageBridge.forceSaveActiveProfileStoresAsync(player); } catch (Throwable ignored) {}
                    player.sendSystemMessage(Component.literal("Nuzlocke: released Pokémon was added to your locked Graveyard.").withStyle(ChatFormatting.RED));
                } catch (Throwable t) { t.printStackTrace(); }
            });
        } catch (Throwable ignored) {}
    }

    private static void handleFaintEvent(Object event) throws Exception {
        Object killed = firstValue(event, "killed", "getKilled", "pokemon", "getPokemon");
        Object pokemon = battlePokemonToStoragePokemon(killed);
        ServerPlayer owner = ownerOfFaintedPokemon(event, killed, pokemon);
        if (owner == null || !PlayerProfileManager.isNuzlocke(owner)) return;
        UUID profileId = PlayerProfileManager.activeProfileId(owner);
        if (profileId == null) return;

        // Save to Graveyard before removing anything. This makes the operation deletion-safe.
        recordDeath(profileId, owner, pokemon, "battle_faint");

        ServerPlayer finalOwner = owner;
        Object finalPokemon = pokemon;
        UUID finalProfileId = profileId;
        UUID finalPokemonUuid = pokemonUuid(finalPokemon);
        owner.server.execute(() -> {
            boolean removed = false;
            try { removed = removeFromCurrentStore(finalPokemon); } catch (Throwable ignored) {}
            try {
                if (!removed) {
                    removed = CobblemonProfileStorageBridge.removePokemonFromCachedStores(finalProfileId, finalPokemonUuid, finalPokemon);
                }
            } catch (Throwable ignored) {}
            try { CobblemonProfileStorageBridge.forceSaveActiveProfileStoresAsync(finalOwner); } catch (Throwable ignored) {}
            try { CobblemonProfileStorageBridge.loadActiveProfileStores(finalOwner); } catch (Throwable ignored) {}
            String name = PokemonHuntReflection.speciesId(finalPokemon);
            if (name == null || name.isBlank()) name = "A Pokémon";
            finalOwner.sendSystemMessage(Component.literal(removed
                    ? "Nuzlocke: " + name + " fainted and was moved to your locked Graveyard. It has been removed from your party."
                    : "Nuzlocke: " + name + " fainted and was recorded in your locked Graveyard, but active-storage removal failed. Do not use it; contact staff.")
                    .withStyle(removed ? ChatFormatting.RED : ChatFormatting.DARK_RED));
        });
    }

    private static Object battlePokemonToStoragePokemon(Object battlePokemon) {
        Object pokemon = firstValue(battlePokemon,
                "originalPokemon", "getOriginalPokemon",
                "pokemon", "getPokemon",
                "effectedPokemon", "getEffectedPokemon");
        if (pokemon instanceof Pokemon) return pokemon;
        Object nested = firstValue(pokemon, "pokemon", "getPokemon");
        return nested instanceof Pokemon ? nested : pokemon;
    }

    public static List<GraveyardEntry> graveyardEntries(ServerPlayer player, boolean includeClaimed) {
        List<GraveyardEntry> out = new ArrayList<>();
        if (player == null || !DatabaseManager.isEnabled() || !PlayerProfileManager.hasActiveProfile(player)) return out;
        String sql = "select pokemon_uuid, species, reason, entered_at, claimed_at from profile_nuzlocke_graveyard where profile_id = ? " + (includeClaimed ? "" : "and claimed_at is null ") + "order by entered_at desc";
        try (var ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setObject(1, PlayerProfileManager.activeProfileId(player));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new GraveyardEntry((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant(), rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant()));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return out;
    }

    public static String claimGraveyard(ServerPlayer player) {
        if (player == null) return "No player.";
        if (!DatabaseManager.isEnabled()) return "Database is not enabled.";
        if (!PlayerProfileManager.hasActiveProfile(player)) return "You must select a profile first.";
        if (!canAccessGraveyard(player)) return "Your Graveyard is locked until your Nuzlocke is completed or converted to a Normal profile.";
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        int restored = 0;
        int failed = 0;
        try (var ps = DatabaseManager.getConnection().prepareStatement("select pokemon_uuid, species, pokemon_nbt_base64 from profile_nuzlocke_graveyard where profile_id = ? and claimed_at is null order by entered_at asc")) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID pokemonUuid = (UUID) rs.getObject(1);
                    String species = rs.getString(2);
                    String nbt = rs.getString(3);
                    Pokemon pokemon = restorePokemon(player, species, nbt);
                    if (pokemon == null) { failed++; continue; }
                    AuctionPokemonSerializer.DeliveryResult delivered = AuctionPokemonSerializer.deliverToPartyOrPc(player, pokemon);
                    if (delivered == AuctionPokemonSerializer.DeliveryResult.FAILED) { failed++; continue; }
                    if (markClaimed(profileId, pokemonUuid, player.getUUID())) restored++; else failed++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Could not claim Graveyard Pokémon. Nothing was intentionally deleted.";
        }
        try { CobblemonProfileStorageBridge.forceSaveActiveProfileStoresAsync(player); } catch (Throwable ignored) {}
        return "Graveyard claim complete. Restored " + restored + " Pokémon" + (failed > 0 ? " and skipped " + failed + " that could not be safely restored." : ".");
    }

    private static boolean markClaimed(UUID profileId, UUID pokemonUuid, UUID playerUuid) {
        try (var ps = DatabaseManager.getConnection().prepareStatement("update profile_nuzlocke_graveyard set claimed_at = now(), claimed_by = ? where profile_id = ? and pokemon_uuid = ? and claimed_at is null")) {
            ps.setObject(1, playerUuid);
            ps.setObject(2, profileId);
            ps.setObject(3, pokemonUuid);
            return ps.executeUpdate() == 1;
        } catch (Exception e) { e.printStackTrace(); return false; }
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

    private static void recordDeath(UUID profileId, ServerPlayer player, Object pokemon, String reason) throws Exception {
        recordGraveyard(profileId, player, pokemon, PokemonHuntReflection.speciesId(pokemon), reason);
    }

    private static void recordGraveyard(UUID profileId, ServerPlayer player, Object pokemon, String species, String reason) throws Exception {
        UUID uuid = pokemonUuid(pokemon);
        if (uuid == null) uuid = UUID.randomUUID();
        String nbt = savePokemonBase64(player, pokemon);
        try (var ps = DatabaseManager.getConnection().prepareStatement("insert into profile_nuzlocke_graveyard (profile_id, pokemon_uuid, species, reason, pokemon_nbt_base64) values (?, ?, ?, ?, ?) on conflict (profile_id, pokemon_uuid) do update set reason = excluded.reason, entered_at = now(), pokemon_nbt_base64 = coalesce(profile_nuzlocke_graveyard.pokemon_nbt_base64, excluded.pokemon_nbt_base64)")) {
            ps.setObject(1, profileId);
            ps.setObject(2, uuid);
            ps.setString(3, species);
            ps.setString(4, reason);
            ps.setString(5, nbt);
            ps.executeUpdate();
        }
    }

    public static boolean canAccessGraveyard(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled()) return false;
        if (!PlayerProfileManager.isNuzlocke(player)) return true;
        return hasCompletedReward(player);
    }

    private static boolean removeFromCurrentStore(Object pokemon) {
        if (pokemon == null) return false;
        try {
            if (pokemon instanceof Pokemon p) {
                var coordinates = p.getStoreCoordinates().get();
                if (coordinates != null) return coordinates.remove();
            }
        } catch (Throwable ignored) {}
        try {
            Object coordinatesObservable = firstValue(pokemon, "storeCoordinates", "getStoreCoordinates");
            Object coordinates = firstValue(coordinatesObservable, "get", "getValue", "value");
            if (coordinates != null) {
                for (Method m : coordinates.getClass().getMethods()) {
                    if (!m.getName().equals("remove") || m.getParameterCount() != 0) continue;
                    m.setAccessible(true);
                    Object result = m.invoke(coordinates);
                    return !(result instanceof Boolean) || (Boolean) result;
                }
                Object store = firstValue(coordinates, "store", "getStore");
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
        } catch (Throwable ignored) {}
        return false;
    }

    private static ServerPlayer ownerOfFaintedPokemon(Object event, Object killed, Object pokemon) {
        ServerPlayer owner = ownerOfPokemon(pokemon);
        if (owner != null) return owner;
        try {
            if (killed instanceof BattlePokemon bp && bp.actor instanceof PlayerBattleActor pba) return pba.getEntity();
        } catch (Throwable ignored) {}
        Object actor = firstValue(killed, "actor", "getActor");
        Object entity = firstValue(actor, "entity", "getEntity");
        if (entity instanceof ServerPlayer p) return p;
        Object battle = firstValue(event, "battle", "getBattle");
        Object actors = firstValue(battle, "actors", "getActors");
        if (actors instanceof Iterable<?> iterable) {
            UUID pokemonId = pokemonUuid(pokemon);
            for (Object a : iterable) {
                Object list = firstValue(a, "pokemonList", "getPokemonList");
                if (!(list instanceof Iterable<?> mons)) continue;
                for (Object mon : mons) {
                    Object original = firstValue(mon, "originalPokemon", "getOriginalPokemon", "effectedPokemon", "getEffectedPokemon");
                    if (pokemonId != null && pokemonId.equals(pokemonUuid(original))) {
                        Object e = firstValue(a, "entity", "getEntity");
                        if (e instanceof ServerPlayer p) return p;
                    }
                }
            }
        }
        return null;
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

    private static String savePokemonBase64(ServerPlayer player, Object pokemon) {
        if (!(pokemon instanceof Pokemon p) || player == null) return null;
        try {
            CompoundTag tag = p.saveToNBT(player.registryAccess(), new CompoundTag());
            if (tag == null || tag.isEmpty()) return null;
            return Base64.getEncoder().encodeToString(tag.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) { return null; }
    }

    private static Pokemon restorePokemon(ServerPlayer player, String species, String nbtBase64) {
        try {
            String safeSpecies = species == null || species.isBlank() ? "cobblemon:pikachu" : species;
            Pokemon pokemon = PokemonProperties.Companion.parse("species=\"" + safeSpecies + "\"").create();
            if (nbtBase64 == null || nbtBase64.isBlank()) return pokemon;
            CompoundTag tag = TagParser.parseTag(new String(Base64.getDecoder().decode(nbtBase64), StandardCharsets.UTF_8));
            loadPokemonNbt(player, pokemon, tag);
            return pokemon;
        } catch (Throwable t) { t.printStackTrace(); return null; }
    }

    private static void loadPokemonNbt(ServerPlayer player, Pokemon pokemon, CompoundTag tag) throws Exception {
        for (Method method : pokemon.getClass().getMethods()) {
            String name = method.getName();
            if (!name.equals("loadFromNBT") && !name.equals("loadFromNbt")) continue;
            method.setAccessible(true);
            Class<?>[] params = method.getParameterTypes();
            try {
                if (params.length == 2 && acceptsCompound(params[0]) && registryArgument(player, params[1]) != null) { method.invoke(pokemon, tag, registryArgument(player, params[1])); return; }
                if (params.length == 2 && acceptsCompound(params[1]) && registryArgument(player, params[0]) != null) { method.invoke(pokemon, registryArgument(player, params[0]), tag); return; }
                if (params.length == 1 && acceptsCompound(params[0])) { method.invoke(pokemon, tag); return; }
            } catch (Throwable ignored) {}
        }
        throw new IllegalStateException("Could not load Pokémon NBT.");
    }

    private static boolean acceptsCompound(Class<?> type) {
        return type.isAssignableFrom(CompoundTag.class) || CompoundTag.class.isAssignableFrom(type);
    }

    private static Object registryArgument(ServerPlayer player, Class<?> expectedType) {
        try { Object registryAccess = player.registryAccess(); if (registryAccess != null && expectedType.isAssignableFrom(registryAccess.getClass())) return registryAccess; } catch (Exception ignored) {}
        return null;
    }

    private static Object findObservableExact(Class<?> eventsClass, String exactName) {
        if (eventsClass == null || exactName == null) return null;
        try { Field field = eventsClass.getField(exactName); Object value = field.get(null); if (value != null) return value; } catch (Throwable ignored) {}
        return null;
    }

    private static ServerPlayer playerFromCaptureEvent(Object event) {
        Object player = firstValue(event, "player", "getPlayer", "thrower", "getThrower", "capturer", "getCapturer", "catcher", "getCatcher", "owner", "getOwner");
        return player instanceof ServerPlayer p ? p : null;
    }

    private static Object pokemonFromCaptureEvent(Object event) {
        Object pokemon = firstValue(event, "pokemon", "getPokemon", "pokemonEntity", "getPokemonEntity", "caught", "getCaught", "captured", "getCaptured");
        if (pokemon == null) return null;
        Object nested = firstValue(pokemon, "pokemon", "getPokemon");
        return nested == null ? pokemon : nested;
    }

    private static boolean captureResultSuccessful(Object event) {
        Object result = firstValue(event, "captureResult", "getCaptureResult");
        Object successful = firstValue(result, "isSuccessfulCapture", "getSuccessfulCapture", "getIsSuccessfulCapture");
        return successful instanceof Boolean b && b;
    }

    private static void forceFailedCapture(Object event) {
        CaptureContext failed = new CaptureContext(0, false, false);
        try { Method setter = event.getClass().getMethod("setCaptureResult", CaptureContext.class); setter.setAccessible(true); setter.invoke(event, failed); return; } catch (Throwable ignored) {}
        try { Field field = event.getClass().getDeclaredField("captureResult"); field.setAccessible(true); field.set(event, failed); } catch (Throwable ignored) {}
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
