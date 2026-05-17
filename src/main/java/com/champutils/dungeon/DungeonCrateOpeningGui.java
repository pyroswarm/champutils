package com.champutils.dungeon;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
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
    private static final int TOTAL_TICKS = 64;

    private DungeonCrateOpeningGui() {
    }

    private static final class Opening {
        final UUID playerId;
        final DungeonRarity rarity;
        final DungeonNativeCrateRegistry.CrateType type;
        final SimpleGui gui;
        final List<Item> icons;
        int tick;
        int offset;

        Opening(ServerPlayer player, DungeonRarity rarity, DungeonNativeCrateRegistry.CrateType type, SimpleGui gui, List<Item> icons) {
            this.playerId = player.getUUID();
            this.rarity = rarity == null ? DungeonRarity.COMMON : rarity;
            this.type = type == null ? DungeonNativeCrateRegistry.CrateType.NORMAL : type;
            this.gui = gui;
            this.icons = icons == null || icons.isEmpty() ? defaultIcons(this.type) : icons;
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

        OPENINGS.remove(player.getUUID());

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setTitle(Component.literal(nice(safeRarity.name()) + (safeType == DungeonNativeCrateRegistry.CrateType.POKEMON ? " Pokemon Crate" : " Loot Crate")));

        for (int i = 0; i < gui.getSize(); i++) {
            gui.setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Component.literal(" ")));
        }

        gui.setSlot(4, new GuiElementBuilder(safeType == DungeonNativeCrateRegistry.CrateType.POKEMON ? Items.DRAGON_EGG : Items.CHEST)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Opening...").withStyle(ChatFormatting.BOLD))
                .addLoreLine(Component.literal("§7The reward will land in the center.")));

        gui.open();

        Opening opening = new Opening(player, safeRarity, safeType, gui, defaultIcons(safeType));
        OPENINGS.put(player.getUUID(), opening);
        updateSpin(player, opening);
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

            int speed = opening.tick < 26 ? 2 : opening.tick < 46 ? 4 : 6;
            if (opening.tick % speed == 0) {
                opening.offset++;
                updateSpin(player, opening);
                playLocalSound(player, "minecraft:block.note_block.hat", 0.25F, 1.0F + (opening.tick / 80.0F));
            }

            if (opening.tick >= TOTAL_TICKS) {
                iterator.remove();
                player.closeContainer();

                boolean opened;
                if (opening.type == DungeonNativeCrateRegistry.CrateType.POKEMON) {
                    opened = DungeonRewardManager.openPendingPokemonChest(player, opening.rarity);
                } else {
                    opened = DungeonRewardManager.openPendingDungeonChest(player, opening.rarity);
                }

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
                Item icon = line.icon().getItem();
                gui.setSlot(rewardSlots[i], new GuiElementBuilder(icon)
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

    private static void updateSpin(ServerPlayer player, Opening opening) {
        for (int i = 0; i < SPIN_SLOTS.length; i++) {
            Item icon = opening.icons.get((opening.offset + i) % opening.icons.size());
            boolean center = SPIN_SLOTS[i] == CENTER_SLOT;
            opening.gui.setSlot(SPIN_SLOTS[i], new GuiElementBuilder(icon)
                    .hideDefaultTooltip()
                    .setName(Component.literal(center ? "§e§l???" : "§7???"))
                    .addLoreLine(Component.literal(center ? "§6Final reward lands here" : "§8Rolling...")));
        }
    }

    private static void playLocalSound(ServerPlayer player, String soundId, float volume, float pitch) {
        if (player == null || soundId == null || soundId.isBlank()) return;
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(soundId));
        if (sound == null) return;
        player.level().playSound(
                player,
                player.getX(),
                player.getY(),
                player.getZ(),
                sound,
                SoundSource.PLAYERS,
                volume,
                pitch
        );
    }

    private static List<Item> defaultIcons(DungeonNativeCrateRegistry.CrateType type) {
        List<Item> icons = new ArrayList<>();
        if (type == DungeonNativeCrateRegistry.CrateType.POKEMON) {
            icons.add(Items.EGG);
            icons.add(Items.DRAGON_EGG);
            icons.add(Items.NETHER_STAR);
            icons.add(Items.ENDER_EYE);
            icons.add(Items.AMETHYST_SHARD);
            icons.add(Items.GOLDEN_APPLE);
            icons.add(Items.EMERALD);
        } else {
            icons.add(Items.CHEST);
            icons.add(Items.AMETHYST_SHARD);
            icons.add(Items.DIAMOND);
            icons.add(Items.EMERALD);
            icons.add(Items.NETHERITE_SCRAP);
            icons.add(Items.NETHER_STAR);
            icons.add(Items.ENCHANTED_BOOK);
            icons.add(Items.GOLDEN_APPLE);
        }
        return icons;
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
