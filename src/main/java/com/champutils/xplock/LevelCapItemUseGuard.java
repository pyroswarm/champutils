package com.champutils.xplock;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

public final class LevelCapItemUseGuard {
    private static boolean registered = false;
    private LevelCapItemUseGuard() {}
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.pass(player.getItemInHand(hand));
            ItemStack stack = serverPlayer.getItemInHand(hand);
            String id = stack.getItem().builtInRegistryHolder().key().location().toString();
            if (!id.equals("cobblemon:rare_candy") && !id.startsWith("cobblemon:exp_candy_")) {
                return InteractionResultHolder.pass(stack);
            }
            com.champutils.commands.LevelCapCommand.applyStoredCap(serverPlayer);
            PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(serverPlayer);
            for (int i = 0; i < 6; i++) {
                Pokemon pokemon = party == null ? null : party.get(i);
                int cap = XpLockManager.getLevelCap(pokemon);
                if (cap > 0 && pokemon != null && pokemon.getLevel() >= cap) {
                    serverPlayer.sendSystemMessage(Component.literal("Your party levelcap blocks EXP candy/rare candy while a capped Pokémon is in your party. Use /levelcap off or raise the cap.").withStyle(ChatFormatting.RED));
                    return InteractionResultHolder.fail(stack);
                }
            }
            return InteractionResultHolder.pass(stack);
        });
    }
}
