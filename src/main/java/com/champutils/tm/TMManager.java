package com.champutils.tm;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.moves.LearnsetQuery;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.util.Unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TMManager {
    private static final Map<String, Item> REGISTERED = new HashMap<>();
    private static final Map<String, String> RARITY_BY_MOVE = new LinkedHashMap<>();
    private static final Map<String, List<String>> MOVES_BY_RARITY = new LinkedHashMap<>();
    private static final Random RANDOM = new Random();
    private static Item TM_ITEM;
    private static boolean ITEM_REGISTERED = false;
    private static boolean REGISTRY_REBUILDING = false;
    private static final String TM_MOVE_KEY = "tm_move";
    private static final String TM_USES_KEY = "tm_uses_left";
    private static final int MAX_TM_USES = 3;
    private static final Map<UUID, PendingTeach> PENDING_TEACHES = new ConcurrentHashMap<>();

    private TMManager() {}

    public static void registerTMs() {
        TMConfig.load();
        ensureTmItemRegistered();
        rebuildRegistry("mod init");
    }

    private static void ensureTmItemRegistered() {
        if (ITEM_REGISTERED) return;
        TM_ITEM = new TMItem(Items.MUSIC_DISC_CAT, new Item.Properties().stacksTo(1));
        try {
            Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("champutils", "tm"), TM_ITEM);
            ITEM_REGISTERED = true;
        } catch (Exception e) {
            // In dev/test reloads the item can already exist. Re-use it instead of leaving TM_ITEM null.
            Item existing = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("champutils", "tm"));
            if (existing instanceof TMItem) {
                TM_ITEM = existing;
                ITEM_REGISTERED = true;
            } else {
                System.err.println("[ChampUtils] Failed to register base TM item.");
                e.printStackTrace();
            }
        }
    }

    private static void ensureRegistryReady() {
        if (!REGISTERED.isEmpty()) return;
        rebuildRegistry("lazy load");
    }

    private static void rebuildRegistry(String reason) {
        if (REGISTRY_REBUILDING) return;
        REGISTRY_REBUILDING = true;
        try {
            ensureTmItemRegistered();
            REGISTERED.clear();
            RARITY_BY_MOVE.clear();
            MOVES_BY_RARITY.clear();
            for (String rarity : TMConfig.RARITIES) MOVES_BY_RARITY.put(rarity, new ArrayList<>());

            for (String moveId : officialTmMoveIds()) {
                MoveTemplate template = Moves.getByName(moveId);
                if (template == null) continue;
                REGISTERED.put(moveId, TM_ITEM);
            }

            TMConfig.pruneConfiguredRarities(REGISTERED.keySet());
            assignRarities();
            System.out.println("[ChampUtils] Registered " + REGISTERED.size() + " official Cobblemon-supported TM moves using one Polymer TM item (" + reason + ").");
        } finally {
            REGISTRY_REBUILDING = false;
        }
    }

    private static void assignRarities() {
        for (String moveId : REGISTERED.keySet()) {
            String rarity = TMConfig.configuredRarityForMove(moveId);
            if (rarity == null) rarity = automaticRarity(moveId);
            RARITY_BY_MOVE.put(moveId, rarity);
            MOVES_BY_RARITY.computeIfAbsent(rarity, k -> new ArrayList<>()).add(moveId);
        }
        for (List<String> moves : MOVES_BY_RARITY.values()) Collections.sort(moves);
    }

    private static String automaticRarity(String moveId) {
        MoveTemplate t = Moves.getByName(moveId);
        if (t == null) return "COMMON";
        int power = 0;
        try { power = (int) t.getPower(); } catch (Throwable ignored) {}
        String id = sanitizeMove(moveId);
        if (List.of("dracometeor", "trickroom", "tailwind", "terablast", "steelbeam").contains(id)) return "MYTHIC";
        if (List.of("earthquake", "thunderbolt", "icebeam", "flamethrower", "fireblast", "blizzard", "thunder", "hydropump", "surf", "stoneedge", "closecombat").contains(id)) return "LEGENDARY";
        if (List.of("swordsdance", "calmmind", "nastyplot", "dragondance", "willowisp", "toxic", "stealthrock", "spikes", "toxicspikes").contains(id)) return "EPIC";
        if (List.of("protect", "thunderwave", "roost", "substitute", "shadowball", "psychic", "darkpulse", "energyball", "aurasphere", "dazzlinggleam").contains(id) || power >= 90) return "RARE";
        if (power >= 60 || List.of("rest", "brickbreak", "lightscreen", "reflect", "raindance", "sunnyday", "sleeptalk").contains(id)) return "UNCOMMON";
        return "COMMON";
    }

    public static ItemStack createTMStack(String rawMove, int amount) {
        ensureRegistryReady();
        String moveId = sanitizeMove(rawMove);
        Item item = REGISTERED.get(moveId);
        MoveTemplate template = Moves.getByName(moveId);
        if (item == null || template == null || amount <= 0) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, Math.min(1, amount));
        CompoundTag tag = new CompoundTag();
        tag.putString(TM_MOVE_KEY, moveId);
        tag.putInt(TM_USES_KEY, MAX_TM_USES);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        applyDisplay(stack, moveId, template);
        return stack;
    }

    public static String getMoveId(ItemStack stack) {
        ensureRegistryReady();
        if (stack == null || stack.isEmpty()) return null;
        if (!(stack.getItem() instanceof TMItem)) return null;
        try {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) return null;
            String moveId = sanitizeMove(data.copyTag().getString(TM_MOVE_KEY));
            return REGISTERED.containsKey(moveId) ? moveId : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isRegisteredMove(String rawMove) {
        ensureRegistryReady();
        return REGISTERED.containsKey(sanitizeMove(rawMove));
    }

    public static Set<String> registeredMoveIds() {
        ensureRegistryReady();
        return REGISTERED.keySet();
    }

    public static String rarityForMove(String rawMove) {
        ensureRegistryReady();
        return RARITY_BY_MOVE.getOrDefault(sanitizeMove(rawMove), "COMMON");
    }

    public static List<String> movesForRarity(String rawRarity) {
        ensureRegistryReady();
        return MOVES_BY_RARITY.getOrDefault(TMConfig.normalizeRarity(rawRarity), List.of());
    }

    public static ItemStack createRandomTMStack(String rawRarity, int amount) {
        List<String> pool = movesForRarity(rawRarity);
        if (pool.isEmpty()) return ItemStack.EMPTY;
        return createTMStack(pool.get(RANDOM.nextInt(pool.size())), amount);
    }

    public static CraftResult craftRandom(ServerPlayer player, String rawRarity) {
        ensureRegistryReady();
        String rarity = TMConfig.normalizeRarity(rawRarity);
        List<String> pool = movesForRarity(rarity);
        if (pool.isEmpty()) return CraftResult.fail("No TMs are available in rarity " + prettyRarity(rarity) + ".");
        String move = pool.get(RANDOM.nextInt(pool.size()));
        Map<String, Integer> cost = randomCostForRarity(rarity);
        return craftWithCost(player, move, rarity, cost, false);
    }

    public static CraftResult craftSpecific(ServerPlayer player, String rawMove) {
        ensureRegistryReady();
        String moveId = sanitizeMove(rawMove);
        if (!REGISTERED.containsKey(moveId)) return CraftResult.fail("Unknown/unregistered TM move: " + rawMove);
        String rarity = rarityForMove(moveId);
        Map<String, Integer> cost = specificCostForRarity(rarity);
        return craftWithCost(player, moveId, rarity, cost, true);
    }

    private static CraftResult craftWithCost(ServerPlayer player, String moveId, String rarity, Map<String, Integer> cost, boolean specific) {
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            int have = com.champutils.profession.ProfessionFragmentManager.countFragments(player, entry.getKey());
            if (have < entry.getValue()) {
                return CraftResult.fail("You need " + costText(cost) + ". Missing " + (entry.getValue() - have) + " " + com.champutils.profession.ProfessionFragmentManager.formatWords(entry.getKey()) + " fragments.");
            }
        }
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            if (!com.champutils.profession.ProfessionFragmentManager.removeFragments(player, entry.getKey(), entry.getValue())) {
                return CraftResult.fail("Could not remove required fragments.");
            }
        }
        ItemStack stack = createTMStack(moveId, 1);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        return CraftResult.success(moveId, rarity, cost, specific);
    }

    public static Map<String, Integer> randomCostForRarity(String rawRarity) {
        ensureRegistryReady();
        return new LinkedHashMap<>(TMConfig.costs.getOrDefault(TMConfig.normalizeRarity(rawRarity), Map.of()));
    }

    public static Map<String, Integer> specificCostForRarity(String rawRarity) {
        return multiplyCost(randomCostForRarity(rawRarity), 2);
    }

    public static Map<String, Integer> specificCostForMove(String rawMove) {
        return specificCostForRarity(rarityForMove(rawMove));
    }

    private static Map<String, Integer> multiplyCost(Map<String, Integer> source, int multiplier) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (source == null) return out;
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            int amount = Math.max(0, entry.getValue() == null ? 0 : entry.getValue()) * Math.max(1, multiplier);
            if (amount > 0) out.put(TMConfig.normalizeRarity(entry.getKey()), amount);
        }
        return out;
    }

    public static String costText(Map<String, Integer> cost) {
        if (cost == null || cost.isEmpty()) return "no fragments";
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            parts.add(entry.getValue() + " " + com.champutils.profession.ProfessionFragmentManager.formatWords(entry.getKey()) + " Fragments");
        }
        return String.join(", ", parts);
    }

    public static String prettyRarity(String rarity) {
        return com.champutils.profession.ProfessionFragmentManager.formatWords(TMConfig.normalizeRarity(rarity));
    }

    public static Item iconForRarity(String rarity) {
        return switch (TMConfig.normalizeRarity(rarity)) {
            case "UNCOMMON" -> Items.MUSIC_DISC_BLOCKS;
            case "RARE" -> Items.MUSIC_DISC_CHIRP;
            case "EPIC" -> Items.MUSIC_DISC_MALL;
            case "LEGENDARY" -> Items.MUSIC_DISC_PIGSTEP;
            case "MYTHIC" -> Items.MUSIC_DISC_OTHERSIDE;
            default -> Items.MUSIC_DISC_CAT;
        };
    }

    public static Item iconForMove(String rawMove) {
        return iconForRarity(rarityForMove(rawMove));
    }

    public static int getUsesLeft(ItemStack stack) {
        String moveId = getMoveId(stack);
        if (moveId == null) return 0;
        try {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
            int uses = tag.contains(TM_USES_KEY) ? tag.getInt(TM_USES_KEY) : MAX_TM_USES;
            return Math.max(0, Math.min(MAX_TM_USES, uses));
        } catch (Throwable ignored) {
            return MAX_TM_USES;
        }
    }

    private static void setUsesLeft(ItemStack stack, String moveId, int usesLeft) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TM_MOVE_KEY, sanitizeMove(moveId));
        tag.putInt(TM_USES_KEY, Math.max(0, Math.min(MAX_TM_USES, usesLeft)));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        MoveTemplate template = Moves.getByName(moveId);
        if (template != null) applyDisplay(stack, moveId, template);
    }

    private static void consumeUse(ServerPlayer player, ItemStack stack, String moveId) {
        if (player == null || player.isCreative() || stack == null || stack.isEmpty()) return;
        int usesLeft = getUsesLeft(stack);
        if (usesLeft <= 1) {
            stack.shrink(1);
            if (!stack.isEmpty()) setUsesLeft(stack, moveId, MAX_TM_USES);
        } else {
            setUsesLeft(stack, moveId, usesLeft - 1);
        }
    }

    public static TeachPreview previewHeldTM(ServerPlayer player, int partySlotOneBased, int replaceSlotOneBased) {
        ItemStack stack = player.getMainHandItem();
        String moveId = getMoveId(stack);
        if (moveId == null) return TeachPreview.fail("Hold the TM in your main hand first.");
        return previewTeach(player, moveId, partySlotOneBased, replaceSlotOneBased);
    }

    public static TeachPreview previewTeach(ServerPlayer player, String moveId, int partySlotOneBased, int replaceSlotOneBased) {
        ensureRegistryReady();
        if (player == null) return TeachPreview.fail("Player not found.");
        MoveTemplate template = Moves.getByName(moveId);
        if (template == null) return TeachPreview.fail("That TM move does not exist in Cobblemon: " + moveId);
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party == null) return TeachPreview.fail("Could not access your Cobblemon party.");
        int slot = partySlotOneBased - 1;
        if (slot < 0 || slot >= party.size()) return TeachPreview.fail("Party slot must be 1-6.");
        Pokemon pokemon = party.get(slot);
        if (pokemon == null) return TeachPreview.fail("There is no Pokémon in party slot " + partySlotOneBased + ".");
        if (alreadyKnows(pokemon, moveId)) return TeachPreview.fail(displayName(pokemon) + " already knows " + prettyMove(moveId) + ".");
        if (!canLearnAsTM(pokemon, template)) return TeachPreview.fail(displayName(pokemon) + " cannot learn TM - " + prettyMove(moveId) + ".");
        List<String> currentMoves = currentMoveIds(pokemon);
        if (currentMoves.size() >= 4 && (replaceSlotOneBased < 1 || replaceSlotOneBased > 4)) {
            return TeachPreview.fail(displayName(pokemon) + " already has 4 moves. Use /tms teach <partySlot> <moveSlotToReplace> while holding the TM.");
        }
        String pokemonName = displayName(pokemon);
        String newMove = prettyMove(moveId);
        String replacedMove = null;
        String message;
        if (currentMoves.size() >= 4) {
            int replaceIndex = replaceSlotOneBased - 1;
            replacedMove = prettyMove(currentMoves.get(replaceIndex));
            message = replacedMove + " will be replaced with " + newMove + " on " + pokemonName + ". Click confirm to confirm your choice.";
        } else {
            message = pokemonName + " will learn " + newMove + ". Click confirm to confirm your choice.";
        }
        return TeachPreview.success(message, pokemonName, newMove, replacedMove);
    }

    public static UUID createPendingTeach(ServerPlayer player, int partySlotOneBased, int replaceSlotOneBased) {
        String moveId = getMoveId(player.getMainHandItem());
        if (moveId == null) return null;
        UUID token = UUID.randomUUID();
        PENDING_TEACHES.put(token, new PendingTeach(player.getUUID(), moveId, partySlotOneBased, replaceSlotOneBased, System.currentTimeMillis() + 60000L));
        return token;
    }

    public static TeachResult confirmPendingTeach(ServerPlayer player, String rawToken) {
        UUID token;
        try { token = UUID.fromString(rawToken); } catch (Throwable ignored) { return TeachResult.fail("That TM confirmation is invalid. Run /tms teach again."); }
        PendingTeach pending = PENDING_TEACHES.remove(token);
        if (pending == null || !pending.playerId().equals(player.getUUID())) return TeachResult.fail("That TM confirmation expired or does not belong to you. Run /tms teach again.");
        if (System.currentTimeMillis() > pending.expiresAt()) return TeachResult.fail("That TM confirmation expired. Run /tms teach again.");
        String heldMove = getMoveId(player.getMainHandItem());
        if (heldMove == null || !heldMove.equals(pending.moveId())) return TeachResult.fail("You must still be holding the same TM you confirmed. Run /tms teach again.");
        return teachHeldTM(player, pending.partySlot(), pending.replaceSlot(), true);
    }

    public static TeachResult teachHeldTM(ServerPlayer player, int partySlotOneBased, int replaceSlotOneBased, boolean consume) {
        ItemStack stack = player.getMainHandItem();
        String moveId = getMoveId(stack);
        if (moveId == null) return TeachResult.fail("Hold the TM in your main hand first.");
        return teachFromStack(player, stack, moveId, partySlotOneBased, replaceSlotOneBased, consume);
    }

    public static TeachResult teachFromStack(ServerPlayer player, ItemStack stack, String moveId, int partySlotOneBased, int replaceSlotOneBased, boolean consume) {
        ensureRegistryReady();
        if (player == null) return TeachResult.fail("Player not found.");
        MoveTemplate template = Moves.getByName(moveId);
        if (template == null) return TeachResult.fail("That TM move does not exist in Cobblemon: " + moveId);
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party == null) return TeachResult.fail("Could not access your Cobblemon party.");
        int slot = partySlotOneBased - 1;
        if (slot < 0 || slot >= party.size()) return TeachResult.fail("Party slot must be 1-6.");
        Pokemon pokemon = party.get(slot);
        if (pokemon == null) return TeachResult.fail("There is no Pokémon in party slot " + partySlotOneBased + ".");
        if (alreadyKnows(pokemon, moveId)) return TeachResult.fail(displayName(pokemon) + " already knows " + prettyMove(moveId) + ".");
        if (!canLearnAsTM(pokemon, template)) {
            return TeachResult.fail(displayName(pokemon) + " cannot learn TM - " + prettyMove(moveId) + ".");
        }

        try {
            List<String> currentMoves = currentMoveIds(pokemon);
            if (currentMoves.size() < 4 && replaceSlotOneBased <= 0) {
                pokemon.getMoveSet().add(template.create());
            } else {
                if (replaceSlotOneBased < 1 || replaceSlotOneBased > 4) {
                    return TeachResult.fail(displayName(pokemon) + " already has 4 moves. Use /tms teach <partySlot> <moveSlotToReplace> while holding the TM.");
                }
                int replaceIndex = replaceSlotOneBased - 1;
                while (currentMoves.size() < 4) currentMoves.add("tackle");
                currentMoves.set(replaceIndex, moveId);
                pokemon.getMoveSet().clear();
                for (String id : currentMoves) {
                    MoveTemplate t = Moves.getByName(id);
                    if (t != null) pokemon.getMoveSet().add(t.create());
                }
            }
            try { pokemon.heal(); } catch (Throwable ignored) {}
            if (consume) consumeUse(player, stack, moveId);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.55F, 1.5F);
            return TeachResult.success(displayName(pokemon) + " learned " + prettyMove(moveId) + "!");
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return TeachResult.fail("TM failed to teach. Check console for the Cobblemon move error.");
        }
    }

    public static TeachResult quickUse(ServerPlayer player, ItemStack stack) {
        if (getMoveId(stack) == null) return TeachResult.pass();
        return TeachResult.fail("Use /tms teach <partySlot> [replaceMoveSlot] while holding this TM.");
    }

    private static boolean alreadyKnows(Pokemon pokemon, String moveId) {
        return currentMoveIds(pokemon).contains(moveId);
    }

    private static boolean canLearnAsTM(Pokemon pokemon, MoveTemplate template) {
        if (pokemon == null || template == null) return false;
        try {
            return LearnsetQuery.Companion.getTM_MOVE().canLearn(template, pokemon.getForm().getMoves());
        } catch (Throwable ignored) {
            try {
                return pokemon.getForm().getMoves().getTmMoves().contains(template);
            } catch (Throwable ignoredAgain) {
                return false;
            }
        }
    }

    private static List<String> currentMoveIds(Pokemon pokemon) {
        List<String> ids = new ArrayList<>();
        try {
            for (Move move : pokemon.getMoveSet().getMoves()) {
                if (move == null) continue;
                String id = sanitizeMove(move.getName());
                if (!id.isBlank()) ids.add(id);
            }
        } catch (Throwable ignored) {}
        return ids;
    }

    private static void applyDisplay(ItemStack stack, String moveId, MoveTemplate template) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("TM - " + prettyMove(moveId)).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        List<Component> lore = new ArrayList<>();
        int usesLeft = getUsesLeft(stack);
        lore.add(Component.literal("Rarity: " + prettyRarity(rarityForMove(moveId))).withStyle(ChatFormatting.GOLD));
        lore.add(Component.literal("Teaches " + prettyMove(moveId) + ".").withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Uses Left: " + usesLeft + "/" + MAX_TM_USES).withStyle(ChatFormatting.GREEN));
        lore.add(Component.literal("Command only: /tms teach <partySlot> [replaceMoveSlot]").withStyle(ChatFormatting.DARK_GRAY));
        lore.add(Component.literal("You must click the chat confirmation before the TM is used.").withStyle(ChatFormatting.DARK_GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        // The client sees the Polymer item as a vanilla music disc for the icon.
        // This component suppresses the vanilla disc tooltip lines like "C418 - cat" and "Minecraft",
        // leaving only the custom TM name/lore above.
        stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(Math.abs(moveId.hashCode() % 900000) + 10000));
    }

    private static String displayName(Pokemon pokemon) {
        try { return pokemon.getDisplayName(false).getString(); } catch (Throwable ignored) {}
        try { return pokemon.getSpecies().getName(); } catch (Throwable ignored) {}
        return "Pokémon";
    }

    public static String sanitizeMove(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT).replace("cobblemon:", "").replaceAll("[^a-z0-9]", "");
    }

    public static String prettyMove(String moveId) {
        String clean = sanitizeMove(moveId);
        MoveTemplate template = Moves.getByName(clean);
        if (template != null) {
            try {
                String name = template.getName();
                if (name != null && !name.isBlank()) return titleCaseMoveName(name);
            } catch (Throwable ignored) {}
        }
        return titleCaseMoveName(clean);
    }

    private static String titleCaseMoveName(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String cleanId = sanitizeMove(raw);
        Map<String, String> overrides = Map.ofEntries(
                Map.entry("swordsdance", "Swords Dance"), Map.entry("calmmind", "Calm Mind"), Map.entry("nastyplot", "Nasty Plot"),
                Map.entry("dragondance", "Dragon Dance"), Map.entry("thunderwave", "Thunder Wave"), Map.entry("willowisp", "Will-O-Wisp"),
                Map.entry("stealthrock", "Stealth Rock"), Map.entry("toxicspikes", "Toxic Spikes"), Map.entry("spikes", "Spikes"),
                Map.entry("lightscreen", "Light Screen"), Map.entry("reflect", "Reflect"), Map.entry("raindance", "Rain Dance"),
                Map.entry("sunnyday", "Sunny Day"), Map.entry("sleeptalk", "Sleep Talk"), Map.entry("shadowball", "Shadow Ball"),
                Map.entry("darkpulse", "Dark Pulse"), Map.entry("energyball", "Energy Ball"), Map.entry("aurasphere", "Aura Sphere"),
                Map.entry("dazzlinggleam", "Dazzling Gleam"), Map.entry("dragonclaw", "Dragon Claw"), Map.entry("icebeam", "Ice Beam"),
                Map.entry("fireblast", "Fire Blast"), Map.entry("thunderbolt", "Thunderbolt"), Map.entry("earthquake", "Earthquake"),
                Map.entry("flamethrower", "Flamethrower"), Map.entry("hydropump", "Hydro Pump"), Map.entry("stoneedge", "Stone Edge"),
                Map.entry("closecombat", "Close Combat"), Map.entry("dracometeor", "Draco Meteor"), Map.entry("terablast", "Tera Blast"),
                Map.entry("steelbeam", "Steel Beam"), Map.entry("bodypress", "Body Press"), Map.entry("ironhead", "Iron Head"),
                Map.entry("flashcannon", "Flash Cannon"), Map.entry("playrough", "Play Rough"), Map.entry("zenheadbutt", "Zen Headbutt"),
                Map.entry("hypervoice", "Hyper Voice"), Map.entry("heatwave", "Heat Wave"), Map.entry("bravebird", "Brave Bird"),
                Map.entry("solarblade", "Solar Blade"), Map.entry("flareblitz", "Flare Blitz"), Map.entry("icespinner", "Ice Spinner"),
                Map.entry("chillingwater", "Chilling Water"), Map.entry("trailblaze", "Trailblaze"), Map.entry("pollenpuff", "Pollen Puff"),
                Map.entry("helpinghand", "Helping Hand"), Map.entry("tailwind", "Tailwind"), Map.entry("trickroom", "Trick Room"),
                Map.entry("psychicnoise", "Psychic Noise"), Map.entry("meteorbeam", "Meteor Beam"), Map.entry("scorchingsands", "Scorching Sands"),
                Map.entry("dualwingbeat", "Dual Wingbeat"), Map.entry("expandingforce", "Expanding Force"), Map.entry("tripleaxel", "Triple Axel")
        );
        if (overrides.containsKey(cleanId)) return overrides.get(cleanId);
        String spaced = raw.trim()
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
        StringBuilder out = new StringBuilder();
        for (String part : spaced.split("\\s+")) {
            if (part.isBlank()) continue;
            String lower = part.toLowerCase(Locale.ROOT);
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(lower.charAt(0))).append(lower.length() > 1 ? lower.substring(1) : "");
        }
        return out.length() == 0 ? raw : out.toString();
    }

    public static boolean isOfficialTmMoveId(String rawMove) {
        return officialTmMoveIds().contains(sanitizeMove(rawMove));
    }

    private static List<String> officialTmMoveIds() {
        // Distinct moves that have appeared as official main-series TMs/TRs/HMs through modern games.
        return new ArrayList<>(new LinkedHashSet<>(List.of(
                "megapunch","razorwind","swordsdance","whirlwind","megakick","toxic","horndrill","bodyslam","takedown","doubleedge",
                "bubblebeam","watergun","icebeam","blizzard","hyperbeam","payday","submission","counter","seismictoss","rage",
                "megadrain","solarbeam","dragonrage","thunderbolt","thunder","earthquake","fissure","dig","psychic","teleport",
                "mimic","doubleteam","reflect","bide","metronome","selfdestruct","eggbomb","fireblast","swift","skullbash",
                "softboiled","dreameater","skyattack","rest","thunderwave","psywave","explosion","rockslide","triattack","substitute",
                "dynamicpunch","headbutt","curse","rollout","roar","zapcannon","rocksmash","psychup","hiddenpower","sunnyday",
                "sweetscent","snore","icywind","protect","raindance","gigadrain","endure","frustration","irontail","dragonbreath",
                "return","shadowball","mudslap","icepunch","swagger","sleeptalk","sludgebomb","sandstorm","firepunch","furycutter",
                "nightmare","detect","steelwing","attract","thief","focuspunch","dragonclaw","waterpulse","calmmind","hail",
                "bulkup","bulletseed","taunt","lightscreen","safeguard","brickbreak","shockwave","flamethrower","rocktomb","aerialace",
                "torment","facade","secretpower","skillswap","snatch","overheat","roost","focusblast","energyball","falseswipe",
                "brine","fling","chargebeam","willowisp","silverwind","embargo","payback","recycle","gigaimpact","rockpolish",
                "flash","stoneedge","avalanche","thunderpunch","stealthrock","captivate","darkpulse","rockclimb","defog","honeclaws",
                "psyshock","venoshock","smackdown","sludgewave","flamecharge","lowsweep","round","echoedvoice","allyswitch","scald",
                "sky drop","incinerate","quash","acrobatics","retaliate","voltswitch","strugglebug","bulldoze","frostbreath","dragontail",
                "workup","wildcharge","snarl","naturepower","poweruppunch","confide","dazzlinggleam","infestation","powerwhip","leechlife",
                "brutalswing","auroraveil","smartstrike","stompingtantrum","breakingswipe","magicalleaf","solarblade","screech","lightscreen","reflect",
                "safeguard","selfdestruct","scaryface","facade","swift","helpinghand","revenge","brickbreak","imprison","dig",
                "weatherball","faketears","rocktomb","sandtomb","beatup","snore","protect","scaryface","icywind","gigadrain",
                "charm","steelwing","attract","raindance","sunnyday","whirlpool","willowisp","facade","swift","helpinghand",
                "revenge","brickbreak","imprison","dig","weatherball","faketears","rocktomb","sandtomb","beatup","snore",
                "bulletseed","iciclespear","mudshot","payback","assurance","powerswap","guardswap","speedswap","venoshock","avalanche",
                "thunderfang","icefang","firefang","psychicfangs","drainingkiss","grassyterrain","mistyterrain","electricterrain","psychicterrain","mysticalfire",
                "eerieimpulse","falseswipe","airslash","smartstrike","brutalswing","stompingtantrum","breakingswipe","megahorn","bodyslam","flamethrower",
                "hydropump","surf","icebeam","blizzard","lowkick","thunderbolt","thunder","earthquake","psychic","agility",
                "focusenergy","metronome","fireblast","waterfall","amnesia","leechlife","triattack","substitute","reversal","spikes",
                "outrage","endure","sleeptalk","encore","irontail","crunch","shadowball","futuresight","uproar","heatwave",
                "taunt","trick","superpower","skillswap","blazekick","hypervoice","cosmicpower","muddywater","irondefense","dragondance",
                "closecombat","aurasphere","poisonjab","darkpulse","seedbomb","xscissor","dragonpulse","dragonrush","powergem","drainpunch",
                "focusblast","energyball","bravebird","earthpower","nastyplot","zenheadbutt","flashcannon","leafstorm","powerwhip","gunkshot",
                "ironhead","stoneedge","stealthrock","grassknot","bugbuzz","heavy slam","electroball","foulplay","storedpower","allyswitch",
                "scald","wildcharge","drillrun","heatcrash","hurricane","playrough","venomdrench","highhorsepower","speedswap","liquidation",
                "bodypress","hydrocanon","blastburn","frenzyplant","steelbeam","terrainpulse","burningjealousy","flipturn","risingvoltage","grassyglide",
                "tripleaxel","coaching","scorchingsands","dualwingbeat","expandingforce","skittersmack","meteorbeam","poltergeist","scaleshot","lashout",
                "steelroller","mistyexplosion","headbutt","rocksmash","flipturn","knockoff","agility","selfdestruct","reflect","lightscreen",
                "waterpulse","gigadrain","fly","hyperbeam","mudshot","sunnyday","raindance","sandstorm","snowscape","uturn",
                "voltswitch","nastyplot","helpinghand","pounce","trailblaze","chillingwater","endure","voltswitch","thunderwave","poisonjab",
                "grassknot","rest","rockslide","swordsdance","bodypress","thunderpunch","icepunch","firepunch","sleeptalk","seedbomb",
                "drainpunch","reflect","lightscreen","substitute","willowisp","crunch","shadowclaw","foulplay","psychicfangs","bodyslam",
                "firefang","icefang","thunderfang","lowkick","aurasphere","earthpower","airslash","energyball","falseswipe","shadowball",
                "dragonclaw","dazzlinggleam","metronome","waterfall","calmmind","amnesia","batonpass","encore","tailwind","helpinghand",
                "pollenpuff","batonpass","earthquake","stealthrock","gunkshot","swordsdance","bodypress","flashcannon","ironhead","darkpulse",
                "fly","playrough","aurasphere","amnesia","calmmind","dragondance","stealthrock","spikes","toxicspikes","toxic",
                "thief","zenheadbutt","hypervoice","heatwave","flamethrower","thunderbolt","icebeam","hydropump","surf","psychic",
                "dracometeor","solarblade","flareblitz","bravebird","terablast","icespinner","snowscape","chillingwater","trailblaze","pounce",
                "spikes","toxicspikes","batonpass","encore","helpinghand","pollenpuff","upperhand","temperflare","dragoncheer","alluringvoice",
                "hardpress","supercellslam","psychicnoise","meteorbeam","scorchingsands","dualwingbeat","expandingforce","skittersmack","tripleaxel","coaching",
                "lashout","poltergeist","corrosivegas","flipturn","terrainpulse","burningjealousy","scaleshot"
        ).stream().map(TMManager::sanitizeMove).filter(s -> !s.isBlank()).toList()));
    }

    public record TeachResult(boolean handled, boolean success, String message) {
        public static TeachResult pass() { return new TeachResult(false, false, null); }
        public static TeachResult fail(String message) { return new TeachResult(true, false, message); }
        public static TeachResult success(String message) { return new TeachResult(true, true, message); }
    }

    public record CraftResult(boolean success, String message, String moveId, String rarity) {
        public static CraftResult fail(String message) { return new CraftResult(false, message, null, null); }
        public static CraftResult success(String moveId, String rarity, Map<String, Integer> cost, boolean specific) {
            String mode = specific ? "specific" : "random";
            return new CraftResult(true, "Crafted " + mode + " TM - " + prettyMove(moveId) + " (" + prettyRarity(rarity) + ") for " + costText(cost) + ".", moveId, rarity);
        }
    }

    public record TeachPreview(boolean success, String message, String pokemonName, String newMove, String replacedMove) {
        public static TeachPreview success(String message, String pokemonName, String newMove, String replacedMove) { return new TeachPreview(true, message, pokemonName, newMove, replacedMove); }
        public static TeachPreview fail(String message) { return new TeachPreview(false, message, null, null, null); }
    }

    private record PendingTeach(UUID playerId, String moveId, int partySlot, int replaceSlot, long expiresAt) {}

    public static class TMItem extends Item implements PolymerItem {
        private final Item fallbackBaseItem;
        public TMItem(Item baseItem, Properties properties) {
            super(properties);
            this.fallbackBaseItem = baseItem == null ? Items.MUSIC_DISC_CAT : baseItem;
        }
        @Override public Item getPolymerItem(ItemStack stack, ServerPlayer player) {
            String moveId = getMoveId(stack);
            if (moveId == null) return fallbackBaseItem;
            return iconForMove(moveId);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && getMoveId(stack) != null) {
                serverPlayer.sendSystemMessage(Component.literal("TMs can only be used with /tms teach <partySlot> [replaceMoveSlot].").withStyle(ChatFormatting.YELLOW));
            }
            return InteractionResultHolder.fail(stack);
        }
    }
}
