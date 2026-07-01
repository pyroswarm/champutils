package com.champutils.profession;

import com.champutils.buff.BuffContext;
import com.champutils.buff.BuffManager;
import com.champutils.buff.BuffType;
import com.champutils.profile.IronmanItemOwnership;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.pokemon.Pokemon;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.commands.Commands;
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
            registerTrinket(rarity, "pokemon_xp_egg", Items.PRISMARINE_CRYSTALS);
            registerTrinket(rarity, "friendship_charm", Items.HEART_OF_THE_SEA);
            registerTrinket(rarity, "level_charm", Items.NETHER_STAR);
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
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("tpouch")
                    .executes(context -> {
                        openDigitalPouch(context.getSource().getPlayerOrException());
                        return 1;
                    }));
            dispatcher.register(Commands.literal("trinketpouch")
                    .executes(context -> {
                        openDigitalPouch(context.getSource().getPlayerOrException());
                        return 1;
                    }));
        });
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
                if (extra > 0) {
                    event.setNewFriendship(current + delta + extra);
                    if (ProfessionNotificationSettings.areTrinketMessagesEnabled(owner)) owner.sendSystemMessage(Component.literal("[Trinket] Friendship Charm added +" + extra + " extra friendship.").withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            } catch (Throwable ignored) {
            }
        });
    }


    public static ItemStack migrateStack(ItemStack original) {
        if (original == null || original.isEmpty()) return original;
        CustomData data = original.get(DataComponents.CUSTOM_DATA);
        if (data == null) return original;
        CompoundTag tag = data.copyTag();

        if (tag.getBoolean("champutils_trinket")) {
            String type = normalizeType(tag.getString("type"));
            String rarity = ProfessionFragmentConfig.normalizeRarity(tag.getString("rarity"));
            ItemStack migrated = create(type, rarity);
            if (migrated.isEmpty()) return original;
            CustomData migratedData = migrated.get(DataComponents.CUSTOM_DATA);
            if (migratedData != null) {
                CompoundTag migratedTag = migratedData.copyTag();
                migratedTag.putBoolean("enabled", tag.getBoolean("enabled"));
                migrated.set(DataComponents.CUSTOM_DATA, CustomData.of(migratedTag));
                migrated.set(DataComponents.LORE, new ItemLore(lore(type, rarity, migratedTag.getBoolean("enabled"))));
            }
            migrated.setCount(1);
            return migrated;
        }

        if (tag.getBoolean("champutils_trinket_pouch")) {
            String rarity = ProfessionFragmentConfig.normalizeRarity(tag.getString("rarity"));
            ItemStack migrated = createPouch(rarity);
            if (migrated.isEmpty()) return original;
            CustomData migratedData = migrated.get(DataComponents.CUSTOM_DATA);
            if (migratedData != null) {
                CompoundTag migratedTag = migratedData.copyTag();
                if (tag.contains("storedTrinkets")) migratedTag.put("storedTrinkets", tag.getList("storedTrinkets", Tag.TAG_COMPOUND));
                if (tag.contains("storedCount")) migratedTag.putInt("storedCount", tag.getInt("storedCount"));
                if (tag.contains("pouchId")) {
                    try { migratedTag.putUUID("pouchId", tag.getUUID("pouchId")); } catch (Throwable ignored) {}
                }
                migrated.set(DataComponents.CUSTOM_DATA, CustomData.of(migratedTag));
                updatePouchLore(migrated, rarity, migratedTag.getInt("storedCount"));
            }
            migrated.setCount(1);
            return migrated;
        }

        return original;
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
        tag.putInt("slots", Math.min(54, Math.max(1, ProfessionTrinketConfig.tier(r).pouchSlots)));
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
        List<ItemEntity> items = player.level().getEntities(EntityType.ITEM, box, item -> item != null && item.isAlive() && IronmanItemOwnership.canPickup(player, item));
        for (ItemEntity item : items) {
            Vec3 delta = player.position().add(0, 0.75, 0).subtract(item.position());
            double len = Math.max(0.1D, delta.length());
            item.setDeltaMovement(delta.scale(Math.min(0.45D, 0.18D + radius * 0.03D) / len));
            item.hasImpulse = true;
        }
    }

    public static void tryApplyShinyCharm(ServerPlayer player, Object pokemon) {
        if (player == null || pokemon == null || !ProfessionTrinketConfig.CONFIG.enabled || isShiny(pokemon)) return;
        double charmChance = shinyCharmChancePercent(player);
        double buffChance = serverShinyBonusPercent(player, pokemon);
        double chance = charmChance + buffChance;
        if (chance <= 0.0D) return;
        if (ThreadLocalRandom.current().nextDouble(100.0D) < chance && setShiny(pokemon, true)) {
            String reason = charmChance > 0.0D ? "Your Shiny Charm" : "An active shiny bonus";
            if (charmChance > 0.0D && buffChance > 0.0D) reason = "Your Shiny Charm and active shiny bonuses";
            if (ProfessionNotificationSettings.areTrinketMessagesEnabled(player)) player.sendSystemMessage(Component.literal("[Trinket] " + reason + " turned this catch shiny!").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    public static void tryApplyWildSpawnShiny(ServerPlayer player, Object pokemon) {
        if (player == null || pokemon == null || !ProfessionTrinketConfig.CONFIG.enabled || isShiny(pokemon)) return;
        double base = (1.0D / 8192.0D) * 100.0D;
        double charmChance = shinyCharmChancePercent(player);
        double buffChance = serverShinyBonusPercent(player, pokemon);
        double chance = base + charmChance + buffChance;
        double rolled = ThreadLocalRandom.current().nextDouble(100.0D);
        if (rolled < chance && setShiny(pokemon, true)) {
            if (rolled >= base && (charmChance > 0.0D || buffChance > 0.0D)) {
                String reason = charmChance > 0.0D ? "Shiny Charm" : "active shiny bonus";
                if (charmChance > 0.0D && buffChance > 0.0D) reason = "Shiny Charm and active shiny bonuses";
                if (ProfessionNotificationSettings.areTrinketMessagesEnabled(player)) player.sendSystemMessage(Component.literal("[Bonus] " + reason + " turned a nearby wild Pokémon shiny!").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
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
        migratePhysicalPouchesToDigital(player, false);
        for (ItemStack stack : player.getInventory().items) {
            addIfBest(bestByType, stack, type);
        }
        for (ItemStack stored : readDigitalPouchItems(player)) {
            addIfBest(bestByType, stored, type);
        }
        return new ArrayList<>(bestByType.values());
    }

    private static void addIfBest(Map<String, ItemStack> bestByType, ItemStack stack, String requestedType) {
        if (!isActiveTrinket(stack, requestedType)) return;
        String type = trinketType(stack);
        ItemStack existing = bestByType.get(type);
        if (existing == null || existing.isEmpty() || tier(rarity(stack)) > tier(rarity(existing))) bestByType.put(type, stack.copy());
    }

    public static int digitalPouchTier(ServerPlayer player) {
        if (player == null) return 0;
        ProfessionDataManager.ProfessionData data = ProfessionManager.getData(player);
        if (data.trinketPouchRarity == null || data.trinketPouchRarity.isBlank()) return 0;
        return tier(data.trinketPouchRarity);
    }

    public static int digitalPouchSlots(ServerPlayer player) {
        if (player == null) return 0;
        ProfessionDataManager.ProfessionData data = ProfessionManager.getData(player);
        if (data.trinketPouchRarity == null || data.trinketPouchRarity.isBlank()) return 0;
        int configured = ProfessionTrinketConfig.tier(data.trinketPouchRarity).pouchSlots;
        int slots = data.trinketPouchSlots <= 0 ? configured : data.trinketPouchSlots;
        return Math.min(54, Math.max(0, slots));
    }

    public static boolean canUpgradeDigitalPouch(ServerPlayer player, String rarity) {
        if (player == null) return false;
        String r = ProfessionFragmentConfig.normalizeRarity(rarity);
        return tier(r) > digitalPouchTier(player);
    }

    public static boolean unlockOrUpgradeDigitalPouch(ServerPlayer player, String rarity) {
        if (player == null) return false;
        String r = ProfessionFragmentConfig.normalizeRarity(rarity);
        if (!canUpgradeDigitalPouch(player, r)) return false;
        ProfessionDataManager.ProfessionData data = ProfessionManager.getData(player);
        data.trinketPouchRarity = r;
        data.trinketPouchSlots = Math.max(data.trinketPouchSlots, Math.min(54, Math.max(1, ProfessionTrinketConfig.tier(r).pouchSlots)));
        writeDigitalPouchItems(player, readDigitalPouchItems(player));
        ProfessionManager.markDirtyProfile(com.champutils.profile.PlayerProfileManager.activeProfileId(player));
        ProfessionManager.savePlayer(player);
        player.sendSystemMessage(Component.literal("§aUnlocked " + ProfessionFragmentManager.formatWords(r) + " digital Trinket Pouch with " + data.trinketPouchSlots + " slots."));
        return true;
    }

    public static void openDigitalPouch(ServerPlayer player) {
        if (player == null) return;
        migratePhysicalPouchesToDigital(player, true);
        int capacity = digitalPouchSlots(player);
        if (capacity <= 0) {
            player.sendSystemMessage(Component.literal("§eYou do not have a trinket pouch yet. Craft one from Fragment Crafting to unlock digital pouch slots."));
            return;
        }
        List<ItemStack> sanitized = readDigitalPouchItems(player);
        writeDigitalPouchItems(player, sanitized);
        SimpleGui gui = new DigitalTrinketPouchGui(pouchMenuType(capacity), player, pouchGuiSlots(capacity));
        gui.setTitle(Component.literal("Digital Trinket Pouch"));
        refreshDigitalPouchGui(player, gui);
        gui.open();
    }

    private static List<ItemStack> readDigitalPouchItems(ServerPlayer player) {
        List<ItemStack> parsed = new ArrayList<>();
        if (player == null) return parsed;
        ProfessionDataManager.ProfessionData data = ProfessionManager.getData(player);
        if (data.trinketPouchItems == null) data.trinketPouchItems = new ArrayList<>();
        for (String saved : data.trinketPouchItems) {
            if (saved == null || saved.isBlank()) continue;
            try {
                CompoundTag tag = TagParser.parseTag(saved);
                ItemStack stack = ItemStack.parse(player.registryAccess(), tag).orElse(ItemStack.EMPTY);
                if (!stack.isEmpty() && isTrinket(stack, null)) parsed.add(stack);
            } catch (Throwable ignored) {}
        }
        return sanitizePouchItems(parsed, digitalPouchSlots(player));
    }

    private static boolean writeDigitalPouchItems(ServerPlayer player, List<ItemStack> items) {
        if (player == null) return false;
        ProfessionDataManager.ProfessionData data = ProfessionManager.getData(player);
        if (data.trinketPouchItems == null) data.trinketPouchItems = new ArrayList<>();
        List<ItemStack> sanitized = sanitizePouchItems(items, digitalPouchSlots(player));
        data.trinketPouchItems.clear();
        for (ItemStack stored : sanitized) {
            try {
                Tag saved = stored.save(player.registryAccess());
                if (saved instanceof CompoundTag compound) data.trinketPouchItems.add(compound.toString());
            } catch (Throwable ignored) {}
        }
        ProfessionManager.markDirtyProfile(com.champutils.profile.PlayerProfileManager.activeProfileId(player));
        ProfessionManager.savePlayer(player);
        return true;
    }

    private static void refreshDigitalPouchGui(ServerPlayer player, SimpleGui gui) {
        int capacity = digitalPouchSlots(player);
        List<ItemStack> stored = readDigitalPouchItems(player);
        int visibleSlots = pouchGuiSlots(capacity);
        for (int i = 0; i < visibleSlots; i++) {
            if (i >= capacity) {
                gui.setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).hideDefaultTooltip().setName(Component.literal("§8Locked Slot")));
                continue;
            }
            if (i < stored.size()) {
                ItemStack display = stored.get(i).copy();
                final int index = i;
                gui.setSlot(i, new GuiElementBuilder(display)
                        .addLoreLine(Component.literal("§eLeft-click to withdraw."))
                        .addLoreLine(Component.literal("§eRight-click to toggle."))
                        .setCallback((slot, clickType, actionType) -> {
                            List<ItemStack> now = readDigitalPouchItems(player);
                            if (index >= now.size()) { refreshDigitalPouchGui(player, gui); return; }
                            boolean rightClick = clickType != null && clickType.toString().toLowerCase(Locale.ROOT).contains("right");
                            if (rightClick) {
                                ItemStack toggled = now.get(index);
                                toggle(toggled, player);
                                now.set(index, toggled);
                                writeDigitalPouchItems(player, now);
                                refreshDigitalPouchGui(player, gui);
                                return;
                            }
                            ItemStack removed = now.remove(index);
                            writeDigitalPouchItems(player, now);
                            if (!player.getInventory().add(removed)) player.drop(removed, false);
                            refreshDigitalPouchGui(player, gui);
                        }));
            } else {
                gui.setSlot(i, new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE).hideDefaultTooltip()
                        .setName(Component.literal("§aEmpty Trinket Slot"))
                        .addLoreLine(Component.literal("§7Hold any trinket in main hand/offhand."))
                        .addLoreLine(Component.literal("§eClick to store one. Shift-click also works."))
                        .setCallback((slot, clickType, actionType) -> {
                            insertHeldTrinketDigital(player);
                            refreshDigitalPouchGui(player, gui);
                        }));
            }
        }
    }

    private static boolean insertHeldTrinketDigital(ServerPlayer player) {
        ItemStack held = isTrinket(player.getMainHandItem(), null) ? player.getMainHandItem() : player.getOffhandItem();
        if (!isTrinket(held, null)) {
            player.sendSystemMessage(Component.literal("§eHold a trinket in your main hand or offhand, then click an empty pouch slot."));
            return false;
        }
        return insertSpecificTrinketDigital(player, held);
    }

    private static boolean insertSpecificTrinketDigital(ServerPlayer player, ItemStack trinket) {
        if (player == null || !isTrinket(trinket, null)) return false;
        int capacity = digitalPouchSlots(player);
        if (capacity <= 0) {
            player.sendSystemMessage(Component.literal("§cCraft a Trinket Pouch before storing trinkets."));
            return false;
        }
        String newType = trinketType(trinket);
        List<ItemStack> stored = readDigitalPouchItems(player);
        if (stored.size() >= capacity) {
            player.sendSystemMessage(Component.literal("§cYour digital trinket pouch is full."));
            return false;
        }
        for (ItemStack old : stored) {
            if (newType.equals(trinketType(old))) {
                player.sendSystemMessage(Component.literal("§cYour pouch already has a " + ProfessionFragmentManager.formatWords(newType) + "."));
                return false;
            }
        }
        ItemStack one = trinket.copy();
        one.setCount(1);
        stored.add(one);
        writeDigitalPouchItems(player, stored);
        if (!player.getAbilities().instabuild) trinket.shrink(1);
        player.getInventory().setChanged();
        player.sendSystemMessage(Component.literal("§aStored trinket in digital pouch. §7(" + stored.size() + "/" + capacity + ")"));
        return true;
    }

    private static void migratePhysicalPouchesToDigital(ServerPlayer player, boolean tellPlayer) {
        if (player == null) return;
        boolean changed = false;
        List<Integer> pouchSlots = new ArrayList<>();
        List<ItemStack> digital = readDigitalPouchItems(player);
        ProfessionDataManager.ProfessionData professionData = ProfessionManager.getData(player);
        String bestRarity = professionData.trinketPouchRarity;
        int bestSlots = Math.max(0, professionData.trinketPouchSlots);

        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack pouch = player.getInventory().items.get(i);
            if (!isPouch(pouch)) continue;
            CustomData data = pouch.get(DataComponents.CUSTOM_DATA);
            String rarity = data == null ? "COMMON" : ProfessionFragmentConfig.normalizeRarity(data.copyTag().getString("rarity"));
            if (tier(rarity) > tier(bestRarity)) {
                bestRarity = rarity;
                bestSlots = Math.min(54, Math.max(1, ProfessionTrinketConfig.tier(rarity).pouchSlots));
            }
            digital.addAll(readPouchItems(player, pouch));
            pouchSlots.add(i);
            changed = true;
        }
        if (changed) {
            professionData.trinketPouchRarity = bestRarity == null ? "" : bestRarity;
            professionData.trinketPouchSlots = Math.max(bestSlots, Math.min(54, Math.max(0, ProfessionTrinketConfig.tier(bestRarity).pouchSlots)));
            if (writeDigitalPouchItems(player, digital)) {
                for (Integer slot : pouchSlots) {
                    player.getInventory().items.set(slot, ItemStack.EMPTY);
                }
                player.getInventory().setChanged();
                ProfessionManager.savePlayer(player);
                if (tellPlayer) player.sendSystemMessage(Component.literal("§aConverted your physical trinket pouch into digital storage."));
            }
        }
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
        return Math.min(54, Math.max(1, cap));
    }

    private static List<ItemStack> readPouchItems(ServerPlayer player, ItemStack pouch) {
        List<ItemStack> parsed = new ArrayList<>();
        CustomData data = pouch.get(DataComponents.CUSTOM_DATA);
        if (data == null) return parsed;
        ListTag list = data.copyTag().getList("storedTrinkets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                ItemStack stack = ItemStack.parse(player.registryAccess(), list.getCompound(i)).orElse(ItemStack.EMPTY);
                if (!stack.isEmpty() && isTrinket(stack, null)) parsed.add(stack);
            } catch (Throwable ignored) {}
        }
        return sanitizePouchItems(parsed, pouchCapacity(pouch));
    }

    private static List<ItemStack> sanitizePouchItems(List<ItemStack> items, int capacity) {
        Map<String, ItemStack> bestByType = new LinkedHashMap<>();
        for (ItemStack stored : items) {
            if (!isTrinket(stored, null)) continue;
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
        int slots = Math.min(54, Math.max(1, ProfessionTrinketConfig.tier(r).pouchSlots));
        pouch.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("§7Opens as a safe trinket inventory."),
                Component.literal("§7Slots: §a" + stored + "§7/§a" + slots),
                Component.literal("§7Click empty slots or shift-click trinkets from inventory."),
                Component.literal("§7Disabled trinkets can be stored but will not work."),
                Component.literal("§7Left-click stored trinkets to withdraw them."),
                Component.literal("§7Right-click stored trinkets to toggle them."),
                Component.literal("§8One trinket type per pouch; highest tier wins.")
        )));
    }

    private static void openPouch(ServerPlayer player, ItemStack pouch) {
        migratePhysicalPouchesToDigital(player, true);
        openDigitalPouch(player);
    }

    private static void refreshPouchGui(ServerPlayer player, ItemStack pouch, SimpleGui gui) {
        int capacity = pouchCapacity(pouch);
        List<ItemStack> stored = readPouchItems(player, pouch);
        int visibleSlots = pouchGuiSlots(capacity);
        for (int i = 0; i < visibleSlots; i++) {
            if (i >= capacity) {
                gui.setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).hideDefaultTooltip().setName(Component.literal("§8Locked Slot")));
                continue;
            }
            if (i < stored.size()) {
                ItemStack display = stored.get(i).copy();
                final int index = i;
                gui.setSlot(i, new GuiElementBuilder(display)
                        .addLoreLine(Component.literal("§eLeft-click to withdraw."))
                        .addLoreLine(Component.literal("§eRight-click to toggle."))
                        .setCallback((slot, clickType, actionType) -> {
                            List<ItemStack> now = readPouchItems(player, pouch);
                            if (index >= now.size()) { refreshPouchGui(player, pouch, gui); return; }
                            boolean rightClick = clickType != null && clickType.toString().toLowerCase(Locale.ROOT).contains("right");
                            if (rightClick) {
                                ItemStack toggled = now.get(index);
                                toggle(toggled, player);
                                now.set(index, toggled);
                                writePouchItems(player, pouch, now);
                                refreshPouchGui(player, pouch, gui);
                                return;
                            }
                            ItemStack removed = now.remove(index);
                            writePouchItems(player, pouch, now);
                            if (!player.getInventory().add(removed)) player.drop(removed, false);
                            refreshPouchGui(player, pouch, gui);
                        }));
            } else {
                gui.setSlot(i, new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE).hideDefaultTooltip()
                        .setName(Component.literal("§aEmpty Trinket Slot"))
                        .addLoreLine(Component.literal("§7Hold any trinket in main hand/offhand."))
                        .addLoreLine(Component.literal("§eClick to store one. Shift-click also works."))
                        .setCallback((slot, clickType, actionType) -> {
                            insertHeldTrinket(player, pouch);
                            refreshPouchGui(player, pouch, gui);
                        }));
            }
        }
    }

    private static MenuType<?> pouchMenuType(int capacity) {
        int rows = Math.max(1, Math.min(6, (pouchGuiSlots(capacity) + 8) / 9));
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    private static int pouchGuiSlots(int capacity) {
        return Math.min(54, Math.max(9, ((Math.max(1, capacity) + 8) / 9) * 9));
    }

    private static boolean insertHeldTrinket(ServerPlayer player, ItemStack pouch) {
        ItemStack held = isTrinket(player.getMainHandItem(), null) ? player.getMainHandItem() : player.getOffhandItem();
        if (!isTrinket(held, null)) {
            player.sendSystemMessage(Component.literal("§eHold a trinket in your main hand or offhand, then click an empty pouch slot."));
            return false;
        }
        return insertSpecificTrinket(player, pouch, held);
    }

    private static boolean insertSpecificTrinket(ServerPlayer player, ItemStack pouch, ItemStack trinket) {
        if (!isTrinket(trinket, null)) return false;
        String newType = trinketType(trinket);
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
        ItemStack one = trinket.copy();
        one.setCount(1);
        stored.add(one);
        writePouchItems(player, pouch, stored);
        if (!player.getAbilities().instabuild) trinket.shrink(1);
        player.sendSystemMessage(Component.literal("§aStored trinket in pouch. §7(" + stored.size() + "/" + pouchCapacity(pouch) + ")"));
        return true;
    }

    public static boolean toggleBestMagnet(ServerPlayer player) {
        if (player == null) return false;
        migratePhysicalPouchesToDigital(player, false);
        ItemStack bestInventory = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (isTrinket(stack, "magnet") && (bestInventory.isEmpty() || tier(rarity(stack)) > tier(rarity(bestInventory)))) {
                bestInventory = stack;
            }
        }
        ItemStack bestDigital = ItemStack.EMPTY;
        int bestDigitalIndex = -1;
        List<ItemStack> stored = readDigitalPouchItems(player);
        for (int i = 0; i < stored.size(); i++) {
            ItemStack storedStack = stored.get(i);
            if (isTrinket(storedStack, "magnet") && (bestDigital.isEmpty() || tier(rarity(storedStack)) > tier(rarity(bestDigital)))) {
                bestDigital = storedStack;
                bestDigitalIndex = i;
            }
        }
        if (!bestInventory.isEmpty() && (bestDigital.isEmpty() || tier(rarity(bestInventory)) >= tier(rarity(bestDigital)))) {
            toggle(bestInventory, player);
            return true;
        }
        if (!bestDigital.isEmpty() && bestDigitalIndex >= 0) {
            ItemStack stack = stored.get(bestDigitalIndex);
            toggle(stack, player);
            stored.set(bestDigitalIndex, stack);
            writeDigitalPouchItems(player, stored);
            return true;
        }
        return false;
    }

    private static boolean isTrinket(ItemStack stack, String type) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        CompoundTag tag = data.copyTag();
        return tag.getBoolean("champutils_trinket") && (type == null || type.equals(tag.getString("type")));
    }

    private static boolean isActiveTrinket(ItemStack stack, String type) {
        if (!isTrinket(stack, type)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("enabled");
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

    private static boolean setShiny(Object pokemon, boolean shiny) {
        try { Method method = pokemon.getClass().getMethod("setShiny", boolean.class); method.invoke(pokemon, shiny); return true; } catch (Throwable ignored) {}
        try { Method method = pokemon.getClass().getMethod("setShiny", Boolean.class); method.invoke(pokemon, shiny); return true; } catch (Throwable ignored) {}
        Class<?> c = pokemon.getClass();
        while (c != null) {
            try { Field f = c.getDeclaredField("shiny"); f.setAccessible(true); f.setBoolean(pokemon, shiny); return true; } catch (Throwable ignored) { c = c.getSuperclass(); }
        }
        return false;
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
        lore.add(Component.literal("§7Right-click while holding to toggle."));
        lore.add(Component.literal("§7Store in /tpouch or /trinketpouch."));
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


    private static final class DigitalTrinketPouchGui extends SimpleGui {
        private final ServerPlayer owner;
        private final int topSlots;

        private DigitalTrinketPouchGui(MenuType<?> type, ServerPlayer owner, int topSlots) {
            super(type, owner, false);
            this.owner = owner;
            this.topSlots = topSlots;
        }

        @Override
        public boolean onAnyClick(int index, ClickType type, net.minecraft.world.inventory.ClickType action) {
            if (owner != null && action == net.minecraft.world.inventory.ClickType.QUICK_MOVE && index >= topSlots && index >= 0 && index < owner.containerMenu.slots.size()) {
                ItemStack clicked = owner.containerMenu.getSlot(index).getItem();
                if (isTrinket(clicked, null) && insertSpecificTrinketDigital(owner, clicked)) {
                    refreshDigitalPouchGui(owner, this);
                    this.sendGui();
                    owner.containerMenu.broadcastChanges();
                    owner.inventoryMenu.broadcastChanges();
                }
                return false;
            }
            return super.onAnyClick(index, type, action);
        }
    }

    private static final class TrinketPouchGui extends SimpleGui {
        private final ServerPlayer owner;
        private final ItemStack pouch;
        private final int topSlots;

        private TrinketPouchGui(MenuType<?> type, ServerPlayer owner, ItemStack pouch, int topSlots) {
            super(type, owner, false);
            this.owner = owner;
            this.pouch = pouch;
            this.topSlots = topSlots;
        }

        @Override
        public boolean onAnyClick(int index, ClickType type, net.minecraft.world.inventory.ClickType action) {
            if (owner != null && action == net.minecraft.world.inventory.ClickType.QUICK_MOVE && index >= topSlots && index >= 0 && index < owner.containerMenu.slots.size()) {
                ItemStack clicked = owner.containerMenu.getSlot(index).getItem();
                if (isTrinket(clicked, null) && insertSpecificTrinket(owner, pouch, clicked)) {
                    refreshPouchGui(owner, pouch, this);
                    this.sendGui();
                    owner.containerMenu.broadcastChanges();
                    owner.inventoryMenu.broadcastChanges();
                }
                return false;
            }
            return super.onAnyClick(index, type, action);
        }
    }

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
