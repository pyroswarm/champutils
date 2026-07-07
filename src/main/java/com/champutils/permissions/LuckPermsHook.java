package com.champutils.permissions;

import com.champutils.badge.BadgeType;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;

import net.luckperms.api.model.user.User;
import net.luckperms.api.track.Track;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LuckPermsHook {

    private static final String TRACK_NAME =
            "gymprogress";

    public static boolean isAvailable() {
        try {
            LuckPermsProvider.get();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }




    public static void promoteForBadge(
            ServerPlayer player,
            BadgeType badge
    ){

        if(
                badge == null
        ){
            return;
        }

        try{

            LuckPerms lp =
                    LuckPermsProvider.get();

            User user =
                    lp.getUserManager()
                            .loadUser(
                                    player.getUUID()
                            )
                            .join();

            String before =
                    user.getPrimaryGroup();


            Track track =
                    lp.getTrackManager()
                            .getTrack(
                                    TRACK_NAME
                            );

            if(
                    track == null
            ){
                System.out.println(
                        "[ChampUtils] Missing LP track "
                                + TRACK_NAME
                );
                return;
            }


            track.promote(
                    user,
                    user.getQueryOptions()
                            .context()
            );


            lp.getUserManager()
                    .saveUser(
                            user
                    );


            user =
                    lp.getUserManager()
                            .loadUser(
                                    player.getUUID()
                            )
                            .join();

            String after =
                    user.getPrimaryGroup();


            if(
                    !before.equalsIgnoreCase(
                            after
                    )
            ){
                System.out.println(
                        "[ChampUtils] Rank advanced "
                                + before
                                + " -> "
                                + after
                );
            }
            else{
                System.out.println(
                        "[ChampUtils] Promotion did not advance rank."
                );
            }

        }
        catch(Exception e){
            e.printStackTrace();
        }

    }


    public static boolean hasPermission(
            ServerPlayer player,
            String permission
    ){
        if(
                player == null
                        || permission == null
                        || permission.isBlank()
        ){
            return false;
        }

        if(
                player.hasPermissions(4)
        ){
            return true;
        }

        try{
            LuckPerms lp =
                    LuckPermsProvider.get();

            User user =
                    lp.getUserManager()
                            .getUser(
                                    player.getUUID()
                            );

            if(
                    user == null
            ){
                user =
                        lp.getUserManager()
                                .loadUser(
                                        player.getUUID()
                                )
                                .join();
            }

            return user.getCachedData()
                    .getPermissionData()
                    .checkPermission(
                            permission
                    )
                    .asBoolean();
        }
        catch(Exception ignored){
            return player.hasPermissions(4);
        }
    }


    /**
     * Server-thread hot-path safe permission check.
     *
     * This never calls loadUser(...).join(). If LuckPerms does not already have
     * the online user cached, it returns false instead of blocking the Minecraft
     * tick thread. Use this from movement/tick loops only; commands and slow
     * admin paths can keep using hasPermission(...).
     */
    public static boolean hasPermissionCached(
            ServerPlayer player,
            String permission
    ){
        if(
                player == null
                        || permission == null
                        || permission.isBlank()
        ){
            return false;
        }

        if(player.hasPermissions(4)) return true;

        try{
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUUID());
            if(user == null) return false;

            return user.getCachedData()
                    .getPermissionData()
                    .checkPermission(permission)
                    .asBoolean();
        }
        catch(Exception ignored){
            return false;
        }
    }


    public static boolean hasExactPermissionNode(
            ServerPlayer player,
            String permission
    ){
        if (player == null || permission == null || permission.isBlank()) return false;
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUUID());
            if (user == null) user = lp.getUserManager().loadUser(player.getUUID()).join();
            String wanted = permission.trim().toLowerCase(java.util.Locale.ROOT);
            return user.resolveInheritedNodes(user.getQueryOptions()).stream().anyMatch(node ->
                    node.getKey() != null
                            && node.getKey().equalsIgnoreCase(wanted)
                            && node.getValue()
            );
        } catch (Exception ignored) {
            return false;
        }
    }




    /**
     * Server-thread safe group check. Does not call loadUser(...).join().
     */
    public static boolean hasGroupCached(
            ServerPlayer player,
            String group
    ){
        if (player == null || group == null || group.isBlank()) return false;
        if (player.hasPermissions(4)) return true;
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUUID());
            if (user == null) return false;
            String wanted = group.trim().toLowerCase(java.util.Locale.ROOT);
            if (user.getPrimaryGroup() != null && user.getPrimaryGroup().equalsIgnoreCase(wanted)) return true;
            return user.resolveInheritedNodes(user.getQueryOptions()).stream().anyMatch(node ->
                    node instanceof InheritanceNode
                            && ((InheritanceNode) node).getGroupName() != null
                            && ((InheritanceNode) node).getGroupName().equalsIgnoreCase(wanted)
                            && node.getValue()
            );
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean hasGroup(
            ServerPlayer player,
            String group
    ){
        if (player == null || group == null || group.isBlank()) return false;
        if (player.hasPermissions(4)) return true;
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUUID());
            if (user == null) user = lp.getUserManager().loadUser(player.getUUID()).join();
            String wanted = group.trim().toLowerCase(java.util.Locale.ROOT);
            if (user.getPrimaryGroup() != null && user.getPrimaryGroup().equalsIgnoreCase(wanted)) return true;
            return user.resolveInheritedNodes(user.getQueryOptions()).stream().anyMatch(node ->
                    node instanceof InheritanceNode
                            && ((InheritanceNode) node).getGroupName() != null
                            && ((InheritanceNode) node).getGroupName().equalsIgnoreCase(wanted)
                            && node.getValue()
            );
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean hasAnyGroup(ServerPlayer player, String... groups) {
        if (player == null || groups == null || groups.length == 0) return false;
        for (String group : groups) {
            if (hasGroup(player, group)) return true;
        }
        return false;
    }


    public static boolean addGroup(
            ServerPlayer player,
            String group
    ){
        if (player == null) return false;
        return addGroup(player.getUUID(), group);
    }


    public static boolean addGroup(
            UUID playerUuid,
            String group
    ){
        try {
            return addGroupAsync(playerUuid, group).get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static CompletableFuture<Boolean> addGroupAsync(
            UUID playerUuid,
            String group
    ){
        if (playerUuid == null || group == null || group.isBlank()) return CompletableFuture.completedFuture(false);
        try {
            LuckPerms lp = LuckPermsProvider.get();
            String safeGroup = group.trim().toLowerCase(java.util.Locale.ROOT);
            return lp.getUserManager()
                    .loadUser(playerUuid)
                    .thenCompose(user -> {
                        Node node = InheritanceNode.builder(safeGroup).value(true).build();
                        user.data().add(node);
                        return lp.getUserManager().saveUser(user).thenApply(ignored -> user);
                    })
                    .thenCompose(user -> lp.getUserManager().loadUser(playerUuid))
                    .thenApply(user -> {
                        com.champutils.chat.ChatTagResolver.invalidate(playerUuid);
                        return true;
                    })
                    .exceptionally(error -> {
                        error.printStackTrace();
                        return false;
                    });
        } catch (Exception e) {
            e.printStackTrace();
            return CompletableFuture.completedFuture(false);
        }
    }

}
