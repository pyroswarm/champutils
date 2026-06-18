package com.champutils.claims;

import com.champutils.economy.EconomyManager;
import com.champutils.economy.EconomyManager.TransactionResult;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.teleport.SafeTeleportManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.List;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

public final class LandClaimCommand {
    private static final Map<UUID, Selection> SELECTIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingClaim> PENDING = new ConcurrentHashMap<>();

    private LandClaimCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("claims")
                    .executes(context -> info(context.getSource().getPlayerOrException()))
                    .then(literal("pos1").executes(context -> setPos(context.getSource().getPlayerOrException(), true)))
                    .then(literal("pos2").executes(context -> setPos(context.getSource().getPlayerOrException(), false)))
                    .then(literal("claim").executes(context -> preview(context.getSource().getPlayerOrException())))
                    .then(literal("confirm").executes(context -> confirm(context.getSource().getPlayerOrException())))
                    .then(literal("cancel").executes(context -> cancel(context.getSource().getPlayerOrException())))
                    .then(literal("info").executes(context -> info(context.getSource().getPlayerOrException())))
                    .then(literal("list").executes(context -> listClaims(context.getSource().getPlayerOrException())))
                    .then(literal("tp").then(argument("number", IntegerArgumentType.integer(1)).executes(context -> home(context.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(context, "number")))))
                    .then(literal("settings").executes(context -> { LandClaimSettingsMenu.open(context.getSource().getPlayerOrException()); return 1; }))
                    .then(literal("border").executes(context -> toggleBorder(context.getSource().getPlayerOrException())))
                    .then(literal("friend")
                            .then(literal("add").then(argument("player", EntityArgument.player()).executes(context -> addFriend(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player")))))
                            .then(literal("remove").then(argument("player", EntityArgument.player()).executes(context -> removeFriend(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"))))))
                    .then(literal("extend")
                            .then(literal("north").then(argument("blocks", IntegerArgumentType.integer(1)).executes(context -> extend(context.getSource().getPlayerOrException(), "north", IntegerArgumentType.getInteger(context, "blocks")))))
                            .then(literal("south").then(argument("blocks", IntegerArgumentType.integer(1)).executes(context -> extend(context.getSource().getPlayerOrException(), "south", IntegerArgumentType.getInteger(context, "blocks")))))
                            .then(literal("east").then(argument("blocks", IntegerArgumentType.integer(1)).executes(context -> extend(context.getSource().getPlayerOrException(), "east", IntegerArgumentType.getInteger(context, "blocks")))))
                            .then(literal("west").then(argument("blocks", IntegerArgumentType.integer(1)).executes(context -> extend(context.getSource().getPlayerOrException(), "west", IntegerArgumentType.getInteger(context, "blocks"))))))
                    .then(literal("delete")
                            .executes(context -> deletePrompt(context.getSource().getPlayerOrException()))
                            .then(literal("confirm").executes(context -> deleteConfirm(context.getSource().getPlayerOrException()))))
                    .then(literal("reload")
                            .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                            .executes(context -> reload(context.getSource()))));

            dispatcher.register(literal("claim")
                    .then(literal("home")
                            .then(argument("number", IntegerArgumentType.integer(1))
                                    .executes(context -> home(context.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(context, "number"))))));
        });
    }

    private static int setPos(ServerPlayer player, boolean first) {
        if (!LandClaimConfig.enabled()) {
            player.sendSystemMessage(Component.literal("Land claims are disabled.").withStyle(ChatFormatting.RED));
            return 0;
        }
        Selection selection = SELECTIONS.computeIfAbsent(player.getUUID(), ignored -> new Selection());
        BlockPos pos = player.blockPosition();
        String world = player.serverLevel().dimension().location().toString();
        if (first) {
            selection.pos1 = pos;
            selection.worldName = world;
        } else {
            selection.pos2 = pos;
            if (selection.worldName == null) selection.worldName = world;
        }
        player.sendSystemMessage(Component.literal((first ? "Position 1" : "Position 2") + " set at X " + pos.getX() + ", Z " + pos.getZ() + ".").withStyle(ChatFormatting.GREEN));
        if (selection.pos1 != null && selection.pos2 != null) {
            player.sendSystemMessage(Component.literal("Run /claims claim to preview the cost.").withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static int preview(ServerPlayer player) {
        PendingClaim pending = buildPending(player);
        if (pending == null) return 0;
        PENDING.put(player.getUUID(), pending);
        player.sendSystemMessage(Component.literal("Claim area: " + pending.area + " blocks (X/Z only, all Y levels)." ).withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("Cost: " + EconomyManager.format(pending.costCents) + ". Run /claims confirm to buy this claim.").withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private static int confirm(ServerPlayer player) {
        PendingClaim pending = PENDING.get(player.getUUID());
        if (pending == null) {
            player.sendSystemMessage(Component.literal("Run /claims claim first to preview the cost.").withStyle(ChatFormatting.RED));
            return 0;
        }
        PendingClaim current = buildPending(player);
        if (current == null || !current.sameArea(pending)) {
            PENDING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Your selection changed. Run /claims claim again.").withStyle(ChatFormatting.RED));
            return 0;
        }
        TransactionResult withdraw = EconomyManager.withdraw(player, pending.costCents, "Land claim purchase");
        if (!withdraw.success) {
            player.sendSystemMessage(Component.literal(withdraw.error).withStyle(ChatFormatting.RED));
            return 0;
        }
        LandClaimRepository.CreateResult created = LandClaimRepository.create(player, player.serverLevel(), pending.minX, pending.maxX, pending.minZ, pending.maxZ);
        if (!created.success) {
            EconomyManager.deposit(player, pending.costCents, "Land claim refund");
            player.sendSystemMessage(Component.literal(created.message).withStyle(ChatFormatting.RED));
            return 0;
        }
        PENDING.remove(player.getUUID());
        SELECTIONS.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("Land claimed for " + EconomyManager.format(pending.costCents) + ".").withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.literal("Stand inside it and run /claims settings to manage it.").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int cancel(ServerPlayer player) {
        PENDING.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("Pending claim purchase cancelled.").withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int info(ServerPlayer player) {
        LandClaimRepository.Claim claim = LandClaimRepository.findAt(player.serverLevel(), player.blockPosition());
        if (claim == null) {
            player.sendSystemMessage(Component.literal("You are not standing in a land claim.").withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(Component.literal("Use /claims pos1, /claims pos2, then /claims claim.").withStyle(ChatFormatting.GRAY));
            return 1;
        }
        int claimNumber = LandClaimRepository.numberForClaim(claim);
        player.sendSystemMessage(Component.literal("Claim #" + (claimNumber > 0 ? claimNumber : "?") + " owner: " + claim.ownerName).withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("Area: " + claim.area() + " blocks | X " + claim.minX + " to " + claim.maxX + ", Z " + claim.minZ + " to " + claim.maxZ).withStyle(ChatFormatting.GRAY));
        if (claimNumber > 0 && LandClaimRepository.isOwner(player, claim)) {
            player.sendSystemMessage(Component.literal("Home command: /claims tp " + claimNumber).withStyle(ChatFormatting.YELLOW));
        }
        player.sendSystemMessage(Component.literal("Members: " + claim.memberProfileIds.size() + " profile(s). /claims border toggles a visible border.").withStyle(ChatFormatting.GRAY));
        if (LandClaimRepository.isOwner(player, claim)) {
            player.sendSystemMessage(Component.literal("You own this claim on your active profile. Use /claims settings.").withStyle(ChatFormatting.GREEN));
        }
        return 1;
    }

    private static int listClaims(ServerPlayer player) {
        List<LandClaimRepository.Claim> claims = LandClaimRepository.numberedClaimsForProfile(PlayerProfileManager.activeProfileId(player));
        if (claims.isEmpty()) {
            player.sendSystemMessage(Component.literal("You do not have any claims yet.").withStyle(ChatFormatting.YELLOW));
            return 1;
        }
        player.sendSystemMessage(Component.literal("Your claims:").withStyle(ChatFormatting.AQUA));
        for (int i = 0; i < claims.size(); i++) {
            LandClaimRepository.Claim claim = claims.get(i);
            player.sendSystemMessage(Component.literal("#" + (i + 1) + " " + claim.worldName + " X " + claim.minX + " to " + claim.maxX + ", Z " + claim.minZ + " to " + claim.maxZ + " | " + claim.area() + " blocks | /claims tp " + (i + 1)).withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(Component.literal("Total: " + LandClaimRepository.totalAreaCached(PlayerProfileManager.activeProfileId(player)) + "/" + LandClaimConfig.maxTotalClaimBlocksPerProfile() + " blocks, " + claims.size() + "/" + LandClaimConfig.maxClaimsPerProfile(player) + " claims.").withStyle(ChatFormatting.YELLOW));
        return 1;
    }


    private static int toggleBorder(ServerPlayer player) {
        boolean nowEnabled = LandClaimProtectionListener.toggleBorder(player);
        player.sendSystemMessage(Component.literal("Claim border " + (nowEnabled ? "enabled" : "disabled") + ".").withStyle(nowEnabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        return 1;
    }

    private static int addFriend(ServerPlayer player, ServerPlayer friend) {
        LandClaimRepository.Claim claim = LandClaimRepository.findAt(player.serverLevel(), player.blockPosition());
        if (claim == null || !LandClaimRepository.isOwner(player, claim)) {
            player.sendSystemMessage(Component.literal("Stand inside one of your claims to add a friend.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!LandClaimRepository.addMember(player, claim, friend)) {
            player.sendSystemMessage(Component.literal("Could not add that player's current profile to this claim.").withStyle(ChatFormatting.RED));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Added " + friend.getName().getString() + "'s current profile to this claim.").withStyle(ChatFormatting.GREEN));
        friend.sendSystemMessage(Component.literal(player.getName().getString() + " added your current profile to a claim.").withStyle(ChatFormatting.AQUA));
        return 1;
    }

    private static int removeFriend(ServerPlayer player, ServerPlayer friend) {
        LandClaimRepository.Claim claim = LandClaimRepository.findAt(player.serverLevel(), player.blockPosition());
        if (claim == null || !LandClaimRepository.isOwner(player, claim)) {
            player.sendSystemMessage(Component.literal("Stand inside one of your claims to remove a friend.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!LandClaimRepository.removeMember(player, claim, friend)) {
            player.sendSystemMessage(Component.literal("Could not remove that player's current profile from this claim.").withStyle(ChatFormatting.RED));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Removed " + friend.getName().getString() + "'s current profile from this claim.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int extend(ServerPlayer player, String direction, int blocks) {
        LandClaimRepository.Claim claim = LandClaimRepository.findAt(player.serverLevel(), player.blockPosition());
        if (claim == null || !LandClaimRepository.isOwner(player, claim)) {
            player.sendSystemMessage(Component.literal("Stand inside one of your claims to extend it.").withStyle(ChatFormatting.RED));
            return 0;
        }
        int minX = claim.minX;
        int maxX = claim.maxX;
        int minZ = claim.minZ;
        int maxZ = claim.maxZ;
        switch (direction) {
            case "north" -> minZ -= blocks;
            case "south" -> maxZ += blocks;
            case "east" -> maxX += blocks;
            case "west" -> minX -= blocks;
            default -> { return 0; }
        }
        int oldArea = claim.area();
        int newArea = (maxX - minX + 1) * (maxZ - minZ + 1);
        long cost = Math.max(0, newArea - oldArea) * LandClaimConfig.costPerBlockCents();
        if (cost > 0) {
            TransactionResult withdraw = EconomyManager.withdraw(player, cost, "Land claim extension");
            if (!withdraw.success) {
                player.sendSystemMessage(Component.literal(withdraw.error).withStyle(ChatFormatting.RED));
                return 0;
            }
            LandClaimRepository.CreateResult result = LandClaimRepository.resize(player, claim, minX, maxX, minZ, maxZ);
            if (!result.success) {
                EconomyManager.deposit(player, cost, "Land claim extension refund");
                player.sendSystemMessage(Component.literal(result.message).withStyle(ChatFormatting.RED));
                return 0;
            }
            player.sendSystemMessage(Component.literal("Claim extended by " + blocks + " blocks for " + EconomyManager.format(cost) + ". New area: " + newArea + " blocks.").withStyle(ChatFormatting.GREEN));
        } else {
            LandClaimRepository.CreateResult result = LandClaimRepository.resize(player, claim, minX, maxX, minZ, maxZ);
            player.sendSystemMessage(Component.literal(result.message).withStyle(result.success ? ChatFormatting.GREEN : ChatFormatting.RED));
            return result.success ? 1 : 0;
        }
        return 1;
    }

    private static int home(ServerPlayer player, int number) {
        if (!LandClaimConfig.enabled()) {
            player.sendSystemMessage(Component.literal("Land claims are disabled.").withStyle(ChatFormatting.RED));
            return 0;
        }
        List<LandClaimRepository.Claim> claims = LandClaimRepository.numberedClaimsForProfile(PlayerProfileManager.activeProfileId(player));
        if (number < 1 || number > claims.size()) {
            player.sendSystemMessage(Component.literal("You do not have claim #" + number + ". You currently have " + claims.size() + " claim(s).").withStyle(ChatFormatting.RED));
            return 0;
        }
        LandClaimRepository.Claim claim = claims.get(number - 1);
        ServerLevel targetLevel = null;
        for (ServerLevel level : player.server.getAllLevels()) {
            if (level.dimension().location().toString().equalsIgnoreCase(claim.worldName)) {
                targetLevel = level;
                break;
            }
        }
        if (targetLevel == null) {
            player.sendSystemMessage(Component.literal("Could not find that claim's world: " + claim.worldName).withStyle(ChatFormatting.RED));
            return 0;
        }
        int x = claim.minX + ((claim.maxX - claim.minX) / 2);
        int z = claim.minZ + ((claim.maxZ - claim.minZ) / 2);
        BlockPos surface = targetLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, targetLevel.getMinBuildHeight(), z));
        double tx = surface.getX() + 0.5D;
        double ty = Math.max(targetLevel.getMinBuildHeight() + 1, surface.getY() + 1);
        double tz = surface.getZ() + 0.5D;
        if (!SafeTeleportManager.teleport(player, targetLevel, tx, ty, tz, player.getYRot(), player.getXRot())) return 0;
        player.sendSystemMessage(Component.literal("Teleported to claim #" + number + ".").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int deletePrompt(ServerPlayer player) {
        LandClaimRepository.Claim claim = LandClaimRepository.findAt(player.serverLevel(), player.blockPosition());
        if (claim == null || !LandClaimRepository.isOwner(player, claim)) {
            player.sendSystemMessage(Component.literal("Stand inside one of your claims to delete it.").withStyle(ChatFormatting.RED));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Run /claims delete confirm to permanently delete this claim. No refund is given.").withStyle(ChatFormatting.RED));
        return 1;
    }

    private static int deleteConfirm(ServerPlayer player) {
        LandClaimRepository.Claim claim = LandClaimRepository.findAt(player.serverLevel(), player.blockPosition());
        if (claim == null || !LandClaimRepository.isOwner(player, claim)) {
            player.sendSystemMessage(Component.literal("Stand inside one of your claims to delete it.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!LandClaimRepository.delete(player, claim)) {
            player.sendSystemMessage(Component.literal("Could not delete claim.").withStyle(ChatFormatting.RED));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Claim deleted.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int reload(net.minecraft.commands.CommandSourceStack source) {
        LandClaimConfig.load();
        LandClaimRepository.refreshAll();
        source.sendSuccess(() -> Component.literal("Reloaded land_claims.json and land claim cache.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static PendingClaim buildPending(ServerPlayer player) {
        Selection selection = SELECTIONS.get(player.getUUID());
        if (selection == null || selection.pos1 == null || selection.pos2 == null) {
            player.sendSystemMessage(Component.literal("Set both positions first with /claims pos1 and /claims pos2.").withStyle(ChatFormatting.RED));
            return null;
        }
        ServerLevel level = player.serverLevel();
        String world = level.dimension().location().toString();
        if (selection.worldName != null && !selection.worldName.equals(world)) {
            player.sendSystemMessage(Component.literal("Both claim positions must be in the same world.").withStyle(ChatFormatting.RED));
            return null;
        }
        int minX = Math.min(selection.pos1.getX(), selection.pos2.getX());
        int maxX = Math.max(selection.pos1.getX(), selection.pos2.getX());
        int minZ = Math.min(selection.pos1.getZ(), selection.pos2.getZ());
        int maxZ = Math.max(selection.pos1.getZ(), selection.pos2.getZ());
        int area = (maxX - minX + 1) * (maxZ - minZ + 1);
        if (area < LandClaimConfig.minArea()) {
            player.sendSystemMessage(Component.literal("Claim is too small. Minimum area is " + LandClaimConfig.minArea() + " blocks.").withStyle(ChatFormatting.RED));
            return null;
        }
        if (area > LandClaimConfig.maxArea()) {
            player.sendSystemMessage(Component.literal("Claim is too large. Maximum area is " + LandClaimConfig.maxArea() + " blocks.").withStyle(ChatFormatting.RED));
            return null;
        }
        if (LandClaimRepository.cachedForProfile(PlayerProfileManager.activeProfileId(player)).size() >= LandClaimConfig.maxClaimsPerProfile(player)) {
            player.sendSystemMessage(Component.literal("This profile already has the maximum number of claims. Cap: " + LandClaimConfig.maxClaimsPerProfile(player)).withStyle(ChatFormatting.RED));
            return null;
        }
        if (LandClaimRepository.overlapsCached(level, minX, maxX, minZ, maxZ)) {
            player.sendSystemMessage(Component.literal("That area overlaps an existing claim.").withStyle(ChatFormatting.RED));
            return null;
        }
        if (LandClaimRepository.totalAreaCached(PlayerProfileManager.activeProfileId(player)) + area > LandClaimConfig.maxTotalClaimBlocksPerProfile()) {
            player.sendSystemMessage(Component.literal("This profile can only claim " + LandClaimConfig.maxTotalClaimBlocksPerProfile() + " total blocks.").withStyle(ChatFormatting.RED));
            return null;
        }
        long cost = area * LandClaimConfig.costPerBlockCents();
        return new PendingClaim(world, minX, maxX, minZ, maxZ, area, cost);
    }

    private static final class Selection {
        BlockPos pos1;
        BlockPos pos2;
        String worldName;
    }

    private record PendingClaim(String worldName, int minX, int maxX, int minZ, int maxZ, int area, long costCents) {
        boolean sameArea(PendingClaim other) {
            return other != null && worldName.equals(other.worldName) && minX == other.minX && maxX == other.maxX && minZ == other.minZ && maxZ == other.maxZ;
        }
    }
}
