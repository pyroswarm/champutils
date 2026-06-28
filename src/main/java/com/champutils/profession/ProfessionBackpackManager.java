package com.champutils.profession;

import com.champutils.profile.PlayerProfileManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfessionBackpackManager {
    private record RecentAction(ProfessionType profession, long expiresAtMillis) {}
    private static final Map<UUID, RecentAction> RECENT = new ConcurrentHashMap<>();

    private ProfessionBackpackManager() {}

    public static void markProfessionAction(ServerPlayer player, ProfessionType profession) {
        if (player == null || profession == null) return;
        int seconds = java.lang.Math.max(1, ProfessionBackpackConfig.CONFIG.recentProfessionActionSeconds);
        RECENT.put(player.getUUID(), new RecentAction(profession, System.currentTimeMillis() + seconds * 1000L));
    }

    public static ProfessionType recentProfession(ServerPlayer player) {
        if (player == null) return null;
        RecentAction action = RECENT.get(player.getUUID());
        if (action == null) return null;
        if (System.currentTimeMillis() > action.expiresAtMillis()) {
            RECENT.remove(player.getUUID());
            return null;
        }
        return action.profession();
    }

    public static boolean isAutopickupEnabled(ServerPlayer player) {
        ProfessionDataManager.ProfessionData data = ProfessionManager.getData(player);
        return data.backpackAutopickup;
    }

    public static boolean toggleAutopickup(ServerPlayer player) {
        ProfessionDataManager.ProfessionData data = ProfessionManager.getData(player);
        data.backpackAutopickup = !data.backpackAutopickup;
        ProfessionManager.markDirtyProfile(PlayerProfileManager.activeProfileId(player));
        return data.backpackAutopickup;
    }

    public static boolean shouldCapture(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        if (!ProfessionBackpackConfig.CONFIG.enabled || !isAutopickupEnabled(player)) return false;
        if (!isSafeBackpackStack(stack)) return false;
        String itemId = itemId(stack);
        return ProfessionBackpackConfig.get(itemId) != null;
    }

    public static int capturePickup(ServerPlayer player, ItemStack stack) {
        if (!shouldCapture(player, stack)) return 0;
        String itemId = itemId(stack);
        ProfessionBackpackConfig.ItemData configured = ProfessionBackpackConfig.get(itemId);
        ProfessionType profession = configured == null ? recentProfession(player) : parseProfession(configured.profession);
        if (profession == null) profession = ProfessionType.FARMING;
        if (configured == null) return 0;
        int amount = stack.getCount();
        add(player, itemId, amount);
        stack.setCount(0);
        return amount;
    }

    public static long count(ServerPlayer player, String itemId) {
        ProfessionDataManager.ProfessionData data = ProfessionManager.getData(player);
        if (data.backpack == null) data.backpack = new HashMap<>();
        return java.lang.Math.max(0L, data.backpack.getOrDefault(ProfessionBackpackConfig.normalizeItem(itemId), 0L));
    }

    public static Map<String, Long> balances(ServerPlayer player) {
        ProfessionDataManager.ProfessionData data = ProfessionManager.getData(player);
        if (data.backpack == null) data.backpack = new HashMap<>();
        return new HashMap<>(data.backpack);
    }

    public static void add(ServerPlayer player, String itemId, long amount) {
        if (player == null || itemId == null || amount <= 0) return;
        ProfessionDataManager.ProfessionData data = ProfessionManager.getData(player);
        if (data.backpack == null) data.backpack = new HashMap<>();
        String id = ProfessionBackpackConfig.normalizeItem(itemId);
        long current = java.lang.Math.max(0L, data.backpack.getOrDefault(id, 0L));
        data.backpack.put(id, LongMath.addExactSafe(current, amount));
        ProfessionManager.markDirtyProfile(PlayerProfileManager.activeProfileId(player));
    }

    public static boolean remove(ServerPlayer player, String itemId, long amount) {
        if (player == null || itemId == null || amount <= 0) return false;
        ProfessionDataManager.ProfessionData data = ProfessionManager.getData(player);
        if (data.backpack == null) data.backpack = new HashMap<>();
        String id = ProfessionBackpackConfig.normalizeItem(itemId);
        long current = java.lang.Math.max(0L, data.backpack.getOrDefault(id, 0L));
        if (current < amount) return false;
        long remaining = current - amount;
        if (remaining <= 0) data.backpack.remove(id); else data.backpack.put(id, remaining);
        ProfessionManager.markDirtyProfile(PlayerProfileManager.activeProfileId(player));
        return true;
    }

    public static boolean withdraw(ServerPlayer player, String itemId, int amount) {
        if (amount <= 0) return false;
        Item item = item(itemId);
        if (item == Items.AIR) return false;
        int stackMax = java.lang.Math.max(1, item.getDefaultInstance().getMaxStackSize());
        long stored = count(player, itemId);
        int give = (int) java.lang.Math.min(java.lang.Math.min((long) amount, stored), (long) stackMax);
        if (give <= 0) return false;
        if (!remove(player, itemId, give)) return false;
        ItemStack stack = new ItemStack(item, give);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        return true;
    }

    public static TradeResult trade(ServerPlayer player, String itemId) {
        ProfessionBackpackConfig.ItemData data = ProfessionBackpackConfig.get(itemId);
        if (data == null) return new TradeResult(false, "This item is not configured for profession trades.");
        if (!data.tradeEnabled) return new TradeResult(false, "This trade is disabled.");
        long cost = java.lang.Math.max(1, data.tradeCost);
        if (count(player, itemId) < cost) return new TradeResult(false, "You need " + cost + "x " + data.displayName + ".");
        Item reward = item(data.rewardItem);
        if (reward == Items.AIR) return new TradeResult(false, "Reward item is invalid: " + data.rewardItem);
        if (!remove(player, itemId, cost)) return new TradeResult(false, "Not enough items.");
        ItemStack rewardStack = new ItemStack(reward, java.lang.Math.max(1, data.rewardAmount));
        if (!player.getInventory().add(rewardStack)) player.drop(rewardStack, false);
        return new TradeResult(true, "Traded " + cost + "x " + data.displayName + " for " + data.rewardAmount + "x " + ProfessionBackpackConfig.formatName(data.rewardItem) + ".");
    }

    public record TradeResult(boolean success, String message) {}

    public static ProfessionType parseProfession(String value) {
        try { return ProfessionType.valueOf(value.trim().toUpperCase(Locale.ROOT)); } catch (Exception e) { return null; }
    }

    public static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
    }

    public static Item item(String itemId) {
        try { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(ProfessionBackpackConfig.normalizeItem(itemId))); } catch (Exception e) { return Items.AIR; }
    }

    private static boolean isSafeBackpackStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getMaxStackSize() <= 1) return false;
        if (stack.isDamageableItem()) return false;
        return true;
    }

    private static final class LongMath {
        static long addExactSafe(long a, long b) {
            long result = a + b;
            if (((a ^ result) & (b ^ result)) < 0) return Long.MAX_VALUE;
            return result;
        }
    }
}
