package com.champutils.shop;

import com.champutils.dungeon.DungeonCrateCreditManager;
import com.champutils.dungeon.DungeonRarity;
import com.champutils.economy.EconomyManager;
import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class NpcShopService {

    private static final Random RANDOM = new Random();

    private NpcShopService() {
    }

    public static void buy(ServerPlayer player, NpcShopConfig.ShopEntry entry) {
        if (player == null || entry == null) {
            return;
        }

        long price = Math.max(0L, entry.price);
        if (price > 0L) {
            EconomyManager.TransactionResult result = EconomyManager.withdraw(player, price, "NPC shop purchase: " + safeName(entry));
            if (!result.success) {
                player.sendSystemMessage(Component.literal(result.error == null ? "You cannot afford that." : result.error).withStyle(ChatFormatting.RED));
                return;
            }
        }

        boolean success = switch (normalize(entry.type)) {
            case "tool" -> giveTool(player, entry);
            case "crate_credit" -> giveCrateCredit(player, entry);
            case "command" -> runCommands(player, entry);
            case "item" -> giveItem(player, entry);
            default -> false;
        };

        if (!success) {
            if (price > 0L) {
                EconomyManager.deposit(player, price, "NPC shop refund: " + safeName(entry));
            }
            player.sendSystemMessage(Component.literal("That shop item is not configured correctly. No credits were spent.").withStyle(ChatFormatting.RED));
            return;
        }

        player.sendSystemMessage(Component.literal("Purchased " + stripColor(safeName(entry)) + " for " + EconomyManager.format(price) + " Credits.").withStyle(ChatFormatting.GREEN));
    }

    private static boolean giveItem(ServerPlayer player, NpcShopConfig.ShopEntry entry) {
        Item item = resolveItem(entry.id);
        if (item == Items.AIR) {
            return false;
        }

        int amount = Math.max(1, entry.amount);
        int max = Math.max(1, item.getDefaultMaxStackSize());

        while (amount > 0) {
            int give = Math.min(max, amount);
            giveOrDrop(player, new ItemStack(item, give));
            amount -= give;
        }
        return true;
    }

    private static boolean giveTool(ServerPlayer player, NpcShopConfig.ShopEntry entry) {
        List<String> candidates = findToolCandidates(entry.rarity, entry.toolType);
        if (candidates.isEmpty()) {
            return false;
        }

        String selected = candidates.get(RANDOM.nextInt(candidates.size()));
        ItemStack stack = ProfessionToolManager.createTool(selected, false);
        if (stack.isEmpty()) {
            return false;
        }

        giveOrDrop(player, stack);
        return true;
    }

    private static boolean giveCrateCredit(ServerPlayer player, NpcShopConfig.ShopEntry entry) {
        DungeonRarity rarity;
        try {
            rarity = DungeonRarity.valueOf(entry.crateRarity.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            return false;
        }

        int amount = Math.max(1, entry.amount);
        if (entry.pokemonCrate) {
            DungeonCrateCreditManager.grantCredits(player.getUUID(), rarity, 0, amount);
        } else {
            DungeonCrateCreditManager.grantCredits(player.getUUID(), rarity, amount, 0);
        }
        return true;
    }

    private static boolean runCommands(ServerPlayer player, NpcShopConfig.ShopEntry entry) {
        if (player.getServer() == null || entry.commands == null || entry.commands.isEmpty()) {
            return false;
        }

        for (String raw : entry.commands) {
            if (raw == null || raw.isBlank()) {
                continue;
            }

            String command = raw.replace("%player%", player.getName().getString());
            player.getServer().getCommands().performPrefixedCommand(
                    player.getServer().createCommandSourceStack(),
                    command
            );
        }

        return true;
    }

    public static List<String> findToolCandidates(String rarity, String toolType) {
        String wantedRarity = normalize(rarity);
        String wantedType = normalize(toolType);
        List<String> candidates = new ArrayList<>();

        for (Map.Entry<String, ProfessionToolConfig.ToolData> mapEntry : ProfessionToolConfig.TOOLS.entrySet()) {
            ProfessionToolConfig.ToolData data = mapEntry.getValue();
            if (data == null) continue;
            if (!normalize(data.rarity).equals(wantedRarity)) continue;

            String base = data.baseItem == null ? "" : data.baseItem.toLowerCase(Locale.ROOT);
            if (base.contains(wantedType)) {
                candidates.add(mapEntry.getKey());
            }
        }

        return candidates;
    }

    public static Item resolveItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Items.AIR;
        }

        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId.trim()));
            return item == null ? Items.AIR : item;
        } catch (Exception exception) {
            return Items.AIR;
        }
    }

    public static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }

        boolean added = player.getInventory().add(stack);
        if (!added) {
            player.drop(stack, false);
        }
    }

    private static String safeName(NpcShopConfig.ShopEntry entry) {
        if (entry == null || entry.displayName == null || entry.displayName.isBlank()) {
            return "shop item";
        }
        return entry.displayName;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String stripColor(String value) {
        return value == null ? "" : value.replaceAll("§.", "");
    }
}
