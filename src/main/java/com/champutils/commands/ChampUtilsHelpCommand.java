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

    private static final int TOTAL_PAGES = 4;

    private ChampUtilsHelpCommand() {
    }

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
            case 3 -> dungeonWorldCommands(source);
            case 4 -> adminCommands(source);
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
        if (page < TOTAL_PAGES) {
            int next = page + 1;
            source.sendSuccess(() -> Component.literal("Next: /champutils help " + next).withStyle(ChatFormatting.YELLOW), false);
        }
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
        line(source, "/profile", "Open your player profile menu.");
        line(source, "/leaderboards", "Open the leaderboard menu.");
        line(source, "/leaderboard", "Show the ranked leaderboard.");
        line(source, "/items", "Open the custom item menu.");
        line(source, "/dungeons", "Open the dungeon menu.");
        line(source, "/professionleaderboard", "Open profession leaderboard views.");
        line(source, "/showitem", "Show your held item in chat with hover details. Has cooldown.");
        line(source, "/itemlock", "Lock or unlock your held custom item.");

        section(source, "Account + Economy");
        line(source, "/linkaccount <code>", "Link your Minecraft account to your website account.");
        line(source, "/credits", "Show your Credits balance.");
        line(source, "/balance", "Alias for /credits.");
        line(source, "/pay <player> <amount>", "Safely send Credits to another online player.");
        line(source, "/notifications", "Show recent Cobble Champs notifications.");
    }

    private static void progressionCommands(CommandSourceStack source) {
        section(source, "Items + Tools");
        line(source, "/itemroll identify", "Identify the custom profession tool in your hand.");
        line(source, "/itemroll reroll", "Reroll the custom profession tool in your hand.");
        line(source, "/salvage", "Salvage the custom profession tool in your hand.");
        line(source, "/salvage confirm", "Confirm a protected/high-value salvage action.");
        line(source, "/fragments list", "View your stored profession fragments.");
        line(source, "/fragments menu", "Open the fragment crafting menu.");
        line(source, "/fragments upgrade <rarity>", "Upgrade lower rarity fragments into the next tier.");
        line(source, "/fragments craft <rarity>", "Craft a random unidentified profession tool.");
        line(source, "/fragments trade <rarity>", "Trade physical fragments into stored fragments.");

        section(source, "Battles + Training");
        line(source, "/evtrain <stat>", "Open EV training for the selected stat.");
        line(source, "/elite4 <type>", "Challenge Elite Four content when configured.");
        line(source, "/gym list", "View gym progression and available gyms.");
    }

    private static void dungeonWorldCommands(CommandSourceStack source) {
        section(source, "Dungeons");
        line(source, "/dungeon start <dungeonId>", "Start a dungeon run.");
        line(source, "/dungeon status", "Show your active dungeon status.");
        line(source, "/dungeon credits", "Show your dungeon crate credits.");
        line(source, "/dungeon limits", "Show dungeon cooldowns and limits.");
        line(source, "/dungeon list", "List loaded dungeons.");
        line(source, "/dungeon forfeit", "Forfeit your active dungeon run.");

        section(source, "World Events");
        line(source, "/worldevent teleport <eventId>", "Teleport to an active world event.");
        line(source, "/worldevent tp <eventId>", "Alias for world event teleport.");
        line(source, "/worldevent list", "List active world events.");

        section(source, "Auction House");
        line(source, "/ah", "Open the in-game auction house UI.");
        line(source, "/ah sell <price>", "Start listing the item stack in your hand for Credits.");
        line(source, "/ah sellpokemon <slot> <price>", "Start listing a party Pokémon for Credits.");
        line(source, "/ah confirm", "Confirm a pending auction listing.");
        line(source, "/ah cancel", "Cancel a pending auction listing. Use /ah cancel <listingId> for active listings.");
        line(source, "/ah mylistings", "Open your active listings and cancel auctions in-game.");
        line(source, "/ah claim", "Claim purchases bought from the website.");
    }

    private static void adminCommands(CommandSourceStack source) {
        section(source, "Admin + Config");
        line(source, "/champreload", "Reload ChampUtils configuration files.");
        line(source, "/dbtest", "Test Supabase/database connectivity.");
        line(source, "/eco balance <player>", "Admin: check a player's Credits balance.");
        line(source, "/eco give <player> <amount>", "Admin: give Credits.");
        line(source, "/eco take <player> <amount>", "Admin: remove Credits.");
        line(source, "/eco set <player> <amount>", "Admin: set Credits balance.");
        line(source, "/eco reset <player>", "Admin: reset Credits balance to zero.");
        line(source, "/givechampitem <toolId> [player]", "Admin: give a custom ChampUtils item/tool.");

        section(source, "Admin Progression");
        line(source, "/setrp <player> <amount>", "Admin: set ranked RP.");
        line(source, "/addrp <player> <amount>", "Admin: add ranked RP.");
        line(source, "/removerp <player> <amount>", "Admin: remove ranked RP.");
        line(source, "/professionlevel set <player> <profession> <level>", "Admin: set profession level.");
        line(source, "/professionlevel add <player> <profession> <amount>", "Admin: add profession levels/XP depending on subcommand.");
        line(source, "/season info", "Show current season info.");
        line(source, "/season preview", "Preview season reset/archive changes.");
        line(source, "/season start", "Admin: start a season transition.");
        line(source, "/season rollback", "Admin: rollback the last season action.");
        line(source, "/season confirm", "Admin: confirm a pending dangerous season action.");

        section(source, "Admin NPCs + Events");
        line(source, "/gym bind <gymId>", "Admin: bind nearest NPC to a gym.");
        line(source, "/gym unbind <gymId>", "Admin: remove a gym NPC binding.");
        line(source, "/worldevent start <eventId>", "Admin: start a world event.");
        line(source, "/worldevent start random", "Admin: start a random world event.");
        line(source, "/worldevent stop <eventId>", "Admin: stop a world event.");
        line(source, "/worldevent stop all", "Admin: stop all world events.");
        line(source, "/worldevent bind <eventId>", "Admin: bind nearest NPC to a world event.");
        line(source, "/worldevent unbind <eventId>", "Admin: remove a world event NPC binding.");
        line(source, "/worldevent reload", "Admin: reload world event config.");
        line(source, "/spawntrainer <id>", "Admin: spawn a configured trainer NPC.");
        line(source, "/despawntrainer nearest|radius", "Admin: despawn ChampUtils trainer NPCs.");
        line(source, "/ah bind", "Admin: bind an NPC as the Auction NPC.");
        line(source, "/ah unbind", "Admin: remove the Auction NPC binding.");
        line(source, "/menunpc bind <menu>", "Admin: bind an NPC to a feature menu.");
        line(source, "/menunpc unbind <menu>", "Admin: remove a feature menu NPC binding.");
        line(source, "/dungeon bind <dungeonId>", "Admin: bind nearest NPC to a dungeon.");
        line(source, "/dungeon unbind <dungeonId>", "Admin: remove dungeon NPC binding.");
        line(source, "/dungeon givekey <keyId> [amount]", "Admin: give dungeon keys.");
        line(source, "/dungeon crate bind <rarity> <normal|pokemon>", "Admin: bind a native dungeon crate block.");
        line(source, "/dungeon reward <type> ...", "Admin: test/grant dungeon rewards.");
    }
}
