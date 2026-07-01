package com.champutils.expeditions;

import com.champutils.economy.EconomyManager;
import com.champutils.matchmaking.PokemonIconUtil;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

public final class ExpeditionMenu {
    private ExpeditionMenu() {}

    public static void open(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x5, player, false);
        gui.setTitle(Component.literal("Expeditions"));

        if (ExpeditionManager.hasActive(player)) {
            gui.setSlot(4, new GuiElementBuilder(Items.CLOCK)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§6Active Expedition"))
                    .addLoreLine(Component.literal("§7" + ExpeditionManager.activeStatusText(player)))
                    .addLoreLine(Component.literal("§7Click to claim if it is ready."))
                    .setCallback((slot, click, action) -> { ExpeditionManager.claim(player); open(player); }));
        } else {
            gui.setSlot(4, new GuiElementBuilder(Items.MAP)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§aChoose a Pokémon"))
                    .addLoreLine(Component.literal("§7Click one of your party Pokémon below"))
                    .addLoreLine(Component.literal("§7to preview time and rewards.")));
        }

        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int[] slots = {19, 20, 21, 23, 24, 25};
        for (int i = 0; i < slots.length; i++) {
            Pokemon pokemon = party == null ? null : party.get(i);
            int partySlot = i + 1;
            if (pokemon == null) {
                gui.setSlot(slots[i], new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE).hideDefaultTooltip()
                        .setName(Component.literal("§7Empty Party Slot " + partySlot)));
                continue;
            }
            ExpeditionConfig.Tier tier = ExpeditionConfig.tier(pokemon.getLevel());
            ItemStack icon = PokemonIconUtil.getIcon(pokemon, partySlot, false);
            GuiElementBuilder button = new GuiElementBuilder(icon)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§e" + pokemon.getDisplayName(true).getString()))
                    .addLoreLine(Component.literal("§7Level: §f" + pokemon.getLevel()))
                    .addLoreLine(Component.literal("§7Gone for: §b" + Math.max(1, tier.hours) + " hour(s)"))
                    .addLoreLine(Component.literal("§7Online speed: §a2x §8(" + Math.max(1, Math.max(1, tier.hours) / 2) + "h+ online effective)"))
                    .addLoreLine(Component.literal("§7Credits: §6" + EconomyManager.format(tier.credits)))
                    .addLoreLine(Component.literal("§7Rewards:"));
            addRewardLore(button, pokemon.getLevel());
            button.addLoreLine(Component.literal("§eClick to choose expedition type"))
                    .setCallback((slot, click, action) -> ExpeditionCommand.preview(player, partySlot));
            gui.setSlot(slots[i], button);
        }

        gui.open();
    }

    public static void chooseType(ServerPlayer player, int partySlot, Pokemon pokemon) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setTitle(Component.literal("Choose Expedition Type"));
        typeButton(gui, player, 10, partySlot, "pokeball", CobblemonItems.POKE_BALL, "§cPoké Ball Expedition", "§7Balls scale with Battling level.");
        typeButton(gui, player, 11, partySlot, "held_item", CobblemonItems.LUCKY_EGG, "§6Held Item Expedition", "§7Held item rewards scale up.");
        typeButton(gui, player, 12, partySlot, "candy", Items.SUGAR, "§dCandy Expedition", "§7XP candy rewards scale up.");
        typeButton(gui, player, 13, partySlot, "tm", Items.PAPER, "§bTM Expedition", "§7TM materials and tech rewards.");
        typeButton(gui, player, 14, partySlot, "pokemon", Items.EGG, "§aPokémon Expedition", "§7Can find a random Pokémon.");
        typeButton(gui, player, 15, partySlot, "general", Items.MAP, "§eGeneral Expedition", "§7Classic mixed rewards.");
        gui.setSlot(22, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eGo Back")).setCallback((slot, click, action) -> open(player)));
        gui.open();
    }

    private static void typeButton(SimpleGui gui, ServerPlayer player, int slot, int partySlot, String type, net.minecraft.world.item.Item icon, String name, String lore) {
        gui.setSlot(slot, new GuiElementBuilder(icon).hideDefaultTooltip()
                .setName(Component.literal(name))
                .addLoreLine(Component.literal(lore))
                .addLoreLine(Component.literal("§eClick to preview"))
                .setCallback((i, c, t) -> ExpeditionCommand.previewType(player, partySlot, type)));
    }

    public static void preview(ServerPlayer player, int partySlot, Pokemon pokemon, long endsAt, String type) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setTitle(Component.literal("Confirm Expedition"));
        ExpeditionConfig.Tier tier = ExpeditionConfig.tier(pokemon.getLevel());
        ItemStack icon = PokemonIconUtil.getIcon(pokemon, partySlot, true);
        GuiElementBuilder summary = new GuiElementBuilder(icon)
                .hideDefaultTooltip()
                .setName(Component.literal("§e" + pokemon.getDisplayName(true).getString()))
                .addLoreLine(Component.literal("§7Party Slot: §f" + partySlot))
                .addLoreLine(Component.literal("§7Type: §e" + ExpeditionConfig.normalizeType(type)))
                .addLoreLine(Component.literal("§7Gone for: §b" + Math.max(1, tier.hours) + " hour(s)"))
                .addLoreLine(Component.literal("§7Online speed: §a2x §8(online time counts double)"))
                .addLoreLine(Component.literal("§7Returns at: §f" + relativeTime(endsAt)))
                .addLoreLine(Component.literal("§7Credits: §6" + EconomyManager.format(tier.credits)))
                .addLoreLine(Component.literal("§7Rewards:"));
        addRewardLore(summary, pokemon.getLevel(), type, com.champutils.profession.ProfessionManager.getLevel(player, com.champutils.profession.ProfessionType.BATTLING));
        gui.setSlot(13, summary);
        gui.setSlot(11, new GuiElementBuilder(Items.LIME_WOOL).hideDefaultTooltip()
                .setName(Component.literal("§aStart Expedition"))
                .addLoreLine(Component.literal("§7This removes the Pokémon from your party"))
                .addLoreLine(Component.literal("§7until the expedition is claimed."))
                .setCallback((slot, click, action) -> ExpeditionCommand.confirm(player)));
        gui.setSlot(15, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip()
                .setName(Component.literal("§eGo Back"))
                .setCallback((slot, click, action) -> open(player)));
        gui.open();
    }

    private static void addRewardLore(GuiElementBuilder button, int level) {
        addRewardLore(button, level, "general", 1);
    }

    private static void addRewardLore(GuiElementBuilder button, int level, String type, int battlingLevel) {
        for (ItemStack stack : ExpeditionConfig.itemStacks(level, battlingLevel, type)) {
            if (stack == null || stack.isEmpty()) continue;
            button.addLoreLine(Component.literal("§8• §f" + stack.getCount() + "x " + stack.getHoverName().getString()));
        }
        List<ExpeditionConfig.ChunkReward> chunks = ExpeditionConfig.chunkRewards(level);
        for (ExpeditionConfig.ChunkReward chunk : chunks) {
            if (chunk == null || chunk.chunk == null || chunk.chunk.isBlank()) continue;
            button.addLoreLine(Component.literal("§8• §6" + Math.max(1, chunk.amount) + "x " + chunk.chunk.toLowerCase(Locale.ROOT) + " chunk"));
        }
    }

    private static String relativeTime(long millis) {
        long remaining = Math.max(0L, millis - System.currentTimeMillis()) / 1000L;
        long minutes = (remaining + 59L) / 60L;
        if (minutes < 60L) return minutes + "m from now";
        long hours = (minutes + 59L) / 60L;
        return hours + "h from now";
    }
}
