package com.champutils.profession;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Refreshes dynamic profession-tool tooltip lines without doing per-tick item rewrites.
 * Runs every 5 seconds and only touches actual profession tools in player inventories.
 */
public final class ProfessionToolTooltipUpdater {

    private static final int UPDATE_INTERVAL_TICKS = 100;
    private static int tickCounter = 0;

    private ProfessionToolTooltipUpdater() {
    }

    public static void register() {
        // Intentionally no periodic inventory scan. Rewriting CUSTOM_DATA/LORE on a timer
        // dirties every tool stack and causes continuous inventory synchronization. Tool
        // displays are refreshed only when their real state changes.
    }

    private static void refreshPlayer(ServerPlayer player) {
        if (player == null || player.hasDisconnected()) {
            return;
        }

        for (ItemStack stack : player.getInventory().items) {
            refreshIfProfessionTool(player, stack);
        }
        for (ItemStack stack : player.getInventory().offhand) {
            refreshIfProfessionTool(player, stack);
        }
        for (ItemStack stack : player.getInventory().armor) {
            refreshIfProfessionTool(player, stack);
        }
    }

    private static void refreshIfProfessionTool(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (!ProfessionToolMetadata.isProfessionTool(stack)) {
            return;
        }
        ProfessionToolConfig.ToolData toolData = ProfessionToolUtil.getToolData(stack);
        String activeAbility = ProfessionToolMetadata.getResolvedActiveAbility(stack, toolData);
        if (toolData == null || !ProfessionToolManager.isTimedActiveAbility(activeAbility)) {
            return;
        }
        ProfessionToolManager.refreshToolStackForPlayer(stack, player);
    }
}
