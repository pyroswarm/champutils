package com.champutils.claims;

import com.champutils.database.DatabaseManager;
import com.champutils.network.NetworkServerConfig;
import com.champutils.profile.PlayerProfileManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LandClaimRepository {
    public static final class Claim {
        public UUID id;
        public UUID profileId;
        public UUID playerUuid;
        public String ownerName;
        public String serverId;
        public String worldName;
        public String worldKey;
        public int minX;
        public int maxX;
        public int minZ;
        public int maxZ;
        public boolean allowVisitors = true;
        public boolean visitorsCanBuild = false;
        public boolean visitorsCanOpenContainers = false;
        public boolean visitorsCanInteractEntities = false;
        public boolean visitorsCanUseRedstone = false;
        public boolean visitorsCanUseDoors = false;
        public boolean visitorsCanCatchPokemon = false;
        public boolean pokemonSpawningEnabled = true;
        public Set<UUID> memberProfileIds = ConcurrentHashMap.newKeySet();

        public boolean contains(String serverId, String worldName, BlockPos pos) {
            if (pos == null) return false;
            // Claims are intentionally 2D columns: if X/Z is inside the claim, every Y
            // from world min build height/bedrock through max build height is protected.
            return this.serverId.equalsIgnoreCase(serverId)
                    && this.worldName.equalsIgnoreCase(worldName)
                    && pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        public int area() {
            return (Math.abs(maxX - minX) + 1) * (Math.abs(maxZ - minZ) + 1);
        }
    }

    private static final Map<UUID, Claim> CLAIMS = new ConcurrentHashMap<>();
    private static final Map<String, Map<Long, List<Claim>>> CLAIMS_BY_WORLD_CHUNK = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Claim>> CLAIMS_BY_PROFILE = new ConcurrentHashMap<>();

    private LandClaimRepository() {}

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure land claim schema", LandClaimRepository::ensureSchema);
    }

    public static void ensureSchema(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "create table if not exists profile_land_claims (" +
                        "id uuid primary key default gen_random_uuid(), " +
                        "profile_id uuid not null references player_profiles(id) on delete cascade, " +
                        "player_uuid uuid not null references players(uuid) on delete cascade, " +
                        "owner_name text not null, server_id text not null, world_name text not null, world_key text not null, " +
                        "min_x integer not null, max_x integer not null, min_z integer not null, max_z integer not null, " +
                        "settings jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now())")) {
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("create index if not exists idx_profile_land_claims_profile on profile_land_claims(profile_id)")) { statement.executeUpdate(); }
        try (PreparedStatement statement = connection.prepareStatement("create index if not exists idx_profile_land_claims_world_bounds on profile_land_claims(server_id, world_name, min_x, max_x, min_z, max_z)")) { statement.executeUpdate(); }
        try (PreparedStatement statement = connection.prepareStatement("create index if not exists idx_profile_land_claims_world_key on profile_land_claims(world_key)")) { statement.executeUpdate(); }
        try (PreparedStatement statement = connection.prepareStatement("create table if not exists profile_land_claim_members (claim_id uuid not null references profile_land_claims(id) on delete cascade, profile_id uuid not null references player_profiles(id) on delete cascade, added_by_profile_id uuid references player_profiles(id) on delete set null, added_at timestamptz not null default now(), primary key(claim_id, profile_id))")) { statement.executeUpdate(); }
        try (PreparedStatement statement = connection.prepareStatement("create index if not exists idx_profile_land_claim_members_profile on profile_land_claim_members(profile_id)")) { statement.executeUpdate(); }
    }

    public static void refreshAll() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("refresh land claim cache", connection -> {
            ensureSchema(connection);
            Map<UUID, Claim> fresh = new ConcurrentHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("select * from profile_land_claims")) {
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        Claim claim = read(rs);
                        fresh.put(claim.id, claim);
                    }
                }
            }
            loadMembers(connection, fresh);
            CLAIMS.clear();
            CLAIMS.putAll(fresh);
            rebuildIndexes();
            System.out.println("[ChampUtils] Loaded " + CLAIMS.size() + " land claims from the database.");
        });
    }

    public static Collection<Claim> allCached() { return CLAIMS.values(); }
    public static Claim findById(UUID claimId) { return claimId == null ? null : CLAIMS.get(claimId); }

    public static List<Claim> cachedForProfile(UUID profileId) {
        if (profileId == null) return Collections.emptyList();
        List<Claim> claims = CLAIMS_BY_PROFILE.get(profileId);
        return claims == null ? Collections.emptyList() : claims;
    }

    public static List<Claim> numberedClaimsForProfile(UUID profileId) {
        List<Claim> claims = new ArrayList<>(cachedForProfile(profileId));
        claims.sort(Comparator
                .comparing((Claim claim) -> claim.worldName == null ? "" : claim.worldName)
                .thenComparingInt(claim -> claim.minX)
                .thenComparingInt(claim -> claim.minZ)
                .thenComparing(claim -> claim.id == null ? new UUID(0L, 0L) : claim.id));
        return claims;
    }

    public static int numberForClaim(Claim claim) {
        if (claim == null) return -1;
        List<Claim> claims = numberedClaimsForProfile(claim.profileId);
        for (int i = 0; i < claims.size(); i++) {
            if (claim.id != null && claim.id.equals(claims.get(i).id)) return i + 1;
        }
        return -1;
    }

    public static Claim findAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return null;
        String worldName = level.dimension().location().toString();
        long chunk = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        for (String serverId : serverIdAliases()) {
            Map<Long, List<Claim>> chunks = CLAIMS_BY_WORLD_CHUNK.get(worldKey(serverId, worldName));
            if (chunks == null) continue;
            List<Claim> candidates = chunks.get(chunk);
            if (candidates == null) continue;
            for (Claim claim : candidates) {
                if (claim.contains(serverId, worldName, pos)) return claim;
            }
        }
        return null;
    }

    public static boolean isOwner(ServerPlayer player, Claim claim) {
        if (player == null || claim == null) return false;
        if (player.hasPermissions(4)) return true;
        UUID activeProfile = PlayerProfileManager.activeProfileId(player);
        return activeProfile != null && activeProfile.equals(claim.profileId);
    }

    public static boolean isMember(ServerPlayer player, Claim claim) {
        if (player == null || claim == null) return false;
        UUID activeProfile = PlayerProfileManager.activeProfileId(player);
        return activeProfile != null && claim.memberProfileIds.contains(activeProfile);
    }

    public static boolean canEnter(ServerPlayer player, Claim claim) { return isOwner(player, claim) || isMember(player, claim) || claim.allowVisitors; }
    public static boolean canBuild(ServerPlayer player, Claim claim) { return isOwner(player, claim) || isMember(player, claim) || claim.visitorsCanBuild; }
    public static boolean canOpenContainers(ServerPlayer player, Claim claim) { return isOwner(player, claim) || isMember(player, claim) || claim.visitorsCanOpenContainers; }
    public static boolean canInteractEntities(ServerPlayer player, Claim claim) { return isOwner(player, claim) || isMember(player, claim) || claim.visitorsCanInteractEntities; }
    public static boolean canUseRedstone(ServerPlayer player, Claim claim) { return isOwner(player, claim) || isMember(player, claim) || claim.visitorsCanUseRedstone; }
    public static boolean canUseDoors(ServerPlayer player, Claim claim) { return isOwner(player, claim) || isMember(player, claim) || claim.visitorsCanUseDoors; }
    public static boolean canCatchPokemon(ServerPlayer player, Claim claim) { return isOwner(player, claim) || isMember(player, claim) || claim.visitorsCanCatchPokemon; }

    public static boolean overlapsCached(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
        if (level == null) return false;
        String worldName = level.dimension().location().toString();
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        for (String serverId : serverIdAliases()) {
            Map<Long, List<Claim>> chunks = CLAIMS_BY_WORLD_CHUNK.get(worldKey(serverId, worldName));
            if (chunks == null) continue;
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    List<Claim> list = chunks.get(chunkKey(cx, cz));
                    if (list == null) continue;
                    for (Claim claim : list) {
                        if (claim.maxX >= minX && claim.minX <= maxX && claim.maxZ >= minZ && claim.minZ <= maxZ) return true;
                    }
                }
            }
        }
        return false;
    }

    public static CreateResult create(ServerPlayer player, ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
        if (player == null || level == null) return CreateResult.fail("Player/world missing.");
        if (PlayerProfileManager.isIslander(player)) return CreateResult.fail("Islander profiles cannot claim land. Your island is your home.");
        if (!DatabaseManager.isEnabled()) return CreateResult.fail("Land claims require the SQL database.");
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null) return CreateResult.fail("You must select and load a profile before creating land claims.");
        int area = (Math.abs(maxX - minX) + 1) * (Math.abs(maxZ - minZ) + 1);
        if (area < LandClaimConfig.minArea()) return CreateResult.fail("Claim is too small. Minimum area is " + LandClaimConfig.minArea() + " blocks.");
        if (area > LandClaimConfig.maxArea()) return CreateResult.fail("Claim is too large. Maximum area is " + LandClaimConfig.maxArea() + " blocks.");
        if (totalAreaCached(profileId) + area > LandClaimConfig.maxTotalClaimBlocksPerProfile()) return CreateResult.fail("This profile can only claim " + LandClaimConfig.maxTotalClaimBlocksPerProfile() + " total blocks.");
        if (cachedForProfile(profileId).size() >= LandClaimConfig.maxClaimsPerProfile(player)) return CreateResult.fail("This profile already has the maximum number of claims.");
        if (overlapsCached(level, minX, maxX, minZ, maxZ)) return CreateResult.fail("That area overlaps an existing claim.");

        try {
            Connection connection = DatabaseManager.getConnection();
            ensureSchema(connection);
            try (PreparedStatement overlap = connection.prepareStatement(
                    "select id from profile_land_claims where server_id = any(?) and world_name = ? and max_x >= ? and min_x <= ? and max_z >= ? and min_z <= ? limit 1")) {
                overlap.setArray(1, connection.createArrayOf("text", serverIdAliases().toArray(new String[0])));
                overlap.setString(2, level.dimension().location().toString());
                overlap.setInt(3, minX);
                overlap.setInt(4, maxX);
                overlap.setInt(5, minZ);
                overlap.setInt(6, maxZ);
                try (ResultSet rs = overlap.executeQuery()) {
                    if (rs.next()) return CreateResult.fail("That area overlaps an existing claim.");
                }
            }
            try (PreparedStatement count = connection.prepareStatement("select count(*) from profile_land_claims where profile_id = ?")) {
                count.setObject(1, profileId, Types.OTHER);
                try (ResultSet rs = count.executeQuery()) {
                    if (rs.next() && rs.getInt(1) >= LandClaimConfig.maxClaimsPerProfile(player)) {
                        return CreateResult.fail("This profile already has the maximum number of claims.");
                    }
                }
            }
            try (PreparedStatement total = connection.prepareStatement("select coalesce(sum((abs(max_x - min_x) + 1) * (abs(max_z - min_z) + 1)), 0) from profile_land_claims where profile_id = ?")) {
                total.setObject(1, profileId, Types.OTHER);
                try (ResultSet rs = total.executeQuery()) {
                    if (rs.next() && rs.getInt(1) + area > LandClaimConfig.maxTotalClaimBlocksPerProfile()) {
                        return CreateResult.fail("This profile can only claim " + LandClaimConfig.maxTotalClaimBlocksPerProfile() + " total blocks.");
                    }
                }
            }
            Claim claim;
            try (PreparedStatement insert = connection.prepareStatement(
                    "insert into profile_land_claims(profile_id, player_uuid, owner_name, server_id, world_name, world_key, min_x, max_x, min_z, max_z, settings) " +
                            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '{}'::jsonb) returning *")) {
                insert.setObject(1, profileId, Types.OTHER);
                insert.setObject(2, player.getUUID(), Types.OTHER);
                insert.setString(3, player.getName().getString());
                insert.setString(4, NetworkServerConfig.serverId());
                insert.setString(5, level.dimension().location().toString());
                insert.setString(6, worldKey(NetworkServerConfig.serverId(), level.dimension().location().toString()));
                insert.setInt(7, minX);
                insert.setInt(8, maxX);
                insert.setInt(9, minZ);
                insert.setInt(10, maxZ);
                try (ResultSet rs = insert.executeQuery()) {
                    if (!rs.next()) return CreateResult.fail("Could not create claim.");
                    claim = read(rs);
                }
            }
            CLAIMS.put(claim.id, claim);
            rebuildIndexes();
            publishClaimInvalidation(claim);
            return CreateResult.success(claim);
        } catch (Exception e) {
            e.printStackTrace();
            return CreateResult.fail("Could not save claim to SQL.");
        }
    }


    public static int totalAreaCached(UUID profileId) {
        int total = 0;
        for (Claim claim : cachedForProfile(profileId)) total += claim.area();
        return total;
    }

    public static CreateResult resize(ServerPlayer player, Claim claim, int minX, int maxX, int minZ, int maxZ) {
        if (!isOwner(player, claim) || !DatabaseManager.isEnabled()) return CreateResult.fail("You do not own this claim on your active profile.");
        int oldArea = claim.area();
        int newArea = (Math.abs(maxX - minX) + 1) * (Math.abs(maxZ - minZ) + 1);
        if (newArea < LandClaimConfig.minArea()) return CreateResult.fail("Claim is too small. Minimum area is " + LandClaimConfig.minArea() + " blocks.");
        if (newArea > LandClaimConfig.maxArea()) return CreateResult.fail("Claim is too large. Maximum area is " + LandClaimConfig.maxArea() + " blocks.");
        if (totalAreaCached(claim.profileId) - oldArea + newArea > LandClaimConfig.maxTotalClaimBlocksPerProfile()) return CreateResult.fail("This profile can only claim " + LandClaimConfig.maxTotalClaimBlocksPerProfile() + " total blocks.");
        if (overlapsCachedExcept(player.serverLevel(), claim.id, minX, maxX, minZ, maxZ)) return CreateResult.fail("That resized area overlaps another claim.");
        try {
            Connection connection = DatabaseManager.getConnection();
            ensureSchema(connection);
            try (PreparedStatement overlap = connection.prepareStatement("select id from profile_land_claims where id <> ? and server_id = any(?) and world_name = ? and max_x >= ? and min_x <= ? and max_z >= ? and min_z <= ? limit 1")) {
                overlap.setObject(1, claim.id, Types.OTHER);
                overlap.setArray(2, connection.createArrayOf("text", serverIdAliases().toArray(new String[0])));
                overlap.setString(3, player.serverLevel().dimension().location().toString());
                overlap.setInt(4, minX); overlap.setInt(5, maxX); overlap.setInt(6, minZ); overlap.setInt(7, maxZ);
                try (ResultSet rs = overlap.executeQuery()) { if (rs.next()) return CreateResult.fail("That resized area overlaps another claim."); }
            }
            try (PreparedStatement statement = connection.prepareStatement("update profile_land_claims set min_x = ?, max_x = ?, min_z = ?, max_z = ?, updated_at = now() where id = ? and profile_id = ?")) {
                statement.setInt(1, minX); statement.setInt(2, maxX); statement.setInt(3, minZ); statement.setInt(4, maxZ);
                statement.setObject(5, claim.id, Types.OTHER); statement.setObject(6, claim.profileId, Types.OTHER);
                if (statement.executeUpdate() <= 0) return CreateResult.fail("Could not resize claim.");
            }
            claim.minX = minX; claim.maxX = maxX; claim.minZ = minZ; claim.maxZ = maxZ;
            rebuildIndexes();
            publishClaimInvalidation(claim);
            return CreateResult.success(claim);
        } catch (Exception e) {
            e.printStackTrace();
            return CreateResult.fail("Could not resize claim in SQL.");
        }
    }

    public static java.util.concurrent.CompletableFuture<Boolean> addMemberAsync(ServerPlayer owner, Claim claim, UUID friendProfile) {
        if (!isOwner(owner, claim) || friendProfile == null || !DatabaseManager.isEnabled() || friendProfile.equals(claim.profileId))
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        return DatabaseManager.supplyAsync("add land claim member", connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement("insert into profile_land_claim_members(claim_id, profile_id, added_by_profile_id) values (?, ?, ?) on conflict do nothing")) {
                ps.setObject(1, claim.id, Types.OTHER); ps.setObject(2, friendProfile, Types.OTHER); ps.setObject(3, claim.profileId, Types.OTHER);
                return ps.executeUpdate() > 0;
            }
        }).thenApply(changed -> {
            if (changed) { claim.memberProfileIds.add(friendProfile); com.champutils.network.NetworkEventManager.publishCacheInvalidation("LAND_CLAIMS", claim.id); }
            return changed;
        });
    }

    public static java.util.concurrent.CompletableFuture<Boolean> removeMemberAsync(ServerPlayer owner, Claim claim, UUID friendProfile) {
        if (!isOwner(owner, claim) || friendProfile == null || !DatabaseManager.isEnabled())
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        return DatabaseManager.supplyAsync("remove land claim member", connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement("delete from profile_land_claim_members where claim_id = ? and profile_id = ?")) {
                ps.setObject(1, claim.id, Types.OTHER); ps.setObject(2, friendProfile, Types.OTHER);
                return ps.executeUpdate() > 0;
            }
        }).thenApply(changed -> {
            if (changed) { claim.memberProfileIds.remove(friendProfile); com.champutils.network.NetworkEventManager.publishCacheInvalidation("LAND_CLAIMS", claim.id); }
            return changed;
        });
    }

    public static boolean overlapsCachedExcept(ServerLevel level, UUID exceptClaimId, int minX, int maxX, int minZ, int maxZ) {
        if (level == null) return false;
        String worldName = level.dimension().location().toString();
        for (String serverId : serverIdAliases()) {
            Map<Long, List<Claim>> chunks = CLAIMS_BY_WORLD_CHUNK.get(worldKey(serverId, worldName));
            if (chunks == null) continue;
            for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                    List<Claim> list = chunks.get(chunkKey(cx, cz));
                    if (list == null) continue;
                    for (Claim claim : list) {
                        if (claim.id.equals(exceptClaimId)) continue;
                        if (claim.maxX >= minX && claim.minX <= maxX && claim.maxZ >= minZ && claim.minZ <= maxZ) return true;
                    }
                }
            }
        }
        return false;
    }

    public static int deleteForProfileBlocking(UUID profileId) {
        if (profileId == null || !DatabaseManager.isEnabled()) return 0;
        try {
            Connection connection = DatabaseManager.getConnection();
            ensureSchema(connection);
            int changed;
            try (PreparedStatement statement = connection.prepareStatement("delete from profile_land_claims where profile_id = ?")) {
                statement.setObject(1, profileId, Types.OTHER);
                changed = statement.executeUpdate();
            }
            if (changed > 0) {
                CLAIMS.entrySet().removeIf(entry -> profileId.equals(entry.getValue().profileId));
                rebuildIndexes();
            }
            return changed;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static boolean delete(ServerPlayer player, Claim claim) {
        if (!isOwner(player, claim) || !DatabaseManager.isEnabled()) return false;
        try {
            Connection connection = DatabaseManager.getConnection();
            try (PreparedStatement statement = connection.prepareStatement("delete from profile_land_claims where id = ? and profile_id = ?")) {
                statement.setObject(1, claim.id, Types.OTHER);
                statement.setObject(2, claim.profileId, Types.OTHER);
                int changed = statement.executeUpdate();
                if (changed <= 0) return false;
            }
            CLAIMS.remove(claim.id);
            rebuildIndexes();
            publishClaimInvalidation(claim);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void updateSetting(ServerPlayer player, Claim claim, String setting, boolean value) {
        if (!isOwner(player, claim)) return;
        switch (setting) {
            case "allowVisitors" -> claim.allowVisitors = value;
            case "visitorsCanBuild" -> claim.visitorsCanBuild = value;
            case "visitorsCanOpenContainers" -> claim.visitorsCanOpenContainers = value;
            case "visitorsCanInteractEntities" -> claim.visitorsCanInteractEntities = value;
            case "visitorsCanUseRedstone" -> claim.visitorsCanUseRedstone = value;
            case "visitorsCanUseDoors" -> claim.visitorsCanUseDoors = value;
            case "visitorsCanCatchPokemon" -> claim.visitorsCanCatchPokemon = value;
            case "pokemonSpawningEnabled" -> claim.pokemonSpawningEnabled = value;
            default -> { return; }
        }
        saveSettingsAsync(claim);
    }

    private static void saveSettingsAsync(Claim claim) {
        if (!DatabaseManager.isEnabled() || claim == null) return;
        DatabaseManager.executeAsync("save land claim settings", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "update profile_land_claims set settings = jsonb_build_object(" +
                            "'allowVisitors', ?, 'visitorsCanBuild', ?, 'visitorsCanOpenContainers', ?, " +
                            "'visitorsCanInteractEntities', ?, 'visitorsCanUseRedstone', ?, 'visitorsCanUseDoors', ?, 'visitorsCanCatchPokemon', ?, 'pokemonSpawningEnabled', ?), updated_at = now() where id = ?")) {
                statement.setBoolean(1, claim.allowVisitors);
                statement.setBoolean(2, claim.visitorsCanBuild);
                statement.setBoolean(3, claim.visitorsCanOpenContainers);
                statement.setBoolean(4, claim.visitorsCanInteractEntities);
                statement.setBoolean(5, claim.visitorsCanUseRedstone);
                statement.setBoolean(6, claim.visitorsCanUseDoors);
                statement.setBoolean(7, claim.visitorsCanCatchPokemon);
                statement.setBoolean(8, claim.pokemonSpawningEnabled);
                statement.setObject(9, claim.id, Types.OTHER);
                statement.executeUpdate();
            }
            publishClaimInvalidation(claim);
        });
    }

    private static void publishClaimInvalidation(Claim claim) {
        if (claim == null || claim.id == null) return;
        com.champutils.network.NetworkEventManager.publishCacheInvalidation("LAND_CLAIMS", claim.id);
    }


    private static void loadMembers(Connection connection, Map<UUID, Claim> claims) throws Exception {
        for (Claim claim : claims.values()) claim.memberProfileIds.clear();
        try (PreparedStatement statement = connection.prepareStatement("select claim_id, profile_id from profile_land_claim_members")) {
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UUID claimId = (UUID) rs.getObject("claim_id");
                    UUID profileId = (UUID) rs.getObject("profile_id");
                    Claim claim = claims.get(claimId);
                    if (claim != null && profileId != null) claim.memberProfileIds.add(profileId);
                }
            }
        }
    }

    private static Claim read(ResultSet rs) throws Exception {
        Claim claim = new Claim();
        claim.id = (UUID) rs.getObject("id");
        claim.profileId = (UUID) rs.getObject("profile_id");
        claim.playerUuid = (UUID) rs.getObject("player_uuid");
        claim.ownerName = rs.getString("owner_name");
        claim.serverId = rs.getString("server_id");
        claim.worldName = rs.getString("world_name");
        claim.worldKey = rs.getString("world_key");
        claim.minX = Math.min(rs.getInt("min_x"), rs.getInt("max_x"));
        claim.maxX = Math.max(rs.getInt("min_x"), rs.getInt("max_x"));
        claim.minZ = Math.min(rs.getInt("min_z"), rs.getInt("max_z"));
        claim.maxZ = Math.max(rs.getInt("min_z"), rs.getInt("max_z"));
        String settings = rs.getString("settings");
        if (settings != null) {
            claim.allowVisitors = !settings.contains("\"allowVisitors\": false") && !settings.contains("\"allowVisitors\":false");
            claim.visitorsCanBuild = settings.contains("\"visitorsCanBuild\": true") || settings.contains("\"visitorsCanBuild\":true");
            claim.visitorsCanOpenContainers = settings.contains("\"visitorsCanOpenContainers\": true") || settings.contains("\"visitorsCanOpenContainers\":true");
            claim.visitorsCanInteractEntities = settings.contains("\"visitorsCanInteractEntities\": true") || settings.contains("\"visitorsCanInteractEntities\":true");
            claim.visitorsCanUseRedstone = settings.contains("\"visitorsCanUseRedstone\": true") || settings.contains("\"visitorsCanUseRedstone\":true");
            claim.visitorsCanUseDoors = settings.contains("\"visitorsCanUseDoors\": true") || settings.contains("\"visitorsCanUseDoors\":true");
            claim.visitorsCanCatchPokemon = settings.contains("\"visitorsCanCatchPokemon\": true") || settings.contains("\"visitorsCanCatchPokemon\":true");
            claim.pokemonSpawningEnabled = !settings.contains("\"pokemonSpawningEnabled\": false") && !settings.contains("\"pokemonSpawningEnabled\":false");
        }
        return claim;
    }

    private static void rebuildIndexes() {
        Map<String, Map<Long, List<Claim>>> chunkIndex = new ConcurrentHashMap<>();
        Map<UUID, List<Claim>> profileIndex = new ConcurrentHashMap<>();
        for (Claim claim : CLAIMS.values()) {
            if (claim == null) continue;
            profileIndex.computeIfAbsent(claim.profileId, ignored -> Collections.synchronizedList(new ArrayList<>())).add(claim);
            String key = worldKey(claim.serverId, claim.worldName);
            int minChunkX = claim.minX >> 4;
            int maxChunkX = claim.maxX >> 4;
            int minChunkZ = claim.minZ >> 4;
            int maxChunkZ = claim.maxZ >> 4;
            Map<Long, List<Claim>> chunks = chunkIndex.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    chunks.computeIfAbsent(chunkKey(cx, cz), ignored -> Collections.synchronizedList(new ArrayList<>())).add(claim);
                }
            }
        }
        CLAIMS_BY_WORLD_CHUNK.clear();
        CLAIMS_BY_WORLD_CHUNK.putAll(chunkIndex);
        CLAIMS_BY_PROFILE.clear();
        CLAIMS_BY_PROFILE.putAll(profileIndex);
    }

    private static Set<String> serverIdAliases() {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        String current = NetworkServerConfig.serverId();
        addAlias(aliases, current);

        // Only map true historical names for the backend currently running. Never add the
        // configured primary backend as an alias on Nova/Eclipse, otherwise equal X/Z claims
        // on two different servers incorrectly overlap and share protection checks.
        String normalized = current == null ? "" : current.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("survival") || normalized.equals("survival-1") || normalized.equals("main_survival1")) {
            addAlias(aliases, "survival");
            addAlias(aliases, "survival-1");
            addAlias(aliases, "main_survival1");
        }
        return aliases;
    }

    private static void addAlias(Set<String> aliases, String value) {
        if (value == null || value.isBlank()) return;
        aliases.add(value.trim().toLowerCase(Locale.ROOT));
    }

    private static long chunkKey(int chunkX, int chunkZ) { return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL); }
    private static String worldKey(String serverId, String worldName) { return (serverId == null ? "" : serverId.toLowerCase(Locale.ROOT)) + "|" + (worldName == null ? "" : worldName.toLowerCase(Locale.ROOT)); }

    public static final class CreateResult {
        public final boolean success;
        public final String message;
        public final Claim claim;
        private CreateResult(boolean success, String message, Claim claim) { this.success = success; this.message = message; this.claim = claim; }
        public static CreateResult success(Claim claim) { return new CreateResult(true, null, claim); }
        public static CreateResult fail(String message) { return new CreateResult(false, message, null); }
    }
}
