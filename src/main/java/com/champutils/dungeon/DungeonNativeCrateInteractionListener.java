package com.champutils.dungeon;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DungeonNativeCrateInteractionListener {

    private static final Map<UUID, PendingBind> PENDING_BINDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> PENDING_UNBINDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_CLICK = new ConcurrentHashMap<>();
    private static final long CLICK_DEBOUNCE_MS = 750L;

    private DungeonNativeCrateInteractionListener() {
    }

    public static final class PendingBind {
        public final DungeonRarity rarity;
        public final DungeonNativeCrateRegistry.CrateType type;

        public PendingBind(DungeonRarity rarity, DungeonNativeCrateRegistry.CrateType type) {
            this.rarity = rarity == null ? DungeonRarity.COMMON : rarity;
            this.type = type == null ? DungeonNativeCrateRegistry.CrateType.NORMAL : type;
        }
    }

    public static void beginBind(ServerPlayer player, DungeonRarity rarity, DungeonNativeCrateRegistry.CrateType type) {
        if (player == null) return;
        PENDING_UNBINDS.remove(player.getUUID());
        PENDING_BINDS.put(player.getUUID(), new PendingBind(rarity, type));
        player.sendSystemMessage(Component.literal("Right-click the block to bind as a " + rarity.name() + " " + type.display() + " dungeon crate.").withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Use /dungeon crate cancel to cancel.").withStyle(ChatFormatting.GRAY));
    }

    public static void beginUnbind(ServerPlayer player) {
        if (player == null) return;
        PENDING_BINDS.remove(player.getUUID());
        PENDING_UNBINDS.put(player.getUUID(), true);
        player.sendSystemMessage(Component.literal("Right-click the dungeon crate block to unbind it.").withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Use /dungeon crate cancel to cancel.").withStyle(ChatFormatting.GRAY));
    }

    public static boolean cancel(ServerPlayer player) {
        if (player == null) return false;
        boolean a = PENDING_BINDS.remove(player.getUUID()) != null;
        boolean b = PENDING_UNBINDS.remove(player.getUUID()) != null;
        return a || b;
    }

    private static void playLocalSound(ServerLevel level, ServerPlayer player, BlockPos pos, String soundId, float volume, float pitch) {
        if (level == null || pos == null || soundId == null || soundId.isBlank()) return;
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(soundId));
        if (sound == null) return;
        level.playSound(
                player,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                sound,
                SoundSource.BLOCKS,
                volume,
                pitch
        );
    }

    public static void register() {

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide()) return true;
            if (!(world instanceof ServerLevel level)) return true;

            if (DungeonNativeCrateRegistry.getAt(level, pos) != null) {
                if (player instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.literal("Dungeon crates cannot be broken. Use /dungeon crate unbind first.").withStyle(ChatFormatting.RED));
                }
                return false;
            }

            // stale cleanup if somehow block vanished/desynced
            DungeonNativeCrateRegistry.cleanupStaleBinding(level, pos);
            return true;
        });


        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel level)) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            UUID uuid = serverPlayer.getUUID();

            PendingBind pending = PENDING_BINDS.remove(uuid);
            if (pending != null) {
                DungeonNativeCrateRegistry.CrateBinding binding = DungeonNativeCrateRegistry.bind(pending.rarity, pending.type, level, pos);
                if (binding == null) {
                    serverPlayer.sendSystemMessage(Component.literal("Could not bind dungeon crate here.").withStyle(ChatFormatting.RED));
                    return InteractionResult.SUCCESS;
                }
                serverPlayer.sendSystemMessage(Component.literal("Bound " + binding.name + " at " + pos.toShortString() + ".").withStyle(ChatFormatting.GREEN));
                playLocalSound(level, serverPlayer, pos, "minecraft:entity.experience_orb.pickup", 0.8F, 1.2F);
                return InteractionResult.SUCCESS;
            }

            if (PENDING_UNBINDS.remove(uuid) != null) {
                boolean removed = DungeonNativeCrateRegistry.unbind(level, pos);
                if (removed) {
                    serverPlayer.sendSystemMessage(Component.literal("Unbound dungeon crate at " + pos.toShortString() + ".").withStyle(ChatFormatting.GREEN));
                    playLocalSound(level, serverPlayer, pos, "minecraft:block.anvil.break", 0.5F, 1.4F);
                } else {
                    serverPlayer.sendSystemMessage(Component.literal("That block is not a bound dungeon crate.").withStyle(ChatFormatting.RED));
                }
                return InteractionResult.SUCCESS;
            }

            DungeonNativeCrateRegistry.CrateBinding crate = DungeonNativeCrateRegistry.getAt(world, pos);
            if (crate == null) return InteractionResult.PASS;

            long now = System.currentTimeMillis();
            Long previous = LAST_CLICK.get(uuid);
            if (previous != null && now - previous < CLICK_DEBOUNCE_MS) {
                return InteractionResult.SUCCESS;
            }
            LAST_CLICK.put(uuid, now);

            boolean started = DungeonCrateOpeningGui.open(serverPlayer, crate.rarity(), crate.type());
            if (started) {
                playLocalSound(level, serverPlayer, pos, "minecraft:block.chest.open", 0.8F, 1.0F);
            } else {
                playLocalSound(level, serverPlayer, pos, "minecraft:block.note_block.bass", 0.5F, 0.7F);
            }
            return InteractionResult.SUCCESS;
        });
    }
}
