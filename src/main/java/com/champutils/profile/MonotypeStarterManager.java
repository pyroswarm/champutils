package com.champutils.profile;

import com.champutils.database.DatabaseManager;
import com.champutils.matchmaking.PokemonIconUtil;
import com.champutils.menu.MenuUtil;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes;
import com.cobblemon.mod.common.pokemon.Pokemon;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MonotypeStarterManager {
    private MonotypeStarterManager() {}

    private record StarterChoice(String species, String display) {}

    private static final int[] MANY_SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    private static final int[] THREE_SLOTS = {11,13,15};

    private static final Map<String, StarterChoice[]> STARTERS = Map.ofEntries(
            Map.entry("fire", choices("charmander", "cyndaquil", "torchic", "chimchar", "tepig", "fennekin", "litten", "scorbunny", "fuecoco")),
            Map.entry("water", choices("squirtle", "totodile", "mudkip", "piplup", "oshawott", "froakie", "popplio", "sobble", "quaxly")),
            Map.entry("grass", choices("bulbasaur", "chikorita", "treecko", "turtwig", "snivy", "chespin", "rowlet", "grookey", "sprigatito")),
            Map.entry("normal", choices("eevee", "zigzagoon", "bidoof")),
            Map.entry("electric", choices("pichu", "shinx", "yamper")),
            Map.entry("ice", choices("swinub", "snorunt", "bergmite")),
            Map.entry("fighting", choices("machop", "makuhita", "riolu")),
            Map.entry("poison", choices("nidoran-f", "zubat", "croagunk")),
            Map.entry("ground", choices("sandshrew", "trapinch", "drilbur")),
            Map.entry("flying", choices("pidgey", "starly", "rookidee")),
            Map.entry("psychic", choices("abra", "ralts", "gothita")),
            Map.entry("bug", choices("caterpie", "venipede", "grubbin")),
            Map.entry("rock", choices("geodude", "aron", "roggenrola")),
            Map.entry("ghost", choices("gastly", "duskull", "litwick")),
            Map.entry("dragon", choices("dratini", "bagon", "gible")),
            Map.entry("dark", choices("poochyena", "murkrow", "zorua")),
            Map.entry("steel", choices("magnemite", "aron", "klink")),
            Map.entry("fairy", choices("cleffa", "togepi", "flabebe"))
    );


    public static void register() {
        CobblemonEvents.STARTER_CHOSEN.subscribe(event -> {
            if (event == null || event.getPlayer() == null) return;
            ServerPlayer player = event.getPlayer();
            ProfileGameMode mode = PlayerProfileManager.gameMode(player);
            if (mode == ProfileGameMode.MONOTYPE) {
                String required = normalize(PlayerProfileManager.monotypeType(player));
                event.cancel();
                player.sendSystemMessage(Component.literal("Monotype profiles use the custom " + required + " starter menu instead.").withStyle(ChatFormatting.RED));
                player.server.execute(() -> open(player));
                return;
            }

            UUID profileId = PlayerProfileManager.activeProfileId(player);
            if (profileId != null && !profileId.equals(player.getUUID())) {
                Pokemon chosen = event.getPokemon();
                String species = chosen == null || chosen.getSpecies() == null ? "unknown" : chosen.getSpecies().getResourceIdentifier().toString();
                player.server.execute(() -> {
                    try {
                        if (chosen != null && !partyContains(player, chosen.getUuid())) {
                            addStarterToProfileParty(player, chosen);
                        }
                        markClaimed(profileId, species);
                        syncCobblemonStarterState(player);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });
    }

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure monotype starter schema", connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("create table if not exists profile_starter_claims (" +
                        "profile_id uuid primary key references player_profiles(id) on delete cascade, " +
                        "starter_species text not null, claimed_at timestamptz not null default now())");
            }
        });
    }

    public static void handleProfileLoaded(ServerPlayer player) {
        if (player == null || player.server == null) return;
        player.server.execute(() -> {
            syncCobblemonStarterState(player);
            if (!needsStarter(player)) return;
            open(player);
        });
    }

    private static void syncCobblemonStarterState(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled()) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null || profileId.equals(player.getUUID())) return;
        try {
            boolean claimed = hasClaimed(profileId);
            boolean partyEmpty = isPartyEmpty(player);
            boolean monotype = PlayerProfileManager.gameMode(player) == ProfileGameMode.MONOTYPE;

            var playerData = Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(player);
            playerData.setStarterLocked(monotype);
            playerData.setStarterSelected(claimed || !partyEmpty);
            playerData.setStarterPrompted(claimed || !partyEmpty);
            Cobblemon.INSTANCE.getPlayerDataManager().saveSingle(playerData, PlayerInstancedDataStoreTypes.INSTANCE.getGENERAL());
            playerData.sendToPlayer(player);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static boolean needsStarter(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled()) return false;
        if (PlayerProfileManager.gameMode(player) != ProfileGameMode.MONOTYPE) return false;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null || profileId.equals(player.getUUID())) return false;
        if (hasClaimed(profileId)) return false;
        return isPartyEmpty(player);
    }

    public static void open(ServerPlayer player) {
        if (player == null) return;
        String type = normalize(PlayerProfileManager.monotypeType(player));
        StarterChoice[] choices = STARTERS.get(type);
        if (choices == null || choices.length == 0) {
            player.sendSystemMessage(Component.literal("No monotype starter pool is configured for " + type + ".").withStyle(ChatFormatting.RED));
            return;
        }

        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal(cap(type) + " Starter"));
        int[] slots = choices.length <= 3 ? THREE_SLOTS : MANY_SLOTS;

        for (int i = 0; i < choices.length && i < slots.length; i++) {
            StarterChoice choice = choices[i];
            ItemStack icon = PokemonIconUtil.createPokemonIcon(choice.species(), false, "cobblemon:poke_ball", false);
            GuiElementBuilder builder = new GuiElementBuilder(icon)
                    .hideDefaultTooltip()
                    .setName(Component.literal(choice.display()).withStyle(ChatFormatting.AQUA))
                    .addLoreLine(Component.literal("Level 5 " + cap(type) + " starter").withStyle(ChatFormatting.GRAY))
                    .addLoreLine(Component.literal("Click to choose this Pokémon.").withStyle(ChatFormatting.YELLOW))
                    .setCallback((index, clickType, action) -> claim(player, choice));
            gui.setSlot(slots[i], builder);
        }

        gui.setSlot(49, new GuiElementBuilder(Items.BARRIER)
                .hideDefaultTooltip()
                .setName(Component.literal("Choose a starter to continue").withStyle(ChatFormatting.RED))
                .addLoreLine(Component.literal("Monotype profiles do not use Cobblemon's normal starter screen.").withStyle(ChatFormatting.GRAY)));
        MenuUtil.fillBordersForced(gui, concat(slots, 49));
        gui.open();
    }

    private static void claim(ServerPlayer player, StarterChoice choice) {
        if (player == null || choice == null) return;
        if (!needsStarter(player)) {
            player.sendSystemMessage(Component.literal("This profile already has a starter or Pokémon in its party.").withStyle(ChatFormatting.RED));
            return;
        }
        String required = normalize(PlayerProfileManager.monotypeType(player));
        try {
            Pokemon pokemon = PokemonProperties.Companion.parse("species=\"cobblemon:" + choice.species() + "\" level=5").create();
            if (!ProfileRestrictions.hasType(pokemon, required)) {
                player.sendSystemMessage(Component.literal("That starter is not valid for your " + required + " monotype profile.").withStyle(ChatFormatting.RED));
                return;
            }
            boolean added = addStarterToProfileParty(player, pokemon);
            if (!added) {
                player.sendSystemMessage(Component.literal("Could not add starter. Make sure your party has room.").withStyle(ChatFormatting.RED));
                return;
            }
            markClaimed(PlayerProfileManager.activeProfileId(player), choice.species());
            syncCobblemonStarterState(player);
            try {
                var playerData = Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(player);
                playerData.setStarterSelected(true);
                playerData.setStarterUUID(pokemon.getUuid());
                Cobblemon.INSTANCE.getPlayerDataManager().saveSingle(playerData, PlayerInstancedDataStoreTypes.INSTANCE.getGENERAL());
                playerData.sendToPlayer(player);
            } catch (Throwable ignored) {}
            try { pokemon.heal(); } catch (Throwable ignored) {}
            player.sendSystemMessage(Component.literal("You chose " + choice.display() + " as your " + cap(required) + " starter!").withStyle(ChatFormatting.GREEN));
            player.closeContainer();
        } catch (Throwable t) {
            t.printStackTrace();
            player.sendSystemMessage(Component.literal("Could not create that starter. Check console logs.").withStyle(ChatFormatting.RED));
        }
    }

    private static boolean addStarterToProfileParty(ServerPlayer player, Pokemon pokemon) {
        if (player == null || pokemon == null) return false;
        try {
            var party = Cobblemon.INSTANCE.getStorage().getParty(player);
            if (party == null) return false;
            if (partyContains(player, pokemon.getUuid())) return true;
            boolean added = party.add(pokemon);
            if (!added) return false;
            try { pokemon.heal(); } catch (Throwable ignored) {}
            try { party.sendTo(player); } catch (Throwable ignored) {}
            try { Cobblemon.INSTANCE.getStorage().onPlayerDataSync(player); } catch (Throwable ignored) {}
            try { CobblemonProfileStorageBridge.forceSaveActiveProfileStores(player); } catch (Throwable ignored) {}
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    private static boolean partyContains(ServerPlayer player, UUID pokemonUuid) {
        if (player == null || pokemonUuid == null) return false;
        try {
            var party = Cobblemon.INSTANCE.getStorage().getParty(player);
            if (party == null) return false;
            for (Pokemon pokemon : party) {
                if (pokemon != null && pokemonUuid.equals(pokemon.getUuid())) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isPartyEmpty(ServerPlayer player) {
        try {
            var party = Cobblemon.INSTANCE.getStorage().getParty(player);
            if (party == null) return true;
            for (Pokemon pokemon : party) if (pokemon != null) return false;
        } catch (Throwable ignored) {}
        return true;
    }

    private static boolean hasClaimed(UUID profileId) {
        if (profileId == null) return false;
        try (var ps = DatabaseManager.getConnection().prepareStatement("select 1 from profile_starter_claims where profile_id = ? limit 1")) {
            ps.setObject(1, profileId);
            try (var rs = ps.executeQuery()) { return rs.next(); }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void markClaimed(UUID profileId, String species) throws Exception {
        if (profileId == null || profileId.equals(new UUID(0L, 0L))) return;
        try (Connection connection = DatabaseManager.getConnection();
             var ps = connection.prepareStatement("insert into profile_starter_claims (profile_id, starter_species, claimed_at) values (?, ?, now()) on conflict (profile_id) do update set starter_species = excluded.starter_species, claimed_at = now()")) {
            ps.setObject(1, profileId);
            ps.setString(2, species);
            ps.executeUpdate();
        }
    }

    private static StarterChoice[] choices(String... species) {
        StarterChoice[] out = new StarterChoice[species.length];
        for (int i = 0; i < species.length; i++) out[i] = new StarterChoice(species[i], display(species[i]));
        return out;
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String cap(String s) {
        if (s == null || s.isBlank()) return "";
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String display(String species) {
        String s = species.replace("cobblemon:", "").replace("-", " ");
        String[] parts = s.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(cap(part));
        }
        return out.toString();
    }

    private static int[] concat(int[] base, int extra) {
        int[] out = new int[base.length + 1];
        System.arraycopy(base, 0, out, 0, base.length);
        out[base.length] = extra;
        return out;
    }
}
