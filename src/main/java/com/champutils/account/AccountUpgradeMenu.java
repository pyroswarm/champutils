package com.champutils.account;

import com.champutils.economy.EconomyManager;
import com.champutils.menu.MenuUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public final class AccountUpgradeMenu {
    private AccountUpgradeMenu() {}

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Account Upgrades"));
        MenuUtil.fillBordersForced(gui, 11, 13, 15, 22);

        gui.setSlot(13, new GuiElementBuilder(Items.EMERALD).hideDefaultTooltip()
                .setName(Component.literal("§aAccount Upgrades"))
                .addLoreLine(Component.literal("§7Spend in-game Credits to earn"))
                .addLoreLine(Component.literal("§7permanent LuckPerms ranks."))
                .addLoreLine(Component.literal("§7Balance: §6" + EconomyManager.format(EconomyManager.getBalance(player)))));

        addUpgrade(gui, player, 11, AccountUpgradeManager.Tier.VIP, Items.GOLD_INGOT);
        addUpgrade(gui, player, 15, AccountUpgradeManager.Tier.VIP_PLUS, Items.NETHERITE_INGOT);
        MenuUtil.addBackButton(gui, 22, () -> com.champutils.menu.MainMenu.open(player));
        gui.open();
    }

    private static void addUpgrade(SimpleGui gui, ServerPlayer player, int slot, AccountUpgradeManager.Tier tier, net.minecraft.world.item.Item icon) {
        AccountUpgradeConfig.Upgrade upgrade = tier == AccountUpgradeManager.Tier.VIP ? AccountUpgradeConfig.CONFIG.vip : AccountUpgradeConfig.CONFIG.vipPlus;
        boolean owned = tier == AccountUpgradeManager.Tier.VIP ? AccountUpgradeManager.hasVip(player) : AccountUpgradeManager.hasVipPlus(player);
        long price = AccountUpgradeConfig.priceCents(upgrade);
        GuiElementBuilder builder = new GuiElementBuilder(icon).hideDefaultTooltip()
                .setName(Component.literal((owned ? "§a" : "§6") + upgrade.displayName))
                .addLoreLine(Component.literal("§7Cost: §6" + EconomyManager.format(price)))
                .addLoreLine(Component.literal("§7LuckPerms group: §f" + upgrade.luckPermsGroup));

        if (tier == AccountUpgradeManager.Tier.VIP) {
            builder.addLoreLine(Component.literal("§7Unlocks: §f/pokeheal, /pc, /ec"));
        } else {
            builder.addLoreLine(Component.literal("§7Unlocks: §f/pokeivs"));
            builder.addLoreLine(Component.literal("§7Daily VIP+ booster credits: §fup to 3"));
            builder.addLoreLine(Component.literal("§7VIP+ earned credit cap: §f10"));
        }

        if (!upgrade.enabled) {
            builder.addLoreLine(Component.literal("§cThis upgrade is disabled."));
        } else if (owned) {
            builder.addLoreLine(Component.literal("§aAlready owned."));
        } else if (EconomyManager.getBalance(player) < price) {
            builder.addLoreLine(Component.literal("§cYou cannot afford this yet."));
        } else {
            builder.addLoreLine(Component.literal("§eClick to purchase."));
        }

        builder.setCallback((i, c, t) -> {
            if (owned || !upgrade.enabled) return;
            AccountUpgradeManager.PurchaseResult result = AccountUpgradeManager.purchase(player, tier);
            player.sendSystemMessage(Component.literal((result.success() ? "§a" : "§c") + result.message()));
            open(player);
        });
        gui.setSlot(slot, builder);
    }
}
