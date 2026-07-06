package com.champutils.gym;

import com.champutils.badge.BadgeType;
import com.champutils.database.SharedJsonStateRepository;
import com.champutils.profile.PlayerProfileManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;import java.io.FileReader;import java.io.FileWriter;import java.util.*;

public final class GymRewardClaimData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils/gym_reward_claims/profiles");
    private static final Map<UUID, Set<String>> CACHE = new HashMap<>();
    private static final String STATE_KEY = "gym_reward_claims";
    private GymRewardClaimData() {}
    public static void load(){ if(!DIR.exists()) DIR.mkdirs(); }
    private static UUID key(ServerPlayer p){ return PlayerProfileManager.activeProfileId(p); }
    private static File file(UUID id){ if(!DIR.exists()) DIR.mkdirs(); return new File(DIR, id + ".json"); }
    public static synchronized boolean isClaimed(ServerPlayer p, BadgeType badge){ return load(key(p)).contains(badge.name()); }
    public static synchronized void markClaimed(ServerPlayer p, BadgeType badge){ UUID k=key(p); Set<String> s=load(k); s.add(badge.name()); save(k,s); }
    private static Set<String> load(UUID k){
        if(CACHE.containsKey(k)) return CACHE.get(k); Set<String> s=new HashSet<>(); File f=file(k);
        if(f.exists()) try(FileReader r=new FileReader(f)){ Save save=GSON.fromJson(r,Save.class); if(save!=null&&save.claimed!=null) s.addAll(save.claimed); } catch(Exception e){e.printStackTrace();}
        Save shared=SharedJsonStateRepository.loadProfile(k,STATE_KEY,Save.class,null); if(shared!=null&&shared.claimed!=null){s.clear();s.addAll(shared.claimed);}
        CACHE.put(k,s); return s;
    }
    private static void save(UUID k, Set<String> s){ try(FileWriter w=new FileWriter(file(k))){ Save save=new Save(); save.claimed=new ArrayList<>(s); GSON.toJson(save,w); SharedJsonStateRepository.saveProfile(k,STATE_KEY,save);} catch(Exception e){e.printStackTrace();} }
    private static final class Save { List<String> claimed = new ArrayList<>(); }
}
