package com.champutils.profession;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

public class ProfessionToolUtil {

    private ProfessionToolUtil() {
    }

    public static String getToolId(
            ItemStack stack
    ) {

        if (
                stack == null ||
                        stack.isEmpty()
        ) {
            return null;
        }

        String metadataToolId =
                ProfessionToolMetadata.getToolId(
                        stack
                );

        if (
                metadataToolId != null &&
                        !metadataToolId.isBlank()
        ) {
            return metadataToolId;
        }

        CustomData customData =
                stack.get(
                        DataComponents.CUSTOM_DATA
                );

        if (customData != null) {

            CompoundTag tag =
                    customData.copyTag();

            if (
                    tag.contains(
                            "ChampUtilsToolId"
                    )
            ) {
                return tag.getString(
                        "ChampUtilsToolId"
                );
            }
        }

        Item item =
                stack.getItem();

        /*
         Plain fishing rods should never be treated as custom tools by item type.
         Custom fishing tools are detected only by stored tool ID.
        */
        if (
                item instanceof FishingRodItem
        ) {
            return null;
        }

        for (
                Map.Entry<String, Item> entry :
                ProfessionToolManager
                        .getRegisteredTools()
                        .entrySet()
        ) {

            if (
                    entry.getValue() == item
            ) {
                return entry.getKey();
            }
        }

        return null;
    }

    public static boolean isIdentified(
            ItemStack stack
    ) {

        String toolId =
                getToolId(
                        stack
                );

        if (toolId == null) {
            return false;
        }

        return ProfessionToolMetadata.isIdentified(
                stack
        );
    }


    public static boolean isUsableProfessionTool(
            ServerPlayer player,
            ItemStack stack,
            ProfessionType requiredProfession
    ) {

        if (
                player == null ||
                        stack == null ||
                        stack.isEmpty() ||
                        !ProfessionToolMetadata.isProfessionTool(stack) ||
                        !ProfessionToolMetadata.isIdentified(stack) ||
                        ProfessionToolMetadata.isBroken(stack)
        ) {
            return false;
        }

        String toolId =
                getToolId(stack);

        if (toolId == null) {
            return false;
        }

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolConfig.TOOLS.get(toolId);

        if (toolData == null || toolData.profession == null) {
            return false;
        }

        ProfessionType toolProfession;

        try {
            toolProfession =
                    ProfessionType.valueOf(
                            toolData.profession.toUpperCase()
                    );
        }
        catch (Exception ignored) {
            return false;
        }

        if (requiredProfession != null && toolProfession != requiredProfession) {
            return false;
        }

        return ProfessionManager.getLevel(player, toolProfession) >= toolData.requiredLevel;
    }

    public static ProfessionToolConfig.ToolData getToolData(
            ItemStack stack
    ) {

        String toolId =
                getToolId(
                        stack
                );

        if (toolId == null) {
            return null;
        }

        if (
                !ProfessionToolMetadata.isIdentified(
                        stack
                )
        ) {
            return null;
        }

        return ProfessionToolConfig.TOOLS.get(
                toolId
        );
    }

    public static ProfessionToolConfig.ToolData getToolDataAllowUnidentified(
            ItemStack stack
    ) {

        String toolId =
                getToolId(
                        stack
                );

        if (toolId == null) {
            return null;
        }

        return ProfessionToolConfig.TOOLS.get(
                toolId
        );
    }

    public static boolean hasPassive(
            ItemStack stack,
            String passive
    ) {

        if (
                !ProfessionToolMetadata.isIdentified(
                        stack
                )
        ) {
            return false;
        }

        ProfessionToolConfig.ToolData data =
                getToolData(
                        stack
                );

        if (
                data == null ||
                        data.passives == null ||
                        passive == null
        ) {
            return false;
        }

        for (
                String p :
                data.passives
        ) {

            if (
                    p != null &&
                            p.equalsIgnoreCase(
                                    passive
                            )
            ) {
                return true;
            }
        }

        return false;
    }

    public static double getStat(
            ItemStack stack,
            String stat
    ) {

        if (
                !ProfessionToolMetadata.isIdentified(
                        stack
                )
        ) {
            return 0D;
        }

        if (stat == null) {
            return 0D;
        }

        Map<String, Double> rolledStats =
                ProfessionToolMetadata.getRolledStats(
                        stack
                );

        Double rolledValue =
                rolledStats.get(
                        stat
                );

        if (rolledValue != null) {
            return rolledValue;
        }

        ProfessionToolConfig.ToolData data =
                getToolData(
                        stack
                );

        if (
                data == null ||
                        data.stats == null
        ) {
            return 0D;
        }

        Double value =
                data.stats.get(
                        stat
                );

        return value == null
                ? 0D
                : value;
    }
}
