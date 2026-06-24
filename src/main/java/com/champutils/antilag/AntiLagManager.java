package com.champutils.antilag;

import com.champutils.permissions.LuckPermsHook;
import com.champutils.moderation.ModerationManager;
import com.champutils.time.DailyResetManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.vehicle.AbstractMinecart;

import java.io.OutputStream;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class AntiLagManager {
    private static int ticksUntilScan=20*60;
    private static int ticksUntilCleanup=-1;
    private static int ticksUntilOverloadedChunkCleanup=20*300;
    private static boolean cleanupWarningSent=false;
    private static int lastCleanupCountdownSecond=-1;
    private static long lastTickNanos=System.nanoTime();
    private static final ArrayDeque<Long> tickHistoryMs=new ArrayDeque<>(); private static final Map<UUID,ThrowWindow> snowballThrows=new HashMap<>(); private static final Map<UUID,Violation> violations=new HashMap<>(); private static long resetKey= DailyResetManager.currentResetKeyMillis();
    private static final String[] PROTECTED_TAG_MARKERS={
        "champutils_mega_boss","champutils_guild_boss","champutils_world_boss","champutils_special_spawn",
        "champutils_roaming_trainer","champutils_npc","boss","special","legendary","mythical","ultra_beast",
        "ultrabeast","roaming","event","titan","totem","raid","raid_boss","mega_boss","world_boss","guild_boss"
    };
    private AntiLagManager(){}
    public static void tick(MinecraftServer server){
        recordTick();

        long key=DailyResetManager.currentResetKeyMillis();
        if(key!=resetKey){
            resetKey=key;
            violations.clear();
            snowballThrows.clear();
            alertAdmins(server,"§a[AntiLag] Daily lag violation records wiped at "+DailyResetManager.formatResetTime()+".");
        }

        if(!AntiLagConfig.DATA.enabled) return;

        if(--ticksUntilScan<=0){
            // This is a full loaded-entity scan. Keep it infrequent; running it every 1-2 seconds causes lag.
            ticksUntilScan=Math.max(20*60,AntiLagConfig.DATA.scanIntervalSeconds*20);
            if(AntiLagConfig.DATA.detectLagMachines) scanForLagMachines(server);
        }

        // Emergency guard for chunks that start writing external entity files every autosave.
        // This runs much more often than the normal cleanup, but only touches overloaded chunks
        // and only removes safe dropped items / ordinary natural wild Pokemon.
        if(--ticksUntilOverloadedChunkCleanup<=0){
            ticksUntilOverloadedChunkCleanup=20*300;
            // This groups loaded entities by chunk, so only run it when the server is actually showing lag pressure.
            if(hasTpsSpike() || avgMs() >= Math.max(75.0D, AntiLagConfig.DATA.tpsSpikeMsThreshold * 0.75D)) cleanupOverloadedChunks(server);
        }

        // Destructive entity cleanup must never run from the frequent lag-machine scan path.
        // It is intentionally isolated behind the long cleanup scheduler. Default is 15 minutes = 18,000 ticks.
        if(ticksUntilCleanup<0){
            ticksUntilCleanup=cleanupIntervalTicks();
        }
        if(!AntiLagConfig.DATA.entityCleanupEnabled){
            cleanupWarningSent=false;
            lastCleanupCountdownSecond=-1;
            ticksUntilCleanup=cleanupIntervalTicks();
            return;
        }

        int warningTicks=Math.max(0,AntiLagConfig.DATA.cleanupWarningSeconds)*20;
        if(!cleanupWarningSent && warningTicks>0 && ticksUntilCleanup<=warningTicks){
            cleanupWarningSent=true;
            warnCleanup(server);
        }

        int countdownSecond=(int)Math.ceil(Math.max(0,ticksUntilCleanup)/20.0D);
        if(countdownSecond>=1 && countdownSecond<=3 && countdownSecond!=lastCleanupCountdownSecond){
            lastCleanupCountdownSecond=countdownSecond;
            server.getPlayerList().broadcastSystemMessage(Component.literal("§c[Cleanup] §eClearing lag entities in §c"+countdownSecond+"§e..."), false);
        }

        if(--ticksUntilCleanup<=0){
            ticksUntilCleanup=cleanupIntervalTicks();
            cleanupWarningSent=false;
            lastCleanupCountdownSecond=-1;
            cleanupEntities(server,true);
        }
    }


    private static void cleanupOverloadedChunks(MinecraftServer server){
        WildPokemonCleanupManager.Options options = new WildPokemonCleanupManager.Options();
        options.clearDroppedItems = AntiLagConfig.DATA.cleanupDroppedItems;
        options.clearWildPokemon = AntiLagConfig.DATA.cleanupWildPokemon;
        options.protectCustomNames = AntiLagConfig.DATA.protectPokemonWithCustomName;
        options.minWildPokemonAgeTicks = 20 * 30;
        options.maxRemovals = Math.max(1000, AntiLagConfig.DATA.maxRemovalsPerScan);
        options.disabledDimensions = AntiLagConfig.DATA.disabledDimensions == null ? Set.of() : new HashSet<>(AntiLagConfig.DATA.disabledDimensions);
        WildPokemonCleanupManager.CleanupResult result = WildPokemonCleanupManager.cleanupOverloadedChunks(server, options, 300);
        if(result.totalRemoved()>0){
            String summary = "Emergency overloaded-chunk cleanup removed " + result.wildPokemon + " wild Pokémon and " + result.droppedItems + " dropped items";
            System.out.println("[ChampUtils] " + summary + ".");
            alertAdmins(server,"§6[AntiLag] §e" + summary + ".");
        }
    }

    private static int cleanupIntervalTicks(){
        return Math.max(18000, AntiLagConfig.DATA.cleanupIntervalMinutes*60*20);
    }
    private static void warnCleanup(MinecraftServer server){ String msg="§6[Cleanup] §eDropped items and natural wild Pokémon will be cleared in §c"+AntiLagConfig.DATA.cleanupWarningSeconds+" seconds§e. Pokémon currently in battle/capture are protected."; server.getPlayerList().broadcastSystemMessage(Component.literal(msg), false); }
    private static void recordTick(){ long now=System.nanoTime(); long ms=(now-lastTickNanos)/1_000_000L; lastTickNanos=now; tickHistoryMs.addLast(ms); while(tickHistoryMs.size()>240) tickHistoryMs.removeFirst(); }
    private static boolean hasTpsSpike(){ return tickHistoryMs.stream().anyMatch(v->v>=AntiLagConfig.DATA.tpsSpikeMsThreshold); }
    private static double avgMs(){ return tickHistoryMs.stream().mapToLong(Long::longValue).average().orElse(50.0); }

    public static CleanupResult cleanupEntities(MinecraftServer server, boolean notifyAdmins){
        WildPokemonCleanupManager.Options options = new WildPokemonCleanupManager.Options();
        options.clearDroppedItems = AntiLagConfig.DATA.cleanupDroppedItems;
        options.clearWildPokemon = AntiLagConfig.DATA.cleanupWildPokemon;
        options.protectCustomNames = AntiLagConfig.DATA.protectPokemonWithCustomName;
        options.minWildPokemonAgeTicks = 20 * 60;
        options.maxRemovals = Math.max(1, AntiLagConfig.DATA.maxRemovalsPerScan);
        options.disabledDimensions = AntiLagConfig.DATA.disabledDimensions == null ? Set.of() : new HashSet<>(AntiLagConfig.DATA.disabledDimensions);

        WildPokemonCleanupManager.CleanupResult cleaned = WildPokemonCleanupManager.cleanup(server, options);
        CleanupResult result = new CleanupResult();
        result.droppedItems = cleaned.droppedItems;
        result.wildPokemon = cleaned.wildPokemon;

        if(notifyAdmins){
            String summary = "Cleared " + result.wildPokemon + " wild Pokémon and " + result.droppedItems + " dropped items across " + server.getAllLevels().spliterator().getExactSizeIfKnown() + " worlds";
            System.out.println("[ChampUtils] " + summary + " (checkedWild=" + cleaned.checkedWildPokemon + ", protectedWild=" + cleaned.protectedWildPokemon + ", reasons=" + cleaned.protectedReasonSummary() + ")");
            if(result.totalRemoved()>0) alertAdmins(server,"§7" + summary + ".");
        }
        return result;
    }
    private static void scanForLagMachines(MinecraftServer server){ expireSnowballWindows(); int punished=0; for(ServerLevel level:server.getAllLevels()){ if(isDisabled(level)) continue; List<Entity> minecarts=new ArrayList<>(), snowballs=new ArrayList<>(), generic=new ArrayList<>(); for(Entity e:level.getAllEntities()){ if(isWhitelistedArea(level,e)) continue; if(false && e instanceof AbstractMinecart) minecarts.add(e); if(e instanceof Snowball){ snowballs.add(e); trackSnowballThrow(e);} if(!(e instanceof ServerPlayer)&&!(e instanceof ItemEntity)&&!(e instanceof AbstractMinecart)&&!(e instanceof net.minecraft.world.entity.AgeableMob)&&e.getType()!=net.minecraft.world.entity.EntityType.ENDERMITE&&!isPokemonEntity(e)&&!isNpcEntity(e)) generic.add(e); }
        if(AntiLagConfig.DATA.minecartDetectionEnabled) punished+=detectMinecartGrid(server,level,minecarts,punished); if(punished>=AntiLagConfig.DATA.maxPunishmentsPerScan) return;
        if(AntiLagConfig.DATA.snowballDetectionEnabled) punished+=detectProjectileVelocitySpam(server,level,snowballs,punished); if(punished>=AntiLagConfig.DATA.maxPunishmentsPerScan) return;
        if(AntiLagConfig.DATA.minecartDetectionEnabled) punished+=detectCluster(server,level,minecarts,AntiLagConfig.DATA.minecartClusterRadiusBlocks,AntiLagConfig.DATA.minecartClusterThreshold,"minecart cluster",30,punished); if(punished>=AntiLagConfig.DATA.maxPunishmentsPerScan) return;
        if(AntiLagConfig.DATA.snowballDetectionEnabled){ punished+=detectCluster(server,level,snowballs,AntiLagConfig.DATA.snowballClusterRadiusBlocks,AntiLagConfig.DATA.snowballClusterThreshold,"snowball projectile cluster",25,punished); punished+=detectSnowballThrowSpam(server,punished);} if(punished>=AntiLagConfig.DATA.maxPunishmentsPerScan) return;
        if(AntiLagConfig.DATA.genericEntityClusterDetectionEnabled) punished+=detectCluster(server,level,generic,AntiLagConfig.DATA.genericEntityClusterRadiusBlocks,AntiLagConfig.DATA.genericEntityClusterThreshold,"generic entity cluster",20,punished); } }

    private static int detectMinecartGrid(MinecraftServer server, ServerLevel level, List<Entity> minecarts, int current){
        if(minecarts.size()<4||current>=AntiLagConfig.DATA.maxPunishmentsPerScan) return 0;
        Map<String,List<Entity>> byChunk=new HashMap<>();
        for(Entity e:minecarts){ String key=(e.blockPosition().getX()>>4)+":"+(e.blockPosition().getZ()>>4); byChunk.computeIfAbsent(key,k->new ArrayList<>()).add(e); }
        int punished=0;
        for(List<Entity> group:byChunk.values()){
            if(group.size()<6) continue;
            ServerPlayer suspect=nearestPlayer(level,group.get(0),Math.max(32,AntiLagConfig.DATA.minecartPlayerAttributionRadiusBlocks));
            String loc=level.dimension().location()+" "+group.get(0).blockPosition().toShortString();
            alertAdmins(server,"§c[AntiLag] Minecart lag machine pattern: §e"+group.size()+" minecarts in one chunk near §f"+loc+(suspect==null?"":" suspect=§f"+suspect.getGameProfile().getName()));
            if(AntiLagConfig.DATA.removeDetectedLagMachineEntities) removeEntities(group);
            if(suspect!=null && addViolation(suspect,45,"minecart lag machine chunk density")) punished++;
            if(current+punished>=AntiLagConfig.DATA.maxPunishmentsPerScan) break;
        }
        return punished;
    }
    private static int detectProjectileVelocitySpam(MinecraftServer server, ServerLevel level, List<Entity> snowballs, int current){
        if(snowballs.size()<10||current>=AntiLagConfig.DATA.maxPunishmentsPerScan) return 0;
        Map<UUID,List<Entity>> byOwner=new HashMap<>();
        for(Entity e:snowballs){ if(e instanceof Projectile p && p.getOwner() instanceof ServerPlayer sp) byOwner.computeIfAbsent(sp.getUUID(),id->new ArrayList<>()).add(e); }
        int punished=0;
        for(Map.Entry<UUID,List<Entity>> entry:byOwner.entrySet()){
            if(entry.getValue().size()<10) continue;
            ServerPlayer suspect=server.getPlayerList().getPlayer(entry.getKey());
            if(suspect==null) continue;
            alertAdmins(server,"§c[AntiLag] Snowball lag machine pattern: §f"+suspect.getGameProfile().getName()+" §7has §e"+entry.getValue().size()+"§7 live snowballs loaded.");
            if(AntiLagConfig.DATA.removeDetectedLagMachineEntities) removeEntities(entry.getValue());
            if(addViolation(suspect,50,"live snowball projectile spam")) punished++;
            if(current+punished>=AntiLagConfig.DATA.maxPunishmentsPerScan) break;
        }
        return punished;
    }

    private static int detectCluster(MinecraftServer server,ServerLevel level,List<Entity> entities,int radius,int threshold,String reason,int score,int current){ if(entities.size()<threshold||current>=AntiLagConfig.DATA.maxPunishmentsPerScan) return 0; double r2=radius*radius; Set<UUID> handled=new HashSet<>(); int punished=0; for(Entity c:entities){ if(handled.contains(c.getUUID())) continue; List<Entity> cluster=new ArrayList<>(); for(Entity o:entities) if(c.distanceToSqr(o)<=r2) cluster.add(o); if(cluster.size()<threshold) continue; cluster.forEach(e->handled.add(e.getUUID())); ServerPlayer suspect=nearestPlayer(level,c,AntiLagConfig.DATA.minecartPlayerAttributionRadiusBlocks); String loc=level.dimension().location()+" "+c.blockPosition().toShortString(); String msg="§c[AntiLag] Possible lag machine: §e"+reason+" §7("+cluster.size()+" entities) near §f"+loc+"§7. avgTick="+String.format(Locale.ROOT,"%.1f",avgMs())+"ms spike="+hasTpsSpike()+(suspect==null?"":" suspect=§f"+suspect.getGameProfile().getName()); alertAdmins(server,msg); ModerationManager.webhook(msg.replace('§','&')); if(AntiLagConfig.DATA.removeDetectedLagMachineEntities) removeEntities(cluster); if(suspect!=null && addViolation(suspect,score,reason)) punished++; if(current+punished>=AntiLagConfig.DATA.maxPunishmentsPerScan) break; } return punished; }
    private static int detectSnowballThrowSpam(MinecraftServer server,int current){ int punished=0; for(Map.Entry<UUID,ThrowWindow> e:new ArrayList<>(snowballThrows.entrySet())){ if(e.getValue().count<AntiLagConfig.DATA.snowballThrowThresholdPerWindow) continue; ServerPlayer p=server.getPlayerList().getPlayer(e.getKey()); if(p==null) continue; alertAdmins(server,"§c[AntiLag] Possible snowball lag machine: §f"+p.getGameProfile().getName()+" §7created §e"+e.getValue().count+"§7 snowballs in one window."); ModerationManager.webhook("AntiLag caught: player="+p.getGameProfile().getName()+" snowballs="+e.getValue().count+" actionEligible="+(AntiLagConfig.DATA.autoPunishLagMachineSuspects&&(!AntiLagConfig.DATA.requireTpsSpikeForPunishment||hasTpsSpike()))); if(addViolation(p,35,"snowball throw spam")) punished++; snowballThrows.remove(e.getKey()); if(current+punished>=AntiLagConfig.DATA.maxPunishmentsPerScan) break; } return punished; }
    private static boolean addViolation(ServerPlayer p,int score,String reason){ if(!AntiLagConfig.DATA.autoPunishLagMachineSuspects) return false; Violation v=violations.computeIfAbsent(p.getUUID(),id->new Violation()); v.score+=score; v.lastReason=reason; boolean confident=v.score>=AntiLagConfig.DATA.warnScore; if(AntiLagConfig.DATA.requireTpsSpikeForPunishment && !hasTpsSpike() && v.score<45){ p.sendSystemMessage(Component.literal("[AntiLag] Your area matched a lag pattern, but no TPS spike was detected. Staff were alerted only.").withStyle(ChatFormatting.YELLOW)); ModerationManager.systemViolation(p,"AntiLag","score="+v.score+" reason="+reason+" no TPS spike, alert only",false); return false; } if(p.hasPermissions(4)){ p.sendSystemMessage(Component.literal("[AntiLag] You matched a lag rule, but ops are exempt: "+reason).withStyle(ChatFormatting.RED)); ModerationManager.systemViolation(p,"AntiLag","score="+v.score+" reason="+reason+" op exempt",false); return false; } if(confident){ return ModerationManager.systemViolation(p,"AntiLag","lag-machine confidence score="+v.score+" reason="+reason,true); } return false; }
    private static void trackSnowballThrow(Entity e){ if(!(e instanceof Projectile p)) return; if(!(p.getOwner() instanceof ServerPlayer player)) return; long now=System.currentTimeMillis(), win=Math.max(1000L,AntiLagConfig.DATA.snowballWindowSeconds*1000L); ThrowWindow w=snowballThrows.computeIfAbsent(player.getUUID(),u->new ThrowWindow(now+win)); if(now>w.expiresAt){w.count=0;w.expiresAt=now+win;} w.count++; }
    private static void expireSnowballWindows(){ long now=System.currentTimeMillis(); snowballThrows.entrySet().removeIf(e->now>e.getValue().expiresAt); }
    private static void removeEntities(List<Entity> entities){ int removed=0,max=Math.max(1,AntiLagConfig.DATA.maxRemovalsPerScan); for(Entity e:entities){ if(removed>=max) break; if(e instanceof ServerPlayer) continue; e.discard(); removed++; } }
    private static ServerPlayer nearestPlayer(ServerLevel level,Entity e,int radius){ double r2=radius*radius,best=Double.MAX_VALUE; ServerPlayer out=null; for(ServerPlayer p:level.players()){ if(p.isSpectator()) continue; double d=p.distanceToSqr(e); if(d<=r2&&d<best){best=d;out=p;}} return out; }

    private static boolean isSafeWildPokemonToWipe(Entity e){
        return WildPokemonCleanupManager.isSafeNaturalWildPokemon(e, 20 * 60, AntiLagConfig.DATA.protectPokemonWithCustomName);
    }


    private static boolean isShinyOrSpecialPokemon(Entity e,Object pokemon){
        String entityAspects=String.valueOf(firstValue(e,"aspects","getAspects","appliedAspects","getAppliedAspects","features","getFeatures")).toLowerCase(Locale.ROOT);
        String pokemonAspects=String.valueOf(firstValue(pokemon,"aspects","getAspects","features","getFeatures","labels","getLabels")).toLowerCase(Locale.ROOT);
        String species=String.valueOf(firstValue(pokemon,"species","getSpecies")).toLowerCase(Locale.ROOT);
        String form=String.valueOf(firstValue(pokemon,"form","getForm")).toLowerCase(Locale.ROOT);
        String spawnData=String.valueOf(firstValue(e,"spawnData","getSpawnData","spawnContext","getSpawnContext","spawnDetail","getSpawnDetail")).toLowerCase(Locale.ROOT);
        String combined=entityAspects+" "+pokemonAspects+" "+species+" "+form+" "+spawnData;
        if(combined.contains("shiny")) return true;
        for(String marker:PROTECTED_TAG_MARKERS) if(combined.contains(marker)) return true;

        Object speciesObj=firstValue(pokemon,"species","getSpecies");
        if(booleanValue(speciesObj,"legendary","isLegendary","mythical","isMythical","ultraBeast","isUltraBeast")) return true;
        Object labels=firstValue(speciesObj,"labels","getLabels");
        String labelText=String.valueOf(labels).toLowerCase(Locale.ROOT);
        return labelText.contains("legendary")||labelText.contains("mythical")||labelText.contains("ultra_beast")||labelText.contains("ultrabeast");
    }

    private static boolean isOwnedOrActivePokemonEntity(Entity e,Object pokemon){
        if(hasEntityOwner(e)||hasOwnerOrStorage(pokemon)) return true;
        if(booleanValue(pokemon,"isPlayerOwned","isNPCOwned")) return true;
        Object ownerEntity=firstValue(pokemon,"getOwnerEntity","getOwnerPlayer","getOwnerNPC");
        if(ownerEntity!=null) return true;
        Object state=firstValue(pokemon,"state","getState");
        if(state!=null){
            String name=state.getClass().getName().toLowerCase(Locale.ROOT);
            String text=String.valueOf(state).toLowerCase(Locale.ROOT);
            if(name.contains("sentoutstate")||name.contains("shoulderedstate")||text.contains("sentoutstate")||text.contains("shoulderedstate")) return true;
        }
        Object tethering=firstValue(e,"tethering","getTethering");
        return tethering!=null;
    }
    private static boolean isPokemonInAnyBattle(Entity e){ if(booleanValue(e,"isBattling","isInBattle","battleId","battleIds","getBattleId","getBattleIds","getBattleIdsSnapshot")) return true; Object pokemon=firstValue(e,"pokemon","getPokemon"); if(booleanValue(pokemon,"isBattling","isInBattle","battleId","battleIds","getBattleId","getBattleIds")) return true; MinecraftServer server=e.getServer(); if(server==null) return false; for(ServerPlayer player:server.getPlayerList().getPlayers()) if(player.level()==e.level() && player.distanceToSqr(e)<4096.0D && playerIsBattlingEntity(player,e)) return true; return false; }
    private static boolean playerIsBattlingEntity(ServerPlayer player, Entity target){ Object state=invokeStatic("com.cobblemon.mod.common.util.PlayerExtensionsKt","getBattleState",player); if(state==null) state=invokeStatic("com.cobblemon.mod.common.util.PlayerExtensionsKt","battleState",player); Object battle=firstValue(state,"first","getFirst"); if(battle==null) battle=state; Object actor=invokeMethod(battle,"getActor",target); return actor!=null; }
    private static boolean hasEntityOwner(Entity e){ Object owner=firstValue(e,"ownerUUID","getOwnerUUID","owner","getOwner"); if(owner==null) return false; if(owner instanceof Optional<?> o) return o.isPresent(); String v=owner.toString(); return !v.equalsIgnoreCase("null")&&!v.equalsIgnoreCase("Optional.empty")&&!v.isBlank(); }
    private static boolean hasOwnerOrStorage(Object p){
        // Only protect Pokémon that have a real owner/storage relationship. Do not include
        // storeCoordinates/storeCoordinate here: natural wild Cobblemon entities may expose those
        // while still being safe cleanup targets. Including them caused the wild cleanup to skip all mons.
        Object owner=firstValue(p,"ownerUUID","getOwnerUUID","owner","getOwner","getOwnerPlayer","getOwnerEntity","getStoreUUID","storeUUID","getStoreUuid","storeUuid");
        if(owner==null) return false;
        if(owner instanceof Optional<?> o) return o.isPresent();
        String v=owner.toString();
        if(v.equalsIgnoreCase("null")||v.equalsIgnoreCase("Optional.empty")||v.isBlank()) return false;
        // Cobblemon can expose zero/default UUID-like values on wild Pokémon; those are not ownership.
        return !v.contains("00000000-0000-0000-0000-000000000000");
    }
    private static boolean hasProtectedTag(Entity e){ for(String tag:e.getTags()){ String l=tag.toLowerCase(Locale.ROOT); for(String m:PROTECTED_TAG_MARKERS) if(l.contains(m)) return true;} return false; }
    private static boolean isPokemonEntity(Entity e){ return e!=null&&(e.getClass().getName().equals("com.cobblemon.mod.common.entity.pokemon.PokemonEntity")||isInstanceOf(e,"com.cobblemon.mod.common.entity.pokemon.PokemonEntity")); } private static boolean isNpcEntity(Entity e){ return e!=null&&(e.getClass().getName().equals("com.cobblemon.mod.common.entity.npc.NPCEntity")||isInstanceOf(e,"com.cobblemon.mod.common.entity.npc.NPCEntity")); }
    private static boolean isInstanceOf(Object o,String className){ try{return Class.forName(className).isInstance(o);}catch(Throwable ignored){return false;} }
    private static boolean booleanValue(Object src,String...names){ for(String n:names){ Object v=firstValue(src,n); if(v instanceof Boolean b) return b; if(v instanceof Set<?> s&&!s.isEmpty()) return true; if(v instanceof Iterable<?> it&&it.iterator().hasNext()) return true; if(v instanceof UUID) return true; if(v instanceof Optional<?> o) return o.isPresent(); } return false; }
    private static boolean booleanField(Object src,String name){ try{ Field f=findField(src.getClass(),name); if(f==null)return false; f.setAccessible(true); Object v=f.get(src); return v instanceof Boolean b&&b; }catch(Throwable ignored){return false;} }
    private static Object firstValue(Object src,String...names){
        if(src==null)return null;
        for(String n:names){
            try{
                if(n.startsWith("get")||n.startsWith("is")){
                    Method m=findNoArgMethod(src.getClass(),n);
                    if(m!=null){
                        m.setAccessible(true);
                        Object v=m.invoke(src);
                        if(v!=null)return v;
                    }
                } else {
                    Field f=findField(src.getClass(),n);
                    if(f!=null){ f.setAccessible(true); Object v=f.get(src); if(v!=null)return v; }
                }
            }catch(Throwable ignored){}
        }
        return null;
    }

    private static Object invokeMethod(Object src,String name,Object...args){ if(src==null)return null; for(Method m:allMethods(src.getClass())){ if(!m.getName().equals(name)||m.getParameterCount()!=args.length) continue; try{ m.setAccessible(true); return m.invoke(src,args); }catch(Throwable ignored){} } return null; }
    private static Object invokeStatic(String className,String name,Object...args){ try{ Class<?> c=Class.forName(className); for(Method m:allMethods(c)){ if(!Modifier.isStatic(m.getModifiers())||!m.getName().equals(name)||m.getParameterCount()!=args.length) continue; try{ m.setAccessible(true); return m.invoke(null,args); }catch(Throwable ignored){} } }catch(Throwable ignored){} return null; }
    private static Method findNoArgMethod(Class<?> t,String n){ for(Method m:allMethods(t)) if(m.getName().equals(n)&&m.getParameterCount()==0) return m; return null; }
    private static List<Method> allMethods(Class<?> t){ List<Method> out=new ArrayList<>(); for(Class<?> c=t;c!=null;c=c.getSuperclass()){ try{ out.addAll(Arrays.asList(c.getDeclaredMethods())); }catch(Throwable ignored){} } try{ out.addAll(Arrays.asList(t.getMethods())); }catch(Throwable ignored){} return out; }
    private static Field findField(Class<?> t,String n){ for(Class<?> c=t;c!=null;c=c.getSuperclass()){ try{return c.getDeclaredField(n);}catch(Throwable ignored){} } return null; }
    private static boolean isDisabled(ServerLevel level){ ResourceLocation id=level.dimension().location(); return AntiLagConfig.DATA.disabledDimensions!=null&&AntiLagConfig.DATA.disabledDimensions.contains(id.toString()); }
    private static boolean isWhitelistedArea(ServerLevel level, Entity e){ if(AntiLagConfig.DATA.whitelistedAreas==null) return false; String dim=level.dimension().location().toString(); for(AntiLagConfig.Area a:AntiLagConfig.DATA.whitelistedAreas){ if(a==null||a.dimension==null||!a.dimension.equals(dim)) continue; int x=e.blockPosition().getX(),y=e.blockPosition().getY(),z=e.blockPosition().getZ(); if(x>=Math.min(a.minX,a.maxX)&&x<=Math.max(a.minX,a.maxX)&&y>=Math.min(a.minY,a.maxY)&&y<=Math.max(a.minY,a.maxY)&&z>=Math.min(a.minZ,a.maxZ)&&z<=Math.max(a.minZ,a.maxZ)) return true; } return false; }
    private static void alertAdmins(MinecraftServer server,String msg){ if(!AntiLagConfig.DATA.alertAdmins) return; Component c=Component.literal(msg); for(ServerPlayer p:server.getPlayerList().getPlayers()) if(p.hasPermissions(4)|| LuckPermsHook.hasPermission(p,AntiLagConfig.DATA.adminAlertPermission)) p.sendSystemMessage(c); System.out.println(msg.replace('§','&')); }
    private static void webhook(String text){ String hook=AntiLagConfig.DATA.discordWebhookUrl; if(hook==null||hook.isBlank()) return; new Thread(()->{ try{ HttpURLConnection con=(HttpURLConnection)new URL(hook).openConnection(); con.setRequestMethod("POST"); con.setRequestProperty("Content-Type","application/json"); con.setDoOutput(true); String json="{\"content\":\""+text.replace("\\","\\\\").replace("\"","\\\"")+"\"}"; try(OutputStream os=con.getOutputStream()){ os.write(json.getBytes(StandardCharsets.UTF_8)); } con.getInputStream().close(); }catch(Exception ignored){} },"ChampUtils-AntiLagWebhook").start(); }
    private static final class ThrowWindow{ int count=0; long expiresAt; ThrowWindow(long e){expiresAt=e;} } private static final class Violation{ int score; String lastReason; } public static final class CleanupResult{ public int droppedItems,wildPokemon; public int totalRemoved(){return droppedItems+wildPokemon;} }
}
