package com.champutils.territory;

import com.champutils.database.DatabaseManager;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.IslanderProfileManager;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.sql.Connection;

public final class TerritoryRepository {

    public enum OwnerType { PLAYER, GUILD }
    public enum TrustLevel { VISITOR, TRUSTED, MANAGER, BANNED }

    public static final class Territory {
        public UUID id;
        public OwnerType ownerType;
        public String ownerId;
        public String ownerName;
        public String displayName;
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
        public boolean stewardNpcSpawned;
        public Double stewardNpcX;
        public Double stewardNpcY;
        public Double stewardNpcZ;
        public Float stewardNpcYaw;
        public Float stewardNpcPitch;

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

        public String publicName() {
            return displayName == null || displayName.isBlank() ? ownerName : displayName;
        }
    }

    private static final Map<UUID, Territory> TERRITORIES = new ConcurrentHashMap<>();
    private static final Map<String, UUID> OWNER_INDEX = new ConcurrentHashMap<>();
    private static final Map<String, TrustLevel> TRUST = new ConcurrentHashMap<>();

    // Hot-path indexes. These prevent per-tick and per-interaction territory checks from scanning every territory.
    private static final Map<String, Map<Long, List<Territory>>> TERRITORIES_BY_WORLD_CHUNK = new ConcurrentHashMap<>();
    private static final Map<String, List<Territory>> TERRITORIES_BY_WORLD = new ConcurrentHashMap<>();
    private static final Set<String> TERRITORY_WORLD_KEYS = ConcurrentHashMap.newKeySet();

    private static final String ISLANDER_WORLD_PREFIX = "multiworld:islander";
    private static final int ISLANDER_TERRITORIES_PER_WORLD = 100;

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
            rebuildSpatialIndexes();

            System.out.println("[ChampUtils] Loaded " + TERRITORIES.size() + " territories from the database.");
        });
    }

    public static Collection<Territory> allCached() { return TERRITORIES.values(); }

    public static Territory get(UUID territoryId) {
        return territoryId == null ? null : TERRITORIES.get(territoryId);
    }

    public static List<Territory> publicCached(OwnerType type) {
        List<Territory> list = new ArrayList<>();
        for (Territory t : TERRITORIES.values()) {
            if (t.ownerType == type && t.isPublic) list.add(t);
        }
        list.sort(Comparator.comparing(t -> t.publicName().toLowerCase(Locale.ROOT)));
        return list;
    }

    public static boolean isTerritoryWorld(ServerLevel level) {
        if (level == null) return false;
        return TERRITORY_WORLD_KEYS.contains(worldKey(NetworkServerConfig.serverId(), level.dimension().location().toString()));
    }

    public static Territory findAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return null;
        String serverId = NetworkServerConfig.serverId();
        String worldName = level.dimension().location().toString();
        Map<Long, List<Territory>> chunks = TERRITORIES_BY_WORLD_CHUNK.get(worldKey(serverId, worldName));
        if (chunks == null || chunks.isEmpty()) return null;

        List<Territory> candidates = chunks.get(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
        if (candidates == null || candidates.isEmpty()) return null;

        for (Territory territory : candidates) {
            if (territory.contains(serverId, worldName, pos)) return territory;
        }
        return null;
    }

    public static List<Territory> cachedInWorld(ServerLevel level) {
        if (level == null) return Collections.emptyList();
        return cachedInWorld(NetworkServerConfig.serverId(), level.dimension().location().toString());
    }

    public static List<Territory> cachedInWorld(String serverId, String worldName) {
        List<Territory> territories = TERRITORIES_BY_WORLD.get(worldKey(serverId, worldName));
        return territories == null ? Collections.emptyList() : territories;
    }


    private static void rebuildSpatialIndexes() {
        Map<String, Map<Long, List<Territory>>> chunkIndex = new ConcurrentHashMap<>();
        Map<String, List<Territory>> worldIndex = new ConcurrentHashMap<>();
        Set<String> worldKeys = ConcurrentHashMap.newKeySet();

        for (Territory territory : TERRITORIES.values()) {
            if (territory == null || territory.serverId == null || territory.worldName == null) continue;
            normalizeBounds(territory);
            String key = worldKey(territory.serverId, territory.worldName);
            worldKeys.add(key);
            worldIndex.computeIfAbsent(key, ignored -> Collections.synchronizedList(new ArrayList<>())).add(territory);

            int minChunkX = territory.minX >> 4;
            int maxChunkX = territory.maxX >> 4;
            int minChunkZ = territory.minZ >> 4;
            int maxChunkZ = territory.maxZ >> 4;
            Map<Long, List<Territory>> chunks = chunkIndex.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    chunks.computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> Collections.synchronizedList(new ArrayList<>())).add(territory);
                }
            }
        }

        TERRITORIES_BY_WORLD_CHUNK.clear();
        TERRITORIES_BY_WORLD_CHUNK.putAll(chunkIndex);
        TERRITORIES_BY_WORLD.clear();
        TERRITORIES_BY_WORLD.putAll(worldIndex);
        TERRITORY_WORLD_KEYS.clear();
        TERRITORY_WORLD_KEYS.addAll(worldKeys);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static String worldKey(String serverId, String worldName) {
        return (serverId == null ? "" : serverId.toLowerCase(Locale.ROOT)) + "|" + (worldName == null ? "" : worldName.toLowerCase(Locale.ROOT));
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
        rebuildSpatialIndexes();
    }

    public static Territory cachedPersonal(ServerPlayer player) {
        if (player == null) return null;
        return cachedForOwner(OwnerType.PLAYER, PlayerProfileManager.activeProfileId(player).toString());
    }

    public static Territory cachedGuildForPlayer(ServerPlayer player) {
        if (player == null) return null;
        com.champutils.guild.GuildRepository.GuildSnapshot guild = com.champutils.guild.GuildRepository.cachedGuild(player.getUUID());
        return guild == null ? null : cachedForOwner(OwnerType.GUILD, guild.id.toString());
    }

    public static boolean isOwnerOrGuildMember(ServerPlayer player, Territory territory) {
        if (player == null || territory == null) return false;
        UUID playerId = player.getUUID();
        if (territory.ownerType == OwnerType.PLAYER) return territory.ownerId.equalsIgnoreCase(PlayerProfileManager.activeProfileId(playerId).toString());
        com.champutils.guild.GuildRepository.GuildSnapshot guild = com.champutils.guild.GuildRepository.cachedGuild(playerId);
        return guild != null && territory.ownerId.equalsIgnoreCase(guild.id.toString());
    }

    public static boolean isBanned(ServerPlayer player, Territory territory) {
        if (player == null || territory == null || com.champutils.permissions.LuckPermsHook.hasPermission(player, "champutils.admin")) return false;
        if (territory.ownerType == OwnerType.PLAYER && territory.ownerId.equalsIgnoreCase(PlayerProfileManager.activeProfileId(player).toString())) return false;
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
        if (com.champutils.permissions.LuckPermsHook.hasPermission(player, "champutils.admin")) return true;
        UUID playerId = player.getUUID();
        if (territory.ownerType == OwnerType.PLAYER) {
            if (territory.ownerId.equalsIgnoreCase(PlayerProfileManager.activeProfileId(playerId).toString())) return true;
            return getTrust(territory.id, playerId) == TrustLevel.MANAGER;
        }
        com.champutils.guild.GuildRepository.GuildSnapshot guild = com.champutils.guild.GuildRepository.cachedGuild(playerId);
        return guild != null
                && territory.ownerId.equalsIgnoreCase(guild.id.toString())
                && com.champutils.guild.GuildRepository.canManageGuildTerritory(guild.role);
    }


    /**
     * Main-thread hot-path entry check used by movement/tick enforcement.
     *
     * This intentionally avoids LuckPerms/user loading because territory border checks run very often.
     * Full permission checks still happen in commands and lower-frequency administrative paths.
     */
    public static boolean canEnterFast(ServerPlayer player, Territory territory) {
        if (player == null || territory == null) return true;
        if (player.hasPermissions(4)) return true;
        if (!territory.isReady()) return false;
        if (!IslanderProfileManager.canEnterTerritoryFast(player, territory)) return false;
        if (getTrust(territory.id, player.getUUID()) == TrustLevel.BANNED) return false;
        if (isOwnerOrGuildMember(player, territory)) return true;
        TrustLevel trust = getTrust(territory.id, player.getUUID());
        if (trust != null && trust != TrustLevel.BANNED) return true;
        return territory.allowVisitors || territory.isPublic;
    }

    public static boolean canEnter(ServerPlayer player, Territory territory) {
        if (player == null || territory == null) return true;
        if (com.champutils.permissions.LuckPermsHook.hasPermission(player, "champutils.admin")) return true;
        if (!territory.isReady()) return com.champutils.permissions.LuckPermsHook.hasPermission(player, "champutils.admin");
        if (!IslanderProfileManager.canEnterTerritory(player, territory)) return false;
        if (isBanned(player, territory)) return false;
        if (isOwnerOrGuildMember(player, territory)) return true;
        TrustLevel trust = getTrust(territory.id, player.getUUID());
        if (trust != null && trust != TrustLevel.BANNED) return true;
        return territory.allowVisitors || territory.isPublic;
    }

    public static boolean canBuild(ServerPlayer player, Territory territory) {
        if (player == null || territory == null) return true;
        if (com.champutils.permissions.LuckPermsHook.hasPermission(player, "champutils.admin")) return true;
        if (isBanned(player, territory)) return false;
        if (territory.ownerType == OwnerType.PLAYER) {
            java.util.UUID activeProfileId = PlayerProfileManager.activeProfileId(player);
            if (activeProfileId != null && territory.ownerId.equalsIgnoreCase(activeProfileId.toString())) return true;
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
        if (com.champutils.permissions.LuckPermsHook.hasPermission(player, "champutils.admin")) return true;
        if (isBanned(player, territory)) return false;
        if (isOwnerOrGuildMember(player, territory)) return true;
        TrustLevel trust = getTrust(territory.id, player.getUUID());
        if (trust == TrustLevel.TRUSTED || trust == TrustLevel.MANAGER) return true;
        return territory.allowVisitors && territory.visitorsCanOpenContainers;
    }

    public static boolean canInteractEntities(ServerPlayer player, Territory territory) {
        if (player == null || territory == null) return true;
        if (com.champutils.permissions.LuckPermsHook.hasPermission(player, "champutils.admin")) return true;
        if (isBanned(player, territory)) return false;
        if (isOwnerOrGuildMember(player, territory)) return true;
        TrustLevel trust = getTrust(territory.id, player.getUUID());
        if (trust == TrustLevel.TRUSTED || trust == TrustLevel.MANAGER) return true;
        return territory.allowVisitors && territory.visitorsCanInteractEntities;
    }

    public static boolean canUseRedstone(ServerPlayer player, Territory territory) {
        if (player == null || territory == null) return true;
        if (com.champutils.permissions.LuckPermsHook.hasPermission(player, "champutils.admin")) return true;
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
        String ownerId = PlayerProfileManager.activeProfileId(ownerUuid).toString();
        Territory existing = cachedForOwner(OwnerType.PLAYER, ownerId);
        if (existing != null) {
            callback.done(false, isDeleting(existing) ? "Your old territory is still being deleted. Try again later." : "You already have a territory.");
            return;
        }
        if (biomePreference != null && !biomePreference.isBlank() && cleanBiomePreference(biomePreference) == null) { callback.done(false, "Invalid biome. Press TAB after /territory create to choose an overworld biome."); return; }

        checkRecreateCooldown(OwnerType.PLAYER, ownerId, (allowed, remainingMessage) -> {
            if (!allowed) { callback.done(false, remainingMessage); return; }
            TerritoryConfig.Data cfg = TerritoryConfig.get();
            boolean islander = PlayerProfileManager.isIslander(player);
            String worldName = islander ? null : (cfg.createPersonalInCurrentWorld ? player.serverLevel().dimension().location().toString() : null);
            String preferredBiome = islander ? "plains" : biomePreference;
            Territory territory = islander
                    ? allocateIslanderTerritory(ownerId, player.getGameProfile().getName(), preferredBiome)
                    : allocate(OwnerType.PLAYER, ownerId, player.getGameProfile().getName(), worldName, preferredBiome);
            if (islander) {
                territory.displayName = "Islander - " + player.getGameProfile().getName();
                territory.isPublic = true;
                territory.allowVisitors = true;
                territory.visitorsCanBuild = false;
                territory.visitorsCanOpenContainers = false;
                territory.visitorsCanInteractEntities = true;
                territory.visitorsCanUseRedstone = false;
            }
            save(territory, (success, message) -> {
                if (success) TerritoryWorldGenerationManager.requestGeneration(player.server, player, territory);
                callback.done(success, success ? "Your territory is being prepared. You will get a chat message when it is ready. You cannot teleport there until it is finished." : message);
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
        Territory existing = cachedForOwner(OwnerType.GUILD, ownerId);
        if (existing != null) {
            callback.done(!isDeleting(existing), isDeleting(existing) ? "Your old guild territory is still being deleted. Try again later." : "Guild territory already exists.");
            return;
        }
        if (biomePreference != null && !biomePreference.isBlank() && cleanBiomePreference(biomePreference) == null) { callback.done(false, "Invalid biome. Press TAB after /gterritory create to choose an overworld biome."); return; }
        checkRecreateCooldown(OwnerType.GUILD, ownerId, (allowed, remainingMessage) -> {
            if (!allowed) { callback.done(false, remainingMessage); return; }
            Territory territory = allocate(OwnerType.GUILD, ownerId, guildName == null ? "Guild" : guildName, null, biomePreference);
            save(territory, (success, message) -> {
                if (success) TerritoryWorldGenerationManager.requestGeneration(server, initiator, territory);
                callback.done(success, success ? "Guild territory is being prepared. Guild members can use /gterritory when it is ready." : message);
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

    public static void setDisplayName(Territory territory, String rawName, Callback callback) {
        if (territory == null) { callback.done(false, "Invalid territory."); return; }
        String clean = rawName == null ? "" : rawName.trim();
        if (clean.isBlank()) { callback.done(false, "Name cannot be blank."); return; }
        if (clean.length() > 32) { callback.done(false, "Name must be 32 characters or less."); return; }
        if (!clean.matches("[A-Za-z0-9 _'&.-]+")) {
            callback.done(false, "Name can only use letters, numbers, spaces, and basic punctuation.");
            return;
        }
        territory.displayName = clean;
        save(territory, (success, message) -> callback.done(success, success ? "Territory name set to " + clean + "." : message));
    }

    public static List<Territory> trustedPersonalFor(ServerPlayer player) {
        List<Territory> list = new ArrayList<>();
        if (player == null) return list;
        for (Territory t : TERRITORIES.values()) {
            if (t.ownerType != OwnerType.PLAYER) continue;
            if (t.ownerId.equalsIgnoreCase(PlayerProfileManager.activeProfileId(player).toString())) continue;
            TrustLevel trust = getTrust(t.id, player.getUUID());
            if (trust != null && trust != TrustLevel.BANNED) list.add(t);
        }
        list.sort(Comparator.comparing(t -> t.publicName().toLowerCase(Locale.ROOT)));
        return list;
    }

    private static Territory allocate(OwnerType ownerType, String ownerId, String ownerName, String forcedWorldName, String biomePreference) {
        TerritoryConfig.Data cfg = TerritoryConfig.get();
        int slot = findFirstFreeSlot(ownerType);
        int territoriesPerWorld = Math.max(1, cfg.territoriesPerWorld);
        int worldNumber = (slot / territoriesPerWorld) + 1;
        int slotInWorld = slot % territoriesPerWorld;
        String worldPrefix = ownerType == OwnerType.GUILD ? cfg.guildWorldPrefix : cfg.personalWorldPrefix;
        String worldName = forcedWorldName == null || forcedWorldName.isBlank() ? worldPrefix + "_" + worldNumber : forcedWorldName;
        return allocateAt(ownerType, ownerId, ownerName, worldName, slotInWorld, biomePreference);
    }

    private static Territory allocateIslanderTerritory(String ownerId, String ownerName, String biomePreference) {
        int absoluteSlot = findFirstFreeIslanderSlot();
        int worldNumber = (absoluteSlot / ISLANDER_TERRITORIES_PER_WORLD) + 1;
        int slotInWorld = absoluteSlot % ISLANDER_TERRITORIES_PER_WORLD;
        return allocateAt(OwnerType.PLAYER, ownerId, ownerName, ISLANDER_WORLD_PREFIX + "_" + worldNumber, slotInWorld, biomePreference);
    }

    private static Territory allocateAt(OwnerType ownerType, String ownerId, String ownerName, String worldName, int slotInWorld, String biomePreference) {
        TerritoryConfig.Data cfg = TerritoryConfig.get();
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
        territory.displayName = territory.ownerName;
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
        territory.stewardNpcSpawned = false;
        return territory;
    }

    private static int findFirstFreeSlot(OwnerType ownerType) {
        TerritoryConfig.Data cfg = TerritoryConfig.get();
        int territoriesPerWorld = Math.max(1, cfg.territoriesPerWorld);
        for (int absolute = 0; absolute < 1000000; absolute++) {
            int worldNumber = (absolute / territoriesPerWorld) + 1;
            int slotInWorld = absolute % territoriesPerWorld;
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

    private static int findFirstFreeIslanderSlot() {
        for (int absolute = 0; absolute < 1000000; absolute++) {
            int worldNumber = (absolute / ISLANDER_TERRITORIES_PER_WORLD) + 1;
            int slotInWorld = absolute % ISLANDER_TERRITORIES_PER_WORLD;
            String expectedWorld = ISLANDER_WORLD_PREFIX + "_" + worldNumber;
            boolean used = false;
            for (Territory t : TERRITORIES.values()) {
                if (t.ownerType == OwnerType.PLAYER
                        && isIslanderWorldName(t.worldName)
                        && t.worldName.equalsIgnoreCase(expectedWorld)
                        && t.slotIndex == slotInWorld
                        && !isDeleting(t)) {
                    used = true;
                    break;
                }
            }
            if (!used) return absolute;
        }
        return TERRITORIES.size();
    }

    private static boolean isIslanderWorldName(String worldName) {
        if (worldName == null) return false;
        String clean = worldName.trim().toLowerCase(Locale.ROOT);
        return clean.matches("multiworld:islander_\\d+") || clean.matches("islander_\\d+");
    }

    public static boolean isDeleting(Territory territory) {
        return territory != null && territory.generationState != null && territory.generationState.equalsIgnoreCase("DELETING");
    }

    private static String generationMessage(Territory territory) {
        if (territory.isReady()) return "Use /territory home to visit it.";
        return "It is being prepared. Try /territory home shortly.";
    }

    public static void markReady(UUID territoryId, Callback callback) {
        Territory territory = TERRITORIES.get(territoryId);
        if (territory == null) { callback.done(false, "Territory not found."); return; }
        territory.generationState = "READY";
        save(territory, (success, message) -> callback.done(success, success ? "Territory marked READY." : message));
    }

    public static void beginDelete(Territory territory, Callback callback) {
        if (territory == null) { callback.done(false, "No territory found."); return; }

        String originalOwnerId = territory.ownerId;
        String deletingOwnerId = deletingOwnerId(territory);

        DatabaseManager.executeAsync("begin territory delete " + territory.id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "update territories set owner_id = ?, generation_state = 'DELETING', is_public = false, allow_visitors = false, updated_at = now() where id = ?"
            )) {
                statement.setString(1, deletingOwnerId);
                statement.setObject(2, territory.id);
                int changed = statement.executeUpdate();
                if (changed <= 0) {
                    callback.done(false, "Territory not found.");
                    return;
                }
            }

            OWNER_INDEX.remove(ownerKey(territory.ownerType, originalOwnerId));
            territory.ownerId = deletingOwnerId;
            territory.generationState = "DELETING";
            territory.isPublic = false;
            territory.allowVisitors = false;
            rebuildSpatialIndexes();
            callback.done(true, "Territory deletion started. You can create another territory now while the old slot wipes in the background.");
        });
    }

    private static String deletingOwnerId(Territory territory) {
        return "__deleting__" + (territory == null || territory.id == null ? UUID.randomUUID() : territory.id);
    }

    public static void finishDelete(Territory territory, Callback callback) {
        if (territory == null) { callback.done(false, "No territory found."); return; }
        DatabaseManager.executeAsync("delete territory " + territory.id, connection -> {
            try (PreparedStatement trust = connection.prepareStatement("delete from territory_trust where territory_id = ?")) {
                trust.setObject(1, territory.id);
                trust.executeUpdate();
            }
            try (PreparedStatement cleanup = connection.prepareStatement("delete from territory_delete_cooldowns where owner_type = ? and owner_id = ?")) {
                cleanup.setString(1, territory.ownerType.name());
                cleanup.setString(2, territory.ownerId);
                cleanup.executeUpdate();
            }
            try (java.sql.Statement repair = connection.createStatement()) {
                repair.executeUpdate("alter table territory_delete_cooldowns add column if not exists owner_id text");
                repair.executeUpdate("do $$ begin if exists (select 1 from information_schema.columns where table_name='territory_delete_cooldowns' and column_name='owner_profile_id') then execute 'alter table territory_delete_cooldowns alter column owner_profile_id drop not null'; end if; end $$");
                repair.executeUpdate("do $$ begin if exists (select 1 from information_schema.columns where table_name='territory_delete_cooldowns' and column_name='owner_guild_id') then execute 'alter table territory_delete_cooldowns alter column owner_guild_id drop not null'; end if; end $$");
            } catch (Exception ignored) {}
            int minutes = TerritoryConfig.get().recreateCooldownMinutes;
            if (minutes > 0) {
                try (PreparedStatement cooldown = connection.prepareStatement("insert into territory_delete_cooldowns (owner_type, owner_id, deleted_at) values (?, ?, now())")) {
                    cooldown.setString(1, territory.ownerType.name());
                    cooldown.setString(2, territory.ownerId);
                    cooldown.executeUpdate();
                }
            }
            tryDeleteIslanderMineRecord(connection, territory);
            try (PreparedStatement statement = connection.prepareStatement("delete from territories where id = ?")) {
                statement.setObject(1, territory.id);
                statement.executeUpdate();
            }
            TERRITORIES.remove(territory.id);
            OWNER_INDEX.remove(ownerKey(territory.ownerType, territory.ownerId));
            TRUST.keySet().removeIf(key -> key.startsWith(territory.id.toString() + ":"));
            rebuildSpatialIndexes();
            callback.done(true, "Territory deleted. You can create another territory now.");
        });
    }

    private static void tryDeleteIslanderMineRecord(Connection connection, Territory territory) {
        if (connection == null || territory == null || territory.ownerId == null || territory.ownerId.isBlank()) return;
        try (PreparedStatement mine = connection.prepareStatement("delete from islander_mines where profile_id::text = ?")) {
            mine.setString(1, territory.ownerId);
            mine.executeUpdate();
        } catch (Exception ignored) {
            // Older databases may not have islander_mines yet. Territory deletion must still succeed.
        }
    }

    public static void save(Territory territory, Callback callback) {
        if (territory == null) { callback.done(false, "Invalid territory."); return; }
        normalizeBounds(territory);
        DatabaseManager.executeAsync("save territory " + territory.id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into territories " +
                            "(id, owner_type, owner_id, owner_name, display_name, server_id, world_name, world_key, slot_index, generation_state, center_x, center_z, radius, min_x, max_x, min_z, max_z, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, level, biome_preference, is_public, allow_visitors, visitors_can_build, visitors_can_open_containers, visitors_can_interact_entities, visitors_can_use_redstone, lock_border, steward_npc_spawned, steward_npc_x, steward_npc_y, steward_npc_z, steward_npc_yaw, steward_npc_pitch, created_at, updated_at) " +
                            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now()) " +
                            "on conflict (owner_type, owner_id) do update set " +
                            "owner_name = excluded.owner_name, display_name = excluded.display_name, server_id = excluded.server_id, world_name = excluded.world_name, world_key = excluded.world_key, slot_index = excluded.slot_index, generation_state = excluded.generation_state, center_x = excluded.center_x, center_z = excluded.center_z, radius = excluded.radius, " +
                            "min_x = excluded.min_x, max_x = excluded.max_x, min_z = excluded.min_z, max_z = excluded.max_z, " +
                            "spawn_x = excluded.spawn_x, spawn_y = excluded.spawn_y, spawn_z = excluded.spawn_z, spawn_yaw = excluded.spawn_yaw, spawn_pitch = excluded.spawn_pitch, level = excluded.level, biome_preference = excluded.biome_preference, " +
                            "is_public = excluded.is_public, allow_visitors = excluded.allow_visitors, visitors_can_build = excluded.visitors_can_build, visitors_can_open_containers = excluded.visitors_can_open_containers, visitors_can_interact_entities = excluded.visitors_can_interact_entities, visitors_can_use_redstone = excluded.visitors_can_use_redstone, lock_border = excluded.lock_border, steward_npc_spawned = excluded.steward_npc_spawned, steward_npc_x = excluded.steward_npc_x, steward_npc_y = excluded.steward_npc_y, steward_npc_z = excluded.steward_npc_z, steward_npc_yaw = excluded.steward_npc_yaw, steward_npc_pitch = excluded.steward_npc_pitch, updated_at = now()"
            )) {
                statement.setObject(1, territory.id);
                statement.setString(2, territory.ownerType.name());
                statement.setString(3, territory.ownerId);
                statement.setString(4, territory.ownerName);
                statement.setString(5, territory.displayName == null || territory.displayName.isBlank() ? territory.ownerName : territory.displayName);
                statement.setString(6, territory.serverId);
                statement.setString(7, territory.worldName);
                statement.setString(8, territory.worldKey == null ? territory.worldName : territory.worldKey);
                statement.setInt(9, territory.slotIndex);
                statement.setString(10, territory.generationState == null ? "READY" : territory.generationState);
                statement.setInt(11, territory.centerX);
                statement.setInt(12, territory.centerZ);
                statement.setInt(13, territory.radius);
                statement.setInt(14, territory.minX);
                statement.setInt(15, territory.maxX);
                statement.setInt(16, territory.minZ);
                statement.setInt(17, territory.maxZ);
                statement.setDouble(18, territory.spawnX);
                statement.setDouble(19, territory.spawnY);
                statement.setDouble(20, territory.spawnZ);
                statement.setFloat(21, territory.spawnYaw);
                statement.setFloat(22, territory.spawnPitch);
                statement.setInt(23, territory.level);
                if (territory.biomePreference == null) statement.setNull(24, Types.VARCHAR); else statement.setString(24, territory.biomePreference);
                statement.setBoolean(25, territory.isPublic);
                statement.setBoolean(26, territory.allowVisitors);
                statement.setBoolean(27, territory.visitorsCanBuild);
                statement.setBoolean(28, territory.visitorsCanOpenContainers);
                statement.setBoolean(29, territory.visitorsCanInteractEntities);
                statement.setBoolean(30, territory.visitorsCanUseRedstone);
                statement.setBoolean(31, territory.lockBorder);
                statement.setBoolean(32, territory.stewardNpcSpawned);
                if (territory.stewardNpcX == null) statement.setNull(33, Types.DOUBLE); else statement.setDouble(33, territory.stewardNpcX);
                if (territory.stewardNpcY == null) statement.setNull(34, Types.DOUBLE); else statement.setDouble(34, territory.stewardNpcY);
                if (territory.stewardNpcZ == null) statement.setNull(35, Types.DOUBLE); else statement.setDouble(35, territory.stewardNpcZ);
                if (territory.stewardNpcYaw == null) statement.setNull(36, Types.REAL); else statement.setFloat(36, territory.stewardNpcYaw);
                if (territory.stewardNpcPitch == null) statement.setNull(37, Types.REAL); else statement.setFloat(37, territory.stewardNpcPitch);
                statement.executeUpdate();
            }
            TERRITORIES.put(territory.id, territory);
            OWNER_INDEX.put(ownerKey(territory.ownerType, territory.ownerId), territory.id);
            rebuildSpatialIndexes();
            callback.done(true, "Territory saved.");
        });
    }

    private static Territory read(ResultSet rs) throws Exception {
        Territory t = new Territory();
        t.id = (UUID) rs.getObject("id");
        t.ownerType = OwnerType.valueOf(rs.getString("owner_type"));
        t.ownerId = rs.getString("owner_id");
        t.ownerName = rs.getString("owner_name");
        t.displayName = getStringOrNull(rs, "display_name");
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
        t.stewardNpcSpawned = getBooleanOrDefault(rs, "steward_npc_spawned", true);
        t.stewardNpcX = getDoubleOrNull(rs, "steward_npc_x");
        t.stewardNpcY = getDoubleOrNull(rs, "steward_npc_y");
        t.stewardNpcZ = getDoubleOrNull(rs, "steward_npc_z");
        t.stewardNpcYaw = getFloatOrNull(rs, "steward_npc_yaw");
        t.stewardNpcPitch = getFloatOrNull(rs, "steward_npc_pitch");
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
    private static Double getDoubleOrNull(ResultSet rs, String column) {
        try { double value = rs.getDouble(column); return rs.wasNull() ? null : value; } catch (Exception ignored) { return null; }
    }
    private static Float getFloatOrNull(ResultSet rs, String column) {
        try { float value = rs.getFloat(column); return rs.wasNull() ? null : value; } catch (Exception ignored) { return null; }
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

    public static List<String> settingSuggestions() {
        return List.of("public", "visitors", "visitorbuild", "visitorcontainers", "visitorentities", "visitorredstone", "border");
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
