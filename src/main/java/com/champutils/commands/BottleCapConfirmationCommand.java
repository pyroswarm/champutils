package com.champutils.commands;

import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.breeding.PokemonBreedability;
import com.champutils.item.BottleCapItemManager;
import com.champutils.item.BottleCapItemManager.CapType;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Party-selection and confirmation flow for ChampUtils bottle caps and Cobblemon Ability Patches. */
public final class BottleCapConfirmationCommand {
    private static final Set<UUID> OPEN = ConcurrentHashMap.newKeySet();
    private static final String ABILITY_PATCH_ID = "cobblemon:ability_patch";
    private BottleCapConfirmationCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("champutilsbottlecap").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    InteractionHand hand = heldSupportedHand(player);
                    if (hand == null) return 0;
                    openPartySelection(player, hand);
                    return 1;
                })
        ));
        UseItemCallback.EVENT.register((user, world, hand) -> {
            if (!(user instanceof ServerPlayer player) || world.isClientSide()) return InteractionResultHolder.pass(user.getItemInHand(hand));
            ItemStack held = user.getItemInHand(hand);
            if (BottleCapItemManager.typeOf(held) == null && !isAbilityPatch(held)) return InteractionResultHolder.pass(held);
            openPartySelection(player, hand);
            // Consume the interaction so Cobblemon's direct item handler cannot bypass confirmation/unbreedable marking.
            return InteractionResultHolder.success(held);
        });
    }

    private static InteractionHand heldSupportedHand(ServerPlayer player) {
        if (isSupported(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (isSupported(player.getOffhandItem())) return InteractionHand.OFF_HAND;
        return null;
    }

    private static boolean isSupported(ItemStack stack) {
        return BottleCapItemManager.typeOf(stack) != null || isAbilityPatch(stack);
    }

    private static void openPartySelection(ServerPlayer player, InteractionHand hand) {
        if (!OPEN.add(player.getUUID())) return;
        ItemStack held = player.getItemInHand(hand);
        if (!isSupported(held)) { OPEN.remove(player.getUUID()); return; }

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false) {
            @Override public void onClose() { super.onClose(); OPEN.remove(player.getUUID()); }
        };
        gui.setTitle(Component.literal(isAbilityPatch(held) ? "Choose Ability Patch Target" : "Choose Bottle Cap Target"));
        for (int i = 0; i < 6; i++) {
            final int slot = i;
            Pokemon pokemon = AuctionPokemonSerializer.getPartyPokemon(player, slot);
            int menuSlot = 10 + i;
            if (pokemon == null) {
                gui.setSlot(menuSlot, new GuiElementBuilder(Items.GRAY_DYE).hideDefaultTooltip()
                        .setName(Component.literal("§7Empty Party Slot " + (i + 1))));
                continue;
            }
            ItemStack icon;
            try { icon = PokemonItem.from(pokemon, 1); }
            catch (Throwable ignored) { icon = new ItemStack(Items.PLAYER_HEAD); }
            icon.set(DataComponents.CUSTOM_NAME, Component.literal("§f" + pokemon.getDisplayName(true).getString()));
            GuiElementBuilder button = new GuiElementBuilder(icon).hideDefaultTooltip()
                    .addLoreLine(Component.literal("§7Party Slot: §f" + (i + 1)))
                    .addLoreLine(Component.literal("§7Level: §f" + pokemon.getLevel()))
                    .addLoreLine(Component.literal("§cThis use permanently makes it unbreedable."))
                    .addLoreLine(Component.literal("§eClick to review and confirm."));
            button.setCallback((s, c, a) -> openConfirmation(player, hand, slot, pokemon.getUuid()));
            gui.setSlot(menuSlot, button);
        }
        gui.setSlot(22, new GuiElementBuilder(Items.BARRIER).hideDefaultTooltip().setName(Component.literal("§cCancel"))
                .setCallback((s,c,a) -> gui.close()));
        gui.open();
    }

    private static void openConfirmation(ServerPlayer player, InteractionHand hand, int partySlot, UUID expectedPokemon) {
        Pokemon pokemon = AuctionPokemonSerializer.getPartyPokemon(player, partySlot);
        ItemStack held = player.getItemInHand(hand);
        if (pokemon == null || !pokemon.getUuid().equals(expectedPokemon) || !isSupported(held)) {
            player.sendSystemMessage(Component.literal("The Pokémon or held item changed. Nothing was consumed.").withStyle(ChatFormatting.RED));
            player.closeContainer();
            return;
        }
        boolean patch = isAbilityPatch(held);
        CapType capType = BottleCapItemManager.typeOf(held);
        String itemName = patch ? "Ability Patch" : capType.displayName;

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false) {
            @Override public void onClose() { super.onClose(); OPEN.remove(player.getUUID()); }
        };
        gui.setTitle(Component.literal("Confirm " + itemName));
        gui.setSlot(4, new GuiElementBuilder(patch ? Items.NETHER_STAR : Items.EXPERIENCE_BOTTLE).hideDefaultTooltip()
                .setName(Component.literal("§e" + itemName))
                .addLoreLine(Component.literal("§7Target: §f" + pokemon.getDisplayName(true).getString()))
                .addLoreLine(Component.literal("§7Party Slot: §f" + (partySlot + 1)))
                .addLoreLine(patch ? Component.literal("§7Effect: §fUnlock hidden ability") : capEffect(capType))
                .addLoreLine(Component.literal("§cPermanent: Pokémon becomes unbreedable.")));
        gui.setSlot(11, new GuiElementBuilder(Items.LIME_CONCRETE).hideDefaultTooltip().setName(Component.literal("§aConfirm and Use"))
                .setCallback((s,c,a) -> apply(player, hand, partySlot, expectedPokemon, patch, capType, gui)));
        gui.setSlot(15, new GuiElementBuilder(Items.RED_CONCRETE).hideDefaultTooltip().setName(Component.literal("§cCancel"))
                .addLoreLine(Component.literal("§7The item will not be consumed."))
                .setCallback((s,c,a) -> gui.close()));
        gui.open();
    }

    private static void apply(ServerPlayer player, InteractionHand hand, int partySlot, UUID expectedPokemon, boolean patch, CapType expectedCap, SimpleGui gui) {
        Pokemon pokemon = AuctionPokemonSerializer.getPartyPokemon(player, partySlot);
        ItemStack held = player.getItemInHand(hand);
        CapType actualCap = BottleCapItemManager.typeOf(held);
        if (pokemon == null || !pokemon.getUuid().equals(expectedPokemon)
                || (patch ? !isAbilityPatch(held) : actualCap != expectedCap)) {
            player.sendSystemMessage(Component.literal("The Pokémon or held item changed. Nothing was consumed.").withStyle(ChatFormatting.RED));
            gui.close();
            return;
        }

        if (patch) {
            PotentialAbility hidden = null;
            for (PotentialAbility potential : pokemon.getForm().getAbilities()) {
                if (potential instanceof HiddenAbility) { hidden = potential; break; }
            }
            if (hidden == null) {
                player.sendSystemMessage(Component.literal(pokemon.getDisplayName(true).getString() + " has no hidden ability. Nothing was consumed.").withStyle(ChatFormatting.RED));
                gui.close();
                return;
            }
            if (pokemon.getAbility() != null && hidden.getTemplate().equals(pokemon.getAbility().getTemplate())) {
                player.sendSystemMessage(Component.literal(pokemon.getDisplayName(true).getString() + " already has its hidden ability. Nothing was consumed.").withStyle(ChatFormatting.RED));
                gui.close();
                return;
            }
            pokemon.updateAbility(hidden.getTemplate().create(false, hidden.getPriority()));
        } else {
            for (Stat stat : stats(expectedCap)) pokemon.getIvs().set(stat, 31);
            PokemonBreedability.refreshIvBaseline(pokemon);
        }

        PokemonBreedability.makeUnbreedable(pokemon, patch ? "Ability Patch used" : "Bottle Cap / Hyper Training used");
        held.shrink(1);
        player.setItemInHand(hand, held);
        player.sendSystemMessage(Component.literal((patch ? "Ability Patch" : expectedCap.displayName) + " applied to "
                + pokemon.getDisplayName(true).getString() + ". It is now permanently unbreedable.").withStyle(ChatFormatting.GREEN));
        gui.close();
    }

    private static List<Stat> stats(CapType type) {
        if (type == null) return List.of();
        return switch (type) {
            case ATTACK -> List.of(Stats.ATTACK);
            case DEFENCE -> List.of(Stats.DEFENCE);
            case HP -> List.of(Stats.HP);
            case SPECIAL_ATTACK -> List.of(Stats.SPECIAL_ATTACK);
            case SPECIAL_DEFENCE -> List.of(Stats.SPECIAL_DEFENCE);
            case SPEED -> List.of(Stats.SPEED);
            case GOLDEN -> List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED);
        };
    }

    private static Component capEffect(CapType type) {
        String stat = switch (type) {
            case ATTACK -> "Attack"; case DEFENCE -> "Defence"; case HP -> "HP";
            case SPECIAL_ATTACK -> "Special Attack"; case SPECIAL_DEFENCE -> "Special Defence"; case SPEED -> "Speed";
            case GOLDEN -> "all six stats";
        };
        return Component.literal("§7Effect: §fSet " + stat + " IV" + (type == CapType.GOLDEN ? "s" : "") + " to 31");
    }

    private static boolean isAbilityPatch(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && ABILITY_PATCH_ID.equals(id.toString().toLowerCase(Locale.ROOT));
    }
}
