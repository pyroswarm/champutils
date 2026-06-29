package com.champutils.profile;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime-only restricted-profile item ownership.
 *
 * This intentionally does not write ownership to ItemStack NBT, custom data, lore, or components.
 * Ground item ownership is tracked by ItemEntity UUID in memory so normal stackables keep identical
 * components and can stack normally inside inventories.
 */
public final class IronmanItemOwnership {
    public static final String ROOT = "champutils_ironman";
    public static final String OWNER_PROFILE = "owner_profile";
    public static final String OWNER_PLAYER = "owner_player";
    public static final String SOURCE = "source";
    public static final String DROP_TAG_PREFIX = "champutils_ironman_dropper_profile:";
    public static final String DROP_MODE_TAG_PREFIX = "champutils_dropper_mode:";

    private static final long OWNERSHIP_TTL_MS = 30L * 60L * 1000L;
    private static final Map<UUID, Ownership> ITEM_OWNERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> DENY_MESSAGE_COOLDOWN = new ConcurrentHashMap<>();
    private static boolean registered = false;

    private IronmanItemOwnership() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 1200 != 0) return;
            long cutoff = System.currentTimeMillis() - OWNERSHIP_TTL_MS;
            ITEM_OWNERS.entrySet().removeIf(entry -> entry.getValue().lastSeenMillis < cutoff);
            DENY_MESSAGE_COOLDOWN.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        });
    }

    public static void tagDroppedEntity(Player player, ItemEntity entity) {
        if (!(player instanceof ServerPlayer serverPlayer) || entity == null) return;
        markEntityOwner(entity, PlayerProfileManager.activeProfileId(serverPlayer), serverPlayer.getUUID(), PlayerProfileManager.gameMode(serverPlayer));
    }

    public static void markEntityOwner(ItemEntity entity, UUID ownerProfile, UUID ownerPlayer, ProfileGameMode mode) {
        if (entity == null || ownerProfile == null) return;
        ProfileGameMode safeMode = mode == null ? ProfileGameMode.NORMAL : mode;
        ITEM_OWNERS.put(entity.getUUID(), new Ownership(ownerProfile, ownerPlayer, safeMode, System.currentTimeMillis()));
    }

    public static void markEntityOwnerIfUnowned(ItemEntity entity, UUID ownerProfile, UUID ownerPlayer, ProfileGameMode mode) {
        if (entity == null || ownerProfile == null) return;
        ITEM_OWNERS.putIfAbsent(entity.getUUID(), new Ownership(ownerProfile, ownerPlayer, mode == null ? ProfileGameMode.NORMAL : mode, System.currentTimeMillis()));
    }

    public static boolean hasRuntimeOwner(ItemEntity entity) {
        return entity != null && ITEM_OWNERS.containsKey(entity.getUUID());
    }

    public static boolean canPickup(ServerPlayer player, ItemEntity entity) {
        if (player == null || entity == null) return true;
        Ownership owner = ITEM_OWNERS.get(entity.getUUID());
        if (owner == null) return true;
        owner.lastSeenMillis = System.currentTimeMillis();

        ProfileGameMode playerMode = PlayerProfileManager.gameMode(player);
        UUID playerProfile = PlayerProfileManager.activeProfileId(player);
        boolean allowed;

        if (playerMode.usesIronmanRules()) {
            // Ironman and Nuzlocke are solo self-found. Only the exact same active profile may pick it up.
            allowed = playerProfile != null && playerProfile.equals(owner.ownerProfile)
                    && owner.mode.usesIronmanRules();
        } else if (playerMode == ProfileGameMode.ISLANDER) {
            // Islanders may share with other Islander profiles, but not with normal/ironman/nuzlocke profiles.
            allowed = owner.mode == ProfileGameMode.ISLANDER;
        } else {
            // Normal profiles cannot be used as a mule for restricted-mode drops.
            allowed = owner.mode != ProfileGameMode.ISLANDER && !owner.mode.usesIronmanRules();
        }

        if (!allowed) denyMessage(player, playerMode);
        return allowed;
    }

    private static void denyMessage(ServerPlayer player, ProfileGameMode playerMode) {
        long now = System.currentTimeMillis();
        Long last = DENY_MESSAGE_COOLDOWN.get(player.getUUID());
        if (last != null && now - last < 2000L) return;
        DENY_MESSAGE_COOLDOWN.put(player.getUUID(), now);
        String message = playerMode == ProfileGameMode.ISLANDER
                ? "Islanders can only pick up Islander/self-found items."
                : playerMode.usesIronmanRules()
                ? playerMode.displayName() + " profiles can only pick up their own self-found items."
                : "That item belongs to a restricted profile.";
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    public static boolean isRestricted(ServerPlayer player) {
        if (player == null) return false;
        ProfileGameMode mode = PlayerProfileManager.gameMode(player);
        return mode.usesIronmanRules() || mode == ProfileGameMode.ISLANDER;
    }

    public static boolean usesItemOwnershipRules(ServerPlayer player) {
        return isRestricted(player);
    }

    public static boolean canMoveStackIntoRestrictedInventory(ServerPlayer player, ItemStack stack) {
        // Stack-only container movement has no safe ownership signal without writing to the ItemStack.
        // Ground pickup, direct drops, and player-caused block drops are enforced by ItemEntity UUID instead.
        return true;
    }

    public static void stampContainerDeposit(ServerPlayer player, ItemStack stack) {
        // No-op. Never write ownership components during container movement.
    }

    public static boolean denyForeignUse(ServerPlayer player, ItemStack stack) {
        return false;
    }

    public static void stampIfIronmanOwned(ServerPlayer player, ItemStack stack, String source) {
        // No-op. Never stamp held/used items.
    }

    public static void stampOwned(ServerPlayer player, ItemStack stack, String source) {
        // No-op. Never write owner/profile UUIDs to ItemStack components.
    }

    public static boolean clearRestrictedProfileData(ItemStack stack) {
        return false;
    }

    public static boolean clearOwnership(ItemStack stack) {
        return false;
    }

    public static boolean normalizeOwnedStack(ItemStack stack) {
        return false;
    }

    public static UUID ownerProfile(ItemStack stack) {
        return null;
    }

    public static UUID dropperProfile(Entity entity) {
        if (entity == null) return null;
        Ownership owner = ITEM_OWNERS.get(entity.getUUID());
        return owner == null ? null : owner.ownerProfile;
    }

    public static boolean sanitizeStack(ServerPlayer player, ItemStack stack) {
        return false;
    }

    private static final class Ownership {
        final UUID ownerProfile;
        final UUID ownerPlayer;
        final ProfileGameMode mode;
        volatile long lastSeenMillis;

        private Ownership(UUID ownerProfile, UUID ownerPlayer, ProfileGameMode mode, long lastSeenMillis) {
            this.ownerProfile = ownerProfile;
            this.ownerPlayer = ownerPlayer;
            this.mode = mode;
            this.lastSeenMillis = lastSeenMillis;
        }
    }
}
