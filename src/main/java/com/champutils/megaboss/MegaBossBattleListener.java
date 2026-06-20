package com.champutils.megaboss;

import com.champutils.battle.BattleContextManager;
import com.champutils.profession.ProfessionFragmentManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.profession.ProfessionType;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class MegaBossBattleListener {
    private static final Map<UUID, UUID> ACTIVE_PLAYER_BOSS = new ConcurrentHashMap<>();
    private static final Map<String, BossRewardSnapshot> ACTIVE_BATTLE_BOSS = new ConcurrentHashMap<>();
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

    private static void handleBattleStarting(BattleStartedEvent event) {
        Entity boss = findMegaBossEntity(event.getBattle().getActors());
        if (boss == null) return;
        String battleId = readBattleId(event.getBattle());
        ACTIVE_BATTLE_BOSS.put(battleId, BossRewardSnapshot.from(boss));
        scaleBossForBattleStart(boss, event.getBattle().getActors());
    }

    private static void handleBattleStarted(BattleStartedEvent event) {
        Entity boss = findMegaBossEntity(event.getBattle().getActors());
        if (boss == null) {
            return;
        }
        scaleBossForBattleStart(boss, event.getBattle().getActors());
        ACTIVE_BATTLE_BOSS.put(readBattleId(event.getBattle()), BossRewardSnapshot.from(boss));
        for (Object actor : event.getBattle().getActors()) {
            if (actor instanceof PlayerBattleActor playerActor) {
                if (!(playerActor.getEntity() instanceof ServerPlayer player)) continue;
                ACTIVE_PLAYER_BOSS.put(player.getUUID(), boss.getUUID());
                BattleContextManager.setContext(player.getUUID(), BattleContextManager.BattleType.MEGA_BOSS);
                player.sendSystemMessage(Component.literal("§5§lMega Boss Challenge! §cThis Pokémon cannot be caught. Defeat it for fragments, extra Battling XP, and a chance at its Mega Stone."));
            }
        }
    }

    private static void handleVictory(BattleVictoryEvent event) {
        String battleId = readBattleId(event.getBattle());
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

        if (winners.isEmpty() || defeatedBoss == null) {
            for (ServerPlayer player : allPlayers) {
                player.sendSystemMessage(Component.literal("§cMega Boss battle ended. No rewards were granted."));
            }
            Entity bossToRemove = defeatedBoss != null ? defeatedBoss : survivingBoss;
            if (bossToRemove == null) bossToRemove = findEntityByUuid(allPlayers, snapshot.bossUuid());
            if (bossToRemove != null) MegaBossManager.discardBoss(bossToRemove);
            return;
        }

        for (ServerPlayer winner : winners) giveRewards(winner, snapshot.bossUuid(), snapshot.rarity(), snapshot.stones());
        MegaBossManager.discardBoss(defeatedBoss);
    }

    private static void giveRewards(ServerPlayer player, UUID bossUuid, String rarity, List<String> stoneItems) {
        int battlingLevel = Math.max(1, ProfessionManager.getLevel(player, ProfessionType.BATTLING));
        int xp = Math.max(0, MegaBossConfig.DATA.battlingXpReward);
        if (xp > 0) ProfessionManager.addXp(player, ProfessionType.BATTLING, xp);

        int min = Math.max(0, MegaBossConfig.DATA.fragmentMin);
        int max = Math.max(min, MegaBossConfig.DATA.fragmentMax);
        int fragments = max <= 0 ? 0 : min + ThreadLocalRandom.current().nextInt(max - min + 1);
        if (fragments > 0) ProfessionFragmentManager.giveFragments(player, rarity, fragments);

        double chance = megaStoneChance(battlingLevel);
        String stoneItem = pickStone(stoneItems);
        double roll = ThreadLocalRandom.current().nextDouble();
        boolean gotStone = stoneItem != null && !stoneItem.isBlank() && roll < chance;
        if (gotStone) giveItem(player, stoneItem, 1);

        player.sendSystemMessage(Component.literal("§dMega Boss defeated! §b+" + xp + " Battling XP §7| §6" + fragments + " " + pretty(rarity) + " Fragments §7| §eMega Stone Chance: " + percent(chance) + (gotStone ? " §aSUCCESS!" : " §cNo drop.")));
        if (gotStone && MegaBossConfig.DATA.broadcastMegaStoneDrops && player.getServer() != null) {
            player.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§6§lMega Stone Drop! §e" + player.getName().getString() + " obtained §b" + stoneItem + " §efrom a Mega Boss!"), false);
        }
    }

    private static double megaStoneChance(int battlingLevel) {
        return Math.max(0.0D, Math.min(1.0D, MegaBossConfig.DATA.megaStoneDropChance));
    }

    private static String pickStone(List<String> stoneItems) {
        if (stoneItems == null || stoneItems.isEmpty()) return "";
        List<String> valid = new ArrayList<>();
        for (String item : stoneItems) if (item != null && !item.isBlank() && !valid.contains(item.trim())) valid.add(item.trim());
        if (valid.isEmpty()) return "";
        return valid.get(ThreadLocalRandom.current().nextInt(valid.size()));
    }

    private static void giveItem(ServerPlayer player, String itemId, int amount) {
        if (player == null || player.getServer() == null || itemId == null || itemId.isBlank() || amount <= 0) return;
        player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack().withSuppressedOutput(), "give " + player.getName().getString() + " " + itemId + " " + amount);
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
