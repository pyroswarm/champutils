package com.champutils.commands;

import com.champutils.menu.ProfessionBackpackMenu;
import com.champutils.profession.ProfessionBackpackConfig;
import com.champutils.profession.ProfessionBackpackManager;
import com.champutils.profession.ProfessionType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

public final class BackpackCommand {
    private BackpackCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(root("backpack"));
            dispatcher.register(root("bp"));
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ProfessionBackpackMenu.open(player);
                    return 1;
                })
                .then(Commands.literal("toggle")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            boolean enabled = ProfessionBackpackManager.toggleAutopickup(player);
                            player.sendSystemMessage(Component.literal(enabled
                                    ? "§aProfession backpack autopickup enabled."
                                    : "§cProfession backpack autopickup disabled."));
                            return 1;
                        }))
                .then(Commands.literal("list")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            listStored(player);
                            return 1;
                        }))
                .then(Commands.literal("search")
                        .then(Commands.argument("item", StringArgumentType.greedyString())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ProfessionBackpackMenu.open(player, StringArgumentType.getString(context, "item"));
                                    return 1;
                                })))
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.literal("allow")
                                .then(Commands.argument("item", StringArgumentType.string())
                                        .then(Commands.argument("profession", StringArgumentType.word())
                                                .executes(context -> allow(context.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(context, "item"),
                                                        StringArgumentType.getString(context, "profession"),
                                                        null))
                                                .then(Commands.argument("displayName", StringArgumentType.greedyString())
                                                        .executes(context -> allow(context.getSource().getPlayerOrException(),
                                                                StringArgumentType.getString(context, "item"),
                                                                StringArgumentType.getString(context, "profession"),
                                                                StringArgumentType.getString(context, "displayName")))))))
                        .then(Commands.literal("enable")
                                .then(Commands.argument("item", StringArgumentType.string())
                                        .executes(context -> setEnabled(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "item"), true))))
                        .then(Commands.literal("disable")
                                .then(Commands.argument("item", StringArgumentType.string())
                                        .executes(context -> setEnabled(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "item"), false))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("item", StringArgumentType.string())
                                        .executes(context -> remove(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "item"))))));
    }

    private static void listStored(ServerPlayer player) {
        Map<String, Long> balances = ProfessionBackpackManager.balances(player);
        player.sendSystemMessage(Component.literal("§6Profession Backpack Items:"));
        ProfessionBackpackConfig.CONFIG.items.values().stream()
                .filter(data -> data != null && data.enabled && ProfessionBackpackConfig.isBackpackProfession(data.profession))
                .filter(data -> balances.getOrDefault(data.item, 0L) > 0L)
                .sorted(Comparator.comparing(data -> data.displayName.toLowerCase(Locale.ROOT)))
                .limit(40)
                .forEach(data -> player.sendSystemMessage(Component.literal("§7- §e" + data.displayName + "§7: §a" + balances.getOrDefault(data.item, 0L))));
    }

    private static int allow(ServerPlayer player, String item, String professionRaw, String displayName) {
        ProfessionType profession = ProfessionBackpackManager.parseProfession(professionRaw);
        if (!ProfessionBackpackConfig.isBackpackProfession(profession)) {
            player.sendSystemMessage(Component.literal("§cProfession must be MINING, FORESTRY, or FARMING."));
            return 0;
        }
        String normalizedItem = ProfessionBackpackConfig.normalizeItem(item);
        if (!isRegisteredItem(normalizedItem)) {
            player.sendSystemMessage(Component.literal("§cUnknown item id: " + normalizedItem + ". Use a literal item code like minecraft:stick or cobblemon:dawn_stone."));
            return 0;
        }
        if (ProfessionBackpackConfig.allowItem(normalizedItem, profession, displayName)) {
            player.sendSystemMessage(Component.literal("§aAllowed backpack item §f" + normalizedItem + "§a under §f" + profession.name() + "§a."));
            return 1;
        }
        player.sendSystemMessage(Component.literal("§cCould not allow that item."));
        return 0;
    }

    private static boolean isRegisteredItem(String itemId) {
        try {
            if (itemId == null || itemId.isBlank()) return false;
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            return item != null && item != Items.AIR;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int setEnabled(ServerPlayer player, String item, boolean enabled) {
        if (ProfessionBackpackConfig.setEnabled(item, enabled)) {
            player.sendSystemMessage(Component.literal((enabled ? "§aEnabled " : "§cDisabled ") + ProfessionBackpackConfig.normalizeItem(item) + " in the backpack config."));
            return 1;
        }
        player.sendSystemMessage(Component.literal("§cThat item is not in profession_backpack.json."));
        return 0;
    }

    private static int remove(ServerPlayer player, String item) {
        if (ProfessionBackpackConfig.removeItem(item)) {
            player.sendSystemMessage(Component.literal("§aRemoved §f" + ProfessionBackpackConfig.normalizeItem(item) + "§a from profession_backpack.json."));
            return 1;
        }
        player.sendSystemMessage(Component.literal("§cThat item is not in profession_backpack.json."));
        return 0;
    }
}
