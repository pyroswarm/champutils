package com.champutils.menu;

import com.champutils.emblem.EmblemConfig;
import com.champutils.emblem.EmblemManager;
import com.champutils.profession.ProfessionFragmentConfig;
import com.champutils.profession.ProfessionFragmentManager;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Map;

public final class EmblemMenu {

    private EmblemMenu() {}

    public static void open(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setTitle(Component.literal("Emblem Crafting"));
        MenuUtil.fillBorders(gui, 0,1,2,3,4,5,6,7,8,18,19,20,21,22,23,24,25,26);

        int slot = 10;
        for (Map.Entry<String, EmblemConfig.EmblemData> entry : EmblemConfig.CONFIG.emblems.entrySet()) {
            if (slot > 16) break;
            addButton(gui, player, slot++, entry.getKey(), entry.getValue());
        }

        MenuUtil.addBackButton(gui, 22, () -> GearWorkshopMenu.open(player));
        gui.open();
    }

    private static void addButton(SimpleGui gui, ServerPlayer player, int slot, String id, EmblemConfig.EmblemData data) {
        String fragment = ProfessionFragmentConfig.normalizeRarity(data.fragment);
        int fragmentCost = Math.max(0, data.fragmentCost);
        int available = ProfessionFragmentManager.countFragments(player, fragment);
        boolean canCraft = available >= fragmentCost;

        GuiElementBuilder builder = new GuiElementBuilder(resolveItem(data.baseItem))
                .hideDefaultTooltip()
                .setName(Component.literal(data.displayName == null ? id : data.displayName).withStyle(color(data.type), ChatFormatting.BOLD));

        if (data.lore != null && !data.lore.isBlank()) builder.addLoreLine(Component.literal("§7" + data.lore));
        builder.addLoreLine(Component.literal("§7Shard Cost: §6" + fragmentCost + "x " + ProfessionFragmentManager.formatWords(fragment) + " Fragment"));
        builder.addLoreLine(Component.literal("§7You have: §e" + available));
        if (data.itemCosts != null && !data.itemCosts.isEmpty()) {
            builder.addLoreLine(Component.literal("§7Rare Item Costs:"));
            for (EmblemConfig.ItemCost cost : data.itemCosts) builder.addLoreLine(Component.literal("§8- §f" + Math.max(0, cost.amount) + "x " + itemName(cost.item)));
        }
        builder.addLoreLine(Component.literal(canCraft ? "§eClick to craft" : "§cNot enough shards/items"));
        builder.setCallback((i, c, t) -> {
            player.closeContainer();
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "emblems craft " + id);
        });
        gui.setSlot(slot, builder);
    }

    private static String itemName(String id) {
        if (id == null || id.isBlank()) return "Unknown Item";
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String[] parts = path.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.length() == 0 ? id : out.toString();
    }

    private static Item resolveItem(String id) {
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            return item == null || item == Items.AIR ? Items.NETHER_STAR : item;
        } catch (Exception e) {
            return Items.NETHER_STAR;
        }
    }

    private static ChatFormatting color(String type) {
        if (type == null) return ChatFormatting.WHITE;
        return switch (type.toUpperCase()) {
            case "REGULAR_SHINY" -> ChatFormatting.GOLD;
            case "ULTRA_PARADOX_SHINY" -> ChatFormatting.LIGHT_PURPLE;
            case "LEGENDARY_SHINY" -> ChatFormatting.AQUA;
            case "MEGASTONE" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }
}
