package com.champutils.pokefan;

import com.champutils.battle.BattleStateManager;
import com.champutils.economy.EconomyManager;
import com.champutils.matchmaking.PokemonIconUtil;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.menu.MenuUtil;
import com.champutils.xplock.XpLockManager;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.pokemon.evolution.PreEvolution;
import com.cobblemon.mod.common.api.pokemon.requirement.Requirement;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.requirements.LevelRequirement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Pokefan NPC menu: party XP locks and safe one-level delevel purchases. */
public final class PokefanMenu {
    private static final long DELEVEL_COST = EconomyManager.wholeCreditsToCents(500L);
    private static final int CANDY_COST = 3;
    private static final Item XS_CANDY = BuiltInRegistries.ITEM.get(ResourceLocation.parse("cobblemon:exp_candy_xs"));
    private static final Set<UUID> PURCHASES_IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private PokefanMenu() {}

    public static void open(ServerPlayer player) {
        if (player == null) return;
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Pokefan Services"));
        MenuUtil.fillBordersForced(gui, 11, 15);

        gui.setSlot(11, new GuiElementBuilder(Items.EXPERIENCE_BOTTLE)
                .hideDefaultTooltip()
                .setName(Component.literal("§bXP Lock"))
                .addLoreLine(Component.literal("§7Choose party Pokémon and toggle"))
                .addLoreLine(Component.literal("§7whether they can gain experience."))
                .addLoreLine(Component.literal("§eClick to manage"))
                .setCallback((slot, click, action) -> openXpLocks(player)));

        gui.setSlot(15, new GuiElementBuilder(Items.REDSTONE)
                .hideDefaultTooltip()
                .setName(Component.literal("§cDelevel My Pokémon"))
                .addLoreLine(Component.literal("§7Safely lowers one party Pokémon"))
                .addLoreLine(Component.literal("§7by exactly one level."))
                .addLoreLine(Component.literal("§7Cost: §6500 Credits §7+ §b3 Exp. Candy XS"))
                .addLoreLine(Component.literal("§7Evolution level floors are enforced."))
                .addLoreLine(Component.literal("§eClick to choose"))
                .setCallback((slot, click, action) -> openDelevelSelection(player)));
        gui.open();
    }

    public static void openXpLocks(ServerPlayer player) {
        PartyStore party = party(player);
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("XP Locks"));
        MenuUtil.fillBordersForced(gui, 10, 11, 12, 14, 15, 16, 22);
        int[] slots = {10, 11, 12, 14, 15, 16};
        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party == null ? null : party.get(i);
            if (pokemon == null) continue;
            boolean locked = XpLockManager.isLocked(pokemon);
            ItemStack icon = PokemonIconUtil.getIcon(pokemon, i + 1, locked);
            GuiElementBuilder builder = new GuiElementBuilder(icon)
                    .hideDefaultTooltip()
                    .setName(Component.literal((locked ? "§c" : "§a") + pokemon.getDisplayName(true).getString()))
                    .addLoreLine(Component.literal("§7Party slot: §f" + (i + 1)))
                    .addLoreLine(Component.literal("§7Level: §f" + pokemon.getLevel()))
                    .addLoreLine(Component.literal("§7XP gain: " + (locked ? "§cLOCKED" : "§aENABLED")))
                    .addLoreLine(Component.literal("§eClick to " + (locked ? "unlock" : "lock")));
            final UUID pokemonId = pokemon.getUuid();
            builder.setCallback((slot, click, action) -> {
                Pokemon current = findPartyPokemon(player, pokemonId);
                if (current == null) {
                    player.sendSystemMessage(Component.literal("That Pokémon is no longer in your party.").withStyle(ChatFormatting.RED));
                    openXpLocks(player);
                    return;
                }
                boolean nowLocked = XpLockManager.toggle(current);
                player.sendSystemMessage(Component.literal(current.getDisplayName(true).getString() + " XP lock is now " + (nowLocked ? "ON" : "OFF") + ".")
                        .withStyle(nowLocked ? ChatFormatting.RED : ChatFormatting.GREEN));
                openXpLocks(player);
            });
            gui.setSlot(slots[i], builder);
        }
        gui.setSlot(22, new GuiElementBuilder(Items.BARRIER).hideDefaultTooltip().setName(Component.literal("§cBack"))
                .setCallback((slot, click, action) -> open(player)));
        gui.open();
    }

    public static void openDelevelSelection(ServerPlayer player) {
        PartyStore party = party(player);
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Choose Pokémon to Delevel"));
        MenuUtil.fillBordersForced(gui, 10, 11, 12, 14, 15, 16, 22);
        int[] slots = {10, 11, 12, 14, 15, 16};
        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party == null ? null : party.get(i);
            if (pokemon == null) continue;
            int floor = minimumSafeLevel(pokemon);
            boolean eligible = pokemon.getLevel() > floor;
            ItemStack icon = PokemonIconUtil.getIcon(pokemon, i + 1, false);
            GuiElementBuilder builder = new GuiElementBuilder(icon).hideDefaultTooltip()
                    .setName(Component.literal((eligible ? "§f" : "§c") + pokemon.getDisplayName(true).getString()))
                    .addLoreLine(Component.literal("§7Current level: §f" + pokemon.getLevel()))
                    .addLoreLine(Component.literal("§7Minimum safe level: §f" + floor));
            if (eligible) {
                builder.addLoreLine(Component.literal("§7New level: §f" + (pokemon.getLevel() - 1)))
                        .addLoreLine(Component.literal("§7Cost: §6500 Credits §7+ §b3 Exp. Candy XS"))
                        .addLoreLine(Component.literal("§eClick to continue"));
                final UUID pokemonId = pokemon.getUuid();
                builder.setCallback((slot, click, action) -> openConfirmation(player, pokemonId));
            } else {
                builder.addLoreLine(Component.literal("§cCannot be lowered further."));
            }
            gui.setSlot(slots[i], builder);
        }
        gui.setSlot(22, new GuiElementBuilder(Items.BARRIER).hideDefaultTooltip().setName(Component.literal("§cBack"))
                .setCallback((slot, click, action) -> open(player)));
        gui.open();
    }

    private static void openConfirmation(ServerPlayer player, UUID pokemonId) {
        Pokemon pokemon = findPartyPokemon(player, pokemonId);
        if (pokemon == null) { openDelevelSelection(player); return; }
        int floor = minimumSafeLevel(pokemon);
        if (pokemon.getLevel() <= floor) { openDelevelSelection(player); return; }

        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Confirm Delevel"));
        MenuUtil.fillBordersForced(gui, 11, 13, 15);
        gui.setSlot(13, new GuiElementBuilder(PokemonIconUtil.getIcon(pokemon, 1, false)).hideDefaultTooltip()
                .setName(Component.literal("§f" + pokemon.getDisplayName(true).getString()))
                .addLoreLine(Component.literal("§7Level §f" + pokemon.getLevel() + " §7→ §f" + (pokemon.getLevel() - 1)))
                .addLoreLine(Component.literal("§7Minimum safe level: §f" + floor))
                .addLoreLine(Component.literal("§7Cost: §6500 Credits §7+ §b3 Exp. Candy XS")));
        gui.setSlot(11, new GuiElementBuilder(Items.LIME_CONCRETE).hideDefaultTooltip().setName(Component.literal("§aConfirm"))
                .addLoreLine(Component.literal("§7This lowers the Pokémon by one level."))
                .setCallback((slot, click, action) -> purchaseDelevel(player, pokemonId)));
        gui.setSlot(15, new GuiElementBuilder(Items.RED_CONCRETE).hideDefaultTooltip().setName(Component.literal("§cCancel"))
                .setCallback((slot, click, action) -> openDelevelSelection(player)));
        gui.open();
    }

    private static void purchaseDelevel(ServerPlayer player, UUID pokemonId) {
        player.closeContainer();
        if (!PURCHASES_IN_FLIGHT.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal("A delevel purchase is already processing.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        String username = player.getName().getString();
        Pokemon pokemon = findPartyPokemon(player, pokemonId);
        String error = validate(player, pokemon);
        if (error != null) {
            PURCHASES_IN_FLIGHT.remove(player.getUUID());
            player.sendSystemMessage(Component.literal(error).withStyle(ChatFormatting.RED));
            return;
        }

        EconomyManager.withdrawAsync(EconomyManager.operationId("pokefan-delevel", profileId, pokemonId, UUID.randomUUID()), profileId, username, DELEVEL_COST, "Pokefan Pokémon delevel")
                .whenComplete((result, throwable) -> player.server.execute(() -> {
                    try {
                        if (player.hasDisconnected()) {
                            if (throwable == null && result != null && result.success) refund(profileId, username, "Pokefan delevel disconnect rollback");
                            return;
                        }
                        if (throwable != null || result == null || !result.success) {
                            player.sendSystemMessage(Component.literal(result == null ? "The credit transaction failed." : (result.error == null ? "The credit transaction failed." : result.error)).withStyle(ChatFormatting.RED));
                            return;
                        }
                        Pokemon current = findPartyPokemon(player, pokemonId);
                        String recheck = !profileId.equals(PlayerProfileManager.activeProfileId(player))
                                ? "Your active profile changed while the purchase was processing."
                                : validate(player, current);
                        if (recheck != null) {
                            refund(profileId, username, "Pokefan delevel rollback");
                            player.sendSystemMessage(Component.literal(recheck + " Your 500 Credits are being refunded.").withStyle(ChatFormatting.RED));
                            return;
                        }
                        if (!removeItems(player, XS_CANDY, CANDY_COST)) {
                            refund(profileId, username, "Pokefan delevel candy rollback");
                            player.sendSystemMessage(Component.literal("You no longer have 3 Exp. Candy XS. Your 500 Credits are being refunded.").withStyle(ChatFormatting.RED));
                            return;
                        }
                        int newLevel = current.getLevel() - 1;
                        current.setExperienceAndUpdateLevel(current.getExperienceToLevel(newLevel));
                        if (current.getLevel() != newLevel) {
                            current.setLevel(newLevel);
                        }
                        player.sendSystemMessage(Component.literal(current.getDisplayName(true).getString() + " was safely lowered to level " + newLevel + ".").withStyle(ChatFormatting.GREEN));
                    } finally {
                        PURCHASES_IN_FLIGHT.remove(player.getUUID());
                    }
                }));
    }

    private static String validate(ServerPlayer player, Pokemon pokemon) {
        if (player == null || pokemon == null) return "That Pokémon is no longer in your party.";
        if (BattleStateManager.isInBattle(player)) return "You cannot delevel a Pokémon during a battle.";
        int floor = minimumSafeLevel(pokemon);
        if (pokemon.getLevel() <= floor) return "This Pokémon cannot be lowered below level " + floor + ".";
        if (EconomyManager.getBalance(player) < DELEVEL_COST) return "You need 500 Credits.";
        if (countItem(player, XS_CANDY) < CANDY_COST) return "You need 3 Exp. Candy XS.";
        return null;
    }

    /**
     * Returns the direct evolution's highest level requirement that actually resolves to the current species/form.
     * Non-level evolutions have no artificial floor beyond level 1.
     */
    public static int minimumSafeLevel(Pokemon pokemon) {
        if (pokemon == null) return 1;
        PreEvolution pre = pokemon.getPreEvolution();
        if (pre == null) return 1;
        int floor = 1;
        Set<Evolution> candidates = new HashSet<>();
        Species species = pre.getSpecies();
        FormData form = pre.getForm();
        if (species != null && species.getEvolutions() != null) candidates.addAll(species.getEvolutions());
        if (form != null && form.getEvolutions() != null) candidates.addAll(form.getEvolutions());
        for (Evolution evolution : candidates) {
            try {
                if (evolution == null || evolution.getResult() == null || !evolution.getResult().matches(pokemon)) continue;
                for (Requirement requirement : evolution.getRequirements()) {
                    if (requirement instanceof LevelRequirement levelRequirement) {
                        floor = Math.max(floor, levelRequirement.getMinLevel());
                    }
                }
            } catch (Throwable ignored) {}
        }
        return Math.max(1, Math.min(100, floor));
    }

    private static PartyStore party(ServerPlayer player) {
        return player == null ? null : Cobblemon.INSTANCE.getStorage().getParty(player);
    }

    private static Pokemon findPartyPokemon(ServerPlayer player, UUID pokemonId) {
        PartyStore party = party(player);
        if (party == null || pokemonId == null) return null;
        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon != null && pokemonId.equals(pokemon.getUuid())) return pokemon;
        }
        return null;
    }

    private static int countItem(ServerPlayer player, Item item) {
        if (player == null || item == null || item == Items.AIR) return 0;
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static boolean removeItems(ServerPlayer player, Item item, int amount) {
        if (countItem(player, item) < amount) return false;
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || !stack.is(item)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        player.getInventory().setChanged();
        return remaining == 0;
    }

    private static void refund(UUID profileId, String username, String reason) {
        EconomyManager.depositAsync(EconomyManager.operationId("pokefan-delevel-refund", profileId, UUID.randomUUID()), profileId, username, DELEVEL_COST, reason);
    }
}
