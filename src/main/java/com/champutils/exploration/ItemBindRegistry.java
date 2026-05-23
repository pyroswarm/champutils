package com.champutils.exploration;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemBindRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/item_npc_bindings.json");

    private static final Map<String, Binding> BINDINGS_BY_ENTITY = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> CLAIMED_BY_PLAYER = new ConcurrentHashMap<>();

    private ItemBindRegistry() {}

    public static final class Binding {
        public String bindName = "";
        public String rewardName = "";
        public String dialogue = "";
        public String entityUuid = "";
        public String world = "minecraft:overworld";

        public Binding() {}

        public Binding(String bindName, String rewardName, String dialogue, UUID entityUuid, ResourceLocation world) {
            this.bindName = normalize(bindName);
            this.rewardName = normalize(rewardName);
            this.dialogue = dialogue == null || dialogue.isBlank() ? defaultDialogue(this.rewardName) : dialogue.trim();
            this.entityUuid = entityUuid == null ? "" : entityUuid.toString();
            this.world = world == null ? "minecraft:overworld" : world.toString();
        }

        public UUID uuid() {
            try { return UUID.fromString(entityUuid); }
            catch (Exception ignored) { return null; }
        }
    }

    private static final class SaveData {
        Map<String, Binding> bindings = new LinkedHashMap<>();
        Map<String, Set<String>> claimed = new LinkedHashMap<>();
    }

    public static void load() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            BINDINGS_BY_ENTITY.clear();
            CLAIMED_BY_PLAYER.clear();

            if (!FILE.exists()) {
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                SaveData data = GSON.fromJson(reader, SaveData.class);
                if (data == null) return;

                if (data.bindings != null) {
                    for (Map.Entry<String, Binding> entry : data.bindings.entrySet()) {
                        Binding binding = entry.getValue();
                        if (binding == null || binding.entityUuid == null || binding.entityUuid.isBlank()) continue;
                        binding.bindName = normalize(binding.bindName);
                        binding.rewardName = normalize(binding.rewardName);
                        if (binding.dialogue == null || binding.dialogue.isBlank()) binding.dialogue = defaultDialogue(binding.rewardName);
                        BINDINGS_BY_ENTITY.put(entityKey(binding.world, binding.entityUuid), binding);
                    }
                }

                if (data.claimed != null) {
                    for (Map.Entry<String, Set<String>> entry : data.claimed.entrySet()) {
                        if (entry.getKey() == null || entry.getValue() == null) continue;
                        Set<String> normalized = ConcurrentHashMap.newKeySet();
                        for (String value : entry.getValue()) normalized.add(normalize(value));
                        CLAIMED_BY_PLAYER.put(entry.getKey(), normalized);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load item NPC bindings.");
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();

            SaveData data = new SaveData();
            data.bindings.putAll(BINDINGS_BY_ENTITY);
            data.claimed.putAll(CLAIMED_BY_PLAYER);

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save item NPC bindings.");
            e.printStackTrace();
        }
    }

    public static void bind(Entity entity, String bindName, String rewardName, String dialogue) {
        if (entity == null) return;
        Binding binding = new Binding(bindName, rewardName, dialogue, entity.getUUID(), entity.level().dimension().location());
        BINDINGS_BY_ENTITY.put(entityKey(binding.world, binding.entityUuid), binding);
        save();
    }

    public static boolean unbind(Entity entity) {
        if (entity == null) return false;
        Binding removed = BINDINGS_BY_ENTITY.remove(entityKey(entity));
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public static Binding getBinding(Entity entity) {
        if (entity == null) return null;
        return BINDINGS_BY_ENTITY.get(entityKey(entity));
    }

    public static boolean hasClaimed(ServerPlayer player, Binding binding) {
        if (player == null || binding == null) return false;
        return CLAIMED_BY_PLAYER.getOrDefault(player.getUUID().toString(), Collections.emptySet()).contains(normalize(binding.bindName));
    }

    public static void markClaimed(ServerPlayer player, Binding binding) {
        if (player == null || binding == null) return;
        CLAIMED_BY_PLAYER.computeIfAbsent(player.getUUID().toString(), ignored -> ConcurrentHashMap.newKeySet()).add(normalize(binding.bindName));
        save();
    }

    public static boolean resetClaim(UUID playerUuid, String bindName) {
        if (playerUuid == null) return false;
        Set<String> claimed = CLAIMED_BY_PLAYER.get(playerUuid.toString());
        if (claimed == null) return false;
        boolean removed = claimed.remove(normalize(bindName));
        if (removed) save();
        return removed;
    }

    public static Collection<Binding> allBindings() {
        return Collections.unmodifiableCollection(BINDINGS_BY_ENTITY.values());
    }

    public static boolean isDialogueOnly(String rewardName) {
        String reward = normalize(rewardName);
        return reward.isBlank() || reward.equals("none") || reward.equals("dialogue");
    }

    public static List<ItemStack> createRewardStacks(String rewardName) {
        String reward = normalize(rewardName);
        List<ItemStack> stacks = new ArrayList<>();

        switch (reward) {
            case "none", "dialogue" -> {}
            case "potion", "potions" -> stacks.add(stack("cobblemon:potion", 3));
            case "super_potion", "superpotion" -> stacks.add(stack("cobblemon:super_potion", 2));
            case "pokeball", "pokeballs", "poke_ball", "poke_balls" -> stacks.add(stack("cobblemon:poke_ball", 10));
            case "greatball", "great_ball", "greatballs" -> stacks.add(stack("cobblemon:great_ball", 5));
            case "food", "bread" -> stacks.add(stack("minecraft:bread", 12));
            case "revive", "revives" -> stacks.add(stack("cobblemon:revive", 2));
            case "full_heal", "fullheal" -> stacks.add(stack("cobblemon:full_heal", 2));
            case "antidote" -> stacks.add(stack("cobblemon:antidote", 2));
            case "paralyze_heal", "paralyzeheal" -> stacks.add(stack("cobblemon:paralyze_heal", 2));
            case "exp", "candy", "exp_candy" -> stacks.add(stack("cobblemon:exp_candy_xs", 3));
            case "apricorn", "apricorns" -> {
                stacks.add(stack("cobblemon:red_apricorn", 2));
                stacks.add(stack("cobblemon:blue_apricorn", 2));
                stacks.add(stack("cobblemon:yellow_apricorn", 2));
            }
            default -> {
                String itemId = reward.contains(":") ? reward : "minecraft:" + reward;
                ItemStack item = stack(itemId, 1);
                if (!item.isEmpty()) stacks.add(item);
            }
        }

        stacks.removeIf(ItemStack::isEmpty);
        return stacks;
    }

    public static String validRewardsText() {
        return "none, potion, super_potion, pokeballs, great_ball, food, revive, full_heal, antidote, paralyze_heal, exp_candy, apricorns, or any item id like minecraft:apple";
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    public static String defaultDialogue(String rewardName) {
        String reward = normalize(rewardName);
        if (isDialogueOnly(reward)) return "Nice finding me out here. Keep exploring spawn!";
        return "Nice finding me out here. Take this to help with your adventure!";
    }

    private static ItemStack stack(String id, int amount) {
        try {
            ResourceLocation location = ResourceLocation.parse(id);
            Item item = BuiltInRegistries.ITEM.get(location);
            if (item == null || item == Items.AIR) return ItemStack.EMPTY;
            return new ItemStack(item, Math.max(1, amount));
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static String entityKey(Entity entity) {
        return entityKey(entity.level().dimension().location().toString(), entity.getUUID().toString());
    }

    private static String entityKey(String world, String uuid) {
        return (world == null ? "minecraft:overworld" : world) + ":" + (uuid == null ? "" : uuid);
    }
}
