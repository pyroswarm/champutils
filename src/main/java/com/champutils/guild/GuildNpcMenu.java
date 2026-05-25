package com.champutils.guild;

import com.champutils.territory.TerritoryMenus;
import com.champutils.territory.TerritoryRepository;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

public final class GuildNpcMenu {
    private GuildNpcMenu() {}

    public static void open(ServerPlayer player, TerritoryRepository.Territory territory) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("Guild Steward"));
        fill(gui);
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(player.getUUID());
        boolean officer = guild != null && GuildRepository.canManageGuildTerritory(guild.role);

        gui.setSlot(4, new GuiElementBuilder(Items.BELL).hideDefaultTooltip()
                .setName(Component.literal(guild == null ? "No Guild" : guild.name).withStyle(ChatFormatting.GOLD))
                .addLoreLine(Component.literal(guild == null ? "Join or create a guild first." : "Role: " + guild.role.name()).withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal(territory == null ? "No guild territory found." : "Territory: " + territory.publicName()).withStyle(ChatFormatting.GRAY)));

        gui.setSlot(10, button(Items.PLAYER_HEAD, "Member Management", "Promote, demote, kick, or transfer leadership.", () -> openMembers(player, 0)));
        gui.setSlot(12, button(Items.ENDER_PEARL, "Guild Territory Home", "Teleport to your guild territory.", () -> run(player, gui, "gterritory home")));
        gui.setSlot(14, button(Items.COMPARATOR, "Territory Settings", officer ? "Manage guild territory settings." : "Only leaders and officers can manage settings.", () -> {
            if (!officer) {
                player.sendSystemMessage(Component.literal("Only guild leaders and officers can manage guild territory settings.").withStyle(ChatFormatting.RED));
                return;
            }
            run(player, gui, "gterritory settings");
        }));
        gui.setSlot(16, button(Items.NETHER_STAR, "Daily Guild Boss", officer ? "Spawn today's guild boss." : "Only leaders and officers can spawn this.", () -> {
            gui.close();
            GuildBossManager.spawnBoss(player);
        }));
        gui.setSlot(18, button(Items.CHEST, "Claim Boss Rewards", "Claim rewards after your guild defeats a boss.", () -> {
            gui.close();
            GuildBossManager.claimRewards(player);
        }));
        gui.setSlot(22, button(Items.BOOK, "Guild Territory Menu", "Open the full guild territory menu.", () -> TerritoryMenus.openGuildManage(player)));
        gui.open();
    }

    public static void openMembers(ServerPlayer player, int page) {
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(player.getUUID());
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("Guild Members"));
        fill(gui);
        if (guild == null) {
            gui.setSlot(22, new GuiElementBuilder(Items.BARRIER).hideDefaultTooltip().setName(Component.literal("You are not in a guild.").withStyle(ChatFormatting.RED)));
            gui.open(); return;
        }
        List<GuildRepository.MemberSnapshot> members = GuildRepository.cachedOnlineMembers(player.server.getPlayerList().getPlayers(), guild.id);
        int start = Math.max(0, page) * 45;
        for (int i = start, slot = 0; i < members.size() && slot < 45; i++, slot++) {
            GuildRepository.MemberSnapshot m = members.get(i);
            gui.setSlot(slot, new GuiElementBuilder(Items.PLAYER_HEAD).hideDefaultTooltip()
                    .setName(Component.literal(m.playerName).withStyle(ChatFormatting.YELLOW))
                    .addLoreLine(Component.literal("Role: " + m.role.name()).withStyle(ChatFormatting.GRAY))
                    .addLoreLine(Component.literal("Click to manage this member.").withStyle(ChatFormatting.YELLOW))
                    .setCallback((index, clickType, actionType) -> openMemberActions(player, m)));
        }
        gui.setSlot(49, button(Items.ARROW, "Back", "Return to guild steward.", () -> open(player, TerritoryRepository.cachedGuildForPlayer(player))));
        gui.open();
    }


    private static void openMemberActions(ServerPlayer player, GuildRepository.MemberSnapshot member) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("Manage " + member.playerName));
        fill(gui);
        gui.setSlot(4, new GuiElementBuilder(Items.PLAYER_HEAD).hideDefaultTooltip()
                .setName(Component.literal(member.playerName).withStyle(ChatFormatting.YELLOW))
                .addLoreLine(Component.literal("Role: " + member.role.name()).withStyle(ChatFormatting.GRAY)));
        gui.setSlot(10, button(Items.EMERALD, "Promote", "Move this member up one rank.", () -> run(player, gui, "guild promote " + member.playerName)));
        gui.setSlot(12, button(Items.REDSTONE, "Demote", "Move this member down one rank.", () -> run(player, gui, "guild demote " + member.playerName)));
        gui.setSlot(14, button(Items.BARRIER, "Kick", "Remove this member from the guild.", () -> run(player, gui, "guild kick " + member.playerName)));
        gui.setSlot(16, button(Items.GOLD_BLOCK, "Transfer Leadership", "Transfer guild ownership to this member.", () -> run(player, gui, "guild transfer " + member.playerName + " confirm")));
        gui.setSlot(22, button(Items.ARROW, "Back", "Return to member list.", () -> openMembers(player, 0)));
        gui.open();
    }

    private static void run(ServerPlayer player, SimpleGui gui, String command) { gui.close(); runCommand(player, command); }
    private static void runCommand(ServerPlayer player, String command) { player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command); }
    private static GuiElementBuilder button(Item item, String name, String lore, Runnable click) {
        return new GuiElementBuilder(item).hideDefaultTooltip().setName(Component.literal(name).withStyle(ChatFormatting.GREEN)).addLoreLine(Component.literal(lore).withStyle(ChatFormatting.GRAY)).setCallback((i,c,a)->click.run());
    }
    private static void fill(SimpleGui gui) { for (int i=0;i<gui.getSize();i++) gui.setSlot(i, new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE).hideDefaultTooltip().setName(Component.literal(" "))); }
}
