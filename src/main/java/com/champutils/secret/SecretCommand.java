package com.champutils.secret;

import com.mojang.brigadier.arguments.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class SecretCommand {
 private SecretCommand(){}
 public static void register(){CommandRegistrationCallback.EVENT.register((d,r,e)->{
  d.register(Commands.literal("secrets").executes(c->{SecretMenu.open(c.getSource().getPlayerOrException());return 1;}));
  var root=Commands.literal("secret").requires(s->s.hasPermission(4));
  root.then(Commands.literal("create").then(Commands.argument("secret",StringArgumentType.word()).executes(c->{SecretManager.create(str(c,"secret"));ok(c,"Secret created.");return 1;})));
  root.then(Commands.literal("delete").then(secretArg().executes(c->{SecretManager.delete(str(c,"secret"));ok(c,"Secret deleted.");return 1;})));
  root.then(Commands.literal("stepdelete").then(secretArg().then(stepArg().executes(c->{SecretManager.deleteStep(str(c,"secret"),num(c));ok(c,"Step deleted.");return 1;}))));
  root.then(Commands.literal("steptext").then(secretArg().then(stepArg().then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->{SecretManager.mutate(str(c,"secret"),num(c),s->s.text=StringArgumentType.getString(c,"text"));ok(c,"Step text saved.");return 1;})))));
  root.then(Commands.literal("stepsound").then(secretArg().then(stepArg().then(Commands.argument("sound",StringArgumentType.word()).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggestResource(BuiltInRegistries.SOUND_EVENT.keySet(),b)).executes(c->{SecretManager.mutate(str(c,"secret"),num(c),s->s.sound=str(c,"sound"));ok(c,"Step sound saved.");return 1;})))));
  root.then(Commands.literal("stepcommand").then(secretArg().then(stepArg().then(Commands.argument("command",StringArgumentType.greedyString()).executes(c->{SecretManager.mutate(str(c,"secret"),num(c),s->s.command=StringArgumentType.getString(c,"command"));ok(c,"Step command saved.");return 1;})))));
  root.then(Commands.literal("steptp").then(secretArg().then(stepArg().then(Commands.argument("dimension",StringArgumentType.word()).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggestResource(c.getSource().getServer().levelKeys().stream().map(k->k.location()),b)).then(Commands.argument("position",Vec3Argument.vec3()).executes(c->{var v=Vec3Argument.getVec3(c,"position");SecretManager.mutate(str(c,"secret"),num(c),s->{s.teleport=new SecretManager.Teleport();s.teleport.world=str(c,"dimension");s.teleport.x=v.x;s.teleport.y=v.y;s.teleport.z=v.z;});ok(c,"Step teleport saved.");return 1;}))))));
  root.then(Commands.literal("signbind").then(secretArg().then(stepArg().executes(c->{SecretManager.beginBind(c.getSource().getPlayerOrException(),"sign",str(c,"secret"),num(c));return 1;}))));
  root.then(Commands.literal("npcbind").then(secretArg().then(stepArg().executes(c->{SecretManager.beginBind(c.getSource().getPlayerOrException(),"npc",str(c,"secret"),num(c));return 1;}))));
  root.then(Commands.literal("portal").then(Commands.literal("pos1").executes(c->{SecretManager.portalPos1(c.getSource().getPlayerOrException());return 1;})).then(Commands.literal("pos2").then(secretArg().then(stepArg().executes(c->{SecretManager.portalPos2(c.getSource().getPlayerOrException(),str(c,"secret"),num(c));return 1;})))));
  root.then(Commands.literal("item").then(Commands.argument("item",StringArgumentType.word()).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(),b)).then(Commands.argument("amount",IntegerArgumentType.integer(1)).then(secretArg().then(stepArg().executes(c->{SecretManager.mutate(str(c,"secret"),num(c),s->{s.trigger.type="ITEM";s.trigger.item=str(c,"item");s.trigger.amount=IntegerArgumentType.getInteger(c,"amount");});ok(c,"Item trigger saved.");return 1;}))))));
  root.then(Commands.literal("chat").then(secretArg().then(stepArg().then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->{SecretManager.mutate(str(c,"secret"),num(c),s->{s.trigger.type="CHAT";s.trigger.text=StringArgumentType.getString(c,"text");});ok(c,"Chat trigger saved.");return 1;})))));
  root.then(Commands.literal("killpokemon").then(Commands.argument("pokemon",StringArgumentType.word()).then(Commands.argument("quantity",IntegerArgumentType.integer(1)).then(secretArg().then(stepArg().executes(c->{SecretManager.mutate(str(c,"secret"),num(c),s->{s.trigger.type="KILL_POKEMON";s.trigger.pokemon=str(c,"pokemon");s.trigger.amount=IntegerArgumentType.getInteger(c,"quantity");});ok(c,"Pokemon kill trigger saved.");return 1;}))))));
  root.then(Commands.literal("catch").then(Commands.argument("pokemon",StringArgumentType.word()).then(secretArg().then(stepArg().executes(c->{SecretManager.mutate(str(c,"secret"),num(c),s->{s.trigger.type="CATCH";s.trigger.pokemon=str(c,"pokemon");});ok(c,"Catch trigger saved. Optional filters can be edited in secrets.json.");return 1;})))));
  var rewards=Commands.literal("rewards");
  rewards.then(Commands.literal("items").then(Commands.argument("item",StringArgumentType.word()).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(),b)).then(Commands.argument("amount",IntegerArgumentType.integer(1)).then(secretArg().executes(c->{var x=new SecretManager.RewardDef();x.type="ITEM";x.value=str(c,"item");x.amount=IntegerArgumentType.getInteger(c,"amount");SecretManager.addReward(str(c,"secret"),x);ok(c,"Item reward saved.");return 1;})))));
  rewards.then(Commands.literal("command").then(secretArg().then(Commands.argument("command",StringArgumentType.greedyString()).executes(c->{var x=new SecretManager.RewardDef();x.type="COMMAND";x.value=StringArgumentType.getString(c,"command");SecretManager.addReward(str(c,"secret"),x);ok(c,"Command reward saved.");return 1;}))));
  rewards.then(Commands.literal("pokemon").then(Commands.argument("pokemon",StringArgumentType.word()).then(secretArg().executes(c->{var x=new SecretManager.RewardDef();x.type="POKEMON";x.value=str(c,"pokemon");SecretManager.addReward(str(c,"secret"),x);ok(c,"Pokemon reward saved. Optional filters can be edited in secrets.json.");return 1;}))));
  root.then(rewards);d.register(root);
 });}
 private static com.mojang.brigadier.builder.RequiredArgumentBuilder<net.minecraft.commands.CommandSourceStack,String> secretArg(){return Commands.argument("secret",StringArgumentType.word()).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(SecretManager.names(),b));}
 private static com.mojang.brigadier.builder.RequiredArgumentBuilder<net.minecraft.commands.CommandSourceStack,Integer> stepArg(){return Commands.argument("step",IntegerArgumentType.integer(1));}
 private static String str(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,String n){return StringArgumentType.getString(c,n);}private static int num(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c){return IntegerArgumentType.getInteger(c,"step");}private static void ok(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,String s){c.getSource().sendSuccess(()->Component.literal(s),false);}
}
