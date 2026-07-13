package com.champutils.matchmaking;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Mandatory match confirmation menu. Escape immediately reopens it until resolved. */
public final class MatchAcceptanceMenu {
    private static final Set<UUID> WAITING = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SUPPRESS_REOPEN = ConcurrentHashMap.newKeySet();

    private MatchAcceptanceMenu() {}

    public static void open(ServerPlayer player, String type) {
        if (player == null || player.server == null || player.isRemoved() || player.hasDisconnected()) return;
        WAITING.add(player.getUUID());
        new LockedMenu(player, type).open();
    }

    public static void resolve(ServerPlayer player) {
        if (player == null) return;
        WAITING.remove(player.getUUID());
        SUPPRESS_REOPEN.add(player.getUUID());
        player.closeContainer();
    }

    private static final class LockedMenu extends SimpleGui {
        private final ServerPlayer owner;
        private final String type;
        private boolean choiceMade;

        private LockedMenu(ServerPlayer owner, String type) {
            super(MenuType.GENERIC_9x3, owner, false);
            this.owner = owner;
            this.type = type == null ? "casual" : type;
            setLockPlayerInventory(true);
            setTitle(Component.literal("Match Found - Confirm"));

            for (int i = 0; i < getSize(); i++) {
                setSlot(i, new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
                        .hideTooltip().setName(Component.literal(" ")));
            }

            setSlot(11, new GuiElementBuilder(Items.LIME_CONCRETE)
                    .setName(Component.literal("§a§lACCEPT MATCH"))
                    .addLoreLine(Component.literal("§7Queue: §f" + this.type))
                    .addLoreLine(Component.literal("§7Click to accept."))
                    .setCallback((index, clickType, actionType) -> {
                        if (choiceMade) return;
                        choiceMade = true;
                        WAITING.remove(owner.getUUID());
                        SUPPRESS_REOPEN.add(owner.getUUID());
                        MatchmakingManager.acceptMatch(owner);
                    }));

            setSlot(15, new GuiElementBuilder(Items.RED_CONCRETE)
                    .setName(Component.literal("§c§lDENY MATCH"))
                    .addLoreLine(Component.literal("§7You will leave the queue."))
                    .addLoreLine(Component.literal("§7Click to deny."))
                    .setCallback((index, clickType, actionType) -> {
                        if (choiceMade) return;
                        choiceMade = true;
                        WAITING.remove(owner.getUUID());
                        SUPPRESS_REOPEN.add(owner.getUUID());
                        MatchmakingManager.declineMatch(owner);
                    }));

            setSlot(13, new GuiElementBuilder(Items.CLOCK)
                    .setName(Component.literal("§eRespond within 30 seconds"))
                    .addLoreLine(Component.literal("§7This menu cannot be dismissed.")));
        }

        @Override
        public void onClose() {
            super.onClose();
            if (owner == null || owner.server == null) return;
            if (SUPPRESS_REOPEN.remove(owner.getUUID())) return;
            if (choiceMade || !WAITING.contains(owner.getUUID())) return;
            owner.server.execute(() -> {
                if (owner.isRemoved() || owner.hasDisconnected()) return;
                if (WAITING.contains(owner.getUUID())) new LockedMenu(owner, type).open();
            });
        }
    }
}
