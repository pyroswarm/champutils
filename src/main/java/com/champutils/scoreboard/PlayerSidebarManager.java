package com.champutils.scoreboard;

import com.champutils.dex.DexProgressManager;
import com.champutils.economy.EconomyManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionType;
import com.champutils.profile.PlayerDataManager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlayerSidebarManager {

    private static final Map<UUID, List<String>> LAST_LINES = new HashMap<>();
    private static final Map<UUID, Boolean> OBJECTIVE_CREATED = new HashMap<>();

    private PlayerSidebarManager() {
    }

    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ScoreboardPreferenceManager.isEnabled(player.getUUID())) {
                update(player);
            } else {
                clear(player);
            }
        }
    }

    public static void update(ServerPlayer player) {
        if (player == null) {
            return;
        }

        String objectiveName = objectiveName(player);
        List<String> lines = buildLines(player);

        if (!sendSidebar(player, objectiveName, Component.literal("Cobble Champs").withStyle(ChatFormatting.GOLD), lines)) {
            player.displayClientMessage(Component.literal("§cCould not render the custom scoreboard on this server mapping."), true);
        }
    }

    public static void clear(ServerPlayer player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUUID();
        String objectiveName = objectiveName(player);

        try {
            Object objective = createObjective(objectiveName, Component.literal("Cobble Champs"));
            sendPacket(player, createObjectivePacket(objective, 1));
            LAST_LINES.remove(uuid);
            OBJECTIVE_CREATED.remove(uuid);
        } catch (Exception ignored) {
            LAST_LINES.remove(uuid);
            OBJECTIVE_CREATED.remove(uuid);
        }
    }

    private static List<String> buildLines(ServerPlayer player) {
        List<String> lines = new ArrayList<>();

        int rp = PlayerDataManager.getRp(player.getUUID(), player.getName().getString());
        long balance = EconomyManager.getBalance(player);
        int caught = DexProgressManager.getCaughtCount(player);
        int total = DexProgressManager.getTotalPokemon();
        double dexPercent = DexProgressManager.getCompletionPercent(player);

        int battling = ProfessionManager.getLevel(player, ProfessionType.BATTLING);
        int mining = ProfessionManager.getLevel(player, ProfessionType.MINING);
        int forestry = ProfessionManager.getLevel(player, ProfessionType.FORESTRY);
        int farming = ProfessionManager.getLevel(player, ProfessionType.FARMING);

        lines.add("§6Money: §f" + EconomyManager.format(balance));
        lines.add("§bRP: §f" + rp);
        lines.add("§dDex: §f" + caught + "§7/§f" + total + " §7(" + String.format("%.1f", dexPercent) + "%)");
        lines.add("§8────────────");
        lines.add("§cBattle: §f" + battling);
        lines.add("§7Mining: §f" + mining);
        lines.add("§2Forestry: §f" + forestry);
        lines.add("§aFarming: §f" + farming);

        return makeUnique(lines);
    }

    private static List<String> makeUnique(List<String> source) {
        List<String> result = new ArrayList<>();
        String[] suffixes = {
                "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9",
                "§a", "§b", "§c", "§d", "§e", "§f"
        };

        for (int i = 0; i < source.size(); i++) {
            String line = source.get(i);
            if (line.length() > 32) {
                line = line.substring(0, 32);
            }
            result.add(line + suffixes[i % suffixes.length]);
        }

        return result;
    }

    private static boolean sendSidebar(ServerPlayer player, String objectiveName, Component title, List<String> lines) {
        try {
            UUID uuid = player.getUUID();
            Object objective = createObjective(objectiveName, title);

            boolean created = OBJECTIVE_CREATED.getOrDefault(uuid, false);
            sendPacket(player, createObjectivePacket(objective, created ? 2 : 0));
            sendPacket(player, createDisplayPacket(objective));
            OBJECTIVE_CREATED.put(uuid, true);

            List<String> previous = LAST_LINES.getOrDefault(uuid, List.of());
            for (String oldLine : previous) {
                if (!lines.contains(oldLine)) {
                    sendPacket(player, createResetScorePacket(oldLine, objectiveName));
                }
            }

            int score = lines.size();
            for (String line : lines) {
                sendPacket(player, createScorePacket(line, objectiveName, score));
                score--;
            }

            LAST_LINES.put(uuid, lines);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static Object createObjective(String objectiveName, Component title) throws Exception {
        Class<?> scoreboardClass = Class.forName("net.minecraft.world.scores.Scoreboard");
        Class<?> criteriaClass = Class.forName("net.minecraft.world.scores.criteria.ObjectiveCriteria");
        Class<?> renderTypeClass = Class.forName("net.minecraft.world.scores.criteria.ObjectiveCriteria$RenderType");

        Object scoreboard = scoreboardClass.getConstructor().newInstance();
        Object dummyCriteria = getStaticField(criteriaClass, "DUMMY");
        Object integerRender = getStaticField(renderTypeClass, "INTEGER");

        for (Method method : scoreboardClass.getMethods()) {
            if (!method.getName().equals("addObjective")) {
                continue;
            }

            Class<?>[] types = method.getParameterTypes();
            if (types.length < 4 || types[0] != String.class) {
                continue;
            }

            Object[] args = new Object[types.length];
            args[0] = objectiveName;
            args[1] = dummyCriteria;
            args[2] = title;
            args[3] = integerRender;

            for (int i = 4; i < types.length; i++) {
                if (types[i] == boolean.class || types[i] == Boolean.class) {
                    args[i] = false;
                } else {
                    args[i] = null;
                }
            }

            return method.invoke(scoreboard, args);
        }

        throw new IllegalStateException("Could not find Scoreboard.addObjective signature.");
    }

    private static Object createObjectivePacket(Object objective, int mode) throws Exception {
        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetObjectivePacket");

        for (Constructor<?> constructor : packetClass.getConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types.length == 2 && types[1] == int.class && types[0].isAssignableFrom(objective.getClass())) {
                return constructor.newInstance(objective, mode);
            }
        }

        throw new IllegalStateException("Could not create ClientboundSetObjectivePacket.");
    }

    private static Object createDisplayPacket(Object objective) throws Exception {
        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket");
        Class<?> displaySlotClass = Class.forName("net.minecraft.world.scores.DisplaySlot");
        Object sidebar = getStaticField(displaySlotClass, "SIDEBAR");

        for (Constructor<?> constructor : packetClass.getConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types.length == 2) {
                return constructor.newInstance(sidebar, objective);
            }
        }

        throw new IllegalStateException("Could not create ClientboundSetDisplayObjectivePacket.");
    }

    private static Object createScorePacket(String line, String objectiveName, int score) throws Exception {
        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetScorePacket");

        for (Constructor<?> constructor : packetClass.getConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            Object[] args = new Object[types.length];

            if (types.length == 3 && types[0] == String.class && types[1] == String.class && types[2] == int.class) {
                return constructor.newInstance(line, objectiveName, score);
            }

            if (types.length >= 5 && types[0] == String.class && types[1] == String.class && types[2] == int.class) {
                args[0] = line;
                args[1] = objectiveName;
                args[2] = score;
                for (int i = 3; i < types.length; i++) {
                    if (types[i].getName().equals("java.util.Optional")) {
                        args[i] = Optional.empty();
                    } else if (types[i] == boolean.class || types[i] == Boolean.class) {
                        args[i] = false;
                    } else {
                        args[i] = null;
                    }
                }
                return constructor.newInstance(args);
            }
        }

        throw new IllegalStateException("Could not create ClientboundSetScorePacket.");
    }

    private static Object createResetScorePacket(String line, String objectiveName) throws Exception {
        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundResetScorePacket");

        for (Constructor<?> constructor : packetClass.getConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types.length == 2 && types[0] == String.class && types[1] == String.class) {
                return constructor.newInstance(line, objectiveName);
            }
            if (types.length == 2 && types[0] == String.class && types[1].getName().equals("java.util.Optional")) {
                return constructor.newInstance(line, Optional.of(objectiveName));
            }
            if (types.length == 1 && types[0] == String.class) {
                return constructor.newInstance(line);
            }
        }

        throw new IllegalStateException("Could not create ClientboundResetScorePacket.");
    }

    private static Object getStaticField(Class<?> clazz, String name) throws Exception {
        Field field = clazz.getField(name);
        return field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static void sendPacket(ServerPlayer player, Object packet) {
        player.connection.send((Packet<?>) packet);
    }

    private static String objectiveName(ServerPlayer player) {
        String compact = player.getUUID().toString().replace("-", "");
        return "cu_sb_" + compact.substring(0, 10);
    }
}
