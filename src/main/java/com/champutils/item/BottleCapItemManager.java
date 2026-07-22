package com.champutils.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server-owned Polymer bottle caps. No datapack functions or paper-name matching is required. */
public final class BottleCapItemManager {
    public enum CapType {
        ATTACK("silver_bottle_cap_atk", "Attack Bottle Cap", Items.IRON_NUGGET),
        DEFENCE("silver_bottle_cap_def", "Defence Bottle Cap", Items.IRON_NUGGET),
        HP("silver_bottle_cap_hp", "HP Bottle Cap", Items.IRON_NUGGET),
        SPECIAL_ATTACK("silver_bottle_cap_sp_atk", "Special Attack Bottle Cap", Items.IRON_NUGGET),
        SPECIAL_DEFENCE("silver_bottle_cap_sp_def", "Special Defence Bottle Cap", Items.IRON_NUGGET),
        SPEED("silver_bottle_cap_speed", "Speed Bottle Cap", Items.IRON_NUGGET),
        GOLDEN("golden_bottle_cap", "Golden Bottle Cap", Items.GOLD_NUGGET);

        public final String path;
        public final String displayName;
        public final Item polymerBase;
        CapType(String path, String displayName, Item polymerBase) {
            this.path = path;
            this.displayName = displayName;
            this.polymerBase = polymerBase;
        }
    }

    private static final Map<CapType, Item> ITEMS = new LinkedHashMap<>();
    private BottleCapItemManager() {}

    public static void registerItems() {
        if (!ITEMS.isEmpty()) return;
        for (CapType type : CapType.values()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("champutils", type.path);
            Item item = BuiltInRegistries.ITEM.containsKey(id)
                    ? BuiltInRegistries.ITEM.get(id)
                    : Registry.register(BuiltInRegistries.ITEM, id, new BottleCapItem(type, new Item.Properties().stacksTo(64)));
            ITEMS.put(type, item);
        }
        System.out.println("[ChampUtils] Registered " + ITEMS.size() + " Polymer Bottle Cap items.");
    }

    public static ItemStack create(CapType type, int amount) {
        Item item = ITEMS.get(type);
        if (item == null || amount <= 0) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, Math.min(64, amount));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(type.displayName)
                .withStyle(type == CapType.GOLDEN ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Hyper Training item").withStyle(ChatFormatting.GRAY),
                Component.literal("Right-click to choose a party Pokémon.").withStyle(ChatFormatting.DARK_GRAY),
                Component.literal("Makes the Pokémon permanently unbreedable.").withStyle(ChatFormatting.RED)
        )));
        return stack;
    }

    public static CapType typeOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (Map.Entry<CapType, Item> entry : ITEMS.entrySet()) {
            if (stack.is(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    public static CapType fromConfiguredId(String raw) {
        String path = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        int colon = path.indexOf(':');
        if (colon >= 0) path = path.substring(colon + 1);
        return switch (path) {
            case "bottle_cap", "silver_bottle_cap", "silver_bottle_cap_atk", "silver_bottle_cap_attack", "attack_bottle_cap" -> CapType.ATTACK;
            case "silver_bottle_cap_def", "silver_bottle_cap_defence", "silver_bottle_cap_defense", "defence_bottle_cap", "defense_bottle_cap" -> CapType.DEFENCE;
            case "silver_bottle_cap_hp", "hp_bottle_cap" -> CapType.HP;
            case "silver_bottle_cap_sp_atk", "silver_bottle_cap_special_attack", "special_attack_bottle_cap", "sp_atk_bottle_cap" -> CapType.SPECIAL_ATTACK;
            case "silver_bottle_cap_sp_def", "silver_bottle_cap_special_defence", "silver_bottle_cap_special_defense", "special_defence_bottle_cap", "special_defense_bottle_cap", "sp_def_bottle_cap" -> CapType.SPECIAL_DEFENCE;
            case "silver_bottle_cap_speed", "speed_bottle_cap" -> CapType.SPEED;
            case "gold_bottle_cap", "golden_bottle_cap" -> CapType.GOLDEN;
            default -> null;
        };
    }

    public static String configuredId(CapType type) {
        return "champutils:" + type.path;
    }

    private static final class BottleCapItem extends Item implements PolymerItem {
        private final CapType type;
        private BottleCapItem(CapType type, Properties properties) { super(properties); this.type = type; }
        @Override public Item getPolymerItem(ItemStack stack, ServerPlayer player) { return type.polymerBase; }
    }
}
