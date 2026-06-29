package com.champutils.profession;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfessionGearManager {
    private static final Map<String, Item> REGISTERED = new ConcurrentHashMap<>();
    private static final ResourceLocation CHEST_REACH_ID = ResourceLocation.fromNamespaceAndPath("champutils", "profession_chest_reach");
    private static final ResourceLocation LEGGINGS_STEP_ID = ResourceLocation.fromNamespaceAndPath("champutils", "profession_leggings_step");
    private static final ResourceLocation LEGGINGS_KNOCKBACK_ID = ResourceLocation.fromNamespaceAndPath("champutils", "profession_leggings_knockback");
    private static boolean effectsRegistered = false;
    private static final Map<UUID, Boolean> AUTOSTEP_ENABLED = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_AUTOSTEP_LEGGINGS = new ConcurrentHashMap<>();

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
        Item item = new ProfessionArmorItem(base, armorMaterial(rarity), type, gearType, new Item.Properties().stacksTo(1).rarity(rarity(rarity)));
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

    public static void refreshPlayerEffects(ServerPlayer player) {
        applyEffects(player);
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
            int aqua = getInt(helmet, "aquaAffinity");
            if (aqua > 0) applyAquaAffinityEnchantment(player, helmet);
            int night = getInt(helmet, "nightVision");
            if (night > 0) player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0, true, false, false));
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
        refreshAutoStepState(player, legs);
        if (isType(legs, "leggings")) {
            makeStackUnbreakable(legs);
            double step = getDouble(legs, "stepHeight");
            if (step > 0 && isAutoStepEnabled(player)) add(player, Attributes.STEP_HEIGHT, LEGGINGS_STEP_ID, step, AttributeModifier.Operation.ADD_VALUE);
            double knockback = getDouble(legs, "knockbackResistance");
            if (knockback > 0) add(player, Attributes.KNOCKBACK_RESISTANCE, LEGGINGS_KNOCKBACK_ID, knockback, AttributeModifier.Operation.ADD_VALUE);
        }
    }

    public static boolean hasAutoStepArmor(ServerPlayer player) {
        if (player == null) return false;
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        return isType(legs, "leggings") && getDouble(legs, "stepHeight") > 0.0D;
    }

    public static boolean isAutoStepEnabled(ServerPlayer player) {
        if (player == null) return true;
        return AUTOSTEP_ENABLED.getOrDefault(player.getUUID(), Boolean.TRUE);
    }

    public static boolean setAutoStep(ServerPlayer player, boolean enabled) {
        if (player == null || !hasAutoStepArmor(player)) return false;
        refreshAutoStepState(player, player.getItemBySlot(EquipmentSlot.LEGS));
        AUTOSTEP_ENABLED.put(player.getUUID(), enabled);
        refreshPlayerEffects(player);
        return true;
    }

    private static void refreshAutoStepState(ServerPlayer player, ItemStack leggings) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        String key = autoStepLeggingsKey(leggings);
        String previous = LAST_AUTOSTEP_LEGGINGS.get(uuid);
        LAST_AUTOSTEP_LEGGINGS.put(uuid, key);
        if (key.isEmpty()) {
            AUTOSTEP_ENABLED.remove(uuid);
            return;
        }
        if (!key.equals(previous)) {
            AUTOSTEP_ENABLED.put(uuid, Boolean.TRUE);
        }
    }

    private static String autoStepLeggingsKey(ItemStack stack) {
        if (!isType(stack, "leggings") || getDouble(stack, "stepHeight") <= 0.0D) return "";
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        return itemId + ":" + tag.getString("rarity") + ":" + tag.getString("gearType") + ":" + tag.getDouble("stepHeight") + ":" + tag.getDouble("knockbackResistance") + ":" + tag.getDouble("fireReduction");
    }

    public static float underwaterMiningMultiplier(Player player) {
        // Underwater speed is now handled by the real vanilla Aqua Affinity enchantment
        // on profession helmets. Keep this method as a safe no-op for older mixin references.
        return 1.0F;
    }

    private static void applyAquaAffinityEnchantment(ServerPlayer player, ItemStack helmet) {
        if (player == null || helmet == null || helmet.isEmpty()) return;
        try {
            Holder<Enchantment> aquaAffinity = player.registryAccess()
                    .registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.AQUA_AFFINITY);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(helmet.getEnchantments());
            if (mutable.getLevel(aquaAffinity) < 1) {
                mutable.set(aquaAffinity, 1);
                helmet.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
            }
        } catch (Throwable ignored) {
            // If a modded registry lookup fails, do not break armor effects.
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
            tag.putDouble("damageReduction", stats.damageReduction);
        } else if ("leggings".equals(type)) {
            tag.putDouble("fireReduction", stats.fireReduction);
            tag.putDouble("stepHeight", stats.stepHeight);
            tag.putDouble("knockbackResistance", stats.knockbackResistance);
        }
    }

    private static List<Component> lore(String type, String rarity, CompoundTag tag) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("§8" + ProfessionFragmentManager.formatWords(rarity) + " Profession Gear"));
        lore.add(Component.literal(" "));
        lore.add(Component.literal("§6Stats"));
        if ("helmet".equals(type)) {
            if (tag.getInt("waterBreathing") > 0) lore.add(Component.literal(" §aWater Breathing"));
            if (tag.getInt("nightVision") > 0) lore.add(Component.literal(" §aNight Vision"));
            if (tag.getInt("aquaAffinity") > 0) lore.add(Component.literal(" §aAqua Affinity"));
            if (tag.getInt("dolphinsGrace") > 0) lore.add(Component.literal(" §aSwim Speed"));
            if (tag.getInt("conduitBoost") > 0) lore.add(Component.literal(" §aOcean Utility"));
        } else if ("chestplate".equals(type)) {
            if (tag.getInt("strength") > 0) lore.add(Component.literal(" §a+" + tag.getInt("strength") + " Strength"));
            if (tag.getInt("haste") > 0) lore.add(Component.literal(" §a+" + tag.getInt("haste") + " Haste"));
            if (tag.getDouble("blockReach") > 0) lore.add(Component.literal(" §a+" + String.format(Locale.US, "%.1f", tag.getDouble("blockReach")) + " Block Reach"));
            if (tag.getDouble("damageReduction") > 0) lore.add(Component.literal(" §a" + String.format(Locale.US, "%.1f", tag.getDouble("damageReduction")).replaceAll("\\.0$", "") + "% Damage Reduction"));
        } else if ("leggings".equals(type)) {
            lore.add(Component.literal(" §a" + String.format(Locale.US, "%.0f", tag.getDouble("fireReduction")) + "% Fire Reduction"));
            if (tag.getDouble("stepHeight") > 0) lore.add(Component.literal(" §aAuto Step"));
            if (tag.getDouble("knockbackResistance") > 0) lore.add(Component.literal(" §a" + String.format(Locale.US, "%.0f", tag.getDouble("knockbackResistance") * 100.0D) + "% Knockback Resist"));
        }
        lore.add(Component.literal(" "));
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

    public static float applyChestplateDamageReduction(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (player == null || amount <= 0.0F) return amount;
        if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
        ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!isType(chestplate, "chestplate")) return amount;
        double reduction = Math.max(0.0D, Math.min(95.0D, getDouble(chestplate, "damageReduction")));
        if (reduction <= 0.0D) return amount;
        return (float) (amount * (1.0D - reduction / 100.0D));
    }

    private static Holder<ArmorMaterial> armorMaterial(String rarity) {
        return switch (ProfessionFragmentConfig.normalizeRarity(rarity)) {
            case "UNCOMMON" -> ArmorMaterials.IRON;
            case "RARE", "EPIC" -> ArmorMaterials.DIAMOND;
            case "LEGENDARY", "MYTHIC" -> ArmorMaterials.NETHERITE;
            default -> ArmorMaterials.LEATHER;
        };
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
        public ProfessionArmorItem(Item base, Holder<ArmorMaterial> material, ArmorItem.Type type, String gearType, Properties properties) {
            super(material, type, properties);
            this.base = base;
        }
        @Override public Item getPolymerItem(ItemStack stack, ServerPlayer player) { return base; }
    }
}
