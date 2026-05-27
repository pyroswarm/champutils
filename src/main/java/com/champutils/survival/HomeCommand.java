package com.champutils.survival;

import com.champutils.profile.PlayerProfileManager;
import com.champutils.teleport.SafeTeleportManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class HomeCommand {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/survival_homes.json");
    private static HomeState state = new HomeState();

    private HomeCommand() {}

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) { save(); return; }
            try (FileReader reader = new FileReader(FILE)) {
                HomeState loaded = GSON.fromJson(reader, HomeState.class);
                state = loaded == null ? new HomeState() : loaded;
                if (state.players == null) state.players = new HashMap<>();
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load survival_homes.json.");
            e.printStackTrace();
            state = new HomeState();
        }
    }

    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(state, writer); }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save survival_homes.json.");
            e.printStackTrace();
        }
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("sethome")
                    .executes(ctx -> setHome(ctx.getSource(), "home"))
                    .then(argument("name", StringArgumentType.word())
                            .executes(ctx -> setHome(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));

            dispatcher.register(literal("home")
                    .executes(ctx -> goHome(ctx.getSource(), "home"))
                    .then(argument("name", StringArgumentType.word())
                            .executes(ctx -> goHome(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));

            dispatcher.register(literal("delhome")
                    .then(argument("name", StringArgumentType.word())
                            .executes(ctx -> deleteHome(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));

            dispatcher.register(literal("homes")
                    .executes(ctx -> listHomes(ctx.getSource())));
        });
    }

    private static int setHome(CommandSourceStack source, String rawName) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use /sethome."));
            return 0;
        }
        if (!SurvivalWorldManager.isSurvivalLevel(player.serverLevel())) {
            player.sendSystemMessage(Component.literal("/sethome can only be used inside survival worlds.").withStyle(ChatFormatting.RED));
            return 0;
        }

        String name = normalizeName(rawName);
        if (name.isBlank()) {
            player.sendSystemMessage(Component.literal("Home name cannot be blank.").withStyle(ChatFormatting.RED));
            return 0;
        }

        Map<String, HomeLocation> homes = homes(player.getUUID());
        int maxHomes = maxHomes(player);
        if (!homes.containsKey(name) && homes.size() >= maxHomes) {
            player.sendSystemMessage(Component.literal("You already have the maximum of " + maxHomes + " homes.").withStyle(ChatFormatting.RED));
            return 0;
        }

        HomeLocation home = new HomeLocation();
        home.world = player.serverLevel().dimension().location().toString();
        home.x = player.getX();
        home.y = player.getY();
        home.z = player.getZ();
        home.yaw = player.getYRot();
        home.pitch = player.getXRot();
        homes.put(name, home);
        save();
        player.sendSystemMessage(Component.literal("Set home '" + name + "'.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int goHome(CommandSourceStack source, String rawName) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use /home."));
            return 0;
        }
        String name = normalizeName(rawName);
        HomeLocation home = homes(player.getUUID()).get(name);
        if (home == null) {
            player.sendSystemMessage(Component.literal("You do not have a home named '" + name + "'.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerLevel level = getLevel(player, home.world);
        if (level == null || !SurvivalWorldManager.isSurvivalLevel(level)) {
            player.sendSystemMessage(Component.literal("That home's survival world is not loaded right now.").withStyle(ChatFormatting.RED));
            return 0;
        }
        SafeTeleportManager.teleport(player, level, home.x, home.y, home.z, home.yaw, home.pitch);
        player.sendSystemMessage(Component.literal("Teleported to home '" + name + "'.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int deleteHome(CommandSourceStack source, String rawName) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use /delhome."));
            return 0;
        }
        String name = normalizeName(rawName);
        HomeLocation removed = homes(player.getUUID()).remove(name);
        if (removed == null) {
            player.sendSystemMessage(Component.literal("You do not have a home named '" + name + "'.").withStyle(ChatFormatting.RED));
            return 0;
        }
        save();
        player.sendSystemMessage(Component.literal("Deleted home '" + name + "'.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int listHomes(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use /homes."));
            return 0;
        }
        Map<String, HomeLocation> homes = homes(player.getUUID());
        if (homes.isEmpty()) {
            player.sendSystemMessage(Component.literal("You do not have any homes set.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Homes: " + String.join(", ", homes.keySet())).withStyle(ChatFormatting.AQUA));
        return 1;
    }

    private static Map<String, HomeLocation> homes(UUID uuid) {
        if (state.players == null) state.players = new HashMap<>();
        String key = PlayerProfileManager.activeProfileId(uuid).toString();
        PlayerHomes playerHomes = state.players.computeIfAbsent(key, ignored -> new PlayerHomes());
        if (playerHomes.homes == null) playerHomes.homes = new HashMap<>();
        return playerHomes.homes;
    }

    private static int maxHomes(ServerPlayer player) {
        return SurvivalWorldConfig.get().defaultMaxHomes;
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) return "home";
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static ServerLevel getLevel(ServerPlayer player, String worldName) {
        try {
            ResourceLocation id = ResourceLocation.parse(worldName);
            return player.server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
        } catch (Exception ignored) { return null; }
    }

    public static final class HomeState { public Map<String, PlayerHomes> players = new HashMap<>(); }
    public static final class PlayerHomes { public Map<String, HomeLocation> homes = new HashMap<>(); }
    public static final class HomeLocation {
        public String world;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
    }
}
