package com.champutils.profession;

import eu.pb4.polymer.core.api.item.PolymerItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ProfessionWeaponFragmentManager {

    private static final Map<String, Item> REGISTERED_FRAGMENTS = new HashMap<>();

    private ProfessionWeaponFragmentManager() {
    }

    public static void registerFragments() {
        REGISTERED_FRAGMENTS.clear();

        for (Map.Entry<String, ProfessionWeaponFragmentConfig.FragmentData> entry : ProfessionWeaponFragmentConfig.FRAGMENTS.entrySet()) {
            registerFragment(entry.getKey(), entry.getValue());
        }

        System.out.println("[ChampUtils] Registered " + REGISTERED_FRAGMENTS.size() + " weapon fragment items.");
    }

    private static void registerFragment(String fragmentKey, ProfessionWeaponFragmentConfig.FragmentData data) {
        if (fragmentKey == null || fragmentKey.isBlank() || data == null || data.itemId == null || data.itemId.isBlank()) {
            return;
        }

        String normalized = ProfessionWeaponFragmentConfig.normalizeRarity(fragmentKey);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("champutils", data.itemId);

        if (BuiltInRegistries.ITEM.containsKey(id)) {
            Item existing = BuiltInRegistries.ITEM.get(id);
            REGISTERED_FRAGMENTS.put(normalized, existing);
            return;
        }

        Item fragmentItem = new WeaponFragmentItem(
                normalized,
                getBaseItem(data.baseItem),
                new Item.Properties().stacksTo(64)
        );

        try {
            Registry.register(BuiltInRegistries.ITEM, id, fragmentItem);
            REGISTERED_FRAGMENTS.put(normalized, fragmentItem);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Item getBaseItem(String baseItemId) {
        if (baseItemId == null || baseItemId.isBlank()) {
            return Items.PAPER;
        }

        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(baseItemId));
            return item == null || item == Items.AIR ? Items.PAPER : item;
        } catch (Exception e) {
            return Items.PAPER;
        }
    }

    public static ItemStack createFragmentStack(String fragmentKey, int amount) {
        String normalized = ProfessionWeaponFragmentConfig.normalizeRarity(fragmentKey);
        ProfessionWeaponFragmentConfig.FragmentData data = ProfessionWeaponFragmentConfig.FRAGMENTS.get(normalized);
        Item item = REGISTERED_FRAGMENTS.get(normalized);

        if (data == null || item == null || amount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item, Math.min(64, amount));
        applyDisplay(stack, normalized, data);
        return stack;
    }

    public static boolean giveFragments(ServerPlayer player, String fragmentKey, int amount) {
        if (player == null || amount <= 0) {
            return false;
        }

        int remaining = amount;

        while (remaining > 0) {
            int stackSize = Math.min(64, remaining);
            ItemStack stack = createFragmentStack(fragmentKey, stackSize);

            if (stack.isEmpty()) {
                return false;
            }

            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }

            remaining -= stackSize;
        }

        return true;
    }

    private static void applyDisplay(ItemStack stack, String rarity, ProfessionWeaponFragmentConfig.FragmentData data) {
        ChatFormatting color = parseColor(data.color);

        stack.set(
                DataComponents.CUSTOM_NAME,
                Component.literal(data.displayName).withStyle(color)
        );

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(formatWords(rarity) + " Weapon Fragment").withStyle(color));

        if (data.lore != null && !data.lore.isBlank()) {
            lore.add(Component.literal(data.lore).withStyle(ChatFormatting.GRAY));
        }

        lore.add(Component.literal("Used later to craft unidentified weapons.").withStyle(ChatFormatting.DARK_GRAY));

        stack.set(DataComponents.LORE, new ItemLore(lore));

        if (data.customModelData > 0) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(data.customModelData));
        }
    }

    private static ChatFormatting parseColor(String color) {
        if (color == null || color.isBlank()) {
            return ChatFormatting.WHITE;
        }

        try {
            return ChatFormatting.valueOf(color.trim().toUpperCase());
        } catch (Exception e) {
            return ChatFormatting.WHITE;
        }
    }

    private static String formatWords(String input) {
        if (input == null || input.isBlank()) {
            return "Common";
        }

        String[] parts = input.toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();

        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            if (!builder.isEmpty()) {
                builder.append(' ');
            }

            builder.append(Character.toUpperCase(part.charAt(0)));

            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }

        return builder.toString();
    }

    public static class WeaponFragmentItem extends Item implements PolymerItem {

        private final String fragmentKey;
        private final Item baseItem;

        public WeaponFragmentItem(String fragmentKey, Item baseItem, Properties properties) {
            super(properties);
            this.fragmentKey = fragmentKey;
            this.baseItem = baseItem;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);

            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(
                        Component.literal("§eWeapon fragments will be used in the weapon crafting menu soon. Keep this item safe.")
                );
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        @Override
        public Item getPolymerItem(ItemStack stack, ServerPlayer player) {
            return baseItem;
        }
    }
}
