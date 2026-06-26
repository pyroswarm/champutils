package com.champutils.menu;

import com.champutils.auction.AuctionHouseGui;
import com.champutils.crate.OpenCratesMenu;
import com.champutils.territory.TerritoryMenus;
import com.champutils.profile.ProfileLobbyDebug;
import com.cobblemon.mod.common.entity.npc.NPCEntity;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuNpcInteractionListener {

    private static final Map<UUID, String> PENDING_BINDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_CLICK = new ConcurrentHashMap<>();
    private static final long CLICK_DEBOUNCE_MS = 1000L;

    private MenuNpcInteractionListener() {}

    public static void beginBind(ServerPlayer player, String menu) {
        if (player == null) return;

        String normalized = MenuNpcBindingRegistry.normalize(menu);
        if (!MenuNpcBindingRegistry.isValidMenu(normalized)) {
            player.sendSystemMessage(Component.literal("Unknown menu type. Valid menus: " + MenuNpcBindingRegistry.validMenusText()).withStyle(ChatFormatting.RED));
            return;
        }

        PENDING_BINDS.put(player.getUUID(), normalized);
        player.sendSystemMessage(Component.literal("Right-click the Cobblemon NPC you want to bind to the " + normalized + " menu.").withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Use /menunpc bindcancel to cancel.").withStyle(ChatFormatting.GRAY));
    }

    public static boolean cancelBind(ServerPlayer player) {
        if (player == null) return false;
        return PENDING_BINDS.remove(player.getUUID()) != null;
    }

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!(entity instanceof NPCEntity npc)) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;

            String pendingMenu = PENDING_BINDS.remove(serverPlayer.getUUID());
            if (pendingMenu != null) {
                MenuNpcBindingRegistry.bind(pendingMenu, npc);
                serverPlayer.sendSystemMessage(Component.literal("Bound this NPC to the " + pendingMenu + " menu.").withStyle(ChatFormatting.GREEN));
                return InteractionResult.SUCCESS;
            }

            String menu = MenuNpcBindingRegistry.getBoundMenu(npc);
            if (menu == null) return InteractionResult.PASS;

            UUID key = serverPlayer.getUUID();
            long now = System.currentTimeMillis();
            Long previous = LAST_CLICK.get(key);
            if (previous != null && now - previous < CLICK_DEBOUNCE_MS) return InteractionResult.SUCCESS;
            LAST_CLICK.put(key, now);

            openBoundMenu(serverPlayer, menu);
            return InteractionResult.SUCCESS;
        });
    }

    private static void openBoundMenu(ServerPlayer player, String menu) {
        switch (MenuNpcBindingRegistry.normalize(menu)) {
            case "profiles", "profile", "profilemenu" -> {
                ProfileLobbyDebug.log("menuNpc.openProfiles", player);
                ProfileSelectionMenu.open(player);
            }
            case "gearworkshop" -> GearWorkshopMenu.open(player);
            case "gearappraiser" -> GearAppraiserMenu.open(player);
            case "tmcrafter", "tms" -> TMCrafterMenu.open(player);
            case "battles", "pvp" -> BattleMenu.open(player);
            case "auction" -> AuctionHouseGui.openMain(player);
            case "shop", "essentials" -> NpcShopMenu.open(player);
            case "opencrates", "crates" -> OpenCratesMenu.open(player);
            case "quests", "quest" -> QuestMenu.open(player);
            case "contracts", "contract" -> ContractMenu.open(player);
            case "wondertrade", "wonder" -> WonderTradeMenu.open(player);
            case "territorycreation", "territorycreate" -> TerritoryMenus.openCreationNpcMenu(player);
            case "territories", "territory" -> TerritoryMenus.openHub(player);
            case "pterritories" -> TerritoryMenus.openBrowser(player, TerritoryMenus.BrowserType.PERSONAL, "", 0);
            case "gterritories" -> TerritoryMenus.openBrowser(player, TerritoryMenus.BrowserType.GUILD, "", 0);
            case "guild", "guilds" -> GuildMenu.open(player);
            default -> player.sendSystemMessage(Component.literal("This NPC is bound to an unknown menu: " + menu).withStyle(ChatFormatting.RED));
        }
    }
}
