package com.champutils.commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ChampUtilsHelpCommand {

    private static final int TOTAL_PAGES = 3;

    private ChampUtilsHelpCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("champutils")
                        .executes(context -> sendHelp(context.getSource(), 1))
                        .then(literal("help")
                                .executes(context -> sendHelp(context.getSource(), 1))
                                .then(argument("page", integer(1, TOTAL_PAGES))
                                        .executes(context -> sendHelp(context.getSource(), getInteger(context, "page")))))
        ));
    }

    private static int sendHelp(CommandSourceStack source, int page) {
        header(source, page);
        switch (page) {
            case 1 -> playerCommands(source);
            case 2 -> progressionCommands(source);
            case 3 -> adminCommands(source);
            default -> playerCommands(source);
        }
        footer(source, page);
        return 1;
    }

    private static void header(CommandSourceStack source, int page) {
        source.sendSuccess(() -> Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY), false);
        source.sendSuccess(() -> Component.literal("Cobble Champs Commands ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("Page " + page + "/" + TOTAL_PAGES).withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("Use /champutils help <page>").withStyle(ChatFormatting.YELLOW), false);
    }

    private static void footer(CommandSourceStack source, int page) {
        source.sendSuccess(() -> Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY), false);
        if (page < TOTAL_PAGES) source.sendSuccess(() -> Component.literal("Next: /champutils help " + (page + 1)).withStyle(ChatFormatting.YELLOW), false);
    }

    private static void line(CommandSourceStack source, String command, String description) {
        source.sendSuccess(() -> Component.literal(command).withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(description).withStyle(ChatFormatting.GRAY)), false);
    }

    private static void section(CommandSourceStack source, String title) {
        source.sendSuccess(() -> Component.literal(" ").append(Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)), false);
    }

    private static void playerCommands(CommandSourceStack source) {
        section(source, "General");
        line(source, "/menu", "Open the main Cobble Champs menu.");
        line(source, "/profile", "Admin-only profile viewer/menu.");
        line(source, "/leaderboards", "Open the leaderboard menu.");
        line(source, "/leaderboard", "Show the ranked leaderboard.");
        line(source, "/items", "Open the custom item menu.");
        line(source, "/professionleaderboard", "Open profession leaderboard views.");
        line(source, "/showitem", "Show your held item in chat with hover details. Has cooldown.");
        line(source, "/itemlock", "Lock or unlock your held custom item.");
        line(source, "/xplock <slot>", "Toggle XP gain for a party Pokémon slot.");
        section(source, "Account + Economy");
        line(source, "/linkaccount <code>", "Link your Minecraft account to your website account.");
        line(source, "/credits", "Show your Credits balance.");
        line(source, "/pay <player> <amount>", "Safely send Credits to another online player.");
        line(source, "/notifications", "Show recent Cobble Champs notifications.");
    }

    private static void progressionCommands(CommandSourceStack source) {
        section(source, "Items + Tools");
        line(source, "/itemroll identify", "Identify the custom profession tool in your hand.");
        line(source, "/itemroll reroll", "Reroll the custom profession tool in your hand.");
        line(source, "/salvage", "Salvage the custom profession tool in your hand.");
        line(source, "/essence menu", "Open the essence crafting menu.");
        line(source, "/essence withdraw <rarity> <amount>", "Turn stored digital essence back into physical items.");
        section(source, "Battles + Training");
        line(source, "/evtrain <stat>", "Open EV training for the selected stat.");
        line(source, "/gym list", "View gym progression and available gyms.");
        line(source, "/worldevent list", "View configured world events.");
        line(source, "/hunt", "Open Pokémon hunts.");
        line(source, "Guild Clerk NPC", "Talk to the Adventurer's Guild NPC to open the guild hub.");
        line(source, "/wondertrade", "Use Wonder Trade.");
    }

    private static void adminCommands(CommandSourceStack source) {
        section(source, "Admin + Config");
        line(source, "/champreload", "Reload ChampUtils configuration files.");
        line(source, "/champutils doctor", "Admin: run beta readiness diagnostics.");
        line(source, "/eco give|take|set <player> <amount>", "Admin: manage Credits.");
        line(source, "/givechampitem <toolId> [player]", "Admin: give a custom ChampUtils item/tool.");
        section(source, "Admin Progression");
        line(source, "/setrp <player> <amount>", "Admin: set ranked RP.");
        line(source, "/professionlevel set <player> <profession> <level>", "Admin: set profession level.");
        line(source, "/season info", "View the current active season.");
        line(source, "/season preseason", "Admin: set Season 0 Preseason without resetting players.");
        line(source, "/season set <number> <name>", "Admin: directly set the active season without resetting players.");
        line(source, "/season start <name>", "Admin: end current season and start the next season.");
        section(source, "Admin NPCs + Events");
        line(source, "/gym bind <gymId>", "Admin: bind nearest NPC to a gym.");
        line(source, "/worldevent start <eventId>", "Admin: start a world event.");
        line(source, "/worldevent skin <eventId> <playerName>", "Admin: set a world event NPC skin from a Minecraft username.");
        line(source, "/spawntrainer <id>", "Admin: spawn a configured trainer NPC.");
        line(source, "/spawnblanknpc <name>", "Admin: spawn a blank NPC for menu binding.");
        line(source, "/ah bind", "Admin: bind an NPC as the Auction NPC.");
        line(source, "/menunpc bind <menu>", "Admin: bind an NPC to a feature menu.");
    }
}
