package com.champutils.dungeon;

import eu.pb4.polymer.core.api.item.PolymerItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DungeonKeyManager {

    private static final Map<String, Item> REGISTERED_KEYS = new HashMap<>();

    private DungeonKeyManager() {
    }

    public static void registerKeys() {
        REGISTERED_KEYS.clear();

        for (Map.Entry<String, DungeonKeyConfig.KeyData> entry : DungeonKeyConfig.KEYS.entrySet()) {
            registerKey(entry.getKey(), entry.getValue());
        }

        System.out.println("[ChampUtils] Registered " + REGISTERED_KEYS.size() + " dungeon keys.");
    }

    private static void registerKey(String keyId, DungeonKeyConfig.KeyData data) {
        if (keyId == null || keyId.isBlank() || data == null || data.itemId == null || data.itemId.isBlank()) {
            return;
        }

        Item baseItem = getBaseItem(data.baseItem);
        Item keyItem = new DungeonKeyItem(keyId, baseItem, new Item.Properties().stacksTo(64));

        try {
            Registry.register(
                    BuiltInRegistries.ITEM,
                    ResourceLocation.fromNamespaceAndPath("champutils", data.itemId),
                    keyItem
            );
            REGISTERED_KEYS.put(keyId, keyItem);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Item getBaseItem(String baseItemId) {
        if (baseItemId == null || baseItemId.isBlank()) {
            return Items.TRIPWIRE_HOOK;
        }

        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(baseItemId));
            return item == null || item == Items.AIR ? Items.TRIPWIRE_HOOK : item;
        } catch (Exception ignored) {
            return Items.TRIPWIRE_HOOK;
        }
    }

    public static ItemStack createKeyStack(String keyId, int amount) {
        DungeonKeyConfig.KeyData data = DungeonKeyConfig.KEYS.get(keyId);
        Item item = REGISTERED_KEYS.get(keyId);

        if (data == null || item == null || amount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item, Math.min(64, amount));
        applyKeyDisplay(stack, keyId, data);
        return stack;
    }

    public static boolean consumeKey(ServerPlayer player, String keyId) {
        if (player == null || keyId == null || keyId.isBlank()) {
            return false;
        }

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isKey(stack, keyId)) {
                stack.shrink(1);
                player.getInventory().setChanged();
                return true;
            }
        }

        return false;
    }

    public static boolean hasKey(ServerPlayer player, String keyId) {
        if (player == null || keyId == null || keyId.isBlank()) {
            return false;
        }

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isKey(player.getInventory().getItem(slot), keyId)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isKey(ItemStack stack, String keyId) {
        if (stack == null || stack.isEmpty() || keyId == null || keyId.isBlank()) {
            return false;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }

        CompoundTag tag = customData.copyTag();
        return tag.getBoolean("ChampDungeonKey") && keyId.equals(tag.getString("DungeonKeyId"));
    }

    private static void applyKeyDisplay(ItemStack stack, String keyId, DungeonKeyConfig.KeyData data) {
        ChatFormatting color = parseColor(data.color);

        stack.set(
                DataComponents.CUSTOM_NAME,
                Component.literal(data.displayName == null ? keyId : data.displayName)
                        .withStyle(color)
                        .withStyle(ChatFormatting.BOLD)
        );

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(data.rarity == null ? "Dungeon Key" : data.rarity + " Dungeon Key").withStyle(ChatFormatting.GRAY));
        if (data.lore != null && !data.lore.isBlank()) {
            lore.add(Component.literal(data.lore).withStyle(ChatFormatting.DARK_GRAY));
        }
        lore.add(Component.literal("Consumed when entering a dungeon.").withStyle(ChatFormatting.RED));
        stack.set(DataComponents.LORE, new ItemLore(lore));

        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(data.customModelData));

        CompoundTag tag = new CompoundTag();
        tag.putBoolean("ChampDungeonKey", true);
        tag.putString("DungeonKeyId", keyId);
        tag.putString("DungeonKeyRarity", data.rarity == null ? "COMMON" : data.rarity.toUpperCase());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static ChatFormatting parseColor(String value) {
        if (value == null || value.isBlank()) {
            return ChatFormatting.WHITE;
        }

        try {
            return ChatFormatting.valueOf(value.trim().toUpperCase());
        } catch (Exception ignored) {
            return ChatFormatting.WHITE;
        }
    }

    public static class DungeonKeyItem extends Item implements PolymerItem {
        private final String keyId;
        private final Item polymerItem;

        public DungeonKeyItem(String keyId, Item polymerItem, Properties properties) {
            super(properties);
            this.keyId = keyId;
            this.polymerItem = polymerItem == null ? Items.TRIPWIRE_HOOK : polymerItem;
        }

        @Override
        public Item getPolymerItem(ItemStack itemStack, ServerPlayer player) {
            return polymerItem;
        }

        public String getKeyId() {
            return keyId;
        }
    }
}
