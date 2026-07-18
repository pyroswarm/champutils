package com.champutils.guild;

import com.champutils.adventureguide.AdventureGuideManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.champutils.economy.EconomyManager;
import com.champutils.territory.TerritoryRegionWipeManager;
import com.champutils.territory.TerritoryRepository;
import com.champutils.territory.TerritoryTeleportUtil;
import com.champutils.menu.ConfirmationMenu;
import com.champutils.network.NetworkEventManager;
import com.champutils.network.NetworkPlayerDirectory;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuildCommand {

    private static final long CONFIRM_MS = 60_000L;
    private static final Map<UUID, PendingGuildAction> PENDING_ACTIONS = new ConcurrentHashMap<>();

    private GuildCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("guild")
                    .executes(context -> info(context.getSource().getPlayerOrException()))
                    .then(Commands.literal("info")
                            .executes(context -> info(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("buffs")
                            .executes(context -> buffs(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("boss")
                            .then(Commands.literal("spawn")
                                    .executes(context -> {
                                        GuildBossManager.spawnBoss(context.getSource().getPlayerOrException());
                                        return 1;
                                    }))
                            .then(Commands.literal("claim")
                                    .executes(context -> {
                                        GuildBossManager.claimRewards(context.getSource().getPlayerOrException());
                                        return 1;
                                    })))
                    .then(Commands.literal("territory")
                            .executes(context -> guildTerritoryHome(context.getSource().getPlayerOrException()))
                            .then(Commands.literal("home")
                                    .executes(context -> guildTerritoryHome(context.getSource().getPlayerOrException())))
                            .then(Commands.literal("tp")
                                    .executes(context -> guildTerritoryHome(context.getSource().getPlayerOrException()))))
                    .then(Commands.literal("create")
                            .then(Commands.argument("name", StringArgumentType.string())
                                    .then(Commands.argument("tag", StringArgumentType.string())
                                            .executes(context -> create(
                                                    context.getSource().getPlayerOrException(),
                                                    StringArgumentType.getString(context, "name"),
                                                    StringArgumentType.getString(context, "tag")
                                            )))))
                    .then(Commands.literal("invite")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(NetworkPlayerDirectory::suggestNames)
                                    .executes(context -> invite(
                                            context.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(context, "player")
                                    ))))
                    .then(Commands.literal("accept")
                            .executes(context -> accept(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("deny")
                            .executes(context -> deny(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("leave")
                            .executes(context -> leave(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("kick")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(NetworkPlayerDirectory::suggestNames)
                                    .executes(context -> kick(
                                            context.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(context, "player")
                                    ))))
                    .then(Commands.literal("promote")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(NetworkPlayerDirectory::suggestNames)
                                    .executes(context -> promote(
                                            context.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(context, "player")
                                    ))))
                    .then(Commands.literal("demote")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(NetworkPlayerDirectory::suggestNames)
                                    .executes(context -> demote(
                                            context.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(context, "player")
                                    ))))
                    .then(Commands.literal("transfer")
                            .then(Commands.literal("confirm")
                                    .executes(context -> confirmTransfer(context.getSource().getPlayerOrException())))
                            .then(Commands.literal("cancel")
                                    .executes(context -> cancelPending(context.getSource().getPlayerOrException())))
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(NetworkPlayerDirectory::suggestNames)
                                    .executes(context -> requestTransfer(
                                            context.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(context, "player")
                                    ))))
                    .then(Commands.literal("disband")
                            .executes(context -> requestDisband(context.getSource().getPlayerOrException()))
                            .then(Commands.literal("confirm")
                                    .executes(context -> confirmDisband(context.getSource().getPlayerOrException())))
                            .then(Commands.literal("cancel")
                                    .executes(context -> cancelPending(context.getSource().getPlayerOrException()))))
                    .then(Commands.literal("chat")
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(context -> guildChat(
                                            context.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(context, "message")
                                    ))))
                    .then(Commands.literal("admin")
                            .requires(source -> source.hasPermission(4))
                            .then(Commands.literal("setcreatecooldown")
                                    .then(Commands.argument("minutes", LongArgumentType.longArg(0L))
                                            .executes(context -> setCreateCooldown(
                                                    context.getSource().getPlayerOrException(),
                                                    LongArgumentType.getLong(context, "minutes")
                                            ))))
                            .then(Commands.literal("setcreatecost")
                                    .then(Commands.argument("credits", LongArgumentType.longArg(0L))
                                            .executes(context -> setCreateCost(
                                                    context.getSource().getPlayerOrException(),
                                                    LongArgumentType.getLong(context, "credits")
                                            )))))
                    .then(Commands.literal("debugreload")
                            .requires(source -> source.hasPermission(4))
                            .executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                GuildConfig.load();
                                GuildBuffConfig.load();
                                GuildRepository.loadForPlayer(player.getUUID(), player.getGameProfile().getName());
                                player.sendSystemMessage(Component.literal("Reloaded your guild cache and guild buff config.").withStyle(ChatFormatting.GREEN));
                                return 1;
                            })));

            dispatcher.register(Commands.literal("g")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(context -> guildChat(
                                    context.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(context, "message")
                            ))));
        });
    }

    private static int guildTerritoryHome(ServerPlayer player) {
        TerritoryRepository.Territory territory = TerritoryRepository.cachedGuildForPlayer(player);
        if (territory == null) {
            player.sendSystemMessage(Component.literal("Your guild does not have a territory yet.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        if (!territory.isReady() && !player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal(TerritoryRepository.isDeleting(territory) ? "That guild territory is being deleted." : "Your guild territory is being prepared. Try again shortly.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        if (!TerritoryRepository.canEnter(player, territory)) {
            player.sendSystemMessage(Component.literal("You cannot enter that guild territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!TerritoryTeleportUtil.teleportHome(player, territory)) {
            player.sendSystemMessage(Component.literal("Your guild territory is not ready yet. Try again shortly.").withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private static int create(ServerPlayer player, String name, String tag) {
        String cleanName = GuildRepository.cleanName(name);
        String cleanTag = GuildRepository.cleanTag(tag);

        if (cleanTag == null || cleanTag.isBlank()) {
            player.sendSystemMessage(Component.literal("You must choose a guild tag. Use /guild create <name> <tag>.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (cleanName.length() < 3) {
            player.sendSystemMessage(Component.literal("Guild names must be at least 3 characters.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (cleanTag != null && cleanTag.length() < 2) {
            player.sendSystemMessage(Component.literal("Guild tags must be 2-5 letters/numbers.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!com.champutils.database.DatabaseManager.isEnabled()) {
            player.sendSystemMessage(Component.literal("Guild creation is unavailable right now. Please try again later.").withStyle(ChatFormatting.RED));
            return 0;
        }

        long createCost = GuildConfig.GUILD_CREATION == null ? 10_000L : Math.max(0L, GuildConfig.GUILD_CREATION.createCostCredits);
        player.sendSystemMessage(Component.literal("Creating guild...").withStyle(ChatFormatting.YELLOW));
        EconomyManager.withdrawAsync(player, createCost, "Guild creation: " + cleanName).thenAccept(charge ->
                player.server.execute(() -> {
                    if (!charge.success) {
                        player.sendSystemMessage(Component.literal(charge.error == null ? "You do not have enough credits to create a guild." : charge.error).withStyle(ChatFormatting.RED));
                        return;
                    }
                    GuildRepository.createGuild(player.getUUID(), player.getGameProfile().getName(), cleanName, cleanTag, (success, message) ->
                            player.server.execute(() -> {
                                if (!success && createCost > 0L) EconomyManager.depositAsync(player, createCost, "Refund failed guild creation: " + cleanName);
                                if (success) AdventureGuideManager.increment(player, "guild", 1);
                                player.sendSystemMessage(Component.literal(message + (success && createCost > 0L ? " Cost: " + EconomyManager.format(createCost) + "." : "")).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
                            }));
                }));
        return 1;
    }

    private static int setCreateCooldown(ServerPlayer player, long minutes) {
        if (GuildConfig.GUILD_CREATION == null) {
            GuildConfig.GUILD_CREATION = new GuildConfig.GuildCreation();
        }
        GuildConfig.GUILD_CREATION.disbandCreateCooldownMinutes = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, minutes));
        GuildConfig.save();
        player.sendSystemMessage(Component.literal("Guild disband/create cooldown set to " + GuildConfig.GUILD_CREATION.disbandCreateCooldownMinutes + " minute(s).").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int setCreateCost(ServerPlayer player, long credits) {
        if (GuildConfig.GUILD_CREATION == null) {
            GuildConfig.GUILD_CREATION = new GuildConfig.GuildCreation();
        }
        GuildConfig.GUILD_CREATION.createCostCredits = Math.max(0L, credits);
        GuildConfig.save();
        player.sendSystemMessage(Component.literal("Guild creation cost set to " + EconomyManager.format(GuildConfig.GUILD_CREATION.createCostCredits) + ".").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int invite(ServerPlayer inviter, String targetName) {
        return resolveTarget(inviter, targetName, target -> {
            GuildRepository.invite(inviter.getUUID(), inviter.getGameProfile().getName(), target.playerUuid(), target.playerName(), (success, message) ->
                    inviter.server.execute(() -> {
                        inviter.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
                        if (success) {
                            GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(inviter.getUUID());
                            NetworkEventManager.sendPlayerNotice(inviter.server, target.playerUuid(), "§6" + inviter.getGameProfile().getName() + " invited you to join " + (guild == null ? "their guild" : guild.name) + ". Use /guild accept or /guild deny.");
                            if (guild != null) NetworkEventManager.publishCacheInvalidation("GUILD", guild.id);
                        }
                    })
            );
        });
    }

    private static int accept(ServerPlayer player) {
        if (!databaseReady(player)) return 0;
        GuildRepository.acceptInvite(player.getUUID(), player.getGameProfile().getName(), (success, message) ->
                player.server.execute(() -> {
                    player.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
                    if (success) {
                        AdventureGuideManager.increment(player, "guild", 1);
                        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(player.getUUID());
                        if (guild != null) {
                            String joinMessage = "§a" + player.getGameProfile().getName() + " has joined the guild!";
                            for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
                                GuildRepository.GuildSnapshot onlineGuild = GuildRepository.cachedGuild(online.getUUID());
                                if (onlineGuild != null && guild.id.equals(onlineGuild.id)) {
                                    online.sendSystemMessage(Component.literal(joinMessage));
                                }
                            }
                            // Origin-server events are intentionally ignored by the network bus, so local
                            // members are notified above and this event reaches members on other servers.
                            NetworkEventManager.publishGuildNotice(guild.id, joinMessage);
                            NetworkEventManager.publishCacheInvalidation("GUILD", guild.id);
                        }
                    }
                })
        );
        return 1;
    }

    private static int deny(ServerPlayer player) {
        if (!databaseReady(player)) return 0;
        GuildRepository.denyInvites(player.getUUID(), (success, message) ->
                player.server.execute(() -> player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.YELLOW)))
        );
        return 1;
    }

    private static int leave(ServerPlayer player) {
        if (!databaseReady(player)) return 0;
        GuildRepository.GuildSnapshot before = GuildRepository.cachedGuild(player.getUUID());
        GuildRepository.leave(player.getUUID(), player.getGameProfile().getName(), (success, message) ->
                player.server.execute(() -> {
                    player.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
                    if (success && before != null) {
                        NetworkEventManager.publishGuildNotice(before.id, "§e" + player.getGameProfile().getName() + " left the guild.");
                        NetworkEventManager.publishCacheInvalidation("GUILD", before.id);
                    }
                }))
        ;
        return 1;
    }

    private static int kick(ServerPlayer actor, String targetName) {
        return resolveTarget(actor, targetName, target -> {
            GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(actor.getUUID());
            GuildRepository.kick(actor.getUUID(), target.playerUuid(), target.playerName(), (success, message) ->
                    actor.server.execute(() -> {
                        actor.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
                        if (success) {
                            NetworkEventManager.sendPlayerNotice(actor.server, target.playerUuid(), "§cYou were kicked from your guild.");
                            if (guild != null) NetworkEventManager.publishCacheInvalidation("GUILD", guild.id);
                        }
                    })
            );
        });
    }

    private static int promote(ServerPlayer actor, String targetName) {
        return changeRole(actor, targetName, true);
    }

    private static int demote(ServerPlayer actor, String targetName) {
        return changeRole(actor, targetName, false);
    }

    private static int changeRole(ServerPlayer actor, String targetName, boolean promote) {
        return resolveTarget(actor, targetName, target -> {
            GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(actor.getUUID());
            GuildRepository.Callback callback = (success, message) -> actor.server.execute(() -> {
                actor.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
                if (success) {
                    NetworkEventManager.sendPlayerNotice(actor.server, target.playerUuid(), "§6Your guild role changed. Use /guild info to check it.");
                    if (guild != null) NetworkEventManager.publishCacheInvalidation("GUILD", guild.id);
                }
            });
            if (promote) GuildRepository.promote(actor.getUUID(), target.playerUuid(), target.playerName(), callback);
            else GuildRepository.demote(actor.getUUID(), target.playerUuid(), target.playerName(), callback);
        });
    }

    private static int requestTransfer(ServerPlayer actor, String targetName) {
        if (!databaseReady(actor)) return 0;
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(actor.getUUID());
        if (guild == null) {
            actor.sendSystemMessage(Component.literal("You are not in a guild.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (guild.role != GuildRepository.Role.LEADER) {
            actor.sendSystemMessage(Component.literal("Only guild owners can transfer guild ownership.").withStyle(ChatFormatting.RED));
            return 0;
        }
        return resolveTarget(actor, targetName, target -> {
            if (actor.getUUID().equals(target.playerUuid())) {
                actor.sendSystemMessage(Component.literal("You already own this guild.").withStyle(ChatFormatting.YELLOW));
                return;
            }
            PendingGuildAction pending = PendingGuildAction.transfer(guild.id, target.playerUuid(), target.playerName());
            PENDING_ACTIONS.put(actor.getUUID(), pending);
            ConfirmationMenu.open(
                    actor,
                    "Confirm Guild Transfer",
                    Items.GOLDEN_HELMET,
                    "§eTransfer Guild Ownership",
                    new String[]{
                            "§7Guild: §f" + guild.name,
                            "§7New Owner: §f" + pending.targetName,
                            "§cYou will become an OFFICER.",
                            "§cThis is a major guild action."
                    },
                    () -> confirmTransfer(actor),
                    () -> cancelPending(actor)
            );
        });
    }

    private static int resolveTarget(ServerPlayer actor, String targetName, java.util.function.Consumer<NetworkPlayerDirectory.PlayerIdentity> action) {
        if (!databaseReady(actor)) return 0;
        if (targetName == null || targetName.isBlank()) {
            actor.sendSystemMessage(Component.literal("Player name is required.").withStyle(ChatFormatting.RED));
            return 0;
        }
        NetworkPlayerDirectory.resolveIdentityAsync(targetName).whenComplete((target, error) -> actor.server.execute(() -> {
            if (error != null || target == null) {
                actor.sendSystemMessage(Component.literal("Player not found on the network.").withStyle(ChatFormatting.RED));
                return;
            }
            action.accept(target);
        }));
        return 1;
    }

    private static int confirmTransfer(ServerPlayer actor) {
        if (!databaseReady(actor)) {
            return 0;
        }
        PendingGuildAction pending = pending(actor, PendingType.TRANSFER);
        if (pending == null) {
            actor.sendSystemMessage(Component.literal("You do not have a guild transfer waiting for confirmation.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        GuildRepository.transferOwnership(actor.getUUID(), pending.guildId, pending.targetUuid, pending.targetName, (success, message) ->
                actor.server.execute(() -> {
                    actor.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
                    if (success) {
                        NetworkEventManager.sendPlayerNotice(actor.server, pending.targetUuid, "§6You are now the owner of your guild.");
                        NetworkEventManager.publishCacheInvalidation("GUILD", pending.guildId);
                    }
                })
        );
        return 1;
    }

    private static int requestDisband(ServerPlayer actor) {
        if (!databaseReady(actor)) {
            return 0;
        }

        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(actor.getUUID());
        if (guild == null) {
            actor.sendSystemMessage(Component.literal("You are not in a guild.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (guild.role != GuildRepository.Role.LEADER) {
            actor.sendSystemMessage(Component.literal("Only guild owners can disband a guild.").withStyle(ChatFormatting.RED));
            return 0;
        }

        PENDING_ACTIONS.put(actor.getUUID(), PendingGuildAction.disband(guild.id, guild.name));
        ConfirmationMenu.open(
                actor,
                "Confirm Guild Disband",
                Items.BARRIER,
                "§cDisband Guild",
                new String[]{
                        "§7Guild: §f" + guild.name,
                        "§cThis removes the guild, members, invites, and guild territory.",
                        "§cThis cannot be undone."
                },
                () -> confirmDisband(actor),
                () -> cancelPending(actor)
        );
        return 1;
    }

    private static int confirmDisband(ServerPlayer actor) {
        if (!databaseReady(actor)) {
            return 0;
        }
        PendingGuildAction pending = pending(actor, PendingType.DISBAND);
        if (pending == null) {
            actor.sendSystemMessage(Component.literal("You do not have a guild disband waiting for confirmation.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        TerritoryRepository.Territory guildTerritory = TerritoryRepository.cachedForOwner(TerritoryRepository.OwnerType.GUILD, pending.guildId.toString());
        if (guildTerritory != null && !TerritoryRepository.isDeleting(guildTerritory)) {
            TerritoryRegionWipeManager.enqueueDelete(actor, guildTerritory);
        }

        GuildRepository.disbandGuild(actor.getUUID(), pending.guildId, pending.guildName, (success, message) ->
                actor.server.execute(() -> {
                    actor.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
                    if (success) {
                        TerritoryRepository.refreshAll();
                        com.champutils.teleport.DefaultSpawnManager.teleportToSpawn(actor);
                    }
                })
        );
        return 1;
    }

    private static int cancelPending(ServerPlayer actor) {
        PendingGuildAction removed = PENDING_ACTIONS.remove(actor.getUUID());
        if (removed == null) {
            actor.sendSystemMessage(Component.literal("You do not have a guild action waiting for confirmation.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        actor.sendSystemMessage(Component.literal("Canceled pending guild action.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static PendingGuildAction pending(ServerPlayer actor, PendingType type) {
        PendingGuildAction pending = PENDING_ACTIONS.get(actor.getUUID());
        if (pending == null || pending.type != type) {
            return null;
        }
        if (pending.expiresAtMillis < System.currentTimeMillis()) {
            PENDING_ACTIONS.remove(actor.getUUID());
            actor.sendSystemMessage(Component.literal("That guild confirmation expired. Run the command again if you still want to do it.").withStyle(ChatFormatting.YELLOW));
            return null;
        }
        PENDING_ACTIONS.remove(actor.getUUID());
        return pending;
    }

    private static int guildChat(ServerPlayer player, String message) {
        return com.champutils.chat.ServerChatManager.send(player, com.champutils.chat.ChatMode.GUILD, message, true) ? 1 : 0;
    }

    private static int info(ServerPlayer player) {
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(player.getUUID());

        if (guild == null) {
            player.sendSystemMessage(Component.literal("You are not in a guild. Use /guild create <name> [tag].").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        long currentLevelXp = GuildConfig.xpIntoCurrentLevel(guild.xp);
        long neededForNext = GuildConfig.xpNeededForNextLevel(guild.xp);
        String xpLine = neededForNext <= 0
                ? "XP: " + guild.xp + " / MAX"
                : "XP: " + currentLevelXp + " / " + neededForNext;

        player.sendSystemMessage(Component.literal("Guild: " + guild.name + (guild.tag == null ? "" : " [" + guild.tag + "]")).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("Guild Level: " + guild.level).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal(xpLine + " | Total XP: " + guild.xp).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Members: " + guild.memberCount + " | Your role: " + guild.role).withStyle(ChatFormatting.GRAY));

        java.util.List<Component> activeBuffs = GuildBuffManager.activeBuffLines(player.getUUID());
        java.util.List<Component> nextBuffs = GuildBuffManager.nextBuffLines(player.getUUID());
        player.sendSystemMessage(Component.literal("Guild Buffs").withStyle(ChatFormatting.GOLD));
        if (activeBuffs.isEmpty()) {
            player.sendSystemMessage(Component.literal("✦ No active buffs yet.").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (Component line : activeBuffs) player.sendSystemMessage(line);
        }
        for (Component line : nextBuffs) player.sendSystemMessage(line);
        return 1;
    }

    private static int buffs(ServerPlayer player) {
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(player.getUUID());
        if (guild == null) {
            player.sendSystemMessage(Component.literal("You are not in a guild. Use /guild create <name> [tag].").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        java.util.List<Component> activeBuffs = GuildBuffManager.activeBuffLines(player.getUUID());
        java.util.List<Component> nextBuffs = GuildBuffManager.nextBuffLines(player.getUUID());

        player.sendSystemMessage(Component.literal("Guild Buffs - " + guild.name + " Lv. " + guild.level).withStyle(ChatFormatting.GOLD));
        if (activeBuffs.isEmpty()) {
            player.sendSystemMessage(Component.literal("✦ No active buffs yet.").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (Component line : activeBuffs) player.sendSystemMessage(line);
        }

        if (!nextBuffs.isEmpty()) {
            player.sendSystemMessage(Component.literal("Upcoming Guild Buffs").withStyle(ChatFormatting.GRAY));
            for (Component line : nextBuffs) player.sendSystemMessage(line);
        }
        return 1;
    }

    private static boolean databaseReady(ServerPlayer player) {
        if (!com.champutils.database.DatabaseManager.isEnabled()) {
            player.sendSystemMessage(Component.literal("Guild actions are unavailable right now. Please try again later.").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }


    private enum PendingType {
        TRANSFER,
        DISBAND
    }

    private static final class PendingGuildAction {
        private final PendingType type;
        private final UUID guildId;
        private final UUID targetUuid;
        private final String targetName;
        private final String guildName;
        private final long expiresAtMillis;

        private PendingGuildAction(PendingType type, UUID guildId, UUID targetUuid, String targetName, String guildName) {
            this.type = type;
            this.guildId = guildId;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.guildName = guildName;
            this.expiresAtMillis = System.currentTimeMillis() + CONFIRM_MS;
        }

        private static PendingGuildAction transfer(UUID guildId, UUID targetUuid, String targetName) {
            return new PendingGuildAction(PendingType.TRANSFER, guildId, targetUuid, targetName, null);
        }

        private static PendingGuildAction disband(UUID guildId, String guildName) {
            return new PendingGuildAction(PendingType.DISBAND, guildId, null, null, guildName);
        }
    }
}
