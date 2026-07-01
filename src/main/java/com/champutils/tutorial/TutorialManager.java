package com.champutils.tutorial;

import com.champutils.database.DatabaseManager;
import com.champutils.economy.EconomyManager;
import com.champutils.shop.NpcShopService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TutorialManager {
    private static final Map<UUID, TutorialState> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_ACTIONBAR = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_CHAT_REMINDER = new ConcurrentHashMap<>();

    public static final List<SpawnGuideNpc> NPCS = List.of(
            new SpawnGuideNpc("welcome", "Welcome Guide", List.of(
                    "§6Welcome to Cobble Champs!",
                    "§7This is not a normal Cobblemon server. Your profile grows through gyms, professions, PvP, quests, bosses, guilds, and exploration.",
                    "§7Use §f/menu §7as your main hub. It links you to most custom systems.",
                    "§7Start simple: catch Pokémon, beat gyms, earn credits, and explore the other guide NPCs in spawn."
            )),
            new SpawnGuideNpc("profiles", "Profile Guide", List.of(
                    "§6Profiles let you play different styles without mixing progress.",
                    "§7Normal is the standard experience. Ironman, Islander, Monotype, and Nuzlocke add special rules and restrictions.",
                    "§7Most progression is tied to your active profile, so always make sure you are on the profile you want before grinding."
            )),
            new SpawnGuideNpc("gyms", "Gym Guide", List.of(
                    "§6Gyms are the backbone of progression.",
                    "§7Badges unlock more features and raise your wild Pokémon level cap over time.",
                    "§7Gym teams are built from configured pools, so rematches can feel different while still matching the gym theme.",
                    "§7If wild Pokémon feel too low-level, keep progressing through gyms."
            )),
            new SpawnGuideNpc("professions", "Profession Guide", List.of(
                    "§6Professions reward you for playing the server.",
                    "§7Mining, Forestry, Farming, and Battling all level as you use the right tools or battle systems.",
                    "§7Profession tools can roll passives and actives. Higher tiers are stronger and give you more ways to earn resources.",
                    "§7Use §f/professions §7and related profession menus to check progress, tools, fragments, and trades."
            )),
            new SpawnGuideNpc("economy", "Economy Guide", List.of(
                    "§6Credits are the main server currency.",
                    "§7You can earn credits from progression systems, battles, quests, shops, bosses, and other activities.",
                    "§7Use the Auction House and chest shops to trade with other players.",
                    "§7Credits are intentionally valuable, so most systems are balanced around steady long-term earning."
            )),
            new SpawnGuideNpc("claims", "Claim Guide", List.of(
                    "§6Claims protect your builds.",
                    "§7Use the golden sword claim tool to select corners, or use claim commands from the claims menu.",
                    "§7Claims protect the full vertical area and can be expanded, managed, and trusted to friends.",
                    "§7Make sure to claim important builds before storing valuables."
            )),
            new SpawnGuideNpc("pvp", "PvP Guide", List.of(
                    "§6Cobble Champs is built around competitive Pokémon battles.",
                    "§7Ranked and casual queues use configured formats, rules, clauses, bans, and rewards.",
                    "§7Ranked earns RP and ranked tokens, while casual is better for practice.",
                    "§7Check the battle menus before queueing so your team matches the current format."
            )),
            new SpawnGuideNpc("guilds", "Guild Guide", List.of(
                    "§6Guilds are your long-term group progression system.",
                    "§7Guilds can work together on bosses, quests, buffs, and shared goals.",
                    "§7Guild tags and progression help groups build an identity on the server.",
                    "§7Join a guild early if you want a team to progress with."
            )),
            new SpawnGuideNpc("bosses", "Boss and Spawn Guide", List.of(
                    "§6World events keep the server active.",
                    "§7World bosses, mega bosses, roaming trainers, and special spawns give rare rewards and reasons to explore.",
                    "§7Legendary, Mythical, Ultra Beast, and Paradox spawns are handled by special pools and timers.",
                    "§7Watch chat, menus, and notifications so you do not miss major events."
            )),
            new SpawnGuideNpc("quests", "Quest Guide", List.of(
                    "§6Daily and weekly activities give steady progress.",
                    "§7Quests, hunts, contracts, daily login rewards, crates, expeditions, and the reward track all give you goals outside gyms.",
                    "§7Use §f/quest §7and §f/menu §7when you are not sure what to do next."
            )),
            new SpawnGuideNpc("crafting", "Crafting Guide", List.of(
                    "§6Cobble Champs has custom crafting and progression shops.",
                    "§7Use §f/crafting §7to view special recipes. Many custom recipes use items and small credit costs.",
                    "§7Profession trades can turn useful resources into XP candies and other progression items.",
                    "§7If an item feels important, check the crafting and shop menus before throwing it away."
            ))
    );

    private TutorialManager() {}

    public static void ensureSchemaAsync() {
        DatabaseManager.executeAsync("tutorial schema", connection -> {
            try (PreparedStatement state = connection.prepareStatement("""
                    create table if not exists public.spawn_tutorial_state (
                        player_uuid uuid primary key,
                        skipped boolean not null default false,
                        completed boolean not null default false,
                        reward_claimed boolean not null default false,
                        created_at timestamptz not null default now(),
                        updated_at timestamptz not null default now()
                    )
                    """)) {
                state.executeUpdate();
            }
            try (PreparedStatement progress = connection.prepareStatement("""
                    create table if not exists public.spawn_tutorial_npc_progress (
                        player_uuid uuid not null,
                        npc_id text not null,
                        completed_at timestamptz not null default now(),
                        primary key (player_uuid, npc_id)
                    )
                    """)) {
                progress.executeUpdate();
            }
            try (PreparedStatement index = connection.prepareStatement("create index if not exists idx_spawn_tutorial_npc_progress_player on public.spawn_tutorial_npc_progress(player_uuid)")) {
                index.executeUpdate();
            }
        });
    }

    public static void handleJoin(ServerPlayer player) {
        loadAsync(player, true);
    }

    public static void unload(ServerPlayer player) {
        if (player != null) {
            LAST_ACTIONBAR.remove(player.getUUID());
            LAST_CHAT_REMINDER.remove(player.getUUID());
        }
    }

    public static void tick(MinecraftServer server) {
        if (server == null || server.getTickCount() % 100 != 0) return;
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TutorialState state = state(player);
            if (!state.shouldShowNotifiers()) continue;

            long lastActionbar = LAST_ACTIONBAR.getOrDefault(player.getUUID(), 0L);
            if (now - lastActionbar >= 10_000L) {
                LAST_ACTIONBAR.put(player.getUUID(), now);
                player.displayClientMessage(Component.literal("§eTutorial Quest: §fTalk to all spawn guide NPCs §7(" + state.completedNpcIds.size() + "/" + NPCS.size() + ") §8| §7/skiptutorial"), true);
            }

            long lastChat = LAST_CHAT_REMINDER.getOrDefault(player.getUUID(), 0L);
            if (now - lastChat >= 120_000L) {
                LAST_CHAT_REMINDER.put(player.getUUID(), now);
                player.sendSystemMessage(Component.literal("§eNew player quest: §fTalk to all NPCs in spawn §7for a starter reward. Use §f/tutorial §7to check progress or §f/skiptutorial §7to hide this."));
            }
        }
    }

    public static void showProgress(ServerPlayer player) {
        TutorialState state = state(player);
        if (!state.loaded) {
            player.sendSystemMessage(Component.literal("§eLoading tutorial progress...").withStyle(ChatFormatting.YELLOW));
            loadAsync(player, true);
            return;
        }
        player.sendSystemMessage(Component.literal("§6§lSpawn Guide Quest"));
        if (state.completed) {
            player.sendSystemMessage(Component.literal("§aComplete! You already claimed the spawn guide reward."));
            return;
        }
        if (state.skipped) {
            player.sendSystemMessage(Component.literal("§7You skipped tutorial reminders. Use §f/tutorial resetself §7if you want them back."));
        }
        for (SpawnGuideNpc npc : NPCS) {
            boolean done = state.completedNpcIds.contains(npc.id);
            player.sendSystemMessage(Component.literal((done ? "§a✔ " : "§c□ ") + npc.displayName));
        }
        player.sendSystemMessage(Component.literal("§7Progress: §f" + state.completedNpcIds.size() + "§7/§f" + NPCS.size() + " §8| §7Reward: §f16 Great Balls, 5 Potions, 2 Revives, 5 XS XP Candies, 50 Credits"));
    }

    public static void skip(ServerPlayer player) {
        TutorialState state = state(player);
        state.loaded = true;
        state.skipped = true;
        saveStateAsync(player.getUUID(), state);
        player.sendSystemMessage(Component.literal("§7Tutorial reminders hidden. You can still talk to spawn guide NPCs to finish the quest, or use §f/tutorial resetself §7to turn reminders back on."));
    }

    public static void resetSelf(ServerPlayer player) {
        UUID uuid = player.getUUID();
        CACHE.remove(uuid);
        LAST_ACTIONBAR.remove(uuid);
        LAST_CHAT_REMINDER.remove(uuid);
        DatabaseManager.executeAsync("tutorial reset self", connection -> {
            try (PreparedStatement deleteProgress = connection.prepareStatement("delete from public.spawn_tutorial_npc_progress where player_uuid = ?")) {
                deleteProgress.setObject(1, uuid);
                deleteProgress.executeUpdate();
            }
            try (PreparedStatement deleteState = connection.prepareStatement("delete from public.spawn_tutorial_state where player_uuid = ?")) {
                deleteState.setObject(1, uuid);
                deleteState.executeUpdate();
            }
        });
        loadAsync(player, true);
        player.sendSystemMessage(Component.literal("§aYour spawn guide quest was reset."));
    }

    public static void adminReset(ServerPlayer target, ServerPlayer actor) {
        if (target == null) return;
        UUID uuid = target.getUUID();
        CACHE.remove(uuid);
        LAST_ACTIONBAR.remove(uuid);
        LAST_CHAT_REMINDER.remove(uuid);
        DatabaseManager.executeAsync("tutorial admin reset", connection -> {
            try (PreparedStatement deleteProgress = connection.prepareStatement("delete from public.spawn_tutorial_npc_progress where player_uuid = ?")) {
                deleteProgress.setObject(1, uuid);
                deleteProgress.executeUpdate();
            }
            try (PreparedStatement deleteState = connection.prepareStatement("delete from public.spawn_tutorial_state where player_uuid = ?")) {
                deleteState.setObject(1, uuid);
                deleteState.executeUpdate();
            }
        });
        loadAsync(target, true);
        if (actor != null) actor.sendSystemMessage(Component.literal("§aReset tutorial progress for " + target.getName().getString() + "."));
    }


    public static void completeNpcStep(ServerPlayer player, String rawNpcId) {
        String npcId = normalize(rawNpcId);
        SpawnGuideNpc npc = byId(npcId);
        if (npc == null || player == null) return;

        TutorialState state = state(player);
        state.loaded = true;

        if (state.completed) {
            return;
        }

        boolean newlyCompleted = state.completedNpcIds.add(npc.id);
        if (newlyCompleted) {
            saveNpcProgressAsync(player.getUUID(), npc.id);
            player.sendSystemMessage(Component.literal("§a✓ Learned about " + shortDisplayName(npc) + " §7(" + state.completedNpcIds.size() + "/" + NPCS.size() + ")"));
        }

        if (state.completedNpcIds.containsAll(requiredIds())) {
            complete(player, state);
        } else if (newlyCompleted) {
            List<SpawnGuideNpc> remaining = remaining(state);
            if (!remaining.isEmpty()) {
                player.sendSystemMessage(Component.literal("§7Next: talk to §f" + remaining.get(0).displayName + "§7."));
            }
        }
    }

    public static void talk(ServerPlayer player, String rawNpcId) {
        String npcId = normalize(rawNpcId);
        SpawnGuideNpc npc = byId(npcId);
        if (npc == null) {
            player.sendSystemMessage(Component.literal("§cUnknown guide NPC id: " + rawNpcId));
            return;
        }

        TutorialState state = state(player);
        state.loaded = true;

        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§6§l" + npc.displayName));
        for (String line : npc.lines) {
            player.sendSystemMessage(Component.literal(line));
        }

        if (state.completed) {
            return;
        }

        boolean newlyCompleted = state.completedNpcIds.add(npc.id);
        if (newlyCompleted) {
            saveNpcProgressAsync(player.getUUID(), npc.id);
            player.sendSystemMessage(Component.literal("§aGuide learned: §f" + npc.displayName + " §7(" + state.completedNpcIds.size() + "/" + NPCS.size() + ")"));
        }

        if (state.completedNpcIds.containsAll(requiredIds())) {
            complete(player, state);
        } else if (newlyCompleted) {
            List<SpawnGuideNpc> remaining = remaining(state);
            if (!remaining.isEmpty()) {
                player.sendSystemMessage(Component.literal("§7Next: talk to §f" + remaining.get(0).displayName + "§7."));
            }
        }
    }

    private static void complete(ServerPlayer player, TutorialState state) {
        state.completed = true;
        state.skipped = false;
        boolean shouldReward = !state.rewardClaimed;
        state.rewardClaimed = true;
        saveStateAsync(player.getUUID(), state);
        if (shouldReward) {
            giveRewards(player);
        }
        player.sendSystemMessage(Component.literal("§a§lQuest Complete! §fTalk to all NPCs in spawn"));
        player.sendSystemMessage(Component.literal("§7You can always use §f/tutorial §7if you want to review what you learned."));
    }

    private static void giveRewards(ServerPlayer player) {
        give(player, "cobblemon:great_ball", 16);
        give(player, "cobblemon:potion", 5);
        give(player, "cobblemon:revive", 2);
        give(player, "cobblemon:exp_candy_xs", 5);
        EconomyManager.deposit(player, EconomyManager.wholeCreditsToCents(50), "spawn_tutorial_complete");
    }

    private static void give(ServerPlayer player, String itemId, int amount) {
        Item item = resolveItem(itemId);
        if (item == Items.AIR) {
            System.err.println("[ChampUtils] Tutorial reward item not found: " + itemId);
            return;
        }
        int remaining = Math.max(1, amount);
        while (remaining > 0) {
            int give = Math.min(item.getDefaultMaxStackSize(), remaining);
            NpcShopService.giveOrDrop(player, new ItemStack(item, give));
            remaining -= give;
        }
    }

    private static Item resolveItem(String itemId) {
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            return item == null ? Items.AIR : item;
        } catch (Exception e) {
            return Items.AIR;
        }
    }

    private static TutorialState state(ServerPlayer player) {
        return CACHE.computeIfAbsent(player.getUUID(), ignored -> {
            TutorialState state = new TutorialState();
            state.loaded = false;
            return state;
        });
    }

    private static void loadAsync(ServerPlayer player, boolean notify) {
        UUID uuid = player.getUUID();
        DatabaseManager.supplyAsync("tutorial load", connection -> {
            TutorialState state = new TutorialState();
            try (PreparedStatement insert = connection.prepareStatement("insert into public.spawn_tutorial_state(player_uuid) values (?) on conflict (player_uuid) do nothing")) {
                insert.setObject(1, uuid);
                insert.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("select skipped, completed, reward_claimed from public.spawn_tutorial_state where player_uuid = ?")) {
                ps.setObject(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        state.skipped = rs.getBoolean("skipped");
                        state.completed = rs.getBoolean("completed");
                        state.rewardClaimed = rs.getBoolean("reward_claimed");
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement("select npc_id from public.spawn_tutorial_npc_progress where player_uuid = ?")) {
                ps.setObject(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String id = normalize(rs.getString("npc_id"));
                        if (byId(id) != null) state.completedNpcIds.add(id);
                    }
                }
            }
            state.loaded = true;
            return state;
        }).thenAccept(state -> player.server.execute(() -> {
            if (player.hasDisconnected()) return;
            CACHE.put(uuid, state);
            if (notify && state.shouldShowNotifiers()) {
                player.sendSystemMessage(Component.literal("§eNew player quest: §fTalk to all NPCs in spawn §7(" + state.completedNpcIds.size() + "/" + NPCS.size() + "). Use §f/tutorial §7for progress or §f/skiptutorial §7to hide it."));
            }
        })).exceptionally(error -> {
            System.err.println("[ChampUtils] Failed to load tutorial progress for " + player.getName().getString());
            error.printStackTrace();
            return null;
        });
    }

    private static void saveNpcProgressAsync(UUID uuid, String npcId) {
        DatabaseManager.executeAsync("tutorial npc progress", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("insert into public.spawn_tutorial_npc_progress(player_uuid, npc_id) values (?, ?) on conflict (player_uuid, npc_id) do nothing")) {
                ps.setObject(1, uuid);
                ps.setString(2, npcId);
                ps.executeUpdate();
            }
        });
    }

    private static void saveStateAsync(UUID uuid, TutorialState state) {
        DatabaseManager.executeAsync("tutorial state save", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    insert into public.spawn_tutorial_state(player_uuid, skipped, completed, reward_claimed, updated_at)
                    values (?, ?, ?, ?, now())
                    on conflict (player_uuid) do update set
                        skipped = excluded.skipped,
                        completed = excluded.completed,
                        reward_claimed = excluded.reward_claimed,
                        updated_at = now()
                    """)) {
                ps.setObject(1, uuid);
                ps.setBoolean(2, state.skipped);
                ps.setBoolean(3, state.completed);
                ps.setBoolean(4, state.rewardClaimed);
                ps.executeUpdate();
            }
        });
    }

    private static SpawnGuideNpc byId(String id) {
        String normalized = normalize(id);
        for (SpawnGuideNpc npc : NPCS) {
            if (npc.id.equals(normalized)) return npc;
        }
        return null;
    }


    public static boolean isValidNpcId(String id) {
        return byId(id) != null;
    }

    public static SpawnGuideNpc getNpc(String id) {
        return byId(id);
    }

    public static String normalizeNpcId(String value) {
        return normalize(value);
    }

    private static String shortDisplayName(SpawnGuideNpc npc) {
        if (npc == null || npc.displayName == null) return "Tutorial";
        return npc.displayName.replace(" Guide", "");
    }

    public static List<String> npcIds() {
        List<String> ids = new ArrayList<>();
        for (SpawnGuideNpc npc : NPCS) ids.add(npc.id);
        return ids;
    }

    private static Set<String> requiredIds() {
        return Set.copyOf(npcIds());
    }

    private static List<SpawnGuideNpc> remaining(TutorialState state) {
        return NPCS.stream()
                .filter(npc -> !state.completedNpcIds.contains(npc.id))
                .sorted(Comparator.comparing(npc -> npc.displayName))
                .toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
