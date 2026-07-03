package com.champutils.commands;

import com.champutils.menu.MenuNpcBindingRegistry;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

public final class NpcAdminCommand {
    private NpcAdminCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("npcedit")
                    .requires(NpcAdminCommand::canUse)
                    .executes(ctx -> editNearest(ctx.getSource(), 6.0D))
                    .then(Commands.literal("nearest")
                            .executes(ctx -> editNearest(ctx.getSource(), 6.0D))
                            .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 64.0D))
                                    .executes(ctx -> editNearest(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "radius")))))
                    .then(Commands.argument("uuid", StringArgumentType.word())
                            .executes(ctx -> editUuid(ctx.getSource(), StringArgumentType.getString(ctx, "uuid")))));

            dispatcher.register(Commands.literal("npcdelete")
                    .requires(NpcAdminCommand::canUse)
                    .executes(ctx -> deleteNearest(ctx.getSource(), 6.0D))
                    .then(Commands.literal("nearest")
                            .executes(ctx -> deleteNearest(ctx.getSource(), 6.0D))
                            .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 64.0D))
                                    .executes(ctx -> deleteNearest(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "radius")))))
                    .then(Commands.argument("uuid", StringArgumentType.word())
                            .executes(ctx -> deleteUuid(ctx.getSource(), StringArgumentType.getString(ctx, "uuid")))));
        });
    }

    private static boolean canUse(CommandSourceStack source) {
        return source.hasPermission(4);
    }

    private static int editNearest(CommandSourceStack source, double radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        NPCEntity npc = nearestNpc(player, radius);
        if (npc == null) {
            source.sendFailure(Component.literal("No Cobblemon NPC found within " + radius + " blocks."));
            return 0;
        }
        npc.edit(player);
        source.sendSuccess(() -> Component.literal("Opened NPC editor for " + npc.getUUID()).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int editUuid(CommandSourceStack source, String rawUuid) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        NPCEntity npc = getNpcByUuid(source.getLevel(), rawUuid);
        if (npc == null) {
            source.sendFailure(Component.literal("No loaded Cobblemon NPC found with UUID: " + rawUuid));
            return 0;
        }
        npc.edit(player);
        source.sendSuccess(() -> Component.literal("Opened NPC editor for " + npc.getUUID()).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int deleteNearest(CommandSourceStack source, double radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        NPCEntity npc = nearestNpc(player, radius);
        if (npc == null) {
            source.sendFailure(Component.literal("No Cobblemon NPC found within " + radius + " blocks."));
            return 0;
        }
        return delete(source, npc);
    }

    private static int deleteUuid(CommandSourceStack source, String rawUuid) {
        NPCEntity npc = getNpcByUuid(source.getLevel(), rawUuid);
        if (npc == null) {
            source.sendFailure(Component.literal("No loaded Cobblemon NPC found with UUID: " + rawUuid));
            return 0;
        }
        return delete(source, npc);
    }

    private static int delete(CommandSourceStack source, NPCEntity npc) {
        UUID uuid = npc.getUUID();
        String name = npc.getCustomName() == null ? "NPC" : npc.getCustomName().getString();
        ArrayList<String> menusToUnbind = new ArrayList<>();
        for (Map.Entry<String, MenuNpcBindingRegistry.Binding> entry : MenuNpcBindingRegistry.getAll().entrySet()) {
            MenuNpcBindingRegistry.Binding binding = entry.getValue();
            if (binding != null && uuid.equals(binding.uuid())) {
                menusToUnbind.add(entry.getKey());
            }
        }
        for (String menu : menusToUnbind) {
            MenuNpcBindingRegistry.unbind(menu);
        }
        npc.discard();
        source.sendSuccess(() -> Component.literal("Deleted NPC " + name + " " + uuid + (menusToUnbind.isEmpty() ? "" : " and removed menu binding(s): " + String.join(", ", menusToUnbind))).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static NPCEntity nearestNpc(ServerPlayer player, double radius) {
        Vec3 pos = player.position();
        AABB box = new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);
        return player.serverLevel().getEntitiesOfClass(NPCEntity.class, box).stream()
                .min(Comparator.comparingDouble(npc -> npc.distanceToSqr(player)))
                .orElse(null);
    }

    private static NPCEntity getNpcByUuid(ServerLevel level, String rawUuid) {
        try {
            UUID uuid = UUID.fromString(rawUuid);
            Entity entity = level.getEntity(uuid);
            return entity instanceof NPCEntity npc ? npc : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
