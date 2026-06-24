package com.champutils.profile;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Restricted-profile item ownership is intentionally disabled.
 *
 * Minecraft 1.21.1 includes the full ItemStack component map in stack comparison.
 * The previous Ironman/Islander/Nuzlocke enforcement wrote ownership/profile UUIDs,
 * lore, and/or custom data to otherwise normal items when they were held, moved,
 * dropped, or picked up. That created the 5-components vs 6-components split and
 * made vanilla/Cobblemon stackables refuse to merge.
 *
 * This class must not write, strip, sanitize, normalize, or otherwise mutate
 * ItemStack components. Restricted-profile rules can be rebuilt later using a
 * non-ItemStack system such as SQL transaction checks, temporary entity tags, or
 * container/session ownership that never touches the stack itself.
 */
public final class IronmanItemOwnership {
    public static final String ROOT = "champutils_ironman";
    public static final String OWNER_PROFILE = "owner_profile";
    public static final String OWNER_PLAYER = "owner_player";
    public static final String SOURCE = "source";
    public static final String DROP_TAG_PREFIX = "champutils_ironman_dropper_profile:";
    public static final String DROP_MODE_TAG_PREFIX = "champutils_dropper_mode:";

    private static boolean registered = false;

    private IronmanItemOwnership() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        // No event hooks. Do not touch ItemStacks on use, tick, join, pickup, drop, or container movement.
    }

    public static void tagDroppedEntity(Player player, ItemEntity entity) {
        // No-op. Do not add stack data and do not mutate the dropped ItemStack.
    }

    public static boolean canPickup(ServerPlayer player, ItemEntity entity) {
        // Disabled for now: never block pickup and never mutate the ItemStack.
        return true;
    }

    public static boolean isRestricted(ServerPlayer player) {
        return false;
    }

    public static boolean usesItemOwnershipRules(ServerPlayer player) {
        return false;
    }

    public static boolean canMoveStackIntoRestrictedInventory(ServerPlayer player, ItemStack stack) {
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
        // No-op by design. This class should fix stack issues by not creating component differences,
        // not by running a sanitizer that strips components after the fact.
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
        return null;
    }

    public static boolean sanitizeStack(ServerPlayer player, ItemStack stack) {
        return false;
    }
}
