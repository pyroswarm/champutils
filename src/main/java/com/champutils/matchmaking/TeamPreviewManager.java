package com.champutils.matchmaking;

import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.battles.BattleBuilder;
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
    private static final Map<UUID, ServerPlayer> OPPONENT = new HashMap<>();
    private static final Map<UUID, Integer> TIMER = new HashMap<>();
    private static final Set<UUID> STARTED = new HashSet<>();

    private static final int MAX_TIME = 200;

    // ========================
    // START PREVIEW
    // ========================
    public static void startPreview(ServerPlayer p1, ServerPlayer p2) {

        if (isInPreview(p1) || isInPreview(p2)) return;

        OPPONENT.put(p1.getUUID(), p2);
        OPPONENT.put(p2.getUUID(), p1);

        TIMER.put(p1.getUUID(), MAX_TIME);
        TIMER.put(p2.getUUID(), MAX_TIME);

        openGUI(p1);
        openGUI(p2);
    }

    // ========================
    // GUI
    // ========================
    public static void openGUI(ServerPlayer player) {

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

                    GuiElementBuilder button =
                            new GuiElementBuilder(
                                    CobblemonItems.pokeBalls.getFirst()
                            )
                                    .setName(
                                            Component.literal(
                                                    (selected != null && selected == i
                                                            ? "§a▶ "
                                                            : "§f")
                                                            + "Pokémon #"
                                                            + (i + 1)
                                            )
                                    )
                                    .setLore(
                                            Arrays.asList(
                                                    Component.literal(name)
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

            if (player == null) {
                forceCleanupByUUID(id);
                it.remove();
                continue;
            }

            if (time % 20 == 0) {
                player.level().playSound(
                        null,
                        player.blockPosition(),
                        SoundEvents.UI_BUTTON_CLICK.value(),
                        SoundSource.PLAYERS,
                        0.5f,
                        1.2f
                );
            }

            time--;

            // ==================================
            // SAFE TIMEOUT LOCK FOR BOTH PLAYERS
            // ==================================
            if (time <= 0) {

                ServerPlayer opponent =
                        OPPONENT.get(id);

                if (opponent != null) {

                    autoLockDefault(player);
                    autoLockDefault(opponent);

                    TIMER.remove(player.getUUID());
                    TIMER.remove(opponent.getUUID());
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

        ServerPlayer opponent =
                OPPONENT.get(player.getUUID());

        if (opponent == null) return;

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

            p1.closeContainer();
            p2.closeContainer();

            applyLead(p1);
            applyLead(p2);

            BattleBuilder.INSTANCE
                    .pvp1v1(p1, p2);

            cleanup(p1);
            cleanup(p2);

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ========================
    // CLEANUP
    // ========================
    private static void cleanup(ServerPlayer player) {

        UUID id = player.getUUID();

        SELECTED.remove(id);
        LOCKED.remove(id);
        OPPONENT.remove(id);
        TIMER.remove(id);
        STARTED.remove(id);
    }

    private static void forceCleanupByUUID(UUID id) {

        ServerPlayer opponent =
                OPPONENT.get(id);

        SELECTED.remove(id);
        LOCKED.remove(id);
        OPPONENT.remove(id);
        TIMER.remove(id);
        STARTED.remove(id);

        if (opponent != null) {
            cleanup(opponent);
        }
    }

    public static void forceCleanup(
            ServerPlayer player
    ) {

        cleanup(player);

        ServerPlayer opponent =
                OPPONENT.get(
                        player.getUUID()
                );

        if (opponent != null) {
            cleanup(opponent);
        }
    }

    // ========================
    // HELPERS
    // ========================
    private static ServerPlayer getPlayer(
            Collection<ServerPlayer> players,
            UUID id
    ) {
        for (ServerPlayer p : players) {
            if (p.getUUID().equals(id)) {
                return p;
            }
        }
        return null;
    }

    public static Integer getSelected(
            ServerPlayer player
    ) {
        return SELECTED.get(
                player.getUUID()
        );
    }

    public static boolean isInPreview(
            ServerPlayer player
    ) {
        return OPPONENT.containsKey(
                player.getUUID()
        );
    }
}