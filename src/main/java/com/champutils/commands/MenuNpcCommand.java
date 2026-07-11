package com.champutils.commands;

import com.champutils.menu.MenuNpcBindingRegistry;
import com.champutils.menu.MenuNpcInteractionListener;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

public final class MenuNpcCommand {

    private MenuNpcCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("menunpc")
                        .requires(source -> source.hasPermission(4))
                        .executes(context -> help(context.getSource()))
                        .then(Commands.literal("bind")
                                .then(Commands.argument("menu", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("adventurersguild");
                                            builder.suggest("profiles");
                                            builder.suggest("gearworkshop");
                                            builder.suggest("gearworkshop");
                                            builder.suggest("tmcrafter");
                                            builder.suggest("pvp");
                                            builder.suggest("auction");
                                            builder.suggest("shop");
                                            builder.suggest("essentials");
                                            builder.suggest("genesis");
                                            builder.suggest("professionforeman");
                                            builder.suggest("foreman");
                                            builder.suggest("chunks");
                                            builder.suggest("champsshop");
                                            builder.suggest("accountupgrade");
                                            builder.suggest("vip");
                                            builder.suggest("rankedshop");
                                            builder.suggest("rankedtokens");
                                            builder.suggest("territories");
                                            builder.suggest("pterritories");
                                            builder.suggest("gterritories");
                                            builder.suggest("guilds");
                                            builder.suggest("breeding");
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> bind(
                                                context.getSource(),
                                                normalizeMenu(StringArgumentType.getString(context, "menu"))
                                        ))))
                        .then(Commands.literal("bindcancel")
                                .executes(context -> bindCancel(context.getSource())))
                        .then(Commands.literal("unbind")
                                .then(Commands.argument("menu", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("adventurersguild");
                                            builder.suggest("profiles");
                                            builder.suggest("gearworkshop");
                                            builder.suggest("gearworkshop");
                                            builder.suggest("tmcrafter");
                                            builder.suggest("pvp");
                                            builder.suggest("auction");
                                            builder.suggest("shop");
                                            builder.suggest("essentials");
                                            builder.suggest("genesis");
                                            builder.suggest("professionforeman");
                                            builder.suggest("foreman");
                                            builder.suggest("chunks");
                                            builder.suggest("champsshop");
                                            builder.suggest("accountupgrade");
                                            builder.suggest("vip");
                                            builder.suggest("rankedshop");
                                            builder.suggest("rankedtokens");
                                            builder.suggest("territories");
                                            builder.suggest("pterritories");
                                            builder.suggest("gterritories");
                                            builder.suggest("guilds");
                                            builder.suggest("breeding");
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> unbind(
                                                context.getSource(),
                                                normalizeMenu(StringArgumentType.getString(context, "menu"))
                                        ))))
                        .then(Commands.literal("list")
                                .executes(context -> list(context.getSource())))
        ));
    }


    private static String normalizeMenu(String menu) {
        return switch (menu.toLowerCase()) {
            case "adventurer", "adventurersguild", "aguild", "guildhall" -> "adventurersguild";
            case "profile", "profiles", "profilemenu" -> "profiles";
            case "pvp", "battle", "battles" -> "battles";
            case "guild" -> "guilds";
            case "tms", "tm", "tmcraft", "tmcrafter" -> "tmcrafter";
            case "champsshop", "champs_shop", "cashshop", "accountupgrade", "accountupgrades", "upgrades", "rankupgrade", "vip" -> "champsshop";
            case "rankedshop", "rankedtokens", "ranked_token_shop" -> "rankedshop";
            case "breed", "nursery" -> "breeding";
            default -> menu.toLowerCase();
        };
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6Menu NPC Commands"), false);
        source.sendSuccess(() -> Component.literal("§7/menunpc bind <menu> §8- §fRight-click an NPC to bind it."), false);
        source.sendSuccess(() -> Component.literal("§7/menunpc unbind <menu> §8- §fRemove a menu NPC binding."), false);
        source.sendSuccess(() -> Component.literal("§7/menunpc list §8- §fShow current bindings."), false);
        source.sendSuccess(() -> Component.literal("§7Valid menus: §f" + MenuNpcBindingRegistry.validMenusText()), false);
        return 1;
    }

    private static int bind(CommandSourceStack source, String menu) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String normalized = MenuNpcBindingRegistry.normalize(menu);

        if (!MenuNpcBindingRegistry.isValidMenu(normalized)) {
            source.sendFailure(Component.literal("Unknown menu type. Valid menus: " + MenuNpcBindingRegistry.validMenusText()));
            return 0;
        }

        MenuNpcInteractionListener.beginBind(player, normalized);
        return 1;
    }

    private static int bindCancel(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (MenuNpcInteractionListener.cancelBind(player)) {
            source.sendSuccess(() -> Component.literal("Cancelled pending menu NPC bind.").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        source.sendFailure(Component.literal("You do not have a pending menu NPC bind."));
        return 0;
    }

    private static int unbind(CommandSourceStack source, String menu) {
        String normalized = MenuNpcBindingRegistry.normalize(menu);
        if (!MenuNpcBindingRegistry.isValidMenu(normalized)) {
            source.sendFailure(Component.literal("Unknown menu type. Valid menus: " + MenuNpcBindingRegistry.validMenusText()));
            return 0;
        }

        if (MenuNpcBindingRegistry.unbind(normalized)) {
            source.sendSuccess(() -> Component.literal("Unbound the " + normalized + " menu NPC.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }

        source.sendFailure(Component.literal("No NPC binding exists for menu: " + normalized));
        return 0;
    }

    private static int list(CommandSourceStack source) {
        Map<String, MenuNpcBindingRegistry.Binding> bindings = MenuNpcBindingRegistry.getAll();
        if (bindings.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7No menu NPC bindings exist."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("§6Menu NPC bindings:"), false);
        for (MenuNpcBindingRegistry.Binding binding : bindings.values()) {
            source.sendSuccess(() -> Component.literal("§7- §e" + binding.menu + " §8-> §f" + binding.npcUuid + " §8(" + binding.world + ")"), false);
        }
        return 1;
    }
}
