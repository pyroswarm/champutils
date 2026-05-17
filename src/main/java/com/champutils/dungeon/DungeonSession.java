package com.champutils.dungeon;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DungeonSession {
    public final UUID playerId;
    public final String playerName;
    public final String dungeonId;
    public final String displayName;
    public final DungeonRarity rarity;
    public final long startedAtMillis;

    public final ResourceKey<Level> dungeonWorld;
    public final double dungeonX;
    public final double dungeonY;
    public final double dungeonZ;

    public final ResourceKey<Level> returnWorld;
    public final double returnX;
    public final double returnY;
    public final double returnZ;
    public final float returnYaw;
    public final float returnPitch;

    public int currentTrainerIndex = 0;
    public java.util.UUID activeTrainerUuid = null;
    public boolean completed = false;
    public List<String> lockedPokemonIds = new ArrayList<>();

    public DungeonSession(
            UUID playerId,
            String playerName,
            String dungeonId,
            String displayName,
            DungeonRarity rarity,
            ResourceKey<Level> dungeonWorld,
            double dungeonX,
            double dungeonY,
            double dungeonZ,
            ResourceKey<Level> returnWorld,
            double returnX,
            double returnY,
            double returnZ,
            float returnYaw,
            float returnPitch
    ) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.dungeonId = dungeonId;
        this.displayName = displayName;
        this.rarity = rarity;
        this.dungeonWorld = dungeonWorld;
        this.dungeonX = dungeonX;
        this.dungeonY = dungeonY;
        this.dungeonZ = dungeonZ;
        this.returnWorld = returnWorld;
        this.returnX = returnX;
        this.returnY = returnY;
        this.returnZ = returnZ;
        this.returnYaw = returnYaw;
        this.returnPitch = returnPitch;
        this.startedAtMillis = System.currentTimeMillis();
    }
}
