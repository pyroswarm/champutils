package com.champutils.dex;

import com.champutils.emblem.EmblemManager;
import com.champutils.hunt.PokemonHuntReflection;
import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.util.CobblemonEventReflection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class SpecialCatchAnnouncementListener {
    private static boolean registered = false;
    private SpecialCatchAnnouncementListener() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Object observable = getCaptureObservable(eventsClass);
            if (observable == null) return;
            CobblemonEventReflection.subscribe(observable, event -> {
                try {
                    ServerPlayer player = PokemonHuntReflection.extractPlayer(event);
                    Object pokemon = PokemonHuntReflection.extractPokemon(event);
                    if (player == null || pokemon == null || player.server == null) return;
                    String species = speciesId(pokemon);
                    boolean shiny = isShiny(pokemon);
                    boolean legendary = EmblemManager.isLegendary(species);
                    boolean ultra = EmblemManager.isUltraBeast(species);
                    boolean paradox = EmblemManager.isParadox(species);
                    boolean mythic = isMythical(pokemon, species);
                    if (!shiny && !legendary && !ultra && !paradox && !mythic) return;
                    String kind = shiny ? "Shiny" : mythic ? "Mythical" : legendary ? "Legendary" : ultra ? "Ultra Beast" : "Paradox";
                    Component hover = Component.literal("Trainer: " + player.getGameProfile().getName() + "\nPokémon: " + displayName(pokemon) + "\nKind: " + kind + "\nLevel: " + level(pokemon) + "\nShiny: " + (shiny ? "Yes" : "No"));
                    Component msg = Component.literal("✦ ").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(player.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(" caught a ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal((shiny ? "Shiny " : "") + displayName(pokemon)).withStyle(shiny ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD)
                                    .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))))
                            .append(Component.literal("! Hover to inspect.").withStyle(ChatFormatting.GRAY));
                    ProfessionNotificationSettings.sendBroadcast(player.server, msg);
                    if (shiny) {
                        try {
                            player.server.execute(() -> player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "pokeshout"));
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private static Object getCaptureObservable(Class<?> eventsClass) {
        for (String name : new String[]{"POKEMON_CAPTURED","POKEMON_CAPTURED_EVENT","POKEMON_CAPTURED_POST","POKEMON_CAUGHT","POKEMON_CATCH_SUCCEEDED"}) {
            try { Field f = eventsClass.getField(name); Object v = f.get(null); if (v != null) return v; } catch (Throwable ignored) {}
        }
        for (Field f : eventsClass.getFields()) {
            String lower = f.getName().toLowerCase(Locale.ROOT);
            if ((!lower.contains("capture") && !lower.contains("caught") && !lower.contains("catch")) || lower.contains("pre") || lower.contains("attempt") || lower.contains("fail")) continue;
            try { Object v = f.get(null); if (v != null) return v; } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String speciesId(Object pokemon) {
        Object species = read(pokemon, "getSpecies");
        Object id = read(species, "getResourceIdentifier");
        if (id == null) id = read(species, "getName");
        String out = id == null ? "" : id.toString();
        int colon = out.indexOf(':');
        if (colon >= 0) out = out.substring(colon + 1);
        return out.toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
    }

    private static String displayName(Object pokemon) {
        Object name = read(pokemon, "getDisplayName", boolean.class, true);
        if (name instanceof Component c) return c.getString();
        Object species = read(pokemon, "getSpecies");
        Object n = read(species, "getName");
        return n == null ? "Pokémon" : n.toString();
    }

    private static int level(Object pokemon) { Object l = read(pokemon, "getLevel"); return l instanceof Number n ? n.intValue() : 0; }
    private static boolean isShiny(Object pokemon) { Object v = read(pokemon, "getShiny"); if (v == null) v = read(pokemon, "isShiny"); return v instanceof Boolean b && b; }
    private static boolean isMythical(Object pokemon, String species) {
        Object sp = read(pokemon, "getSpecies");
        for (String m : new String[]{"getMythical","isMythical"}) { Object v = read(sp, m); if (v instanceof Boolean b && b) return true; }
        return species.contains("mew") || species.contains("celebi") || species.contains("jirachi") || species.contains("deoxys") || species.contains("darkrai") || species.contains("shaymin") || species.contains("arceus") || species.contains("victini") || species.contains("keldeo") || species.contains("meloetta") || species.contains("genesect") || species.contains("diancie") || species.contains("hoopa") || species.contains("volcanion") || species.contains("magearna") || species.contains("marshadow") || species.contains("zeraora") || species.contains("meltan") || species.contains("melmetal") || species.contains("zarude") || species.contains("pecharunt");
    }
    private static Object read(Object target, String method, Object... args) {
        if (target == null) return null;
        try {
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) types[i] = args[i] instanceof Boolean ? boolean.class : args[i].getClass();
            Method m = target.getClass().getMethod(method, types);
            return m.invoke(target, args);
        } catch (Throwable ignored) { return null; }
    }
}
