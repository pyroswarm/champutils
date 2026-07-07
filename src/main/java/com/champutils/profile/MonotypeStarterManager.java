package com.champutils.profile;

import com.champutils.database.DatabaseManager;
import com.champutils.matchmaking.PokemonIconUtil;
import com.champutils.menu.MenuUtil;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes;
import com.cobblemon.mod.common.pokemon.Pokemon;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.elements.GuiElementInterface;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MonotypeStarterManager {
    private MonotypeStarterManager() {}

    private record StarterChoice(String species, String display) {}

    private static final int[] MANY_SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    private static final int[] THREE_SLOTS = {11,13,15};
    private static final Set<UUID> CLAIMING = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> CLAIM_LOAD_IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Boolean> STARTER_CLAIM_CACHE = new ConcurrentHashMap<>();
    private static final int STARTER_LEVEL = 10;

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
                        markClaimedAsync(profileId, species);
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
        player.server.execute(() -> syncCobblemonStarterState(player, true));
    }

    private static void syncCobblemonStarterState(ServerPlayer player) {
        syncCobblemonStarterState(player, false);
    }

    private static void syncCobblemonStarterState(ServerPlayer player, boolean openIfNeeded) {
        if (player == null || !DatabaseManager.isEnabled()) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null || profileId.equals(player.getUUID())) return;
        boolean partyEmpty = isPartyEmpty(player);
        boolean monotype = PlayerProfileManager.gameMode(player) == ProfileGameMode.MONOTYPE;

        loadClaimedAsync(profileId).whenComplete((claimed, error) -> {
            if (player.server == null) return;
            player.server.execute(() -> {
                if (player.hasDisconnected()) return;
                UUID active = PlayerProfileManager.activeProfileId(player);
                if (active == null || !active.equals(profileId)) return;
                boolean safeClaimed = error == null && Boolean.TRUE.equals(claimed);
                applyCobblemonStarterState(player, safeClaimed, partyEmpty, monotype);
                CobblemonProfileStorageBridge.resyncActiveProfileParty(player, "starter-state");
                if (openIfNeeded && monotype && !safeClaimed && partyEmpty) {
                    open(player);
                }
            });
        });
    }

    private static void applyCobblemonStarterState(ServerPlayer player, boolean claimed, boolean partyEmpty, boolean monotype) {
        try {
            var playerData = Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(player);
            // For monotype profiles, block Cobblemon's normal starter UI only until the
            // custom ChampUtils starter has been claimed. Leaving starterLocked=true after
            // claiming keeps Cobblemon's client-side party controls in a restricted state.
            boolean stillNeedsCustomStarter = monotype && !claimed && partyEmpty;
            playerData.setStarterLocked(stillNeedsCustomStarter);
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
        Boolean claimed = STARTER_CLAIM_CACHE.get(profileId);
        if (claimed == null) {
            syncCobblemonStarterState(player);
            return false;
        }
        if (claimed) return false;
        return isPartyEmpty(player);
    }

    public static void open(ServerPlayer player) {
        if (player == null) return;
        String type = normalize(PlayerProfileManager.monotypeType(player));
        StarterChoice[] choices = STARTERS.get(type);
        if (choices == null || choices.length == 0) {
            player.sendSystemMessage(Component.literal("No monotype starters are available for " + type + " right now.").withStyle(ChatFormatting.RED));
            return;
        }

        LockedStarterGui gui = new LockedStarterGui(player);
        gui.setTitle(Component.literal(cap(type) + " Starter"));
        int[] slots = choices.length <= 3 ? THREE_SLOTS : MANY_SLOTS;

        for (int i = 0; i < choices.length && i < slots.length; i++) {
            StarterChoice choice = choices[i];
            ItemStack icon = PokemonIconUtil.createPokemonIcon(choice.species(), false, "cobblemon:poke_ball", false);
            GuiElementBuilder builder = new GuiElementBuilder(icon)
                    .hideDefaultTooltip()
                    .setName(Component.literal(choice.display()).withStyle(ChatFormatting.AQUA))
                    .addLoreLine(Component.literal("Level " + STARTER_LEVEL + " " + cap(type) + " starter").withStyle(ChatFormatting.GRAY))
                    .addLoreLine(Component.literal("Click to choose this Pokémon.").withStyle(ChatFormatting.YELLOW))
                    .setCallback((index, clickType, action, gui1) -> { gui.claim(choice); gui1.setSlot(index, new GuiElementBuilder(Items.BARRIER).hideDefaultTooltip().setName(Component.literal("Processing...").withStyle(ChatFormatting.YELLOW))); });
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
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null || profileId.equals(player.getUUID())) return;
        if (!CLAIMING.add(profileId)) {
            player.sendSystemMessage(Component.literal("Starter selection is already being processed.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (!needsStarter(player)) {
            CLAIMING.remove(profileId);
            player.sendSystemMessage(Component.literal("This profile already has a starter or Pokémon in its party.").withStyle(ChatFormatting.RED));
            return;
        }
        String required = normalize(PlayerProfileManager.monotypeType(player));
        try {
            if (!isConfiguredStarter(required, choice.species())) {
                player.sendSystemMessage(Component.literal("That starter is not valid for your " + required + " monotype profile.").withStyle(ChatFormatting.RED));
                return;
            }
            Pokemon pokemon = PokemonProperties.Companion.parse("species=\"cobblemon:" + choice.species() + "\" level=" + STARTER_LEVEL).create();
            // The starter menu itself is the source of truth for the first pick. Do not
            // reject a configured starter just because Cobblemon's generated Pokemon/form
            // type reflection is not ready yet. Battle/catch enforcement still uses
            // ProfileRestrictions.hasType after the Pokemon is fully hydrated.
            boolean added = addStarterToProfileParty(player, pokemon);
            if (!added) {
                player.sendSystemMessage(Component.literal("Could not add starter. Make sure your party has room.").withStyle(ChatFormatting.RED));
                return;
            }
            STARTER_CLAIM_CACHE.put(profileId, true);
            markClaimedAsync(profileId, choice.species());
            syncCobblemonStarterState(player);
            try {
                var playerData = Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(player);
                playerData.setStarterLocked(false);
                playerData.setStarterSelected(true);
                playerData.setStarterPrompted(true);
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
        } finally {
            CLAIMING.remove(profileId);
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
            try { CobblemonProfileStorageBridge.forceSaveActiveProfileStores(player); } catch (Throwable ignored) {}
            CobblemonProfileStorageBridge.resyncActiveProfileParty(player, "starter-claim");
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


    private static final class LockedStarterGui extends SimpleGui {
        private final ServerPlayer owner;
        private boolean selected = false;

        private LockedStarterGui(ServerPlayer owner) {
            super(MenuType.GENERIC_9x6, owner, false);
            this.owner = owner;
            this.setLockPlayerInventory(true);
        }

        void claim(StarterChoice choice) {
            if (selected) return;
            selected = true;
            MonotypeStarterManager.claim(owner, choice);
            if (owner != null && MonotypeStarterManager.needsStarter(owner)) {
                selected = false;
            }
        }

        @Override
        public boolean onAnyClick(int index, ClickType type, net.minecraft.world.inventory.ClickType action) {
            // Keep the GUI locked, but do not swallow SGUI element callbacks; starter clicks must fire.
            sanitizeInteraction();
            return super.onAnyClick(index, type, action);
        }

        @Override
        public boolean onClick(int index, ClickType type, net.minecraft.world.inventory.ClickType action, GuiElementInterface element) {
            sanitizeInteraction();
            return super.onClick(index, type, action, element);
        }

        private void sanitizeInteraction() {
            if (owner == null || owner.server == null) return;
            owner.containerMenu.setCarried(ItemStack.EMPTY);
            this.sendGui();
            owner.containerMenu.broadcastChanges();
            owner.inventoryMenu.broadcastChanges();
            owner.server.execute(() -> {
                if (owner.isRemoved() || owner.hasDisconnected()) return;
                owner.containerMenu.setCarried(ItemStack.EMPTY);
                this.sendGui();
                owner.containerMenu.broadcastChanges();
                owner.inventoryMenu.broadcastChanges();
            });
        }

        @Override
        public void onClose() {
            if (selected) return;
            if (owner == null || owner.server == null) return;
            owner.server.execute(() -> {
                if (owner.isRemoved() || owner.hasDisconnected()) return;
                if (MonotypeStarterManager.needsStarter(owner)) {
                    MonotypeStarterManager.open(owner);
                }
            });
        }
    }

    private static java.util.concurrent.CompletableFuture<Boolean> loadClaimedAsync(UUID profileId) {
        if (profileId == null) return java.util.concurrent.CompletableFuture.completedFuture(false);
        Boolean cached = STARTER_CLAIM_CACHE.get(profileId);
        if (cached != null) return java.util.concurrent.CompletableFuture.completedFuture(cached);
        if (!CLAIM_LOAD_IN_FLIGHT.add(profileId)) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        return DatabaseManager.supplyAsync("load monotype starter claim", connection -> hasClaimed(connection, profileId))
                .whenComplete((claimed, error) -> {
                    CLAIM_LOAD_IN_FLIGHT.remove(profileId);
                    if (error == null) STARTER_CLAIM_CACHE.put(profileId, Boolean.TRUE.equals(claimed));
                });
    }

    private static boolean hasClaimed(Connection connection, UUID profileId) {
        if (profileId == null || connection == null) return false;
        try (var ps = connection.prepareStatement("select 1 from profile_starter_claims where profile_id = ? limit 1")) {
            ps.setObject(1, profileId);
            try (var rs = ps.executeQuery()) { return rs.next(); }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void markClaimedAsync(UUID profileId, String species) {
        if (profileId == null || profileId.equals(new UUID(0L, 0L))) return;
        STARTER_CLAIM_CACHE.put(profileId, true);
        DatabaseManager.executeCoalescedAsync("monotype-starter-claim:" + profileId, "mark monotype starter claimed", connection -> markClaimed(connection, profileId, species));
    }

    private static void markClaimed(Connection connection, UUID profileId, String species) throws Exception {
        if (profileId == null || profileId.equals(new UUID(0L, 0L)) || connection == null) return;
        try (var ps = connection.prepareStatement("insert into profile_starter_claims (profile_id, starter_species, claimed_at) values (?, ?, now()) on conflict (profile_id) do update set starter_species = excluded.starter_species, claimed_at = now()")) {
            ps.setObject(1, profileId);
            ps.setString(2, species);
            ps.executeUpdate();
        }
    }


    private static boolean isConfiguredStarter(String requiredType, String species) {
        String type = normalize(requiredType);
        String cleanSpecies = normalizeSpecies(species);
        StarterChoice[] configured = STARTERS.get(type);
        if (configured == null || cleanSpecies.isBlank()) return false;
        for (StarterChoice choice : configured) {
            if (normalizeSpecies(choice.species()).equals(cleanSpecies)) return true;
        }
        return false;
    }

    private static String normalizeSpecies(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        int colon = s.indexOf(':');
        if (colon >= 0 && colon + 1 < s.length()) s = s.substring(colon + 1);
        return s;
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
