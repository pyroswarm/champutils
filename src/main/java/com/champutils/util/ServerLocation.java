package com.champutils.util;

public class ServerLocation {

    public String world;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;

    public ServerLocation() {
    }

    public ServerLocation(
            String world,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }
}
