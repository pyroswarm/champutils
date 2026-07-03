package com.champutils.profession;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class ProfessionToolAnnouncementManager {

    private static int tickCounter = 0;
    private static int nextPlayerIndex = 0;

    /**
     * Discovery announcements are nice polish, but scanning every online player's full
     * inventory every second showed up in spark. Scan one player every 5 seconds instead.
     */
    private static final int SCAN_INTERVAL_TICKS = 100;

    private ProfessionToolAnnouncementManager() {
    }

    public static void register() {

        ServerTickEvents.END_SERVER_TICK.register(server -> {

            tickCounter++;

            if (tickCounter < SCAN_INTERVAL_TICKS) {
                return;
            }

            tickCounter = 0;

            scanNextInventoryForDiscoveryAnnouncements(server);
        });
    }

    public static void announcePerfectRollIfNeeded(
            ServerPlayer player,
            ItemStack stack,
            double quality
    ) {

        if (
                player == null ||
                        stack == null ||
                        stack.isEmpty() ||
                        quality < 100.0D ||
                        ProfessionToolMetadata.isPerfectRollAnnounced(stack)
        ) {
            return;
        }

        ProfessionToolMetadata.setPerfectRollAnnounced(
                stack,
                true
        );

        broadcast(
                player.server,
                Component.literal(
                                "✦ "
                        )
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(
                                        player.getGameProfile().getName()
                                )
                                .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(
                                        " rolled a perfect 100% "
                                )
                                .withStyle(ChatFormatting.GOLD))
                        .append(hoverItemName(stack))
                        .append(Component.literal(
                                        "!"
                                )
                                .withStyle(ChatFormatting.GOLD))
        );
    }

    private static void scanNextInventoryForDiscoveryAnnouncements(
            MinecraftServer server
    ) {

        if (server == null) {
            return;
        }

        java.util.List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            nextPlayerIndex = 0;
            return;
        }

        if (nextPlayerIndex >= players.size()) {
            nextPlayerIndex = 0;
        }

        ServerPlayer player = players.get(nextPlayerIndex);
        nextPlayerIndex = (nextPlayerIndex + 1) % players.size();

        for (ItemStack stack : player.getInventory().items) {
            announceDiscoveryIfNeeded(player, stack);
        }

        for (ItemStack stack : player.getInventory().armor) {
            announceDiscoveryIfNeeded(player, stack);
        }

        for (ItemStack stack : player.getInventory().offhand) {
            announceDiscoveryIfNeeded(player, stack);
        }
    }

    private static void announceDiscoveryIfNeeded(
            ServerPlayer player,
            ItemStack stack
    ) {

        if (
                player == null ||
                        stack == null ||
                        stack.isEmpty() ||
                        !ProfessionToolMetadata.isAscended(stack) ||
                        !ProfessionToolMetadata.isDiscoveryAnnouncementEligible(stack) ||
                        ProfessionToolMetadata.isDiscoveryAnnounced(stack)
        ) {
            return;
        }

        ProfessionToolMetadata.setDiscoveryAnnounced(
                stack,
                true
        );

        ProfessionToolMetadata.setDiscoveryAnnouncementEligible(
                stack,
                false
        );

        ProfessionToolManager.refreshToolStack(
                stack
        );
        ProfessionToolManager.applyVanillaEfficiencyEnchant(player, stack);

        broadcast(
                player.server,
                Component.literal(
                                "✦ "
                        )
                        .withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal(
                                        player.getGameProfile().getName()
                                )
                                .withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(
                                        " discovered "
                                )
                                .withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(hoverItemName(stack))
                        .append(Component.literal(
                                        "!"
                                )
                                .withStyle(ChatFormatting.LIGHT_PURPLE))
        );
    }

    private static Component hoverItemName(
            ItemStack stack
    ) {

        ItemStack shownStack =
                stack.copy();

        return shownStack.getHoverName()
                .copy()
                .withStyle(style -> style.withHoverEvent(
                        new HoverEvent(
                                HoverEvent.Action.SHOW_ITEM,
                                new HoverEvent.ItemStackInfo(
                                        shownStack
                                )
                        )
                ));
    }

    private static void broadcast(
            MinecraftServer server,
            Component message
    ) {
        ProfessionNotificationSettings.sendBroadcast(server, message);
    }
}
