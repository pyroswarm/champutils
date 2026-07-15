package com.champutils.buff;

import com.champutils.util.CobblemonEventReflection;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Turns the configured CATCH_CHANCE title buff into an independent chance for a
 * guaranteed capture on each legitimate wild Poké Ball attempt.
 */
public final class CatchChanceGuaranteeListener {
    private static boolean registered;

    private CatchChanceGuaranteeListener() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Object observable = eventsClass.getField("POKE_BALL_CAPTURE_CALCULATED").get(null);
            if (!CobblemonEventReflection.subscribe(observable, CatchChanceGuaranteeListener::handle)) {
                throw new IllegalStateException("No compatible Poké Ball capture-calculated subscription method found.");
            }
            System.out.println("[ChampUtils] Guaranteed catch-chance title buff registered.");
        } catch (Throwable error) {
            System.err.println("[ChampUtils] Failed to register guaranteed catch-chance title buff.");
            error.printStackTrace();
        }
    }

    private static void handle(Object event) {
        ServerPlayer player = value(event, "thrower", "getThrower") instanceof ServerPlayer p ? p : null;
        PokemonEntity entity = value(event, "pokemonEntity", "getPokemonEntity") instanceof PokemonEntity e ? e : null;
        if (player == null || entity == null) return;

        Pokemon pokemon = entity.getPokemon();
        double chance = BuffManager.getCatchChanceBonus(player, pokemon);
        if (chance <= 0.0D) return;

        chance = Math.min(1.0D, chance);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        CaptureContext guaranteed = new CaptureContext(4, true, false);
        if (!setCaptureResult(event, guaranteed)) {
            System.err.println("[ChampUtils] Catch chance proc rolled, but captureResult could not be replaced.");
            return;
        }

        String percent = formatPercent(chance);
        player.sendSystemMessage(
                Component.literal("✦ Catch Chance activated! ")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("Your " + percent + " bonus guaranteed the catch!")
                                .withStyle(ChatFormatting.YELLOW))
        );
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.45F);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.75F);
    }

    private static boolean setCaptureResult(Object event, CaptureContext result) {
        try {
            Method setter = event.getClass().getMethod("setCaptureResult", CaptureContext.class);
            setter.invoke(event, result);
            return true;
        } catch (Throwable ignored) {
        }

        Class<?> type = event.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("captureResult");
                field.setAccessible(true);
                field.set(event, result);
                return true;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private static Object value(Object source, String fieldName, String getterName) {
        if (source == null) return null;
        try {
            Method getter = source.getClass().getMethod(getterName);
            return getter.invoke(source);
        } catch (Throwable ignored) {
        }

        Class<?> type = source.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(source);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static String formatPercent(double chance) {
        double percent = chance * 100.0D;
        if (Math.abs(percent - Math.rint(percent)) < 0.00001D) {
            return String.format(Locale.ROOT, "%.0f%%", percent);
        }
        return String.format(Locale.ROOT, "%.2f%%", percent).replaceAll("0+%$", "%").replaceAll("\\.%$", "%");
    }
}
