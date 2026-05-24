package com.champutils.territory;

import com.champutils.database.DatabaseManager;
import com.champutils.network.NetworkServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TerritoryRepository {

    public enum OwnerType { PLAYER, GUILD }
    public enum TrustLevel { VISITOR, TRUSTED, MANAGER, BANNED }

    public static final class Territory {
        public UUID id;
        public OwnerType ownerType;
        public String ownerId;
        public String ownerName;
        public String serverId;
        public String worldName;
        public String worldKey;
        public int slotIndex;
        public String generationState;
        public int centerX;
        public int centerZ;
        public int radius;
        public int minX;
        public int maxX;
        public int minZ;
        public int maxZ;
        public double spawnX;
        public double spawnY;
        public double spawnZ;
        public float spawnYaw;
        public float spawnPitch;
        public int level;
        public String biomePreference;

        public boolean isPublic;
        public boolean allowVisitors;
        public boolean visitorsCanBuild;
        public boolean visitorsCanOpenContainers;
        public boolean visitorsCanInteractEntities;
        public boolean visitorsCanUseRedstone;
        public boolean lockBorder;

        public boolean contains(String serverId, String worldName, BlockPos pos) {
            if (pos == null) return false;
            return this.serverId.equalsIgnoreCase(serverId)
                    && this.worldName.equalsIgnoreCase(worldName)
                    && pos.getX() >= minX
                    && pos.getX() <= maxX
                    && pos.getZ() >= minZ
                    && pos.getZ() <= maxZ;
        }

        public boolean isTerritoryWorld(String serverId, String worldName) {
            return this.serverId.equalsIgnoreCase(serverId) && this.worldName.equalsIgnoreCase(worldName);
        }

        public boolean isReady() {
            return generationState == null || generationState.isBlank() || generationState.equalsIgnoreCase("READY");
        }

        public String displayType() {
            return ownerType == OwnerType.GUILD ? "Guild" : "Personal";
        }
    }

    private static final Map<UUID, Territory> TERRITORIES = new ConcurrentHashMap<>();
    private static final Map<String, UUID> OWNER_INDEX = new ConcurrentHashMap<>();
    private static final Map<String, TrustLevel> TRUST = new ConcurrentHashMap<>();

    private TerritoryRepository() {}

    public static void refreshAll() {
        if (!DatabaseManager.isEnabled()) return;

        DatabaseManager.executeAsync("refresh territory cache", connection -> {
            Map<UUID, Territory> fresh = new ConcurrentHashMap<>();
            Map<String, UUID> owners = new ConcurrentHashMap<>();
            Map<String, TrustLevel> trust = new ConcurrentHashMap<>();

            try (PreparedStatement statement = connection.prepareStatement("select * from territories")) {
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        Territory t = read(rs);
                        fresh.put(t.id, t);
                        owners.put(ownerKey(t.ownerType, t.ownerId), t.id);
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("select territory_id, player_uuid, trust_level from territory_trust")) {
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        UUID territoryId = (UUID) rs.getObject("territory_id");
                        UUID playerId = (UUID) rs.getObject("player_uuid");
                        TrustLevel level = parseTrust(rs.getString("trust_level"));
                        trust.put(trustKey(territoryId, playerId), level);
                    }
                }
            }

            TERRITORIES.clear();
            TERRITORIES.putAll(fresh);
            OWNER_INDEX.clear();
            OWNER_INDEX.putAll(owners);
            TRUST.clear();
            TRUST.putAll(trust);

            System.out.println("[ChampUtils] Loaded " + TERRITORIES.size() + " territories from the database.");
        });
    }

    public static Collection<Territory> allCached() { return TERRITORIES.values(); }

    public static List<Territory> publicCached(OwnerType type) {
        List<Territory> list = new ArrayList<>();
        for (Territory t : TERRITORIES.values()) {
            if (t.ownerType == type && t.isPublic) list.add(t);
        }
        list.sort(Comparator.comparing(t -> t.ownerName.toLowerCase(Locale.ROOT)));
        return list;
    }

    public static boolean isTerritoryWorld(ServerLevel level) {
        if (level == null) return false;
        String worldName = level.dimension().location().toString();
        String serverId = NetworkServerConfig.serverId();
        for (Territory territory : TERRITORIES.values()) {
            if (territory.isTerritoryWorld(serverId, worldName)) return true;
        }
        return false;
    }

    public static Territory findAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return null;
        String worldName = level.dimension().location().toString();
        String serverId = NetworkServerConfig.serverId();
        for (Territory territory : TERRITORIES.values()) {
            if (territory.contains(serverId, worldName, pos)) return territory;
        }
        return null;
    }

    public static Territory cachedForOwner(OwnerType ownerType, String ownerId) {
        UUID territoryId = OWNER_INDEX.get(ownerKey(ownerType, ownerId));
        return territoryId == null ? null : TERRITORIES.get(territoryId);
    }

    public static void removeCachedForOwner(OwnerType ownerType, String ownerId) {
        UUID territoryId = OWNER_INDEX.remove(ownerKey(ownerType, ownerId));
        if (territoryId == null) {
            return;
        }
        TERRITORIES.remove(territoryId);
        TRUST.keySet().removeIf(key -> key.startsWith(territoryId.toString() + ":"));
    }

    public static Territory cachedPersonal(ServerPlayer player) {
        return player == null ? null : cachedForOwner(OwnerType.PLAYER, player.getUUID().toString());
    }

    public static Territory cachedGuildForPlayer(ServerPlayer player) {
        if (player == null) return null;
        com.champutils.guild.GuildRepository.GuildSnapshot guild = com.champutils.guild.GuildRepository.cachedGuild(player.getUUID());
        return guild == null ? null : cachedForOwner(OwnerType.GUILD, guild.id.toString());
    }

    public static boolean isOwnerOrGuildMember(ServerPlayer player, Territory territory) {
        if (player == null || territory == null) return false;
        UUID playerId = player.getUUID();
        if (territory.ownerType == OwnerType.PLAYER) return territory.ownerId.equalsIgnoreCase(playerId.toString());
        com.champutils.guild.GuildRepository.GuildSnapshot guild = com.champutils.guild.GuildRepository.cachedGuild(playerId);
        return guild != null && territory.ownerId.equalsIgnoreCase(guild.id.toString());
    }

    public static boolean isBanned(ServerPlayer player, Territory territory) {
        if (player == null || territory == null || player.hasPermissions(4)) return false;
        if (territory.ownerType == OwnerType.PLAYER && territory.ownerId.equalsIgnoreCase(player.getUUID().toString())) return false;
        return getTrust(territory.id, player.getUUID()) == TrustLevel.BANNED;
    }

    private static com.champutils.guild.GuildRepository.GuildSnapshot guildForTerritoryMember(ServerPlayer player, Territory territory) {
        if (player == null || territory == null || territory.ownerType != OwnerType.GUILD) return null;
        com.champutils.guild.GuildRepository.GuildSnapshot guild = com.champutils.guild.GuildRepository.cachedGuild(player.getUUID());
        if (guild == null || !territory.ownerId.equalsIgnoreCase(guild.id.toString())) return null;
        return guild;
    }

    public static boolean canManage(ServerPlayer player, Territory territory) {
        if (player == null || territory == null) return false;
        if (player.hasPermissions(4)) return true;
        UUID playerId = player.getUUID();
        if (territory.ownerType == OwnerType.PLAYER) {
            if (territory.ownerId.equalsIgnoreCase(playerId.toString())) return true;
            return getTrust(territory.id, playerId) == TrustLevel.MANAGER;
        }
        com.champutils.guild.GuildRepository.GuildSnapshot guild = com.champutils.guild.GuildRepository.cachedGuild(playerId);
        return guild != null
                && territory.ownerId.equalsIgnoreCase(guild.id.toString())
                && com.champutils.guild.GuildRepository.canManageGuildTerritory(guild.role);
    }

    public static boolean canEnter(ServerPlayer player, Territory territory) {
        if (player == null || territory == null) return true;
        if (player.hasPermissions(4)) return true;
        if (!territory.isReady()) return player.hasPermissions(4);
        if (isBanned(player, territory)) return false;
        if (isOwnerOrGuildMember(player, territory)) return true;
        TrustLevel trust = getTrust(territory.id, player.getUUID());
        if (trust != null && trust != TrustLevel.BANNED) return true;
        return territory.allowVisitors || territory.isPublic;
    }

    public static boolean canBuild(ServerPlayer player, Territory territory) {
        if (player == null || territory == null) return true;
        if (player.hasPermissions(4)) return true;
        if (isBanned(player, territory)) return false;
        if (territory.ownerType == OwnerType.PLAYER) {
            if (territory.ownerId.equalsIgnoreCase(player.getUUID().toString())) return true;
            TrustLevel trust = getTrust(territory.id, player.getUUID());
            if (trust == TrustLevel.TRUSTED || trust == TrustLevel.MANAGER) return true;
            return territory.allowVisitors && territory.visitorsCanBuild;
        }
        com.champutils.guild.GuildRepository.GuildSnapshot guild = guildForTerritoryMember(player, territory);
        if (guild != null) return com.champutils.guild.GuildRepository.canBuildInGuildTerritory(guild.role);
        TrustLevel trust = getTrust(territory.id, player.getUUID());
        if (trust == TrustLevel.TRUSTED || trust == TrustLevel.MANAGER) return true;
        return territory.allowVisitors && territory.visitorsCanBuild;
    }

    public static boolean canOpenContainers(ServerPlayer player, Territory territory) {
        if (player == null || territory == null) return true;
        if (player.hasPermissions(4)) return true;
        if (isBanned(player, territory)) return false;
        if (isOwnerOrGuildMember(player, territory)) return true;
        TrustLevel trust = getTrust(territory.id, player.getUUID());
        if (trust == TrustLevel.TRUSTED || trust == TrustLevel.MANAGER) return true;
        return territory.allowVisitors && territory.visitorsCanOpenContainers;
    }

    public static boolean canInteractEntities(ServerPlayer player, Territory territory) {
        if (player == null || territory == null) return true;
        if (player.hasPermissions(4)) return true;
        if (isBanned(player, territory)) return false;
        if (isOwnerOrGuildMember(player, territory)) return true;
        TrustLevel trust = getTrust(territory.id, player.getUUID());
        if (trust == TrustLevel.TRUSTED || trust == TrustLevel.MANAGER) return true;
        return territory.allowVisitors && territory.visitorsCanInteractEntities;
    }

    public static boolean canUseRedstone(ServerPlayer player, Territory territory) {
        if (player == null || territory == null) return true;
        if (player.hasPermissions(4)) return true;
        if (isBanned(player, territory)) return false;
        if (isOwnerOrGuildMember(player, territory)) return true;
        TrustLevel trust = getTrust(territory.id, player.getUUID());
        if (trust == TrustLevel.TRUSTED || trust == TrustLevel.MANAGER) return true;
        return territory.allowVisitors && territory.visitorsCanUseRedstone;
    }

    public static TrustLevel getTrust(UUID territoryId, UUID playerId) {
        if (territoryId == null || playerId == null) return null;
        return TRUST.get(trustKey(territoryId, playerId));
    }

    public static void setTrust(Territory territory, UUID playerId, String playerName, TrustLevel level, Callback callback) {
        if (territory == null || playerId == null || playerName == null) {
            callback.done(false, "Invalid trust request.");
            return;
        }
        DatabaseManager.executeAsync("set territory trust", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into territory_trust (territory_id, player_uuid, player_name, trust_level, created_at) " +
                            "values (?, ?, ?, ?, now()) " +
                            "on conflict (territory_id, player_uuid) do update set player_name = excluded.player_name, trust_level = excluded.trust_level"
            )) {
                statement.setObject(1, territory.id);
                statement.setObject(2, playerId);
                statement.setString(3, playerName);
                statement.setString(4, level.name());
                statement.executeUpdate();
            }
            TRUST.put(trustKey(territory.id, playerId), level);
            callback.done(true, playerName + " is now " + level.name().toLowerCase(Locale.ROOT) + " in this territory.");
        });
    }

    public static void removeTrust(Territory territory, UUID playerId, String playerName, Callback callback) {
        if (territory == null || playerId == null) {
            callback.done(false, "Invalid untrust request.");
            return;
        }
        DatabaseManager.executeAsync("remove territory trust", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("delete from territory_trust where territory_id = ? and player_uuid = ?")) {
                statement.setObject(1, territory.id);
                statement.setObject(2, playerId);
                statement.executeUpdate();
            }
            TRUST.remove(trustKey(territory.id, playerId));
            callback.done(true, "Removed " + playerName + " from this territory's visitor list.");
        });
    }

    public static void createPersonalTerritory(ServerPlayer player, String biomePreference, Callback callback) {
        if (player == null) { callback.done(false, "Only players can create territories."); return; }
        UUID ownerUuid = player.getUUID();
        String ownerId = ownerUuid.toString();
        if (cachedForOwner(OwnerType.PLAYER, ownerId) != null) { callback.done(false, "You already have a territory."); return; }
        if (biomePreference != null && !biomePreference.isBlank() && cleanBiomePreference(biomePreference) == null) { callback.done(false, "Invalid biome. Press TAB after /territory create to choose an overworld biome."); return; }

        checkRecreateCooldown(OwnerType.PLAYER, ownerId, (allowed, remainingMessage) -> {
            if (!allowed) { callback.done(false, remainingMessage); return; }
            TerritoryConfig.Data cfg = TerritoryConfig.get();
            String worldName = cfg.createPersonalInCurrentWorld ? player.serverLevel().dimension().location().toString() : null;
            Territory territory = allocate(OwnerType.PLAYER, ownerId, player.getGameProfile().getName(), worldName, biomePreference);
            save(territory, (success, message) -> {
                if (success) TerritoryWorldGenerationManager.requestGeneration(player.server, player, territory);
                callback.done(success, success ? "Territory created. " + generationMessage(territory) : message);
            });
        });
    }

    public static void ensureGuildTerritory(UUID guildId, String guildName, String biomePreference, Callback callback) {
        ensureGuildTerritory(null, guildId, guildName, biomePreference, callback);
    }

    public static void ensureGuildTerritory(MinecraftServer server, UUID guildId, String guildName, String biomePreference, Callback callback) {
        ensureGuildTerritory(server, null, guildId, guildName, biomePreference, callback);
    }

    public static void ensureGuildTerritory(MinecraftServer server, ServerPlayer initiator, UUID guildId, String guildName, String biomePreference, Callback callback) {
        if (guildId == null) { callback.done(false, "Invalid guild territory."); return; }
        String ownerId = guildId.toString();
        if (cachedForOwner(OwnerType.GUILD, ownerId) != null) { callback.done(true, "Guild territory already exists."); return; }
        if (biomePreference != null && !biomePreference.isBlank() && cleanBiomePreference(biomePreference) == null) { callback.done(false, "Invalid biome. Press TAB after /gterritory create to choose an overworld biome."); return; }
        checkRecreateCooldown(OwnerType.GUILD, ownerId, (allowed, remainingMessage) -> {
            if (!allowed) { callback.done(false, remainingMessage); return; }
            Territory territory = allocate(OwnerType.GUILD, ownerId, guildName == null ? "Guild" : guildName, null, biomePreference);
            save(territory, (success, message) -> {
                if (success) TerritoryWorldGenerationManager.requestGeneration(server, initiator, territory);
                callback.done(success, success ? "Guild territory created. " + generationMessage(territory) : message);
            });
        });
    }

    public static void setHome(Territory territory, ServerPlayer player, Callback callback) {
        if (territory == null || player == null) { callback.done(false, "Invalid territory home."); return; }
        BlockPos pos = player.blockPosition();
        if (!territory.contains(NetworkServerConfig.serverId(), player.serverLevel().dimension().location().toString(), pos)) {
            callback.done(false, "Stand inside the territory before setting its home.");
            return;
        }
        territory.spawnX = player.getX();
        territory.spawnY = player.getY();
        territory.spawnZ = player.getZ();
        territory.spawnYaw = player.getYRot();
        territory.spawnPitch = player.getXRot();
        save(territory, callback);
    }

    public static void setSetting(Territory territory, String setting, boolean value, Callback callback) {
        if (territory == null) { callback.done(false, "Invalid territory."); return; }
        String key = setting == null ? "" : setting.toLowerCase(Locale.ROOT);
        switch (key) {
            case "public", "ispublic", "is_public" -> territory.isPublic = value;
            case "visitors", "allowvisitors", "allow_visitors" -> territory.allowVisitors = value;
            case "visitorbuild", "visitorscanbuild", "visitor_build" -> territory.visitorsCanBuild = value;
            case "visitorcontainers", "containers", "visitor_containers" -> territory.visitorsCanOpenContainers = value;
            case "visitorentities", "entities", "visitor_entities" -> territory.visitorsCanInteractEntities = value;
            case "visitorredstone", "redstone", "visitor_redstone" -> territory.visitorsCanUseRedstone = value;
            case "border", "lockborder", "lock_border" -> territory.lockBorder = value;
            default -> { callback.done(false, "Unknown setting. Use public, visitors, visitorbuild, visitorcontainers, visitorentities, visitorredstone, or border."); return; }
        }
        save(territory, (success, message) -> callback.done(success, success ? "Updated territory setting: " + setting + " = " + value : message));
    }

    public static void setBiomePreference(Territory territory, String biomePreference, Callback callback) {
        if (territory == null) { callback.done(false, "Invalid territory."); return; }
        String clean = cleanBiomePreference(biomePreference);
        if (clean == null) { callback.done(false, "Invalid biome preference."); return; }
        territory.biomePreference = clean;
        save(territory, (success, message) -> callback.done(success, success ? "Biome preference set to " + prettyBiome(clean) + ". New generation will use this when the territory is assigned/generated." : message));
    }

    private static Territory allocate(OwnerType ownerType, String ownerId, String ownerName, String forcedWorldName, String biomePreference) {
        TerritoryConfig.Data cfg = TerritoryConfig.get();
        int slot = findFirstFreeSlot(ownerType);
        int worldNumber = (slot / cfg.territoriesPerWorld) + 1;
        int slotInWorld = slot % cfg.territoriesPerWorld;
        String worldPrefix = ownerType == OwnerType.GUILD ? cfg.guildWorldPrefix : cfg.personalWorldPrefix;
        String worldName = forcedWorldName == null || forcedWorldName.isBlank() ? worldPrefix + "_" + worldNumber : forcedWorldName;

        int gridWidth = Math.max(1, cfg.slotGridWidth);
        int gridX = slotInWorld % gridWidth;
        int gridZ = slotInWorld / gridWidth;
        int centerX = gridX * cfg.centerSpacing;
        int centerZ = gridZ * cfg.centerSpacing;
        int radius = cfg.defaultRadius;

        Territory territory = new Territory();
        territory.id = UUID.randomUUID();
        territory.ownerType = ownerType;
        territory.ownerId = ownerId;
        territory.ownerName = ownerName == null ? "" : ownerName;
        territory.serverId = NetworkServerConfig.serverId();
        territory.worldName = worldName;
        territory.worldKey = worldName;
        territory.slotIndex = slotInWorld;
        territory.generationState = cfg.runGenerationCommands ? "PENDING" : "READY";
        territory.centerX = centerX;
        territory.centerZ = centerZ;
        territory.radius = radius;
        territory.minX = centerX - radius;
        territory.maxX = centerX + radius;
        territory.minZ = centerZ - radius;
        territory.maxZ = centerZ + radius;
        territory.spawnX = centerX + 0.5D;
        territory.spawnY = cfg.defaultSpawnY;
        territory.spawnZ = centerZ + 0.5D;
        territory.spawnYaw = 0F;
        territory.spawnPitch = 0F;
        territory.level = 1;
        territory.biomePreference = cleanBiomePreference(biomePreference);
        territory.isPublic = false;
        territory.allowVisitors = false;
        territory.visitorsCanBuild = false;
        territory.visitorsCanOpenContainers = false;
        territory.visitorsCanInteractEntities = false;
        territory.visitorsCanUseRedstone = false;
        territory.lockBorder = true;
        return territory;
    }

    private static int findFirstFreeSlot(OwnerType ownerType) {
        TerritoryConfig.Data cfg = TerritoryConfig.get();
        for (int absolute = 0; absolute < 1000000; absolute++) {
            int worldNumber = (absolute / cfg.territoriesPerWorld) + 1;
            int slotInWorld = absolute % cfg.territoriesPerWorld;
            String expectedWorld = (ownerType == OwnerType.GUILD ? cfg.guildWorldPrefix : cfg.personalWorldPrefix) + "_" + worldNumber;
            boolean used = false;
            for (Territory t : TERRITORIES.values()) {
                if (t.ownerType == ownerType
                        && t.worldName.equalsIgnoreCase(expectedWorld)
                        && t.slotIndex == slotInWorld) {
                    used = true;
                    break;
                }
            }
            if (!used) return absolute;
        }
        return TERRITORIES.size();
    }

    private static String generationMessage(Territory territory) {
        if (territory.isReady()) return "It is ready to enter.";
        return "The packed Multiworld territory world is being created/loaded. Try /territory home shortly. No Chunky pregeneration is required.";
    }

    public static void markReady(UUID territoryId, Callback callback) {
        Territory territory = TERRITORIES.get(territoryId);
        if (territory == null) { callback.done(false, "Territory not found."); return; }
        territory.generationState = "READY";
        save(territory, (success, message) -> callback.done(success, success ? "Territory marked READY." : message));
    }

    public static void deleteTerritory(Territory territory, Callback callback) {
        if (territory == null) { callback.done(false, "No territory found."); return; }
        DatabaseManager.executeAsync("delete territory " + territory.id, connection -> {
            try (PreparedStatement cooldown = connection.prepareStatement(
                    "insert into territory_delete_cooldowns (owner_type, owner_id, deleted_at) values (?, ?, now()) " +
                            "on conflict (owner_type, owner_id) do update set deleted_at = now()")) {
                cooldown.setString(1, territory.ownerType.name());
                cooldown.setString(2, territory.ownerId);
                cooldown.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("delete from territories where id = ?")) {
                statement.setObject(1, territory.id);
                statement.executeUpdate();
            }
            TERRITORIES.remove(territory.id);
            OWNER_INDEX.remove(ownerKey(territory.ownerType, territory.ownerId));
            TRUST.keySet().removeIf(key -> key.startsWith(territory.id.toString() + ":"));
            int minutes = TerritoryConfig.get().recreateCooldownMinutes;
            callback.done(true, "Territory deleted. Its packed slot is now open. You must wait " + minutes + " minute" + (minutes == 1 ? "" : "s") + " before creating another.");
        });
    }

    public static void save(Territory territory, Callback callback) {
        if (territory == null) { callback.done(false, "Invalid territory."); return; }
        normalizeBounds(territory);
        DatabaseManager.executeAsync("save territory " + territory.id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into territories " +
                            "(id, owner_type, owner_id, owner_name, server_id, world_name, world_key, slot_index, generation_state, center_x, center_z, radius, min_x, max_x, min_z, max_z, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, level, biome_preference, is_public, allow_visitors, visitors_can_build, visitors_can_open_containers, visitors_can_interact_entities, visitors_can_use_redstone, lock_border, created_at, updated_at) " +
                            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now()) " +
                            "on conflict (owner_type, owner_id) do update set " +
                            "owner_name = excluded.owner_name, server_id = excluded.server_id, world_name = excluded.world_name, world_key = excluded.world_key, slot_index = excluded.slot_index, generation_state = excluded.generation_state, center_x = excluded.center_x, center_z = excluded.center_z, radius = excluded.radius, " +
                            "min_x = excluded.min_x, max_x = excluded.max_x, min_z = excluded.min_z, max_z = excluded.max_z, " +
                            "spawn_x = excluded.spawn_x, spawn_y = excluded.spawn_y, spawn_z = excluded.spawn_z, spawn_yaw = excluded.spawn_yaw, spawn_pitch = excluded.spawn_pitch, level = excluded.level, biome_preference = excluded.biome_preference, " +
                            "is_public = excluded.is_public, allow_visitors = excluded.allow_visitors, visitors_can_build = excluded.visitors_can_build, visitors_can_open_containers = excluded.visitors_can_open_containers, visitors_can_interact_entities = excluded.visitors_can_interact_entities, visitors_can_use_redstone = excluded.visitors_can_use_redstone, lock_border = excluded.lock_border, updated_at = now()"
            )) {
                statement.setObject(1, territory.id);
                statement.setString(2, territory.ownerType.name());
                statement.setString(3, territory.ownerId);
                statement.setString(4, territory.ownerName);
                statement.setString(5, territory.serverId);
                statement.setString(6, territory.worldName);
                statement.setString(7, territory.worldKey == null ? territory.worldName : territory.worldKey);
                statement.setInt(8, territory.slotIndex);
                statement.setString(9, territory.generationState == null ? "READY" : territory.generationState);
                statement.setInt(10, territory.centerX);
                statement.setInt(11, territory.centerZ);
                statement.setInt(12, territory.radius);
                statement.setInt(13, territory.minX);
                statement.setInt(14, territory.maxX);
                statement.setInt(15, territory.minZ);
                statement.setInt(16, territory.maxZ);
                statement.setDouble(17, territory.spawnX);
                statement.setDouble(18, territory.spawnY);
                statement.setDouble(19, territory.spawnZ);
                statement.setFloat(20, territory.spawnYaw);
                statement.setFloat(21, territory.spawnPitch);
                statement.setInt(22, territory.level);
                if (territory.biomePreference == null) statement.setNull(23, Types.VARCHAR); else statement.setString(23, territory.biomePreference);
                statement.setBoolean(24, territory.isPublic);
                statement.setBoolean(25, territory.allowVisitors);
                statement.setBoolean(26, territory.visitorsCanBuild);
                statement.setBoolean(27, territory.visitorsCanOpenContainers);
                statement.setBoolean(28, territory.visitorsCanInteractEntities);
                statement.setBoolean(29, territory.visitorsCanUseRedstone);
                statement.setBoolean(30, territory.lockBorder);
                statement.executeUpdate();
            }
            TERRITORIES.put(territory.id, territory);
            OWNER_INDEX.put(ownerKey(territory.ownerType, territory.ownerId), territory.id);
            callback.done(true, "Territory saved.");
        });
    }

    private static Territory read(ResultSet rs) throws Exception {
        Territory t = new Territory();
        t.id = (UUID) rs.getObject("id");
        t.ownerType = OwnerType.valueOf(rs.getString("owner_type"));
        t.ownerId = rs.getString("owner_id");
        t.ownerName = rs.getString("owner_name");
        t.serverId = rs.getString("server_id");
        t.worldName = rs.getString("world_name");
        t.worldKey = getStringOrNull(rs, "world_key");
        if (t.worldKey == null || t.worldKey.isBlank()) t.worldKey = t.worldName;
        t.generationState = getStringOrNull(rs, "generation_state");
        if (t.generationState == null || t.generationState.isBlank()) t.generationState = "READY";
        t.centerX = getIntOrDefault(rs, "center_x", (rs.getInt("min_x") + rs.getInt("max_x")) / 2);
        t.centerZ = getIntOrDefault(rs, "center_z", (rs.getInt("min_z") + rs.getInt("max_z")) / 2);
        t.slotIndex = getIntOrDefault(rs, "slot_index", inferSlotIndex(t.worldName, t.centerX, t.centerZ));
        t.radius = getIntOrDefault(rs, "radius", Math.max((rs.getInt("max_x") - rs.getInt("min_x")) / 2, (rs.getInt("max_z") - rs.getInt("min_z")) / 2));
        t.minX = rs.getInt("min_x");
        t.maxX = rs.getInt("max_x");
        t.minZ = rs.getInt("min_z");
        t.maxZ = rs.getInt("max_z");
        t.spawnX = rs.getDouble("spawn_x");
        t.spawnY = rs.getDouble("spawn_y");
        t.spawnZ = rs.getDouble("spawn_z");
        t.spawnYaw = rs.getFloat("spawn_yaw");
        t.spawnPitch = rs.getFloat("spawn_pitch");
        t.level = rs.getInt("level");
        t.biomePreference = getStringOrNull(rs, "biome_preference");
        t.isPublic = getBooleanOrDefault(rs, "is_public", false);
        t.allowVisitors = getBooleanOrDefault(rs, "allow_visitors", false);
        t.visitorsCanBuild = getBooleanOrDefault(rs, "visitors_can_build", false);
        t.visitorsCanOpenContainers = getBooleanOrDefault(rs, "visitors_can_open_containers", false);
        t.visitorsCanInteractEntities = getBooleanOrDefault(rs, "visitors_can_interact_entities", false);
        t.visitorsCanUseRedstone = getBooleanOrDefault(rs, "visitors_can_use_redstone", false);
        t.lockBorder = getBooleanOrDefault(rs, "lock_border", true);
        normalizeBounds(t);
        return t;
    }

    private static void normalizeBounds(Territory t) {
        if (t.radius <= 0) t.radius = Math.max(1, Math.max((t.maxX - t.minX) / 2, (t.maxZ - t.minZ) / 2));
        if (t.centerX == 0 && (t.minX != 0 || t.maxX != 0)) t.centerX = (t.minX + t.maxX) / 2;
        if (t.centerZ == 0 && (t.minZ != 0 || t.maxZ != 0)) t.centerZ = (t.minZ + t.maxZ) / 2;
        t.minX = t.centerX - t.radius;
        t.maxX = t.centerX + t.radius;
        t.minZ = t.centerZ - t.radius;
        t.maxZ = t.centerZ + t.radius;
    }

    private static int inferSlotIndex(String worldName, int centerX, int centerZ) {
        TerritoryConfig.Data cfg = TerritoryConfig.get();
        if (cfg.centerSpacing <= 0) return 0;
        int gridX = Math.max(0, Math.round((float) centerX / (float) cfg.centerSpacing));
        int gridZ = Math.max(0, Math.round((float) centerZ / (float) cfg.centerSpacing));
        return gridZ * Math.max(1, cfg.slotGridWidth) + gridX;
    }

    private static int getIntOrDefault(ResultSet rs, String column, int fallback) {
        try { return rs.getInt(column); } catch (Exception ignored) { return fallback; }
    }
    private static boolean getBooleanOrDefault(ResultSet rs, String column, boolean fallback) {
        try { return rs.getBoolean(column); } catch (Exception ignored) { return fallback; }
    }
    private static String getStringOrNull(ResultSet rs, String column) {
        try { return rs.getString(column); } catch (Exception ignored) { return null; }
    }

    private static TrustLevel parseTrust(String raw) {
        try { return TrustLevel.valueOf((raw == null ? "VISITOR" : raw).toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return TrustLevel.VISITOR; }
    }

    public static String cleanBiomePreference(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String clean = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace("minecraft:", "");
        for (String allowed : TerritoryConfig.get().allowedBiomePreferences) {
            if (allowed.equalsIgnoreCase(clean)) return allowed.toLowerCase(Locale.ROOT);
        }
        return null;
    }

    public static List<String> biomeSuggestions() {
        List<String> suggestions = new ArrayList<>();
        for (String biome : TerritoryConfig.get().allowedBiomePreferences) {
            suggestions.add(prettyBiome(biome));
        }
        suggestions.sort(String.CASE_INSENSITIVE_ORDER);
        return suggestions;
    }

    public static String prettyBiome(String raw) {
        if (raw == null || raw.isBlank()) return "Any";
        String clean = raw.trim().replace("minecraft:", "").replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String part : clean.split(" ")) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static void checkRecreateCooldown(OwnerType ownerType, String ownerId, CooldownCallback callback) {
        int minutes = TerritoryConfig.get().recreateCooldownMinutes;
        if (minutes <= 0 || !DatabaseManager.isEnabled()) { callback.done(true, ""); return; }
        DatabaseManager.executeAsync("check territory recreate cooldown", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "select extract(epoch from (now() - deleted_at))::bigint as elapsed_seconds from territory_delete_cooldowns where owner_type = ? and owner_id = ?")) {
                statement.setString(1, ownerType.name());
                statement.setString(2, ownerId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) { callback.done(true, ""); return; }
                    long elapsedSeconds = Math.max(0L, rs.getLong("elapsed_seconds"));
                    long cooldownSeconds = Duration.ofMinutes(minutes).getSeconds();
                    if (elapsedSeconds >= cooldownSeconds) { callback.done(true, ""); return; }
                    long remaining = cooldownSeconds - elapsedSeconds;
                    long remMinutes = Math.max(1L, (remaining + 59L) / 60L);
                    callback.done(false, "You must wait " + remMinutes + " more minute" + (remMinutes == 1 ? "" : "s") + " before creating another " + (ownerType == OwnerType.GUILD ? "guild territory" : "territory") + ".");
                }
            }
        });
    }

    private static String ownerKey(OwnerType ownerType, String ownerId) { return ownerType.name() + ":" + ownerId; }
    private static String trustKey(UUID territoryId, UUID playerId) { return territoryId + ":" + playerId; }

    @FunctionalInterface
    public interface Callback { void done(boolean success, String message); }

    @FunctionalInterface
    private interface CooldownCallback { void done(boolean allowed, String message); }
}
