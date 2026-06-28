package com.champutils.profession;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfessionGearManager {
    private static final Map<String, Item> REGISTERED = new ConcurrentHashMap<>();
    private static final ResourceLocation CHEST_REACH_ID = ResourceLocation.fromNamespaceAndPath("champutils", "profession_chest_reach");
    private static final ResourceLocation LEGGINGS_STEP_ID = ResourceLocation.fromNamespaceAndPath("champutils", "profession_leggings_step");
    private static final ResourceLocation LEGGINGS_KNOCKBACK_ID = ResourceLocation.fromNamespaceAndPath("champutils", "profession_leggings_knockback");
    private static boolean effectsRegistered = false;

    private ProfessionGearManager() {}

    public static void registerItems() {
        for (String rarity : new String[]{"COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC"}) {
            register(rarity.toLowerCase(Locale.ROOT) + "_profession_helmet", rarity, "helmet", ArmorItem.Type.HELMET, helmetBase(rarity));
            register(rarity.toLowerCase(Locale.ROOT) + "_profession_chestplate", rarity, "chestplate", ArmorItem.Type.CHESTPLATE, chestBase(rarity));
            register(rarity.toLowerCase(Locale.ROOT) + "_profession_leggings", rarity, "leggings", ArmorItem.Type.LEGGINGS, legBase(rarity));
        }
        System.out.println("[ChampUtils] Registered " + REGISTERED.size() + " profession armor items.");
    }

    private static void register(String id, String rarity, String gearType, ArmorItem.Type type, Item base) {
        if (REGISTERED.containsKey(id)) return;
        Item item = new ProfessionArmorItem(base, type, gearType, new Item.Properties().stacksTo(1).rarity(rarity(rarity)));
        try {
            Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("champutils", id), item);
            REGISTERED.put(id, item);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Could not register profession armor: " + id + " (probably already registered)");
        }
    }

    public static void registerEffects() {
        if (effectsRegistered) return;
        effectsRegistered = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 60 != 0) return;
            server.getPlayerList().getPlayers().forEach(ProfessionGearManager::applyEffects);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player)) return true;
            if (!source.is(DamageTypeTags.IS_FIRE)) return true;
            ItemStack leggings = player.getItemBySlot(EquipmentSlot.LEGS);
            if (!isType(leggings, "leggings")) return true;
            double fireReduction = getDouble(leggings, "fireReduction");
            if (fireReduction >= 100.0D) return false;
            return java.util.concurrent.ThreadLocalRandom.current().nextDouble(100.0D) >= Math.max(0.0D, fireReduction);
        });
    }

    private static void applyEffects(ServerPlayer player) {
        if (player == null) return;
        remove(player, Attributes.BLOCK_INTERACTION_RANGE, CHEST_REACH_ID);
        remove(player, Attributes.ENTITY_INTERACTION_RANGE, CHEST_REACH_ID);
        remove(player, Attributes.STEP_HEIGHT, LEGGINGS_STEP_ID);
        remove(player, Attributes.KNOCKBACK_RESISTANCE, LEGGINGS_KNOCKBACK_ID);

        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (isType(helmet, "helmet")) {
            makeStackUnbreakable(helmet);
            int water = getInt(helmet, "waterBreathing");
            if (water > 0) player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 160, 0, true, false, false));
            int night = getInt(helmet, "nightVision");
            if (night > 0) player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 360, 0, true, false, false));
            int dolphin = getInt(helmet, "dolphinsGrace");
            if (dolphin > 0) player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 160, Math.max(0, dolphin - 1), true, false, false));
        }

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (isType(chest, "chestplate")) {
            makeStackUnbreakable(chest);
            int strength = getInt(chest, "strength");
            if (strength > 0) player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, Math.max(0, strength - 1), true, false, false));
            int haste = getInt(chest, "haste");
            if (haste > 0) player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 160, Math.max(0, haste - 1), true, false, false));
            double reach = getDouble(chest, "blockReach");
            if (reach > 0) {
                add(player, Attributes.BLOCK_INTERACTION_RANGE, CHEST_REACH_ID, reach, AttributeModifier.Operation.ADD_VALUE);
                add(player, Attributes.ENTITY_INTERACTION_RANGE, CHEST_REACH_ID, Math.min(1.0D, reach), AttributeModifier.Operation.ADD_VALUE);
            }
        }

        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        if (isType(legs, "leggings")) {
            makeStackUnbreakable(legs);
            double step = getDouble(legs, "stepHeight");
            if (step > 0) add(player, Attributes.STEP_HEIGHT, LEGGINGS_STEP_ID, step, AttributeModifier.Operation.ADD_VALUE);
            double knockback = getDouble(legs, "knockbackResistance");
            if (knockback > 0) add(player, Attributes.KNOCKBACK_RESISTANCE, LEGGINGS_KNOCKBACK_ID, knockback, AttributeModifier.Operation.ADD_VALUE);
        }
    }

    private static void remove(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, ResourceLocation id) {
        if (player.getAttribute(attribute) != null) player.getAttribute(attribute).removeModifier(id);
    }

    private static void add(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        if (player.getAttribute(attribute) == null) return;
        player.getAttribute(attribute).removeModifier(id);
        player.getAttribute(attribute).addTransientModifier(new AttributeModifier(id, amount, operation));
    }

    public static ItemStack createArmor(String type, String rarity) {
        String normalizedType = normalizeArmorType(type);
        if ("boots".equals(normalizedType)) return RunningShoeManager.create(rarity);
        String normalizedRarity = ProfessionFragmentConfig.normalizeRarity(rarity);
        Item item = REGISTERED.get(normalizedRarity.toLowerCase(Locale.ROOT) + "_profession_" + normalizedType);
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("champutils_profession_armor", true);
        tag.putString("rarity", normalizedRarity);
        tag.putString("gearType", normalizedType);
        applyTierStats(tag, normalizedType, normalizedRarity);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(modelData(normalizedType, normalizedRarity)));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(ProfessionFragmentManager.formatWords(normalizedRarity) + " Profession " + ProfessionFragmentManager.formatWords(normalizedType)).withStyle(color(normalizedRarity)));
        stack.set(DataComponents.LORE, new ItemLore(lore(normalizedType, normalizedRarity, tag)));
        makeStackUnbreakable(stack);
        return stack;
    }

    public static boolean isProfessionArmor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        return data.copyTag().getBoolean("champutils_profession_armor");
    }

    private static boolean isType(ItemStack stack, String type) {
        if (!isProfessionArmor(stack)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return type.equalsIgnoreCase(data.copyTag().getString("gearType"));
    }

    private static void applyTierStats(CompoundTag tag, String type, String rarity) {
        ProfessionGearConfig.ArmorStats stats = ProfessionGearConfig.stats(rarity, type);
        if ("helmet".equals(type)) {
            tag.putInt("waterBreathing", stats.waterBreathing);
            tag.putInt("nightVision", stats.nightVision);
            tag.putInt("aquaAffinity", stats.aquaAffinity);
            tag.putInt("dolphinsGrace", stats.dolphinsGrace);
            tag.putInt("conduitBoost", stats.conduitBoost);
        } else if ("chestplate".equals(type)) {
            tag.putInt("strength", stats.strength);
            tag.putInt("haste", stats.haste);
            tag.putDouble("blockReach", stats.blockReach);
        } else if ("leggings".equals(type)) {
            tag.putDouble("fireReduction", stats.fireReduction);
            tag.putDouble("stepHeight", stats.stepHeight);
            tag.putDouble("knockbackResistance", stats.knockbackResistance);
        }
    }

    private static List<Component> lore(String type, String rarity, CompoundTag tag) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("§7Profession gear. Bonuses compound by rarity."));
        if ("helmet".equals(type)) {
            if (tag.getInt("waterBreathing") > 0) lore.add(Component.literal("§7Water Breathing: §aYes"));
            if (tag.getInt("nightVision") > 0) lore.add(Component.literal("§7Night Vision: §aYes"));
            if (tag.getInt("aquaAffinity") > 0) lore.add(Component.literal("§7Aqua Affinity: §aYes"));
            if (tag.getInt("dolphinsGrace") > 0) lore.add(Component.literal("§7Swim Speed: §aImproved"));
            if (tag.getInt("conduitBoost") > 0) lore.add(Component.literal("§7Ocean Utility: §aMaximum"));
        } else if ("chestplate".equals(type)) {
            if (tag.getInt("strength") > 0) lore.add(Component.literal("§7Strength: §a" + tag.getInt("strength")));
            if (tag.getInt("haste") > 0) lore.add(Component.literal("§7Haste: §a" + tag.getInt("haste")));
            if (tag.getDouble("blockReach") > 0) lore.add(Component.literal("§7Block Reach: §a+" + String.format(Locale.US, "%.1f", tag.getDouble("blockReach"))));
        } else if ("leggings".equals(type)) {
            lore.add(Component.literal("§7Fire Reduction: §a" + String.format(Locale.US, "%.0f", tag.getDouble("fireReduction")) + "%"));
            if (tag.getDouble("stepHeight") > 0) lore.add(Component.literal("§7Auto Step: §aYes"));
            if (tag.getDouble("knockbackResistance") > 0) lore.add(Component.literal("§7Knockback Resist: §a" + String.format(Locale.US, "%.0f", tag.getDouble("knockbackResistance") * 100.0D) + "%"));
        }
        lore.add(Component.literal("§8Unbreakable"));
        return lore;
    }

    private static String normalizeArmorType(String type) {
        if (type == null) return "boots";
        String s = type.trim().toLowerCase(Locale.ROOT);
        if (s.equals("helm") || s.equals("helmet") || s.equals("helmets")) return "helmet";
        if (s.equals("chest") || s.equals("chestplate") || s.equals("chestplates")) return "chestplate";
        if (s.equals("leg") || s.equals("legs") || s.equals("legging") || s.equals("leggings")) return "leggings";
        if (s.equals("boot") || s.equals("boots") || s.equals("shoes") || s.equals("running_shoes")) return "boots";
        return s;
    }

    private static void makeStackUnbreakable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        stack.remove(DataComponents.MAX_DAMAGE);
        stack.remove(DataComponents.DAMAGE);
    }

    private static int tier(String rarity) {
        return switch (ProfessionFragmentConfig.normalizeRarity(rarity)) {
            case "UNCOMMON" -> 2;
            case "RARE" -> 3;
            case "EPIC" -> 4;
            case "LEGENDARY" -> 5;
            case "MYTHIC" -> 6;
            default -> 1;
        };
    }

    private static int modelData(String type, String rarity) {
        int base = switch (type) { case "helmet" -> 9970; case "chestplate" -> 9980; case "leggings" -> 9990; default -> 9960; };
        return base + tier(rarity);
    }

    private static Item helmetBase(String rarity) { return switch (ProfessionFragmentConfig.normalizeRarity(rarity)) { case "COMMON" -> Items.LEATHER_HELMET; case "UNCOMMON" -> Items.IRON_HELMET; case "RARE", "EPIC" -> Items.DIAMOND_HELMET; default -> Items.NETHERITE_HELMET; }; }
    private static Item chestBase(String rarity) { return switch (ProfessionFragmentConfig.normalizeRarity(rarity)) { case "COMMON" -> Items.LEATHER_CHESTPLATE; case "UNCOMMON" -> Items.IRON_CHESTPLATE; case "RARE", "EPIC" -> Items.DIAMOND_CHESTPLATE; default -> Items.NETHERITE_CHESTPLATE; }; }
    private static Item legBase(String rarity) { return switch (ProfessionFragmentConfig.normalizeRarity(rarity)) { case "COMMON" -> Items.LEATHER_LEGGINGS; case "UNCOMMON" -> Items.IRON_LEGGINGS; case "RARE", "EPIC" -> Items.DIAMOND_LEGGINGS; default -> Items.NETHERITE_LEGGINGS; }; }

    private static int getInt(ItemStack stack, String key) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getInt(key);
    }

    private static double getDouble(ItemStack stack, String key) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0.0D : data.copyTag().getDouble(key);
    }

    private static ChatFormatting color(String rarity) {
        return switch (ProfessionFragmentConfig.normalizeRarity(rarity)) {
            case "UNCOMMON" -> ChatFormatting.GREEN;
            case "RARE" -> ChatFormatting.BLUE;
            case "EPIC" -> ChatFormatting.LIGHT_PURPLE;
            case "LEGENDARY" -> ChatFormatting.GOLD;
            case "MYTHIC" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }

    private static Rarity rarity(String rarity) {
        return switch (ProfessionFragmentConfig.normalizeRarity(rarity)) {
            case "UNCOMMON" -> Rarity.UNCOMMON;
            case "RARE" -> Rarity.RARE;
            case "EPIC", "LEGENDARY", "MYTHIC" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }

    public static class ProfessionArmorItem extends ArmorItem implements PolymerItem {
        private final Item base;
        public ProfessionArmorItem(Item base, ArmorItem.Type type, String gearType, Properties properties) {
            super(ArmorMaterials.LEATHER, type, properties);
            this.base = base;
        }
        @Override public Item getPolymerItem(ItemStack stack, ServerPlayer player) { return base; }
    }
}
