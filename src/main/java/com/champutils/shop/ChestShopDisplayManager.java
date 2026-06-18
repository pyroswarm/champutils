package com.champutils.shop;

import com.champutils.economy.EconomyManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Display.TextDisplay;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class ChestShopDisplayManager {

    private static final String DISPLAY_TAG = "champutils_chestshop_display";
    private static int nextShopIndex = 0;
    private static final int SYNC_INTERVAL_TICKS = 200;
    private static final int MAX_SHOPS_PER_SYNC = 10;

    private ChestShopDisplayManager() {
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) {
            return;
        }

        for (ChestShopRegistry.ChestShop shop : ChestShopRegistry.getAll()) {
            ServerLevel level = levelFor(server, shop);
            if (level == null) {
                continue;
            }
            if (!ChestShopRegistry.isValidShopContainer(level, shop.pos())) {
                continue;
            }
            updateShop(level, shop);
        }
    }

    public static void tick(MinecraftServer server) {
        if (server == null || server.getTickCount() % SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        syncBatch(server);
    }

    private static void syncBatch(MinecraftServer server) {
        java.util.List<ChestShopRegistry.ChestShop> shops = new java.util.ArrayList<>(ChestShopRegistry.getAll());
        if (shops.isEmpty()) {
            nextShopIndex = 0;
            return;
        }

        int checked = 0;
        int total = shops.size();
        while (checked < Math.min(MAX_SHOPS_PER_SYNC, total)) {
            if (nextShopIndex >= total) {
                nextShopIndex = 0;
            }

            ChestShopRegistry.ChestShop shop = shops.get(nextShopIndex);
            nextShopIndex = (nextShopIndex + 1) % total;
            checked++;

            ServerLevel level = levelFor(server, shop);
            if (level == null) {
                continue;
            }
            if (!ChestShopRegistry.isValidShopContainer(level, shop.pos())) {
                continue;
            }
            updateShop(level, shop);
        }
    }

    public static void updateShop(ServerLevel level, ChestShopRegistry.ChestShop shop) {
        if (level == null || shop == null) {
            return;
        }

        Vec3 position = displayPosition(level, shop.pos());
        TextDisplay display = getExistingDisplay(level, shop, position);
        if (display == null) {
            display = EntityType.TEXT_DISPLAY.create(level);
            if (display == null) {
                return;
            }
            display.addTag(DISPLAY_TAG);
            display.setNoGravity(true);
            display.setInvulnerable(true);
            level.addFreshEntity(display);
        }

        display.setPos(position.x, position.y, position.z);
        display.setText(displayText(shop));
        applyDisplayOptions(level, display, shop.pos());

        UUID displayId = display.getUUID();
        if (!displayId.equals(shop.displayUuid())) {
            ChestShopRegistry.updateDisplayId(shop, displayId);
        }
    }

    public static void removeDisplay(MinecraftServer server, ChestShopRegistry.ChestShop shop) {
        if (server == null || shop == null) {
            return;
        }
        ServerLevel level = levelFor(server, shop);
        if (level == null) {
            return;
        }
        removeDisplay(level, shop);
    }

    public static void removeDisplay(ServerLevel level, ChestShopRegistry.ChestShop shop) {
        if (level == null || shop == null) {
            return;
        }

        UUID displayId = shop.displayUuid();
        if (displayId != null) {
            Entity entity = level.getEntity(displayId);
            if (entity != null) {
                entity.discard();
            }
        }

        Vec3 position = displayPosition(level, shop.pos());
        AABB box = AABB.ofSize(position, 3.0D, 3.0D, 3.0D);
        for (TextDisplay display : level.getEntitiesOfClass(TextDisplay.class, box, entity -> entity.getTags().contains(DISPLAY_TAG))) {
            display.discard();
        }
    }

    private static TextDisplay getExistingDisplay(ServerLevel level, ChestShopRegistry.ChestShop shop, Vec3 position) {
        UUID displayId = shop.displayUuid();
        if (displayId != null) {
            Entity entity = level.getEntity(displayId);
            if (entity instanceof TextDisplay textDisplay && entity.isAlive()) {
                return textDisplay;
            }
        }

        AABB box = AABB.ofSize(position, 2.0D, 2.0D, 2.0D);
        for (TextDisplay display : level.getEntitiesOfClass(TextDisplay.class, box, entity -> entity.getTags().contains(DISPLAY_TAG))) {
            return display;
        }

        return null;
    }

    private static void applyDisplayOptions(ServerLevel level, TextDisplay display, BlockPos storagePos) {
        invokeOptional(display, "setLineWidth", int.class, 220);
        invokeOptional(display, "setSeeThrough", boolean.class, false);
        invokeOptional(display, "setDefaultBackground", boolean.class, true);
        invokeOptional(display, "setBackgroundColor", int.class, 0x66000000);
        invokeOptional(display, "setTextOpacity", byte.class, (byte) 255);

        try {
            Class<?> displayClass = Class.forName("net.minecraft.world.entity.Display");
            Class<?> billboardClass = Class.forName("net.minecraft.world.entity.Display$BillboardConstraints");
            Object fixed = Enum.valueOf((Class<Enum>) billboardClass.asSubclass(Enum.class), "FIXED");
            displayClass.getMethod("setBillboardConstraints", billboardClass).invoke(display, fixed);
            Direction facing = chestFacing(level, storagePos);
            display.setYRot(facing.toYRot() + 180.0F);
            display.setYHeadRot(display.getYRot());
        } catch (Exception ignored) {
        }
    }

    private static void invokeOptional(Object target, String method, Class<?> parameterType, Object value) {
        try {
            target.getClass().getMethod(method, parameterType).invoke(target, value);
        } catch (Exception ignored) {
        }
    }

    private static Component displayText(ChestShopRegistry.ChestShop shop) {
        boolean selling = shop.mode() == ChestShopRegistry.ShopMode.SELL;
        String line1 = selling ? "SELL SHOP" : "BUY SHOP";
        String line2 = Math.max(1, shop.amount) + "x " + shop.itemName;
        String line3 = EconomyManager.format(shop.price);
        String line4 = shop.ownerName == null ? "Unknown" : shop.ownerName;

        return Component.literal(line1).withStyle(selling ? ChatFormatting.GREEN : ChatFormatting.AQUA)
                .append(Component.literal("\n" + line2).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n" + line3).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n" + line4).withStyle(ChatFormatting.GRAY));
    }

    private static Vec3 displayPosition(ServerLevel level, BlockPos storagePos) {
        BlockPos connected = ChestShopContainers.connectedContainerPos(level, storagePos);
        Direction facing = chestFacing(level, storagePos);
        double x = storagePos.getX() + 0.5D;
        double y = storagePos.getY() + 1.35D;
        double z = storagePos.getZ() + 0.5D;

        if (connected != null) {
            x = (x + connected.getX() + 0.5D) / 2.0D;
            z = (z + connected.getZ() + 0.5D) / 2.0D;
        }

        x += facing.getStepX() * 0.62D;
        z += facing.getStepZ() * 0.62D;
        return new Vec3(x, y, z);
    }

    private static Direction chestFacing(ServerLevel level, BlockPos storagePos) {
        try {
            BlockState state = level.getBlockState(storagePos);
            if (state.hasProperty(ChestBlock.FACING)) return state.getValue(ChestBlock.FACING);
            if (state.hasProperty(BarrelBlock.FACING)) return state.getValue(BarrelBlock.FACING);
        } catch (Throwable ignored) {
        }
        return Direction.NORTH;
    }

    private static ServerLevel levelFor(MinecraftServer server, ChestShopRegistry.ChestShop shop) {
        if (server == null || shop == null || shop.dimension == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(shop.dimension)) {
                return level;
            }
        }
        return null;
    }
}
