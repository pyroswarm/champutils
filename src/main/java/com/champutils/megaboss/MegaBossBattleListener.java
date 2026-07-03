package com.champutils.megaboss;

import com.champutils.battle.BattleContextManager;
import com.champutils.economy.EconomyManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.profession.ProfessionType;
import com.champutils.profession.WildBattleRewardManager;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class MegaBossBattleListener {
    private static final Map<UUID, UUID> ACTIVE_PLAYER_BOSS = new ConcurrentHashMap<>();
    private static final Map<String, BossRewardSnapshot> ACTIVE_BATTLE_BOSS = new ConcurrentHashMap<>();
    private static final Set<String> REWARDED_BATTLE_IDS = ConcurrentHashMap.newKeySet();
    private static final TagKey<Item> POKE_BALLS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_balls"));

    private MegaBossBattleListener() {}

    public static void register() {
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(event -> safeHandle("battle start pre", () -> handleBattleStarting((BattleStartedEvent) event)));
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(event -> safeHandle("battle start post", () -> handleBattleStarted((BattleStartedEvent) event)));
        CobblemonEvents.BATTLE_VICTORY.subscribe(event -> safeHandle("battle victory", () -> handleVictory((BattleVictoryEvent) event)));
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.pass(player.getItemInHand(hand));
            ItemStack stack = player.getItemInHand(hand);
            if (stack == null || stack.isEmpty() || !stack.is(POKE_BALLS)) return InteractionResultHolder.pass(stack);
            if (!isPlayerInMegaBossBattle(serverPlayer)) return InteractionResultHolder.pass(stack);
            serverPlayer.sendSystemMessage(Component.literal("§cMega Boss Pokémon cannot be caught."));
            return InteractionResultHolder.fail(stack);
        });
    }

    private static void safeHandle(String phase, Runnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            System.err.println("[ChampUtils] Mega boss " + phase + " handler failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            throwable.printStackTrace();
        }
    }

    public static boolean isPlayerInMegaBossBattle(ServerPlayer player) {
        return player != null && ACTIVE_PLAYER_BOSS.containsKey(player.getUUID());
    }

    /**
     * Defensive cleanup for disconnect/profile-switch paths. Cobblemon does not always
     * fire a normal victory/end event if a player leaves during a Pokémon battle, so
     * stale megaboss battle markers must be cleared outside the battle event pipeline.
     */
    public static void cleanupPlayer(ServerPlayer player) {
        if (player == null) return;
        UUID bossUuid = ACTIVE_PLAYER_BOSS.remove(player.getUUID());
        BattleContextManager.clearContext(player.getUUID());
        if (bossUuid == null) return;

        // Remove snapshots only when no online player still points at this boss.
        if (!ACTIVE_PLAYER_BOSS.containsValue(bossUuid)) {
            ACTIVE_BATTLE_BOSS.entrySet().removeIf(entry -> entry.getValue() != null && bossUuid.equals(entry.getValue().bossUuid()));
        }
    }

    private static void handleBattleStarting(BattleStartedEvent event) {
        Entity boss = findMegaBossEntity(event.getBattle().getActors());
        if (boss == null) return;
        String battleId = readBattleId(event.getBattle());
        ACTIVE_BATTLE_BOSS.put(battleId, BossRewardSnapshot.from(boss));
        registerMegaBossTrainerContext(battleId, boss, event.getBattle().getActors());
        scaleBossForBattleStart(boss, event.getBattle().getActors());
    }


    private static void registerMegaBossTrainerContext(String battleId, Entity boss, Iterable<?> actors) {
        if (boss == null) return;
        for (Object actor : actors) {
            if (!(actor instanceof PlayerBattleActor playerActor)) continue;
            if (!(playerActor.getEntity() instanceof ServerPlayer player)) continue;
            BattleContextManager.registerTrainerBattleContextForBattle(
                    battleId,
                    player.getUUID(),
                    boss.getUUID(),
                    BattleContextManager.BattleType.MEGA_BOSS,
                    "mega_boss"
            );
        }
    }

    private static void handleBattleStarted(BattleStartedEvent event) {
        Entity boss = findMegaBossEntity(event.getBattle().getActors());
        if (boss == null) {
            return;
        }
        scaleBossForBattleStart(boss, event.getBattle().getActors());
        String battleId = readBattleId(event.getBattle());
        ACTIVE_BATTLE_BOSS.put(battleId, BossRewardSnapshot.from(boss));
        registerMegaBossTrainerContext(battleId, boss, event.getBattle().getActors());
        for (Object actor : event.getBattle().getActors()) {
            if (actor instanceof PlayerBattleActor playerActor) {
                if (!(playerActor.getEntity() instanceof ServerPlayer player)) continue;
                ACTIVE_PLAYER_BOSS.put(player.getUUID(), boss.getUUID());
                BattleContextManager.setContext(player.getUUID(), BattleContextManager.BattleType.MEGA_BOSS);
                player.sendSystemMessage(Component.literal("§5§lMega Boss Challenge! §cThis Pokémon cannot be caught. Defeat it for credits, extra Battling XP, and a chance at its Mega Stone."));
            }
        }
    }

    private static void handleVictory(BattleVictoryEvent event) {
        String battleId = readBattleId(event.getBattle());
        if (!"unknown".equals(battleId) && !REWARDED_BATTLE_IDS.add(battleId)) {
            return;
        }
        Entity defeatedBoss = findMegaBossEntity(event.getLosers());
        Entity survivingBoss = findMegaBossEntity(event.getWinners());
        List<ServerPlayer> winners = playerActors(event.getWinners());
        List<ServerPlayer> allPlayers = playerActors(event.getBattle().getActors());

        BossRewardSnapshot snapshot = defeatedBoss != null ? BossRewardSnapshot.from(defeatedBoss) : ACTIVE_BATTLE_BOSS.remove(battleId);
        if (snapshot == null) {
            snapshot = snapshotFromActivePlayers(allPlayers);
        }
        if (defeatedBoss == null && snapshot != null) {
            defeatedBoss = findEntityByUuid(allPlayers, snapshot.bossUuid());
        }

        for (ServerPlayer player : allPlayers) {
            ACTIVE_PLAYER_BOSS.remove(player.getUUID());
            BattleContextManager.clearContext(player.getUUID());
        }
        ACTIVE_BATTLE_BOSS.remove(battleId);

        if (snapshot == null) {
            return;
        }

        // Cobblemon can remove/despawn the Pokémon entity before the victory event is fully
        // processed. Rewards must be based on the battle snapshot, not on the entity still
        // being present. Only withhold rewards when the boss is clearly on the winning side
        // or no player won the battle.
        if (survivingBoss != null || allPlayers.isEmpty()) {
            for (ServerPlayer player : allPlayers) {
                player.sendSystemMessage(Component.literal("§cMega Boss battle ended. No rewards were granted."));
            }
            Entity bossToRemove = defeatedBoss != null ? defeatedBoss : survivingBoss;
            if (bossToRemove == null) bossToRemove = findEntityByUuid(allPlayers, snapshot.bossUuid());
            if (bossToRemove != null) MegaBossManager.discardBoss(bossToRemove);
            return;
        }

        List<ServerPlayer> rewardTargets = winners.isEmpty() ? allPlayers : winners;
        for (ServerPlayer winner : rewardTargets) giveRewards(winner, snapshot.bossUuid(), snapshot.rarity(), snapshot.stones());
        Entity bossToRemove = defeatedBoss != null ? defeatedBoss : findEntityByUuid(allPlayers, snapshot.bossUuid());
        if (bossToRemove != null) MegaBossManager.discardBoss(bossToRemove);
    }

    private static void giveRewards(ServerPlayer player, UUID bossUuid, String rarity, List<String> stoneItems) {
        int xp = Math.max(0, MegaBossConfig.DATA.battlingXpReward);
        if (xp > 0) ProfessionManager.addXp(player, ProfessionType.BATTLING, xp);

        // Mega Bosses intentionally no longer award profession fragments.
        // Keep fragments exclusive to profession gameplay so Mega Bosses stay focused on
        // credits, Battling XP, and Mega Stone/prestige rewards.

        double chance = megaStoneChance();
        String stoneItem = pickStone(stoneItems);
        double roll = ThreadLocalRandom.current().nextDouble();
        // Exactly one independent 50% Mega Stone roll per rewarded player per megaboss victory.
        boolean gotStone = stoneItem != null && !stoneItem.isBlank() && roll < chance;
        if (gotStone) giveItem(player, stoneItem, 1);

        long creditReward = megaBossCreditReward(rarity);
        if (creditReward > 0L) {
            EconomyManager.deposit(player, EconomyManager.wholeCreditsToCents(creditReward), "Mega Boss victory " + bossUuid);
        }

        // Mega bosses use their own XP/credit rewards, but they should still trigger the
        // Battling profession's post-battle random loot roll.
        WildBattleRewardManager.rollRewardNoMoney(player);

        player.sendSystemMessage(Component.literal("§dMega Boss defeated! §a+" + EconomyManager.formatWholeCredits(creditReward) + " §7| §b+" + xp + " Battling XP §7| §eMega Stone Chance: " + percent(chance) + (gotStone ? " §aSUCCESS!" : " §cNo drop.")));
        if (gotStone && MegaBossConfig.DATA.broadcastMegaStoneDrops && player.getServer() != null) {
            com.champutils.profession.ProfessionNotificationSettings.sendBroadcast(
                    player.getServer(),
                    Component.literal("§6§lMega Stone Drop! §e" + player.getName().getString() + " obtained §b" + prettyItemName(stoneItem) + " §efrom a Mega Boss!")
            );
        }
    }

    private static long megaBossCreditReward(String rarity) {
        if (rarity == null) return 100L;
        return switch (rarity.trim().toUpperCase(Locale.ROOT)) {
            case "COMMON" -> 25L;
            case "UNCOMMON" -> 50L;
            case "RARE" -> 100L;
            case "EPIC" -> 250L;
            case "LEGEND", "LEGENDARY" -> 500L;
            case "MYTHIC", "MYTHICAL" -> 1000L;
            default -> 100L;
        };
    }

    private static double megaStoneChance() {
        // Fixed design rule: megaboss wins have a flat 50% Mega Stone chance.
        // This intentionally ignores profession level and any stale config values.
        return 0.50D;
    }

    private static String pickStone(List<String> stoneItems) {
        if (stoneItems == null || stoneItems.isEmpty()) return "";
        List<String> valid = new ArrayList<>();
        for (String item : stoneItems) {
            String normalized = MegaBossManager.normalizeGenesisItemId(item);
            if (normalized != null && !normalized.isBlank() && !valid.contains(normalized)) valid.add(normalized);
        }
        if (valid.isEmpty()) return "";
        return valid.get(ThreadLocalRandom.current().nextInt(valid.size()));
    }

    private static String prettyItemName(String itemId) {
        if (itemId == null || itemId.isBlank()) return "Mega Stone";
        String path = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        path = path.replace('_', ' ').trim();
        if (path.isBlank()) return "Mega Stone";
        StringBuilder out = new StringBuilder();
        for (String part : path.split("\\s+")) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            if (part.length() == 1) out.append(part.toUpperCase(Locale.ROOT));
            else out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static void giveItem(ServerPlayer player, String itemId, int amount) {
        if (player == null || itemId == null || itemId.isBlank() || amount <= 0) return;
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            if (item == null) return;
            ItemStack stack = new ItemStack(item, amount);
            boolean added = player.getInventory().add(stack);
            if (!added || !stack.isEmpty()) {
                player.drop(stack.copy(), false);
                stack.setCount(0);
                player.sendSystemMessage(Component.literal("§eYour inventory was full, so your Mega Stone dropped at your feet."));
            }
        } catch (Throwable t) {
            if (player.getServer() != null) {
                player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack().withSuppressedOutput(), "give " + player.getName().getString() + " " + itemId + " " + amount);
            }
        }
    }

    private static Entity findMegaBossEntity(Iterable<?> actors) {
        for (Object actor : actors) {
            for (Object battlePokemon : pokemonList(actor)) {
                Entity entity = entityFromBattlePokemon(battlePokemon);
                if (MegaBossManager.isMegaBoss(entity)) return entity;
            }
            Entity actorEntity = entityFromActor(actor);
            if (MegaBossManager.isMegaBoss(actorEntity)) return actorEntity;
        }
        return null;
    }

    private static BossRewardSnapshot snapshotFromActivePlayers(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            UUID bossUuid = ACTIVE_PLAYER_BOSS.get(player.getUUID());
            if (bossUuid == null) continue;
            Entity boss = findEntityByUuid(players, bossUuid);
            if (boss != null && MegaBossManager.isMegaBoss(boss)) return BossRewardSnapshot.from(boss);
            return new BossRewardSnapshot(bossUuid, "RARE", List.of());
        }
        return null;
    }

    private static Entity findEntityByUuid(List<ServerPlayer> players, UUID uuid) {
        if (uuid == null || players == null || players.isEmpty() || players.get(0).getServer() == null) return null;
        for (ServerLevel level : players.get(0).getServer().getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    private static String readBattleId(Object battle) {
        if (battle == null) return "unknown";
        for (String methodName : new String[]{"getBattleId", "getId"}) {
            try {
                Object value = battle.getClass().getMethod(methodName).invoke(battle);
                if (value != null) return String.valueOf(value);
            } catch (Exception ignored) {}
        }
        return "unknown";
    }

    private record BossRewardSnapshot(UUID bossUuid, String rarity, List<String> stones) {
        static BossRewardSnapshot from(Entity boss) {
            return new BossRewardSnapshot(boss.getUUID(), MegaBossManager.rarity(boss), MegaBossManager.megaStones(boss));
        }
    }

    private static void scaleBossForBattleStart(Entity boss, Iterable<?> actors) {
        if (boss == null) return;
        int targetLevel = 0;
        for (ServerPlayer player : playerActors(actors)) {
            targetLevel = Math.max(targetLevel, MegaBossManager.playerPartyHighestLevelPlusTen(player));
        }
        if (targetLevel <= 0) return;
        applyEntityPokemonLevel(boss, targetLevel);

        for (Object actor : actors) {
            Entity actorEntity = entityFromActor(actor);
            boolean actorIsBoss = actorEntity != null && actorEntity.getUUID().equals(boss.getUUID());
            for (Object battlePokemonObject : pokemonList(actor)) {
                if (!(battlePokemonObject instanceof BattlePokemon battlePokemon)) continue;
                Entity pokemonEntity = entityFromBattlePokemon(battlePokemon);
                boolean pokemonIsBoss = pokemonEntity != null && pokemonEntity.getUUID().equals(boss.getUUID());
                if (!actorIsBoss && !pokemonIsBoss) continue;
                applyBattlePokemonLevel(battlePokemon, targetLevel);
            }
        }
        boss.setCustomName(Component.literal(updateNameTagLevel(boss.getCustomName() == null ? "" : boss.getCustomName().getString(), targetLevel)));
        boss.setCustomNameVisible(true);
    }

    private static void applyEntityPokemonLevel(Entity entity, int targetLevel) {
        try {
            Object pokemonObject = invoke(entity, "getPokemon");
            if (pokemonObject instanceof Pokemon pokemon) {
                MegaBossManager.maximizePokemon(pokemon, targetLevel);
            }
        } catch (Throwable ignored) {}
    }

    private static void applyBattlePokemonLevel(BattlePokemon battlePokemon, int targetLevel) {
        try {
            Pokemon effected = battlePokemon.getEffectedPokemon();
            MegaBossManager.maximizePokemon(effected, targetLevel);
        } catch (Throwable ignored) {}
        try {
            Pokemon original = battlePokemon.getOriginalPokemon();
            MegaBossManager.maximizePokemon(original, targetLevel);
        } catch (Throwable ignored) {}
        try { battlePokemon.sendUpdate(); } catch (Throwable ignored) {}
    }

    private static String updateNameTagLevel(String current, int targetLevel) {
        if (current == null || current.isBlank()) return "§5§lMega Boss §fLv." + targetLevel;
        if (current.matches(".*Lv\\.\\d+.*")) return current.replaceAll("Lv\\.\\d+", "Lv." + targetLevel);
        if (current.matches(".*Lv\\s+\\d+.*")) return current.replaceAll("Lv\\s+\\d+", "Lv." + targetLevel);
        return current + " §fLv." + targetLevel;
    }

    private static List<ServerPlayer> playerActors(Iterable<?> actors) {
        List<ServerPlayer> players = new ArrayList<>();
        for (Object actor : actors) {
            if (actor instanceof PlayerBattleActor playerActor) {
                if (playerActor.getEntity() instanceof ServerPlayer player && !players.contains(player)) players.add(player);
            }
        }
        return players;
    }

    private static Iterable<?> pokemonList(Object actor) {
        Object value = invoke(actor, "getPokemonList");
        if (value instanceof Iterable<?> iterable) return iterable;
        return List.of();
    }

    private static Entity entityFromBattlePokemon(Object battlePokemon) {
        Object value = invoke(battlePokemon, "getEntity");
        if (value instanceof Entity e) return e;
        Object pokemon = invoke(battlePokemon, "getEffectedPokemon");
        value = invoke(pokemon, "getEntity");
        return value instanceof Entity e ? e : null;
    }

    private static Entity entityFromActor(Object actor) {
        Object value = invoke(actor, "getEntity");
        return value instanceof Entity e ? e : null;
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception ignored) { return null; }
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0D);
    }

    private static String pretty(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (v.isBlank()) return "Rare";
        StringBuilder sb = new StringBuilder();
        for (String part : v.split(" ")) {
            if (part.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.length() > 1 ? part.substring(1) : "");
        }
        return sb.toString();
    }
}
