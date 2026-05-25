package com.champutils.territory;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TerritoryNpcInteractionListener {
    private static final Map<UUID, Long> LAST_CLICK = new ConcurrentHashMap<>();
    private TerritoryNpcInteractionListener() {}

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer) || !(entity instanceof NPCEntity)) return InteractionResult.PASS;
            TerritoryRepository.Territory territory = TerritoryNpcManager.territoryFor(entity);
            if (territory == null) return InteractionResult.PASS;
            long now = System.currentTimeMillis();
            Long prev = LAST_CLICK.get(serverPlayer.getUUID());
            if (prev != null && now - prev < 800L) return InteractionResult.SUCCESS;
            LAST_CLICK.put(serverPlayer.getUUID(), now);
            if (!TerritoryNpcManager.canUseNpc(serverPlayer, territory)) {
                serverPlayer.sendSystemMessage(Component.literal("This NPC belongs to another territory.").withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            if (territory.ownerType == TerritoryRepository.OwnerType.GUILD) {
                com.champutils.guild.GuildNpcMenu.open(serverPlayer, territory);
            } else {
                TerritoryMenus.openNpcManage(serverPlayer, territory);
            }
            return InteractionResult.SUCCESS;
        });
    }
}
