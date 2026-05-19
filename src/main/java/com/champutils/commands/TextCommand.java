package com.champutils.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class TextCommand {

    private static final String TEXT_TAG = "champutils_floating_text";
    private static final String ID_PREFIX = "champutils_text_id_";
    private static final String LINE_PREFIX = "champutils_text_line_";
    private static final double LINE_SPACING = 0.25D;

    private TextCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("text")
                        .requires(source -> source.hasPermission(4))
                        .then(literal("create")
                                .then(argument("id", StringArgumentType.word())
                                        .then(argument("text", StringArgumentType.greedyString())
                                                .executes(context -> create(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        StringArgumentType.getString(context, "text")
                                                )))))
                        .then(literal("edit")
                                .then(argument("id", StringArgumentType.word())
                                        .then(argument("text", StringArgumentType.greedyString())
                                                .executes(context -> edit(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        StringArgumentType.getString(context, "text")
                                                )))))
                        .then(literal("delete")
                                .then(argument("id", StringArgumentType.word())
                                        .executes(context -> deleteById(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id")
                                        )))
                                .then(literal("radius")
                                        .then(argument("radius", IntegerArgumentType.integer(1, 128))
                                                .executes(context -> deleteRadius(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "radius")
                                                )))))
                        .then(literal("list")
                                .executes(context -> list(context.getSource())))
        ));
    }

    private static int create(CommandSourceStack source, String rawId, String rawText) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        String id = normalizeId(rawId);

        if (id.isBlank()) {
            source.sendFailure(Component.literal("Text id cannot be blank."));
            return 0;
        }

        List<String> lines = parseLines(rawText);
        if (lines.isEmpty()) {
            source.sendFailure(Component.literal("Text cannot be blank."));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        deleteExisting(level, id);

        double x = player.getX();
        double y = player.getY() + 1.35D;
        double z = player.getZ();

        spawnLines(level, id, x, y, z, lines);

        source.sendSuccess(
                () -> Component.literal("Created floating text '" + id + "' with " + lines.size() + " line(s).").withStyle(ChatFormatting.GREEN),
                true
        );
        return 1;
    }

    private static int edit(CommandSourceStack source, String rawId, String rawText) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        String id = normalizeId(rawId);
        ServerLevel level = player.serverLevel();

        List<ArmorStand> existing = findById(level, id);
        if (existing.isEmpty()) {
            source.sendFailure(Component.literal("No floating text found with id '" + id + "' in this dimension."));
            return 0;
        }

        List<String> lines = parseLines(rawText);
        if (lines.isEmpty()) {
            source.sendFailure(Component.literal("Text cannot be blank."));
            return 0;
        }

        ArmorStand anchor = existing.stream()
                .min(Comparator.comparingDouble(TextCommand::lineIndex))
                .orElse(existing.get(0));

        double x = anchor.getX();
        double y = anchor.getY();
        double z = anchor.getZ();

        for (ArmorStand stand : existing) {
            stand.discard();
        }

        spawnLines(level, id, x, y, z, lines);

        source.sendSuccess(
                () -> Component.literal("Updated floating text '" + id + "'.").withStyle(ChatFormatting.GREEN),
                true
        );
        return 1;
    }

    private static int deleteById(CommandSourceStack source, String rawId) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        String id = normalizeId(rawId);
        List<ArmorStand> existing = findById(player.serverLevel(), id);

        for (ArmorStand stand : existing) {
            stand.discard();
        }

        if (existing.isEmpty()) {
            source.sendFailure(Component.literal("No floating text found with id '" + id + "' in this dimension."));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Deleted floating text '" + id + "'.").withStyle(ChatFormatting.GREEN),
                true
        );
        return 1;
    }

    private static int deleteRadius(CommandSourceStack source, int radius) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        AABB box = new AABB(player.blockPosition()).inflate(radius);

        List<ArmorStand> stands = player.serverLevel().getEntitiesOfClass(
                ArmorStand.class,
                box,
                stand -> stand.getTags().contains(TEXT_TAG)
        );

        for (ArmorStand stand : stands) {
            stand.discard();
        }

        source.sendSuccess(
                () -> Component.literal("Deleted " + stands.size() + " floating text line(s) within " + radius + " blocks.").withStyle(ChatFormatting.GREEN),
                true
        );
        return stands.isEmpty() ? 0 : 1;
    }

    private static int list(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        Set<String> ids = new java.util.TreeSet<>();
        for (ArmorStand stand : player.serverLevel().getEntitiesOfClass(
                ArmorStand.class,
                player.getBoundingBox().inflate(256),
                stand -> stand.getTags().contains(TEXT_TAG)
        )) {
            for (String tag : stand.getTags()) {
                if (tag.startsWith(ID_PREFIX)) {
                    ids.add(tag.substring(ID_PREFIX.length()));
                }
            }
        }

        if (ids.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("No floating text found within 256 blocks.").withStyle(ChatFormatting.YELLOW),
                    false
            );
            return 1;
        }

        source.sendSuccess(
                () -> Component.literal("Floating text within 256 blocks: " + String.join(", ", ids)).withStyle(ChatFormatting.GOLD),
                false
        );
        return 1;
    }

    private static void spawnLines(ServerLevel level, String id, double x, double y, double z, List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            double lineY = y + ((lines.size() - 1 - i) * LINE_SPACING);
            spawnLine(level, id, i, x, lineY, z, Component.literal(colorize(lines.get(i))));
        }
    }

    private static void spawnLine(ServerLevel level, String id, int line, double x, double y, double z, Component text) {
        ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, level);
        stand.moveTo(x, y, z, 0.0F, 0.0F);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setCustomName(text);
        stand.setCustomNameVisible(true);
        stand.addTag(TEXT_TAG);
        stand.addTag(ID_PREFIX + id);
        stand.addTag(LINE_PREFIX + line);
        stand.addTag("champutils_no_despawn");
        level.addFreshEntity(stand);
    }

    private static List<ArmorStand> findById(ServerLevel level, String id) {
        return level.getEntitiesOfClass(
                ArmorStand.class,
                new AABB(-30000000, level.getMinBuildHeight(), -30000000, 30000000, level.getMaxBuildHeight(), 30000000),
                stand -> stand.getTags().contains(TEXT_TAG) && stand.getTags().contains(ID_PREFIX + id)
        );
    }

    private static void deleteExisting(ServerLevel level, String id) {
        for (ArmorStand stand : findById(level, id)) {
            stand.discard();
        }
    }

    private static double lineIndex(ArmorStand stand) {
        for (String tag : stand.getTags()) {
            if (tag.startsWith(LINE_PREFIX)) {
                try {
                    return Integer.parseInt(tag.substring(LINE_PREFIX.length()));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static List<String> parseLines(String rawText) {
        List<String> lines = new ArrayList<>();
        for (String part : rawText.split("\\|")) {
            String line = part.trim();
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static String normalizeId(String rawId) {
        return rawId == null ? "" : rawId.trim().toLowerCase();
    }

    private static String colorize(String text) {
        return text.replace('&', '§');
    }
}
