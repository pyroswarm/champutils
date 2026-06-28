package com.champutils.profession;

import com.champutils.buff.BuffContext;
import com.champutils.buff.BuffManager;
import com.champutils.buff.BuffType;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.pokemon.Pokemon;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class ProfessionTrinketManager {
    private static final Map<String, Item> REGISTERED = new ConcurrentHashMap<>();
    private static final String[] RARITIES = {"COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC"};
    private static boolean effectsRegistered = false;

    private ProfessionTrinketManager() {}

    public static void registerItems() {
        for (String rarity : RARITIES) {
            registerTrinket(rarity, "magnet", Items.IRON_INGOT);
            registerTrinket(rarity, "shiny_charm", Items.AMETHYST_SHARD);
            registerTrinket(rarity, "profession_xp_gem", Items.EMERALD);
            registerTrinket(rarity, "pokemon_xp_egg", Items.EGG);
            registerTrinket(rarity, "friendship_charm", Items.HEART_OF_THE_SEA);
            registerTrinket(rarity, "level_charm", Items.EXPERIENCE_BOTTLE);
            registerTrinket(rarity, "rare_pokemon_charm", Items.PRISMARINE_CRYSTALS);
            registerTrinket(rarity, "chunky_brick", Items.BRICK);
            registerPouch(rarity, Items.ENDER_CHEST);
        }
        System.out.println("[ChampUtils] Registered " + REGISTERED.size() + " profession trinket items.");
    }

    private static void registerTrinket(String rarity, String type, Item base) {
        String id = rarity.toLowerCase(Locale.ROOT) + "_" + type;
        if (REGISTERED.containsKey(id)) return;
        Item item = new TrinketItem(base, new Item.Properties().stacksTo(1).rarity(rarity(rarity)));
        try { Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("champutils", id), item); REGISTERED.put(id, item); }
        catch (Exception e) { System.err.println("[ChampUtils] Could not register trinket: " + id); }
    }

    private static void registerPouch(String rarity, Item base) {
        String id = rarity.toLowerCase(Locale.ROOT) + "_trinket_pouch";
        if (REGISTERED.containsKey(id)) return;
        Item item = new TrinketPouchItem(base, new Item.Properties().stacksTo(1).rarity(rarity(rarity)));
        try { Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("champutils", id), item); REGISTERED.put(id, item); }
        catch (Exception e) { System.err.println("[ChampUtils] Could not register trinket pouch: " + id); }
    }

    public static void registerEffects() {
        if (effectsRegistered) return;
        effectsRegistered = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(ProfessionTrinketManager::applyMagnet));
        CobblemonEvents.FRIENDSHIP_UPDATED.subscribe(event -> {
            try {
                Pokemon pokemon = event.getPokemon();
                ServerPlayer owner = pokemon.getOwnerPlayer();
                double bonus = friendshipBonus(owner);
                if (bonus <= 0.0D) return;
                int current = pokemon.getFriendship();
                int delta = event.getNewFriendship() - current;
                if (delta <= 0) return;
                int extra = (int)Math.floor(delta * bonus);
                if (extra <= 0 && ThreadLocalRandom.current().nextDouble() < (delta * bonus)) extra = 1;
                if (extra > 0) event.setNewFriendship(current + delta + extra);
            } catch (Throwable ignored) {
            }
        });
    }

    public static ItemStack create(String type, String rarity) {
        String t = normalizeType(type);
        String r = ProfessionFragmentConfig.normalizeRarity(rarity);
        if ("pouch".equals(t) || "trinket_pouch".equals(t)) return createPouch(r);
        Item item = REGISTERED.get(r.toLowerCase(Locale.ROOT) + "_" + t);
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("champutils_trinket", true);
        tag.putString("type", t);
        tag.putString("rarity", r);
        tag.putBoolean("enabled", true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(modelData(t, r)));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(ProfessionFragmentManager.formatWords(r) + " " + ProfessionFragmentManager.formatWords(t)).withStyle(color(r)));
        stack.set(DataComponents.LORE, new ItemLore(lore(t, r, true)));
        return stack;
    }

    public static ItemStack createPouch(String rarity) {
        String r = ProfessionFragmentConfig.normalizeRarity(rarity);
        Item item = REGISTERED.get(r.toLowerCase(Locale.ROOT) + "_trinket_pouch");
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("champutils_trinket_pouch", true);
        tag.putString("rarity", r);
        tag.putInt("slots", Math.min(9, Math.max(1, ProfessionTrinketConfig.tier(r).pouchSlots)));
        tag.putUUID("pouchId", UUID.randomUUID());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(9930 + tier(r)));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(ProfessionFragmentManager.formatWords(r) + " Trinket Pouch").withStyle(color(r)));
        updatePouchLore(stack, r, 0);
        return stack;
    }

    private static void applyMagnet(ServerPlayer player) {
        if (player == null || !ProfessionTrinketConfig.CONFIG.enabled) return;
        double radius = tierValue(player, "magnet", tier -> tier.magnetRadiusBonus);
        if (radius <= 0.0D) return;
        AABB box = player.getBoundingBox().inflate(radius);
        List<ItemEntity> items = player.level().getEntities(EntityType.ITEM, box, item -> item != null && item.isAlive());
        for (ItemEntity item : items) {
            Vec3 delta = player.position().add(0, 0.75, 0).subtract(item.position());
            double len = Math.max(0.1D, delta.length());
            item.setDeltaMovement(delta.scale(Math.min(0.45D, 0.18D + radius * 0.03D) / len));
            item.hasImpulse = true;
        }
    }

    public static void tryApplyShinyCharm(ServerPlayer player, Object pokemon) {
        if (player == null || pokemon == null || !ProfessionTrinketConfig.CONFIG.enabled) return;
        double chance = shinyCharmChancePercent(player);
        chance += serverShinyBonusPercent(player, pokemon);
        if (chance <= 0.0D) return;
        if (ThreadLocalRandom.current().nextDouble(100.0D) < chance) setShiny(pokemon, true);
    }

    public static void tryApplyWildSpawnShiny(ServerPlayer player, Object pokemon) {
        if (player == null || pokemon == null || !ProfessionTrinketConfig.CONFIG.enabled || isShiny(pokemon)) return;
        double base = (1.0D / 8192.0D) * 100.0D;
        double chance = base + shinyCharmChancePercent(player) + serverShinyBonusPercent(player, pokemon);
        if (ThreadLocalRandom.current().nextDouble(100.0D) < chance) setShiny(pokemon, true);
    }


    private static double serverShinyBonusPercent(ServerPlayer player, Object pokemon) {
        if (player == null || !(pokemon instanceof Pokemon cobblemonPokemon)) return 0.0D;
        double base = (1.0D / 8192.0D) * 100.0D;
        return BuffManager.getTotalBuff(BuffContext.trueWildCatch(player, cobblemonPokemon), BuffType.SHINY_CHANCE) * base;
    }

    public static boolean rollDoubleProfessionXp(ServerPlayer player) {
        double chance = tierValue(player, "profession_xp_gem", tier -> tier.professionXpDoubleChancePercent);
        return chance > 0.0D && ThreadLocalRandom.current().nextDouble(100.0D) < chance;
    }

    public static double pokemonXpBonus(ServerPlayer player) {
        return tierValue(player, "pokemon_xp_egg", tier -> tier.pokemonXpBonusPercent) / 100.0D;
    }

    public static double friendshipBonus(ServerPlayer player) {
        return tierValue(player, "friendship_charm", tier -> tier.friendshipBonusPercent) / 100.0D;
    }

    public static double levelCharmGymCapPercent(ServerPlayer player) {
        return tierValue(player, "level_charm", tier -> tier.levelCharmGymCapPercent) / 100.0D;
    }

    public static double rarePokemonSpawnBonus(ServerPlayer player) {
        return tierValue(player, "rare_pokemon_charm", tier -> tier.rarePokemonSpawnBonusPercent) / 100.0D;
    }

    public static double chunkChanceBonus(ServerPlayer player) {
        return tierValue(player, "chunky_brick", tier -> tier.chunkChanceBonusPercent) / 100.0D;
    }

    private static double shinyCharmChancePercent(ServerPlayer player) {
        return tierValue(player, "shiny_charm", tier -> tier.shinyChancePercent);
    }

    private interface TierExtractor { double get(ProfessionTrinketConfig.Tier tier); }

    private static double tierValue(ServerPlayer player, String type, TierExtractor extractor) {
        ItemStack best = bestActiveTrinket(player, type);
        return best.isEmpty() ? 0.0D : Math.max(0.0D, extractor.get(ProfessionTrinketConfig.tier(rarity(best))));
    }

    public static ItemStack bestActiveTrinket(ServerPlayer player, String type) {
        List<ItemStack> all = activeTrinkets(player, type);
        ItemStack best = ItemStack.EMPTY;
        for (ItemStack stack : all) {
            if (best.isEmpty() || tier(rarity(stack)) > tier(rarity(best))) best = stack;
        }
        return best;
    }

    private static List<ItemStack> activeTrinkets(ServerPlayer player, String type) {
        Map<String, ItemStack> bestByType = new LinkedHashMap<>();
        if (player == null) return new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            addIfBest(bestByType, stack, type);
            if (isPouch(stack)) {
                for (ItemStack stored : readPouchItems(player, stack)) addIfBest(bestByType, stored, type);
            }
        }
        return new ArrayList<>(bestByType.values());
    }

    private static void addIfBest(Map<String, ItemStack> bestByType, ItemStack stack, String requestedType) {
        if (!isActiveTrinket(stack, requestedType)) return;
        String type = trinketType(stack);
        ItemStack existing = bestByType.get(type);
        if (existing == null || existing.isEmpty() || tier(rarity(stack)) > tier(rarity(existing))) bestByType.put(type, stack.copy());
    }

    private static boolean isPouch(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("champutils_trinket_pouch");
    }

    private static int pouchCapacity(ItemStack pouch) {
        CustomData data = pouch.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        CompoundTag tag = data.copyTag();
        int configured = ProfessionTrinketConfig.tier(tag.getString("rarity")).pouchSlots;
        int cap = tag.getInt("slots") <= 0 ? configured : tag.getInt("slots");
        return Math.min(9, Math.max(1, cap));
    }

    private static List<ItemStack> readPouchItems(ServerPlayer player, ItemStack pouch) {
        List<ItemStack> parsed = new ArrayList<>();
        CustomData data = pouch.get(DataComponents.CUSTOM_DATA);
        if (data == null) return parsed;
        ListTag list = data.copyTag().getList("storedTrinkets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                ItemStack stack = ItemStack.parse(player.registryAccess(), list.getCompound(i)).orElse(ItemStack.EMPTY);
                if (!stack.isEmpty() && isActiveTrinket(stack, null)) parsed.add(stack);
            } catch (Throwable ignored) {}
        }
        return sanitizePouchItems(parsed, pouchCapacity(pouch));
    }

    private static List<ItemStack> sanitizePouchItems(List<ItemStack> items, int capacity) {
        Map<String, ItemStack> bestByType = new LinkedHashMap<>();
        for (ItemStack stored : items) {
            if (!isActiveTrinket(stored, null)) continue;
            String type = trinketType(stored);
            if (type == null || type.isBlank()) continue;
            ItemStack one = stored.copy();
            one.setCount(1);
            ItemStack existing = bestByType.get(type);
            if (existing == null || tier(rarity(one)) > tier(rarity(existing))) bestByType.put(type, one);
        }
        List<ItemStack> result = new ArrayList<>(bestByType.values());
        if (result.size() > capacity) result = new ArrayList<>(result.subList(0, capacity));
        return result;
    }

    private static void writePouchItems(ServerPlayer player, ItemStack pouch, List<ItemStack> items) {
        CustomData data = pouch.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        CompoundTag tag = data.copyTag();
        List<ItemStack> sanitized = sanitizePouchItems(items, pouchCapacity(pouch));
        ListTag list = new ListTag();
        for (ItemStack stored : sanitized) {
            try {
                Tag saved = stored.save(player.registryAccess());
                if (saved instanceof CompoundTag compound) list.add(compound);
            } catch (Throwable ignored) {}
        }
        tag.put("storedTrinkets", list);
        tag.putInt("storedCount", list.size());
        pouch.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        updatePouchLore(pouch, tag.getString("rarity"), list.size());
    }

    private static void updatePouchLore(ItemStack pouch, String rarity, int stored) {
        String r = ProfessionFragmentConfig.normalizeRarity(rarity);
        int slots = Math.min(9, Math.max(1, ProfessionTrinketConfig.tier(r).pouchSlots));
        pouch.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("§7Opens as a safe trinket inventory."),
                Component.literal("§7Slots: §a" + stored + "§7/§a" + slots),
                Component.literal("§7Click an empty slot to store a held trinket."),
                Component.literal("§7Click a stored trinket to withdraw it."),
                Component.literal("§8One trinket type per pouch; highest tier wins.")
        )));
    }

    private static void openPouch(ServerPlayer player, ItemStack pouch) {
        List<ItemStack> sanitized = readPouchItems(player, pouch);
        writePouchItems(player, pouch, sanitized);
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x1, player, false);
        gui.setTitle(Component.literal("Trinket Pouch"));
        refreshPouchGui(player, pouch, gui);
        gui.open();
    }

    private static void refreshPouchGui(ServerPlayer player, ItemStack pouch, SimpleGui gui) {
        int capacity = pouchCapacity(pouch);
        List<ItemStack> stored = readPouchItems(player, pouch);
        for (int i = 0; i < 9; i++) {
            if (i >= capacity) {
                gui.setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).hideDefaultTooltip().setName(Component.literal("§8Locked Slot")));
                continue;
            }
            if (i < stored.size()) {
                ItemStack display = stored.get(i).copy();
                final int index = i;
                gui.setSlot(i, new GuiElementBuilder(display)
                        .addLoreLine(Component.literal("§eClick to withdraw."))
                        .setCallback((slot, clickType, actionType) -> {
                            List<ItemStack> now = readPouchItems(player, pouch);
                            if (index >= now.size()) { refreshPouchGui(player, pouch, gui); return; }
                            ItemStack removed = now.remove(index);
                            writePouchItems(player, pouch, now);
                            if (!player.getInventory().add(removed)) player.drop(removed, false);
                            refreshPouchGui(player, pouch, gui);
                        }));
            } else {
                gui.setSlot(i, new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE).hideDefaultTooltip()
                        .setName(Component.literal("§aEmpty Trinket Slot"))
                        .addLoreLine(Component.literal("§7Hold a toggled trinket in main hand/offhand."))
                        .addLoreLine(Component.literal("§eClick to store one."))
                        .setCallback((slot, clickType, actionType) -> {
                            insertHeldTrinket(player, pouch);
                            refreshPouchGui(player, pouch, gui);
                        }));
            }
        }
    }

    private static boolean insertHeldTrinket(ServerPlayer player, ItemStack pouch) {
        ItemStack held = isActiveTrinket(player.getMainHandItem(), null) ? player.getMainHandItem() : player.getOffhandItem();
        if (!isActiveTrinket(held, null)) {
            player.sendSystemMessage(Component.literal("§eHold a toggled trinket in your main hand or offhand, then click an empty pouch slot."));
            return false;
        }
        String newType = trinketType(held);
        List<ItemStack> stored = readPouchItems(player, pouch);
        if (stored.size() >= pouchCapacity(pouch)) {
            player.sendSystemMessage(Component.literal("§cThat trinket pouch is full."));
            return false;
        }
        for (ItemStack old : stored) {
            if (newType.equals(trinketType(old))) {
                player.sendSystemMessage(Component.literal("§cThat pouch already has a " + ProfessionFragmentManager.formatWords(newType) + "."));
                return false;
            }
        }
        ItemStack one = held.copy();
        one.setCount(1);
        stored.add(one);
        writePouchItems(player, pouch, stored);
        if (!player.getAbilities().instabuild) held.shrink(1);
        player.sendSystemMessage(Component.literal("§aStored trinket in pouch. §7(" + stored.size() + "/" + pouchCapacity(pouch) + ")"));
        return true;
    }

    private static boolean isActiveTrinket(ItemStack stack, String type) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        CompoundTag tag = data.copyTag();
        return tag.getBoolean("champutils_trinket") && tag.getBoolean("enabled") && (type == null || type.equals(tag.getString("type")));
    }

    private static String trinketType(ItemStack stack) {
        CustomData data = stack == null ? null : stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString("type");
    }

    private static String rarity(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "COMMON" : ProfessionFragmentConfig.normalizeRarity(data.copyTag().getString("rarity"));
    }

    private static void toggle(ItemStack stack, Player player) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        CompoundTag tag = data.copyTag();
        boolean enabled = !tag.getBoolean("enabled");
        tag.putBoolean("enabled", enabled);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        String type = tag.getString("type");
        String rarity = ProfessionFragmentConfig.normalizeRarity(tag.getString("rarity"));
        stack.set(DataComponents.LORE, new ItemLore(lore(type, rarity, enabled)));
        player.sendSystemMessage(Component.literal((enabled ? "§aEnabled " : "§cDisabled ") + ProfessionFragmentManager.formatWords(type) + "§7."));
    }

    private static boolean isShiny(Object pokemon) {
        Object value = firstValue(pokemon, "getShiny", "isShiny", "shiny");
        return value instanceof Boolean b && b;
    }

    private static Object firstValue(Object source, String... names) {
        if (source == null) return null;
        for (String name : names) {
            try {
                if (name.startsWith("get") || name.startsWith("is")) {
                    Method method = source.getClass().getMethod(name);
                    method.setAccessible(true);
                    if (method.getParameterCount() == 0) return method.invoke(source);
                } else {
                    Field field = source.getClass().getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(source);
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void setShiny(Object pokemon, boolean shiny) {
        try { Method method = pokemon.getClass().getMethod("setShiny", boolean.class); method.invoke(pokemon, shiny); return; } catch (Throwable ignored) {}
        try { Method method = pokemon.getClass().getMethod("setShiny", Boolean.class); method.invoke(pokemon, shiny); return; } catch (Throwable ignored) {}
        Class<?> c = pokemon.getClass();
        while (c != null) {
            try { Field f = c.getDeclaredField("shiny"); f.setAccessible(true); f.setBoolean(pokemon, shiny); return; } catch (Throwable ignored) { c = c.getSuperclass(); }
        }
    }

    private static String normalizeType(String type) {
        if (type == null) return "magnet";
        String s = type.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (s.equals("charm")) return "shiny_charm";
        if (s.equals("trinketpouch")) return "trinket_pouch";
        if (s.equals("professionxpgem") || s.equals("xp_gem")) return "profession_xp_gem";
        if (s.equals("pokemonxpegg") || s.equals("xp_egg")) return "pokemon_xp_egg";
        if (s.equals("friendshipcharm")) return "friendship_charm";
        if (s.equals("levelcharm")) return "level_charm";
        if (s.equals("rarepokemoncharm") || s.equals("rare_charm")) return "rare_pokemon_charm";
        if (s.equals("chunkybrick") || s.equals("chunk_brick")) return "chunky_brick";
        return s;
    }

    private static List<Component> lore(String type, String rarity, boolean enabled) {
        List<Component> lore = new ArrayList<>();
        ProfessionTrinketConfig.Tier tier = ProfessionTrinketConfig.tier(rarity);
        lore.add(Component.literal(enabled ? "§aToggled ON" : "§cToggled OFF"));
        if ("magnet".equals(type)) lore.add(Component.literal("§7Pickup Radius: §a+" + String.format(Locale.US, "%.0f", tier.magnetRadiusBonus) + " blocks"));
        if ("shiny_charm".equals(type)) lore.add(Component.literal("§7Catch/Spawn Shiny Bonus: §d" + String.format(Locale.US, "%.4f", tier.shinyChancePercent) + "%"));
        if ("profession_xp_gem".equals(type)) lore.add(Component.literal("§7Double Profession XP Chance: §a" + fmt(tier.professionXpDoubleChancePercent) + "%"));
        if ("pokemon_xp_egg".equals(type)) lore.add(Component.literal("§7Pokémon Battle XP: §b+" + fmt(tier.pokemonXpBonusPercent) + "%"));
        if ("friendship_charm".equals(type)) lore.add(Component.literal("§7Friendship Gain: §d+" + fmt(tier.friendshipBonusPercent) + "%"));
        if ("level_charm".equals(type)) lore.add(Component.literal("§7Nearby Spawn Min Level: §e" + fmt(tier.levelCharmGymCapPercent) + "% of gym cap"));
        if ("rare_pokemon_charm".equals(type)) lore.add(Component.literal("§7Rare Non-Special Spawn Weight: §6+" + fmt(tier.rarePokemonSpawnBonusPercent) + "%"));
        if ("chunky_brick".equals(type)) lore.add(Component.literal("§7Chunk Odds Multiplier: §6+" + fmt(tier.chunkChanceBonusPercent) + "%"));
        lore.add(Component.literal("§7Right click while holding to toggle."));
        lore.add(Component.literal("§8Duplicate types do not stack; highest tier is used."));
        return lore;
    }

    private static String fmt(double value) { return String.format(Locale.US, value >= 10 ? "%.0f" : "%.2f", value).replaceAll("\\.00$", ""); }

    private static int modelData(String type, String rarity) {
        int base = switch (type) {
            case "magnet" -> 9910;
            case "shiny_charm" -> 9920;
            case "profession_xp_gem" -> 9940;
            case "pokemon_xp_egg" -> 9950;
            case "friendship_charm" -> 9960;
            case "level_charm" -> 9970;
            case "rare_pokemon_charm" -> 9980;
            case "chunky_brick" -> 9990;
            default -> 9900;
        };
        return base + tier(rarity);
    }
    private static int tier(String rarity) { return switch (ProfessionFragmentConfig.normalizeRarity(rarity)) { case "UNCOMMON" -> 2; case "RARE" -> 3; case "EPIC" -> 4; case "LEGENDARY" -> 5; case "MYTHIC" -> 6; default -> 1; }; }
    private static ChatFormatting color(String rarity) { return switch (ProfessionFragmentConfig.normalizeRarity(rarity)) { case "UNCOMMON" -> ChatFormatting.GREEN; case "RARE" -> ChatFormatting.BLUE; case "EPIC" -> ChatFormatting.LIGHT_PURPLE; case "LEGENDARY" -> ChatFormatting.GOLD; case "MYTHIC" -> ChatFormatting.DARK_PURPLE; default -> ChatFormatting.WHITE; }; }
    private static Rarity rarity(String rarity) { return switch (ProfessionFragmentConfig.normalizeRarity(rarity)) { case "UNCOMMON" -> Rarity.UNCOMMON; case "RARE" -> Rarity.RARE; case "EPIC", "LEGENDARY", "MYTHIC" -> Rarity.EPIC; default -> Rarity.COMMON; }; }

    public static class TrinketItem extends Item implements PolymerItem {
        private final Item base;
        public TrinketItem(Item base, Properties properties) { super(properties); this.base = base; }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) toggle(stack, player);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        @Override public Item getPolymerItem(ItemStack stack, ServerPlayer player) { return base; }
    }

    public static class TrinketPouchItem extends Item implements PolymerItem {
        private final Item base;
        public TrinketPouchItem(Item base, Properties properties) { super(properties); this.base = base; }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack pouch = player.getItemInHand(hand);
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) openPouch(serverPlayer, pouch);
            return InteractionResultHolder.sidedSuccess(pouch, level.isClientSide());
        }
        @Override public Item getPolymerItem(ItemStack stack, ServerPlayer player) { return base; }
    }
}
