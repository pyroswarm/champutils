package com.champutils.commands;

import com.champutils.permissions.PermissionUtil;
import com.champutils.profession.ProfessionToolMetadata;
import com.champutils.profession.ProfessionToolUtil;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ItemDebugCommand {

    private ItemDebugCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("itemdebug")
                .requires(source -> PermissionUtil.has(source, "champutils.admin") || PermissionUtil.has(source, "champutils.itemdebug"))
                .executes(context -> held(context.getSource().getPlayerOrException()))
                .then(Commands.literal("held").executes(context -> held(context.getSource().getPlayerOrException())))
                .then(Commands.literal("compare").executes(context -> compare(context.getSource().getPlayerOrException())))
                .then(Commands.literal("clean").executes(context -> clean(context.getSource().getPlayerOrException())))
                .then(Commands.literal("log").executes(context -> log(context.getSource().getPlayerOrException()))));
    }

    private static int held(ServerPlayer player) {
        sendStackReport(player, "Main hand", player.getItemInHand(InteractionHand.MAIN_HAND));
        return 1;
    }

    private static int compare(ServerPlayer player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        sendStackReport(player, "Main hand", main);
        sendStackReport(player, "Off hand", off);
        player.sendSystemMessage(Component.literal("Same item: " + ItemStack.isSameItem(main, off)).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Same item + components: " + ItemStack.isSameItemSameComponents(main, off)).withStyle(ItemStack.isSameItemSameComponents(main, off) ? ChatFormatting.GREEN : ChatFormatting.RED));
        return 1;
    }


    private static int clean(ServerPlayer player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        boolean changed = ProfessionToolMetadata.cleanupInvalidProfessionMetadata(main);
        player.sendSystemMessage(Component.literal(changed
                ? "Removed invalid empty ChampUtilsProfessionTool custom data from held item."
                : "No invalid empty ChampUtilsProfessionTool custom data found on held item.").withStyle(changed ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        sendStackReport(player, "Main hand", main);
        return 1;
    }

    private static int log(ServerPlayer player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        String report = buildReport("Main hand", main).replace('§', '&');
        System.out.println("[ChampUtils][ItemDebug] player=" + player.getGameProfile().getName() + " " + report);
        player.sendSystemMessage(Component.literal("Item debug written to server console.").withStyle(ChatFormatting.GREEN));
        sendStackReport(player, "Main hand", main);
        return 1;
    }

    private static void sendStackReport(ServerPlayer player, String label, ItemStack stack) {
        String[] lines = buildReport(label, stack).split("\\n");
        for (String line : lines) {
            player.sendSystemMessage(Component.literal(line));
        }
    }

    private static String buildReport(String label, ItemStack stack) {
        StringBuilder out = new StringBuilder();
        out.append("§6[ItemDebug] §e").append(label).append('\n');
        if (stack == null || stack.isEmpty()) {
            out.append("§7empty");
            return out.toString();
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        int componentCount = stack.getComponents().size();
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag customTag = customData.copyTag();
        boolean professionTool = ProfessionToolMetadata.isProfessionTool(stack);
        String metadataToolId = ProfessionToolMetadata.getToolId(stack);
        String utilToolId = ProfessionToolUtil.getToolId(stack);

        out.append("§7Item: §f").append(itemId).append(" §8x").append(stack.getCount()).append('\n');
        out.append("§7Components: §f").append(componentCount).append('\n');
        out.append("§7Max stack size: §f").append(stack.getMaxStackSize()).append('\n');
        out.append("§7Profession metadata: §f").append(professionTool).append('\n');
        out.append("§7Metadata tool id: §f").append(metadataToolId == null ? "none" : metadataToolId).append('\n');
        out.append("§7Resolved tool id: §f").append(utilToolId == null ? "none" : utilToolId).append('\n');
        out.append("§7Has custom data: §f").append(!customTag.isEmpty()).append('\n');
        out.append("§7Custom data: §f").append(customTag.isEmpty() ? "{}" : customTag.toString()).append('\n');
        out.append("§7Component map: §f").append(stack.getComponents());
        return out.toString();
    }
}
