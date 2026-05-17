package com.champutils.dungeon;

import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.profession.ProfessionType;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

public final class DungeonKeyDropManager {

    private static final Random RANDOM = new Random();

    private DungeonKeyDropManager() {
    }

    public static void rollKeyDrop(ServerPlayer player, ProfessionType profession) {
        if (player == null || profession == null) return;
        if (!DungeonKeyDropConfig.enabled) return;

        DungeonKeyDropConfig.ProfessionDropTable table = DungeonKeyDropConfig.professionDrops.get(profession.name());
        if (table == null || !table.enabled || table.drops == null || table.drops.isEmpty()) return;

        for (DungeonKeyDropConfig.KeyDropEntry entry : table.drops) {
            if (entry == null || entry.keyId == null || entry.keyId.isBlank() || entry.chance <= 0.0D) continue;
            if (RANDOM.nextDouble() >= entry.chance) continue;

            int min = Math.max(1, entry.minAmount);
            int max = Math.max(min, entry.maxAmount);
            int amount = min + RANDOM.nextInt((max - min) + 1);

            giveKey(player, entry.keyId, amount);
        }
    }

    public static boolean giveKey(ServerPlayer player, String keyId, int amount) {
        if (player == null || keyId == null || keyId.isBlank() || amount <= 0) return false;

        int remaining = amount;
        boolean gaveAny = false;

        while (remaining > 0) {
            int stackAmount = Math.min(64, remaining);
            ItemStack stack = DungeonKeyManager.createKeyStack(keyId, stackAmount);
            if (stack.isEmpty()) return gaveAny;

            boolean inserted = player.getInventory().add(stack);
            if (!inserted && !stack.isEmpty()) {
                player.drop(stack, false);
            }

            gaveAny = true;
            remaining -= stackAmount;
        }

        if (gaveAny) {
            announce(player, keyId, amount);
        }

        return gaveAny;
    }

    private static void announce(ServerPlayer player, String keyId, int amount) {
        if (!DungeonKeyDropConfig.announceDrops) return;
        if (!ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) return;

        DungeonKeyConfig.KeyData data = DungeonKeyConfig.KEYS.get(keyId);
        String displayName = data == null || data.displayName == null || data.displayName.isBlank()
                ? keyId
                : data.displayName;

        DungeonRarity rarity = DungeonRarity.parse(data == null ? "COMMON" : data.rarity);

        player.displayClientMessage(
                Component.literal("Dungeon Key Drop! ")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal(displayName + " x" + amount).withStyle(rarity.getColor())),
                true
        );

        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS,
                1.0F,
                1.25F
        );
    }
}
