package com.champutils.gym;

import com.champutils.badge.BadgeManager;
import com.champutils.badge.BadgeType;
import com.champutils.economy.EconomyManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import static net.minecraft.commands.Commands.argument;import static net.minecraft.commands.Commands.literal;

public final class GymRewardCommand {
    private GymRewardCommand() {}
    public static void register(){ CommandRegistrationCallback.EVENT.register((dispatcher,r,e)->dispatcher.register(literal("gymrewards").requires(source -> source.hasPermission(4))
        .executes(ctx->{ list(ctx.getSource().getPlayerOrException()); return 1; })
        .then(literal("claim").then(argument("badge", StringArgumentType.word()).executes(ctx->{ claim(ctx.getSource().getPlayerOrException(), BadgeType.fromString(StringArgumentType.getString(ctx,"badge"))); return 1; })))
        .then(literal("claimall").executes(ctx->{ claimAll(ctx.getSource().getPlayerOrException()); return 1; })))); }
    private static void list(ServerPlayer p){
        p.sendSystemMessage(Component.literal("Gym rewards: /gymrewards claim <badge> or /gymrewards claimall").withStyle(ChatFormatting.GOLD));
        for(BadgeType b: BadgeType.values()) if(BadgeManager.hasBadge(p,b)) p.sendSystemMessage(Component.literal("- "+b.name()+": "+(GymRewardClaimData.isClaimed(p,b)?"claimed":"available")).withStyle(ChatFormatting.YELLOW));
    }
    public static void claimAll(ServerPlayer p){ int n=0; for(BadgeType b: BadgeType.values()) if(claimOne(p,b,false)) n++; p.sendSystemMessage(Component.literal("Claimed "+n+" gym reward(s).").withStyle(ChatFormatting.GREEN)); }
    public static void claim(ServerPlayer p, BadgeType badge){ if(badge==null){p.sendSystemMessage(Component.literal("Unknown badge.").withStyle(ChatFormatting.RED));return;} claimOne(p,badge,true); }
    public static boolean claimOne(ServerPlayer p, BadgeType badge, boolean message){
        if(!BadgeManager.hasBadge(p,badge)){ if(message)p.sendSystemMessage(Component.literal("You have not earned that badge yet.").withStyle(ChatFormatting.RED)); return false; }
        if(GymRewardClaimData.isClaimed(p,badge)){ if(message)p.sendSystemMessage(Component.literal("You already claimed that reward.").withStyle(ChatFormatting.GRAY)); return false; }
        var reward=GymRewardConfig.reward(badge); if(reward==null){ if(message)p.sendSystemMessage(Component.literal("No reward configured for that badge.").withStyle(ChatFormatting.RED)); return false; }
        var stacks = GymRewardConfig.itemStacks(badge);
        if(!canFit(p, stacks)){ p.sendSystemMessage(Component.literal("Make inventory space before claiming gym rewards.").withStyle(ChatFormatting.RED)); return false; }
        for(ItemStack stack: stacks) p.getInventory().add(stack.copy());
        if(reward.credits>0) EconomyManager.depositAsync(p,reward.credits,"gym_reward_"+badge.name());
        GymRewardClaimData.markClaimed(p,badge);
        if(message)p.sendSystemMessage(Component.literal("Claimed "+badge.getDisplayName()+" reward.").withStyle(ChatFormatting.GREEN));
        return true;
    }
    private static boolean canFit(ServerPlayer p, java.util.List<ItemStack> stacks){
        if(stacks==null || stacks.isEmpty()) return true;
        int emptySlots=0;
        for(ItemStack slot : p.getInventory().items) if(slot.isEmpty()) emptySlots++;
        int needed=0;
        for(ItemStack stack : stacks){
            if(stack==null || stack.isEmpty()) continue;
            needed += Math.max(1, (int)Math.ceil(stack.getCount() / (double)stack.getMaxStackSize()));
        }
        return emptySlots >= needed;
    }

}
