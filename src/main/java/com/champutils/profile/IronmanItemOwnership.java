package com.champutils.profile;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class IronmanItemOwnership {
    public static final String ROOT = "champutils_ironman";
    public static final String OWNER_PROFILE = "owner_profile";
    public static final String OWNER_PLAYER = "owner_player";
    /**
     * Legacy field. Do not write this anymore.
     * Different source values made otherwise identical owned items refuse to stack.
     */
    public static final String SOURCE = "source";
    public static final String DROP_TAG_PREFIX = "champutils_ironman_dropper_profile:";
    public static final String DROP_MODE_TAG_PREFIX = "champutils_dropper_mode:";

    private static boolean registered = false;
    private static int tickCounter = 0;
    private static final long DENY_COOLDOWN_MS = 5000L;
    private static final Map<UUID, Long> LAST_DENY_MS = new ConcurrentHashMap<>();

    private IronmanItemOwnership() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = serverPlayer.getItemInHand(hand);
                if (denyForeignUse(serverPlayer, stack)) return InteractionResultHolder.fail(stack);
                stampIfIronmanOwned(serverPlayer, stack, "use");
            }
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = serverPlayer.getItemInHand(hand);
                if (denyForeignUse(serverPlayer, stack)) return InteractionResult.FAIL;
                stampIfIronmanOwned(serverPlayer, stack, "block_use");
            }
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = serverPlayer.getItemInHand(hand);
                if (denyForeignUse(serverPlayer, stack)) return InteractionResult.FAIL;
                stampIfIronmanOwned(serverPlayer, stack, "entity_use");
            }
            return InteractionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = serverPlayer.getItemInHand(hand);
                if (denyForeignUse(serverPlayer, stack)) return InteractionResult.FAIL;
                stampIfIronmanOwned(serverPlayer, stack, "attack");
            }
            return InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter % 40 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) sanitizeInventory(player);
        });
    }

    public static void tagDroppedEntity(Player player, ItemEntity entity) {
        if (!(player instanceof ServerPlayer serverPlayer) || entity == null) return;
        ProfileGameMode mode = PlayerProfileManager.gameMode(serverPlayer);
        entity.addTag(DROP_MODE_TAG_PREFIX + mode.name());
        if (usesItemOwnershipRules(serverPlayer)) {
            UUID profileId = PlayerProfileManager.activeProfileId(serverPlayer);
            if (profileId == null) return;
            entity.addTag(DROP_TAG_PREFIX + profileId);
            stampOwned(serverPlayer, entity.getItem(), "player_drop");
        } else {
            normalizeOwnedStack(entity.getItem());
        }
    }

    public static boolean canPickup(ServerPlayer player, ItemEntity entity) {
        if (player == null || entity == null) return true;

        // Normal profiles should behave like vanilla for trading and dropped items.
        // They may pick up anything, including legacy Ironman/Islander-stamped stacks.
        // Strip the ownership marker on pickup so the item can be traded/stacked normally afterwards.
        if (!usesItemOwnershipRules(player)) {
            clearOwnership(entity.getItem());
            return true;
        }

        UUID activeProfile = PlayerProfileManager.activeProfileId(player);
        UUID itemOwner = ownerProfile(entity.getItem());
        UUID dropper = dropperProfile(entity);

        boolean playerIsIslander = PlayerProfileManager.isIslander(player);
        ProfileGameMode itemOwnerMode = itemOwner == null ? null : ownerProfileMode(itemOwner);
        ProfileGameMode dropperMode = dropperMode(entity);

        // Islander profiles are isolated from the normal economy, but may share
        // Islander-stamped items with other Islander profiles. Ironman/Nuzlocke
        // stay strictly profile-private.
        if (itemOwner != null && !itemOwner.equals(activeProfile) && !player.hasPermissions(4)) {
            if (!(playerIsIslander && itemOwnerMode == ProfileGameMode.ISLANDER)) {
                deny(player, playerIsIslander ? "Islanders can only pick up Islander items." : "That item belongs to another profile.");
                return false;
            }
        }

        if (playerIsIslander) {
            if (dropperMode != null && dropperMode != ProfileGameMode.ISLANDER) {
                deny(player, "Islanders can only pick up items from other Islanders or the world.");
                return false;
            }
            if (dropper != null && ownerProfileMode(dropper) != ProfileGameMode.ISLANDER) {
                deny(player, "Islanders can only pick up items from other Islanders or the world.");
                return false;
            }
        } else if (usesItemOwnershipRules(player)) {
            if (dropper != null && !dropper.equals(activeProfile)) {
                deny(player, "Restricted profiles cannot pick up items dropped by other players.");
                return false;
            }
        }

        if (itemOwner == null && usesItemOwnershipRules(player)) stampOwned(player, entity.getItem(), dropper == null ? "world_pickup" : "own_drop_pickup");
        return true;
    }

    public static boolean isRestricted(ServerPlayer player) {
        return usesItemOwnershipRules(player);
    }

    /**
     * Profiles that should stamp otherwise normal stackable items with ownership.
     * Normal/Monotype profiles intentionally do not stamp items, which keeps the
     * common player experience fully vanilla-stackable.
     */
    public static boolean usesItemOwnershipRules(ServerPlayer player) {
        return player != null
                && (PlayerProfileManager.isIronman(player)
                    || PlayerProfileManager.isNuzlocke(player)
                    || PlayerProfileManager.isIslander(player))
                && !player.hasPermissions(4);
    }

    public static boolean canMoveStackIntoRestrictedInventory(ServerPlayer player, ItemStack stack) {
        return !denyForeignUse(player, stack);
    }

    public static void stampContainerDeposit(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return;
        if (!usesItemOwnershipRules(player)) return;
        if (!denyForeignUse(player, stack)) stampIfIronmanOwned(player, stack, "container_deposit");
    }

    public static boolean denyForeignUse(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        if (!usesItemOwnershipRules(player)) {
            clearOwnership(stack);
            return false;
        }
        if (player.hasPermissions(4)) return false;
        UUID owner = ownerProfile(stack);
        if (owner == null) return false;
        UUID active = PlayerProfileManager.activeProfileId(player);
        if (!owner.equals(active)) {
            if (PlayerProfileManager.isIslander(player) && ownerProfileMode(owner) == ProfileGameMode.ISLANDER) {
                normalizeOwnedStack(stack);
                return false;
            }
            deny(player, PlayerProfileManager.isIslander(player) ? "Islanders can only use Islander items." : "That item belongs to another profile.");
            return true;
        }
        normalizeOwnedStack(stack);
        return false;
    }

    public static void stampIfIronmanOwned(ServerPlayer player, ItemStack stack, String source) {
        if (player == null || stack == null || stack.isEmpty()) return;
        UUID owner = ownerProfile(stack);
        if (owner != null) {
            normalizeOwnedStack(stack);
            return;
        }

        if (!usesItemOwnershipRules(player)) return;

        // Right-clicking Cobblemon stackables such as apricorns used to stamp only the
        // held stack. That left identical items split between owned and unowned component
        // states, so Minecraft would not merge them. When a restricted profile stamps a
        // stack, stamp matching unowned inventory stacks at the same time.
        stampMatchingUnownedInventoryStacks(player, stack);
        stampOwned(player, stack, source);
    }

    private static void stampMatchingUnownedInventoryStacks(ServerPlayer player, ItemStack reference) {
        if (player == null || reference == null || reference.isEmpty()) return;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack other = inv.getItem(i);
            if (other == null || other.isEmpty() || other == reference) continue;
            if (!ItemStack.isSameItem(reference, other)) continue;
            if (ownerProfile(other) != null) {
                normalizeOwnedStack(other);
                continue;
            }
            stampOwned(player, other, "inventory_match");
        }
    }

    public static void stampOwned(ServerPlayer player, ItemStack stack, String source) {
        if (player == null || stack == null || stack.isEmpty()) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null) return;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag root = tag.getCompound(ROOT);
        root.putString(OWNER_PROFILE, profileId.toString());
        root.putString(OWNER_PLAYER, player.getUUID().toString());
        // IMPORTANT: do not write per-action/source metadata to the item.
        // Components must be byte-identical for Minecraft to merge stacks, so
        // storing values like world_pickup/container_deposit/player_drop splits
        // the same item into multiple stacks. Keep ownership stable and minimal.
        root.remove(SOURCE);
        tag.put(ROOT, root);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * Removes ChampUtils restricted-profile ownership from a stack. Used when normal
     * profiles receive traded/dropped legacy restricted items so normal gameplay stays vanilla.
     */
    public static boolean clearOwnership(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (!tag.contains(ROOT)) return false;
            tag.remove(ROOT);
            if (tag.isEmpty()) {
                stack.remove(DataComponents.CUSTOM_DATA);
            } else {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Migrates old owned items to the stack-friendly format. Returns true when
     * the stack's components were changed.
     */
    public static boolean normalizeOwnedStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (!tag.contains(ROOT)) return false;
            CompoundTag root = tag.getCompound(ROOT);
            boolean changed = false;
            if (root.contains(SOURCE)) {
                root.remove(SOURCE);
                changed = true;
            }
            if (changed) {
                tag.put(ROOT, root);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            return changed;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static UUID ownerProfile(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            CompoundTag root = tag.getCompound(ROOT);
            if (!root.contains(OWNER_PROFILE)) return null;
            return UUID.fromString(root.getString(OWNER_PROFILE));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static UUID dropperProfile(Entity entity) {
        if (entity == null) return null;
        for (String tag : entity.getTags()) {
            if (!tag.startsWith(DROP_TAG_PREFIX)) continue;
            try { return UUID.fromString(tag.substring(DROP_TAG_PREFIX.length())); }
            catch (Throwable ignored) { return null; }
        }
        return null;
    }

    private static ProfileGameMode dropperMode(Entity entity) {
        if (entity == null) return null;
        for (String tag : entity.getTags()) {
            if (!tag.startsWith(DROP_MODE_TAG_PREFIX)) continue;
            return ProfileGameMode.parse(tag.substring(DROP_MODE_TAG_PREFIX.length()));
        }
        return null;
    }

    private static ProfileGameMode ownerProfileMode(UUID profileId) {
        if (profileId == null) return ProfileGameMode.NORMAL;
        return PlayerProfileManager.modeOfProfileIdBlocking(profileId.toString());
    }

    private static void sanitizeInventory(ServerPlayer player) {
        if (player == null || PlayerProfileManager.isInMainMenu(player)) return;
        if (!usesItemOwnershipRules(player)) return;
        Inventory inv = player.getInventory();
        boolean changed = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            if (!denyForeignUse(player, stack)) {
                UUID before = ownerProfile(stack);
                stampIfIronmanOwned(player, stack, "inventory_scan");
                changed = true;
            }
        }
        if (changed) compactMatchingInventoryStacks(inv);
    }

    private static void compactMatchingInventoryStacks(Inventory inv) {
        if (inv == null) return;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack target = inv.getItem(i);
            if (target == null || target.isEmpty() || target.getCount() >= target.getMaxStackSize()) continue;
            for (int j = i + 1; j < inv.getContainerSize(); j++) {
                ItemStack source = inv.getItem(j);
                if (source == null || source.isEmpty()) continue;
                normalizeOwnedStack(target);
                normalizeOwnedStack(source);
                if (!ItemStack.isSameItemSameComponents(target, source)) continue;
                int room = target.getMaxStackSize() - target.getCount();
                if (room <= 0) break;
                int move = Math.min(room, source.getCount());
                target.grow(move);
                source.shrink(move);
                if (source.isEmpty()) inv.setItem(j, ItemStack.EMPTY);
            }
        }
    }

    private static void deny(ServerPlayer player, String message) {
        if (player == null) return;
        long now = System.currentTimeMillis();
        Long last = LAST_DENY_MS.get(player.getUUID());
        if (last != null && now - last < DENY_COOLDOWN_MS) return;
        LAST_DENY_MS.put(player.getUUID(), now);
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
