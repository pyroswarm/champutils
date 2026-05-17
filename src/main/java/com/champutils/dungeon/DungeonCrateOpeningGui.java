package com.champutils.dungeon;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DungeonCrateOpeningGui {

    private static final Map<UUID, Opening> OPENINGS = new ConcurrentHashMap<>();
    private static final int[] SPIN_SLOTS = new int[]{9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int CENTER_SLOT = 13;
    private static final int CENTER_INDEX_IN_REEL = 4;
    private static final int SPIN_END_TICKS = 64;
    private static final int TOTAL_TICKS = 96;
    private static final int REVEAL_SLOT_BELOW_CENTER = 22;

    private DungeonCrateOpeningGui() {
    }

    private static final class Opening {
        final UUID playerId;
        final DungeonRarity rarity;
        final DungeonNativeCrateRegistry.CrateType type;
        final SimpleGui gui;
        final DungeonRewardManager.PlannedCrateReward plan;
        final List<DungeonCrateRewardSummary.RewardLine> spinRewards;
        int tick;
        int offset;

        Opening(ServerPlayer player, DungeonRewardManager.PlannedCrateReward plan, SimpleGui gui) {
            this.playerId = player.getUUID();
            this.rarity = plan == null ? DungeonRarity.COMMON : plan.rarity();
            this.type = plan == null ? DungeonNativeCrateRegistry.CrateType.NORMAL : plan.type();
            this.gui = gui;
            this.plan = plan;
            this.spinRewards = buildSpinRewards(plan);
        }
    }

    public static boolean open(ServerPlayer player, DungeonRarity rarity, DungeonNativeCrateRegistry.CrateType type) {
        if (player == null) return false;
        DungeonRarity safeRarity = rarity == null ? DungeonRarity.COMMON : rarity;
        DungeonNativeCrateRegistry.CrateType safeType = type == null ? DungeonNativeCrateRegistry.CrateType.NORMAL : type;

        if (safeType == DungeonNativeCrateRegistry.CrateType.POKEMON) {
            int credits = DungeonCrateCreditManager.getPokemonCredits(player.getUUID(), safeRarity);
            if (credits <= 0) {
                player.sendSystemMessage(Component.literal("You have no " + nice(safeRarity.name()) + " Pokemon Crate credits.").withStyle(ChatFormatting.RED));
                return false;
            }
        } else {
            int credits = DungeonCrateCreditManager.getNormalCredits(player.getUUID(), safeRarity);
            if (credits <= 0) {
                player.sendSystemMessage(Component.literal("You have no " + nice(safeRarity.name()) + " Reward Crate credits.").withStyle(ChatFormatting.RED));
                return false;
            }
        }

        DungeonRewardManager.PlannedCrateReward plan = DungeonRewardManager.planCrateReward(safeRarity, safeType);
        if (plan == null) {
            player.sendSystemMessage(Component.literal("Could not prepare crate reward. Check the crate config.").withStyle(ChatFormatting.RED));
            return false;
        }

        OPENINGS.remove(player.getUUID());

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setTitle(Component.literal(nice(safeRarity.name()) + (safeType == DungeonNativeCrateRegistry.CrateType.POKEMON ? " Pokemon Crate" : " Loot Crate")));

        for (int i = 0; i < gui.getSize(); i++) {
            gui.setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Component.literal(" ")));
        }

        DungeonCrateRewardSummary.RewardLine finalReward = plan.finalReward();
        gui.setSlot(4, new GuiElementBuilder(safeType == DungeonNativeCrateRegistry.CrateType.POKEMON ? Items.DRAGON_EGG : Items.CHEST)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Opening...").withStyle(ChatFormatting.BOLD))
                .addLoreLine(Component.literal("§7The center slot will land on your real reward."))
                .addLoreLine(Component.literal("§8Final: ").append(finalReward.title())));

        gui.open();

        Opening opening = new Opening(player, plan, gui);
        OPENINGS.put(player.getUUID(), opening);
        updateSpin(opening, false);
        playLocalSound(player, "minecraft:ui.button.click", 0.6F, 1.2F);
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || OPENINGS.isEmpty()) return;

        Iterator<Map.Entry<UUID, Opening>> iterator = OPENINGS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Opening> entry = iterator.next();
            Opening opening = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(opening.playerId);
            if (player == null) {
                iterator.remove();
                continue;
            }

            opening.tick++;

            boolean finalLock = opening.tick >= SPIN_END_TICKS;
            int speed = opening.tick < 26 ? 2 : opening.tick < 46 ? 4 : 6;
            if (finalLock || opening.tick % speed == 0) {
                if (!finalLock) {
                    opening.offset++;
                }
                updateSpin(opening, finalLock);
                if (opening.tick == SPIN_END_TICKS) {
                    playLocalSound(player, "minecraft:entity.player.levelup", 0.7F, 1.4F);
                } else if (!finalLock) {
                    playCrateTickSound(player, opening.tick);
                }
            }

            if (opening.tick >= TOTAL_TICKS) {
                iterator.remove();
                player.closeContainer();

                boolean opened = DungeonRewardManager.grantPlannedCrateReward(player, opening.plan);
                if (opened) {
                    playLocalSound(player, "minecraft:ui.toast.challenge_complete", 0.8F, 1.0F);
                }
            }
        }
    }

    public static void openRewardSummary(ServerPlayer player, DungeonCrateRewardSummary summary) {
        if (player == null || summary == null) return;

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setTitle(summary.title());

        for (int i = 0; i < gui.getSize(); i++) {
            gui.setSlot(i, new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE).setName(Component.literal(" ")));
        }

        gui.setSlot(4, new GuiElementBuilder(summary.pokemonCrate() ? Items.DRAGON_EGG : Items.CHEST)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Rewards Received").withStyle(ChatFormatting.BOLD))
                .addLoreLine(Component.literal("§7" + nice(summary.rarity().name()) + (summary.pokemonCrate() ? " Pokemon Crate" : " Loot Crate"))));

        int[] rewardSlots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        List<DungeonCrateRewardSummary.RewardLine> rewards = summary.rewards();
        if (rewards.isEmpty()) {
            gui.setSlot(13, new GuiElementBuilder(Items.BARRIER)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§cNo rewards displayed"))
                    .addLoreLine(Component.literal("§7Check the crate reward config.")));
        } else {
            for (int i = 0; i < rewards.size() && i < rewardSlots.length; i++) {
                DungeonCrateRewardSummary.RewardLine line = rewards.get(i);
                gui.setSlot(rewardSlots[i], rewardElement(line)
                        .hideDefaultTooltip()
                        .setName(line.title())
                        .addLoreLine(line.detail()));
            }
        }

        gui.setSlot(26, new GuiElementBuilder(Items.BARRIER)
                .hideDefaultTooltip()
                .setName(Component.literal("§cClose"))
                .setCallback((i, c, t) -> player.closeContainer()));

        gui.open();
    }

    private static void updateSpin(Opening opening, boolean finalLock) {
        DungeonCrateRewardSummary.RewardLine finalReward = opening.plan.finalReward();

        for (int i = 0; i < SPIN_SLOTS.length; i++) {
            boolean center = SPIN_SLOTS[i] == CENTER_SLOT;
            DungeonCrateRewardSummary.RewardLine line;
            if (finalLock && center) {
                line = finalReward;
            } else {
                line = opening.spinRewards.get((opening.offset + i) % opening.spinRewards.size());
            }

            opening.gui.setSlot(SPIN_SLOTS[i], rewardElement(line)
                    .hideDefaultTooltip()
                    .setName(center && finalLock ? Component.literal("§e§lYOUR REWARD").append(Component.literal(" - ")).append(line.title()) : line.title())
                    .addLoreLine(center && finalLock ? Component.literal("§aThis is what you won.") : Component.literal("§8Rolling..."))
                    .addLoreLine(line.detail()));
        }

        if (finalLock) {
            opening.gui.setSlot(REVEAL_SLOT_BELOW_CENTER, new GuiElementBuilder(Items.LIME_STAINED_GLASS_PANE)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§a§lWINNING REWARD"))
                    .addLoreLine(finalReward.title()));
        } else {
            opening.gui.setSlot(REVEAL_SLOT_BELOW_CENTER, new GuiElementBuilder(Items.YELLOW_STAINED_GLASS_PANE)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§eLanding slot")));
        }
    }

    private static GuiElementBuilder rewardElement(DungeonCrateRewardSummary.RewardLine line) {
        ItemStack stack = line == null || line.icon() == null || line.icon().isEmpty() ? new ItemStack(Items.BARRIER) : line.icon().copy();
        try {
            return new GuiElementBuilder(stack);
        } catch (Throwable ignored) {
            return new GuiElementBuilder(stack.getItem());
        }
    }

    private static List<DungeonCrateRewardSummary.RewardLine> buildSpinRewards(DungeonRewardManager.PlannedCrateReward plan) {
        List<DungeonCrateRewardSummary.RewardLine> rewards = new ArrayList<>();
        if (plan != null && plan.spinRewards() != null && !plan.spinRewards().isEmpty()) {
            rewards.addAll(plan.spinRewards());
        }

        DungeonCrateRewardSummary.RewardLine finalReward = plan == null ? null : plan.finalReward();
        if (rewards.isEmpty()) {
            rewards.add(new DungeonCrateRewardSummary.RewardLine(
                    new ItemStack(plan != null && plan.type() == DungeonNativeCrateRegistry.CrateType.POKEMON ? Items.EGG : Items.CHEST),
                    Component.literal("Crate Reward").withStyle(ChatFormatting.GOLD),
                    Component.literal("Opening...").withStyle(ChatFormatting.GRAY)
            ));
        }

        // Build a deterministic reel. The final reward is placed at the exact index that will be
        // under the center slot on the final real movement frame. This prevents the old clunky
        // end-swap where the GUI showed one item and then suddenly changed it to the real reward.
        List<DungeonCrateRewardSummary.RewardLine> expanded = new ArrayList<>();
        while (expanded.size() < 36) {
            expanded.addAll(rewards);
        }

        if (finalReward != null) {
            int finalOffset = calculateFinalOffsetBeforeLock();
            int landingIndex = Math.floorMod(finalOffset + CENTER_INDEX_IN_REEL, expanded.size());
            expanded.set(landingIndex, finalReward);
        }

        return expanded;
    }

    private static int calculateFinalOffsetBeforeLock() {
        int advances = 0;
        for (int tick = 1; tick < SPIN_END_TICKS; tick++) {
            int speed = tick < 26 ? 2 : tick < 46 ? 4 : 6;
            if (tick % speed == 0) {
                advances++;
            }
        }
        return advances;
    }

    private static void playCrateTickSound(ServerPlayer player, int tick) {
        // CS2-style tick each time the reel advances past an item.
        float pitch = Math.min(1.85F, 0.85F + (tick / 70.0F));
        playLocalSound(player, "minecraft:block.note_block.hat", 0.45F, pitch);
    }

    private static void playLocalSound(ServerPlayer player, String soundId, float volume, float pitch) {
        if (player == null || soundId == null || soundId.isBlank()) return;
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(soundId));
        if (sound == null) return;
        player.playNotifySound(sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static String nice(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        String lower = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        String[] words = lower.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }
}
