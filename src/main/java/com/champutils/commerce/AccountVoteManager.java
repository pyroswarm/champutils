package com.champutils.commerce;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class AccountVoteManager {
    private static boolean registered = false;
    private static MinecraftServer server;

    private AccountVoteManager() {}

    public static synchronized void start(MinecraftServer minecraftServer) {
        server = minecraftServer;
        if (registered) return;
        try {
            Class<?> listenerClass = Class.forName("com.vexsoftware.votifier.fabric.event.VoteListener");
            Field eventField = listenerClass.getField("EVENT");
            Object event = eventField.get(null);
            Method register = event.getClass().getMethod("register", Object.class);
            register.setAccessible(true);
            Object listener = Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    (proxy, method, args) -> {
                        if ("onVote".equals(method.getName()) && args != null && args.length == 1) {
                            handleVote(args[0]);
                        }
                        return null;
                    }
            );
            register.invoke(event, listener);
            registered = true;
            System.out.println("[ChampUtils] NuVotifier account vote listener registered.");
        } catch (ClassNotFoundException ignored) {
            System.out.println("[ChampUtils] NuVotifier not present. Account vote listener skipped.");
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to register NuVotifier account vote listener.");
            e.printStackTrace();
        }
    }

    private static void handleVote(Object vote) {
        if (vote == null || !AccountCommerceConfig.CONFIG.voteRewardsEnabled) return;
        MinecraftServer currentServer = server;
        if (currentServer == null) return;

        String username = readString(vote, "getUsername");
        String service = readString(vote, "getServiceName");
        String address = readString(vote, "getAddress");
        String addressHash = hashAddress(address);
        int points = AccountCommerceConfig.CONFIG.votePointsPerVote;
        if (username == null || username.isBlank() || points <= 0) return;

        AccountCommerceRepository.resolveAccountAsync(currentServer, username)
                .thenCompose(account -> {
                    if (account == null) {
                        AccountCommerceRepository.recordUnresolvedVoteAsync(username, service, addressHash);
                        return java.util.concurrent.CompletableFuture.completedFuture(0);
                    }
                    return AccountCommerceRepository.recordVoteAsync(account, service, addressHash, points);
                })
                .thenAccept(granted -> currentServer.execute(() -> {
                    ServerPlayer player = currentServer.getPlayerList().getPlayerByName(username);
                    if (player == null || player.hasDisconnected()) return;
                    if (granted > 0) {
                        player.sendSystemMessage(Component.literal("Thanks for voting! +" + granted + " account vote point(s).").withStyle(ChatFormatting.GREEN));
                    } else {
                        player.sendSystemMessage(Component.literal("Vote received. You have already been credited for this vote site today.").withStyle(ChatFormatting.YELLOW));
                    }
                }))
                .exceptionally(error -> {
                    System.err.println("[ChampUtils] Vote processing failed for " + username + ": " + error.getMessage());
                    return null;
                });
    }

    private static String readString(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String hashAddress(String address) {
        try {
            String input = AccountCommerceConfig.CONFIG.voteAddressHashSalt + ":" + (address == null ? "" : address);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }
}
