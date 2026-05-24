package com.champutils.territory;

import com.champutils.teleport.SafeTeleportManager;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.UUID;

public final class TerritoryCommand {

    private static final SuggestionProvider<net.minecraft.commands.CommandSourceStack> BIOME_SUGGESTIONS = (context, builder) -> SharedSuggestionProvider.suggest(TerritoryRepository.biomeSuggestions(), builder);

    private TerritoryCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("territory")
                    .executes(context -> infoPersonal(context.getSource().getPlayerOrException()))
                    .then(Commands.literal("info").executes(context -> infoPersonal(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("create")
                            .executes(context -> createPersonal(context.getSource().getPlayerOrException(), null))
                            .then(Commands.argument("biome", StringArgumentType.greedyString())
                                    .suggests(BIOME_SUGGESTIONS)
                                    .executes(context -> createPersonal(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "biome")))))
                    .then(Commands.literal("home").executes(context -> homePersonal(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("border")
                            .executes(context -> borderPersonal(context.getSource().getPlayerOrException(), null))
                            .then(Commands.literal("show").executes(context -> borderPersonal(context.getSource().getPlayerOrException(), true)))
                            .then(Commands.literal("hide").executes(context -> borderPersonal(context.getSource().getPlayerOrException(), false))))
                    .then(Commands.literal("sethome").executes(context -> setHomePersonal(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("settings").executes(context -> settings(context.getSource().getPlayerOrException(), ownPersonal(context.getSource().getPlayerOrException()))))
                    .then(Commands.literal("set")
                            .then(Commands.argument("setting", StringArgumentType.word())
                                    .then(Commands.argument("value", BoolArgumentType.bool())
                                            .executes(context -> setPersonal(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "setting"), BoolArgumentType.getBool(context, "value"))))))
                    .then(Commands.literal("biome")
                            .then(Commands.argument("biome", StringArgumentType.greedyString())
                                    .suggests(BIOME_SUGGESTIONS)
                                    .executes(context -> biomePersonal(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "biome")))))
                    .then(Commands.literal("trust")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> trustPersonal(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"), TerritoryRepository.TrustLevel.TRUSTED))
                                    .then(Commands.argument("level", StringArgumentType.word())
                                            .executes(context -> trustPersonal(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"), parseTrust(StringArgumentType.getString(context, "level")))))))
                    .then(Commands.literal("untrust")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> untrustPersonal(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player")))))
                    .then(Commands.literal("ban")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> banPersonal(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player")))))
                    .then(Commands.literal("unban")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> untrustPersonal(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player")))))
                    .then(Commands.literal("kick")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> kickFromTerritory(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"), ownPersonal(context.getSource().getPlayerOrException())))))
                    .then(Commands.literal("delete")
                            .executes(context -> deletePersonal(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("admin")
                            .requires(source -> source.hasPermission(4))
                            .then(Commands.literal("ready")
                                    .then(Commands.argument("territoryId", StringArgumentType.word())
                                            .executes(context -> markReady(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "territoryId")))))
                            .then(Commands.literal("generate")
                                    .then(Commands.argument("territoryId", StringArgumentType.word())
                                            .executes(context -> requestGeneration(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "territoryId")))))
                            .then(Commands.literal("cooldownminutes")
                                    .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 10080))
                                            .executes(context -> setRecreateCooldown(context.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(context, "minutes"))))))
                    .then(Commands.literal("reloadcache")
                            .requires(source -> source.hasPermission(4))
                            .executes(context -> {
                                TerritoryConfig.load();
                                TerritoryRepository.refreshAll();
                                context.getSource().sendSuccess(() -> Component.literal("Reloading territory config/cache from database."), false);
                                return 1;
                            })));

            dispatcher.register(Commands.literal("pterritories")
                    .executes(context -> {
                        TerritoryMenus.open(context.getSource().getPlayerOrException(), TerritoryRepository.OwnerType.PLAYER);
                        return 1;
                    }));

            dispatcher.register(Commands.literal("gterritories")
                    .executes(context -> {
                        TerritoryMenus.open(context.getSource().getPlayerOrException(), TerritoryRepository.OwnerType.GUILD);
                        return 1;
                    }));

            dispatcher.register(Commands.literal("gterritory")
                    .executes(context -> infoGuild(context.getSource().getPlayerOrException()))
                    .then(Commands.literal("info").executes(context -> infoGuild(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("home").executes(context -> homeGuild(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("border")
                            .executes(context -> borderGuild(context.getSource().getPlayerOrException(), null))
                            .then(Commands.literal("show").executes(context -> borderGuild(context.getSource().getPlayerOrException(), true)))
                            .then(Commands.literal("hide").executes(context -> borderGuild(context.getSource().getPlayerOrException(), false))))
                    .then(Commands.literal("create")
                            .executes(context -> ensureGuild(context.getSource().getPlayerOrException(), null))
                            .then(Commands.argument("biome", StringArgumentType.greedyString())
                                    .suggests(BIOME_SUGGESTIONS)
                                    .executes(context -> ensureGuild(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "biome")))))
                    .then(Commands.literal("sethome").executes(context -> setHomeGuild(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("settings").executes(context -> settings(context.getSource().getPlayerOrException(), ownGuild(context.getSource().getPlayerOrException()))))
                    .then(Commands.literal("set")
                            .then(Commands.argument("setting", StringArgumentType.word())
                                    .then(Commands.argument("value", BoolArgumentType.bool())
                                            .executes(context -> setGuild(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "setting"), BoolArgumentType.getBool(context, "value"))))))
                    .then(Commands.literal("biome")
                            .then(Commands.argument("biome", StringArgumentType.greedyString())
                                    .suggests(BIOME_SUGGESTIONS)
                                    .executes(context -> biomeGuild(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "biome")))))
                    .then(Commands.literal("ban")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> banGuild(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player")))))
                    .then(Commands.literal("unban")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> unbanGuild(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player")))))
                    .then(Commands.literal("kick")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> kickFromTerritory(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"), ownGuild(context.getSource().getPlayerOrException())))))
                    .then(Commands.literal("delete")
                            .executes(context -> deleteGuild(context.getSource().getPlayerOrException()))));
        });
    }

    private static TerritoryRepository.Territory ownPersonal(ServerPlayer player) {
        return TerritoryRepository.cachedPersonal(player);
    }

    private static TerritoryRepository.Territory ownGuild(ServerPlayer player) {
        return TerritoryRepository.cachedGuildForPlayer(player);
    }

    private static int deletePersonal(ServerPlayer player) {
        TerritoryRepository.Territory territory = ownPersonal(player);
        if (territory == null) {
            player.sendSystemMessage(Component.literal("You do not have a personal territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!TerritoryRepository.canManage(player, territory)) {
            player.sendSystemMessage(Component.literal("You cannot delete this territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        TerritoryRepository.deleteTerritory(territory, (success, message) -> player.server.execute(() -> player.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED))));
        return 1;
    }

    private static int deleteGuild(ServerPlayer player) {
        TerritoryRepository.Territory territory = ownGuild(player);
        if (territory == null) {
            player.sendSystemMessage(Component.literal("Your guild does not have a territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!player.hasPermissions(4) && !TerritoryRepository.canManage(player, territory)) {
            player.sendSystemMessage(Component.literal("Only guild leaders/officers can delete the guild territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        TerritoryRepository.deleteTerritory(territory, (success, message) -> player.server.execute(() -> player.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED))));
        return 1;
    }

    private static int markReady(ServerPlayer player, String rawId) {
        try {
            UUID id = UUID.fromString(rawId);
            TerritoryRepository.markReady(id, (success, message) -> player.server.execute(() -> player.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED))));
            return 1;
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Invalid territory UUID.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int requestGeneration(ServerPlayer player, String rawId) {
        try {
            UUID id = UUID.fromString(rawId);
            TerritoryRepository.Territory territory = TerritoryRepository.allCached().stream().filter(t -> id.equals(t.id)).findFirst().orElse(null);
            if (territory == null) {
                player.sendSystemMessage(Component.literal("Territory not found.").withStyle(ChatFormatting.RED));
                return 0;
            }
            TerritoryWorldGenerationManager.requestGeneration(player.server, player, territory);
            player.sendSystemMessage(Component.literal("Requested generation for " + territory.worldName + " slot " + territory.slotIndex + ".").withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Invalid territory UUID.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setRecreateCooldown(ServerPlayer player, int minutes) {
        TerritoryConfig.setRecreateCooldownMinutes(minutes);
        player.sendSystemMessage(Component.literal("Territory recreate cooldown set to " + minutes + " minute" + (minutes == 1 ? "" : "s") + ". Saved to config/champutils/territories.json.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int createPersonal(ServerPlayer player, String biome) {
        if (!databaseReady(player)) return 0;
        player.sendSystemMessage(Component.literal("Creating your personal territory...").withStyle(ChatFormatting.YELLOW));
        TerritoryRepository.createPersonalTerritory(player, biome, (success, message) -> player.server.execute(() -> player.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED))));
        return 1;
    }

    private static int ensureGuild(ServerPlayer player, String biome) {
        if (!databaseReady(player)) return 0;
        com.champutils.guild.GuildRepository.GuildSnapshot guild = com.champutils.guild.GuildRepository.cachedGuild(player.getUUID());
        if (guild == null) {
            player.sendSystemMessage(Component.literal("You are not in a guild.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!player.hasPermissions(4) && !com.champutils.guild.GuildRepository.canManageGuildTerritory(guild.role)) {
            player.sendSystemMessage(Component.literal("Only guild leaders/officers can create or configure the guild territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        TerritoryRepository.ensureGuildTerritory(player.server, player, guild.id, guild.name, biome, (success, message) -> player.server.execute(() -> player.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED))));
        return 1;
    }

    private static int homePersonal(ServerPlayer player) {
        TerritoryRepository.Territory territory = ownPersonal(player);
        if (territory == null) {
            player.sendSystemMessage(Component.literal("You do not have a personal territory yet. Use /territory create [biome].").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        if (!territory.isReady() && !player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("That territory world is still being created or loaded. Try again shortly.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        if (!TerritoryTeleportUtil.teleportHome(player, territory)) {
            player.sendSystemMessage(Component.literal("That territory world is not loaded. Check Multiworld world name: " + territory.worldName).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private static int homeGuild(ServerPlayer player) {
        TerritoryRepository.Territory territory = ownGuild(player);
        if (territory == null) {
            player.sendSystemMessage(Component.literal("Your guild does not have a territory yet. Owners/officers can use /gterritory create [biome].").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        if (!territory.isReady() && !player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("That guild territory world is still being created or loaded. Try again shortly.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        if (!TerritoryRepository.canEnter(player, territory)) {
            player.sendSystemMessage(Component.literal("You cannot enter that guild territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!TerritoryTeleportUtil.teleportHome(player, territory)) {
            player.sendSystemMessage(Component.literal("That territory world is not loaded. Check Multiworld world name: " + territory.worldName).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private static int borderPersonal(ServerPlayer player, Boolean show) {
        TerritoryRepository.Territory territory = borderTarget(player, TerritoryRepository.cachedPersonal(player));
        return borderDisplay(player, territory, show);
    }

    private static int borderGuild(ServerPlayer player, Boolean show) {
        TerritoryRepository.Territory territory = borderTarget(player, TerritoryRepository.cachedGuildForPlayer(player));
        return borderDisplay(player, territory, show);
    }

    private static TerritoryRepository.Territory borderTarget(ServerPlayer player, TerritoryRepository.Territory fallback) {
        TerritoryRepository.Territory current = TerritoryRepository.findAt(player.serverLevel(), player.blockPosition());
        if (current != null && TerritoryRepository.canEnter(player, current)) return current;
        return fallback;
    }

    private static int borderDisplay(ServerPlayer player, TerritoryRepository.Territory territory, Boolean show) {
        if (territory == null) {
            player.sendSystemMessage(Component.literal("No territory found to display.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (show == null) TerritoryBorderDisplayManager.toggle(player, territory);
        else if (show) TerritoryBorderDisplayManager.show(player, territory);
        else TerritoryBorderDisplayManager.hide(player);
        return 1;
    }

    private static int setHomePersonal(ServerPlayer player) { return setHome(player, ownPersonal(player)); }
    private static int setHomeGuild(ServerPlayer player) { return setHome(player, ownGuild(player)); }

    private static int setHome(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (territory == null) {
            player.sendSystemMessage(Component.literal("No territory found.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!TerritoryRepository.canManage(player, territory)) {
            player.sendSystemMessage(Component.literal("You cannot manage this territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        TerritoryRepository.setHome(territory, player, (success, message) -> player.server.execute(() -> player.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED))));
        return 1;
    }

    private static int setPersonal(ServerPlayer player, String setting, boolean value) { return set(player, ownPersonal(player), setting, value); }
    private static int setGuild(ServerPlayer player, String setting, boolean value) { return set(player, ownGuild(player), setting, value); }

    private static int set(ServerPlayer player, TerritoryRepository.Territory territory, String setting, boolean value) {
        if (territory == null) {
            player.sendSystemMessage(Component.literal("No territory found.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!TerritoryRepository.canManage(player, territory)) {
            player.sendSystemMessage(Component.literal("You cannot manage this territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        TerritoryRepository.setSetting(territory, setting, value, (success, message) -> player.server.execute(() -> player.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED))));
        return 1;
    }

    private static int biomePersonal(ServerPlayer player, String biome) { return biome(player, ownPersonal(player), biome); }
    private static int biomeGuild(ServerPlayer player, String biome) { return biome(player, ownGuild(player), biome); }

    private static int biome(ServerPlayer player, TerritoryRepository.Territory territory, String biome) {
        if (territory == null) {
            player.sendSystemMessage(Component.literal("No territory found.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!TerritoryRepository.canManage(player, territory)) {
            player.sendSystemMessage(Component.literal("You cannot manage this territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        TerritoryRepository.setBiomePreference(territory, biome, (success, message) -> player.server.execute(() -> player.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED))));
        return 1;
    }

    private static int trustPersonal(ServerPlayer owner, ServerPlayer target, TerritoryRepository.TrustLevel level) {
        TerritoryRepository.Territory territory = ownPersonal(owner);
        if (territory == null) {
            owner.sendSystemMessage(Component.literal("You do not have a personal territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!TerritoryRepository.canManage(owner, territory)) {
            owner.sendSystemMessage(Component.literal("You cannot manage this territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        TerritoryRepository.setTrust(territory, target.getUUID(), target.getGameProfile().getName(), level, (success, message) -> owner.server.execute(() -> owner.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED))));
        return 1;
    }

    private static int untrustPersonal(ServerPlayer owner, ServerPlayer target) {
        TerritoryRepository.Territory territory = ownPersonal(owner);
        if (territory == null) {
            owner.sendSystemMessage(Component.literal("You do not have a personal territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!TerritoryRepository.canManage(owner, territory)) {
            owner.sendSystemMessage(Component.literal("You cannot manage this territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        TerritoryRepository.removeTrust(territory, target.getUUID(), target.getGameProfile().getName(), (success, message) -> owner.server.execute(() -> owner.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED))));
        return 1;
    }


    private static int banPersonal(ServerPlayer owner, ServerPlayer target) {
        return ban(owner, target, ownPersonal(owner));
    }

    private static int banGuild(ServerPlayer actor, ServerPlayer target) {
        return ban(actor, target, ownGuild(actor));
    }

    private static int unbanGuild(ServerPlayer actor, ServerPlayer target) {
        TerritoryRepository.Territory territory = ownGuild(actor);
        if (territory == null) {
            actor.sendSystemMessage(Component.literal("Your guild does not have a territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!TerritoryRepository.canManage(actor, territory)) {
            actor.sendSystemMessage(Component.literal("You cannot manage this territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        TerritoryRepository.removeTrust(territory, target.getUUID(), target.getGameProfile().getName(), (success, message) -> actor.server.execute(() -> actor.sendSystemMessage(Component.literal(message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED))));
        return 1;
    }

    private static int ban(ServerPlayer actor, ServerPlayer target, TerritoryRepository.Territory territory) {
        if (territory == null) {
            actor.sendSystemMessage(Component.literal("No territory found.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!TerritoryRepository.canManage(actor, territory)) {
            actor.sendSystemMessage(Component.literal("You cannot manage this territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (TerritoryRepository.isOwnerOrGuildMember(target, territory)) {
            actor.sendSystemMessage(Component.literal("You cannot ban the owner or a guild member from their own territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        TerritoryRepository.setTrust(territory, target.getUUID(), target.getGameProfile().getName(), TerritoryRepository.TrustLevel.BANNED, (success, message) -> actor.server.execute(() -> {
            actor.sendSystemMessage(Component.literal(success ? target.getGameProfile().getName() + " is banned from this territory." : message).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
            if (success && TerritoryRepository.findAt(target.serverLevel(), target.blockPosition()) != null && territory.id.equals(TerritoryRepository.findAt(target.serverLevel(), target.blockPosition()).id)) {
                teleportOut(target);
                target.sendSystemMessage(Component.literal("You were banned from " + territory.ownerName + "'s territory.").withStyle(ChatFormatting.RED));
            }
        }));
        return 1;
    }

    private static int kickFromTerritory(ServerPlayer actor, ServerPlayer target, TerritoryRepository.Territory territory) {
        if (territory == null) {
            actor.sendSystemMessage(Component.literal("No territory found.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!TerritoryRepository.canManage(actor, territory)) {
            actor.sendSystemMessage(Component.literal("You cannot manage this territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        TerritoryRepository.Territory current = TerritoryRepository.findAt(target.serverLevel(), target.blockPosition());
        if (current == null || !territory.id.equals(current.id)) {
            actor.sendSystemMessage(Component.literal(target.getGameProfile().getName() + " is not inside this territory.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        if (TerritoryRepository.isOwnerOrGuildMember(target, territory)) {
            actor.sendSystemMessage(Component.literal("You cannot kick the owner or a guild member from their own territory.").withStyle(ChatFormatting.RED));
            return 0;
        }
        teleportOut(target);
        actor.sendSystemMessage(Component.literal("Kicked " + target.getGameProfile().getName() + " out of this territory.").withStyle(ChatFormatting.GREEN));
        target.sendSystemMessage(Component.literal("You were kicked out of " + territory.ownerName + "'s territory.").withStyle(ChatFormatting.RED));
        return 1;
    }

    private static void teleportOut(ServerPlayer player) {
        TerritoryRepository.Territory personal = TerritoryRepository.cachedPersonal(player);
        if (personal != null && TerritoryTeleportUtil.teleportHome(player, personal)) return;
        TerritoryRepository.Territory guild = TerritoryRepository.cachedGuildForPlayer(player);
        if (guild != null && TerritoryTeleportUtil.teleportHome(player, guild)) return;
        SafeTeleportManager.teleportUncheckedNoBack(player, player.server.overworld(), player.server.overworld().getSharedSpawnPos().getX() + 0.5D, player.server.overworld().getSharedSpawnPos().getY(), player.server.overworld().getSharedSpawnPos().getZ() + 0.5D, player.getYRot(), player.getXRot());
    }

    private static int infoPersonal(ServerPlayer player) { return info(player, ownPersonal(player), "Personal Territory", "Use /territory create [biome]."); }
    private static int infoGuild(ServerPlayer player) { return info(player, ownGuild(player), "Guild Territory", "Your guild does not have a territory yet."); }

    private static int info(ServerPlayer player, TerritoryRepository.Territory territory, String title, String missing) {
        if (territory == null) {
            player.sendSystemMessage(Component.literal(missing).withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        player.sendSystemMessage(Component.literal(title + " - " + territory.ownerName).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("World: " + territory.worldName + " | Slot: " + territory.slotIndex + " | Center: " + territory.centerX + ", " + territory.centerZ + " | Radius: " + territory.radius).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("World key: " + (territory.worldKey == null ? territory.worldName : territory.worldKey) + " | Generation: " + (territory.generationState == null ? "READY" : territory.generationState) + " | ID: " + territory.id).withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(Component.literal("Biome preference: " + TerritoryRepository.prettyBiome(territory.biomePreference)).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Public: " + territory.isPublic + " | Visitors: " + territory.allowVisitors + " | Border lock: " + territory.lockBorder).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Visitor permissions: build=" + territory.visitorsCanBuild + ", containers=" + territory.visitorsCanOpenContainers + ", entities=" + territory.visitorsCanInteractEntities + ", redstone=" + territory.visitorsCanUseRedstone).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int settings(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (territory == null) {
            player.sendSystemMessage(Component.literal("No territory found.").withStyle(ChatFormatting.RED));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Territory Settings").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("/territory set public true|false - Show in /pterritories").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("/territory set visitors true|false - Allow public visitors to enter").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("/territory set visitorbuild true|false - Visitors may build").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("/territory set visitorcontainers true|false - Visitors may open containers").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("/territory set visitorentities true|false - Visitors may interact with entities").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("/territory set visitorredstone true|false - Visitors may use redstone/buttons/levers").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("/territory set border true|false - Lock players inside the territory border").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("/territory border show|hide - Display or hide a particle outline of the border").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("/territory trust|untrust|ban|unban|kick <player> - Manage player access").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("/territory delete - Delete your personal territory and free the packed slot").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("/gterritory ban|unban|kick <player> - Guild territory access control").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static TerritoryRepository.TrustLevel parseTrust(String raw) {
        try { return TerritoryRepository.TrustLevel.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return TerritoryRepository.TrustLevel.TRUSTED; }
    }

    private static boolean databaseReady(ServerPlayer player) {
        if (!com.champutils.database.DatabaseManager.isEnabled()) {
            player.sendSystemMessage(Component.literal("The database is not connected, so territory actions are unavailable.").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }
}
