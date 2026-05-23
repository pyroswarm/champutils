package com.champutils.menu;

import com.champutils.matchmaking.PokemonIconUtil;
import com.champutils.hunt.PokemonHuntManager;
import com.champutils.hunt.PokemonHuntState;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.TimeUnit;

public final class PokemonHuntMenu {

    private PokemonHuntMenu() {}

    public static void open(ServerPlayer player) {
        PokemonHuntManager.ensureStarted(player.server);

        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("§bPokémon Hunts"));
        MenuUtil.fillBordersForced(gui, 10, 11, 12, 14, 15, 16, 22);

        List<PokemonHuntState.HuntEntry> hunts = PokemonHuntManager.sortedHunts();
        int[] slots = {10, 11, 12, 14, 15, 16};
        for (int i = 0; i < Math.min(slots.length, hunts.size()); i++) {
            PokemonHuntState.HuntEntry hunt = hunts.get(i);
            boolean done = hunt.claimed;
            ItemStack icon = PokemonIconUtil.createPokemonIcon(hunt.species, false, "minecraft:egg", false);
            GuiElementBuilder builder = new GuiElementBuilder(icon)
                    .hideDefaultTooltip()
                    .setName(Component.literal((done ? "§a" : "§e") + PokemonHuntManager.prettySpecies(hunt.species)))
                    .addLoreLine(Component.literal("§7First player to catch this exact target wins."))
                    .addLoreLine(Component.literal("§7Species: §f" + PokemonHuntManager.prettySpecies(hunt.species)))
                    .addLoreLine(Component.literal("§7Nature: §f" + PokemonHuntManager.prettyNature(hunt.nature)))
                    .addLoreLine(Component.literal("§7Gender: §f" + PokemonHuntManager.prettyGender(hunt.gender)))
                    .addLoreLine(Component.literal("§7Ability: §f" + PokemonHuntManager.prettyAbility(hunt.ability)))
                    .addLoreLine(Component.literal("§7Difficulty: §f" + (hunt.difficulty == null ? "Common" : hunt.difficulty)))
                    .addLoreLine(Component.literal(" "));

            if (done) {
                builder.addLoreLine(Component.literal("§aCompleted by §f" + hunt.winnerName));
                if (player.getUUID().toString().equals(hunt.winnerUuid) && !hunt.rewardClaimed) {
                    builder.addLoreLine(Component.literal("§eReward ready: §f/hunts claim"));
                } else if (hunt.rewardClaimed) {
                    builder.addLoreLine(Component.literal("§7Reward claimed."));
                }
            } else {
                builder.addLoreLine(Component.literal("§eStatus: §fAvailable"));
                builder.addLoreLine(Component.literal("§8Trades, evolutions, and Wondertrade do not count."));
            }

            gui.setSlot(slots[i], builder);
        }

        gui.setSlot(22, new GuiElementBuilder(Items.CLOCK)
                .hideDefaultTooltip()
                .setName(Component.literal("§bNext Refresh"))
                .addLoreLine(Component.literal("§7Refreshes in: §f" + formatDuration(PokemonHuntManager.millisUntilRefresh())))
                .addLoreLine(Component.literal("§7Use §e/hunts§7 anytime to check active hunts."))
        );

        gui.open();
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = TimeUnit.SECONDS.toHours(seconds);
        seconds -= TimeUnit.HOURS.toSeconds(hours);
        long minutes = TimeUnit.SECONDS.toMinutes(seconds);
        seconds -= TimeUnit.MINUTES.toSeconds(minutes);
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }
}
