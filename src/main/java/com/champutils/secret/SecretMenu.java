package com.champutils.secret;
import com.champutils.menu.MenuUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import java.util.*;
public final class SecretMenu {
 private SecretMenu(){}
 public static void open(ServerPlayer p){ List<SecretManager.SecretDef> defs=new ArrayList<>(SecretManager.definitions()); int size=Math.max(9,Math.min(54,((defs.size()+8)/9)*9)); SimpleGui g=MenuUtil.createGui(size<=9?MenuType.GENERIC_9x1:size<=18?MenuType.GENERIC_9x2:size<=27?MenuType.GENERIC_9x3:size<=36?MenuType.GENERIC_9x4:size<=45?MenuType.GENERIC_9x5:MenuType.GENERIC_9x6,p); g.setTitle(Component.literal("Secrets")); int i=0; for(SecretManager.SecretDef ignored:defs){ if(i>=size) break; g.setSlot(i++,new GuiElementBuilder(Items.GRAY_CONCRETE).hideDefaultTooltip().setName(Component.literal("§8???")).addLoreLine(Component.literal("§7A secret remains hidden."))); } g.open(); }
}
