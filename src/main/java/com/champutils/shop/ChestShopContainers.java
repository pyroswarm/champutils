package com.champutils.shop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ChestShopContainers {

    private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[] {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };

    private ChestShopContainers() {
    }

    public static BlockPos connectedContainerPos(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !looksLikeChest(level, pos)) {
            return null;
        }

        String stateText = level.getBlockState(pos).toString().toLowerCase();
        String type = propertyValue(stateText, "type");
        String facing = propertyValue(stateText, "facing");

        if (type == null || facing == null || "single".equals(type)) {
            return null;
        }

        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos otherPos = pos.relative(direction);
            if (!looksLikeChest(level, otherPos)) {
                continue;
            }

            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.getBlock() != level.getBlockState(pos).getBlock()) {
                continue;
            }

            String otherText = otherState.toString().toLowerCase();
            String otherType = propertyValue(otherText, "type");
            String otherFacing = propertyValue(otherText, "facing");

            if (otherType == null || otherFacing == null || "single".equals(otherType)) {
                continue;
            }
            if (!facing.equals(otherFacing)) {
                continue;
            }
            if (type.equals(otherType)) {
                continue;
            }

            return otherPos;
        }

        return null;
    }

    public static Container containerFor(ServerLevel level, BlockPos pos) {
        Container primary = singleContainer(level, pos);
        if (primary == null) {
            return null;
        }

        BlockPos connectedPos = connectedContainerPos(level, pos);
        if (connectedPos == null) {
            return primary;
        }

        Container secondary = singleContainer(level, connectedPos);
        if (secondary == null) {
            return primary;
        }

        return new JoinedContainer(primary, secondary);
    }

    public static boolean isValidShopContainer(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) {
            return false;
        }

        String id = blockId.toString().toLowerCase();
        return (id.contains("chest") || id.contains("barrel")) && !id.contains("ender_chest");
    }

    private static boolean looksLikeChest(ServerLevel level, BlockPos pos) {
        if (!isValidShopContainer(level, pos)) {
            return false;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return blockId != null && blockId.toString().toLowerCase().contains("chest") && !blockId.toString().toLowerCase().contains("ender_chest");
    }

    private static Container singleContainer(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container container ? container : null;
    }

    private static String propertyValue(String stateText, String property) {
        String needle = property + "=";
        int start = stateText.indexOf(needle);
        if (start < 0) {
            return null;
        }
        start += needle.length();
        int end = stateText.indexOf(',', start);
        int bracket = stateText.indexOf(']', start);
        if (end < 0 || (bracket >= 0 && bracket < end)) {
            end = bracket;
        }
        if (end < 0) {
            end = stateText.length();
        }
        return stateText.substring(start, end).trim();
    }

    private static final class JoinedContainer implements Container {
        private final Container first;
        private final Container second;

        private JoinedContainer(Container first, Container second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public int getContainerSize() {
            return first.getContainerSize() + second.getContainerSize();
        }

        @Override
        public boolean isEmpty() {
            return first.isEmpty() && second.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            SlotRef ref = ref(slot);
            return ref == null ? ItemStack.EMPTY : ref.container.getItem(ref.slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            SlotRef ref = ref(slot);
            if (ref == null) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = ref.container.getItem(ref.slot);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack removed = stack.split(amount);
            if (stack.isEmpty()) {
                ref.container.setItem(ref.slot, ItemStack.EMPTY);
            } else {
                ref.container.setItem(ref.slot, stack);
            }
            ref.container.setChanged();
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            SlotRef ref = ref(slot);
            if (ref == null) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = ref.container.getItem(ref.slot);
            ref.container.setItem(ref.slot, ItemStack.EMPTY);
            ref.container.setChanged();
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            SlotRef ref = ref(slot);
            if (ref == null) {
                return;
            }
            ref.container.setItem(ref.slot, stack);
            ref.container.setChanged();
        }

        @Override
        public void setChanged() {
            first.setChanged();
            second.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return first.stillValid(player) && second.stillValid(player);
        }

        @Override
        public void clearContent() {
            first.clearContent();
            second.clearContent();
            setChanged();
        }

        private SlotRef ref(int slot) {
            if (slot < 0) {
                return null;
            }
            int firstSize = first.getContainerSize();
            if (slot < firstSize) {
                return new SlotRef(first, slot);
            }
            int secondSlot = slot - firstSize;
            if (secondSlot < second.getContainerSize()) {
                return new SlotRef(second, secondSlot);
            }
            return null;
        }
    }

    private record SlotRef(Container container, int slot) {
    }
}
