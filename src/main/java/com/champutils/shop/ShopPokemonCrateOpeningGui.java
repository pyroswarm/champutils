package com.champutils.shop;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;

import com.champutils.profession.ProfessionNotificationSettings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopPokemonCrateOpeningGui {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File PENDING_FILE = new File("config/champutils/shop_pokemon_crate_pending.json");
    private static final Map<UUID, Opening> OPENINGS = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingReward> PENDING_REWARDS = new ConcurrentHashMap<>();
    private static boolean loadedPending = false;
    private static final int[] SPIN_SLOTS = new int[]{9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int CENTER_SLOT = 13;
    private static final int CENTER_INDEX_IN_REEL = 4;
    private static final int CENTER_MARKER_SLOT = 4;
    private static final int SPIN_END_TICKS = 78;
    private static final int TOTAL_TICKS = 108;

    private ShopPokemonCrateOpeningGui() {
    }

    private static final class PendingReward {
        String species;
        int level;
        boolean shiny;
        NpcShopService.PokemonCratePool pool;

        PendingReward() {
        }

        PendingReward(NpcShopService.PlannedPokemonCrateReward reward) {
            this.species = reward.species();
            this.level = reward.level();
            this.shiny = reward.shiny();
            this.pool = reward.pool();
        }

        NpcShopService.PlannedPokemonCrateReward toPlannedReward() {
            return NpcShopService.restorePlannedPokemonCrateReward(species, level, shiny, pool);
        }
    }

    private static final class Opening {
        final UUID playerId;
        final SimpleGui gui;
        final NpcShopService.PlannedPokemonCrateReward plan;
        final List<NpcShopService.PlannedPokemonCrateReward> spinRewards;
        int tick;
        int offset;

        Opening(ServerPlayer player, NpcShopService.PlannedPokemonCrateReward plan, SimpleGui gui) {
            this.playerId = player.getUUID();
            this.gui = gui;
            this.plan = plan;
            this.spinRewards = buildSpinRewards(plan);
        }
    }

    public static boolean open(ServerPlayer player, NpcShopConfig.ShopEntry entry) {
        if (player == null || entry == null) return false;

        NpcShopService.PlannedPokemonCrateReward plan = NpcShopService.planPokemonCrateReward(player, entry);
        if (plan == null) {
            player.sendSystemMessage(Component.literal("Could not prepare the Store Pokémon Crate reward.").withStyle(ChatFormatting.RED));
            return false;
        }

        if (isOpening(player) || hasPendingReward(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Your current Store Pokémon Crate is still opening.").withStyle(ChatFormatting.YELLOW));
            return false;
        }

        PENDING_REWARDS.put(player.getUUID(), new PendingReward(plan));
        savePendingRewards();

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setTitle(Component.literal("Store Pokémon Crate"));

        for (int i = 0; i < gui.getSize(); i++) {
            gui.setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Component.literal(" ")));
        }

        gui.setSlot(CENTER_MARKER_SLOT, centerMarkerElement());

        gui.open();

        Opening opening = new Opening(player, plan, gui);
        OPENINGS.put(player.getUUID(), opening);
        updateSpin(opening, false);
        playLocalSound(player, "minecraft:ui.button.click", 0.6F, 1.2F);
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || OPENINGS.isEmpty()) return;

        Iterator<Map.Entry<UUID, Opening>> iterator = OPENINGS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Opening> entry = iterator.next();
            Opening opening = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(opening.playerId);
            if (player == null) {
                iterator.remove();
                continue;
            }

            opening.tick++;

            boolean finalLock = opening.tick >= SPIN_END_TICKS;
            int speed = spinSpeed(opening.tick);
            if (finalLock || opening.tick % speed == 0) {
                if (!finalLock) {
                    opening.offset++;
                }
                updateSpin(opening, finalLock);
                if (opening.tick == SPIN_END_TICKS) {
                    playLocalSound(player, "minecraft:entity.player.levelup", 0.7F, opening.plan.special() ? 1.55F : 1.15F);
                } else if (!finalLock) {
                    playCrateTickSound(player, opening.tick);
                }
            }

            if (opening.tick >= TOTAL_TICKS) {
                iterator.remove();
                player.closeContainer();

                boolean granted = NpcShopService.grantPlannedPokemonCrateReward(player, opening.plan);
                if (granted) {
                    PENDING_REWARDS.remove(player.getUUID());
                    savePendingRewards();
                    playLocalSound(player, opening.plan.special() ? "minecraft:ui.toast.challenge_complete" : "minecraft:entity.experience_orb.pickup", 0.8F, opening.plan.special() ? 1.0F : 1.25F);
                } else {
                    player.sendSystemMessage(Component.literal("Your Store Pokémon Crate reward is saved and will retry when you rejoin.").withStyle(ChatFormatting.YELLOW));
                    savePendingRewards();
                }
            }
        }
    }

    public static boolean isOpening(ServerPlayer player) {
        return player != null && (OPENINGS.containsKey(player.getUUID()) || hasPendingReward(player.getUUID()));
    }

    public static void handleJoin(ServerPlayer player) {
        if (player == null) return;
        loadPendingRewards();
        PendingReward pending = PENDING_REWARDS.get(player.getUUID());
        if (pending == null) return;

        NpcShopService.PlannedPokemonCrateReward reward = pending.toPlannedReward();
        if (reward == null) {
            PENDING_REWARDS.remove(player.getUUID());
            savePendingRewards();
            player.sendSystemMessage(Component.literal("A saved Store Pokémon Crate reward was invalid and was cleared. Please contact staff if this seems wrong.").withStyle(ChatFormatting.RED));
            return;
        }

        boolean granted = NpcShopService.grantPlannedPokemonCrateReward(player, reward);
        if (granted) {
            OPENINGS.remove(player.getUUID());
            PENDING_REWARDS.remove(player.getUUID());
            savePendingRewards();
            player.sendSystemMessage(Component.literal("Your unfinished Store Pokémon Crate was safely delivered after reconnecting.").withStyle(ChatFormatting.GREEN));
        } else {
            savePendingRewards();
            player.sendSystemMessage(Component.literal("You still have a saved Store Pokémon Crate reward. It will retry when you rejoin.").withStyle(ChatFormatting.YELLOW));
        }
    }

    public static void handleDisconnect(ServerPlayer player) {
        if (player == null) return;
        Opening opening = OPENINGS.remove(player.getUUID());
        if (opening != null) {
            try {
                opening.gui.close();
            } catch (Throwable ignored) {
            }
            savePendingRewards();
        }
    }

    public static void handleServerStopping(MinecraftServer server) {
        OPENINGS.clear();
        savePendingRewards();
    }

    private static boolean hasPendingReward(UUID playerId) {
        if (playerId == null) return false;
        loadPendingRewards();
        return PENDING_REWARDS.containsKey(playerId);
    }

    private static synchronized void loadPendingRewards() {
        if (loadedPending) return;
        loadedPending = true;
        PENDING_REWARDS.clear();
        if (!PENDING_FILE.exists()) return;

        try (FileReader reader = new FileReader(PENDING_FILE)) {
            Type type = new TypeToken<Map<String, PendingReward>>() {}.getType();
            Map<String, PendingReward> loaded = GSON.fromJson(reader, type);
            if (loaded == null) return;
            for (Map.Entry<String, PendingReward> entry : loaded.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    PendingReward reward = entry.getValue();
                    if (reward != null && reward.species != null && !reward.species.isBlank()) {
                        PENDING_REWARDS.put(uuid, reward);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Exception exception) {
            System.err.println("[ChampUtils] Failed to load pending shop Pokémon crate rewards.");
            exception.printStackTrace();
        }
    }

    private static synchronized void savePendingRewards() {
        if (!loadedPending) {
            loadPendingRewards();
        }
        try {
            File parent = PENDING_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            Map<String, PendingReward> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<UUID, PendingReward> entry : PENDING_REWARDS.entrySet()) {
                out.put(entry.getKey().toString(), entry.getValue());
            }

            try (FileWriter writer = new FileWriter(PENDING_FILE)) {
                GSON.toJson(out, writer);
            }
        } catch (Exception exception) {
            System.err.println("[ChampUtils] Failed to save pending shop Pokémon crate rewards.");
            exception.printStackTrace();
        }
    }

    private static void updateSpin(Opening opening, boolean finalLock) {
        NpcShopService.PlannedPokemonCrateReward finalReward = opening.plan;

        for (int i = 0; i < SPIN_SLOTS.length; i++) {
            int spinSlot = SPIN_SLOTS[i];
            boolean center = spinSlot == CENTER_SLOT;
            NpcShopService.PlannedPokemonCrateReward line;
            if (finalLock && center) {
                line = finalReward;
            } else {
                line = opening.spinRewards.get((opening.offset + i) % opening.spinRewards.size());
            }

            GuiElementBuilder rewardBuilder = rewardElement(line).hideDefaultTooltip();
            if (center && finalLock) {
                rewardBuilder
                        .setName(Component.literal("§e§lYOUR POKÉMON - ").append(line.title()))
                        .addLoreLine(Component.literal("§aThis is what you won."))
                        .addLoreLine(line.detail());
            } else {
                // During the CS2-style roll, hide Pokémon names completely.
                // Players only see sprites moving by and the colored rarity glass.
                rewardBuilder.setName(Component.literal(" "));
            }
            opening.gui.setSlot(spinSlot, rewardBuilder);

            int bottomSlot = spinSlot + 9;
            opening.gui.setSlot(bottomSlot, rarityGlassElement(line));
        }
    }

    private static GuiElementBuilder centerMarkerElement() {
        return new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
                .hideDefaultTooltip()
                .setName(Component.literal("§7§l▲ Winning Slot"));
    }

    private static GuiElementBuilder rarityGlassElement(NpcShopService.PlannedPokemonCrateReward reward) {
        return new GuiElementBuilder(glassForReward(reward))
                .hideDefaultTooltip()
                .setName(Component.literal("§7↑ " + rarityLabel(reward)));
    }

    private static String rarityLabel(NpcShopService.PlannedPokemonCrateReward reward) {
        if (reward == null) return "§7Unknown";
        if (reward.shiny()) return "§6§lSHINY";
        return switch (reward.pool()) {
            case LEGENDARY -> "§6§lLEGENDARY";
            case ULTRA_BEAST -> "§5§lULTRA BEAST";
            case PARADOX -> "§5§lPARADOX";
            case MYTHICAL -> "§c§lMYTHICAL";
            default -> "§aRegular";
        };
    }

    private static net.minecraft.world.item.Item glassForReward(NpcShopService.PlannedPokemonCrateReward reward) {
        if (reward == null) return Items.GRAY_STAINED_GLASS_PANE;
        if (reward.shiny()) return Items.YELLOW_STAINED_GLASS_PANE;

        return switch (reward.pool()) {
            case LEGENDARY -> Items.ORANGE_STAINED_GLASS_PANE;
            case ULTRA_BEAST, PARADOX -> Items.PURPLE_STAINED_GLASS_PANE;
            case MYTHICAL -> Items.RED_STAINED_GLASS_PANE;
            default -> Items.LIME_STAINED_GLASS_PANE;
        };
    }

    private static GuiElementBuilder rewardElement(NpcShopService.PlannedPokemonCrateReward line) {
        ItemStack stack = line == null || line.icon() == null || line.icon().isEmpty() ? new ItemStack(Items.EGG) : line.icon().copy();
        try {
            return new GuiElementBuilder(stack);
        } catch (Throwable ignored) {
            return new GuiElementBuilder(stack.getItem());
        }
    }

    private static List<NpcShopService.PlannedPokemonCrateReward> buildSpinRewards(NpcShopService.PlannedPokemonCrateReward finalReward) {
        List<NpcShopService.PlannedPokemonCrateReward> expanded = new ArrayList<>();

        // Build a fresh randomized reel every time.
        // The real reward is inserted only at the calculated landing slot, so it visibly
        // rolls into the center instead of popping into place at the end.
        // Keep the reel long enough to avoid obvious repeats, but not so long that
        // opening a crate creates dozens of expensive Pokémon item stacks at once.
        while (expanded.size() < 36) {
            expanded.add(NpcShopService.randomDisplayPokemonCrateReward(finalReward));
        }

        if (finalReward != null) {
            int finalOffset = calculateFinalOffsetBeforeLock();
            int landingIndex = Math.floorMod(finalOffset + CENTER_INDEX_IN_REEL, expanded.size());

            // Make sure the won Pokémon appears only where the roll actually lands.
            for (int i = 0; i < expanded.size(); i++) {
                NpcShopService.PlannedPokemonCrateReward reward = expanded.get(i);
                if (i != landingIndex && sameReward(reward, finalReward)) {
                    expanded.set(i, NpcShopService.randomDisplayPokemonCrateReward(finalReward));
                }
            }

            expanded.set(landingIndex, finalReward);
        }

        return expanded;
    }

    private static boolean sameReward(NpcShopService.PlannedPokemonCrateReward a, NpcShopService.PlannedPokemonCrateReward b) {
        if (a == null || b == null) return false;
        if (a.shiny() != b.shiny()) return false;
        if (a.pool() != b.pool()) return false;
        if (a.species() == null || b.species() == null) return false;
        return a.species().equalsIgnoreCase(b.species());
    }

    private static NpcShopService.PlannedPokemonCrateReward fakeReward(String name, String species, int level, boolean shiny, NpcShopService.PokemonCratePool pool) {
        ChatFormatting color = shiny ? ChatFormatting.GOLD : switch (pool) {
            case LEGENDARY -> ChatFormatting.GOLD;
            case ULTRA_BEAST, PARADOX -> ChatFormatting.LIGHT_PURPLE;
            case MYTHICAL -> ChatFormatting.RED;
            default -> ChatFormatting.AQUA;
        };

        return new NpcShopService.PlannedPokemonCrateReward(
                species,
                level,
                shiny,
                pool,
                Component.literal(name).withStyle(color),
                Component.literal("Level ?? • " + (shiny ? "Shiny" : rarityPlainLabel(pool))).withStyle(ChatFormatting.GRAY),
                createPokemonIcon(species, level, shiny, pool)
        );
    }

    private static String rarityPlainLabel(NpcShopService.PokemonCratePool pool) {
        return switch (pool) {
            case LEGENDARY -> "Legendary";
            case ULTRA_BEAST -> "Ultra Beast";
            case PARADOX -> "Paradox";
            case MYTHICAL -> "Mythical";
            default -> "Regular";
        };
    }

    private static ItemStack createPokemonIcon(String species, int level, boolean shiny, NpcShopService.PokemonCratePool pool) {
        try {
            Pokemon pokemon = PokemonProperties.Companion.parse("species=\"" + species + "\" level=" + level).create();
            try {
                Pokemon.class.getMethod("setShiny", boolean.class).invoke(pokemon, shiny);
            } catch (Throwable ignored) {
                // Icon is cosmetic only. If a Cobblemon build changes this setter, fall back safely.
            }
            return PokemonItem.from(pokemon, 1);
        } catch (Throwable ignored) {
            return new ItemStack(shiny ? Items.NETHER_STAR : pool == NpcShopService.PokemonCratePool.REGULAR ? Items.EGG : Items.DRAGON_EGG);
        }
    }

    private static int calculateFinalOffsetBeforeLock() {
        int advances = 0;
        for (int tick = 1; tick < SPIN_END_TICKS; tick++) {
            int speed = spinSpeed(tick);
            if (tick % speed == 0) {
                advances++;
            }
        }
        return advances;
    }

    private static int spinSpeed(int tick) {
        // The original first 24 ticks updated every server tick, which meant 20 GUI
        // packet bursts per second. This keeps the roll smooth while cutting the
        // hottest part of the animation roughly in half.
        if (tick < 28) return 2;
        if (tick < 44) return 3;
        if (tick < 58) return 4;
        if (tick < 68) return 5;
        return 6;
    }

    private static void playCrateTickSound(ServerPlayer player, int tick) {
        float pitch = Math.min(1.85F, 0.85F + (tick / 70.0F));
        playLocalSound(player, "minecraft:block.note_block.hat", 0.45F, pitch);
    }

    private static void playLocalSound(ServerPlayer player, String soundId, float volume, float pitch) {
        if (player == null || soundId == null || soundId.isBlank()) return;
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(soundId));
        if (sound == null) return;
        ProfessionNotificationSettings.playSound(player, sound, SoundSource.PLAYERS, volume, pitch);
    }
}
