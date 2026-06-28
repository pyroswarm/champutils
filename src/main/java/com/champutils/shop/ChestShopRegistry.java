package com.champutils.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.champutils.profile.PlayerProfileManager;

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
import java.util.ArrayList;
import java.util.List;

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

        ChestShop direct = DATA.shops.get(key(level, pos));
        if (direct != null) {
            return direct;
        }

        BlockPos connected = ChestShopContainers.connectedContainerPos(level, pos);
        if (connected != null) {
            return DATA.shops.get(key(level, connected));
        }

        return null;
    }

    public static synchronized BlockPos getShopStoragePos(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return pos;
        }

        if (DATA.shops.containsKey(key(level, pos))) {
            return pos;
        }

        BlockPos connected = ChestShopContainers.connectedContainerPos(level, pos);
        if (connected != null && DATA.shops.containsKey(key(level, connected))) {
            return connected;
        }

        return pos;
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
        shop.ownerProfileId = PlayerProfileManager.activeProfileId(ownerId).toString();
        shop.ownerName = ownerName == null ? "Unknown" : ownerName;
        shop.itemId = itemId.toString();
        shop.itemName = template.getHoverName().getString();
        shop.amount = Math.max(1, amount);
        shop.price = Math.max(1L, price);
        shop.createdAt = Instant.now().toString();
        shop.updatedAt = shop.createdAt;

        BlockPos storagePos = getShopStoragePos(level, pos);
        shop.x = storagePos.getX();
        shop.y = storagePos.getY();
        shop.z = storagePos.getZ();

        ChestShop previous = DATA.shops.get(key(level, storagePos));
        if (previous != null) {
            shop.displayEntityId = previous.displayEntityId;
        }

        DATA.shops.put(key(level, storagePos), shop);
        save();
        ChestShopDisplayManager.updateShop(level, shop);
        return shop;
    }

    public static synchronized boolean remove(ServerLevel level, BlockPos pos, UUID requester, boolean admin) {
        if (level == null || pos == null) {
            return false;
        }

        BlockPos storagePos = getShopStoragePos(level, pos);
        String key = key(level, storagePos);
        ChestShop shop = DATA.shops.get(key);
        if (shop == null) {
            return false;
        }

        if (!admin && (requester == null || !shop.isOwner(requester))) {
            return false;
        }

        ChestShopDisplayManager.removeDisplay(level.getServer(), shop);
        DATA.shops.remove(key);
        save();
        return true;
    }

    public static synchronized boolean isShopBlock(ServerLevel level, BlockPos pos) {
        return getAt(level, pos) != null;
    }

    public static synchronized List<ChestShop> getAll() {
        return new ArrayList<>(DATA.shops.values());
    }

    public static synchronized void updateDisplayId(ChestShop shop, UUID displayEntityId) {
        if (shop == null) {
            return;
        }
        shop.displayEntityId = displayEntityId == null ? null : displayEntityId.toString();
        save();
    }

    public static synchronized void cleanupStaleShop(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }

        ChestShop shop = getAt(level, pos);
        if (shop == null) {
            return;
        }

        BlockPos storagePos = getShopStoragePos(level, pos);
        if (!isValidShopContainer(level, storagePos)) {
            ChestShopDisplayManager.removeDisplay(level.getServer(), shop);
            DATA.shops.remove(key(level, storagePos));
            save();
        }
    }

    public static boolean isValidShopContainer(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }

        return ChestShopContainers.isValidShopContainer(level, pos);
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
        /** Minecraft account UUID of the shop owner. Kept for ownership checks and online notifications. */
        public String ownerId;
        /** Profile UUID whose economy balance backs this shop. Older shops may not have this yet. */
        public String ownerProfileId;
        public String ownerName;
        public String itemId;
        public String itemName;
        public int amount = 1;
        public long price = 1L;
        public String createdAt;
        public String updatedAt;
        public String displayEntityId;

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

        public UUID ownerProfileUuid() {
            try {
                return ownerProfileId == null || ownerProfileId.isBlank() ? null : UUID.fromString(ownerProfileId);
            } catch (Exception ignored) {
                return null;
            }
        }

        public void setOwnerProfileUuid(UUID profileId) {
            ownerProfileId = profileId == null ? null : profileId.toString();
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

        public UUID displayUuid() {
            try {
                return displayEntityId == null || displayEntityId.isBlank() ? null : UUID.fromString(displayEntityId);
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
