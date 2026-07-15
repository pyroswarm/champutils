package com.champutils.commerce;

import com.champutils.network.NetworkEventManager;
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
import java.util.UUID;

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
                        return java.util.concurrent.CompletableFuture.completedFuture(new VoteResult(null, 0));
                    }
                    return AccountCommerceRepository.recordVoteAsync(account, service, addressHash, points)
                            .thenApply(granted -> new VoteResult(account.accountUuid(), granted));
                })
                .thenAccept(result -> {
                    if (result == null || result.accountUuid == null) return;
                    String message = result.granted > 0
                            ? "Thanks for voting! +" + result.granted + " account vote point(s)."
                            : "Vote received. You have already been credited for this vote site today.";
                    NetworkEventManager.sendPlayerNotice(currentServer, result.accountUuid, message);
                })
                .exceptionally(error -> {
                    System.err.println("[ChampUtils] Vote processing failed for " + username + ": " + error.getMessage());
                    return null;
                });
    }

    private record VoteResult(UUID accountUuid, int granted) {}

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
