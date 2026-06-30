package com.champutils.cashshop;

import com.champutils.buff.BuffManager;
import com.champutils.buff.BuffType;
import com.champutils.buff.ServerBuffManager;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

public final class CashShopBoostItemManager {
    public static final long DEFAULT_DURATION_MS = 15L * 60L * 1000L;
    private static final Map<String, Def> DEFS = new LinkedHashMap<>();
    private static boolean registered = false;

    static {
        add("shiny_surge", "§dServer Shiny Surge", BuffType.SHINY_CHANCE, 0.01D, "Increases the current shiny chance by +1% for the whole server for 15 minutes.");
        add("special_surge", "§6Server Legendary Surge", null, 0.50D, "Adds +50% legendary/mythical wild spawn chance for the whole server for 15 minutes.");
        add("paradox_surge", "§5Server Paradox Surge", null, 0.50D, "Adds +50% paradox wild spawn chance for the whole server for 15 minutes.");
        add("ultrabeast_surge", "§dServer Ultra Beast Surge", null, 0.50D, "Adds +50% Ultra Beast wild spawn chance for the whole server for 15 minutes.");
        add("pokemon_xp_surge", "§bServer Pokémon XP Surge", BuffType.POKEMON_XP, 0.25D, "Adds +25% Pokémon battle XP for 15 minutes.");
        add("mining_xp_surge", "§3Server Mining XP Surge", BuffType.MINING_XP, 0.50D, "Adds +50% Mining profession XP for the whole server for 15 minutes.");
        add("forestry_xp_surge", "§aServer Forestry XP Surge", BuffType.FORESTRY_XP, 0.50D, "Adds +50% Forestry profession XP for the whole server for 15 minutes.");
        add("farming_xp_surge", "§eServer Farming XP Surge", BuffType.FARMING_XP, 0.50D, "Adds +50% Farming profession XP for the whole server for 15 minutes.");
        add("battling_xp_surge", "§cServer Battle XP Surge", BuffType.BATTLING_XP, 0.50D, "Adds +50% Battle profession XP for the whole server for 15 minutes.");
    }

    private CashShopBoostItemManager() {}
    private static void add(String id, String name, BuffType type, double amount, String lore) { DEFS.put(id, new Def(id, name, type, amount, lore)); }
    public static Collection<Def> defs() { return DEFS.values(); }

    public static void register() {
        if (registered) return;
        registered = true;
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(player.getItemInHand(hand));
            ItemStack stack = player.getItemInHand(hand);
            String id = readId(stack);
            Def def = DEFS.get(id);
            if (def == null) return InteractionResultHolder.pass(stack);
            if (!activate(sp.server, sp, def)) return InteractionResultHolder.fail(stack);
            if (!sp.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.success(stack);
        });
    }

    public static ItemStack createItem(String id, int count) {
        Def def = DEFS.get(id);
        if (def == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(Items.NETHER_STAR, Math.max(1, count));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(def.name));
        stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                Component.literal("§7Server booster credit item"),
                Component.literal("§7" + def.lore),
                Component.literal("§eRight-click to activate for everyone."),
                Component.literal("§8champutils_cash_boost:" + id)
        )));
        return stack;
    }

    private static String readId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            net.minecraft.world.item.component.ItemLore lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
            if (lore == null) return null;
            for (Component line : lore.lines()) {
                String text = line.getString();
                int idx = text.indexOf("champutils_cash_boost:");
                if (idx >= 0) return text.substring(idx + "champutils_cash_boost:".length()).trim();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static boolean activateFromCredit(ServerPlayer player, String id) {
        Def def = DEFS.get(id);
        if (def == null) return false;
        return activate(player.server, player, def);
    }

    public static boolean activateFromAdmin(MinecraftServer server, ServerPlayer sourcePlayer, String id) {
        Def def = DEFS.get(id);
        if (def == null || server == null) return false;
        return activate(server, sourcePlayer, def);
    }

    public static void deactivateAdmin(String id) {
        if (id == null || id.isBlank()) return;
        ServerBuffManager.deactivateBoost(id);
        ServerBuffManager.deactivate("cash_" + id);
        if (id.equals("profession_xp_surge")) {
            ServerBuffManager.deactivate("cash_mining_xp");
            ServerBuffManager.deactivate("cash_forestry_xp");
            ServerBuffManager.deactivate("cash_farming_xp");
            ServerBuffManager.deactivate("cash_battling_xp");
        }
        if (id.equals("special_surge")) {
            com.champutils.specialspawn.SpecialWildSpawnManager.deactivateCashShopBoost();
        }
        if (id.equals("paradox_surge")) {
            com.champutils.specialspawn.SpecialWildSpawnManager.deactivateParadoxCashShopBoost();
        }
        if (id.equals("ultrabeast_surge")) {
            com.champutils.specialspawn.SpecialWildSpawnManager.deactivateUltraBeastCashShopBoost();
        }
    }

    private static boolean activate(MinecraftServer server, ServerPlayer player, Def def) {
        if (!ServerBuffManager.tryBeginExclusiveBoost(player, def.id, def.cleanName(), def.amount, DEFAULT_DURATION_MS)) return false;
        if (def.id.equals("special_surge")) {
            com.champutils.specialspawn.SpecialWildSpawnManager.activateCashShopBoost(def.amount, DEFAULT_DURATION_MS);
            server.getPlayerList().broadcastSystemMessage(Component.literal("[Server Boost] +" + BuffManager.percent(def.amount) + " Legendary Spawn Chance is now active!").withStyle(ChatFormatting.GOLD), false);
            return true;
        }
        if (def.id.equals("paradox_surge")) {
            com.champutils.specialspawn.SpecialWildSpawnManager.activateParadoxCashShopBoost(def.amount, DEFAULT_DURATION_MS);
            server.getPlayerList().broadcastSystemMessage(Component.literal("[Server Boost] +" + BuffManager.percent(def.amount) + " Paradox Spawn Chance is now active!").withStyle(ChatFormatting.DARK_PURPLE), false);
            return true;
        }
        if (def.id.equals("ultrabeast_surge")) {
            com.champutils.specialspawn.SpecialWildSpawnManager.activateUltraBeastCashShopBoost(def.amount, DEFAULT_DURATION_MS);
            server.getPlayerList().broadcastSystemMessage(Component.literal("[Server Boost] +" + BuffManager.percent(def.amount) + " Ultra Beast Spawn Chance is now active!").withStyle(ChatFormatting.LIGHT_PURPLE), false);
            return true;
        }
        ServerBuffManager.activateAndAnnounce(server, "cash_" + def.id, def.type, def.amount, DEFAULT_DURATION_MS);
        return true;
    }

    public static final class Def {
        public final String id, name, lore;
        public final BuffType type;
        public final double amount;
        Def(String id, String name, BuffType type, double amount, String lore){this.id=id;this.name=name;this.type=type;this.amount=amount;this.lore=lore;}
        public String cleanName() { return name == null ? id : name.replaceAll("§.", ""); }
    }
}
