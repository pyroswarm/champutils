package com.champutils.profession;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class RunningShoeManager {
    private static final Map<String, Item> REGISTERED = new ConcurrentHashMap<>();
    private static final ResourceLocation SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("champutils", "running_shoes_speed");
    private static final ResourceLocation JUMP_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("champutils", "running_shoes_jump");
    private static boolean effectsRegistered = false;

    private RunningShoeManager() {}

    public static void registerItems() {
        register("f_running_shoes", "F", Items.LEATHER_BOOTS);
        register("e_running_shoes", "E", Items.IRON_BOOTS);
        register("d_running_shoes", "D", Items.DIAMOND_BOOTS);
        register("c_running_shoes", "C", Items.DIAMOND_BOOTS);
        register("b_running_shoes", "B", Items.NETHERITE_BOOTS);
        register("a_running_shoes", "A", Items.NETHERITE_BOOTS);
        register("s_running_shoes", "S", Items.NETHERITE_BOOTS);
        System.out.println("[ChampUtils] Registered " + REGISTERED.size() + " running shoes.");
    }

    private static void register(String id, String rarity, Item base) {
        if (REGISTERED.containsKey(id)) return;
        Item item = new RunningShoeItem(base, armorMaterial(rarity), new Item.Properties().stacksTo(1).rarity(rarity(rarity)));
        try {
            Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("champutils", id), item);
            REGISTERED.put(id, item);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Could not register running shoes: " + id + " (probably already registered)");
        }
    }

    public static void registerEffects() {
        if (effectsRegistered) return;
        effectsRegistered = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 60 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) applyMovement(player);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player)) return true;
            if (!source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) return true;
            ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
            String rarity = getShoeRarity(boots);
            return !("D".equals(rarity) || "C".equals(rarity) || "A".equals(rarity) || "S".equals(rarity));
        });
    }

    private static void applyMovement(ServerPlayer player) {
        if (player == null) return;
        if (player.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            player.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED_MODIFIER_ID);
        }
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (!isRunningShoes(boots)) return;
        makeStackUnbreakable(boots);
        suppressRunningShoeGlint(boots);
        applyDepthStriderEnchantment(player, boots);
        double speedPercent = getDouble(boots, "speedPercent");
        if (speedPercent > 0 && player.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            player.getAttribute(Attributes.MOVEMENT_SPEED).addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID, speedPercent / 100.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        int jumpBoost = getInt(boots, "jumpBoost");
        if (jumpBoost > 0) {
            // Player jump height is reliably handled by the vanilla Jump Boost effect.
            // The JUMP_STRENGTH attribute is not reliable for normal player movement on all server/client paths.
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 100, Math.max(0, jumpBoost - 1), true, false, false));
        }
    }

    public static ItemStack create(String rarity) {
        String normalized = ProfessionFragmentConfig.normalizeRarity(rarity);
        String id = normalized.toLowerCase(Locale.ROOT) + "_running_shoes";
        Item item = REGISTERED.get(id);
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        double speed = rollSpeed(normalized);
        int jump = jump(normalized);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("champutils_running_shoes", true);
        tag.putString("rarity", normalized);
        tag.putDouble("speedPercent", speed);
        tag.putInt("jumpBoost", jump);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(modelData(normalized)));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(ProfessionFragmentManager.formatWords(normalized) + " Profession Boots").withStyle(color(normalized)));
        makeStackUnbreakable(stack);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("§8" + ProfessionFragmentManager.displayRankName(normalized) + " Profession Gear"));
        lore.add(Component.literal(" "));
        lore.add(Component.literal("§6Stats"));
        lore.add(Component.literal(" §a+" + String.format(Locale.US, "%.1f", speed) + "% Move Speed §8(range " + speedRange(normalized) + ")"));
        if (jump > 0) lore.add(Component.literal(" §a+" + jump + " Jump Height"));
        if (isFallImmune(normalized)) lore.add(Component.literal(" §aFall Damage Immune"));
        if (isDepthStrider(normalized)) lore.add(Component.literal(" §aDepth Strider"));
        lore.add(Component.literal(" "));
        lore.add(Component.literal("§8Unbreakable"));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        suppressRunningShoeGlint(stack);
        return stack;
    }

    public static boolean isRunningShoes(ItemStack stack) {
        return stack != null && !stack.isEmpty() && getShoeRarity(stack) != null;
    }

    public static String getShoeRarity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag tag = customData.copyTag();
        if (!tag.getBoolean("champutils_running_shoes")) return null;
        return ProfessionFragmentConfig.normalizeRarity(tag.getString("rarity"));
    }

    private static double getDouble(ItemStack stack, String key) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return 0.0D;
        return customData.copyTag().getDouble(key);
    }

    private static int getInt(ItemStack stack, String key) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return 0;
        return customData.copyTag().getInt(key);
    }

    private static void applyDepthStriderEnchantment(ServerPlayer player, ItemStack boots) {
        if (player == null || boots == null || boots.isEmpty()) return;
        String rarity = getShoeRarity(boots);
        int level = depthStriderLevel(rarity);
        if (level <= 0) return;

        try {
            Holder<Enchantment> depthStrider = player.registryAccess()
                    .registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.DEPTH_STRIDER);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(boots.getEnchantments());
            if (mutable.getLevel(depthStrider) != level) {
                mutable.set(depthStrider, level);
                boots.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
                suppressRunningShoeGlint(boots);
            }
        } catch (Throwable ignored) {
            // Registry failures should never break movement gear.
        }
    }

    private static void suppressRunningShoeGlint(ItemStack boots) {
        if (boots == null || boots.isEmpty() || !isRunningShoes(boots)) return;
        boots.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
    }

    private static int depthStriderLevel(String rarity) {
        return switch (ProfessionFragmentConfig.normalizeRarity(rarity)) {
            case "C", "B" -> 1;
            case "A" -> 2;
            case "S" -> 3;
            default -> 0;
        };
    }

    private static void makeStackUnbreakable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        stack.remove(DataComponents.MAX_DAMAGE);
        stack.remove(DataComponents.DAMAGE);
    }

    private static double rollSpeed(String rarity) {
        double min, max;
        switch (rarity) {
            case "E" -> { min = 10.0D; max = 15.0D; }
            case "D" -> { min = 15.0D; max = 20.0D; }
            case "C" -> { min = 20.0D; max = 25.0D; }
            case "B" -> { min = 25.0D; max = 32.5D; }
            case "A" -> { min = 32.5D; max = 42.5D; }
            case "S" -> { min = 42.5D; max = 55.0D; }
            default -> { min = 0.0D; max = 10.0D; }
        }
        return min + ThreadLocalRandom.current().nextDouble() * (max - min);
    }

    private static int jump(String rarity) {
        return switch (rarity) {
            case "D", "C" -> 1;
            case "B", "A" -> 2;
            case "S" -> 3;
            default -> 0;
        };
    }

    private static String speedRange(String rarity) {
        return switch (rarity) {
            case "E" -> "+10.0%-15.0%"; case "D" -> "+15.0%-20.0%"; case "C" -> "+20.0%-25.0%"; case "B" -> "+25.0%-32.5%"; case "A" -> "+32.5%-42.5%"; case "S" -> "+42.5%-55.0%"; default -> "+0.0%-10.0%";
        };
    }

    private static boolean isDepthStrider(String rarity) {
        return switch (rarity) { case "C", "B", "A", "S" -> true; default -> false; };
    }

    private static boolean isFallImmune(String rarity) {
        return switch (rarity) {
            case "D", "C", "B", "A", "S" -> true;
            default -> false;
        };
    }

    private static Holder<ArmorMaterial> armorMaterial(String rarity) {
        return switch (ProfessionFragmentConfig.normalizeRarity(rarity)) {
            case "E" -> ArmorMaterials.IRON;
            case "D", "C" -> ArmorMaterials.DIAMOND;
            case "B", "A", "S" -> ArmorMaterials.NETHERITE;
            default -> ArmorMaterials.LEATHER;
        };
    }

    private static int modelData(String rarity) {
        return switch (rarity) {
            case "E" -> 9962;
            case "D" -> 9963;
            case "C" -> 9964;
            case "B" -> 9965;
            case "A" -> 9966;
            case "S" -> 9967;
            default -> 9961;
        };
    }

    private static ChatFormatting color(String rarity) {
        return switch (rarity) {
            case "E" -> ChatFormatting.GREEN;
            case "D" -> ChatFormatting.BLUE;
            case "C" -> ChatFormatting.LIGHT_PURPLE;
            case "B" -> ChatFormatting.DARK_AQUA;
            case "A" -> ChatFormatting.GOLD;
            case "S" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }

    private static Rarity rarity(String rarity) {
        return switch (ProfessionFragmentConfig.normalizeRarity(rarity)) {
            case "E" -> Rarity.UNCOMMON;
            case "D" -> Rarity.RARE;
            case "C", "B", "A", "S" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }

    public static class RunningShoeItem extends ArmorItem implements PolymerItem {
        private final Item base;
        public RunningShoeItem(Item base, Holder<ArmorMaterial> material, Properties properties) {
            super(material, ArmorItem.Type.BOOTS, properties);
            this.base = base;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack held = player.getItemInHand(hand);
            if (!RunningShoeManager.isRunningShoes(held)) {
                return super.use(level, player, hand);
            }

            if (!level.isClientSide()) {
                ItemStack currentBoots = player.getItemBySlot(EquipmentSlot.FEET).copy();
                ItemStack shoesToEquip = held.copy();
                shoesToEquip.setCount(1);
                RunningShoeManager.makeStackUnbreakable(shoesToEquip);

                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }

                player.setItemSlot(EquipmentSlot.FEET, shoesToEquip);
                if (player instanceof ServerPlayer serverPlayer) {
                    RunningShoeManager.applyMovement(serverPlayer);
                }

                if (!currentBoots.isEmpty()) {
                    if (!player.getAbilities().instabuild && held.isEmpty()) {
                        player.setItemInHand(hand, currentBoots);
                    } else if (!player.getInventory().add(currentBoots)) {
                        player.drop(currentBoots, false);
                    }
                }
            }

            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }

        @Override public Item getPolymerItem(ItemStack stack, ServerPlayer player) { return base; }
    }
}
