package com.champutils.commands;

import com.champutils.trainer.ChampTrainerSpawner;

import com.cobblemon.mod.common.entity.npc.NPCEntity;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class BlankNpcCommand {

    private BlankNpcCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("spawnblanknpc")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> spawnAtPlayer(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                )))
                        .then(Commands.literal("at")
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                                .executes(context -> spawnAtCoords(
                                                                        context.getSource(),
                                                                        DoubleArgumentType.getDouble(context, "x"),
                                                                        DoubleArgumentType.getDouble(context, "y"),
                                                                        DoubleArgumentType.getDouble(context, "z"),
                                                                        StringArgumentType.getString(context, "name")
                                                                )))))))
        ));
    }

    private static int spawnAtPlayer(CommandSourceStack source, String name) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            return spawn(source, player.serverLevel(), player.position(), player.getYRot(), name);
        } catch (Exception e) {
            source.sendFailure(Component.literal("Console must provide coordinates: /spawnblanknpc at <x> <y> <z> <name>"));
            return 0;
        }
    }

    private static int spawnAtCoords(CommandSourceStack source, double x, double y, double z, String name) {
        ServerLevel level = source.getLevel();
        float yaw = 0.0F;
        try {
            yaw = source.getPlayerOrException().getYRot();
        } catch (Exception ignored) {}
        return spawn(source, level, new Vec3(x + 0.5D, y, z + 0.5D), yaw, name);
    }

    private static int spawn(CommandSourceStack source, ServerLevel level, Vec3 pos, float yaw, String name) {
        String displayName = cleanName(name);
        if (displayName.isBlank()) {
            source.sendFailure(Component.literal("NPC name cannot be blank."));
            return 0;
        }

        NPCEntity npc = ChampTrainerSpawner.createProtectedNpc(level, pos, yaw, displayName, "");
        if (npc == null) {
            source.sendFailure(Component.literal("Could not create blank NPC."));
            return 0;
        }

        makeBlank(npc);

        source.sendSuccess(
                () -> Component.literal("Spawned blank menu-bind NPC: ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(displayName).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" (" + npc.getUUID() + ")").withStyle(ChatFormatting.DARK_GRAY)),
                true
        );
        return 1;
    }

    private static String cleanName(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private static void makeBlank(NPCEntity npc) {
        if (npc == null) return;

        try { npc.setBattle(null); } catch (Exception ignored) {}
        try { npc.setParty(null); } catch (Exception ignored) {}
        try { npc.setInteraction(null); } catch (Exception ignored) {}
        try { npc.setSkill(null); } catch (Exception ignored) {}
        try { npc.setNoAi(true); } catch (Exception ignored) {}

        // Cobblemon NPCEntity has its own nullable invulnerability flag in addition to
        // vanilla Entity#setInvulnerable(boolean). Calling setInvulnerable(true) with a
        // primitive boolean can hit the vanilla setter instead, which still leaves the
        // Cobblemon NPC damage check using its original value. Use Boolean.TRUE so the
        // Kotlin NPCEntity#setInvulnerable(Boolean) setter is selected.
        try { npc.setInvulnerable(Boolean.TRUE); } catch (Exception ignored) {}
        try { npc.setAllowProjectileHits(Boolean.FALSE); } catch (Exception ignored) {}
        try { npc.setMovable(Boolean.FALSE); } catch (Exception ignored) {}

        // Keep the vanilla flag too as a second layer of protection.
        try { ((net.minecraft.world.entity.Entity) npc).setInvulnerable(true); } catch (Exception ignored) {}
        try { npc.setPersistenceRequired(); } catch (Exception ignored) {}
    }
}
