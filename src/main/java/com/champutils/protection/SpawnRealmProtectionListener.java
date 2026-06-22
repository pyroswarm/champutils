package com.champutils.protection;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnRealmProtectionListener {
    private static final Map<UUID, Long> LAST_WARNING = new ConcurrentHashMap<>();
    private static final long WARNING_COOLDOWN_MS = 5000L;

    private SpawnRealmProtectionListener() {}

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return true;
            if (!isSpawn1(level) || SpawnEditCommand.canEdit(sp)) return true;
            deny(sp, "Spawn is protected.");
            return false;
        });



        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (!isSpawn1(level) || SpawnEditCommand.canEdit(sp)) return InteractionResult.PASS;
            deny(sp, "Spawn is protected.");
            return InteractionResult.FAIL;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (!isSpawn1(level) || SpawnEditCommand.canEdit(sp)) return InteractionResult.PASS;
            if (isAllowedEntityInteraction(entity)) return InteractionResult.PASS;
            deny(sp, "Entities are protected in spawn.");
            return InteractionResult.FAIL;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (!isSpawn1(level) || SpawnEditCommand.canEdit(sp)) return InteractionResult.PASS;
            if (isAllowedEntityInteraction(entity)) return InteractionResult.PASS;
            deny(sp, "Only NPCs can be used in spawn.");
            return InteractionResult.FAIL;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (!isSpawn1(level) || SpawnEditCommand.canEdit(sp)) return InteractionResult.PASS;

            ItemStack stack = sp.getItemInHand(hand);
            if (isPlacementItem(stack)) {
                deny(sp, "You cannot place blocks in spawn.");
                return InteractionResult.FAIL;
            }

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (isAllowedInteraction(state)) return InteractionResult.PASS;

            deny(sp, "Only NPCs, healers, and PCs can be used in spawn.");
            return InteractionResult.FAIL;
        });

        // Some placeable items (especially off-hand torches / fluids / spawn eggs) can bypass
        // normal block interaction paths on Fabric. Block item-use in spawn too unless /spawnedit is enabled.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) {
                return net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));
            }
            if (!isSpawn1(level) || SpawnEditCommand.canEdit(sp)) {
                return net.minecraft.world.InteractionResultHolder.pass(sp.getItemInHand(hand));
            }
            ItemStack stack = sp.getItemInHand(hand);
            if (isPlacementItem(stack)) {
                deny(sp, "You cannot place blocks in spawn.");
                return net.minecraft.world.InteractionResultHolder.fail(stack);
            }
            return net.minecraft.world.InteractionResultHolder.pass(stack);
        });

    }


    public static boolean canPlaceBlock(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) return true;
        if (!isSpawn1(level) || SpawnEditCommand.canEdit(player)) return true;
        deny(player, "You cannot place blocks in spawn.");
        return false;
    }

    public static boolean isSpawn1(ServerLevel level) {
        String id = level.dimension().location().toString().toLowerCase(Locale.ROOT);
        return id.equals("multiworld:spawn1") || id.equals("minecraft:spawn1") || id.equals("spawn1") || id.endsWith(":spawn1");
    }


    private static boolean isPlacementItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof BlockItem) return true;
        return item == Items.WATER_BUCKET
                || item == Items.LAVA_BUCKET
                || item == Items.POWDER_SNOW_BUCKET
                || item == Items.FLINT_AND_STEEL
                || item == Items.FIRE_CHARGE
                || item == Items.ARMOR_STAND
                || item == Items.ITEM_FRAME
                || item == Items.GLOW_ITEM_FRAME
                || item == Items.PAINTING;
    }

    private static boolean isAllowedEntityInteraction(Entity entity) {
        if (entity == null) return false;
        String type = EntityType.getKey(entity.getType()).toString().toLowerCase(Locale.ROOT);
        return type.contains("npc") || type.contains("cobblemon:npc") || type.contains("pokemon");
    }

    private static boolean isAllowedInteraction(BlockState state) {
        String id = state.getBlock().builtInRegistryHolder().key().location().toString().toLowerCase(Locale.ROOT);
        return id.contains("healing_machine") || id.endsWith(":pc") || id.contains("pokemon_pc") || id.endsWith(":ender_chest");
    }

    private static void deny(ServerPlayer player, String msg) {
        if (player == null) return;
        long now = System.currentTimeMillis();
        Long last = LAST_WARNING.get(player.getUUID());
        if (last != null && now - last < WARNING_COOLDOWN_MS) return;
        LAST_WARNING.put(player.getUUID(), now);
        player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
    }
}
