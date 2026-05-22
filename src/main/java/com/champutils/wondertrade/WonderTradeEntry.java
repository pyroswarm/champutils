package com.champutils.wondertrade;

import com.google.gson.JsonObject;

import java.util.UUID;

public final class WonderTradeEntry {
    public final UUID id;
    public final UUID ownerUuid;
    public final String ownerUsername;
    public final String species;
    public final String displayName;
    public final boolean shiny;
    public final boolean legendary;
    public final int level;
    public final JsonObject payload;

    public WonderTradeEntry(UUID id, UUID ownerUuid, String ownerUsername, String species, String displayName, boolean shiny, boolean legendary, int level, JsonObject payload) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.ownerUsername = ownerUsername;
        this.species = species;
        this.displayName = displayName;
        this.shiny = shiny;
        this.legendary = legendary;
        this.level = level;
        this.payload = payload;
    }
}
