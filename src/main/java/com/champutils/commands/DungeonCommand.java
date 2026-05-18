package com.champutils.commands;

import com.champutils.dungeon.DungeonBindInteractionListener;
import com.champutils.dungeon.DungeonBindingRegistry;
import com.champutils.dungeon.DungeonConfig;
import com.champutils.dungeon.DungeonCrateCreditManager;
import com.champutils.dungeon.DungeonKeyConfig;
import com.champutils.dungeon.DungeonKeyManager;
import com.champutils.dungeon.DungeonLimitManager;
import com.champutils.dungeon.DungeonManager;
import com.champutils.dungeon.DungeonRarity;
import com.champutils.dungeon.DungeonRewardManager;
import com.champutils.dungeon.DungeonNativeCrateInteractionListener;
import com.champutils.dungeon.DungeonNativeCrateRegistry;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

public final class DungeonCommand {

    private static final SuggestionProvider<CommandSourceStack> DUNGEON_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(DungeonConfig.DUNGEONS.keySet(), builder);

    private static final SuggestionProvider<CommandSourceStack> KEY_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(DungeonKeyConfig.KEYS.keySet(), builder);

    private static final SuggestionProvider<CommandSourceStack> RARITY_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    Arrays.stream(DungeonRarity.values()).map(Enum::name),
                    builder
            );

    private DungeonCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("dungeon")
                        .executes(context -> help(context.getSource()))
                        .then(Commands.literal("start")
                                .then(Commands.argument("dungeonId", StringArgumentType.word())
                                        .suggests(DUNGEON_SUGGESTIONS)
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            return DungeonManager.startDungeon(player, StringArgumentType.getString(context, "dungeonId"));
                                        })))
                        .then(Commands.literal("forfeit")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    return DungeonManager.forfeitDungeon(player);
                                }))
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    DungeonManager.sendStatus(player);
                                    return 1;
                                }))
                        .then(Commands.literal("credits")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    return showCredits(context.getSource(), player);
                                }))
                        .then(Commands.literal("limits")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    return showLimits(context.getSource(), player);
                                }))
                        .then(Commands.literal("list")
                                .executes(context -> listDungeons(context.getSource())))
                        .then(Commands.literal("bind")
                                .requires(source -> source.hasPermission(4))
                                .then(Commands.argument("dungeonId", StringArgumentType.word())
                                        .suggests(DUNGEON_SUGGESTIONS)
                                        .executes(context -> beginBind(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "dungeonId")
                                        ))))
                        .then(Commands.literal("bindcancel")
                                .requires(source -> source.hasPermission(4))
                                .executes(context -> cancelBind(context.getSource())))
                        .then(Commands.literal("unbind")
                                .requires(source -> source.hasPermission(4))
                                .then(Commands.argument("dungeonId", StringArgumentType.word())
                                        .suggests(DUNGEON_SUGGESTIONS)
                                        .executes(context -> unbindDungeon(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "dungeonId")
                                        ))))
                        .then(Commands.literal("crate")
                                .requires(source -> source.hasPermission(4))
                                .then(Commands.literal("bind")
                                        .then(Commands.argument("rarity", StringArgumentType.word())
                                                .suggests(RARITY_SUGGESTIONS)
                                                .then(Commands.argument("type", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"normal", "pokemon"}, builder))
                                                        .executes(context -> beginCrateBind(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "rarity"),
                                                                StringArgumentType.getString(context, "type")
                                                        )))))
                                .then(Commands.literal("unbind")
                                        .executes(context -> beginCrateUnbind(context.getSource())))
                                .then(Commands.literal("cancel")
                                        .executes(context -> cancelCrateAction(context.getSource())))
                                .then(Commands.literal("list")
                                        .executes(context -> listCrates(context.getSource())))
                                .then(Commands.literal("reloadholograms")
                                        .executes(context -> reloadCrateHolograms(context.getSource())))
                                .then(Commands.literal("unbindnear")
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 50))
                                                .executes(context -> unbindNearbyCrates(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "radius")
                                                )))))
                        .then(Commands.literal("givekey")
                                .requires(source -> source.hasPermission(4))
                                .then(Commands.argument("keyId", StringArgumentType.word())
                                        .suggests(KEY_SUGGESTIONS)
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> giveKeyToSelf(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "keyId"),
                                                        IntegerArgumentType.getInteger(context, "amount")
                                                )))
                                        .executes(context -> giveKeyToSelf(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "keyId"),
                                                1
                                        ))))
                        .then(Commands.literal("reward")
                                .requires(source -> source.hasPermission(4))
                                .then(Commands.literal("key")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("keyId", StringArgumentType.word())
                                                        .suggests(KEY_SUGGESTIONS)
                                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                                .executes(context -> giveKeyToPlayer(
                                                                        context.getSource(),
                                                                        EntityArgument.getPlayer(context, "player"),
                                                                        StringArgumentType.getString(context, "keyId"),
                                                                        IntegerArgumentType.getInteger(context, "amount")
                                                                )))
                                                        .executes(context -> giveKeyToPlayer(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "keyId"),
                                                                1
                                                        )))))
                                .then(Commands.literal("chest")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("rarity", StringArgumentType.word())
                                                        .suggests(RARITY_SUGGESTIONS)
                                                        .executes(context -> rewardChest(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "rarity")
                                                        )))))
                                .then(Commands.literal("pokemoncrate")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("rarity", StringArgumentType.word())
                                                        .suggests(RARITY_SUGGESTIONS)
                                                        .executes(context -> rewardPokemonChest(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "rarity")
                                                        )))))
                                .then(Commands.literal("grantcrate")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("rarity", StringArgumentType.word())
                                                        .suggests(RARITY_SUGGESTIONS)
                                                        .then(Commands.argument("normal", IntegerArgumentType.integer(0, 999))
                                                                .then(Commands.argument("pokemon", IntegerArgumentType.integer(0, 999))
                                                                        .executes(context -> grantCrateCredits(
                                                                                context.getSource(),
                                                                                EntityArgument.getPlayer(context, "player"),
                                                                                StringArgumentType.getString(context, "rarity"),
                                                                                IntegerArgumentType.getInteger(context, "normal"),
                                                                                IntegerArgumentType.getInteger(context, "pokemon")
                                                                        )))))))
                                .then(Commands.literal("fragments")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("rarity", StringArgumentType.word())
                                                        .suggests(RARITY_SUGGESTIONS)
                                                        .then(Commands.argument("min", IntegerArgumentType.integer(0, 999))
                                                                .then(Commands.argument("max", IntegerArgumentType.integer(0, 999))
                                                                        .executes(context -> rewardFragments(
                                                                                context.getSource(),
                                                                                EntityArgument.getPlayer(context, "player"),
                                                                                StringArgumentType.getString(context, "rarity"),
                                                                                IntegerArgumentType.getInteger(context, "min"),
                                                                                IntegerArgumentType.getInteger(context, "max")
                                                                        )))))))
                                .then(Commands.literal("randomtool")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("rarity", StringArgumentType.word())
                                                        .suggests(RARITY_SUGGESTIONS)
                                                        .then(Commands.argument("ascendedChance", DoubleArgumentType.doubleArg(0.0D, 100.0D))
                                                                .executes(context -> rewardRandomTool(
                                                                        context.getSource(),
                                                                        EntityArgument.getPlayer(context, "player"),
                                                                        StringArgumentType.getString(context, "rarity"),
                                                                        DoubleArgumentType.getDouble(context, "ascendedChance")
                                                                )))
                                                        .executes(context -> rewardRandomTool(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "rarity"),
                                                                0.0D
                                                        )))))
                                .then(Commands.literal("item")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("itemId", StringArgumentType.string())
                                                        .then(Commands.argument("min", IntegerArgumentType.integer(1, 64))
                                                                .then(Commands.argument("max", IntegerArgumentType.integer(1, 64))
                                                                        .executes(context -> rewardItem(
                                                                                context.getSource(),
                                                                                EntityArgument.getPlayer(context, "player"),
                                                                                StringArgumentType.getString(context, "itemId"),
                                                                                IntegerArgumentType.getInteger(context, "min"),
                                                                                IntegerArgumentType.getInteger(context, "max")
                                                                        ))))))))
        ));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("/dungeon start <dungeonId>").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("/dungeon status").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("/dungeon credits").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("/dungeon limits").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("/dungeon list").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("/dungeon forfeit").withStyle(ChatFormatting.RED), false);
        if (source.hasPermission(4)) {
            source.sendSuccess(() -> Component.literal("/dungeon bind <dungeonId>").withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("/dungeon unbind <dungeonId>").withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("/dungeon givekey <keyId> [amount]").withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("/dungeon crate bind <rarity> <normal|pokemon>").withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("/dungeon crate unbind | list | reloadholograms").withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("/dungeon reward <key|chest|pokemoncrate|grantcrate|fragments|randomtool|item> ...").withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }



    private static int unbindNearbyCrates(CommandSourceStack source, int radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            source.sendFailure(Component.literal("Must be used in a server world."));
            return 0;
        }
        int removed = DungeonNativeCrateRegistry.unbindNearby(level, player.blockPosition(), radius);
        source.sendSuccess(() -> Component.literal("Removed " + removed + " crate bindings within " + radius + " blocks.")
                .withStyle(removed > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        return removed;
    }

    private static int listDungeons(CommandSourceStack source) {
        if (DungeonConfig.DUNGEONS.isEmpty()) {
            source.sendFailure(Component.literal("No dungeons are loaded."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Loaded dungeons:").withStyle(ChatFormatting.GOLD), false);
        for (String id : DungeonConfig.DUNGEONS.keySet()) {
            DungeonConfig.DungeonData data = DungeonConfig.DUNGEONS.get(id);
            String name = data == null || data.displayName == null || data.displayName.isBlank() ? id : data.displayName;
            String rarity = data == null ? "UNKNOWN" : data.rarity;
            boolean bound = DungeonBindingRegistry.get(id) != null;
            source.sendSuccess(() -> Component.literal("- " + id + " | " + name + " | " + rarity + (bound ? " | bound" : " | not bound")).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int beginBind(CommandSourceStack source, String dungeonId) {
        if (!DungeonConfig.DUNGEONS.containsKey(dungeonId)) {
            source.sendFailure(Component.literal("Unknown dungeon: " + dungeonId));
            return 0;
        }

        try {
            ServerPlayer player = source.getPlayerOrException();
            DungeonBindInteractionListener.beginBind(player, dungeonId);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can use /dungeon bind because you must right-click an NPC."));
            return 0;
        }
    }

    private static int cancelBind(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            boolean cancelled = DungeonBindInteractionListener.cancelBind(player);
            if (cancelled) {
                source.sendSuccess(() -> Component.literal("Cancelled pending dungeon bind.").withStyle(ChatFormatting.YELLOW), false);
            } else {
                source.sendFailure(Component.literal("You do not have a pending dungeon bind."));
            }
            return cancelled ? 1 : 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can use /dungeon bindcancel."));
            return 0;
        }
    }

    private static int unbindDungeon(CommandSourceStack source, String dungeonId) {
        boolean removed = DungeonBindingRegistry.unbind(dungeonId);
        if (!removed) {
            source.sendFailure(Component.literal("No NPC binding exists for dungeon: " + dungeonId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Unbound dungeon NPC for " + dungeonId + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int showCredits(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(() -> Component.literal("Spawn crate credits for " + player.getName().getString() + ":").withStyle(ChatFormatting.GOLD), false);
        boolean any = false;
        for (DungeonRarity rarity : DungeonRarity.values()) {
            int normal = DungeonCrateCreditManager.getNormalCredits(player.getUUID(), rarity);
            int pokemon = DungeonCrateCreditManager.getPokemonCredits(player.getUUID(), rarity);
            if (normal > 0 || pokemon > 0) {
                any = true;
                DungeonRarity lineRarity = rarity;
                source.sendSuccess(() -> Component.literal("- " + lineRarity.name() + ": " + normal + " normal, " + pokemon + " Pokemon").withStyle(lineRarity.getColor()), false);
            }
        }
        if (!any) {
            source.sendSuccess(() -> Component.literal("You do not have any spawn crate credits yet.").withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int showLimits(CommandSourceStack source, ServerPlayer player) {
        DungeonLimitManager.sendLimits(player);
        return 1;
    }

    private static int beginCrateBind(CommandSourceStack source, String rarityText, String typeText) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            DungeonRarity rarity = DungeonRarity.parse(rarityText);
            DungeonNativeCrateRegistry.CrateType type = DungeonNativeCrateRegistry.CrateType.parse(typeText);
            DungeonNativeCrateInteractionListener.beginBind(player, rarity, type);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can bind dungeon crates because you must right-click a block."));
            return 0;
        }
    }

    private static int beginCrateUnbind(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            DungeonNativeCrateInteractionListener.beginUnbind(player);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can unbind dungeon crates because you must right-click a block."));
            return 0;
        }
    }

    private static int cancelCrateAction(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            boolean cancelled = DungeonNativeCrateInteractionListener.cancel(player);
            if (cancelled) {
                source.sendSuccess(() -> Component.literal("Cancelled pending dungeon crate action.").withStyle(ChatFormatting.YELLOW), false);
            } else {
                source.sendFailure(Component.literal("You do not have a pending dungeon crate action."));
            }
            return cancelled ? 1 : 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can cancel dungeon crate actions."));
            return 0;
        }
    }

    private static int listCrates(CommandSourceStack source) {
        if (DungeonNativeCrateRegistry.getAll().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No native dungeon crates are bound yet.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Native dungeon crates:").withStyle(ChatFormatting.GOLD), false);
        for (DungeonNativeCrateRegistry.CrateBinding crate : DungeonNativeCrateRegistry.getAll().values()) {
            source.sendSuccess(() -> Component.literal("- " + crate.name + " | " + crate.world + " " + crate.x + " " + crate.y + " " + crate.z)
                    .withStyle(crate.type() == DungeonNativeCrateRegistry.CrateType.POKEMON ? ChatFormatting.LIGHT_PURPLE : crate.rarity().getColor()), false);
        }
        return 1;
    }

    private static int reloadCrateHolograms(CommandSourceStack source) {
        int count = DungeonNativeCrateRegistry.respawnAllHolograms(source.getServer());
        source.sendSuccess(() -> Component.literal("Respawned " + count + " dungeon crate nametag(s).").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int giveKeyToSelf(CommandSourceStack source, String keyId, int amount) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            return giveKeyToPlayer(source, player, keyId, amount);
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can receive dungeon keys with this command. Use /dungeon reward key <player> <keyId> <amount> from console."));
            return 0;
        }
    }

    private static int giveKeyToPlayer(CommandSourceStack source, ServerPlayer player, String keyId, int amount) {
        ItemStack stack = DungeonKeyManager.createKeyStack(keyId, amount);
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("Unknown dungeon key: " + keyId));
            return 0;
        }

        boolean added = player.getInventory().add(stack);
        if (!added) {
            player.drop(stack, false);
        }

        source.sendSuccess(() -> Component.literal("Gave " + amount + "x " + keyId + " to " + player.getName().getString() + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int rewardChest(CommandSourceStack source, ServerPlayer player, String rarityText) {
        DungeonRarity rarity = DungeonRarity.parse(rarityText);
        boolean opened = DungeonRewardManager.openPendingDungeonChest(player, rarity);
        if (!opened) {
            source.sendFailure(Component.literal("No valid completed dungeon crate reward is waiting for " + player.getName().getString() + "."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Opened one " + rarity.name() + " spawn reward crate credit for " + player.getName().getString() + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int rewardPokemonChest(CommandSourceStack source, ServerPlayer player, String rarityText) {
        DungeonRarity rarity = DungeonRarity.parse(rarityText);
        boolean opened = DungeonRewardManager.openPendingPokemonChest(player, rarity);
        if (!opened) {
            source.sendFailure(Component.literal("No valid completed dungeon Pokemon crate reward is waiting for " + player.getName().getString() + "."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Opened one " + rarity.name() + " spawn Pokemon crate credit for " + player.getName().getString() + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }


    private static int grantCrateCredits(CommandSourceStack source, ServerPlayer player, String rarityText, int normal, int pokemon) {
        DungeonRarity rarity = DungeonRarity.parse(rarityText);
        DungeonCrateCreditManager.grantCredits(player.getUUID(), rarity, normal, pokemon);
        source.sendSuccess(() -> Component.literal("Granted " + normal + " normal and " + pokemon + " Pokemon " + rarity.name() + " spawn crate credit(s) to " + player.getName().getString() + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int rewardFragments(CommandSourceStack source, ServerPlayer player, String rarityText, int min, int max) {
        DungeonRarity rarity = DungeonRarity.parse(rarityText);
        DungeonRewardManager.grantFragments(player, rarity, min, max);
        source.sendSuccess(() -> Component.literal("Rolled " + rarity.name() + " fragments for " + player.getName().getString() + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int rewardRandomTool(CommandSourceStack source, ServerPlayer player, String rarityText, double ascendedChance) {
        DungeonRarity rarity = DungeonRarity.parse(rarityText);
        DungeonRewardManager.grantRandomTool(player, rarity, ascendedChance);
        source.sendSuccess(() -> Component.literal("Rolled a " + rarity.name() + " dungeon tool for " + player.getName().getString() + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int rewardItem(CommandSourceStack source, ServerPlayer player, String itemId, int min, int max) {
        DungeonRewardManager.grantItem(player, itemId, min, max);
        source.sendSuccess(() -> Component.literal("Rolled item reward " + itemId + " for " + player.getName().getString() + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
