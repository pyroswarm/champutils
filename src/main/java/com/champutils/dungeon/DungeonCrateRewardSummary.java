package com.champutils.dungeon;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DungeonCrateRewardSummary {

    public static final class RewardLine {
        private final ItemStack icon;
        private final Component title;
        private final Component detail;

        public RewardLine(ItemStack icon, Component title, Component detail) {
            this.icon = icon == null || icon.isEmpty() ? new ItemStack(Items.CHEST) : icon.copy();
            this.title = title == null ? Component.literal("Reward") : title;
            this.detail = detail == null ? Component.empty() : detail;
        }

        public ItemStack icon() {
            return icon.copy();
        }

        public Component title() {
            return title;
        }

        public Component detail() {
            return detail;
        }
    }

    private final Component title;
    private final DungeonRarity rarity;
    private final boolean pokemonCrate;
    private final List<RewardLine> rewards = new ArrayList<>();

    public DungeonCrateRewardSummary(Component title, DungeonRarity rarity, boolean pokemonCrate) {
        this.title = title == null ? Component.literal("Crate Rewards") : title;
        this.rarity = rarity == null ? DungeonRarity.COMMON : rarity;
        this.pokemonCrate = pokemonCrate;
    }

    public Component title() {
        return title;
    }

    public DungeonRarity rarity() {
        return rarity;
    }

    public boolean pokemonCrate() {
        return pokemonCrate;
    }

    public void add(ItemStack icon, Component title, Component detail) {
        rewards.add(new RewardLine(icon, title, detail));
    }

    public boolean isEmpty() {
        return rewards.isEmpty();
    }

    public List<RewardLine> rewards() {
        return Collections.unmodifiableList(rewards);
    }
}
