package com.champutils.profession;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

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
         IMPORTANT:
         Plain fishing rods should never be treated as custom tools by item type.
         Custom fishing tools are detected only by ChampUtilsToolId.
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

        return ProfessionToolConfig.TOOLS.get(
                toolId
        );
    }

    public static boolean hasPassive(
            ItemStack stack,
            String passive
    ) {

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

        ProfessionToolConfig.ToolData data =
                getToolData(
                        stack
                );

        if (
                data == null ||
                        data.stats == null ||
                        stat == null
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