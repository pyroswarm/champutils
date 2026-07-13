package com.champutils.worldfirst;

import com.champutils.menu.MenuUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import java.util.List;

public final class WorldFirstMenu {
    private WorldFirstMenu() {}
    public static void open(ServerPlayer player) { open(player,0); }
    public static void open(ServerPlayer player, int requestedPage) {
        List<WorldFirstManager.WorldFirstDef> defs=WorldFirstManager.definitions();
        int pageSize=45, pages=Math.max(1,(defs.size()+pageSize-1)/pageSize), page=Math.max(0,Math.min(pages-1,requestedPage));
        SimpleGui gui=MenuUtil.createGui(MenuType.GENERIC_9x6,player);
        gui.setTitle(Component.literal("World Firsts " + (page+1) + "/" + pages));
        int from=page*pageSize,to=Math.min(defs.size(),from+pageSize),slot=0;
        for(int i=from;i<to;i++){
            WorldFirstManager.WorldFirstDef def=defs.get(i); WorldFirstManager.Claim claim=WorldFirstManager.claim(def.id); boolean found=claim!=null;
            gui.setSlot(slot++,new GuiElementBuilder(found?Items.NETHER_STAR:Items.GRAY_DYE).hideDefaultTooltip()
                .setName(Component.literal(found?"§6"+def.name:"§8???"))
                .addLoreLine(Component.literal(found?"§7First completed by: §b"+claim.playerName:"§7Unknown until someone unlocks it."))
                .addLoreLine(Component.literal(found?"§7Unlocked: §f"+claim.claimedAt:"§7Rewards are hidden until discovered."))
                .addLoreLine(Component.literal(found?"§7Title: "+def.titleDisplay.replace('&','§'):"§8???")));
        }
        if(page>0) gui.setSlot(45,new GuiElementBuilder(Items.ARROW).setName(Component.literal("§ePrevious Page")).setCallback((a,b,c)->open(player,page-1)));
        MenuUtil.addBackButton(gui,49,()->com.champutils.menu.TitleWorldFirstMenu.open(player));
        if(page+1<pages) gui.setSlot(53,new GuiElementBuilder(Items.ARROW).setName(Component.literal("§eNext Page")).setCallback((a,b,c)->open(player,page+1)));
        gui.open();
    }
}
