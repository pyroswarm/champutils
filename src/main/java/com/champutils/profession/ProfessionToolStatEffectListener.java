package com.champutils.profession;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProfessionToolStatEffectListener {

    private static final ResourceLocation SHARPNESS_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath("champutils", "profession_sword_sharpness");

    private static final int UPDATE_INTERVAL_TICKS = 10;
    private static final Map<UUID, AppliedState> APPLIED = new ConcurrentHashMap<>();
    private static final List<PendingLootTriple> PENDING_LOOT_TRIPLES = new CopyOnWriteArrayList<>();
    private static final Set<UUID> PROCESSED_LOOT_ENTITIES = ConcurrentHashMap.newKeySet();

    private record AppliedState(String key, boolean sharpnessModifier) {}
    private record PendingLootTriple(ServerLevel level, BlockPos pos, int ticksLeft, double radius) {}

    private ProfessionToolStatEffectListener() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            Entity attacker = damageSource.getEntity();
            if (!(attacker instanceof ServerPlayer player) || !(entity.level() instanceof ServerLevel level)) return;

            ItemStack weapon = player.getMainHandItem();
            if (!(weapon.getItem() instanceof SwordItem) || !isUsableProfessionTool(player, weapon)) return;

            double lootingChance = ProfessionToolUtil.getStat(weapon, "lootingChance");
            if (lootingChance <= 0.0D) return;
            if (ThreadLocalRandom.current().nextDouble(100.0D) >= lootingChance) return;

            PENDING_LOOT_TRIPLES.add(new PendingLootTriple(level, entity.blockPosition().immutable(), 8, 2.5D));
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            processPendingLootTriples();

            if (server.getTickCount() % UPDATE_INTERVAL_TICKS != 0) return;

            Set<UUID> online = new HashSet<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                online.add(player.getUUID());
                updateMiningSpeedModifier(player);
            }
            APPLIED.keySet().removeIf(uuid -> !online.contains(uuid));
        });
    }


    private static void processPendingLootTriples() {
        if (PENDING_LOOT_TRIPLES.isEmpty()) {
            PROCESSED_LOOT_ENTITIES.clear();
            return;
        }

        for (PendingLootTriple pending : PENDING_LOOT_TRIPLES) {
            AABB box = new AABB(pending.pos()).inflate(pending.radius());
            for (Entity entity : pending.level().getEntities(null, box)) {
                if (!(entity instanceof ItemEntity itemEntity)) continue;
                if (itemEntity.tickCount > 40) continue;
                if (!PROCESSED_LOOT_ENTITIES.add(itemEntity.getUUID())) continue;

                ItemStack original = itemEntity.getItem();
                if (original == null || original.isEmpty()) continue;

                int bonusCount = Math.max(0, original.getCount() * 2);
                while (bonusCount > 0) {
                    ItemStack bonus = original.copy();
                    int count = Math.min(bonus.getMaxStackSize(), bonusCount);
                    bonus.setCount(count);
                    bonusCount -= count;

                    ItemEntity bonusEntity = new ItemEntity(
                            pending.level(),
                            itemEntity.getX(),
                            itemEntity.getY(),
                            itemEntity.getZ(),
                            bonus
                    );
                    bonusEntity.setDefaultPickUpDelay();
                    PROCESSED_LOOT_ENTITIES.add(bonusEntity.getUUID());
                    pending.level().addFreshEntity(bonusEntity);
                }
            }
        }

        PENDING_LOOT_TRIPLES.replaceAll(pending -> new PendingLootTriple(
                pending.level(),
                pending.pos(),
                pending.ticksLeft() - 1,
                pending.radius()
        ));
        PENDING_LOOT_TRIPLES.removeIf(pending -> pending.ticksLeft() <= 0);
    }

    private static void updateMiningSpeedModifier(ServerPlayer player) {
        AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null) return;

        ItemStack stack = player.getMainHandItem();

        /*
         * Keep the visible/proxied vanilla tool and the server-side custom tool
         * using a real Efficiency enchantment. Tools created by crates/loot can
         * already have an efficiencyLevel roll before any /itemroll command path
         * runs, so applying the enchant only from identify/reroll is not enough.
         *
         * This runs at the existing low-frequency tool stat tick and only mutates
         * profession tools when their real Efficiency component is missing or stale.
         */
        ProfessionToolManager.applyVanillaEfficiencyEnchant(player, stack);

        String key = stackKey(stack);
        UUID uuid = player.getUUID();
        AppliedState previous = APPLIED.get(uuid);
        if (previous != null && previous.key.equals(key)) return;

        if (previous != null && previous.sharpnessModifier) {
            attackDamage.removeModifier(SHARPNESS_MODIFIER_ID);
        } else {
            attackDamage.removeModifier(SHARPNESS_MODIFIER_ID);
        }

        boolean sharpnessModifier = false;

        if (!isUsableProfessionTool(player, stack)) {
            APPLIED.put(uuid, new AppliedState(key, false));
            return;
        }

        // Mining speed is not touched here. It is controlled only by the registered
        // vanilla Tier, the stack's real Efficiency enchantment, and vanilla Haste.
        if (stack.getItem() instanceof SwordItem) {
            double sharpnessPercent = ProfessionToolUtil.getStat(stack, "sharpnessPercent");
            if (sharpnessPercent > 0.0D) {
                attackDamage.addTransientModifier(new AttributeModifier(
                        SHARPNESS_MODIFIER_ID,
                        sharpnessPercent / 100.0D,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
                sharpnessModifier = true;
            }
        }

        APPLIED.put(uuid, new AppliedState(key, sharpnessModifier));
    }

    private static String stackKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        // The component string is only built twice per second now, instead of every tick. It catches tool stat changes,
        // identified/broken changes, and normal item swaps without constantly removing/readding attributes.
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + stack.getComponents().toString();
    }

    private static boolean isUsableProfessionTool(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !ProfessionToolMetadata.isProfessionTool(stack) ||
                !ProfessionToolMetadata.isIdentified(stack) || ProfessionToolMetadata.isBroken(stack)) {
            return false;
        }

        String toolId = ProfessionToolUtil.getToolId(stack);
        if (toolId == null) return false;

        ProfessionToolConfig.ToolData toolData = ProfessionToolConfig.TOOLS.get(toolId);
        if (toolData == null) return false;
        if (stack.getItem() instanceof SwordItem) return true;
        if (toolData.profession == null) return false;

        try {
            ProfessionType.valueOf(toolData.profession.toUpperCase());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

}
