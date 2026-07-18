package com.champutils.secret;

import com.champutils.cosmetic.TitleManager;
import com.champutils.database.DatabaseManager;
import com.champutils.worldfirst.WorldFirstManager;
import com.champutils.profile.PlayerProfileManager;
import com.google.gson.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SignBlock;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SecretManager {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE=new File("config/champutils/secrets.json");
    private static volatile Config config=new Config();
    private static final Map<UUID,Map<String,SecretRepository.Progress>> cache=new ConcurrentHashMap<>();
    private static final Map<UUID,PendingBind> pending=new ConcurrentHashMap<>();
    private static final Set<UUID> loadsInFlight=ConcurrentHashMap.newKeySet();
    private static final Set<String> advancesInFlight=ConcurrentHashMap.newKeySet();
    private static final Map<UUID,BlockPos> portalPos1=new ConcurrentHashMap<>();
    private static long tick;
    private SecretManager(){}

    public static void register(){ load(); SecretCommand.register();
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(world.isClientSide()||hand!=InteractionHand.MAIN_HAND||!(player instanceof ServerPlayer sp)||!(world instanceof ServerLevel level)) return InteractionResult.PASS;
            PendingBind pb=pending.get(sp.getUUID());
            if(pb!=null&&pb.type.equals("sign")&&level.getBlockState(hit.getBlockPos()).getBlock() instanceof SignBlock){ pending.remove(sp.getUUID()); setSignBinding(pb.secret,pb.step,WorldPos.of(level,hit.getBlockPos())); sp.sendSystemMessage(Component.literal("Secret sign bound.").withStyle(ChatFormatting.GREEN)); return InteractionResult.SUCCESS; }
            if(level.getBlockState(hit.getBlockPos()).getBlock() instanceof SignBlock){ triggerAt(sp,"SIGN",WorldPos.of(level,hit.getBlockPos()),null); }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(world.isClientSide()||hand!=InteractionHand.MAIN_HAND||!(player instanceof ServerPlayer sp)||!(world instanceof ServerLevel level)) return InteractionResult.PASS;
            PendingBind pb=pending.get(sp.getUUID());
            if(pb!=null&&pb.type.equals("npc")){ pending.remove(sp.getUUID()); setNpcBinding(pb.secret,pb.step,entity.getUUID().toString()); sp.sendSystemMessage(Component.literal("Secret NPC bound.").withStyle(ChatFormatting.GREEN)); return InteractionResult.SUCCESS; }
            triggerAt(sp,"NPC",null,entity.getUUID().toString()); return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(SecretManager::tick);
        registerCatchListener();
    }

    public static void preload(UUID profileId){
        if(profileId==null || cache.containsKey(profileId) || !loadsInFlight.add(profileId)) return;
        DatabaseManager.supplyAsync("load secret progress",c->SecretRepository.load(profileId))
                .whenComplete((m,error)->{
                    loadsInFlight.remove(profileId);
                    if(error==null && m!=null) cache.put(profileId,new ConcurrentHashMap<>(m));
                });
    }
    public static void unload(UUID profileId){ if(profileId!=null){ cache.remove(profileId); loadsInFlight.remove(profileId); advancesInFlight.removeIf(k->k.startsWith(profileId+":")); } }
    private static UUID profileId(ServerPlayer p){ return p==null?null:PlayerProfileManager.activeProfileId(p); }
    public static Collection<SecretDef> definitions(){ return Collections.unmodifiableCollection(config.secrets.values()); }
    public static Set<String> names(){ return new TreeSet<>(config.secrets.keySet()); }
    public static SecretDef get(String id){ return config.secrets.get(normalize(id)); }
    public static synchronized void create(String id){ String n=normalize(id); config.secrets.computeIfAbsent(n,k->{SecretDef d=new SecretDef();d.id=n;d.name=id;return d;});save(); }
    public static synchronized void delete(String id){ config.secrets.remove(normalize(id)); save(); }
    public static synchronized void deleteStep(String id,int step){ SecretDef d=get(id);if(d!=null){d.steps.removeIf(s->s.step==step);save();} }
    public static synchronized StepDef step(String id,int step){ SecretDef d=get(id);if(d==null)return null; return d.steps.stream().filter(s->s.step==step).findFirst().orElseGet(()->{StepDef s=new StepDef();s.step=step;d.steps.add(s);d.steps.sort(Comparator.comparingInt(a->a.step));return s;}); }
    public static synchronized void mutate(String id,int step,java.util.function.Consumer<StepDef> fn){ StepDef s=step(id,step);if(s!=null){fn.accept(s);save();} }
    public static synchronized void addReward(String id,RewardDef r){
        SecretDef d=get(id);
        if(d==null||r==null)return;
        if("ITEM".equalsIgnoreCase(r.type)){
            ResourceLocation itemId=ResourceLocation.tryParse(r.value);
            if(itemId==null||!BuiltInRegistries.ITEM.containsKey(itemId)) throw new IllegalArgumentException("Unknown item id: "+r.value);
            r.value=itemId.toString();
        }
        d.rewards.add(r);save();
    }
    public static void beginBind(ServerPlayer p,String type,String secret,int step){pending.put(p.getUUID(),new PendingBind(type,normalize(secret),step));p.sendSystemMessage(Component.literal("Right-click the "+type+" to bind it.").withStyle(ChatFormatting.YELLOW));}
    public static void portalPos1(ServerPlayer p){portalPos1.put(p.getUUID(),p.blockPosition());p.sendSystemMessage(Component.literal("Portal position 1 set.").withStyle(ChatFormatting.YELLOW));}
    public static synchronized void portalPos2(ServerPlayer p,String secret,int step){BlockPos a=portalPos1.remove(p.getUUID());if(a==null){p.sendSystemMessage(Component.literal("Set pos1 first.").withStyle(ChatFormatting.RED));return;}BlockPos b=p.blockPosition();mutate(secret,step,s->{s.trigger.type="AREA";s.trigger.world=p.serverLevel().dimension().location().toString();s.trigger.minX=Math.min(a.getX(),b.getX());s.trigger.minY=Math.min(a.getY(),b.getY());s.trigger.minZ=Math.min(a.getZ(),b.getZ());s.trigger.maxX=Math.max(a.getX(),b.getX());s.trigger.maxY=Math.max(a.getY(),b.getY());s.trigger.maxZ=Math.max(a.getZ(),b.getZ());});p.sendSystemMessage(Component.literal("Portal area saved.").withStyle(ChatFormatting.GREEN));}

    public static boolean consumeChat(ServerPlayer p,String message){boolean matched=false;for(SecretDef d:config.secrets.values()){StepDef s=nextStep(p,d);if(s==null||!"CHAT".equals(s.trigger.type))continue;if(normalizeText(message).equals(normalizeText(s.trigger.text))){matched=true;advance(p,d,s);}}return matched;}
    public static void handlePokemonKill(ServerPlayer p,String species){ if(p==null||species==null)return; for(SecretDef d:config.secrets.values()){StepDef s=nextStep(p,d);if(s==null||!"KILL_POKEMON".equals(s.trigger.type)||!normalizeText(species).equals(normalizeText(s.trigger.pokemon)))continue; UUID profile=profileId(p); if(profile==null)continue; int count=s.runtimeCounts.merge(profile,1,Integer::sum); if(count>=Math.max(1,s.trigger.amount)){s.runtimeCounts.remove(profile);advance(p,d,s);} } }
    public static void handleCatch(ServerPlayer p,Object pokemon){String species=readString(pokemon,"getSpecies","species");String gender=readString(pokemon,"getGender","gender");String nature=readString(pokemon,"getNature","nature");String ability=readString(pokemon,"getAbility","ability");for(SecretDef d:config.secrets.values()){StepDef s=nextStep(p,d);if(s==null||!"CATCH".equals(s.trigger.type))continue;if(matchesPokemon(s.trigger,species,gender,nature,ability))advance(p,d,s);} }

    private static void tick(MinecraftServer server){ if(++tick%5!=0)return; for(ServerPlayer p:server.getPlayerList().getPlayers()){ for(SecretDef d:config.secrets.values()){StepDef s=nextStep(p,d);if(s==null)continue; if("AREA".equals(s.trigger.type)&&inside(p,s.trigger)) advance(p,d,s); else if("ITEM".equals(s.trigger.type)&&countItem(p,s.trigger.item)>=Math.max(1,s.trigger.amount)) advance(p,d,s); } } }
    private static void triggerAt(ServerPlayer p,String type,WorldPos pos,String entityUuid){for(SecretDef d:config.secrets.values()){StepDef s=nextStep(p,d);if(s==null||!type.equals(s.trigger.type))continue;if(type.equals("SIGN")&&Objects.equals(s.trigger.position,pos))advance(p,d,s);else if(type.equals("NPC")&&Objects.equals(s.trigger.entityUuid,entityUuid))advance(p,d,s);}}
    private static StepDef nextStep(ServerPlayer p,SecretDef d){UUID profile=profileId(p);if(profile==null)return null;Map<String,SecretRepository.Progress> m=cache.get(profile);if(m==null){preload(profile);return null;}SecretRepository.Progress pr=m.get(d.id);if(pr!=null&&pr.completed())return null;int completed=pr==null?0:pr.completedStep();return d.steps.stream().filter(s->s.step==completed+1).findFirst().orElse(null);}
    private static void advance(ServerPlayer p,SecretDef d,StepDef s){UUID profile=profileId(p);if(profile==null)return;String flight=profile+":"+d.id+":"+s.step;if(!advancesInFlight.add(flight))return;int prev=s.step-1;boolean complete=d.steps.stream().mapToInt(x->x.step).max().orElse(s.step)==s.step;DatabaseManager.supplyAsync("advance secret "+d.id,c->SecretRepository.advance(c,profile,d.id,prev,s.step,complete)).whenComplete((ok,error)->{advancesInFlight.remove(flight);if(error!=null||!Boolean.TRUE.equals(ok))return;cache.computeIfAbsent(profile,x->new ConcurrentHashMap<>()).put(d.id,new SecretRepository.Progress(d.id,s.step,complete));p.server.execute(()->{if(profile.equals(profileId(p)))effects(p,d,s,complete);});});}
    private static void effects(ServerPlayer p,SecretDef d,StepDef s,boolean complete){if(s.text!=null&&!s.text.isBlank())p.sendSystemMessage(Component.literal(s.text.replace('&','§')));if(s.sound!=null&&!s.sound.isBlank()){ResourceLocation id=ResourceLocation.tryParse(s.sound);SoundEvent ev=id==null?null:BuiltInRegistries.SOUND_EVENT.get(id);if(ev!=null)p.playSound(ev,1f,1f);}if(s.command!=null&&!s.command.isBlank())runCommand(p,s.command);if(s.teleport!=null&&s.teleport.world!=null){ServerLevel level=p.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse(s.teleport.world)));if(level!=null)p.teleportTo(level,s.teleport.x,s.teleport.y,s.teleport.z,p.getYRot(),p.getXRot());}if(complete){for(RewardDef r:d.rewards)reward(p,r);p.sendSystemMessage(Component.literal("Secret discovered!").withStyle(ChatFormatting.GOLD));DatabaseManager.supplyAsync("count completed secrets",c->SecretRepository.completedCount(c,profileId(p))).thenAccept(count->p.server.execute(()->milestones(p,count)));}}
    private static void milestones(ServerPlayer p,int count){for(int n:new int[]{1,5,10,25,50,100})if(count>=n){TitleManager.unlock(p,"secrets_"+n);WorldFirstManager.award(p,"first_"+n+"_secrets");}}
    private static void reward(ServerPlayer p,RewardDef r){if("ITEM".equals(r.type)){ResourceLocation id=ResourceLocation.tryParse(r.value);if(id==null||!BuiltInRegistries.ITEM.containsKey(id)){System.err.println("[ChampUtils] Skipping invalid secret reward item: "+r.value);return;}Item item=BuiltInRegistries.ITEM.get(id);p.getInventory().add(new ItemStack(item,Math.max(1,r.amount)));}else if("COMMAND".equals(r.type))runCommand(p,r.value);else if("POKEMON".equals(r.type)){String spec=r.value+(blank(r.gender)?"":" gender="+r.gender)+(blank(r.nature)?"":" nature="+r.nature)+(blank(r.ability)?"":" ability="+r.ability);runCommand(p,"pokegive "+p.getGameProfile().getName()+" "+spec);}}
    private static void runCommand(ServerPlayer p,String cmd){p.server.getCommands().performPrefixedCommand(p.server.createCommandSourceStack(),cmd.replace("{player}",p.getGameProfile().getName()).replaceFirst("^/",""));}
    private static boolean inside(ServerPlayer p,Trigger t){return p.serverLevel().dimension().location().toString().equals(t.world)&&p.getX()>=t.minX&&p.getX()<=t.maxX+1&&p.getY()>=t.minY&&p.getY()<=t.maxY+1&&p.getZ()>=t.minZ&&p.getZ()<=t.maxZ+1;}
    private static int countItem(ServerPlayer p,String id){ResourceLocation rl=ResourceLocation.tryParse(id);if(rl==null)return 0;Item item=BuiltInRegistries.ITEM.get(rl);int n=0;for(ItemStack st:p.getInventory().items)if(st.is(item))n+=st.getCount();return n;}
    private static boolean matchesPokemon(Trigger t,String species,String gender,String nature,String ability){return eq(t.pokemon,species)&&(blank(t.gender)||eq(t.gender,gender))&&(blank(t.nature)||eq(t.nature,nature))&&(blank(t.ability)||eq(t.ability,ability));}
    private static boolean eq(String a,String b){return normalizeText(a).equals(normalizeText(b));}private static boolean blank(String s){return s==null||s.isBlank();}
    private static String readString(Object o,String...names){if(o==null)return"";for(String n:names)try{Method m=o.getClass().getMethod(n);Object v=m.invoke(o);if(v!=null){try{Method name=v.getClass().getMethod("getName");return String.valueOf(name.invoke(v));}catch(Exception ignored){}return String.valueOf(v);}}catch(Exception ignored){}return"";}
    private static void registerCatchListener(){try{Class<?> events=Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");Object observable=events.getField("POKEMON_CAPTURED").get(null);Method subscribe=Arrays.stream(observable.getClass().getMethods()).filter(m->m.getName().equals("subscribe")&&m.getParameterCount()==1).findFirst().orElse(null);if(subscribe==null)return;Class<?> fn=subscribe.getParameterTypes()[0];Object proxy=Proxy.newProxyInstance(fn.getClassLoader(),new Class[]{fn},(x,m,args)->{if(args!=null&&args.length>0){Object ev=args[0];ServerPlayer p=findPlayer(ev);Object pokemon=findPokemon(ev);if(p!=null&&pokemon!=null)handleCatch(p,pokemon);}return null;});subscribe.invoke(observable,proxy);}catch(Exception e){System.err.println("[ChampUtils] Secret catch listener unavailable: "+e.getMessage());}}
    private static ServerPlayer findPlayer(Object e){for(String n:new String[]{"getPlayer","player","getEntity"})try{Object v=e.getClass().getMethod(n).invoke(e);if(v instanceof ServerPlayer p)return p;}catch(Exception ignored){}return null;}
    private static Object findPokemon(Object e){for(String n:new String[]{"getPokemon","pokemon"})try{return e.getClass().getMethod(n).invoke(e);}catch(Exception ignored){}return null;}
    private static synchronized void setSignBinding(String id,int step,WorldPos pos){mutate(id,step,s->{s.trigger.type="SIGN";s.trigger.position=pos;});}
    private static synchronized void setNpcBinding(String id,int step,String uuid){mutate(id,step,s->{s.trigger.type="NPC";s.trigger.entityUuid=uuid;});}
    public static synchronized void load(){try{FILE.getParentFile().mkdirs();if(FILE.exists())try(Reader r=new FileReader(FILE)){Config c=GSON.fromJson(r,Config.class);if(c!=null)config=c;}if(config.secrets==null)config.secrets=new LinkedHashMap<>();save();}catch(Exception e){e.printStackTrace();}}
    public static synchronized void save(){try{FILE.getParentFile().mkdirs();try(Writer w=new FileWriter(FILE)){GSON.toJson(config,w);}}catch(Exception e){e.printStackTrace();}}
    private static String normalize(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","_");}private static String normalizeText(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}

    public static final class Config{public Map<String,SecretDef> secrets=new LinkedHashMap<>();}
    public static final class SecretDef{public String id="";public String name="";public List<StepDef> steps=new ArrayList<>();public List<RewardDef> rewards=new ArrayList<>();}
    public static final class StepDef{public int step;public Trigger trigger=new Trigger();public String text="";public String sound="";public String command="";public Teleport teleport;transient Map<UUID,Integer> runtimeCounts=new ConcurrentHashMap<>();}
    public static final class Trigger{public String type="NONE",text="",item="",pokemon="",gender="",nature="",ability="",world="",entityUuid="";public int amount=1,minX,minY,minZ,maxX,maxY,maxZ;public WorldPos position;}
    public static final class Teleport{public String world;public double x,y,z;}
    public static final class RewardDef{public String type="COMMAND",value="";public int amount=1;public String gender="",nature="",ability="";}
    public record WorldPos(String world,int x,int y,int z){static WorldPos of(ServerLevel l,BlockPos p){return new WorldPos(l.dimension().location().toString(),p.getX(),p.getY(),p.getZ());}}
    private record PendingBind(String type,String secret,int step){}
}
