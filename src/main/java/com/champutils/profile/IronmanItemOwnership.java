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

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class IronmanItemOwnership {
    public static final String ROOT = "champutils_ironman";
    public static final String OWNER_PROFILE = "owner_profile";
    public static final String OWNER_PLAYER = "owner_player";
    public static final String SOURCE = "source";
    public static final String DROP_TAG_PREFIX = "champutils_ironman_dropper_profile:";

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
        UUID profileId = PlayerProfileManager.activeProfileId(serverPlayer);
        if (profileId == null) return;
        entity.addTag(DROP_TAG_PREFIX + profileId);
        stampOwned(serverPlayer, entity.getItem(), "player_drop");
    }

    public static boolean canPickup(ServerPlayer player, ItemEntity entity) {
        if (player == null || entity == null) return true;

        UUID activeProfile = PlayerProfileManager.activeProfileId(player);
        UUID itemOwner = ownerProfile(entity.getItem());
        UUID dropper = dropperProfile(entity);

        if (isRestricted(player)) {
            if (itemOwner != null && !itemOwner.equals(activeProfile)) {
                deny(player, "That item came from another player.");
                return false;
            }

            if (dropper != null && !dropper.equals(activeProfile)) {
                deny(player, "Ironman/Nuzlocke profiles cannot pick up items dropped by other players.");
                return false;
            }
        }

        if (itemOwner == null && isRestricted(player)) stampOwned(player, entity.getItem(), dropper == null ? "world_pickup" : "own_drop_pickup");
        return true;
    }

    public static boolean isRestricted(ServerPlayer player) {
        return player != null && (PlayerProfileManager.isIronman(player) || PlayerProfileManager.isNuzlocke(player)) && !player.hasPermissions(4);
    }

    public static boolean canMoveStackIntoRestrictedInventory(ServerPlayer player, ItemStack stack) {
        return !denyForeignUse(player, stack);
    }

    public static void stampContainerDeposit(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return;
        if (!isRestricted(player)) return;
        if (!denyForeignUse(player, stack)) stampIfIronmanOwned(player, stack, "container_deposit");
    }

    public static boolean denyForeignUse(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        if (!isRestricted(player)) return false;
        UUID owner = ownerProfile(stack);
        UUID active = PlayerProfileManager.activeProfileId(player);
        if (owner != null && !owner.equals(active)) {
            deny(player, "Ironman/Nuzlocke profiles cannot use items that came from another player.");
            return true;
        }
        return false;
    }

    public static void stampIfIronmanOwned(ServerPlayer player, ItemStack stack, String source) {
        if (player == null || stack == null || stack.isEmpty()) return;
        if (ownerProfile(stack) != null) return;
        stampOwned(player, stack, source);
    }

    public static void stampOwned(ServerPlayer player, ItemStack stack, String source) {
        if (player == null || stack == null || stack.isEmpty()) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null) return;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag root = tag.getCompound(ROOT);
        root.putString(OWNER_PROFILE, profileId.toString());
        root.putString(OWNER_PLAYER, player.getUUID().toString());
        root.putString(SOURCE, source == null ? "unknown" : source.toLowerCase(Locale.ROOT));
        tag.put(ROOT, root);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
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

    private static void sanitizeInventory(ServerPlayer player) {
        if (player == null || PlayerProfileManager.isInMainMenu(player)) return;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            if (!denyForeignUse(player, stack) && isRestricted(player)) stampIfIronmanOwned(player, stack, "inventory_scan");
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
