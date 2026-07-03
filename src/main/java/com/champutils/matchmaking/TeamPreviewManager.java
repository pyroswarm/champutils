package com.champutils.matchmaking;

import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.battle.BattleContextManager;
import com.champutils.battle.PvPBattleFormatRules;
import com.champutils.battle.PvPBattleStarter;
import com.champutils.teleport.SafeTeleportManager;

import com.cobblemon.mod.common.battles.BattleFormat;
import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;

import java.util.*;

public class TeamPreviewManager {

    private static final Map<UUID, Integer> SELECTED = new HashMap<>();
    private static final Set<UUID> LOCKED = new HashSet<>();
    // Store UUIDs only. Holding old ServerPlayer objects after logout/profile transfer can
    // make vanilla try to track/spawn a removed player entity for other clients.
    private static final Map<UUID, UUID> OPPONENT = new HashMap<>();
    private static final Map<UUID, Integer> TIMER = new HashMap<>();
    private static final Set<UUID> STARTED = new HashSet<>();

    private static final int MAX_TIME = 200;

    // ========================
    // START PREVIEW
    // ========================
    public static void startPreview(ServerPlayer p1, ServerPlayer p2) {

        if (!SafeTeleportManager.isLive(p1) || !SafeTeleportManager.isLive(p2)) return;
        if (isInPreview(p1) || isInPreview(p2)) return;

        OPPONENT.put(p1.getUUID(), p2.getUUID());
        OPPONENT.put(p2.getUUID(), p1.getUUID());

        TIMER.put(p1.getUUID(), MAX_TIME);
        TIMER.put(p2.getUUID(), MAX_TIME);

        openGUI(p1);
        openGUI(p2);
    }

    // ========================
    // GUI
    // ========================
    public static void openGUI(ServerPlayer player) {
        if (!SafeTeleportManager.isLive(player)) return;

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x1, player, false);
        gui.setTitle(Component.literal("Choose Your Lead"));

        Integer selected = SELECTED.get(player.getUUID());

        for (int i = 0; i < 6; i++) {

            int slot = i;

            var party = Cobblemon.INSTANCE
                    .getStorage()
                    .getParty(player);

            if (party != null) {
                var pokemon = party.get(slot);

                if (pokemon != null) {

                    String name =
                            pokemon.getDisplayName(true)
                                    .getString();

                    boolean isSelected = selected != null && selected == i;

                    GuiElementBuilder button =
                            new GuiElementBuilder(
                                    PokemonIconUtil.getIcon(pokemon, slot, isSelected)
                            )
                                    .setName(
                                            Component.literal(
                                                    (isSelected
                                                            ? "§a▶ "
                                                            : "§f")
                                                            + "Pokémon #"
                                                            + (i + 1)
                                            )
                                    )
                                    .setLore(
                                            Arrays.asList(
                                                    Component.literal("§f" + name),
                                                    Component.literal("§7Click to choose this Pokémon as your lead.")
                                            )
                                    )
                                    .setCallback(
                                            (index, clickType, actionType, guiInstance) -> {
                                                select(player, slot);
                                            }
                                    );

                    gui.setSlot(i, button);
                }
            }
        }

        GuiElementBuilder filler =
                new GuiElementBuilder(
                        Items.GRAY_STAINED_GLASS_PANE
                ).setName(Component.empty());

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getSlot(i) == null) {
                gui.setSlot(i, filler);
            }
        }

        gui.open();
    }

    // ========================
    // SELECT
    // ========================
    public static void select(ServerPlayer player, int slot) {

        if (!SafeTeleportManager.isLive(player)) return;
        UUID id = player.getUUID();

        if (!isInPreview(player)) return;
        if (LOCKED.contains(id)) return;

        SELECTED.put(id, slot);
        LOCKED.add(id);

        player.sendSystemMessage(
                Component.literal(
                        "§aLocked Pokémon #" + (slot + 1)
                )
        );

        checkStart(player);
    }

    // ========================
    // TICK
    // ========================
    public static void tick(Collection<ServerPlayer> players) {

        Iterator<Map.Entry<UUID, Integer>> it =
                TIMER.entrySet().iterator();

        while (it.hasNext()) {

            Map.Entry<UUID,Integer> entry =
                    it.next();

            UUID id = entry.getKey();
            int time = entry.getValue();

            ServerPlayer player =
                    getPlayer(players, id);

            if (!SafeTeleportManager.isLive(player)) {
                forceCleanupByUUID(id);
                it.remove();
                continue;
            }

            if (time % 20 == 0) {
                if (ProfessionNotificationSettings.areQueueNotificationsEnabled(player)) {
                    ProfessionNotificationSettings.playSound(
                            player,
                            SoundEvents.UI_BUTTON_CLICK.value(),
                            SoundSource.PLAYERS,
                            0.5f,
                            1.2f
                    );
                }
            }

            time--;

            // ==================================
            // SAFE TIMEOUT LOCK FOR BOTH PLAYERS
            // ==================================
            if (time <= 0) {

                ServerPlayer opponent =
                        getPlayer(players, OPPONENT.get(id));

                if (SafeTeleportManager.isLive(opponent)) {

                    autoLockDefault(player);
                    autoLockDefault(opponent);

                    TIMER.remove(player.getUUID());
                    TIMER.remove(opponent.getUUID());
                } else {
                    forceCleanupByUUID(id);
                    it.remove();
                    return;
                }

                checkStart(player);
                return;
            }

            entry.setValue(time);
        }
    }

    private static void autoLockDefault(
            ServerPlayer player
    ) {

        if (!SafeTeleportManager.isLive(player)) return;
        UUID id = player.getUUID();

        if (LOCKED.contains(id)) return;

        SELECTED.put(id, 0);
        LOCKED.add(id);

        player.sendSystemMessage(
                Component.literal(
                        "§eTime expired — first Pokémon auto-selected."
                )
        );

        player.closeContainer();
    }

    // ========================
    // CHECK START
    // ========================
    private static void checkStart(ServerPlayer player) {

        if (!SafeTeleportManager.isLive(player)) return;
        ServerPlayer opponent = resolveOpponent(player);

        if (!SafeTeleportManager.isLive(opponent)) {
            forceCleanup(player);
            return;
        }

        UUID id1 = player.getUUID();
        UUID id2 = opponent.getUUID();

        if (!LOCKED.contains(id1)
                || !LOCKED.contains(id2))
            return;

        if (STARTED.contains(id1)
                || STARTED.contains(id2))
            return;

        STARTED.add(id1);
        STARTED.add(id2);

        startBattle(player, opponent);
    }

    // ========================
    // APPLY LEAD
    // ========================
    private static void applyLead(ServerPlayer player) {

        if (!SafeTeleportManager.isLive(player)) return;
        PartyStore party =
                Cobblemon.INSTANCE
                        .getStorage()
                        .getParty(player);

        if (party == null) return;

        Integer selected =
                SELECTED.get(player.getUUID());

        // null safety fallback
        if (selected == null) {
            selected = 0;
            SELECTED.put(
                    player.getUUID(),
                    0
            );
        }

        if (selected < 0 || selected > 5) {
            selected = 0;
        }

        if (selected == 0) return;

        party.swap(0, selected);
    }

    // ========================
    // START BATTLE
    // ========================
    private static void startBattle(
            ServerPlayer p1,
            ServerPlayer p2
    ) {

        try {

            if (!SafeTeleportManager.isLive(p1) || !SafeTeleportManager.isLive(p2)) {
                forceCleanup(p1);
                forceCleanup(p2);
                return;
            }

            p1.closeContainer();
            p2.closeContainer();

            applyLead(p1);
            applyLead(p2);

            String formatId = BattleContextManager.getFormatId(p1.getUUID());
            BattleFormat battleFormat = PvPBattleFormatRules.getCobblemonFormat(formatId);

            PvPBattleStarter.start1v1(p1, p2, battleFormat);

            cleanup(p1.getUUID());
            cleanup(p2.getUUID());

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ========================
    // CLEANUP
    // ========================
    private static void cleanup(ServerPlayer player) {
        if (player == null) return;
        cleanup(player.getUUID());
    }

    private static void cleanup(UUID id) {
        if (id == null) return;
        SELECTED.remove(id);
        LOCKED.remove(id);
        OPPONENT.remove(id);
        TIMER.remove(id);
        STARTED.remove(id);
    }

    public static void forceCleanupByUUID(UUID id) {

        if (id == null) return;
        UUID opponentId = OPPONENT.get(id);

        cleanup(id);

        if (opponentId != null) {
            cleanup(opponentId);
        }
    }

    public static void forceCleanup(
            ServerPlayer player
    ) {

        if (player == null) return;
        forceCleanupByUUID(player.getUUID());
    }

    // ========================
    // HELPERS
    // ========================
    private static ServerPlayer resolveOpponent(ServerPlayer player) {
        if (player == null || player.getServer() == null) return null;
        UUID opponentId = OPPONENT.get(player.getUUID());
        return opponentId == null ? null : player.getServer().getPlayerList().getPlayer(opponentId);
    }

    private static ServerPlayer getPlayer(
            Collection<ServerPlayer> players,
            UUID id
    ) {
        if (players == null || id == null) return null;
        for (ServerPlayer p : players) {
            if (p != null && p.getUUID().equals(id)) {
                return p;
            }
        }
        return null;
    }

    public static Integer getSelected(
            ServerPlayer player
    ) {
        if (player == null) return null;
        return SELECTED.get(
                player.getUUID()
        );
    }

    public static boolean isInPreview(
            ServerPlayer player
    ) {
        return player != null && OPPONENT.containsKey(
                player.getUUID()
        );
    }
}
