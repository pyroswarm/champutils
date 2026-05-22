package com.champutils.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ChestShopRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "chest_shops.json");
    private static final File TEMP_FILE = new File(DIR, "chest_shops.json.tmp");

    private static ChestShopRoot DATA = new ChestShopRoot();

    private ChestShopRegistry() {
    }

    public static synchronized void load() {
        try {
            if (!DIR.exists()) {
                DIR.mkdirs();
            }

            if (!FILE.exists()) {
                DATA = new ChestShopRoot();
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                ChestShopRoot loaded = GSON.fromJson(reader, ChestShopRoot.class);
                DATA = loaded == null ? new ChestShopRoot() : loaded;
            }

            if (DATA.shops == null) {
                DATA.shops = new HashMap<>();
            }

            sanitize();
            save();
        } catch (Exception e) {
            e.printStackTrace();
            DATA = new ChestShopRoot();
        }
    }

    public static synchronized void save() {
        try {
            if (!DIR.exists()) {
                DIR.mkdirs();
            }

            try (FileWriter writer = new FileWriter(TEMP_FILE)) {
                GSON.toJson(DATA, writer);
            }

            Files.move(
                    TEMP_FILE.toPath(),
                    FILE.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (Exception atomicMoveFailed) {
            try {
                Files.move(
                        TEMP_FILE.toPath(),
                        FILE.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static synchronized ChestShop getAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        return DATA.shops.get(key(level, pos));
    }

    public static synchronized ChestShop createOrUpdate(
            ShopMode mode,
            ServerLevel level,
            BlockPos pos,
            UUID ownerId,
            String ownerName,
            ItemStack template,
            int amount,
            long price
    ) {
        if (mode == null || level == null || pos == null || ownerId == null || template == null || template.isEmpty()) {
            return null;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(template.getItem());
        if (itemId == null) {
            return null;
        }

        ChestShop shop = new ChestShop();
        shop.mode = mode.name();
        shop.dimension = level.dimension().location().toString();
        shop.x = pos.getX();
        shop.y = pos.getY();
        shop.z = pos.getZ();
        shop.ownerId = ownerId.toString();
        shop.ownerName = ownerName == null ? "Unknown" : ownerName;
        shop.itemId = itemId.toString();
        shop.itemName = template.getHoverName().getString();
        shop.amount = Math.max(1, amount);
        shop.price = Math.max(1L, price);
        shop.createdAt = Instant.now().toString();
        shop.updatedAt = shop.createdAt;

        DATA.shops.put(key(level, pos), shop);
        save();
        return shop;
    }

    public static synchronized boolean remove(ServerLevel level, BlockPos pos, UUID requester, boolean admin) {
        if (level == null || pos == null) {
            return false;
        }

        String key = key(level, pos);
        ChestShop shop = DATA.shops.get(key);
        if (shop == null) {
            return false;
        }

        if (!admin && (requester == null || !shop.isOwner(requester))) {
            return false;
        }

        DATA.shops.remove(key);
        save();
        return true;
    }

    public static synchronized boolean isShopBlock(ServerLevel level, BlockPos pos) {
        return getAt(level, pos) != null;
    }

    public static synchronized void cleanupStaleShop(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }

        ChestShop shop = getAt(level, pos);
        if (shop == null) {
            return;
        }

        if (!isValidShopContainer(level, pos)) {
            DATA.shops.remove(key(level, pos));
            save();
        }
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

    public static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    private static void sanitize() {
        Iterator<Map.Entry<String, ChestShop>> iterator = DATA.shops.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ChestShop> entry = iterator.next();
            ChestShop shop = entry.getValue();
            if (shop == null || shop.itemId == null || shop.ownerId == null || shop.mode == null) {
                iterator.remove();
                continue;
            }
            if (shop.amount <= 0) shop.amount = 1;
            if (shop.price <= 0L) shop.price = 1L;
        }
    }

    public static final class ChestShopRoot {
        public Map<String, ChestShop> shops = new HashMap<>();
    }

    public enum ShopMode {
        SELL,
        BUY
    }

    public static final class ChestShop {
        public String mode = ShopMode.SELL.name();
        public String dimension;
        public int x;
        public int y;
        public int z;
        public String ownerId;
        public String ownerName;
        public String itemId;
        public String itemName;
        public int amount = 1;
        public long price = 1L;
        public String createdAt;
        public String updatedAt;

        public ShopMode mode() {
            try {
                return ShopMode.valueOf(mode == null ? "SELL" : mode.toUpperCase());
            } catch (Exception ignored) {
                return ShopMode.SELL;
            }
        }

        public UUID ownerUuid() {
            try {
                return UUID.fromString(ownerId);
            } catch (Exception ignored) {
                return null;
            }
        }

        public boolean isOwner(UUID uuid) {
            UUID owner = ownerUuid();
            return owner != null && owner.equals(uuid);
        }

        public Item item() {
            try {
                return BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            } catch (Exception ignored) {
                return null;
            }
        }

        public ItemStack templateStack() {
            Item item = item();
            if (item == null) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item, Math.max(1, amount));
        }

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }
}
