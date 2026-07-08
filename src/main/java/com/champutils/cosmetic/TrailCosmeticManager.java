package com.champutils.cosmetic;

import com.champutils.commerce.AccountCommerceRepository;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TrailCosmeticManager {
    public static final String COSMETIC_TYPE = "trail";

    private static final Map<String, TrailDef> TRAILS = new LinkedHashMap<>();
    private static final Map<UUID, Set<String>> UNLOCKED = new ConcurrentHashMap<>();
    private static final Map<UUID, String> SELECTED = new ConcurrentHashMap<>();
    private static final Set<UUID> LOADED = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> LOADING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();
    private static boolean registered = false;
    private static int tickCounter = 0;

    static {
        add("ember", "Ember Trail", "Warm flame sparks while you move.", Items.BLAZE_POWDER, ParticleTypes.FLAME);
        add("aqua", "Aqua Trail", "Clean water splashes at your heels.", Items.HEART_OF_THE_SEA, ParticleTypes.SPLASH);
        add("volt", "Volt Trail", "Sharp crit sparks with an electric feel.", Items.LIGHTNING_ROD, ParticleTypes.CRIT);
        add("starlight", "Starlight Trail", "Bright end-rod shimmer.", Items.NETHER_STAR, ParticleTypes.END_ROD);
        add("shadow", "Shadow Trail", "Soft smoke wisps behind you.", Items.ECHO_SHARD, ParticleTypes.SMOKE);
        add("blossom", "Blossom Trail", "Green flourish particles as you run.", Items.CHERRY_LEAVES, ParticleTypes.HAPPY_VILLAGER);
        add("frost", "Frost Trail", "Cold snowflake sparkle.", Items.POWDER_SNOW_BUCKET, ParticleTypes.SNOWFLAKE);
    }

    private TrailCosmeticManager() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> loadAsync(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LAST_POS.remove(handler.player.getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter < 5) return;
            tickCounter = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tick(player);
            }
        });
    }

    public static void ensureSchemaAsync() {
        com.champutils.commerce.AccountCommerceRepository.ensureSchemaAsync();
    }

    public static List<TrailDef> trails() {
        return Collections.unmodifiableList(new ArrayList<>(TRAILS.values()));
    }

    public static TrailDef get(String id) {
        return TRAILS.get(normalize(id));
    }

    public static boolean isValid(String id) {
        return get(id) != null;
    }

    public static void loadAsync(ServerPlayer player) {
        if (player == null || !com.champutils.database.DatabaseManager.isEnabled()) return;
        UUID uuid = player.getUUID();
        if (LOADED.contains(uuid) || !LOADING.add(uuid)) return;
        AccountCommerceRepository.loadCosmeticsAsync(uuid, COSMETIC_TYPE)
                .thenCombine(AccountCommerceRepository.loadSelectedCosmeticAsync(uuid, COSMETIC_TYPE), Loaded::new)
                .thenAccept(loaded -> player.server.execute(() -> {
                    UNLOCKED.put(uuid, new LinkedHashSet<>(loaded.unlocked()));
                    SELECTED.put(uuid, loaded.selected() == null ? "" : loaded.selected());
                    LOADED.add(uuid);
                    LOADING.remove(uuid);
                }))
                .exceptionally(error -> {
                    LOADING.remove(uuid);
                    return null;
                });
    }

    public static Set<String> unlocked(ServerPlayer player) {
        if (player == null) return Set.of();
        loadAsync(player);
        return UNLOCKED.getOrDefault(player.getUUID(), Set.of());
    }

    public static boolean owns(ServerPlayer player, String id) {
        return player != null && unlocked(player).contains(normalize(id));
    }

    public static String selected(ServerPlayer player) {
        if (player == null) return "";
        loadAsync(player);
        String selected = SELECTED.getOrDefault(player.getUUID(), "");
        return owns(player, selected) ? selected : "";
    }

    public static void select(ServerPlayer player, String id) {
        if (player == null) return;
        String normalized = normalize(id);
        if (normalized.isBlank() || normalized.equals("none") || normalized.equals("off")) {
            SELECTED.put(player.getUUID(), "");
            AccountCommerceRepository.selectCosmeticAsync(player.getUUID(), COSMETIC_TYPE, "");
            player.sendSystemMessage(Component.literal("Trail disabled.").withStyle(ChatFormatting.GRAY));
            return;
        }
        TrailDef def = get(normalized);
        if (def == null) {
            player.sendSystemMessage(Component.literal("Unknown trail.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!owns(player, normalized)) {
            player.sendSystemMessage(Component.literal("You have not unlocked " + def.displayName() + ".").withStyle(ChatFormatting.RED));
            return;
        }
        SELECTED.put(player.getUUID(), normalized);
        AccountCommerceRepository.selectCosmeticAsync(player.getUUID(), COSMETIC_TYPE, normalized);
        player.sendSystemMessage(Component.literal("Equipped " + def.displayName() + ".").withStyle(ChatFormatting.GREEN));
    }

    public static void addUnlocked(UUID accountUuid, String id) {
        if (accountUuid == null) return;
        String normalized = normalize(id);
        if (!isValid(normalized)) return;
        UNLOCKED.computeIfAbsent(accountUuid, ignored -> ConcurrentHashMap.newKeySet()).add(normalized);
        LOADED.add(accountUuid);
    }

    public static void invalidate(UUID accountUuid) {
        if (accountUuid == null) return;
        LOADED.remove(accountUuid);
        LOADING.remove(accountUuid);
        UNLOCKED.remove(accountUuid);
        SELECTED.remove(accountUuid);
        LAST_POS.remove(accountUuid);
    }

    private static void tick(ServerPlayer player) {
        if (player == null || player.isSpectator()) return;
        String selected = selected(player);
        if (selected.isBlank()) return;
        TrailDef def = get(selected);
        if (def == null) return;

        UUID uuid = player.getUUID();
        Vec3 now = player.position();
        Vec3 previous = LAST_POS.put(uuid, now);
        if (previous == null || previous.distanceToSqr(now) < 0.003D) return;

        player.serverLevel().sendParticles(
                def.particle(),
                player.getX(),
                player.getY() + 0.08D,
                player.getZ(),
                4,
                0.22D,
                0.05D,
                0.22D,
                0.01D
        );
    }

    private static void add(String id, String displayName, String description, Item icon, ParticleOptions particle) {
        TRAILS.put(id, new TrailDef(id, displayName, description, icon, particle));
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT).replace("trail_", "").replace('-', '_');
    }

    private record Loaded(Set<String> unlocked, String selected) {}

    public record TrailDef(String id, String displayName, String description, Item icon, ParticleOptions particle) {}
}
