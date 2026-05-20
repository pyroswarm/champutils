package com.champutils.teleport;

import net.minecraft.server.level.ServerPlayer;

public class PortalRegion {

    public String dimension = "minecraft:overworld";
    public int x1;
    public int y1;
    public int z1;
    public int x2;
    public int y2;
    public int z2;
    public String command;

    public boolean hasPos1 = false;
    public boolean hasPos2 = false;

    public void setPos1(ServerPlayer player) {
        this.dimension = player.serverLevel().dimension().location().toString();
        this.x1 = player.blockPosition().getX();
        this.y1 = player.blockPosition().getY();
        this.z1 = player.blockPosition().getZ();
        this.hasPos1 = true;
    }

    public void setPos2(ServerPlayer player) {
        this.dimension = player.serverLevel().dimension().location().toString();
        this.x2 = player.blockPosition().getX();
        this.y2 = player.blockPosition().getY();
        this.z2 = player.blockPosition().getZ();
        this.hasPos2 = true;
    }

    public boolean isComplete() {
        return hasPos1 && hasPos2 && command != null && !command.isBlank();
    }

    public boolean contains(ServerPlayer player) {
        if (!isComplete()) {
            return false;
        }

        String playerDimension = player.serverLevel().dimension().location().toString();
        if (!dimension.equalsIgnoreCase(playerDimension)) {
            return false;
        }

        int px = player.blockPosition().getX();
        int py = player.blockPosition().getY();
        int pz = player.blockPosition().getZ();

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        return px >= minX && px <= maxX
                && py >= minY && py <= maxY
                && pz >= minZ && pz <= maxZ;
    }
}
