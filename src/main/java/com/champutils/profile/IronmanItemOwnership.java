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
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
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
    private static final Pattern UUID_TEXT = Pattern.compile("(?i).*\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b.*");

    private IronmanItemOwnership() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = serverPlayer.getItemInHand(hand);
                if (denyForeignUse(serverPlayer, stack)) return InteractionResultHolder.fail(stack);
                stampIfIronmanOwned(serverPlayer, stack, "use");
                sanitizeInventory(serverPlayer);
            }
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = serverPlayer.getItemInHand(hand);
                if (denyForeignUse(serverPlayer, stack)) return InteractionResult.FAIL;
                stampIfIronmanOwned(serverPlayer, stack, "block_use");
                sanitizeInventory(serverPlayer);
            }
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = serverPlayer.getItemInHand(hand);
                if (denyForeignUse(serverPlayer, stack)) return InteractionResult.FAIL;
                stampIfIronmanOwned(serverPlayer, stack, "entity_use");
                sanitizeInventory(serverPlayer);
            }
            return InteractionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = serverPlayer.getItemInHand(hand);
                if (denyForeignUse(serverPlayer, stack)) return InteractionResult.FAIL;
                stampIfIronmanOwned(serverPlayer, stack, "attack");
                sanitizeInventory(serverPlayer);
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
        // Never let per-player/profile ItemStack NBT or UUID lore survive on dropped items.
        // Restricted-profile pickup rules are enforced by transient entity scoreboard tags below.
        clearRestrictedProfileData(entity.getItem());

        ProfileGameMode mode = PlayerProfileManager.gameMode(serverPlayer);
        entity.addTag(DROP_MODE_TAG_PREFIX + mode.name());
        if (usesItemOwnershipRules(serverPlayer)) {
            UUID profileId = PlayerProfileManager.activeProfileId(serverPlayer);
            if (profileId == null) return;
            entity.addTag(DROP_TAG_PREFIX + profileId);
            // Do not stamp the ItemStack. Entity scoreboard tags enforce pickup rules without breaking stacking.
        } else {
            clearRestrictedProfileData(entity.getItem());
        }
    }

    public static boolean canPickup(ServerPlayer player, ItemEntity entity) {
        if (player == null || entity == null) return true;

        // Normal profiles are fully vanilla-stackable and unrestricted.
        if (!usesItemOwnershipRules(player)) {
            clearRestrictedProfileData(entity.getItem());
            return true;
        }

        if (player.hasPermissions(4)) {
            clearRestrictedProfileData(entity.getItem());
            return true;
        }

        UUID activeProfile = PlayerProfileManager.activeProfileId(player);
        UUID legacyOwner = ownerProfile(entity.getItem());
        UUID dropper = dropperProfile(entity);
        ProfileGameMode dropperMode = dropperMode(entity);
        boolean playerIsIslander = PlayerProfileManager.isIslander(player);

        // Legacy NBT-owned items are no longer written, but still protect old stacks without SQL.
        if (legacyOwner != null && activeProfile != null && !legacyOwner.equals(activeProfile)) {
            deny(player, playerIsIslander ? "Islanders cannot pick up legacy-owned restricted items." : "That item belongs to another profile.");
            return false;
        }

        // New system: dropped item entities only carry transient scoreboard tags. No item NBT, no lore, no SQL.
        if (playerIsIslander) {
            if (dropperMode != null && dropperMode != ProfileGameMode.ISLANDER) {
                deny(player, "Islanders can only pick up items from other Islanders or the world.");
                return false;
            }
            clearRestrictedProfileData(entity.getItem());
            return true;
        }

        if (dropper != null && activeProfile != null && !dropper.equals(activeProfile)) {
            deny(player, "Restricted profiles cannot pick up items dropped by other players.");
            return false;
        }

        clearRestrictedProfileData(entity.getItem());
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
        // Ownership is no longer written to item NBT/lore. Container restrictions are enforced by listeners/mixins.
        if (player == null || stack == null || stack.isEmpty()) return;
        if (!usesItemOwnershipRules(player)) clearRestrictedProfileData(stack);
    }

    public static boolean denyForeignUse(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        if (!usesItemOwnershipRules(player)) {
            clearRestrictedProfileData(stack);
            return false;
        }
        if (player.hasPermissions(4)) return false;
        UUID owner = ownerProfile(stack);
        if (owner == null) return false;
        UUID active = PlayerProfileManager.activeProfileId(player);
        if (!owner.equals(active)) {
            deny(player, PlayerProfileManager.isIslander(player) ? "Islanders cannot use legacy-owned restricted items." : "That item belongs to another profile.");
            return true;
        }
        normalizeOwnedStack(stack);
        return false;
    }

    public static void stampIfIronmanOwned(ServerPlayer player, ItemStack stack, String source) {
        // Disabled by design: item NBT/lore ownership caused stack splitting and server-thread SQL lookups.
        // Restricted-profile isolation is now enforced from player/drop/container context only.
        if (player == null || stack == null || stack.isEmpty()) return;
        clearRestrictedProfileData(stack);
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
        // No-op: never write owner/profile UUIDs to ItemStack components.
        if (player == null || stack == null || stack.isEmpty()) return;
        if (!usesItemOwnershipRules(player)) clearRestrictedProfileData(stack);
    }


    /**
     * Normal profiles must never carry restricted-profile ownership data or visible
     * owner/profile UUID lore. Keep this cleanup separate from restricted profile
     * stamping so Ironman/Islander/Nuzlocke isolation still works.
     */
    public static boolean clearRestrictedProfileData(ItemStack stack) {
        boolean changed = clearOwnership(stack);
        changed |= stripRestrictedUuidLore(stack);
        return changed;
    }

    private static boolean stripRestrictedUuidLore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null || lore.lines().isEmpty()) return false;
            List<Component> kept = new ArrayList<>();
            boolean changed = false;
            for (Component line : lore.lines()) {
                String text = line == null ? "" : line.getString();
                String lower = text.toLowerCase(java.util.Locale.ROOT);
                boolean restrictedOwnerLine = UUID_TEXT.matcher(text).matches()
                        && (lower.contains("owner")
                        || lower.contains("profile")
                        || lower.contains("ironman")
                        || lower.contains("islander")
                        || lower.contains("nuzlocke")
                        || lower.contains("uuid")
                        || lower.contains("player"));
                if (restrictedOwnerLine) {
                    changed = true;
                    continue;
                }
                kept.add(line);
            }
            if (!changed) return false;
            if (kept.isEmpty()) stack.remove(DataComponents.LORE);
            else stack.set(DataComponents.LORE, new ItemLore(kept));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Removes ChampUtils restricted-profile ownership from a stack. Used when normal
     * profiles receive traded/dropped legacy restricted items so normal gameplay stays vanilla.
     */
    public static boolean clearOwnership(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            boolean changed = false;
            if (tag.contains(ROOT)) {
                tag.remove(ROOT);
                changed = true;
            }
            // Clean up older beta/key variants if they ever existed on live items.
            for (String key : List.of("ChampUtilsOwner", "ChampUtilsProfileOwner", "ChampUtilsPlayerOwner", "OwnerProfile", "OwnerPlayer", "profile_id", "player_uuid")) {
                if (tag.contains(key)) {
                    tag.remove(key);
                    changed = true;
                }
            }
            if (!changed) return false;
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

    private static void sanitizeInventory(ServerPlayer player) {
        if (player == null || PlayerProfileManager.isInMainMenu(player)) return;
        Inventory inv = player.getInventory();
        boolean changed = false;

        // Normal/Monotype profiles must stay completely vanilla-stackable. If they ever
        // receive legacy restricted-profile item data through trading, pickups, or old
        // inventories, strip it during the periodic inventory sweep.
        if (!usesItemOwnershipRules(player)) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack == null || stack.isEmpty()) continue;
                if (clearRestrictedProfileData(stack)) changed = true;
            }
            if (changed) compactVanillaInventoryStacks(inv);
            return;
        }

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            if (clearRestrictedProfileData(stack)) changed = true;
            if (denyForeignUse(player, stack)) changed = true;
        }
        if (changed) compactMatchingInventoryStacks(inv);
    }


    private static void compactVanillaInventoryStacks(Inventory inv) {
        if (inv == null) return;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack target = inv.getItem(i);
            if (target == null || target.isEmpty() || target.getCount() >= target.getMaxStackSize()) continue;
            for (int j = i + 1; j < inv.getContainerSize(); j++) {
                ItemStack source = inv.getItem(j);
                if (source == null || source.isEmpty()) continue;
                clearRestrictedProfileData(target);
                clearRestrictedProfileData(source);
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
