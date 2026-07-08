package com.champutils.moderation;

import com.champutils.teleport.SafeTeleportManager;
import com.champutils.database.DatabaseManager;
import com.champutils.permissions.PermissionUtil;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AutoModCommand {
    private static final String WARN = "champutils.mod.warn";
    private static final String KICK = "champutils.mod.kick";
    private static final String MUTE = "champutils.mod.mute";
    private static final String UNMUTE = "champutils.mod.unmute";
    private static final String BAN = "champutils.mod.ban";
    private static final String UNBAN = "champutils.mod.unban";
    private static final String HISTORY = "champutils.mod.history";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private AutoModCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("automod")
                .requires(AutoModCommand::canUseAny)
                .then(Commands.literal("warn")
                        .requires(source -> PermissionUtil.has(source, WARN))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                            String reason = StringArgumentType.getString(context, "reason");
                                            ModerationManager.staffWarn(actor(context.getSource()), target, reason);
                                            context.getSource().sendSuccess(() -> Component.literal("Warned " + target.getGameProfile().getName() + "."), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("kick")
                        .requires(source -> PermissionUtil.has(source, KICK))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                            String reason = StringArgumentType.getString(context, "reason");
                                            ModerationManager.staffKick(actor(context.getSource()), target, reason);
                                            context.getSource().sendSuccess(() -> Component.literal("Kicked " + target.getGameProfile().getName() + "."), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("mute")
                        .requires(source -> PermissionUtil.has(source, MUTE))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("duration", StringArgumentType.word())
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    Duration duration = parseDurationOrFail(context.getSource(), StringArgumentType.getString(context, "duration"));
                                                    if (duration == null) return 0;
                                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                                    String reason = StringArgumentType.getString(context, "reason");
                                                    ModerationManager.staffMute(actor(context.getSource()), target, duration, reason);
                                                    context.getSource().sendSuccess(() -> Component.literal("Muted " + target.getGameProfile().getName() + " for " + format(duration.toMillis()) + "."), true);
                                                    return 1;
                                                })))))
                .then(Commands.literal("unmute")
                        .requires(source -> PermissionUtil.has(source, UNMUTE))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String targetName = StringArgumentType.getString(context, "player");
                                            ServerPlayer target = findOnline(context.getSource().getServer(), targetName);
                                            String reason = StringArgumentType.getString(context, "reason");
                                            boolean changed = ModerationManager.staffUnmute(actor(context.getSource()), target, targetName, reason);
                                            context.getSource().sendSuccess(() -> Component.literal(changed ? "Unmuted " + displayName(target, targetName) + "." : displayName(target, targetName) + " had no active mute, but the unmute was logged."), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("ban")
                        .requires(source -> PermissionUtil.has(source, BAN))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("duration", StringArgumentType.word())
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    String targetName = StringArgumentType.getString(context, "player");
                                                    String durationText = StringArgumentType.getString(context, "duration");
                                                    String reason = StringArgumentType.getString(context, "reason");
                                                    ServerPlayer target = findOnline(context.getSource().getServer(), targetName);
                                                    boolean permanent = durationText.equalsIgnoreCase("permanent") || durationText.equalsIgnoreCase("perm");
                                                    Duration duration = permanent ? null : parseDurationOrFail(context.getSource(), durationText);
                                                    if (!permanent && duration == null) return 0;
                                                    if (target != null) {
                                                        ModerationManager.staffBan(actor(context.getSource()), target, duration, permanent, reason);
                                                    } else {
                                                        ModerationActionRepository.ActionDraft draft = ModerationActionRepository.draftOffline(actor(context.getSource()), targetName, null, ModerationActionRepository.ActionType.BAN, reason);
                                                        if (!permanent) draft.expiresAt = Instant.now().plus(duration);
                                                        ModerationActionRepository.insert(draft);
                                                        ModerationManager.alertAdmins(context.getSource().getServer(), "§4[Staff Ban] §f" + actorName(context.getSource()) + " §7banned offline player §f" + targetName + " §7" + (permanent ? "permanently" : "for §e" + format(duration.toMillis())) + "§7. Reason: §c" + reason);
                                                    }
                                                    context.getSource().sendSuccess(() -> Component.literal("Banned " + displayName(target, targetName) + " " + (permanent ? "permanently" : "for " + format(duration.toMillis())) + "."), true);
                                                    return 1;
                                                })))))
                .then(Commands.literal("unban")
                        .requires(source -> PermissionUtil.has(source, UNBAN))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String targetName = StringArgumentType.getString(context, "player");
                                            String reason = StringArgumentType.getString(context, "reason");
                                            ServerPlayer target = findOnline(context.getSource().getServer(), targetName);
                                            UUID uuid = target == null ? null : target.getUUID();
                                            boolean changed = ModerationManager.staffUnban(actor(context.getSource()), targetName, uuid, context.getSource().getServer(), reason);
                                            context.getSource().sendSuccess(() -> Component.literal(changed ? "Unbanned " + displayName(target, targetName) + "." : displayName(target, targetName) + " had no active ban, but the unban was logged."), true);
                                            return 1;
                                        }))))

                .then(Commands.literal("vanish")
                        .requires(source -> PermissionUtil.has(source, "champutils.mod.vanish"))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            boolean invisible = !player.isInvisible();
                            player.setInvisible(invisible);
                            player.setSilent(invisible);
                            context.getSource().sendSuccess(() -> Component.literal(invisible ? "Vanish enabled." : "Vanish disabled."), true);
                            return 1;
                        }))
                .then(Commands.literal("tp")
                        .requires(source -> PermissionUtil.has(source, "champutils.mod.tp"))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                    ServerPlayer actor = context.getSource().getPlayerOrException();
                                    SafeTeleportManager.teleportNoBack(actor, target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
                                    context.getSource().sendSuccess(() -> Component.literal("Teleported to " + target.getGameProfile().getName() + "."), true);
                                    return 1;
                                })))
                .then(Commands.literal("invsee")
                        .requires(source -> PermissionUtil.has(source, "champutils.mod.invsee"))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                    openInventoryView(context.getSource().getPlayerOrException(), target);
                                    return 1;
                                })))
                .then(Commands.literal("pokesee")
                        .requires(source -> PermissionUtil.has(source, "champutils.mod.pokesee") || PermissionUtil.has(source, "champutils.mod.pokeseeother"))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                    context.getSource().getServer().getCommands().performPrefixedCommand(context.getSource(), "pokesee " + target.getGameProfile().getName());
                                    return 1;
                                })))
                .then(Commands.literal("spectate")
                        .requires(source -> PermissionUtil.has(source, "champutils.mod.spectate"))
                        .then(Commands.literal("end")
                                .executes(context -> {
                                    ServerPlayer actor = context.getSource().getPlayerOrException();
                                    actor.setGameMode(GameType.SURVIVAL);
                                    context.getSource().sendSuccess(() -> Component.literal("Spectate ended. You are back in survival."), true);
                                    return 1;
                                }))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                    ServerPlayer actor = context.getSource().getPlayerOrException();
                                    actor.setGameMode(GameType.SPECTATOR);
                                    SafeTeleportManager.teleportNoBack(actor, target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
                                    context.getSource().sendSuccess(() -> Component.literal("Spectating " + target.getGameProfile().getName() + "."), true);
                                    return 1;
                                })))
                .then(Commands.literal("history")
                        .requires(source -> PermissionUtil.has(source, HISTORY))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> {
                                    String targetName = StringArgumentType.getString(context, "player");
                                    ServerPlayer target = findOnline(context.getSource().getServer(), targetName);
                                    UUID uuid = target == null ? null : target.getUUID();
                                    CommandSourceStack source = context.getSource();
                                    MinecraftServer server = source.getServer();
                                    String display = displayName(target, targetName);
                                    source.sendSuccess(() -> Component.literal("Loading moderation history for " + display + "...").withStyle(ChatFormatting.GRAY), false);
                                    DatabaseManager.supplyAsync("moderation staff history", connection -> ModerationManager.staffHistory(uuid, targetName)).thenAccept(rows -> server.execute(() -> {
                                        source.sendSuccess(() -> Component.literal("Moderation history for " + display + " (" + rows.size() + " records):").withStyle(ChatFormatting.GOLD), false);
                                        for (ModerationActionRepository.ActionRecord row : rows) {
                                            source.sendSuccess(() -> Component.literal(formatRow(row)), false);
                                        }
                                    }));
                                    return 1;
                                })))
                .then(Commands.literal("escalate")
                        .requires(source -> PermissionUtil.has(source, WARN))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                            String reason = StringArgumentType.getString(context, "reason");
                                            ModerationManager.manualEscalate(actor(context.getSource()), target, reason);
                                            context.getSource().sendSuccess(() -> Component.literal("Escalated AutoMod record for " + target.getGameProfile().getName() + "."), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("redstone")
                        .requires(source -> PermissionUtil.has(source, "champutils.staff") || PermissionUtil.has(source, "champutils.admin"))
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    RedstoneAutoModManager.Status status = RedstoneAutoModManager.status();
                                    context.getSource().sendSuccess(() -> Component.literal("Redstone AutoMod: trackedChunks=" + status.trackedChunks() + ", disabledChunks=" + status.disabledChunks() + ", queuedChunks=" + status.queuedChunks()), false);
                                    return 1;
                                }))
                        .then(Commands.literal("chunk")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String status = RedstoneAutoModManager.currentChunkStatus(player.serverLevel(), player.blockPosition());
                                    context.getSource().sendSuccess(() -> Component.literal(status), false);
                                    return 1;
                                }))
                        .then(Commands.literal("clear")
                                .requires(source -> PermissionUtil.has(source, "champutils.admin"))
                                .executes(context -> {
                                    RedstoneAutoModManager.clearAll(context.getSource().getServer());
                                    context.getSource().sendSuccess(() -> Component.literal("Cleared Redstone AutoMod tracked activity and disabled chunks."), true);
                                    return 1;
                                }))
                        .then(Commands.literal("clearchunk")
                                .requires(source -> PermissionUtil.has(source, "champutils.admin"))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    RedstoneAutoModManager.clearChunk(player.serverLevel(), new net.minecraft.world.level.ChunkPos(player.blockPosition()));
                                    context.getSource().sendSuccess(() -> Component.literal("Cleared Redstone AutoMod data for your current chunk."), true);
                                    return 1;
                                })))
                .then(Commands.literal("reload")
                        .requires(source -> PermissionUtil.has(source, "champutils.admin"))
                        .executes(context -> {
                            ModerationConfig.load();
                            context.getSource().sendSuccess(() -> Component.literal("Reloaded moderation.json."), true);
                            return 1;
                        }))));
    }

    private static void openInventoryView(ServerPlayer viewer, ServerPlayer target) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, viewer, false);
        gui.setTitle(Component.literal("Inventory: " + target.getGameProfile().getName()));
        for (int i = 0; i < target.getInventory().getContainerSize() && i < 54; i++) {
            ItemStack stack = target.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty()) gui.setSlot(i, new GuiElementBuilder(stack.copy()).hideDefaultTooltip());
        }
        gui.open();
    }

    private static boolean canUseAny(CommandSourceStack source) {
        return PermissionUtil.has(source, WARN) || PermissionUtil.has(source, KICK) || PermissionUtil.has(source, MUTE)
                || PermissionUtil.has(source, UNMUTE) || PermissionUtil.has(source, BAN) || PermissionUtil.has(source, UNBAN)
                || PermissionUtil.has(source, HISTORY) || PermissionUtil.has(source, "champutils.mod.vanish") || PermissionUtil.has(source, "champutils.mod.tp") || PermissionUtil.has(source, "champutils.mod.invsee") || PermissionUtil.has(source, "champutils.mod.pokesee") || PermissionUtil.has(source, "champutils.mod.pokeseeother") || PermissionUtil.has(source, "champutils.mod.spectate") || PermissionUtil.has(source, "champutils.admin") || PermissionUtil.has(source, "champutils.staff");
    }

    private static ServerPlayer actor(CommandSourceStack source) {
        try { return source.getPlayerOrException(); } catch (Exception ignored) { return null; }
    }

    private static String actorName(CommandSourceStack source) {
        ServerPlayer player = actor(source);
        return player == null ? "Console" : player.getGameProfile().getName();
    }

    private static ServerPlayer findOnline(MinecraftServer server, String name) {
        if (server == null || name == null) return null;
        return server.getPlayerList().getPlayerByName(name);
    }

    private static String displayName(ServerPlayer target, String fallback) {
        return target == null ? fallback : target.getGameProfile().getName();
    }

    private static Duration parseDurationOrFail(CommandSourceStack source, String raw) {
        try {
            return parseDuration(raw);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Invalid duration: " + raw + ". Use 30s, 30m, 1h, 1d, 7d, or permanent.").withStyle(ChatFormatting.RED));
            return null;
        }
    }

    private static Duration parseDuration(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("blank duration");
        String text = raw.trim().toLowerCase(Locale.ROOT);
        long multiplier;
        if (text.endsWith("s")) { multiplier = 1_000L; text = text.substring(0, text.length() - 1); }
        else if (text.endsWith("m")) { multiplier = 60_000L; text = text.substring(0, text.length() - 1); }
        else if (text.endsWith("h")) { multiplier = 3_600_000L; text = text.substring(0, text.length() - 1); }
        else if (text.endsWith("d")) { multiplier = 86_400_000L; text = text.substring(0, text.length() - 1); }
        else throw new IllegalArgumentException("missing unit");
        long amount = Long.parseLong(text);
        if (amount <= 0) throw new IllegalArgumentException("duration must be positive");
        return Duration.ofMillis(Math.multiplyExact(amount, multiplier));
    }

    private static String formatRow(ModerationActionRepository.ActionRecord row) {
        String status;
        if (row.revokedAt() != null) status = "revoked";
        else if (row.expiresAt() != null && row.expiresAt().isBefore(Instant.now())) status = "expired";
        else if (row.active()) status = "active";
        else status = "inactive";
        String duration = row.expiresAt() == null ? "permanent/none" : "until " + DATE.format(row.expiresAt());
        return DATE.format(row.issuedAt()) + " | " + row.actionType().name() + " | by " + row.moderatorName() + " | " + duration + " | " + status + " | " + row.reason();
    }

    private static String format(long ms) {
        long s = Math.max(1, ms / 1000);
        if (s >= 86400) return (s / 86400) + "d " + ((s % 86400) / 3600) + "h";
        if (s >= 3600) return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
        if (s >= 60) return (s / 60) + "m " + (s % 60) + "s";
        return s + "s";
    }
}
