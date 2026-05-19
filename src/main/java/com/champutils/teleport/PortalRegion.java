package com.champutils.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public class PortalRegion {
    public String dimension;
    public int x1;
    public int y1;
    public int z1;
    public int x2;
    public int y2;
    public int z2;
    public boolean hasPos1;
    public boolean hasPos2;
    public String command;

    public void setPos1(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        this.dimension = player.level().dimension().location().toString();
        this.x1 = pos.getX();
        this.y1 = pos.getY();
        this.z1 = pos.getZ();
        this.hasPos1 = true;
    }

    public void setPos2(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        this.dimension = player.level().dimension().location().toString();
        this.x2 = pos.getX();
        this.y2 = pos.getY();
        this.z2 = pos.getZ();
        this.hasPos2 = true;
    }

    public boolean isComplete() {
        return hasPos1 && hasPos2 && command != null && !command.isBlank() && dimension != null && !dimension.isBlank();
    }

    public boolean contains(ServerPlayer player) {
        if (!isComplete()) return false;
        if (!player.level().dimension().location().toString().equals(dimension)) return false;

        BlockPos pos = player.blockPosition();
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }
}
