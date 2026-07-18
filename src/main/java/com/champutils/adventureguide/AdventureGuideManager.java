package com.champutils.adventureguide;

import com.champutils.database.SharedJsonStateRepository;
import com.champutils.economy.EconomyManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.territory.TerritoryRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdventureGuideManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils/adventure_guide/players");
    private static final Map<UUID, PlayerData> DATA = new ConcurrentHashMap<>();
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, CustomBossEvent> BARS = new ConcurrentHashMap<>();
    private static final String STATE_KEY = "adventure_guide";

    private static int tickCounter = 0;

    public static final List<Objective> STANDARD_OBJECTIVES = List.of(
            objective("talk_to_adventurer", "Talk to the Adventurer's Guild Representative", "Start here. The Adventurer's Guild is the hub for PvE, jobs, contracts, expeditions, tower runs, and server progression.", "talk_to_adventurer", 1, 250L, "The representative is inside the big utility building to the south."),
            objective("rtp_survival", "Use RTP to reach the survival world", "RTP takes you out of spawn and into the world where most gathering, battling, and exploration happens.", "rtp", 1, 250L, "Use /rtp or the Adventurer's Guild menu."),
            objective("catch_species", "Catch 5 new species of Pokemon", "The true dex tracks species progress per profile and rewards long-term collecting.", "catch_species", 5, 500L, "Catch Pokemon you have not caught on this profile before."),
            objective("profession_intro", "Earn profession progress 25 times", "Mining, farming, forestry, and other professions reward XP when using profession gear.", "profession_action", 25, 500L, "Gather with profession tools or complete profession actions."),
            objective("guild_board", "Open the Adventurer Board", "The Adventurer Board is where repeatable tasks, PvP quests, contracts, and player guild goals live.", "guild_board", 1, 250L, "Open it from the Adventurer's Guild Representative."),
            objective("finish_contract", "Complete and claim an Adventurer Contract", "Contracts teach daily repeatable goals and help you rank up with the Adventurer's Guild.", "contract_complete", 1, 750L, "Finish the objective, then claim it from the contract menu."),
            objective("auction_listing", "Create an Auction House listing", "The Auction House lets players sell useful items and Pokemon to each other.", "auction_listing", 1, 500L, "List an item or Pokemon from the Auction House menu."),
            objective("expedition_start", "Send a Pokemon on an Expedition", "Expeditions are passive jobs that return themed rewards after time passes.", "expedition_start", 1, 500L, "Open Expeditions through the Adventurer's Guild."),
            objective("pokemon_hunt", "Check a Pokemon Hunt", "Hunts are repeatable catch goals tied to the Adventurer's Guild.", "pokemon_hunt", 1, 400L, "Open Hunts from the Adventurer's Guild and review your target."),
            objective("adventurer_request", "Request an Adventurer challenge", "Adventurer requests spawn a trainer in the world and are a core PvE battle loop.", "adventurer_request", 1, 750L, "Request one from the Adventurer's Guild. If you are at spawn, you will be RTP'd first."),
            objective("pvp_mission", "Play a PvP queue battle", "PvP quests are listed on the Adventurer Board, while ranked progression comes from ranked battles.", "pvp_play", 1, 750L, "Queue casual or ranked PvP."),
            objective("battle_tower_checkpoint", "Reach Battle Tower floor 3", "The Battle Tower is a checkpoint-based PvE challenge. Rewards are paid when you clear a new checkpoint.", "battle_tower_checkpoint", 1, 1500L, "Start the Battle Tower from the Adventurer's Guild."),
            objective("join_guild", "Join or create a Player Guild", "Player Guilds are social progression groups with their own shared goals.", "guild", 1, 500L, "Use the Player Guild menu or ask another player for an invite."),
            objective("land_claim", "Create your first Land Claim", "Claims protect builds and teach players how to safely settle in survival.", "land_claim", 1, 500L, "Use /claims and follow the claim menu."),
            objective("open_crate", "Open a Crate", "Crates and keys are reward sinks for events, quests, contracts, and progression.", "crate", 1, 500L, "Use a crate key at the crate menu/NPC."),
            objective("dex_reward", "Claim a Dex reward", "Dex rewards make catching new species valuable beyond completion percentage.", "dex_reward", 1, 750L, "Open dex rewards after catching enough Pokemon."),
            objective("server_shop", "Open a server shop", "Server shops, Adventurer shops, and special shops explain where progression currencies are spent.", "shop", 1, 250L, "Open a shop from spawn or the Adventurer's Guild menus."),
            objective("cosmetic", "Equip a title, emblem, or chat tag", "Cosmetics show achievements without exposing backend permission details.", "cosmetic", 1, 500L, "Use the cosmetics/title/emblem menus."),
            objective("boss_event", "Participate in a boss event", "Mega bosses and world bosses create shared server moments and high-end rewards.", "world_boss", 1, 1000L, "Join a boss fight when one announces."),
            objective("settings", "Open Settings and choose your preferences", "Settings let players control popups, scoreboard, sounds, and the Adventure Guide boss bar.", "settings", 1, 250L, "Open /menu settings."),
            objective("defeat_misty", "Defeat Misty and earn the Cascade Badge", "Gym badges unlock progression and teach the main Cobblemon battle path.", "badge_cascade", 1, 500L, "Use the Gym menu/NPC to challenge Misty."),
            objective("collect_copper_chunks", "Collect 16 Copper Chunks", "Copper Chunks are the first real step into the E Rank profession gear loop.", "chunk_copper", 16, 500L, "Mine, chop, harvest, or battle with profession progress enabled until you find 16 Copper Chunks."),
            objective("craft_e_tool_armor_trinket", "Craft E Rank profession gear", "Craft one E Rank tool, one E Rank armor piece, and one E Rank trinket to learn the profession gear triangle.", "craft_e_gear_training", 3, 750L, "Use /essence craft e pickaxe, /essence craft e helmet, and /essence craft e magnet or another E Rank trinket."),
            objective("reroll_tool", "Reroll a profession tool", "Rerolling teaches how to improve a profession tool's stats before investing in higher ranks.", "tool_reroll", 1, 500L, "Hold a profession tool and use /itemroll reroll."),
            objective("salvage_common_tool", "Salvage a common profession tool", "Salvaging teaches how unwanted F Rank tools turn back into essence for future crafting.", "salvage_common_tool", 1, 500L, "Hold an F Rank/common profession tool and use /salvage."),
            objective("complete", "Adventure Guide complete", "You know the main Cobble Champs systems. Keep ranking up with the Adventurer's Guild.", "complete", 1, 2500L, "Keep playing your way.")
    );

    /** Islander progression mirrors the standard guide, but replaces wilderness RTP with territory creation. */
    public static final List<Objective> ISLANDER_OBJECTIVES = STANDARD_OBJECTIVES.stream()
            .map(objective -> switch (objective.id()) {
                case "rtp_survival" -> objective("create_territory", "Create your Islander Territory",
                        "Your territory is your permanent Islander home and replaces the normal wilderness progression step.",
                        "territory_created", 1, 250L, "Use /territory create. This completes when the territory is fully ready.");
                case "adventurer_request" -> objective("adventurer_request", "Request an Adventurer challenge",
                        "Adventurer requests bring a trainer directly to your Islander territory and immediately begin the battle.",
                        "adventurer_request", 1, 750L, "Request one from the Adventurer's Guild. You will be sent to your territory first.");
                default -> objective;
            })
            .toList();

    private AdventureGuideManager() {}

    public static void load() {
        DIR.mkdirs();
    }

    public static void handleJoin(ServerPlayer player) {
        if (player == null) return;
        PlayerData data = data(player);
        if (PlayerProfileManager.isIslander(player)) {
            Objective current = objectiveAt(player, data.index);
            TerritoryRepository.Territory territory = TerritoryRepository.cachedPersonal(player);
            if (current != null && "create_territory".equals(current.id()) && territory != null && territory.isReady()) {
                increment(player, "territory_created", 1);
                data = data(player);
            }
        }
        if (data.bossBarVisible) {
            updateBossBar(player);
        }
    }

    public static void preload(UUID profileId) {
        if (profileId == null) return;
        DATA.computeIfAbsent(profileId, AdventureGuideManager::loadProfile);
    }

    public static void unloadPlayer(ServerPlayer player) {
        if (player == null) return;
        save(player);
        removeBossBar(player);
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        DATA.remove(profileId);
        DIRTY.remove(profileId);
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        tickCounter++;
        if (tickCounter % 20 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (PlayerProfileManager.hasActiveProfile(player) && !PlayerProfileManager.isInMainMenu(player)) {
                    updateBossBar(player);
                } else {
                    removeBossBar(player);
                }
            }
        }
        if (tickCounter % 200 == 0) {
            saveAll();
        }
    }

    public static void saveAll() {
        for (UUID profileId : new ArrayList<>(DIRTY)) {
            PlayerData data = DATA.get(profileId);
            if (data != null) save(profileId, data);
        }
    }

    public static boolean handleAdventurerNpcOpen(ServerPlayer player) {
        if (player == null) return false;
        Objective current = currentObjective(player);
        if (current != null && "talk_to_adventurer".equals(current.id())) {
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal("§6Adventurer's Guild Representative: §fWelcome to Cobble Champs."));
            player.sendSystemMessage(Component.literal("§7The Adventure Guide will walk you through gyms, professions, contracts, expeditions, PvP quests, the Battle Tower, shops, claims, guilds, cosmetics, bosses, and rewards."));
            player.sendSystemMessage(Component.literal("§7Your current Guide objective is always shown on the boss bar unless you turn it off in §e/menu settings§7."));
            increment(player, "talk_to_adventurer", 1);
            AdventureGuideMenu.open(player);
            return true;
        }
        return false;
    }

    public static boolean isLockedUntilTalk(ServerPlayer player) {
        if (player == null || !PlayerProfileManager.hasActiveProfile(player)) return false;
        Objective current = currentObjective(player);
        return current != null && "talk_to_adventurer".equals(current.id());
    }

    public static void denyUntilTalk(ServerPlayer player) {
        if (player == null) return;
        player.sendSystemMessage(Component.literal("§eTalk to the Adventurer's Guild Representative first. §7They are inside the big utility building to the south."));
    }

    public static void increment(ServerPlayer player, String systemKey, int amount) {
        if (player == null || systemKey == null || amount <= 0) return;
        PlayerData data = data(player);
        Objective current = objectiveAt(player, data.index);
        if (current == null || !systemKey.equalsIgnoreCase(current.systemKey())) return;
        data.progress = Math.min(current.target(), data.progress + amount);
        markDirty(player);
        if (data.progress >= current.target()) {
            completeCurrent(player, data, current);
        } else {
            updateBossBar(player);
        }
    }

    public static void completeObjectiveIfCurrent(ServerPlayer player, String objectiveId) {
        if (player == null || objectiveId == null) return;
        Objective current = currentObjective(player);
        if (current != null && objectiveId.equalsIgnoreCase(current.id())) {
            increment(player, current.systemKey(), current.target());
        }
    }

    public static void markIntroECraft(ServerPlayer player, String rarity, String toolType) {
        if (player == null || rarity == null || toolType == null) return;
        PlayerData data = data(player);
        Objective current = objectiveAt(player, data.index);
        if (current == null || !"craft_e_tool_armor_trinket".equals(current.id())) return;
        if (!"E".equalsIgnoreCase(rarity.trim())) return;

        String normalized = toolType.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        String flag = switch (normalized) {
            case "pickaxe", "axe", "hoe", "shovel", "sword" -> "e_tool";
            case "helmet", "chestplate", "leggings", "boots" -> "e_armor";
            case "magnet", "shiny_charm", "profession_xp_gem", "pokemon_xp_egg", "friendship_charm", "level_charm", "rare_pokemon_charm", "chunky_brick", "trinket_pouch" -> "e_trinket";
            default -> null;
        };
        if (flag == null) return;
        if (data.guideFlags == null) data.guideFlags = new HashSet<>();
        if (!data.guideFlags.add(flag)) return;
        data.progress = Math.min(current.target(), data.guideFlags.size());
        markDirty(player);
        if (data.progress >= current.target()) {
            completeCurrent(player, data, current);
        } else {
            updateBossBar(player);
            player.sendSystemMessage(Component.literal("§aAdventure Guide progress: §f" + current.title() + " §7(" + data.progress + "/" + current.target() + ")"));
        }
    }

    public static Objective currentObjective(ServerPlayer player) {
        return player == null ? null : objectiveAt(player, data(player).index);
    }

    public static int currentProgress(ServerPlayer player) {
        return player == null ? 0 : data(player).progress;
    }

    public static boolean isBossBarVisible(ServerPlayer player) {
        return player != null && data(player).bossBarVisible;
    }

    public static void toggleBossBar(ServerPlayer player) {
        if (player == null) return;
        PlayerData data = data(player);
        data.bossBarVisible = !data.bossBarVisible;
        markDirty(player);
        if (data.bossBarVisible) {
            updateBossBar(player);
            player.sendSystemMessage(Component.literal("§aAdventure Guide boss bar enabled."));
        } else {
            removeBossBar(player);
            player.sendSystemMessage(Component.literal("§eAdventure Guide boss bar disabled."));
        }
    }

    /** Disables the guide boss bar and persists the preference. */
    public static void disableBossBar(ServerPlayer player, boolean notify) {
        if (player == null) return;
        PlayerData data = data(player);
        data.bossBarVisible = false;
        markDirty(player);
        save(player);
        removeBossBar(player);
        if (notify) {
            player.sendSystemMessage(Component.literal("§eAdventure Guide boss bar disabled."));
        }
    }

    public static List<ObjectiveStatus> statuses(ServerPlayer player) {
        PlayerData data = data(player);
        List<ObjectiveStatus> list = new ArrayList<>();
        List<Objective> objectives = objectivesFor(player);
        for (int i = 0; i < objectives.size(); i++) {
            Objective objective = objectives.get(i);
            Status status = i < data.index ? Status.COMPLETE : (i == data.index ? Status.CURRENT : Status.LOCKED);
            int progress = i == data.index ? data.progress : (i < data.index ? objective.target() : 0);
            list.add(new ObjectiveStatus(objective, status, progress));
        }
        return list;
    }

    private static void completeCurrent(ServerPlayer player, PlayerData data, Objective objective) {
        data.completed.add(objective.id());
        if (objective.rewardCredits() > 0) {
            EconomyManager.depositAsync(player, EconomyManager.wholeCreditsToCents(objective.rewardCredits()), "Adventure Guide: " + objective.id());
        }
        player.sendSystemMessage(Component.literal("§aAdventure Guide complete: §f" + objective.title()));
        if (objective.rewardCredits() > 0) {
            player.sendSystemMessage(Component.literal("§7Reward: §6" + objective.rewardCredits() + " credits"));
        }
        if ("collect_copper_chunks".equals(objective.id())) {
            ProfessionManager.addFragments(player, "E", 48);
            ProfessionManager.savePlayer(player);
            player.sendSystemMessage(Component.literal("§aTraining reward: §648 E Rank Essence §7(enough to craft the guide tool, armor piece, and trinket)."));
        }
        data.guideFlags.clear();
        if (data.index < objectivesFor(player).size() - 1) {
            data.index++;
            data.progress = 0;
            Objective next = objectiveAt(player, data.index);
            if (next != null) {
                player.sendSystemMessage(Component.literal("§eNext Objective: §f" + next.title()));
                player.sendSystemMessage(Component.literal("§7" + next.hint()));
                if ("complete".equals(next.id())) {
                    increment(player, "complete", 1);
                    return;
                }
            }
        }
        markDirty(player);
        updateBossBar(player);
    }

    private static void updateBossBar(ServerPlayer player) {
        if (player == null) return;
        PlayerData data = data(player);
        Objective current = objectiveAt(player, data.index);
        if (current == null || !data.bossBarVisible) {
            removeBossBar(player);
            return;
        }

        CustomBossEvent bar = BARS.get(player.getUUID());
        if (bar == null) {
            ResourceLocation id = new ResourceLocation("champutils", "adventure_guide_" + player.getUUID().toString().replace("-", ""));
            bar = new CustomBossEvent(id, Component.literal("Adventure Guide"));
            bar.setColor(BossEvent.BossBarColor.BLUE);
            bar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
            bar.addPlayer(player);
            BARS.put(player.getUUID(), bar);
        }
        bar.setName(Component.literal("§bAdventure Guide §7- §f" + current.title() + " §7(" + Math.min(data.progress, current.target()) + "/" + current.target() + ")"));
        bar.setProgress(Math.max(0f, Math.min(1f, current.target() <= 0 ? 1f : data.progress / (float) current.target())));
    }

    private static void removeBossBar(ServerPlayer player) {
        if (player == null) return;
        CustomBossEvent bar = BARS.remove(player.getUUID());
        if (bar != null) bar.removeAllPlayers();
    }

    private static PlayerData data(ServerPlayer player) {
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        return DATA.computeIfAbsent(profileId, AdventureGuideManager::loadProfile);
    }

    private static PlayerData loadProfile(UUID profileId) {
        PlayerData local = null;
        File file = file(profileId);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                local = GSON.fromJson(reader, PlayerData.class);
            } catch (Exception ignored) {
            }
        }
        if (local == null) local = new PlayerData();
        PlayerData shared = SharedJsonStateRepository.loadProfile(profileId, STATE_KEY, PlayerData.class, local);
        shared.sanitize();
        return shared;
    }

    private static void markDirty(ServerPlayer player) {
        if (player != null) DIRTY.add(PlayerProfileManager.activeProfileId(player));
    }

    private static void save(ServerPlayer player) {
        if (player == null) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        PlayerData data = DATA.get(profileId);
        if (data != null) save(profileId, data);
    }

    private static void save(UUID profileId, PlayerData data) {
        try {
            DIR.mkdirs();
            try (FileWriter writer = new FileWriter(file(profileId))) {
                GSON.toJson(data, writer);
            }
            SharedJsonStateRepository.saveProfile(profileId, STATE_KEY, data);
            DIRTY.remove(profileId);
        } catch (Exception ignored) {
        }
    }

    private static File file(UUID profileId) {
        return new File(DIR, profileId + ".json");
    }

    private static List<Objective> objectivesFor(ServerPlayer player) {
        return player != null && PlayerProfileManager.isIslander(player) ? ISLANDER_OBJECTIVES : STANDARD_OBJECTIVES;
    }

    private static Objective objectiveAt(ServerPlayer player, int index) {
        List<Objective> objectives = objectivesFor(player);
        if (index < 0 || index >= objectives.size()) return null;
        return objectives.get(index);
    }

    private static Objective objective(String id, String title, String description, String systemKey, int target, long rewardCredits, String hint) {
        return new Objective(id, title, description, systemKey, target, rewardCredits, hint);
    }

    public record Objective(String id, String title, String description, String systemKey, int target, long rewardCredits, String hint) {}
    public record ObjectiveStatus(Objective objective, Status status, int progress) {}
    public enum Status { COMPLETE, CURRENT, LOCKED }

    private static final class PlayerData {
        int index = 0;
        int progress = 0;
        boolean bossBarVisible = true;
        Set<String> completed = new HashSet<>();
        Set<String> guideFlags = new HashSet<>();

        void sanitize() {
            if (completed == null) completed = new HashSet<>();
            if (guideFlags == null) guideFlags = new HashSet<>();
            if (index < 0) index = 0;
            if (index >= STANDARD_OBJECTIVES.size()) index = STANDARD_OBJECTIVES.size() - 1;
            Objective current = STANDARD_OBJECTIVES.get(index);
            if (current != null) progress = Math.max(0, Math.min(progress, current.target()));
        }
    }
}
